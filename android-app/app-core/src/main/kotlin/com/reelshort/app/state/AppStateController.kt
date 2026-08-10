package com.reelshort.app.state

import com.reelshort.app.data.AppDataSource
import com.reelshort.app.data.ApiHealthStatus
import com.reelshort.app.data.AppLanguage
import com.reelshort.app.data.AuthSession
import com.reelshort.app.data.BookInteractionState
import com.reelshort.app.data.BookSummary
import com.reelshort.app.data.Comment
import com.reelshort.app.data.EpisodeSummary
import com.reelshort.app.data.PointAccount
import com.reelshort.app.data.SavedCredentials
import com.reelshort.app.data.WatchRecord
import com.reelshort.app.data.WatchProgressReport
import com.reelshort.app.data.requiresVip
import com.reelshort.app.network.ApiClientException
import com.reelshort.app.network.GeoIpClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex

class AppStateController(private val dataSource: AppDataSource) {
    private val mutableState = MutableStateFlow(AppUiState(apiBaseUrl = dataSource.apiBaseUrl))
    private var rewardReportInFlight = false
    private var pendingCompletionReport: PendingCompletionReport? = null
    private var sessionRestored = false
    private var geoChecked = false
    private var searchRequestVersion = 0L
    private var playbackRequestVersion = 0L
    private var pendingVipEpisodeRequestVersion: Long? = null
    private var vipPaywallGeneration = 0L
    private var accountRequestVersion = 0L
    private var favoritesRequestVersion = 0L
    private val accountOperationMutex = Mutex()
    private val vipPaymentRefreshMutex = Mutex()

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
                    language = language,
                    session = session,
                    savedCredentials = savedCredentials,
                    homeShelf = emptyList(),
                    isLoading = false,
                    errorMessage = "内容暂时加载失败，可以稍后刷新。",
                    messageType = UiMessageType.ERROR,
                )
            }
        }
    }

    /**
     * Check the user's IP geo-location once at startup. If mainland China is detected, set
     * [AppUiState.geoBlocked] to block the app. Fail-open: any error leaves the app usable.
     */
    suspend fun checkGeoRestriction() {
        if (geoChecked) return
        geoChecked = true
        try {
            val countryCode = dataSource.checkGeoIp()
            if (countryCode != null && GeoIpClient.BLOCKED_COUNTRIES.contains(countryCode.uppercase())) {
                mutableState.update { it.copy(geoBlocked = true) }
            }
        }
        catch (_: Exception) {
            // Fail-open: geo-IP check failure should not block the app
        }
    }

    /**
     * Force-set geo-blocked state (used when local device checks indicate China).
     */
    fun setGeoBlocked() {
        if (geoChecked) return
        geoChecked = true
        mutableState.update { it.copy(geoBlocked = true) }
    }

    suspend fun loadSavedCredentials() = runWithLoading(ErrorContext.DIAGNOSTIC) {
        val savedCredentials = dataSource.loadSavedCredentials()
        mutableState.update {
            it.copy(savedCredentials = savedCredentials, isLoading = false)
        }
    }

    suspend fun login(
        username: String,
        password: String,
        rememberPassword: Boolean = false,
    ) = runWithLoading(ErrorContext.LOGIN) {
        val session = dataSource.login(username, password)
        updateSavedCredentials(username, password, rememberPassword)
        openHomeAfterAuthentication(session)
    }

    suspend fun register(
        username: String,
        password: String,
        captchaId: String,
        captchaAnswer: String,
    ) = runWithLoading(ErrorContext.REGISTER) {
        val session = dataSource.register(username, password, captchaId, captchaAnswer)
        updateSavedCredentials(username, password, rememberPassword = false)
        mutableState.update {
            it.copy(
                isLoading = false,
                authPromptVisible = false,
                captchaId = "",
                captchaImageBase64 = "",
                errorMessage = null,
            )
        }
        openHomeAfterAuthentication(session)
    }

    suspend fun fetchCaptcha() = runWithLoading(ErrorContext.REGISTER) {
        val challenge = dataSource.fetchCaptcha()
        mutableState.update {
            it.copy(
                isLoading = false,
                captchaId = challenge.captchaId,
                captchaImageBase64 = challenge.imageBase64,
            )
        }
    }

    private suspend fun updateSavedCredentials(
        username: String,
        password: String,
        rememberPassword: Boolean,
    ) {
        if (rememberPassword) {
            val credentials = SavedCredentials(
                username = username,
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

    private suspend fun openHomeAfterAuthentication(session: AuthSession) {
        val pendingVipEpisode = state.value.pendingVipEpisode
        if (pendingVipEpisode != null) {
            mutableState.update {
                it.copy(
                    session = session,
                    authPromptVisible = false,
                    pendingPlaybackEpisode = null,
                    isLoading = false,
                    errorMessage = null,
                )
            }
            openVipPaywall(
                episode = pendingVipEpisode,
                requestVersion = pendingVipEpisodeRequestVersion,
            )
            return
        }
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
                mutableState.update { it.copy(isLoading = false, errorMessage = "内容暂时加载失败，可以稍后刷新。", messageType = UiMessageType.ERROR) }
                return
            }
            requestEpisodePlayback(book, episode, requestVersion)
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
                messageType = if (homeShelf == null) UiMessageType.ERROR else null,
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
        mutableState.update { it.copy(screen = AppScreen.HOME, isHomeRefreshing = true, errorMessage = null, messageType = null) }
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
                    messageType = UiMessageType.ERROR,
                )
            }
        }
    }

    suspend fun openHome() {
        mutableState.update { it.copy(screen = AppScreen.HOME, errorMessage = null, messageType = null) }
        if (state.value.homeShelf.isEmpty()) {
            refreshHome()
            return
        }

        refreshHomeSilently()
    }

    private suspend fun refreshHomeSilently() {
        try {
            val homeShelf = dataSource.loadHomeShelf()
            mutableState.update {
                it.copy(
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
        mutableState.update { it.copy(screen = AppScreen.SEARCH, errorMessage = null, messageType = null) }
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
                    messageType = UiMessageType.ERROR,
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
                    messageType = UiMessageType.ERROR,
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
                    pendingPlaybackEpisode = if (firstEpisode.requiresVip()) null else firstEpisode,
                    pendingVipEpisode = if (firstEpisode.requiresVip()) firstEpisode else null,
                    isLoading = false,
                )
            }
            pendingVipEpisodeRequestVersion = if (firstEpisode.requiresVip()) requestVersion else null
            return@runWithLoading
        }
        val resumeEpisode = chooseResumeEpisode(book, episodes, firstEpisode, requestVersion) ?: firstEpisode
        if (!isActivePlaybackRequest(requestVersion)) {
            return@runWithLoading
        }
        requestEpisodePlayback(book, resumeEpisode, requestVersion)
    }

    suspend fun openPlayer(episode: EpisodeSummary) = runWithLoading(ErrorContext.PLAYBACK) {
        val book = requireSelectedBook()
        val requestVersion = ++playbackRequestVersion
        if (state.value.session == null) {
            mutableState.update {
                it.copy(
                    authPromptVisible = true,
                    pendingPlaybackEpisode = if (episode.requiresVip()) null else episode,
                    pendingVipEpisode = if (episode.requiresVip()) episode else null,
                    isLoading = false,
                )
            }
            pendingVipEpisodeRequestVersion = if (episode.requiresVip()) requestVersion else null
            return@runWithLoading
        }
        requestEpisodePlayback(book, episode, requestVersion)
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
                    messageType = UiMessageType.ERROR,
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
        requestEpisodePlayback(book, episode, requestVersion)
    }

    private suspend fun requestEpisodePlayback(
        book: BookSummary,
        episode: EpisodeSummary,
        requestVersion: Long,
    ) {
        if (!isActivePlaybackRequest(requestVersion)) return
        val pointAccount = state.value.pointAccount ?: loadPointAccountSilently()
        if (!isActivePlaybackRequest(requestVersion)) return
        if (episode.requiresVip()) {
            if (pointAccount?.vipActive != true) {
                openVipPaywall(
                    episode = episode,
                    requestVersion = requestVersion,
                )
                return
            }
        }
        try {
            openPlayerForAuthenticatedUser(book, episode, requestVersion)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            if (isActivePlaybackRequest(requestVersion) && isVipRequiredError(error)) {
                openVipPaywall(
                    episode = episode,
                    requestVersion = requestVersion,
                    allowImmediateResume = false,
                )
                return
            }
            throw error
        }
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
        val playbackSnapshot = snapshot?.let {
            if (it.progressPercent >= 100) it.copy(positionSeconds = 0, progressPercent = 0) else it
        }
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
                    .let { playback ->
                        playbackSnapshot?.let(playback::withSnapshot)
                            ?: playback.withReportedProgress(initialPosition, 0)
                    },
                interaction = interaction,
                comments = comments,
                isLoading = false,
                authPromptVisible = false,
                pendingPlaybackEpisode = null,
                pendingVipEpisode = null,
                errorMessage = if (snapshot == null) "积分状态暂时加载失败，播放不受影响。" else null,
            )
        }
        pendingVipEpisodeRequestVersion = null
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
                playback = it.playback.withProgressReport(progress),
                isLoading = false,
            )
        }
    }

    suspend fun reportProgressSilently(positionSeconds: Int, durationSeconds: Int) {
        val current = state.value
        val playback = current.playback
        val requestVersion = playbackRequestVersion
        if (playback.status != PlaybackStatus.READY) {
            return
        }
        if (positionSeconds <= 0 || durationSeconds <= 0) {
            return
        }
        val localProgress = playback.withPosition(positionSeconds, durationSeconds)
        if (rewardReportInFlight) {
            mutableState.update { it.copy(playback = localProgress) }
            if (localProgress.progressPercent >= 100 && !playback.rewardClaimed) {
                pendingCompletionReport = PendingCompletionReport(requestVersion, positionSeconds, durationSeconds)
            }
            return
        }
        if (!shouldPersistPlaybackProgress(
                positionSeconds = localProgress.positionSeconds,
                durationSeconds = localProgress.durationSeconds,
                lastReportedPositionSeconds = playback.lastReportedPositionSeconds,
                rewardClaimed = playback.rewardClaimed,
            )
        ) {
            mutableState.update { it.copy(playback = localProgress) }
            return
        }
        val book = current.selectedBook ?: return
        val episode = current.selectedEpisode ?: return

        rewardReportInFlight = true
        val isRewardAttempt = localProgress.progressPercent >= 100 && !playback.rewardClaimed
        mutableState.update {
            it.copy(playback = localProgress.withRewardReporting(isRewardAttempt))
        }
        try {
            val progress = dataSource.reportWatchProgress(book, episode, positionSeconds, durationSeconds)
            if (!isActivePlaybackRequest(requestVersion)) {
                return
            }
            mutableState.update {
                it.copy(
                    watchHistory = upsertWatchHistory(it.watchHistory, progress),
                    playback = it.playback.withProgressReport(progress).withRewardReporting(false),
                )
            }
            if (progress.awardedPoints > 0) {
                try {
                    val points = dataSource.loadPointAccount()
                    if (isActivePlaybackRequest(requestVersion)) {
                        mutableState.update { it.copy(pointAccount = points) }
                    }
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Throwable) {
                }
            }
        } catch (error: CancellationException) {
            if (isActivePlaybackRequest(requestVersion)) {
                mutableState.update { it.copy(playback = it.playback.withRewardReporting(false)) }
            }
            throw error
        } catch (_: Throwable) {
            if (isActivePlaybackRequest(requestVersion)) {
                mutableState.update {
                    it.copy(
                        playback = if (isRewardAttempt) {
                            it.playback.withPosition(positionSeconds, durationSeconds).withRewardReportError()
                        } else {
                            it.playback.withPosition(positionSeconds, durationSeconds).withRewardReporting(false)
                        },
                    )
                }
            }
        } finally {
            rewardReportInFlight = false
            val pending = pendingCompletionReport
            pendingCompletionReport = null
            // M2: 用 NonCancellable 包裹，防止 finally 内的 suspend 调用在协程取消时
            // 再次抛出 CancellationException 覆盖原始异常。限制递归深度为 1 次。
            if (pending != null && isActivePlaybackRequest(pending.requestVersion)) {
                kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                    reportProgressSilently(pending.positionSeconds, pending.durationSeconds)
                }
            }
        }
    }

    private fun upsertWatchHistory(history: List<WatchRecord>, progress: WatchProgressReport): List<WatchRecord> {
        val updated = WatchRecord(
            bookId = progress.bookId,
            bookTitle = progress.bookTitle,
            episode = progress.episode,
            progressPercent = progress.progressPercent,
        )
        val withoutCurrent = history.filterNot { it.bookId == progress.bookId && it.episode == progress.episode }
        return listOf(updated) + withoutCurrent
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

    fun backToFunds() {
        mutableState.update { it.copy(screen = AppScreen.FUNDS, isLoading = false, errorMessage = null) }
    }

    private suspend fun runSocialAction(block: suspend () -> Unit) {
        mutableState.update { it.copy(errorMessage = null, messageType = null) }
        try {
            block()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            mutableState.update {
                it.copy(
                    errorMessage = userFacingErrorMessage(error, ErrorContext.SOCIAL),
                    messageType = UiMessageType.ERROR,
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

    suspend fun openFunds() = openAccountSection(AppScreen.FUNDS)

    suspend fun openWithdrawal() = openAccountSection(AppScreen.WITHDRAWAL)

    suspend fun openVipRecharge() = openAccountSection(AppScreen.VIP_RECHARGE)

    private suspend fun openAccountSection(targetScreen: AppScreen) {
        if (state.value.session == null) {
            mutableState.update { it.copy(screen = targetScreen, errorMessage = null, isLoading = false) }
            return
        }
        if (!hasAccountSnapshot(state.value)) {
            val requestVersion = ++accountRequestVersion
            mutableState.update { it.copy(screen = targetScreen) }
            loadAccountSnapshot(requestVersion, targetScreen)
            return
        }

        val requestVersion = ++accountRequestVersion
        mutableState.update { it.copy(screen = targetScreen, errorMessage = null) }
        refreshAccountSilently(requestVersion)
    }

    private suspend fun loadAccountSnapshot(requestVersion: Long, targetScreen: AppScreen = AppScreen.ACCOUNT) = runWithLoading(ErrorContext.ACCOUNT) {
        loadAccountSnapshotForAuthenticatedUser(requestVersion, targetScreen)
    }

    private suspend fun loadAccountSnapshotForAuthenticatedUser(
        requestVersion: Long,
        targetScreen: AppScreen = AppScreen.ACCOUNT,
    ) = coroutineScope {
        // Parallelize all network requests to avoid 5+ second sequential loading
        val historyAsync = async { dataSource.loadWatchHistory() }
        val pointsAsync = async { dataSource.loadPointAccount() }
        val ordersAsync = async { dataSource.loadOrders() }
        val walletAsync = async { loadOptionalAccountData(null) { dataSource.loadWallet() } }
        val vipOrdersAsync = async { loadOptionalAccountData(emptyList()) { dataSource.loadVipOrders() } }
        val withdrawalSummaryAsync = async { loadOptionalAccountData(null) { dataSource.loadWithdrawalSummary() } }
        val withdrawalsAsync = async { loadOptionalAccountData(emptyList()) { dataSource.loadWithdrawals() } }

        val history = historyAsync.await()
        val points = pointsAsync.await()
        val orders = ordersAsync.await()
        val wallet = walletAsync.await()
        val vipOrders = vipOrdersAsync.await()
        val withdrawalSummary = withdrawalSummaryAsync.await()
        val withdrawals = withdrawalsAsync.await()
        if (requestVersion != accountRequestVersion || !isAccountAreaScreen(state.value.screen)) {
            return@coroutineScope
        }
        val continueWatchingBooks = loadContinueWatchingBooks(history, requestVersion)
        if (requestVersion != accountRequestVersion || !isAccountAreaScreen(state.value.screen)) {
            return@coroutineScope
        }
        mutableState.update {
            it.copy(
                screen = targetScreen,
                watchHistory = history,
                continueWatchingBooks = continueWatchingBooks,
                pointAccount = points,
                vipActive = points.vipActive,
                orders = orders,
                wallet = wallet,
                vipUntil = if (points.vipActive) points.vipUntil ?: wallet?.vipUntil else null,
                vipPriceUsdt = wallet?.vipPriceUsdt ?: it.vipPriceUsdt,
                vipCollectionAddress = wallet?.vipCollectionAddress ?: it.vipCollectionAddress,
                vipOrders = vipOrders,
                withdrawalSummary = withdrawalSummary,
                withdrawals = withdrawals,
                isLoading = false,
            )
        }
    }

    private suspend fun refreshAccountSilently(requestVersion: Long = accountRequestVersion) {
        val targetScreen = state.value.screen.takeIf(::isAccountAreaScreen) ?: AppScreen.ACCOUNT
        try {
            val history = dataSource.loadWatchHistory()
            if (requestVersion != accountRequestVersion || !isAccountAreaScreen(state.value.screen)) {
                return
            }
            val points = dataSource.loadPointAccount()
            if (requestVersion != accountRequestVersion || !isAccountAreaScreen(state.value.screen)) {
                return
            }
            val orders = dataSource.loadOrders()
            if (requestVersion != accountRequestVersion || !isAccountAreaScreen(state.value.screen)) {
                return
            }
            val wallet = loadOptionalAccountData(null) { dataSource.loadWallet() }
            if (requestVersion != accountRequestVersion || !isAccountAreaScreen(state.value.screen)) {
                return
            }
            val withdrawalSummary = loadOptionalAccountData(null) { dataSource.loadWithdrawalSummary() }
            if (requestVersion != accountRequestVersion || !isAccountAreaScreen(state.value.screen)) {
                return
            }
            val withdrawals = loadOptionalAccountData(emptyList()) { dataSource.loadWithdrawals() }
            if (requestVersion != accountRequestVersion || !isAccountAreaScreen(state.value.screen)) {
                return
            }
            if (requestVersion != accountRequestVersion || !isAccountAreaScreen(state.value.screen)) {
                return
            }
            val continueWatchingBooks = loadContinueWatchingBooks(history, requestVersion)
            if (requestVersion != accountRequestVersion || !isAccountAreaScreen(state.value.screen)) {
                return
            }
            mutableState.update {
                it.copy(
                    screen = targetScreen,
                    watchHistory = history,
                    continueWatchingBooks = continueWatchingBooks,
                    pointAccount = points,
                    vipActive = points.vipActive,
                    orders = orders,
                    wallet = wallet,
                    vipUntil = if (points.vipActive) points.vipUntil ?: wallet?.vipUntil else null,
                    vipPriceUsdt = wallet?.vipPriceUsdt ?: it.vipPriceUsdt,
                    withdrawalSummary = withdrawalSummary,
                    withdrawals = withdrawals,
                )
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
        }
    }

    suspend fun bindWallet(network: String, walletAddress: String, password: String) = runAccountOperation(AccountOperation.WALLET_MUTATION) {
        requireAuthenticatedAccount()
        dataSource.bindWallet(network, walletAddress, password)
        reloadAccountSnapshotAfterAction(walletUpdatedMessage(), walletMutationCompleted = true)
    }

    suspend fun unbindWallet(password: String) = runAccountOperation(AccountOperation.WALLET_MUTATION) {
        requireAuthenticatedAccount()
        dataSource.unbindWallet(password)
        reloadAccountSnapshotAfterAction(walletUpdatedMessage(), walletMutationCompleted = true)
    }

    suspend fun createVipOrder() = runAccountOperation(
        operation = AccountOperation.WALLET_MUTATION,
        showGlobalLoading = false,
    ) {
        requireAuthenticatedAccount()
        val generation = vipPaywallGeneration
        val createdOrder = dataSource.createVipOrder()
        if (generation != vipPaywallGeneration) {
            return@runAccountOperation
        }
        mutableState.update { it.copy(latestVipOrder = createdOrder) }
        val refreshed = refreshVipPaymentState(
            autoResume = true,
            generation = generation,
            waitForInFlight = true,
        )
        if (refreshed) {
            mutableState.update {
                it.copy(
                    isLoading = false,
                    errorMessage = vipOrderCreatedMessage(),
                    messageType = UiMessageType.SUCCESS,
                )
            }
        }
    }

    suspend fun refreshLatestVipOrder() = runWithoutLoading(ErrorContext.ACCOUNT) {
        requireAuthenticatedAccount()
        refreshVipPaymentState(
            autoResume = true,
            generation = vipPaywallGeneration,
        )
    }

    suspend fun showVipPaywall() = runWithoutLoading(ErrorContext.ACCOUNT) {
        requireAuthenticatedAccount()
        openVipPaywall(episode = null)
    }

    fun dismissVipPaywall() {
        mutableState.update {
            it.copy(
                vipPaywallVisible = false,
                vipPaywallLoading = false,
                pendingVipEpisode = null,
            )
        }
        pendingVipEpisodeRequestVersion = null
        vipPaywallGeneration++
    }

    suspend fun submitBankCard(holderName: String, cardNumber: String, expiryMonth: String, expiryYear: String, cvv: String) = runWithLoading(ErrorContext.ACCOUNT) {
        requireAuthenticatedAccount()
        dataSource.submitBankCard(holderName, cardNumber, expiryMonth, expiryYear, cvv)
    }

    suspend fun submitWithdrawal(pointAmount: Int) = runAccountOperation(AccountOperation.WITHDRAWAL) {
        requireAuthenticatedAccount()
        dataSource.submitWithdrawal(pointAmount)
        reloadAccountSnapshotAfterAction(withdrawalSubmittedMessage(), withdrawalSubmitted = true)
    }

    suspend fun changePassword(oldPassword: String, newPassword: String) =
        runAccountOperation(AccountOperation.PASSWORD_CHANGE) {
            requireAuthenticatedAccount()
            dataSource.changePassword(oldPassword, newPassword)
            dataSource.clearSession()
            dataSource.clearSavedCredentials()
            mutableState.update {
                it.copy(
                    isLoading = false,
                    session = null,
                    savedCredentials = null,
                    pointAccount = null,
                    orders = emptyList(),
                    wallet = null,
                    vipActive = false,
                    vipUntil = null,
                    vipPaywallVisible = false,
                    vipPaywallLoading = false,
                    pendingVipEpisode = null,
                    latestVipOrder = null,
                    vipOrders = emptyList(),
                    withdrawalSummary = null,
                    withdrawals = emptyList(),
                    watchHistory = emptyList(),
                    continueWatchingBooks = emptyMap(),
                    errorMessage = passwordChangedMessage(),
                    messageType = UiMessageType.SUCCESS,
                )
            }
            pendingVipEpisodeRequestVersion = null
            vipPaywallGeneration++
        }

    fun clearError() {
        mutableState.update { it.copy(errorMessage = null, messageType = null) }
    }

    fun showAuthPrompt() {
        mutableState.update { it.copy(authPromptVisible = true) }
    }

    fun showRegisterAuthMode() {
        mutableState.update { it.copy(authMode = AuthMode.REGISTER) }
    }

    fun showLoginAuthMode() {
        mutableState.update { it.copy(authMode = AuthMode.LOGIN) }
    }

    fun dismissAuthPrompt() {
        mutableState.update {
            it.copy(
                authPromptVisible = false,
                pendingPlaybackEpisode = null,
                pendingVipEpisode = null,
                authMode = AuthMode.LOGIN,
                captchaId = "",
                captchaImageBase64 = "",
            )
        }
        pendingVipEpisodeRequestVersion = null
        vipPaywallGeneration++
    }

    suspend fun checkApiHealth() {
        mutableState.update { it.copy(isLoading = true, errorMessage = null, messageType = null) }
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
        pendingVipEpisodeRequestVersion = null
        vipPaywallGeneration++
    }

    private suspend fun runWithLoading(errorContext: ErrorContext, block: suspend () -> Unit) {
        mutableState.update { it.copy(isLoading = true, errorMessage = null, messageType = null) }
        try {
            block()
        } catch (error: CancellationException) {
            mutableState.update { it.copy(isLoading = false) }
            throw error
        } catch (error: Throwable) {
            handleRequestError(error, errorContext)
        }
    }

    private suspend fun runWithoutLoading(errorContext: ErrorContext, block: suspend () -> Unit) {
        try {
            block()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            handleRequestError(error, errorContext)
        }
    }

    private suspend fun handleRequestError(error: Throwable, errorContext: ErrorContext) {
        val blockedAccount = isBlockedAccountError(error)
        val paywallAuthenticationFailed = state.value.vipPaywallVisible &&
            error is ApiClientException &&
            error.statusCode == 401
        val clearVipPaymentState = blockedAccount || paywallAuthenticationFailed
        if (blockedAccount) {
            dataSource.clearSession()
        }
        mutableState.update {
            it.copy(
                session = if (blockedAccount) null else it.session,
                isLoading = false,
                errorMessage = userFacingErrorMessage(error, errorContext),
                messageTrigger = it.messageTrigger + 1,
                messageType = UiMessageType.ERROR,
                authPromptVisible = if (blockedAccount) {
                    false
                } else if (shouldPromptForLogin(error, errorContext)) {
                    true
                } else {
                    it.authPromptVisible
                },
                pendingPlaybackEpisode = if (blockedAccount) null else it.pendingPlaybackEpisode,
                pointAccount = if (clearVipPaymentState) null else it.pointAccount,
                vipActive = if (clearVipPaymentState) false else it.vipActive,
                vipUntil = if (clearVipPaymentState) null else it.vipUntil,
                vipPaywallVisible = if (clearVipPaymentState) false else it.vipPaywallVisible,
                vipPaywallLoading = if (clearVipPaymentState) false else it.vipPaywallLoading,
                pendingVipEpisode = if (clearVipPaymentState) null else it.pendingVipEpisode,
                latestVipOrder = if (clearVipPaymentState) null else it.latestVipOrder,
                vipOrders = if (clearVipPaymentState) emptyList() else it.vipOrders,
            )
        }
        if (clearVipPaymentState) {
            pendingVipEpisodeRequestVersion = null
            vipPaywallGeneration++
        }
    }

    private suspend fun runAccountOperation(
        operation: AccountOperation,
        showGlobalLoading: Boolean = true,
        block: suspend () -> Unit,
    ) {
        if (!accountOperationMutex.tryLock()) {
            return
        }
        mutableState.update { it.copy(accountOperation = operation) }
        try {
            if (showGlobalLoading) {
                runWithLoading(ErrorContext.ACCOUNT, block)
            } else {
                runWithoutLoading(ErrorContext.ACCOUNT, block)
            }
        } finally {
            mutableState.update { it.copy(accountOperation = null) }
            accountOperationMutex.unlock()
        }
    }

    private suspend fun loadPointAccountSilently(): PointAccount? {
        val pointAccount = try {
            dataSource.loadPointAccount()
        } catch (error: CancellationException) {
            throw error
        } catch (error: ApiClientException) {
            if (error.statusCode == 401 || error.statusCode == 403) {
                throw error
            }
            null
        } catch (_: Throwable) {
            null
        }
        if (pointAccount != null) {
            mutableState.update {
                it.copy(
                    pointAccount = pointAccount,
                    vipActive = pointAccount.vipActive,
                    vipUntil = if (pointAccount.vipActive) pointAccount.vipUntil else null,
                )
            }
        }
        return pointAccount
    }

    private suspend fun openVipPaywall(
        episode: EpisodeSummary?,
        requestVersion: Long? = null,
        allowImmediateResume: Boolean = true,
    ) {
        val generation = ++vipPaywallGeneration
        pendingVipEpisodeRequestVersion = requestVersion
        mutableState.update {
            it.copy(
                vipPaywallVisible = true,
                vipPaywallLoading = true,
                pendingVipEpisode = episode,
                isLoading = false,
                errorMessage = null,
                messageType = null,
            )
        }
        refreshVipPaymentState(
            autoResume = allowImmediateResume && episode != null,
            generation = generation,
            waitForInFlight = true,
        )
    }

    private suspend fun refreshVipPaymentState(
        autoResume: Boolean,
        generation: Long,
        waitForInFlight: Boolean = false,
    ): Boolean {
        if (waitForInFlight) {
            vipPaymentRefreshMutex.lock()
        } else if (!vipPaymentRefreshMutex.tryLock()) {
            return true
        }
        if (generation == vipPaywallGeneration) {
            mutableState.update { it.copy(vipPaywallLoading = true) }
        }
        var episodeToResume: EpisodeSummary? = null
        val refreshed = try {
            val pointAccount = dataSource.loadPointAccount()
            val latest = dataSource.loadLatestVipOrder()
            val wallet = loadOptionalAccountData(state.value.wallet) { dataSource.loadWallet() }
            val vipOrders = loadOptionalAccountData(state.value.vipOrders) { dataSource.loadVipOrders() }
            if (generation != vipPaywallGeneration) {
                false
            } else {
                val pendingEpisode = state.value.pendingVipEpisode
                val pendingRequestVersion = pendingVipEpisodeRequestVersion
                mutableState.update {
                    it.copy(
                        pointAccount = pointAccount,
                        vipActive = pointAccount.vipActive,
                        vipUntil = if (pointAccount.vipActive) pointAccount.vipUntil ?: wallet?.vipUntil else null,
                        wallet = wallet,
                        vipPriceUsdt = wallet?.vipPriceUsdt ?: it.vipPriceUsdt,
                        vipCollectionAddress = wallet?.vipCollectionAddress ?: it.vipCollectionAddress,
                        latestVipOrder = latest,
                        vipOrders = vipOrders,
                        vipPaywallLoading = false,
                        isLoading = false,
                    )
                }
                if (autoResume && pointAccount.vipActive) {
                    episodeToResume = pendingEpisode?.takeIf {
                        pendingRequestVersion == playbackRequestVersion
                    }
                    mutableState.update {
                        it.copy(
                            vipPaywallVisible = false,
                            vipPaywallLoading = false,
                            pendingVipEpisode = null,
                        )
                    }
                    pendingVipEpisodeRequestVersion = null
                }
                true
            }
        } catch (error: CancellationException) {
            if (generation == vipPaywallGeneration) {
                mutableState.update { it.copy(vipPaywallLoading = false, isLoading = false) }
            }
            throw error
        } catch (error: ApiClientException) {
            if (generation != vipPaywallGeneration) {
                false
            } else {
                mutableState.update { it.copy(vipPaywallLoading = false, isLoading = false) }
                if (error.statusCode == 401 || error.statusCode == 403) {
                    throw error
                }
                mutableState.update {
                    it.copy(
                        errorMessage = userFacingErrorMessage(error, ErrorContext.ACCOUNT),
                        messageTrigger = it.messageTrigger + 1,
                        messageType = UiMessageType.ERROR,
                    )
                }
                false
            }
        } catch (error: Throwable) {
            if (generation != vipPaywallGeneration) {
                false
            } else {
                mutableState.update {
                    it.copy(
                        vipPaywallLoading = false,
                        isLoading = false,
                        errorMessage = userFacingErrorMessage(error, ErrorContext.ACCOUNT),
                        messageTrigger = it.messageTrigger + 1,
                        messageType = UiMessageType.ERROR,
                    )
                }
                false
            }
        } finally {
            vipPaymentRefreshMutex.unlock()
        }
        episodeToResume?.let { openPlayer(it) }
        return refreshed
    }

    private fun requireSelectedBook(): BookSummary =
        state.value.selectedBook ?: throw IllegalStateException("未选择剧集")

    private fun isActivePlaybackRequest(requestVersion: Long): Boolean =
        requestVersion == playbackRequestVersion

    private fun hasAccountSnapshot(state: AppUiState): Boolean =
        state.pointAccount != null || state.watchHistory.isNotEmpty() || state.orders.isNotEmpty() || state.wallet != null

    private fun isAccountAreaScreen(screen: AppScreen): Boolean =
        screen == AppScreen.ACCOUNT || screen == AppScreen.FUNDS ||
            screen == AppScreen.WITHDRAWAL || screen == AppScreen.VIP_RECHARGE

    private fun requireAuthenticatedAccount() {
        if (state.value.session == null) {
            mutableState.update { it.copy(authPromptVisible = true, isLoading = false) }
            throw IllegalStateException("登录状态已失效，请重新登录。")
        }
    }

    private suspend fun reloadAccountSnapshotAfterAction(
        successMessage: String,
        walletMutationCompleted: Boolean = false,
        withdrawalSubmitted: Boolean = false,
    ) {
        val requestVersion = ++accountRequestVersion
        val targetScreen = state.value.screen.takeIf(::isAccountAreaScreen) ?: AppScreen.ACCOUNT
        mutableState.update { it.copy(screen = targetScreen) }
        try {
            loadAccountSnapshotForAuthenticatedUser(requestVersion, targetScreen)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
        }
        if (requestVersion == accountRequestVersion && isAccountAreaScreen(state.value.screen)) {
            mutableState.update {
                it.copy(
                    errorMessage = successMessage,
                    messageType = UiMessageType.SUCCESS,
                    isLoading = false,
                    walletMutationVersion = if (walletMutationCompleted) {
                        it.walletMutationVersion + 1
                    } else {
                        it.walletMutationVersion
                    },
                    withdrawalSubmissionVersion = if (withdrawalSubmitted) {
                        it.withdrawalSubmissionVersion + 1
                    } else {
                        it.withdrawalSubmissionVersion
                    },
                )
            }
        }
    }

    private data class PendingCompletionReport(
        val requestVersion: Long,
        val positionSeconds: Int,
        val durationSeconds: Int,
    )

    private suspend fun <T> loadOptionalAccountData(fallback: T, loader: suspend () -> T): T =
        try {
            loader()
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            fallback
        }

    private suspend fun loadContinueWatchingBooks(history: List<WatchRecord>, requestVersion: Long): Map<String, BookSummary> {
        val books = linkedMapOf<String, BookSummary>()
        history.asSequence()
            .map { it.bookId }
            .distinct()
            .take(2)
            .forEach { bookId ->
                if (requestVersion != accountRequestVersion || !isAccountAreaScreen(state.value.screen)) {
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

    private fun isVipRequiredError(error: Throwable): Boolean =
        error is ApiClientException &&
            error.statusCode == 403 &&
            error.message.contains("VIP required", ignoreCase = true)

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
        if (context == ErrorContext.REGISTER &&
            ("captcha" in normalizedMessage || "invalid verification code" in normalizedMessage)
        ) {
            return invalidCaptchaMessage()
        }
        if (context == ErrorContext.REGISTER &&
            error is ApiClientException &&
            error.statusCode == 400 &&
            "bad request" in normalizedMessage
        ) {
            return registerBadRequestMessage()
        }
        if (error is ApiClientException && error.statusCode == 401 && (context == ErrorContext.LOGIN || context == ErrorContext.REGISTER)) {
            return credentialErrorMessage()
        }
        if (error is ApiClientException && error.statusCode == 409 && context == ErrorContext.REGISTER) {
            return usernameExistsMessage()
        }
        if (error is ApiClientException && error.statusCode == 401) {
            return "登录状态已失效，请重新登录。"
        }
        if ((context == ErrorContext.LOGIN || context == ErrorContext.REGISTER) &&
            ("invalid username or password" in normalizedMessage || "invalid credentials" in normalizedMessage)
        ) {
            return credentialErrorMessage()
        }
        return rawMessage.ifBlank { error::class.simpleName ?: "请求失败" }
    }

    // M1: 所有 when(language) 加 else 分支，防止新增语言枚举时崩溃
    private fun invalidCaptchaMessage(): String =
        when (state.value.language) {
            AppLanguage.ENGLISH -> "Captcha answer is incorrect."
            else -> "Captcha answer is incorrect."
        }

    private fun registerBadRequestMessage(): String =
        when (state.value.language) {
            AppLanguage.ENGLISH -> "Check username, password, and captcha answer."
            else -> "Check username, password, and captcha answer."
        }

    private fun credentialErrorMessage(): String =
        when (state.value.language) {
            AppLanguage.ENGLISH -> "Invalid username or password."
            else -> "Invalid username or password."
        }

    private fun usernameExistsMessage(): String =
        when (state.value.language) {
            AppLanguage.ENGLISH -> "Username already exists."
            else -> "Username already exists."
        }

    private fun vipOrderCreatedMessage(): String =
        when (state.value.language) {
            AppLanguage.ENGLISH -> "VIP order created. Submit payment to activate VIP."
            else -> "VIP order created. Submit payment to activate VIP."
        }

    private fun passwordChangedMessage(): String =
        when (state.value.language) {
            AppLanguage.ENGLISH -> "Password changed. Sign in again with the new password."
            else -> "Password changed. Sign in again with the new password."
        }

    private fun walletUpdatedMessage(): String =
        when (state.value.language) {
            AppLanguage.ENGLISH -> "Wallet updated."
            else -> "Wallet updated."
        }

    private fun withdrawalSubmittedMessage(): String =
        when (state.value.language) {
            AppLanguage.ENGLISH -> "Withdrawal submitted."
            else -> "Withdrawal submitted."
        }

    private enum class ErrorContext {
        LOGIN,
        REGISTER,
        CONTENT,
        ACCOUNT,
        PLAYBACK,
        SOCIAL,
        DIAGNOSTIC,
    }

}
