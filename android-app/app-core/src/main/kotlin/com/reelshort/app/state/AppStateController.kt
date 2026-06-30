package com.reelshort.app.state

import com.reelshort.app.data.AppDataSource
import com.reelshort.app.data.ApiHealthStatus
import com.reelshort.app.data.AuthSession
import com.reelshort.app.data.BookInteractionState
import com.reelshort.app.data.BookSummary
import com.reelshort.app.data.Comment
import com.reelshort.app.data.EpisodeSummary
import com.reelshort.app.data.SavedCredentials
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
    private var openBookRequestVersion = 0L
    private var accountRequestVersion = 0L
    private var favoritesRequestVersion = 0L

    val state: StateFlow<AppUiState> = mutableState.asStateFlow()

    suspend fun restoreSession() = runWithLoading(ErrorContext.CONTENT) {
        if (sessionRestored) {
            mutableState.update { it.copy(isLoading = false) }
            return@runWithLoading
        }
        sessionRestored = true
        val session = dataSource.restoreSession()
        val savedCredentials = dataSource.loadSavedCredentials()
        val cachedHomeShelf = dataSource.loadCachedHomeShelf()
        if (cachedHomeShelf.isNotEmpty()) {
            // 冷启动秒开：先用磁盘缓存立即渲染首页，随后在后台静默拉取最新数据替换并更新缓存。
            mutableState.update {
                it.copy(
                    screen = AppScreen.HOME,
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
        val session = dataSource.login(username, password)
        updateSavedCredentials(username, password, rememberPassword)
        openHomeAfterAuthentication(session)
    }

    suspend fun register(username: String, password: String, rememberPassword: Boolean = false) = runWithLoading(ErrorContext.REGISTER) {
        val session = dataSource.register(username, password)
        updateSavedCredentials(username, password, rememberPassword)
        openHomeAfterAuthentication(session)
    }

    private suspend fun updateSavedCredentials(username: String, password: String, rememberPassword: Boolean) {
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
            mutableState.update {
                it.copy(
                    session = session,
                    authPromptVisible = false,
                    pendingPlaybackEpisode = null,
                    isLoading = false,
                    errorMessage = null,
                )
            }
            openPlayerForAuthenticatedUser(pendingEpisode)
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

    suspend fun openBook(book: BookSummary) = runWithLoading(ErrorContext.CONTENT) {
        val requestVersion = ++openBookRequestVersion
        val returnScreen = playbackReturnScreenFor(state.value.screen)
        val episodes = dataSource.loadEpisodes(book)
        if (requestVersion != openBookRequestVersion) {
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
        mutableState.update {
            it.copy(
                selectedBook = book,
                episodes = episodes,
                selectedEpisode = null,
                currentVideoUrl = null,
                playback = PlaybackState(),
                playerReturnScreen = returnScreen,
                isLoading = false,
            )
        }
        if (state.value.session == null) {
            mutableState.update {
                it.copy(
                    authPromptVisible = true,
                    pendingPlaybackEpisode = firstEpisode,
                    isLoading = false,
                )
            }
            return@runWithLoading
        }
        val resumeEpisode = chooseResumeEpisode(book, episodes) ?: firstEpisode
        openPlayerForAuthenticatedUser(resumeEpisode)
    }

    suspend fun openPlayer(episode: EpisodeSummary) = runWithLoading(ErrorContext.PLAYBACK) {
        val book = requireSelectedBook()
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
        openPlayerForAuthenticatedUser(episode)
    }

    private suspend fun openPlayerForAuthenticatedUser(episode: EpisodeSummary) {
        val book = requireSelectedBook()
        val snapshot = try {
            dataSource.loadEpisodeSnapshot(book, episode)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            null
        }
        val videoUrl = dataSource.loadVideoUrl(book, episode)
        val initialPosition = snapshot?.positionSeconds ?: 0
        val initialDuration = snapshot?.durationSeconds?.takeIf { it > 0 } ?: videoUrl.durationSeconds
        val awardedProgress = snapshot?.awardedStages?.maxOrNull() ?: 0
        val interaction = loadInteractionSilently(book)
        val comments = loadCommentsSilently(book)
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

    private suspend fun chooseResumeEpisode(book: BookSummary, episodes: List<EpisodeSummary>): EpisodeSummary? {
        val history = try {
            dataSource.loadWatchHistory()
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            return episodes.firstOrNull()
        }
        val record = history.firstOrNull { it.bookId == book.id } ?: return episodes.firstOrNull()
        return resumeEpisodeForRecord(episodes, record) ?: episodes.firstOrNull()
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

        mutableState.update { it.copy(screen = AppScreen.ACCOUNT, errorMessage = null) }
        refreshAccountSilently()
    }

    private suspend fun loadAccountSnapshot(requestVersion: Long) = runWithLoading(ErrorContext.ACCOUNT) {
        loadAccountSnapshotForAuthenticatedUser(requestVersion)
    }

    private suspend fun loadAccountSnapshotForAuthenticatedUser(requestVersion: Long) {
        val history = dataSource.loadWatchHistory()
        val points = dataSource.loadPointAccount()
        val orders = dataSource.loadOrders()
        if (requestVersion != accountRequestVersion || state.value.screen != AppScreen.ACCOUNT) {
            return
        }
        mutableState.update {
            it.copy(
                screen = AppScreen.ACCOUNT,
                watchHistory = history,
                pointAccount = points,
                orders = orders,
                isLoading = false,
            )
        }
    }

    private suspend fun refreshAccountSilently() {
        try {
            val history = dataSource.loadWatchHistory()
            val points = dataSource.loadPointAccount()
            val orders = dataSource.loadOrders()
            mutableState.update {
                it.copy(
                    screen = AppScreen.ACCOUNT,
                    watchHistory = history,
                    pointAccount = points,
                    orders = orders,
                )
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
        }
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
            mutableState.update {
                it.copy(
                    isLoading = false,
                    errorMessage = userFacingErrorMessage(error, errorContext),
                    authPromptVisible = if (shouldPromptForLogin(error, errorContext)) true else it.authPromptVisible,
                )
            }
        }
    }

    private fun requireSelectedBook(): BookSummary =
        state.value.selectedBook ?: throw IllegalStateException("未选择剧集")

    private fun hasAccountSnapshot(state: AppUiState): Boolean =
        state.pointAccount != null || state.watchHistory.isNotEmpty() || state.orders.isNotEmpty()

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
            else -> AppScreen.HOME
        }

    private fun shouldPromptForLogin(error: Throwable, context: ErrorContext): Boolean =
        error is ApiClientException &&
            error.statusCode == 401 &&
            (context == ErrorContext.ACCOUNT || context == ErrorContext.PLAYBACK || context == ErrorContext.SOCIAL)

    private fun userFacingErrorMessage(error: Throwable, context: ErrorContext): String {
        val rawMessage = error.message?.trim().orEmpty()
        val normalizedMessage = rawMessage.lowercase()
        if (context == ErrorContext.CONTENT) {
            return "内容暂时加载失败，可以稍后刷新。"
        }
        if (error is ApiClientException && error.statusCode == 401 && (context == ErrorContext.LOGIN || context == ErrorContext.REGISTER)) {
            return "用户名或密码错误"
        }
        if (error is ApiClientException && error.statusCode == 401) {
            return "登录状态已失效，请重新登录。"
        }
        if ((context == ErrorContext.LOGIN || context == ErrorContext.REGISTER) &&
            ("invalid username or password" in normalizedMessage || "invalid credentials" in normalizedMessage)
        ) {
            return "用户名或密码错误"
        }
        return rawMessage.ifBlank { error::class.simpleName ?: "请求失败" }
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
