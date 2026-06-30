package com.reelshort.app.state

import com.reelshort.app.data.AppDataSource
import com.reelshort.app.data.ApiHealthStatus
import com.reelshort.app.data.AuthSession
import com.reelshort.app.data.BookSummary
import com.reelshort.app.data.Comment
import com.reelshort.app.data.EpisodeSummary
import com.reelshort.app.data.PointAccount
import com.reelshort.app.data.PointRecord
import com.reelshort.app.data.RechargeOrderSummary
import com.reelshort.app.data.SavedCredentials
import com.reelshort.app.data.SocialToggleResult
import com.reelshort.app.data.VideoUrl
import com.reelshort.app.data.WatchEpisodeSnapshot
import com.reelshort.app.data.WatchProgressReport
import com.reelshort.app.data.WatchRecord
import com.reelshort.app.network.ApiClientException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertFailsWith

@OptIn(ExperimentalCoroutinesApi::class)
class AppStateControllerTest {
    @Test
    fun initialStateStartsOnHomeScreenForGuestBrowsing() {
        val controller = AppStateController(FakeAppDataSource())

        val state = controller.state.value

        assertEquals(AppScreen.HOME, state.screen)
        assertFalse(state.isLoading)
        assertNull(state.session)
        assertNull(state.errorMessage)
        assertFalse(state.authPromptVisible)
    }

    @Test
    fun loginSuccessStoresSessionAndLoadsHomeShelf() = runTest {
        val dataSource = FakeAppDataSource()
        val controller = AppStateController(dataSource)

        controller.login("demo", "Password123")

        val state = controller.state.value
        assertEquals(AppScreen.HOME, state.screen)
        assertEquals("demo", state.session?.username)
        assertEquals(listOf("book-1", "book-2"), state.homeShelf.map { it.id })
        assertFalse(state.isLoading)
        assertNull(state.errorMessage)
        assertEquals(listOf("login:demo", "home", "home:cache:save"), dataSource.calls)
    }

    @Test
    fun loginSuccessKeepsSessionWhenHomeShelfFails() = runTest {
        val dataSource = FakeAppDataSource(homeError = IllegalStateException("content provider returned 502"))
        val controller = AppStateController(dataSource)

        controller.login("demo", "Password123")

        val state = controller.state.value
        assertEquals(AppScreen.HOME, state.screen)
        assertEquals("demo", state.session?.username)
        assertEquals(emptyList(), state.homeShelf)
        assertEquals("内容暂时加载失败，可以稍后刷新。", state.errorMessage)
        assertFalse(state.isLoading)
        assertEquals(listOf("login:demo", "home"), dataSource.calls)
    }

    @Test
    fun registerSuccessKeepsSessionWhenHomeShelfFails() = runTest {
        val dataSource = FakeAppDataSource(homeError = IllegalStateException("content provider returned 502"))
        val controller = AppStateController(dataSource)

        controller.register("new-user", "Password123")

        val state = controller.state.value
        assertEquals(AppScreen.HOME, state.screen)
        assertEquals("new-user", state.session?.username)
        assertEquals(emptyList(), state.homeShelf)
        assertEquals("内容暂时加载失败，可以稍后刷新。", state.errorMessage)
        assertFalse(state.isLoading)
        assertEquals(listOf("register:new-user", "home"), dataSource.calls)
    }

    @Test
    fun loginFailureKeepsCurrentScreenAndRecordsError() = runTest {
        val dataSource = FakeAppDataSource(loginError = IllegalStateException("bad credentials"))
        val controller = AppStateController(dataSource)

        controller.login("demo", "wrong")

        val state = controller.state.value
        assertEquals(AppScreen.HOME, state.screen)
        assertNull(state.session)
        assertEquals("bad credentials", state.errorMessage)
        assertFalse(state.isLoading)
    }

    @Test
    fun loginFailureUsesUserFacingCredentialMessage() = runTest {
        val dataSource = FakeAppDataSource(loginError = ApiClientException(401, null, "invalid username or password"))
        val controller = AppStateController(dataSource)

        controller.login("demo", "wrong", rememberPassword = false)

        val state = controller.state.value
        assertEquals(AppScreen.HOME, state.screen)
        assertEquals("用户名或密码错误", state.errorMessage)
    }

    @Test
    fun homeUnauthorizedErrorDoesNotUseCredentialMessage() = runTest {
        val dataSource = FakeAppDataSource(homeError = ApiClientException(401, null, "unauthorized"))
        val controller = AppStateController(dataSource)

        controller.refreshHome()

        val state = controller.state.value
        assertEquals(AppScreen.HOME, state.screen)
        assertEquals("内容暂时加载失败，可以稍后刷新。", state.errorMessage)
    }

    @Test
    fun accountUnauthorizedErrorShowsExpiredSessionMessageAndAuthPrompt() = runTest {
        val dataSource = FakeAppDataSource(
            restoredSession = AuthSession(username = "demo", token = "expired-token", tokenType = "Bearer"),
        )
        dataSource.accountError = ApiClientException(401, null, "unauthorized")
        val controller = AppStateController(dataSource)
        controller.restoreSession()
        dataSource.calls.clear()

        controller.openAccount()

        val state = controller.state.value
        assertEquals(AppScreen.ACCOUNT, state.screen)
        assertEquals("登录状态已失效，请重新登录。", state.errorMessage)
        assertEquals(true, state.authPromptVisible)
        assertFalse(state.isLoading)
    }

    @Test
    fun loadSavedCredentialsStoresCredentialsInState() = runTest {
        val credentials = SavedCredentials(username = "demo", password = "Password123", rememberPassword = true)
        val dataSource = FakeAppDataSource(savedCredentials = credentials)
        val controller = AppStateController(dataSource)

        controller.loadSavedCredentials()

        assertEquals(credentials, controller.state.value.savedCredentials)
        assertEquals(listOf("credentials:load"), dataSource.calls)
    }

    @Test
    fun loginSuccessSavesRememberedCredentialsWhenEnabled() = runTest {
        val dataSource = FakeAppDataSource()
        val controller = AppStateController(dataSource)

        controller.login("demo", "Password123", rememberPassword = true)

        assertEquals(
            SavedCredentials(username = "demo", password = "Password123", rememberPassword = true),
            dataSource.savedCredentials,
        )
        assertEquals(dataSource.savedCredentials, controller.state.value.savedCredentials)
    }

    @Test
    fun loginSuccessClearsRememberedPasswordWhenDisabled() = runTest {
        val dataSource = FakeAppDataSource(
            savedCredentials = SavedCredentials(username = "demo", password = "old-password", rememberPassword = true),
        )
        val controller = AppStateController(dataSource)

        controller.login("demo", "Password123", rememberPassword = false)

        assertNull(dataSource.savedCredentials)
        assertNull(controller.state.value.savedCredentials)
    }

    @Test
    fun searchUpdatesQueryResultsAndScreen() = runTest {
        val dataSource = FakeAppDataSource()
        val controller = AppStateController(dataSource)

        controller.search("Alpha")

        val state = controller.state.value
        assertEquals(AppScreen.SEARCH, state.screen)
        assertEquals("Alpha", state.searchQuery)
        assertEquals(listOf("book-1"), state.searchResults.map { it.id })
        assertEquals(listOf("search:Alpha"), dataSource.calls)
    }

    @Test
    fun openBookSelectsBookAndLoadsEpisodes() = runTest {
        val dataSource = FakeAppDataSource()
        val controller = AppStateController(dataSource)
        val book = dataSource.books.first()

        controller.openBook(book)

        val state = controller.state.value
        assertEquals(AppScreen.DETAIL, state.screen)
        assertSame(book, state.selectedBook)
        assertEquals(listOf(1, 2), state.episodes.map { it.number })
        assertNull(state.selectedEpisode)
        assertNull(state.currentVideoUrl)
        assertEquals(listOf("episodes:book-1"), dataSource.calls)
    }

    @Test
    fun openPlayerSelectsEpisodeAndLoadsVideoUrl() = runTest {
        val dataSource = FakeAppDataSource(restoredSession = AuthSession("demo", "token-demo", "Bearer"))
        val controller = AppStateController(dataSource)
        val book = dataSource.books.first()

        controller.restoreSession()
        dataSource.calls.clear()
        controller.openBook(book)
        controller.openPlayer(dataSource.episodes.first())

        val state = controller.state.value
        assertEquals(AppScreen.PLAYER, state.screen)
        assertEquals(1, state.selectedEpisode?.number)
        assertEquals("https://media.local/book-1/1.m3u8", state.currentVideoUrl?.url)
        assertEquals(PlaybackStatus.READY, state.playback.status)
        assertEquals(book, state.playback.book)
        assertEquals(dataSource.episodes.first(), state.playback.episode)
        assertEquals("https://media.local/book-1/1.m3u8", state.playback.videoUrl?.url)
        assertEquals(200, state.playback.durationSeconds)
        assertEquals(0, state.playback.positionSeconds)
        assertEquals(0, state.playback.progressPercent)
        assertEquals(listOf("episodes:book-1", "snapshot:book-1:1", "video:book-1:1", "like-status:book-1", "favorite-status:book-1", "comments:book-1"), dataSource.calls)
    }

    @Test
    fun openPlayerWithoutSessionShowsAuthPromptAndDefersEpisode() = runTest {
        val dataSource = FakeAppDataSource(restoredSession = null)
        val controller = AppStateController(dataSource)

        controller.openBook(dataSource.books.first())
        controller.openPlayer(dataSource.episodes.first())

        val state = controller.state.value
        assertEquals(AppScreen.DETAIL, state.screen)
        assertEquals(true, state.authPromptVisible)
        assertEquals(1, state.pendingPlaybackEpisode?.number)
        assertNull(state.currentVideoUrl)
        assertEquals(PlaybackStatus.IDLE, state.playback.status)
        assertEquals(listOf("episodes:book-1"), dataSource.calls)
    }

    @Test
    fun loginAfterDeferredPlaybackContinuesToPlayer() = runTest {
        val dataSource = FakeAppDataSource(restoredSession = null)
        val controller = AppStateController(dataSource)

        controller.openBook(dataSource.books.first())
        controller.openPlayer(dataSource.episodes.first())
        controller.login("demo", "Password123")

        val state = controller.state.value
        assertEquals(AppScreen.PLAYER, state.screen)
        assertEquals("demo", state.session?.username)
        assertFalse(state.authPromptVisible)
        assertNull(state.pendingPlaybackEpisode)
        assertEquals("https://media.local/book-1/1.m3u8", state.currentVideoUrl?.url)
        assertEquals(listOf("episodes:book-1", "login:demo", "snapshot:book-1:1", "video:book-1:1", "like-status:book-1", "favorite-status:book-1", "comments:book-1"), dataSource.calls)
    }

    @Test
    fun openPlayerInitializesPlaybackFromSnapshotAndAwardedStages() = runTest {
        val session = AuthSession(username = "demo", token = "token-demo", tokenType = "Bearer")
        val dataSource = FakeAppDataSource(restoredSession = session)
        dataSource.snapshot = WatchEpisodeSnapshot(
            bookId = "book-1",
            episode = 1,
            positionSeconds = 120,
            durationSeconds = 200,
            progressPercent = 60,
            awardedStages = listOf(25, 50),
        )
        val controller = AppStateController(dataSource)

        controller.restoreSession()
        controller.openBook(dataSource.books.first())
        controller.openPlayer(dataSource.episodes.first())

        val state = controller.state.value
        assertEquals(120, state.playback.positionSeconds)
        assertEquals(60, state.playback.progressPercent)
        assertEquals(50, state.playback.lastReportedProgressPercent)
        assertEquals(120, state.playback.lastReportedPositionSeconds)
    }

    @Test
    fun openPlayerContinuesWhenSnapshotFailsWithFriendlyError() = runTest {
        val session = AuthSession(username = "demo", token = "token-demo", tokenType = "Bearer")
        val dataSource = FakeAppDataSource(restoredSession = session)
        dataSource.snapshotError = IllegalStateException("snapshot unavailable")
        val controller = AppStateController(dataSource)

        controller.restoreSession()
        controller.openBook(dataSource.books.first())
        controller.openPlayer(dataSource.episodes.first())

        val state = controller.state.value
        assertEquals(AppScreen.PLAYER, state.screen)
        assertEquals("积分状态暂时加载失败，播放不受影响。", state.errorMessage)
        assertEquals(0, state.playback.lastReportedProgressPercent)
        assertEquals("https://media.local/book-1/1.m3u8", state.currentVideoUrl?.url)
    }

    @Test
    fun updatePlaybackPositionStoresClampedLocalProgressWithoutCallingBackend() = runTest {
        val dataSource = FakeAppDataSource(restoredSession = AuthSession("demo", "token-demo", "Bearer"))
        val controller = AppStateController(dataSource)

        controller.restoreSession()
        dataSource.calls.clear()
        controller.openBook(dataSource.books.first())
        controller.openPlayer(dataSource.episodes.first())
        controller.updatePlaybackPosition(positionSeconds = 240, durationSeconds = 200)

        val state = controller.state.value
        assertEquals(200, state.playback.positionSeconds)
        assertEquals(200, state.playback.durationSeconds)
        assertEquals(100, state.playback.progressPercent)
        assertEquals(listOf("episodes:book-1", "snapshot:book-1:1", "video:book-1:1", "like-status:book-1", "favorite-status:book-1", "comments:book-1"), dataSource.calls)
    }

    @Test
    fun updatePlaybackPositionClampsNegativePositionToZero() = runTest {
        val dataSource = FakeAppDataSource(restoredSession = AuthSession("demo", "token-demo", "Bearer"))
        val controller = AppStateController(dataSource)

        controller.restoreSession()
        dataSource.calls.clear()
        controller.openBook(dataSource.books.first())
        controller.openPlayer(dataSource.episodes.first())
        controller.updatePlaybackPosition(positionSeconds = -10, durationSeconds = 200)

        val state = controller.state.value
        assertEquals(0, state.playback.positionSeconds)
        assertEquals(0, state.playback.progressPercent)
    }

    @Test
    fun updatePlaybackPositionBeforeOpeningPlayerDoesNotCreatePlaybackState() {
        val controller = AppStateController(FakeAppDataSource())

        controller.updatePlaybackPosition(positionSeconds = 20, durationSeconds = 100)

        val state = controller.state.value
        assertEquals(PlaybackStatus.IDLE, state.playback.status)
        assertEquals(0, state.playback.positionSeconds)
        assertEquals(0, state.playback.progressPercent)
    }

    @Test
    fun reportProgressRefreshesHistoryAndPointAccount() = runTest {
        val dataSource = FakeAppDataSource(restoredSession = AuthSession("demo", "token-demo", "Bearer"))
        val controller = AppStateController(dataSource)

        controller.restoreSession()
        dataSource.calls.clear()
        controller.openBook(dataSource.books.first())
        controller.openPlayer(dataSource.episodes.first())
        controller.reportProgress(positionSeconds = 150, durationSeconds = 200)

        val state = controller.state.value
        assertEquals(75, dataSource.lastProgress?.progressPercent)
        assertEquals(150, state.playback.positionSeconds)
        assertEquals(200, state.playback.durationSeconds)
        assertEquals(75, state.playback.progressPercent)
        assertEquals(150, state.playback.lastReportedPositionSeconds)
        assertEquals(75, state.playback.lastReportedProgressPercent)
        assertEquals(listOf("book-1"), state.watchHistory.map { it.bookId })
        assertEquals(25, state.pointAccount?.balance)
        assertEquals(
            listOf("episodes:book-1", "snapshot:book-1:1", "video:book-1:1", "like-status:book-1", "favorite-status:book-1", "comments:book-1", "progress:book-1:1:150", "history", "points"),
            dataSource.calls,
        )
    }

    @Test
    fun reportProgressSilentlyAtRewardStageDoesNotUseGlobalLoading() = runTest {
        val dataSource = FakeAppDataSource(restoredSession = AuthSession("demo", "token-demo", "Bearer"))
        val controller = AppStateController(dataSource)

        controller.restoreSession()
        dataSource.calls.clear()
        controller.openBook(dataSource.books.first())
        controller.openPlayer(dataSource.episodes.first())
        controller.reportProgressSilently(positionSeconds = 50, durationSeconds = 200)

        val state = controller.state.value
        assertFalse(state.isLoading)
        assertFalse(state.playback.isRewardReporting)
        assertFalse(state.playback.rewardReportError)
        assertEquals(25, dataSource.lastProgress?.progressPercent)
        assertEquals(25, state.playback.lastReportedProgressPercent)
        assertEquals(50, state.playback.lastReportedPositionSeconds)
        assertEquals(listOf("book-1"), state.watchHistory.map { it.bookId })
        assertEquals(25, state.pointAccount?.balance)
        assertEquals(
            listOf("episodes:book-1", "snapshot:book-1:1", "video:book-1:1", "like-status:book-1", "favorite-status:book-1", "comments:book-1", "progress:book-1:1:50", "history", "points"),
            dataSource.calls,
        )
    }

    @Test
    fun reportProgressSilentlyCanSettleMultipleCrossedStages() = runTest {
        val dataSource = FakeAppDataSource(restoredSession = AuthSession("demo", "token-demo", "Bearer"))
        val controller = AppStateController(dataSource)

        controller.restoreSession()
        dataSource.calls.clear()
        controller.openBook(dataSource.books.first())
        controller.openPlayer(dataSource.episodes.first())
        controller.updatePlaybackPosition(positionSeconds = 40, durationSeconds = 200)
        controller.reportProgressSilently(positionSeconds = 160, durationSeconds = 200)

        val state = controller.state.value
        assertFalse(state.isLoading)
        assertEquals(160, state.playback.positionSeconds)
        assertEquals(80, state.playback.progressPercent)
        assertEquals(75, state.playback.lastReportedProgressPercent)
        assertEquals(160, state.playback.lastReportedPositionSeconds)
        assertEquals(listOf("episodes:book-1", "snapshot:book-1:1", "video:book-1:1", "like-status:book-1", "favorite-status:book-1", "comments:book-1", "progress:book-1:1:160", "history", "points"), dataSource.calls)
    }

    @Test
    fun reportProgressSilentlySkipsDuplicateCallsWhileReporting() = runTest {
        val dataSource = FakeAppDataSource(restoredSession = AuthSession("demo", "token-demo", "Bearer"))
        val controller = AppStateController(dataSource)

        controller.restoreSession()
        dataSource.calls.clear()
        controller.openBook(dataSource.books.first())
        controller.openPlayer(dataSource.episodes.first())
        dataSource.progressGate = CompletableDeferred()

        val job = launch { controller.reportProgressSilently(positionSeconds = 50, durationSeconds = 200) }
        runCurrent()
        controller.reportProgressSilently(positionSeconds = 60, durationSeconds = 200)

        val reportingState = controller.state.value
        assertFalse(reportingState.isLoading)
        assertEquals(true, reportingState.playback.isRewardReporting)
        assertEquals(listOf("episodes:book-1", "snapshot:book-1:1", "video:book-1:1", "like-status:book-1", "favorite-status:book-1", "comments:book-1", "progress:book-1:1:50"), dataSource.calls)

        dataSource.progressGate?.complete(Unit)
        job.join()

        assertEquals(25, controller.state.value.playback.lastReportedProgressPercent)
        assertEquals(
            listOf("episodes:book-1", "snapshot:book-1:1", "video:book-1:1", "like-status:book-1", "favorite-status:book-1", "comments:book-1", "progress:book-1:1:50", "history", "points"),
            dataSource.calls,
        )
    }

    @Test
    fun reportProgressSilentlyFailureKeepsPlaybackAndDoesNotShowGlobalError() = runTest {
        val dataSource = FakeAppDataSource(restoredSession = AuthSession("demo", "token-demo", "Bearer"))
        val controller = AppStateController(dataSource)

        controller.restoreSession()
        dataSource.calls.clear()
        controller.openBook(dataSource.books.first())
        controller.openPlayer(dataSource.episodes.first())
        controller.updatePlaybackPosition(positionSeconds = 50, durationSeconds = 200)
        dataSource.progressError = IllegalStateException("backend unavailable")
        controller.reportProgressSilently(positionSeconds = 50, durationSeconds = 200)

        val state = controller.state.value
        assertFalse(state.isLoading)
        assertFalse(state.playback.isRewardReporting)
        assertEquals(true, state.playback.rewardReportError)
        assertEquals(50, state.playback.positionSeconds)
        assertEquals(25, state.playback.progressPercent)
        assertEquals(0, state.playback.lastReportedProgressPercent)
        assertNull(state.errorMessage)
        assertEquals(listOf("episodes:book-1", "snapshot:book-1:1", "video:book-1:1", "like-status:book-1", "favorite-status:book-1", "comments:book-1", "progress:book-1:1:50"), dataSource.calls)
    }

    @Test
    fun reportProgressSilentlySkipsUntilNextUnreportedStage() = runTest {
        val dataSource = FakeAppDataSource(restoredSession = AuthSession("demo", "token-demo", "Bearer"))
        val controller = AppStateController(dataSource)

        controller.restoreSession()
        dataSource.calls.clear()
        controller.openBook(dataSource.books.first())
        controller.openPlayer(dataSource.episodes.first())
        controller.reportProgressSilently(positionSeconds = 150, durationSeconds = 200)
        controller.reportProgressSilently(positionSeconds = 160, durationSeconds = 200)
        controller.reportProgressSilently(positionSeconds = 200, durationSeconds = 200)

        val state = controller.state.value
        assertEquals(100, state.playback.lastReportedProgressPercent)
        assertEquals(2, dataSource.calls.count { it.startsWith("progress:") })
        assertEquals(
            listOf(
                "episodes:book-1",
                "snapshot:book-1:1",
                "video:book-1:1",
                "like-status:book-1",
                "favorite-status:book-1",
                "comments:book-1",
                "progress:book-1:1:150",
                "history",
                "points",
                "progress:book-1:1:200",
                "history",
                "points",
            ),
            dataSource.calls,
        )
    }

    @Test
    fun refreshPlaybackUrlReloadsCurrentEpisodeUrlAndKeepsPosition() = runTest {
        val dataSource = FakeAppDataSource(restoredSession = AuthSession("demo", "token-demo", "Bearer"))
        val controller = AppStateController(dataSource)

        controller.restoreSession()
        dataSource.calls.clear()
        controller.openBook(dataSource.books.first())
        controller.openPlayer(dataSource.episodes.first())
        controller.updatePlaybackPosition(positionSeconds = 80, durationSeconds = 200)
        dataSource.videoUrlVersion = 2
        dataSource.videoDurationSeconds = 400
        controller.refreshPlaybackUrl()

        val state = controller.state.value
        assertEquals("https://media.local/book-1/1-v2.m3u8", state.currentVideoUrl?.url)
        assertEquals("https://media.local/book-1/1-v2.m3u8", state.playback.videoUrl?.url)
        assertEquals(80, state.playback.positionSeconds)
        assertEquals(400, state.playback.durationSeconds)
        assertEquals(20, state.playback.progressPercent)
        assertEquals(listOf("episodes:book-1", "snapshot:book-1:1", "video:book-1:1", "like-status:book-1", "favorite-status:book-1", "comments:book-1", "video:book-1:1"), dataSource.calls)
    }

    @Test
    fun loadAccountSnapshotRefreshesHistoryPointsAndOrders() = runTest {
        val dataSource = FakeAppDataSource()
        val controller = AppStateController(dataSource)

        controller.loadAccountSnapshot()

        val state = controller.state.value
        assertEquals(AppScreen.ACCOUNT, state.screen)
        assertEquals(25, state.pointAccount?.balance)
        assertEquals(listOf("RO202606270001"), state.orders.map { it.orderNo })
        assertEquals(listOf("history", "points", "orders"), dataSource.calls)
    }

    @Test
    fun openHomeWithCachedShelfShowsCacheWithoutGlobalLoadingAndReplacesAfterRefresh() = runTest {
        val dataSource = FakeAppDataSource()
        val controller = AppStateController(dataSource)
        controller.refreshHome()
        dataSource.books = listOf(dataSource.book("book-3", "Gamma"))
        dataSource.homeGate = CompletableDeferred()

        val job = launch { controller.openHome() }
        runCurrent()

        val cachedState = controller.state.value
        assertEquals(AppScreen.HOME, cachedState.screen)
        assertEquals(listOf("book-1", "book-2"), cachedState.homeShelf.map { it.id })
        assertFalse(cachedState.isLoading)
        assertNull(cachedState.errorMessage)

        dataSource.homeGate?.complete(Unit)
        job.join()

        val refreshedState = controller.state.value
        assertEquals(listOf("book-3"), refreshedState.homeShelf.map { it.id })
        assertFalse(refreshedState.isLoading)
        assertNull(refreshedState.errorMessage)
    }

    @Test
    fun openHomeWithCachedShelfKeepsCacheAndStaysSilentWhenRefreshFails() = runTest {
        val dataSource = FakeAppDataSource()
        val controller = AppStateController(dataSource)
        controller.refreshHome()
        dataSource.homeError = IllegalStateException("content source unavailable")

        controller.openHome()

        val state = controller.state.value
        assertEquals(AppScreen.HOME, state.screen)
        assertEquals(listOf("book-1", "book-2"), state.homeShelf.map { it.id })
        assertFalse(state.isLoading)
        assertNull(state.errorMessage)
        assertEquals(listOf("home", "home:cache:save", "home"), dataSource.calls)
    }

    @Test
    fun openHomeWithoutCachedShelfUsesInitialLoadingPath() = runTest {
        val dataSource = FakeAppDataSource()
        val controller = AppStateController(dataSource)

        controller.openHome()

        val state = controller.state.value
        assertEquals(AppScreen.HOME, state.screen)
        assertEquals(listOf("book-1", "book-2"), state.homeShelf.map { it.id })
        assertFalse(state.isLoading)
        assertNull(state.errorMessage)
        assertEquals(listOf("home", "home:cache:save"), dataSource.calls)
    }

    @Test
    fun openAccountWithCachedSnapshotShowsCacheWithoutGlobalLoadingAndReplacesAfterRefresh() = runTest {
        val dataSource = FakeAppDataSource(restoredSession = AuthSession("demo", "token-demo", "Bearer"))
        val controller = AppStateController(dataSource)
        controller.restoreSession()
        dataSource.calls.clear()
        controller.loadAccountSnapshot()
        dataSource.pointBalance = 88
        dataSource.accountGate = CompletableDeferred()

        val job = launch { controller.openAccount() }
        runCurrent()

        val cachedState = controller.state.value
        assertEquals(AppScreen.ACCOUNT, cachedState.screen)
        assertEquals(25, cachedState.pointAccount?.balance)
        assertEquals(listOf("RO202606270001"), cachedState.orders.map { it.orderNo })
        assertFalse(cachedState.isLoading)
        assertNull(cachedState.errorMessage)

        dataSource.accountGate?.complete(Unit)
        job.join()

        val refreshedState = controller.state.value
        assertEquals(88, refreshedState.pointAccount?.balance)
        assertFalse(refreshedState.isLoading)
        assertNull(refreshedState.errorMessage)
    }

    @Test
    fun openAccountWithCachedSnapshotKeepsCacheAndStaysSilentWhenRefreshFails() = runTest {
        val dataSource = FakeAppDataSource(restoredSession = AuthSession("demo", "token-demo", "Bearer"))
        val controller = AppStateController(dataSource)
        controller.restoreSession()
        dataSource.calls.clear()
        controller.loadAccountSnapshot()
        dataSource.accountError = IllegalStateException("account unavailable")

        controller.openAccount()

        val state = controller.state.value
        assertEquals(AppScreen.ACCOUNT, state.screen)
        assertEquals(25, state.pointAccount?.balance)
        assertEquals(listOf("RO202606270001"), state.orders.map { it.orderNo })
        assertFalse(state.isLoading)
        assertNull(state.errorMessage)
        assertEquals(listOf("history", "points", "orders", "history"), dataSource.calls)
    }

    @Test
    fun checkApiHealthWritesDiagnosticsState() = runTest {
        val dataSource = FakeAppDataSource()
        val controller = AppStateController(dataSource)

        controller.checkApiHealth()

        val state = controller.state.value
        assertEquals("http://66.42.99.110:18080/api/app", state.apiBaseUrl)
        assertEquals("UP", state.apiHealthStatus?.status)
        assertEquals("fake-backend", state.apiHealthStatus?.service)
        assertEquals(listOf("health"), dataSource.calls)
    }

    @Test
    fun checkApiHealthFailureMarksDiagnosticsDown() = runTest {
        val dataSource = FakeAppDataSource()
        val controller = AppStateController(dataSource)
        controller.checkApiHealth()
        dataSource.healthError = IllegalStateException("connection refused")

        controller.checkApiHealth()

        val state = controller.state.value
        assertEquals("DOWN", state.apiHealthStatus?.status)
        assertEquals("connection refused", state.errorMessage)
        assertFalse(state.isLoading)
        assertEquals(listOf("health", "health"), dataSource.calls)
    }

    @Test
    fun cancellationIsNotConvertedIntoUiError() = runTest {
        val dataSource = FakeAppDataSource(loginError = CancellationException("cancelled"))
        val controller = AppStateController(dataSource)

        assertFailsWith<CancellationException> {
            controller.login("demo", "Password123")
        }

        val state = controller.state.value
        assertEquals(AppScreen.HOME, state.screen)
        assertNull(state.errorMessage)
    }

    @Test
    fun restoreSessionWithoutStoredSessionLoadsHomeForGuestBrowsing() = runTest {
        val dataSource = FakeAppDataSource(restoredSession = null)
        val controller = AppStateController(dataSource)

        controller.restoreSession()

        val state = controller.state.value
        assertEquals(AppScreen.HOME, state.screen)
        assertNull(state.session)
        assertNull(state.errorMessage)
        assertEquals(listOf("restore", "credentials:load", "home:cache:load", "home", "home:cache:save"), dataSource.calls)
    }

    @Test
    fun restoreSessionWithStoredSessionLoadsHomeShelf() = runTest {
        val session = AuthSession(username = "demo", token = "token-demo", tokenType = "Bearer")
        val dataSource = FakeAppDataSource(restoredSession = session)
        val controller = AppStateController(dataSource)

        controller.restoreSession()

        val state = controller.state.value
        assertEquals(AppScreen.HOME, state.screen)
        assertEquals(session, state.session)
        assertEquals(listOf("book-1", "book-2"), state.homeShelf.map { it.id })
        assertEquals(listOf("restore", "credentials:load", "home:cache:load", "home", "home:cache:save"), dataSource.calls)
    }

    @Test
    fun restoreSessionFailureKeepsStoredSessionAndRecordsContentError() = runTest {
        val session = AuthSession(username = "demo", token = "token-demo", tokenType = "Bearer")
        val dataSource = FakeAppDataSource(restoredSession = session, homeError = IllegalStateException("content unavailable"))
        val controller = AppStateController(dataSource)

        controller.restoreSession()

        val state = controller.state.value
        assertEquals(AppScreen.HOME, state.screen)
        assertEquals(session, state.session)
        assertEquals("内容暂时加载失败，可以稍后刷新。", state.errorMessage)
        assertEquals(listOf("restore", "credentials:load", "home:cache:load", "home"), dataSource.calls)
    }

    @Test
    fun restoreSessionShowsCachedHomeShelfThenSilentlyRefreshes() = runTest {
        val cached = listOf(
            BookSummary(
                id = "cached-1",
                title = "缓存短剧",
                filteredTitle = "cached",
                coverUrl = null,
                description = "来自缓存",
                chapterCount = 3,
            ),
        )
        val dataSource = FakeAppDataSource(cachedHomeShelf = cached)
        val controller = AppStateController(dataSource)

        controller.restoreSession()

        val state = controller.state.value
        assertEquals(AppScreen.HOME, state.screen)
        // 后台静默刷新成功后用最新网络数据替换缓存数据。
        assertEquals(listOf("book-1", "book-2"), state.homeShelf.map { it.id })
        assertNull(state.errorMessage)
        // 读缓存（命中秒开）+ 后台网络拉取 + 写回新缓存。
        assertEquals(listOf("restore", "credentials:load", "home:cache:load", "home", "home:cache:save"), dataSource.calls)
        assertEquals(listOf(dataSource.books), dataSource.savedHomeShelves)
    }

    @Test
    fun restoreSessionKeepsCachedHomeShelfWhenSilentRefreshFails() = runTest {
        val cached = listOf(
            BookSummary(
                id = "cached-1",
                title = "缓存短剧",
                filteredTitle = "cached",
                coverUrl = null,
                description = "来自缓存",
                chapterCount = 3,
            ),
        )
        val dataSource = FakeAppDataSource(cachedHomeShelf = cached, homeError = IllegalStateException("network down"))
        val controller = AppStateController(dataSource)

        controller.restoreSession()

        val state = controller.state.value
        assertEquals(AppScreen.HOME, state.screen)
        // 后台刷新失败静默吞掉，保留缓存数据，不给用户报错。
        assertEquals(listOf("cached-1"), state.homeShelf.map { it.id })
        assertNull(state.errorMessage)
        assertEquals(listOf("restore", "credentials:load", "home:cache:load", "home"), dataSource.calls)
        // 失败时不写回缓存。
        assertTrue(dataSource.savedHomeShelves.isEmpty())
    }

    @Test
    fun pullRefreshReplacesHomeShelfWithoutGlobalLoading() = runTest {
        val dataSource = FakeAppDataSource()
        val controller = AppStateController(dataSource)
        controller.refreshHome()

        controller.refreshHomeWithPull()

        val state = controller.state.value
        assertEquals(AppScreen.HOME, state.screen)
        assertEquals(listOf("book-1", "book-2"), state.homeShelf.map { it.id })
        // 下拉刷新只用顶部指示器，不触发居中 loading 弹窗。
        assertFalse(state.isLoading)
        assertFalse(state.isHomeRefreshing)
        assertNull(state.errorMessage)
        // 拉到新数据后写回缓存。
        assertEquals(2, dataSource.savedHomeShelves.size)
    }

    @Test
    fun pullRefreshKeepsOldDataAndReportsErrorOnFailure() = runTest {
        val dataSource = FakeAppDataSource()
        val controller = AppStateController(dataSource)
        controller.refreshHome()
        // 预置数据后，下拉刷新时让网络失败。
        dataSource.homeError = IllegalStateException("content source unavailable")

        controller.refreshHomeWithPull()

        val state = controller.state.value
        assertEquals(AppScreen.HOME, state.screen)
        // 失败时保留下拉前的旧数据，不替换。
        assertEquals(listOf("book-1", "book-2"), state.homeShelf.map { it.id })
        assertFalse(state.isLoading)
        assertFalse(state.isHomeRefreshing)
        // 失败通过顶部错误条提示用户。
        assertEquals("内容暂时加载失败，可以稍后刷新。", state.errorMessage)
    }

    @Test
    fun loginFromAccountPromptReturnsToAccountAndLoadsProtectedSnapshot() = runTest {
        val dataSource = FakeAppDataSource(restoredSession = null)
        val controller = AppStateController(dataSource)

        controller.openAccount()
        controller.showAuthPrompt()
        controller.login("demo", "Password123")

        val state = controller.state.value
        assertEquals(AppScreen.ACCOUNT, state.screen)
        assertEquals("demo", state.session?.username)
        assertFalse(state.authPromptVisible)
        assertEquals(25, state.pointAccount?.balance)
        assertEquals(listOf("RO202606270001"), state.orders.map { it.orderNo })
        assertEquals(listOf("login:demo", "history", "points", "orders"), dataSource.calls)
    }

    @Test
    fun logoutClearsSessionAndResetsState() = runTest {
        val session = AuthSession(username = "demo", token = "token-demo", tokenType = "Bearer")
        val dataSource = FakeAppDataSource(restoredSession = session)
        val controller = AppStateController(dataSource)
        controller.restoreSession()

        controller.logout()

        val state = controller.state.value
        assertEquals(AppScreen.HOME, state.screen)
        assertNull(state.session)
        assertEquals(emptyList(), state.homeShelf)
        assertNull(state.errorMessage)
        assertEquals(listOf("restore", "credentials:load", "home:cache:load", "home", "home:cache:save", "clear"), dataSource.calls)
    }

    @Test
    fun toggleLikeFlipsInteractionStateForCurrentBook() = runTest {
        val dataSource = FakeAppDataSource(restoredSession = AuthSession("demo", "token-demo", "Bearer"))
        val controller = AppStateController(dataSource)
        controller.restoreSession()
        controller.openBook(dataSource.book("book-1", "Alpha"))
        controller.openPlayer(dataSource.episodes.first())
        controller.toggleLike()

        val state = controller.state.value
        assertTrue(state.interaction.liked)
        assertEquals(1, state.interaction.likeCount)
    }

    @Test
    fun toggleFavoriteFlipsInteractionStateForCurrentBook() = runTest {
        val dataSource = FakeAppDataSource(restoredSession = AuthSession("demo", "token-demo", "Bearer"))
        val controller = AppStateController(dataSource)
        controller.restoreSession()
        controller.openBook(dataSource.book("book-1", "Alpha"))
        controller.openPlayer(dataSource.episodes.first())
        controller.toggleFavorite()

        val state = controller.state.value
        assertTrue(state.interaction.favorited)
        assertEquals(1, state.interaction.favoriteCount)
    }

    @Test
    fun submitCommentAppendsCommentAndRefreshesList() = runTest {
        val dataSource = FakeAppDataSource(restoredSession = AuthSession("demo", "token-demo", "Bearer"))
        val controller = AppStateController(dataSource)
        controller.restoreSession()
        controller.openBook(dataSource.book("book-1", "Alpha"))
        controller.openPlayer(dataSource.episodes.first())

        controller.submitComment("nice drama")

        val state = controller.state.value
        assertEquals(1, state.comments.size)
        assertEquals("nice drama", state.comments.single().content)
    }

    @Test
    fun socialErrorPromptsLoginForLoggedOutUser() = runTest {
        val dataSource = FakeAppDataSource(restoredSession = null)
        dataSource.socialError = ApiClientException(401, 401, "unauthorized")
        val controller = AppStateController(dataSource)
        controller.openBook(dataSource.book("book-1", "Alpha"))
        controller.openPlayer(dataSource.episodes.first())
        controller.toggleLike()

        assertTrue(controller.state.value.authPromptVisible)
    }

    @Test
    fun openFavoritesLoadsUserFavoriteBooks() = runTest {
        val dataSource = FakeAppDataSource(restoredSession = AuthSession("demo", "token-demo", "Bearer"))
        dataSource.favoritesList = listOf(dataSource.book("book-fav", "Love"))
        val controller = AppStateController(dataSource)
        controller.restoreSession()

        controller.openFavorites()

        val state = controller.state.value
        assertEquals(AppScreen.FAVORITES, state.screen)
        assertEquals(listOf("book-fav"), state.favorites.map { it.id })
    }

    private class FakeAppDataSource(
        private val loginError: Throwable? = null,
        private var restoredSession: AuthSession? = null,
        var savedCredentials: SavedCredentials? = null,
        homeError: Throwable? = null,
        cachedHomeShelf: List<BookSummary> = emptyList(),
    ) : AppDataSource {
        var books = listOf(
            BookSummary(
                id = "book-1",
                title = "Alpha",
                filteredTitle = "alpha",
                coverUrl = null,
                description = "First book",
                chapterCount = 2,
            ),
            BookSummary(
                id = "book-2",
                title = "Beta",
                filteredTitle = "beta",
                coverUrl = null,
                description = "Second book",
                chapterCount = 1,
            ),
        )
        var homeError: Throwable? = homeError
        var homeGate: CompletableDeferred<Unit>? = null
        var cachedHomeShelf: List<BookSummary> = cachedHomeShelf
        val savedHomeShelves = mutableListOf<List<BookSummary>>()
        var accountError: Throwable? = null
        var accountGate: CompletableDeferred<Unit>? = null
        var progressError: Throwable? = null
        var progressGate: CompletableDeferred<Unit>? = null
        var pointBalance: Int = 25
        var snapshot: WatchEpisodeSnapshot = WatchEpisodeSnapshot.empty("book-1", 1)
        var snapshotError: Throwable? = null
        val episodes = listOf(
            EpisodeSummary(
                number = 1,
                chapterId = "chapter-1",
                title = "Opening Trap",
                description = "A deal goes wrong.",
                durationSeconds = 200,
            ),
            EpisodeSummary(
                number = 2,
                chapterId = "chapter-2",
                title = "Second Move",
                description = "The secret spreads.",
                durationSeconds = 180,
            ),
        )
        val calls = mutableListOf<String>()
        var lastProgress: WatchProgressReport? = null
        var videoUrlVersion: Int = 1
        var videoDurationSeconds: Int? = null
        var healthError: Throwable? = null
        var socialError: Throwable? = null
        private val liked = mutableSetOf<String>()
        private val favorited = mutableSetOf<String>()
        private val commentStore = mutableListOf<Comment>()
        var favoritesList: List<BookSummary> = emptyList()

        override val apiBaseUrl: String = "http://66.42.99.110:18080/api/app"

        override suspend fun checkSystemHealth(): ApiHealthStatus {
            calls += "health"
            healthError?.let { throw it }
            return ApiHealthStatus(status = "UP", service = "fake-backend")
        }

        override suspend fun login(username: String, password: String): AuthSession {
            calls += "login:$username"
            loginError?.let { throw it }
            return AuthSession(username = username, token = "token-$username", tokenType = "Bearer")
        }

        override suspend fun register(username: String, password: String): AuthSession {
            calls += "register:$username"
            return AuthSession(username = username, token = "token-$username", tokenType = "Bearer")
        }

        override suspend fun loadHomeShelf(): List<BookSummary> {
            calls += "home"
            homeGate?.await()
            homeError?.let { throw it }
            return books
        }

        override suspend fun loadCachedHomeShelf(): List<BookSummary> {
            calls += "home:cache:load"
            return cachedHomeShelf
        }

        override suspend fun saveCachedHomeShelf(shelf: List<BookSummary>) {
            calls += "home:cache:save"
            savedHomeShelves += shelf
            cachedHomeShelf = shelf
        }

        override suspend fun search(query: String): List<BookSummary> {
            calls += "search:$query"
            return books.filter { it.title.contains(query, ignoreCase = true) }
        }

        override suspend fun loadEpisodes(book: BookSummary): List<EpisodeSummary> {
            calls += "episodes:${book.id}"
            return episodes
        }

        override suspend fun loadVideoUrl(book: BookSummary, episode: EpisodeSummary): VideoUrl {
            calls += "video:${book.id}:${episode.number}"
            val suffix = if (videoUrlVersion == 1) "" else "-v$videoUrlVersion"
            return VideoUrl(
                url = "https://media.local/${book.id}/${episode.number}$suffix.m3u8",
                contentType = "application/vnd.apple.mpegurl",
                episode = episode.number,
                durationSeconds = videoDurationSeconds ?: episode.durationSeconds,
            )
        }

        override suspend fun loadEpisodeSnapshot(book: BookSummary, episode: EpisodeSummary): WatchEpisodeSnapshot {
            calls += "snapshot:${book.id}:${episode.number}"
            snapshotError?.let { throw it }
            return snapshot.copy(bookId = book.id, episode = episode.number)
        }

        override suspend fun reportWatchProgress(
            book: BookSummary,
            episode: EpisodeSummary,
            positionSeconds: Int,
            durationSeconds: Int,
        ): WatchProgressReport {
            calls += "progress:${book.id}:${episode.number}:$positionSeconds"
            progressGate?.await()
            progressError?.let { throw it }
            val rawPercent = if (durationSeconds <= 0) {
                0
            } else {
                ((positionSeconds.toDouble() / durationSeconds.toDouble()) * 100).toInt().coerceIn(0, 100)
            }
            val settledPercent = listOf(25, 50, 75, 100).lastOrNull { it <= rawPercent } ?: rawPercent
            val progress = WatchProgressReport(
                bookId = book.id,
                bookTitle = book.title,
                filteredTitle = book.filteredTitle,
                episode = episode.number,
                chapterId = episode.chapterId,
                positionSeconds = positionSeconds,
                durationSeconds = durationSeconds,
                progressPercent = settledPercent,
            )
            lastProgress = progress
            return progress
        }

        override suspend fun loadWatchHistory(): List<WatchRecord> {
            calls += "history"
            accountGate?.await()
            accountError?.let { throw it }
            return listOf(WatchRecord(bookId = "book-1", bookTitle = "Alpha", episode = 1, progressPercent = 75))
        }

        override suspend fun loadPointAccount(): PointAccount {
            calls += "points"
            accountError?.let { throw it }
            return PointAccount(balance = pointBalance, records = listOf(PointRecord(amount = 5, reason = "WATCH_REWARD")))
        }

        override suspend fun loadOrders(): List<RechargeOrderSummary> {
            calls += "orders"
            accountError?.let { throw it }
            return listOf(
                RechargeOrderSummary(
                    orderNo = "RO202606270001",
                    amountCents = 100,
                    pointAmount = 10,
                    status = "CREATED",
                ),
            )
        }

        override suspend fun restoreSession(): AuthSession? {
            calls += "restore"
            return restoredSession
        }

        override suspend fun clearSession() {
            calls += "clear"
            restoredSession = null
        }

        override suspend fun loadSavedCredentials(): SavedCredentials? {
            calls += "credentials:load"
            return savedCredentials
        }

        override suspend fun saveCredentials(credentials: SavedCredentials) {
            savedCredentials = credentials
        }

        override suspend fun clearSavedCredentials() {
            savedCredentials = null
        }

        override suspend fun toggleLike(book: BookSummary): SocialToggleResult {
            calls += "like:${book.id}"
            socialError?.let { throw it }
            if (!liked.add(book.id)) liked.remove(book.id)
            return SocialToggleResult(liked.contains(book.id), liked.size)
        }

        override suspend fun loadLikeStatus(book: BookSummary): SocialToggleResult {
            calls += "like-status:${book.id}"
            return SocialToggleResult(liked.contains(book.id), liked.size)
        }

        override suspend fun toggleFavorite(book: BookSummary): SocialToggleResult {
            calls += "favorite:${book.id}"
            socialError?.let { throw it }
            if (!favorited.add(book.id)) favorited.remove(book.id)
            return SocialToggleResult(favorited.contains(book.id), favorited.size)
        }

        override suspend fun loadFavoriteStatus(book: BookSummary): SocialToggleResult {
            calls += "favorite-status:${book.id}"
            return SocialToggleResult(favorited.contains(book.id), favorited.size)
        }

        override suspend fun addComment(book: BookSummary, content: String): Comment {
            calls += "comment-add:${book.id}"
            socialError?.let { throw it }
            val comment = Comment(
                id = "comment-${commentStore.size + 1}",
                username = "demo",
                content = content,
                createdAt = "2026-06-29T00:00:00Z",
            )
            commentStore.add(comment)
            return comment
        }

        override suspend fun listComments(book: BookSummary): List<Comment> {
            calls += "comments:${book.id}"
            return commentStore.toList()
        }

        override suspend fun loadMyFavorites(): List<BookSummary> {
            calls += "my-favorites"
            socialError?.let { throw it }
            return favoritesList
        }

        fun book(id: String, title: String): BookSummary =
            BookSummary(
                id = id,
                title = title,
                filteredTitle = title.lowercase(),
                coverUrl = null,
                description = "$title book",
                chapterCount = 1,
            )
    }
}
