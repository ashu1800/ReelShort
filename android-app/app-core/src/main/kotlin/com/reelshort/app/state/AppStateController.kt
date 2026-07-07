package com.reelshort.app.state

import com.reelshort.app.data.AppDataSource
import com.reelshort.app.data.ApiHealthStatus
import com.reelshort.app.data.AppLanguage
import com.reelshort.app.data.AuthSession
import com.reelshort.app.data.BookInteractionState
import com.reelshort.app.data.BookSummary
import com.reelshort.app.data.Comment
import com.reelshort.app.data.EpisodeSummary
import com.reelshort.app.data.SavedCredentials
import com.reelshort.app.data.SmsVerificationPurpose
import com.reelshort.app.data.WatchRecord
import com.reelshort.app.network.ApiClientException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class AppStateController(private val dataSource: AppDataSource) {
    private val mutableState = MutableStateFlow(AppUiState(apiBaseUrl = dataSource.apiBaseUrl))
    private var rewardReportInFlight = false
    private var sessionRestored = false
    private var searchRequestVersion = 0L
    private var playbackRequestVersion = 0L
    private var accountRequestVersion = 0L
    private var favoritesRequestVersion = 0L

    val state: StateFlow<AppUiState> = mutableState.asStateFlow()

    suspend fun restoreSession() = runWithLoading(ErrorContext.CONTENT) {
        if (sessionRestored) {
            mutableState.update { it.copy(isLoading = false) }
            return@runWithLoading
        }
        sessionRestored = true
        val language = dataSource.loadLanguagePreference()
        val session = dataSource.restoreSession()
        val savedCredentials = dataSource.loadSavedCredentials()
        val cachedHomeShelf = dataSource.loadCachedHomeShelf()
        if (cachedHomeShelf.isNotEmpty()) {
            // 冷启动秒开：先用磁盘缓存立即渲染首页，随后在后台静默拉取最新数据替换并更新缓存。
            mutableState.update {
                it.copy(
                    screen = AppScreen.HOME,
                    language = language,
                    session = session,
                    savedCredentials = savedCredentials,
                    homeShelf = cachedHomeShelf,
                    isLoading = false,
                )
            }
            refreshHomeSilently()
            return@runWithLoading
        }
        try {
            val homeShelf = dataSource.loadHomeShelf()
            mutableState.update {
                it.copy(
                    screen = AppScreen.HOME,
                    language = language,
                    session = session,
                    savedCredentials = savedCredentials,
                    homeShelf = homeShelf,
                    isLoading = false,
                )
            }
            dataSource.saveCachedHomeShelf(homeShelf)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            mutableState.update {
                it.copy(
                    screen = AppScreen.HOME,
                    language = language,
                    session = session,
                    savedCredentials = savedCredentials,
                    homeShelf = emptyList(),
                    isLoading = false,
                    errorMessage = "内容暂时加载失败，可以稍后刷新。",
                )
            }
        }
    }

    suspend fun loadSavedCredentials() = runWithLoading(ErrorContext.DIAGNOSTIC) {
        val savedCredentials = dataSource.loadSavedCredentials()
        mutableState.update {
            it.copy(savedCredentials = savedCredentials, isLoading = false)
        }
    }

    suspend fun login(username: String, password: String, rememberPassword: Boolean = false) = runWithLoading(ErrorContext.LOGIN) {
        val session = dataSource.login("+1", username, password)
        updateLegacySavedCredentials(username, password, rememberPassword)
        openHomeAfterAuthentication(session)
    }

    suspend fun login(
        countryCode: String,
        phoneNumber: String,
        password: String,
        rememberPassword: Boolean = false,
    ) = runWithLoading(ErrorContext.LOGIN) {
        val session = dataSource.login(countryCode, phoneNumber, password)
        updateSavedCredentials(countryCode, phoneNumber, password, rememberPassword)
        openHomeAfterAuthentication(session)
    }

    suspend fun register(username: String, password: String, rememberPassword: Boolean = false) =
        register("+1", username, password, "000000")

    suspend fun register(
        countryCode: String,
        phoneNumber: String,
        password: String,
        verificationCode: String,
    ) = runWithLoading(ErrorContext.REGISTER) {
        dataSource.register(countryCode, phoneNumber, password, verificationCode)
        mutableState.update {
            it.copy(
                isLoading = false,
                authPromptVisible = false,
                errorMessage = registrationSimulationMessage(),
            )
        }
    }

    suspend fun sendAuthSms(countryCode: String, phoneNumber: String) = runWithLoading(ErrorContext.REGISTER) {
        dataSource.sendAuthSms(SmsVerificationPurpose.PUBLIC_REGISTER, countryCode, phoneNumber)
        mutableState.update {
            it.copy(isLoading = false, errorMessage = smsSentMessage())
        }
    }

    suspend fun sendPasswordChangeVerification() = runWithLoading(ErrorContext.ACCOUNT) {
        requireAuthenticatedAccount()
        val phone = currentSessionPhone()
        dataSource.sendAuthSms(SmsVerificationPurpose.PASSWORD_CHANGE, phone.countryCode, phone.phoneNumber)
        mutableState.update {
            it.copy(isLoading = false, errorMessage = smsSentMessage())
        }
    }

    private suspend fun updateSavedCredentials(
        countryCode: String,
        phoneNumber: String,
        password: String,
        rememberPassword: Boolean,
    ) {
        if (rememberPassword) {
            val credentials = SavedCredentials(
                username = "$countryCode$phoneNumber",
                countryCode = countryCode,
                phoneNumber = phoneNumber,
                password = password,
                rememberPassword = true,
            )
            dataSource.saveCredentials(credentials)
            mutableState.update { it.copy(savedCredentials = credentials) }
        } else {
            dataSource.clearSavedCredentials()
            mutableState.update { it.copy(savedCredentials = null) }
        }
    }

    private suspend fun updateLegacySavedCredentials(username: String, password: String, rememberPassword: Boolean) {
        if (rememberPassword) {
            val credentials = SavedCredentials(username = username, password = password, rememberPassword = true)
            dataSource.saveCredentials(credentials)
            mutableState.update { it.copy(savedCredentials = credentials) }
        } else {
            dataSource.clearSavedCredentials()
            mutableState.update { it.copy(savedCredentials = null) }
        }
    }

    private suspend fun openHomeAfterAuthentication(session: AuthSession) {
        val pendingEpisode = state.value.pendingPlaybackEpisode
        if (pendingEpisode != null) {
            val current = state.value
            val requestVersion = ++playbackRequestVersion
            mutableState.update {
                it.copy(
                    session = session,
                    authPromptVisible = false,
                    pendingPlaybackEpisode = null,
                    isLoading = true,
                    errorMessage = null,
                )
            }
            val book = current.selectedBook ?: requireSelectedBook()
            val episodes = current.episodes
            val fallbackEpisode = playbackFallbackEpisode(episodes, pendingEpisode)
            val episode = if (episodes.isNotEmpty()) {
                chooseResumeEpisode(book, episodes, fallbackEpisode, requestVersion) ?: fallbackEpisode
            } else {
                pendingEpisode
            }
            if (!isActivePlaybackRequest(requestVersion)) {
                return
            }
            if (episode == null) {
                mutableState.update { it.copy(isLoading = false, errorMessage = "内容暂时加载失败，可以稍后刷新。") }
                return
            }
            openPlayerForAuthenticatedUser(book, episode, requestVersion)
            return
        }
        if (state.value.screen == AppScreen.ACCOUNT) {
            mutableState.update {
                it.copy(
                    session = session,
                    authPromptVisible = false,
                    pendingPlaybackEpisode = null,
                    isLoading = false,
                    errorMessage = null,
                )
            }
            loadAccountSnapshot()
            return
        }
        val homeShelf = try {
            dataSource.loadHomeShelf()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            null
        }
        mutableState.update {
            it.copy(
                screen = AppScreen.HOME,
                session = session,
                authPromptVisible = false,
                pendingPlaybackEpisode = null,
                homeShelf = homeShelf ?: emptyList(),
                isLoading = false,
                errorMessage = if (homeShelf == null) "内容暂时加载失败，可以稍后刷新。" else null,
            )
        }
        if (homeShelf != null) {
            dataSource.saveCachedHomeShelf(homeShelf)
        }
    }

    suspend fun refreshHome() = runWithLoading(ErrorContext.CONTENT) {
        val homeShelf = dataSource.loadHomeShelf()
        mutableState.update {
            it.copy(
                screen = AppScreen.HOME,
                homeShelf = homeShelf,
                isLoading = false,
            )
        }
        dataSource.saveCachedHomeShelf(homeShelf)
    }

    /**
     * 下拉刷新首页：与 [refreshHome] 不同，只展示顶部的下拉刷新指示器，
     * 不触发全局居中 loading 弹窗，且失败时保留旧数据并给出顶部错误提示。
     */
    suspend fun refreshHomeWithPull() {
        mutableState.update { it.copy(screen = AppScreen.HOME, isHomeRefreshing = true, errorMessage = null) }
        try {
            val homeShelf = dataSource.loadHomeShelf()
            mutableState.update {
                it.copy(homeShelf = homeShelf, isHomeRefreshing = false)
            }
            dataSource.saveCachedHomeShelf(homeShelf)
        } catch (error: CancellationException) {
            mutableState.update { it.copy(isHomeRefreshing = false) }
            throw error
        } catch (error: Throwable) {
            mutableState.update {
                it.copy(
                    isHomeRefreshing = false,
                    errorMessage = userFacingErrorMessage(error, ErrorContext.CONTENT),
                )
            }
        }
    }

    suspend fun openHome() {
        if (state.value.homeShelf.isEmpty()) {
            refreshHome()
            return
        }

        mutableState.update { it.copy(screen = AppScreen.HOME, errorMessage = null) }
        refreshHomeSilently()
    }

    private suspend fun refreshHomeSilently() {
        try {
            val homeShelf = dataSource.loadHomeShelf()
            mutableState.update {
                it.copy(
                    screen = AppScreen.HOME,
                    homeShelf = homeShelf,
                )
            }
            dataSource.saveCachedHomeShelf(homeShelf)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
        }
    }

    suspend fun search(query: String) = runWithLoading(ErrorContext.CONTENT) {
        val requestVersion = ++searchRequestVersion
        val results = dataSource.search(query)
        if (requestVersion != searchRequestVersion) {
            return@runWithLoading
        }
        mutableState.update {
            it.copy(
                screen = AppScreen.SEARCH,
                searchQuery = query,
                searchResults = results,
                isLoading = false,
            )
        }
    }

    fun showSearch() {
        mutableState.update { it.copy(screen = AppScreen.SEARCH, errorMessage = null) }
    }

    suspend fun setLanguage(language: AppLanguage) {
        mutableState.update {
            it.copy(
                language = language,
                screen = AppScreen.HOME,
                searchQuery = "",
                searchResults = emptyList(),
                isLoading = true,
                errorMessage = null,
            )
        }
        try {
            dataSource.saveLanguagePreference(language)
            val homeShelf = dataSource.loadHomeShelf()
            mutableState.update {
                it.copy(
                    homeShelf = homeShelf,
                    isLoading = false,
                    errorMessage = null,
                )
            }
            dataSource.saveCachedHomeShelf(homeShelf)
        } catch (error: CancellationException) {
            mutableState.update { it.copy(isLoading = false) }
            throw error
        } catch (error: Throwable) {
            mutableState.update {
                it.copy(
                    isLoading = false,
                    errorMessage = userFacingErrorMessage(error, ErrorContext.CONTENT),
                )
            }
        }
    }

    suspend fun openBook(book: BookSummary) = runWithLoading(ErrorContext.CONTENT) {
        val requestVersion = ++playbackRequestVersion
        val returnScreen = playbackReturnScreenFor(state.value.screen)
        val episodes = dataSource.loadEpisodes(book)
        if (!isActivePlaybackRequest(requestVersion)) {
            return@runWithLoading
        }
        val firstEpisode = episodes.firstOrNull()
        if (firstEpisode == null) {
            mutableState.update {
                it.copy(
                    selectedBook = book,
                    episodes = emptyList(),
                    selectedEpisode = null,
                    currentVideoUrl = null,
                    playback = PlaybackState(),
                    playerReturnScreen = returnScreen,
                    isLoading = false,
                    errorMessage = "内容暂时加载失败，可以稍后刷新。",
                )
            }
            return@runWithLoading
        }
        val session = state.value.session
        mutableState.update {
            it.copy(
                selectedBook = book,
                episodes = episodes,
                selectedEpisode = null,
                currentVideoUrl = null,
                playback = PlaybackState(),
                playerReturnScreen = returnScreen,
                isLoading = session != null,
            )
        }
        if (session == null) {
            mutableState.update {
                it.copy(
                    authPromptVisible = true,
                    pendingPlaybackEpisode = firstEpisode,
                    isLoading = false,
                )
            }
            return@runWithLoading
        }
        val resumeEpisode = chooseResumeEpisode(book, episodes, firstEpisode, requestVersion) ?: firstEpisode
        if (!isActivePlaybackRequest(requestVersion)) {
            return@runWithLoading
        }
        openPlayerForAuthenticatedUser(book, resumeEpisode, requestVersion)
    }

    suspend fun openPlayer(episode: EpisodeSummary) = runWithLoading(ErrorContext.PLAYBACK) {
        val book = requireSelectedBook()
        val requestVersion = ++playbackRequestVersion
        if (state.value.session == null) {
            mutableState.update {
                it.copy(
                    authPromptVisible = true,
                    pendingPlaybackEpisode = episode,
                    isLoading = false,
                )
            }
            return@runWithLoading
        }
        openPlayerForAuthenticatedUser(book, episode, requestVersion)
    }

    suspend fun openWatchRecord(record: WatchRecord) = runWithLoading(ErrorContext.CONTENT) {
        if (state.value.session == null) {
            mutableState.update {
                it.copy(
                    screen = AppScreen.ACCOUNT,
                    authPromptVisible = true,
                    isLoading = false,
                    errorMessage = null,
                )
            }
            return@runWithLoading
        }
        val requestVersion = ++playbackRequestVersion
        val book = dataSource.loadBook(record.bookId)
        if (!isActivePlaybackRequest(requestVersion)) {
            return@runWithLoading
        }
        val episodes = dataSource.loadEpisodes(book)
        if (!isActivePlaybackRequest(requestVersion)) {
            return@runWithLoading
        }
        val episode = resumeEpisodeForRecord(episodes, record) ?: episodes.firstOrNull()
        if (episode == null) {
            mutableState.update {
                it.copy(
                    screen = AppScreen.ACCOUNT,
                    selectedBook = book,
                    episodes = emptyList(),
                    selectedEpisode = null,
                    currentVideoUrl = null,
                    playback = PlaybackState(),
                    playerReturnScreen = AppScreen.ACCOUNT,
                    isLoading = false,
                    errorMessage = "内容暂时加载失败，可以稍后刷新。",
                )
            }
            return@runWithLoading
        }
        mutableState.update {
            it.copy(
                selectedBook = book,
                episodes = episodes,
                selectedEpisode = null,
                currentVideoUrl = null,
                playback = PlaybackState(),
                playerReturnScreen = AppScreen.ACCOUNT,
                isLoading = true,
            )
        }
        openPlayerForAuthenticatedUser(book, episode, requestVersion)
    }

    private suspend fun openPlayerForAuthenticatedUser(
        book: BookSummary,
        episode: EpisodeSummary,
        requestVersion: Long,
    ) {
        val snapshot = try {
            dataSource.loadEpisodeSnapshot(book, episode)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            null
        }
        if (!isActivePlaybackRequest(requestVersion)) {
            return
        }
        val videoUrl = dataSource.loadVideoUrl(book, episode)
        if (!isActivePlaybackRequest(requestVersion)) {
            return
        }
        val initialPosition = snapshot?.positionSeconds ?: 0
        val initialDuration = snapshot?.durationSeconds?.takeIf { it > 0 } ?: videoUrl.durationSeconds
        val awardedProgress = snapshot?.awardedStages?.maxOrNull() ?: 0
        val interaction = loadInteractionSilently(book)
        if (!isActivePlaybackRequest(requestVersion)) {
            return
        }
        val comments = loadCommentsSilently(book)
        if (!isActivePlaybackRequest(requestVersion)) {
            return
        }
        mutableState.update {
            it.copy(
                screen = AppScreen.PLAYER,
                selectedEpisode = episode,
                currentVideoUrl = videoUrl,
                playback = PlaybackState.ready(book, episode, videoUrl)
                    .withPosition(initialPosition, initialDuration)
                    .withReportedProgress(initialPosition, awardedProgress),
                interaction = interaction,
                comments = comments,
                isLoading = false,
                authPromptVisible = false,
                pendingPlaybackEpisode = null,
                errorMessage = if (snapshot == null) "积分状态暂时加载失败，播放不受影响。" else null,
            )
        }
    }

    private suspend fun loadInteractionSilently(book: BookSummary): BookInteractionState {
        val like = try {
            dataSource.loadLikeStatus(book)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            null
        }
        val favorite = try {
            dataSource.loadFavoriteStatus(book)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            null
        }
        return BookInteractionState(
            liked = like?.active ?: false,
            likeCount = like?.count ?: 0,
            favorited = favorite?.active ?: false,
            favoriteCount = favorite?.count ?: 0,
        )
    }

    private suspend fun loadCommentsSilently(book: BookSummary): List<Comment> = try {
        dataSource.listComments(book)
    } catch (error: CancellationException) {
        throw error
    } catch (_: Throwable) {
        emptyList()
    }

    private suspend fun chooseResumeEpisode(
        book: BookSummary,
        episodes: List<EpisodeSummary>,
        fallbackEpisode: EpisodeSummary?,
        requestVersion: Long,
    ): EpisodeSummary? {
        val history = try {
            dataSource.loadWatchHistory()
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            return if (isActivePlaybackRequest(requestVersion)) {
                playbackFallbackEpisode(episodes, fallbackEpisode)
            } else {
                null
            }
        }
        if (!isActivePlaybackRequest(requestVersion)) {
            return null
        }
        val record = history.firstOrNull { it.bookId == book.id } ?: return playbackFallbackEpisode(episodes, fallbackEpisode)
        return resumeEpisodeForRecord(episodes, record) ?: playbackFallbackEpisode(episodes, fallbackEpisode)
    }

    fun updatePlaybackPosition(positionSeconds: Int, durationSeconds: Int) {
        mutableState.update {
            if (it.playback.status != PlaybackStatus.READY) {
                it
            } else {
                it.copy(playback = it.playback.withPosition(positionSeconds, durationSeconds))
            }
        }
    }

    suspend fun reportProgress(positionSeconds: Int, durationSeconds: Int) = runWithLoading(ErrorContext.PLAYBACK) {
        val current = state.value
        val book = current.selectedBook ?: throw IllegalStateException("未选择剧集")
        val episode = current.selectedEpisode ?: throw IllegalStateException("未选择分集")
        val progress = dataSource.reportWatchProgress(book, episode, positionSeconds, durationSeconds)
        val history = dataSource.loadWatchHistory()
        val points = dataSource.loadPointAccount()
        mutableState.update {
            it.copy(
                watchHistory = history,
                pointAccount = points,
                playback = it.playback
                    .withPosition(progress.positionSeconds, progress.durationSeconds)
                    .withReportedProgress(
                        positionSeconds = progress.positionSeconds,
                        progressPercent = progress.progressPercent,
                    ),
                isLoading = false,
            )
        }
    }

    suspend fun reportProgressSilently(positionSeconds: Int, durationSeconds: Int) {
        val current = state.value
        val playback = current.playback
        if (playback.status != PlaybackStatus.READY || playback.isRewardReporting || rewardReportInFlight) {
            return
        }
        if (positionSeconds <= 0 || durationSeconds <= 0) {
            return
        }
        val localProgress = playback.withPosition(positionSeconds, durationSeconds)
        if (nextUnreportedRewardStage(localProgress.progressPercent, playback.lastReportedProgressPercent) == null) {
            mutableState.update { it.copy(playback = localProgress) }
            return
        }
        val book = current.selectedBook ?: return
        val episode = current.selectedEpisode ?: return

        rewardReportInFlight = true
        mutableState.update {
            it.copy(playback = localProgress.withRewardReporting(true))
        }
        try {
            val progress = dataSource.reportWatchProgress(book, episode, positionSeconds, durationSeconds)
            val history = dataSource.loadWatchHistory()
            val points = dataSource.loadPointAccount()
            mutableState.update {
                it.copy(
                    watchHistory = history,
                    pointAccount = points,
                    playback = it.playback
                        .withPosition(progress.positionSeconds, progress.durationSeconds)
                        .withReportedProgress(
                            positionSeconds = progress.positionSeconds,
                            progressPercent = progress.progressPercent,
                        )
                        .withRewardReporting(false),
                )
            }
        } catch (error: CancellationException) {
            mutableState.update { it.copy(playback = it.playback.withRewardReporting(false)) }
            throw error
        } catch (_: Throwable) {
            mutableState.update {
                it.copy(playback = it.playback.withPosition(positionSeconds, durationSeconds).withRewardReportError())
            }
        } finally {
            rewardReportInFlight = false
        }
    }

    suspend fun refreshPlaybackUrl() = runWithLoading(ErrorContext.PLAYBACK) {
        val current = state.value
        val book = current.selectedBook ?: throw IllegalStateException("未选择剧集")
        val episode = current.selectedEpisode ?: throw IllegalStateException("未选择分集")
        val videoUrl = dataSource.loadVideoUrl(book, episode)
        mutableState.update {
            it.copy(
                currentVideoUrl = videoUrl,
                playback = it.playback.withVideoUrl(videoUrl),
                isLoading = false,
            )
        }
    }

    suspend fun toggleLike() = runSocialAction {
        val book = requireSelectedBook()
        val result = dataSource.toggleLike(book)
        mutableState.update {
            it.copy(interaction = it.interaction.copy(liked = result.active, likeCount = result.count))
        }
    }

    suspend fun toggleFavorite() = runSocialAction {
        val book = requireSelectedBook()
        val result = dataSource.toggleFavorite(book)
        mutableState.update {
            it.copy(interaction = it.interaction.copy(favorited = result.active, favoriteCount = result.count))
        }
    }

    suspend fun submitComment(content: String) = runSocialAction {
        val trimmed = content.trim()
        require(trimmed.isNotEmpty()) { "评论内容不能为空" }
        val book = requireSelectedBook()
        dataSource.addComment(book, trimmed)
        val comments = dataSource.listComments(book)
        mutableState.update { it.copy(comments = comments) }
    }

    suspend fun openFavorites() = runWithLoading(ErrorContext.ACCOUNT) {
        val requestVersion = ++favoritesRequestVersion
        if (state.value.session == null) {
            mutableState.update {
                it.copy(screen = AppScreen.FAVORITES, favorites = emptyList(), errorMessage = null, isLoading = false)
            }
            return@runWithLoading
        }
        mutableState.update { it.copy(screen = AppScreen.FAVORITES) }
        val favorites = dataSource.loadMyFavorites()
        if (requestVersion != favoritesRequestVersion || state.value.screen != AppScreen.FAVORITES) {
            return@runWithLoading
        }
        mutableState.update {
            it.copy(screen = AppScreen.FAVORITES, favorites = favorites, errorMessage = null, isLoading = false)
        }
    }

    fun backToDetail() {
        backToPlaybackSource()
    }

    fun backToPlaybackSource() {
        mutableState.update {
            it.copy(
                screen = it.playerReturnScreen ?: AppScreen.HOME,
                isLoading = false,
                errorMessage = null,
            )
        }
    }

    fun backToAccount() {
        favoritesRequestVersion++
        mutableState.update { it.copy(screen = AppScreen.ACCOUNT, isLoading = false, errorMessage = null) }
    }

    private suspend fun runSocialAction(block: suspend () -> Unit) {
        mutableState.update { it.copy(errorMessage = null) }
        try {
            block()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            mutableState.update {
                it.copy(
                    errorMessage = userFacingErrorMessage(error, ErrorContext.SOCIAL),
                    authPromptVisible = if (shouldPromptForLogin(error, ErrorContext.SOCIAL)) true else it.authPromptVisible,
                )
            }
        }
    }

    suspend fun loadAccountSnapshot() {
        val requestVersion = ++accountRequestVersion
        mutableState.update { it.copy(screen = AppScreen.ACCOUNT) }
        loadAccountSnapshot(requestVersion)
    }

    suspend fun openAccount() {
        if (state.value.session == null) {
            mutableState.update { it.copy(screen = AppScreen.ACCOUNT, errorMessage = null, isLoading = false) }
            return
        }
        if (!hasAccountSnapshot(state.value)) {
            val requestVersion = ++accountRequestVersion
            mutableState.update { it.copy(screen = AppScreen.ACCOUNT) }
            loadAccountSnapshot(requestVersion)
            return
        }

        val requestVersion = ++accountRequestVersion
        mutableState.update { it.copy(screen = AppScreen.ACCOUNT, errorMessage = null) }
        refreshAccountSilently(requestVersion)
    }

    private suspend fun loadAccountSnapshot(requestVersion: Long) = runWithLoading(ErrorContext.ACCOUNT) {
        loadAccountSnapshotForAuthenticatedUser(requestVersion)
    }

    private suspend fun loadAccountSnapshotForAuthenticatedUser(requestVersion: Long) {
        val history = dataSource.loadWatchHistory()
        val points = dataSource.loadPointAccount()
        val orders = dataSource.loadOrders()
        val wallet = loadOptionalAccountData(null) { dataSource.loadWallet() }
        val withdrawalSummary = loadOptionalAccountData(null) { dataSource.loadWithdrawalSummary() }
        val withdrawals = loadOptionalAccountData(emptyList()) { dataSource.loadWithdrawals() }
        val pointTransfers = loadOptionalAccountData(emptyList()) { dataSource.loadPointTransfers() }
        if (requestVersion != accountRequestVersion || state.value.screen != AppScreen.ACCOUNT) {
            return
        }
        val continueWatchingBooks = loadContinueWatchingBooks(history, requestVersion)
        if (requestVersion != accountRequestVersion || state.value.screen != AppScreen.ACCOUNT) {
            return
        }
        mutableState.update {
            it.copy(
                screen = AppScreen.ACCOUNT,
                watchHistory = history,
                continueWatchingBooks = continueWatchingBooks,
                pointAccount = points,
                orders = orders,
                wallet = wallet,
                withdrawalSummary = withdrawalSummary,
                withdrawals = withdrawals,
                pointTransfers = pointTransfers,
                isLoading = false,
            )
        }
    }

    private suspend fun refreshAccountSilently(requestVersion: Long = accountRequestVersion) {
        try {
            val history = dataSource.loadWatchHistory()
            if (requestVersion != accountRequestVersion || state.value.screen != AppScreen.ACCOUNT) {
                return
            }
            val points = dataSource.loadPointAccount()
            if (requestVersion != accountRequestVersion || state.value.screen != AppScreen.ACCOUNT) {
                return
            }
            val orders = dataSource.loadOrders()
            if (requestVersion != accountRequestVersion || state.value.screen != AppScreen.ACCOUNT) {
                return
            }
            val wallet = loadOptionalAccountData(null) { dataSource.loadWallet() }
            if (requestVersion != accountRequestVersion || state.value.screen != AppScreen.ACCOUNT) {
                return
            }
            val withdrawalSummary = loadOptionalAccountData(null) { dataSource.loadWithdrawalSummary() }
            if (requestVersion != accountRequestVersion || state.value.screen != AppScreen.ACCOUNT) {
                return
            }
            val withdrawals = loadOptionalAccountData(emptyList()) { dataSource.loadWithdrawals() }
            if (requestVersion != accountRequestVersion || state.value.screen != AppScreen.ACCOUNT) {
                return
            }
            val pointTransfers = loadOptionalAccountData(emptyList()) { dataSource.loadPointTransfers() }
            if (requestVersion != accountRequestVersion || state.value.screen != AppScreen.ACCOUNT) {
                return
            }
            val continueWatchingBooks = loadContinueWatchingBooks(history, requestVersion)
            if (requestVersion != accountRequestVersion || state.value.screen != AppScreen.ACCOUNT) {
                return
            }
            mutableState.update {
                it.copy(
                    screen = AppScreen.ACCOUNT,
                    watchHistory = history,
                    continueWatchingBooks = continueWatchingBooks,
                    pointAccount = points,
                    orders = orders,
                    wallet = wallet,
                    withdrawalSummary = withdrawalSummary,
                    withdrawals = withdrawals,
                    pointTransfers = pointTransfers,
                )
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
        }
    }

    suspend fun sendWalletVerification(purpose: SmsVerificationPurpose) = runWithLoading(ErrorContext.ACCOUNT) {
        requireAuthenticatedAccount()
        dataSource.sendWalletVerification(purpose)
        mutableState.update {
            it.copy(isLoading = false, errorMessage = smsSentMessage())
        }
    }

    suspend fun bindWallet(walletAddress: String, verificationCode: String) = runWithLoading(ErrorContext.ACCOUNT) {
        requireAuthenticatedAccount()
        dataSource.bindWallet(walletAddress, verificationCode)
        reloadAccountSnapshotAfterAction()
    }

    suspend fun unbindWallet(verificationCode: String) = runWithLoading(ErrorContext.ACCOUNT) {
        requireAuthenticatedAccount()
        dataSource.unbindWallet(verificationCode)
        reloadAccountSnapshotAfterAction()
    }

    suspend fun submitBankCard(holderName: String, cardNumber: String) = runWithLoading(ErrorContext.ACCOUNT) {
        requireAuthenticatedAccount()
        dataSource.submitBankCard(holderName, cardNumber)
        mutableState.update { it.copy(isLoading = false) }
    }

    suspend fun submitWithdrawal(pointAmount: Int) = runWithLoading(ErrorContext.ACCOUNT) {
        requireAuthenticatedAccount()
        dataSource.submitWithdrawal(pointAmount)
        reloadAccountSnapshotAfterAction()
    }

    suspend fun transferPoints(recipientAccount: String, pointAmount: Int) = runWithLoading(ErrorContext.ACCOUNT) {
        requireAuthenticatedAccount()
        dataSource.transferPoints(recipientAccount, pointAmount)
        reloadAccountSnapshotAfterAction()
    }

    suspend fun changePassword(oldPassword: String, newPassword: String, verificationCode: String) =
        runWithLoading(ErrorContext.ACCOUNT) {
            requireAuthenticatedAccount()
            dataSource.changePassword(oldPassword, newPassword, verificationCode)
            mutableState.update { it.copy(isLoading = false, errorMessage = passwordChangedMessage()) }
        }

    fun clearError() {
        mutableState.update { it.copy(errorMessage = null) }
    }

    fun showAuthPrompt() {
        mutableState.update { it.copy(authPromptVisible = true, errorMessage = null) }
    }

    fun dismissAuthPrompt() {
        mutableState.update { it.copy(authPromptVisible = false, pendingPlaybackEpisode = null) }
    }

    suspend fun checkApiHealth() {
        mutableState.update { it.copy(isLoading = true, errorMessage = null) }
        try {
            val status = dataSource.checkSystemHealth()
            mutableState.update {
                it.copy(
                    apiHealthStatus = status,
                    isLoading = false,
                )
            }
        } catch (error: CancellationException) {
            mutableState.update { it.copy(isLoading = false) }
            throw error
        } catch (error: Throwable) {
            val message = userFacingErrorMessage(error, ErrorContext.DIAGNOSTIC)
            mutableState.update {
                it.copy(
                    apiHealthStatus = ApiHealthStatus(status = "DOWN", service = message),
                    errorMessage = message,
                    isLoading = false,
                )
            }
        }
    }

    suspend fun logout() = runWithLoading(ErrorContext.ACCOUNT) {
        dataSource.clearSession()
        mutableState.value = AppUiState(
            apiBaseUrl = dataSource.apiBaseUrl,
            savedCredentials = state.value.savedCredentials,
            language = state.value.language,
        )
    }

    private suspend fun runWithLoading(errorContext: ErrorContext, block: suspend () -> Unit) {
        mutableState.update { it.copy(isLoading = true, errorMessage = null) }
        try {
            block()
        } catch (error: CancellationException) {
            mutableState.update { it.copy(isLoading = false) }
            throw error
        } catch (error: Throwable) {
            val blockedAccount = isBlockedAccountError(error)
            if (blockedAccount) {
                dataSource.clearSession()
            }
            mutableState.update {
                it.copy(
                    session = if (blockedAccount) null else it.session,
                    isLoading = false,
                    errorMessage = userFacingErrorMessage(error, errorContext),
                    authPromptVisible = if (blockedAccount) {
                        false
                    } else if (shouldPromptForLogin(error, errorContext)) {
                        true
                    } else {
                        it.authPromptVisible
                    },
                    pendingPlaybackEpisode = if (blockedAccount) null else it.pendingPlaybackEpisode,
                )
            }
        }
    }

    private fun requireSelectedBook(): BookSummary =
        state.value.selectedBook ?: throw IllegalStateException("未选择剧集")

    private fun isActivePlaybackRequest(requestVersion: Long): Boolean =
        requestVersion == playbackRequestVersion

    private fun hasAccountSnapshot(state: AppUiState): Boolean =
        state.pointAccount != null || state.watchHistory.isNotEmpty() || state.orders.isNotEmpty() || state.wallet != null

    private fun requireAuthenticatedAccount() {
        if (state.value.session == null) {
            mutableState.update { it.copy(authPromptVisible = true, isLoading = false) }
            throw IllegalStateException("登录状态已失效，请重新登录。")
        }
    }

    private suspend fun reloadAccountSnapshotAfterAction() {
        val requestVersion = ++accountRequestVersion
        mutableState.update { it.copy(screen = AppScreen.ACCOUNT) }
        loadAccountSnapshotForAuthenticatedUser(requestVersion)
    }

    private suspend fun <T> loadOptionalAccountData(fallback: T, loader: suspend () -> T): T =
        try {
            loader()
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            fallback
        }

    private fun currentSessionPhone(): SessionPhone {
        val session = state.value.session ?: throw IllegalStateException("登录状态已失效，请重新登录。")
        val account = (session.phoneE164 ?: session.username).trim()
        val countryCode = supportedPhoneCountryCodes.firstOrNull { account.startsWith(it) }
            ?: throw IllegalStateException("phone account required")
        val phoneNumber = account.removePrefix(countryCode).filter(Char::isDigit)
        if (phoneNumber.isBlank()) {
            throw IllegalStateException("phone account required")
        }
        return SessionPhone(countryCode, phoneNumber)
    }

    private suspend fun loadContinueWatchingBooks(history: List<WatchRecord>, requestVersion: Long): Map<String, BookSummary> {
        val books = linkedMapOf<String, BookSummary>()
        history.asSequence()
            .map { it.bookId }
            .distinct()
            .take(2)
            .forEach { bookId ->
                if (requestVersion != accountRequestVersion || state.value.screen != AppScreen.ACCOUNT) {
                    return books
                }
                try {
                    books[bookId] = dataSource.loadBook(bookId)
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Throwable) {
                }
            }
        return books
    }

    private fun resumeEpisodeForRecord(episodes: List<EpisodeSummary>, record: WatchRecord): EpisodeSummary? {
        val currentIndex = episodes.indexOfFirst { it.number == record.episode }
        if (currentIndex < 0) {
            return null
        }
        if (record.progressPercent < 100) {
            return episodes[currentIndex]
        }
        return episodes.getOrNull(currentIndex + 1) ?: episodes[currentIndex]
    }

    private fun playbackReturnScreenFor(screen: AppScreen): AppScreen =
        when (screen) {
            AppScreen.SEARCH -> AppScreen.SEARCH
            AppScreen.FAVORITES -> AppScreen.FAVORITES
            AppScreen.ACCOUNT -> AppScreen.ACCOUNT
            else -> AppScreen.HOME
        }

    private fun playbackFallbackEpisode(
        episodes: List<EpisodeSummary>,
        fallbackEpisode: EpisodeSummary?,
    ): EpisodeSummary? {
        if (episodes.isEmpty()) {
            return fallbackEpisode
        }
        if (fallbackEpisode == null) {
            return episodes.firstOrNull()
        }
        return episodes.firstOrNull {
            it.number == fallbackEpisode.number && it.chapterId == fallbackEpisode.chapterId
        } ?: episodes.firstOrNull()
    }

    private fun shouldPromptForLogin(error: Throwable, context: ErrorContext): Boolean =
        error is ApiClientException &&
            error.statusCode == 401 &&
            (context == ErrorContext.ACCOUNT || context == ErrorContext.PLAYBACK || context == ErrorContext.SOCIAL)

    private fun isBlockedAccountError(error: Throwable): Boolean {
        val message = error.message?.lowercase().orEmpty()
        return error is ApiClientException &&
            error.statusCode == 403 &&
            ("disabled" in message || "blacklisted" in message || "user status" in message)
    }

    private fun userFacingErrorMessage(error: Throwable, context: ErrorContext): String {
        val rawMessage = error.message?.trim().orEmpty()
        val normalizedMessage = rawMessage.lowercase()
        if (isBlockedAccountError(error)) {
            return "账号不可操作，请联系支持。"
        }
        if (context == ErrorContext.CONTENT) {
            return "内容暂时加载失败，可以稍后刷新。"
        }
        if (error is ApiClientException && error.statusCode == 401 && (context == ErrorContext.LOGIN || context == ErrorContext.REGISTER)) {
            return "手机号或密码错误"
        }
        if (error is ApiClientException && error.statusCode == 401) {
            return "登录状态已失效，请重新登录。"
        }
        if ((context == ErrorContext.LOGIN || context == ErrorContext.REGISTER) &&
            ("invalid username or password" in normalizedMessage || "invalid credentials" in normalizedMessage)
        ) {
            return "手机号或密码错误"
        }
        return rawMessage.ifBlank { error::class.simpleName ?: "请求失败" }
    }

    private fun smsSentMessage(): String =
        when (state.value.language) {
            AppLanguage.ENGLISH -> "Verification code sent. Use 000000 within 120 seconds."
            AppLanguage.TRADITIONAL_CHINESE -> "驗證碼已發送，請在 120 秒內使用 000000。"
        }

    private fun registrationSimulationMessage(): String =
        when (state.value.language) {
            AppLanguage.ENGLISH -> "Registration completed. Account access must be opened internally."
            AppLanguage.TRADITIONAL_CHINESE -> "註冊流程已完成，帳號需要內部開通後才能登入。"
        }

    private fun passwordChangedMessage(): String =
        when (state.value.language) {
            AppLanguage.ENGLISH -> "Password changed."
            AppLanguage.TRADITIONAL_CHINESE -> "密碼已修改。"
        }

    private data class SessionPhone(val countryCode: String, val phoneNumber: String)

    private enum class ErrorContext {
        LOGIN,
        REGISTER,
        CONTENT,
        ACCOUNT,
        PLAYBACK,
        SOCIAL,
        DIAGNOSTIC,
    }

    private companion object {
        val supportedPhoneCountryCodes = listOf(
            "+852",
            "+853",
            "+886",
            "+44",
            "+61",
            "+65",
            "+81",
            "+82",
            "+60",
            "+1",
        )
    }
}
