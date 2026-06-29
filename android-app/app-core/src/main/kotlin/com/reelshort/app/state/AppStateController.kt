package com.reelshort.app.state

import com.reelshort.app.data.AppDataSource
import com.reelshort.app.data.ApiHealthStatus
import com.reelshort.app.data.AuthSession
import com.reelshort.app.data.BookSummary
import com.reelshort.app.data.EpisodeSummary
import com.reelshort.app.network.ApiClientException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class AppStateController(private val dataSource: AppDataSource) {
    private val mutableState = MutableStateFlow(AppUiState(apiBaseUrl = dataSource.apiBaseUrl))
    private var rewardReportInFlight = false

    val state: StateFlow<AppUiState> = mutableState.asStateFlow()

    suspend fun restoreSession() = runWithLoading {
        val session = dataSource.restoreSession()
        try {
            val homeShelf = dataSource.loadHomeShelf()
            mutableState.update {
                it.copy(
                    screen = AppScreen.HOME,
                    session = session,
                    homeShelf = homeShelf,
                    isLoading = false,
                )
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            mutableState.update {
                it.copy(
                    screen = AppScreen.HOME,
                    session = session,
                    homeShelf = emptyList(),
                    isLoading = false,
                    errorMessage = "内容暂时加载失败，可以稍后刷新。",
                )
            }
        }
    }

    suspend fun login(username: String, password: String) = runWithLoading {
        val session = dataSource.login(username, password)
        openHomeAfterAuthentication(session)
    }

    suspend fun register(username: String, password: String) = runWithLoading {
        val session = dataSource.register(username, password)
        openHomeAfterAuthentication(session)
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
            loadAccountSnapshotForAuthenticatedUser()
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
    }

    suspend fun refreshHome() = runWithLoading {
        val homeShelf = dataSource.loadHomeShelf()
        mutableState.update {
            it.copy(
                screen = AppScreen.HOME,
                homeShelf = homeShelf,
                isLoading = false,
            )
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
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
        }
    }

    suspend fun search(query: String) = runWithLoading {
        val results = dataSource.search(query)
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

    suspend fun openBook(book: BookSummary) = runWithLoading {
        val episodes = dataSource.loadEpisodes(book)
        mutableState.update {
            it.copy(
                screen = AppScreen.DETAIL,
                selectedBook = book,
                episodes = episodes,
                selectedEpisode = null,
                currentVideoUrl = null,
                playback = PlaybackState(),
                isLoading = false,
            )
        }
    }

    suspend fun openPlayer(episode: EpisodeSummary) = runWithLoading {
        val book = requireSelectedBook()
        if (state.value.session == null) {
            mutableState.update {
                it.copy(
                    screen = AppScreen.DETAIL,
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
        mutableState.update {
            it.copy(
                screen = AppScreen.PLAYER,
                selectedEpisode = episode,
                currentVideoUrl = videoUrl,
                playback = PlaybackState.ready(book, episode, videoUrl)
                    .withPosition(initialPosition, initialDuration)
                    .withReportedProgress(initialPosition, awardedProgress),
                isLoading = false,
                authPromptVisible = false,
                pendingPlaybackEpisode = null,
                errorMessage = if (snapshot == null) "积分状态暂时加载失败，播放不受影响。" else null,
            )
        }
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

    suspend fun reportProgress(positionSeconds: Int, durationSeconds: Int) = runWithLoading {
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

    suspend fun refreshPlaybackUrl() = runWithLoading {
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

    suspend fun loadAccountSnapshot() = runWithLoading {
        loadAccountSnapshotForAuthenticatedUser()
    }

    private suspend fun loadAccountSnapshotForAuthenticatedUser() {
        val history = dataSource.loadWatchHistory()
        val points = dataSource.loadPointAccount()
        val orders = dataSource.loadOrders()
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

    suspend fun openAccount() {
        if (state.value.session == null) {
            mutableState.update { it.copy(screen = AppScreen.ACCOUNT, errorMessage = null, isLoading = false) }
            return
        }
        if (!hasAccountSnapshot(state.value)) {
            loadAccountSnapshot()
            return
        }

        mutableState.update { it.copy(screen = AppScreen.ACCOUNT, errorMessage = null) }
        refreshAccountSilently()
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
            val message = userFacingErrorMessage(error)
            mutableState.update {
                it.copy(
                    apiHealthStatus = ApiHealthStatus(status = "DOWN", service = message),
                    errorMessage = message,
                    isLoading = false,
                )
            }
        }
    }

    suspend fun logout() = runWithLoading {
        dataSource.clearSession()
        mutableState.value = AppUiState(apiBaseUrl = dataSource.apiBaseUrl)
    }

    private suspend fun runWithLoading(block: suspend () -> Unit) {
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
                    errorMessage = userFacingErrorMessage(error),
                )
            }
        }
    }

    private fun requireSelectedBook(): BookSummary =
        state.value.selectedBook ?: throw IllegalStateException("未选择剧集")

    private fun hasAccountSnapshot(state: AppUiState): Boolean =
        state.pointAccount != null || state.watchHistory.isNotEmpty() || state.orders.isNotEmpty()

    private fun userFacingErrorMessage(error: Throwable): String {
        val rawMessage = error.message?.trim().orEmpty()
        val normalizedMessage = rawMessage.lowercase()
        if (error is ApiClientException && error.statusCode == 401) {
            return "用户名或密码错误"
        }
        if ("invalid username or password" in normalizedMessage || "invalid credentials" in normalizedMessage) {
            return "用户名或密码错误"
        }
        return rawMessage.ifBlank { error::class.simpleName ?: "请求失败" }
    }
}
