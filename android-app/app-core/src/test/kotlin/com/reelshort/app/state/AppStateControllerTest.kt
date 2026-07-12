package com.reelshort.app.state

import com.reelshort.app.data.AppDataSource
import com.reelshort.app.data.ApiHealthStatus
import com.reelshort.app.data.AppLanguage
import com.reelshort.app.data.AuthSession
import com.reelshort.app.data.BookSummary
import com.reelshort.app.data.Comment
import com.reelshort.app.data.EpisodeSummary
import com.reelshort.app.data.PointAccount
import com.reelshort.app.data.PointRecord
import com.reelshort.app.data.PointTransferRecord
import com.reelshort.app.data.RechargeOrderSummary
import com.reelshort.app.data.RegisterSimulationResult
import com.reelshort.app.data.SavedCredentials
import com.reelshort.app.data.SmsSendResult
import com.reelshort.app.data.SmsVerificationPurpose
import com.reelshort.app.data.SocialToggleResult
import com.reelshort.app.data.VideoUrl
import com.reelshort.app.data.WatchEpisodeSnapshot
import com.reelshort.app.data.WatchProgressReport
import com.reelshort.app.data.WatchRecord
import com.reelshort.app.data.WalletInfo
import com.reelshort.app.data.WithdrawalRecord
import com.reelshort.app.data.WithdrawalSummary
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
        assertEquals(AuthMode.LOGIN, state.authMode)
        assertFalse(state.isLoading)
        assertNull(state.session)
        assertNull(state.errorMessage)
        assertFalse(state.authPromptVisible)
    }

    @Test
    fun showAuthPromptKeepsLoginModeByDefault() = runTest {
        val controller = AppStateController(FakeAppDataSource())

        controller.showAuthPrompt()

        val state = controller.state.value
        assertTrue(state.authPromptVisible)
        assertEquals(AuthMode.LOGIN, state.authMode)
    }

    @Test
    fun authModeCanSwitchToRegisterAndBackToLogin() = runTest {
        val controller = AppStateController(FakeAppDataSource())

        controller.showRegisterAuthMode()
        assertEquals(AuthMode.REGISTER, controller.state.value.authMode)

        controller.showLoginAuthMode()
        assertEquals(AuthMode.LOGIN, controller.state.value.authMode)
    }

    @Test
    fun dismissAuthPromptResetsAuthModeToLoginBeforeNextOpen() = runTest {
        val controller = AppStateController(FakeAppDataSource())

        controller.showRegisterAuthMode()
        controller.showAuthPrompt()
        controller.dismissAuthPrompt()
        controller.showAuthPrompt()

        val state = controller.state.value
        assertTrue(state.authPromptVisible)
        assertEquals(AuthMode.LOGIN, state.authMode)
    }

    @Test
    fun loginSuccessStoresSessionAndLoadsHomeShelf() = runTest {
        val dataSource = FakeAppDataSource()
        val controller = AppStateController(dataSource)

        controller.login("+1", "4155550101", "Password123")

        val state = controller.state.value
        assertEquals(AppScreen.HOME, state.screen)
        assertEquals("+14155550101", state.session?.username)
        assertEquals(listOf("book-1", "book-2"), state.homeShelf.map { it.id })
        assertFalse(state.isLoading)
        assertNull(state.errorMessage)
        assertEquals(listOf("login:+1:4155550101", "home", "home:cache:save"), dataSource.calls)
    }

    @Test
    fun loginSuccessKeepsSessionWhenHomeShelfFails() = runTest {
        val dataSource = FakeAppDataSource(homeError = IllegalStateException("content provider returned 502"))
        val controller = AppStateController(dataSource)

        controller.login("+1", "4155550101", "Password123")

        val state = controller.state.value
        assertEquals(AppScreen.HOME, state.screen)
        assertEquals("+14155550101", state.session?.username)
        assertEquals(emptyList(), state.homeShelf)
        assertEquals("内容暂时加载失败，可以稍后刷新。", state.errorMessage)
        assertFalse(state.isLoading)
        assertEquals(listOf("login:+1:4155550101", "home"), dataSource.calls)
    }

    @Test
    fun publicRegisterDoesNotCreateSessionOrLoadHomeShelf() = runTest {
        val dataSource = FakeAppDataSource(homeError = IllegalStateException("content provider returned 502"))
        val controller = AppStateController(dataSource)

        controller.register("+1", "4155550100", "Password123", "000000")

        val state = controller.state.value
        assertEquals(AppScreen.HOME, state.screen)
        assertNull(state.session)
        assertEquals(emptyList(), state.homeShelf)
        assertEquals("Registration completed. Account access must be opened internally.", state.errorMessage)
        assertFalse(state.isLoading)
        assertEquals(listOf("register:+1:4155550100"), dataSource.calls)
    }

    @Test
    fun loginFailureKeepsCurrentScreenAndRecordsError() = runTest {
        val dataSource = FakeAppDataSource(loginError = IllegalStateException("bad credentials"))
        val controller = AppStateController(dataSource)

        controller.login("+1", "4155550101", "wrong")

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

        controller.login("+1", "4155550101", "wrong", rememberPassword = false)

        val state = controller.state.value
        assertEquals(AppScreen.HOME, state.screen)
        assertEquals("手机号或密码错误", state.errorMessage)
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
    fun blockedAccountErrorClearsSessionAndStopsProtectedActions() = runTest {
        val dataSource = FakeAppDataSource(
            restoredSession = AuthSession(username = "+14155550101", token = "blocked-token", tokenType = "Bearer"),
        )
        dataSource.accountError = ApiClientException(403, null, "user disabled")
        val controller = AppStateController(dataSource)
        controller.restoreSession()
        dataSource.calls.clear()

        controller.openAccount()

        val state = controller.state.value
        assertEquals(AppScreen.ACCOUNT, state.screen)
        assertNull(state.session)
        assertEquals("账号不可操作，请联系支持。", state.errorMessage)
        assertFalse(state.authPromptVisible)
        assertFalse(state.isLoading)
        assertEquals(listOf("history", "clear"), dataSource.calls)
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

        controller.login("+1", "4155550101", "Password123", rememberPassword = true)

        assertEquals(
            SavedCredentials(
                username = "+14155550101",
                countryCode = "+1",
                phoneNumber = "4155550101",
                password = "Password123",
                rememberPassword = true,
            ),
            dataSource.savedCredentials,
        )
        assertEquals(dataSource.savedCredentials, controller.state.value.savedCredentials)
    }

    @Test
    fun phoneLoginSavesCountryCodePhoneAndPasswordWhenRemembered() = runTest {
        val dataSource = FakeAppDataSource()
        val controller = AppStateController(dataSource)

        controller.login("+44", "2075550101", "Password123", rememberPassword = true)

        assertEquals(
            SavedCredentials(
                username = "+442075550101",
                countryCode = "+44",
                phoneNumber = "2075550101",
                password = "Password123",
                rememberPassword = true,
            ),
            dataSource.savedCredentials,
        )
        assertEquals("+442075550101", controller.state.value.session?.username)
        assertEquals(listOf("login:+44:2075550101", "home", "home:cache:save"), dataSource.calls)
    }

    @Test
    fun publicRegisterSimulatesSmsFlowAndDoesNotCreateLoggedInSession() = runTest {
        val dataSource = FakeAppDataSource()
        val controller = AppStateController(dataSource)

        controller.sendAuthSms("+1", "4155550102")
        controller.register("+1", "4155550102", "Password123", "000000")

        val state = controller.state.value
        assertNull(state.session)
        assertFalse(state.authPromptVisible)
        assertEquals("Registration completed. Account access must be opened internally.", state.errorMessage)
        assertEquals(listOf("sms:PUBLIC_REGISTER:+1:4155550102", "register:+1:4155550102"), dataSource.calls)
    }

    @Test
    fun sendAuthSmsStartsCountdownOnlyAfterSuccessfulRequest() = runTest {
        val dataSource = FakeAppDataSource()
        val controller = AppStateController(dataSource)

        controller.sendAuthSms("+1", "4155550102")

        val state = controller.state.value
        assertEquals(120, state.authSmsCountdownSeconds)
        assertEquals(1, state.authSmsCountdownTrigger)
        assertEquals("Verification code sent. Enter the latest 6-digit code within 120 seconds.", state.errorMessage)
    }

    @Test
    fun sendAuthSmsFailureDoesNotStartCountdown() = runTest {
        val dataSource = FakeAppDataSource(smsError = ApiClientException(400, null, "bad request"))
        val controller = AppStateController(dataSource)

        controller.sendAuthSms("+1", "4155550102")

        val state = controller.state.value
        assertEquals(0, state.authSmsCountdownSeconds)
        assertEquals(0, state.authSmsCountdownTrigger)
        assertEquals("Check phone number, password, and verification code.", state.errorMessage)
    }

    @Test
    fun publicRegisterInvalidVerificationCodeUsesFriendlyEnglishMessage() = runTest {
        val dataSource = FakeAppDataSource(registerError = ApiClientException(400, null, "invalid verification code"))
        val controller = AppStateController(dataSource)

        controller.register("+1", "4155550102", "Password123", "123456")

        assertEquals("Verification code is incorrect.", controller.state.value.errorMessage)
    }

    @Test
    fun publicRegisterInvalidVerificationCodeUsesFriendlyTraditionalChineseMessage() = runTest {
        val dataSource = FakeAppDataSource(registerError = ApiClientException(400, null, "invalid verification code"))
        val controller = AppStateController(dataSource)
        controller.setLanguage(AppLanguage.TRADITIONAL_CHINESE)
        dataSource.calls.clear()

        controller.register("+1", "4155550102", "Password123", "123456")

        assertEquals("驗證碼錯誤。", controller.state.value.errorMessage)
    }

    @Test
    fun publicRegisterBadRequestUsesRegisterFormMessage() = runTest {
        val dataSource = FakeAppDataSource(registerError = ApiClientException(400, null, "bad request"))
        val controller = AppStateController(dataSource)

        controller.register("+1", "4155550102", "Password123", "123456")

        assertEquals("Check phone number, password, and verification code.", controller.state.value.errorMessage)
    }

    @Test
    fun loginSuccessClearsRememberedPasswordWhenDisabled() = runTest {
        val dataSource = FakeAppDataSource(
            savedCredentials = SavedCredentials(username = "demo", password = "old-password", rememberPassword = true),
        )
        val controller = AppStateController(dataSource)

        controller.login("+1", "4155550101", "Password123", rememberPassword = false)

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
    fun slowerSearchResultDoesNotOverwriteNewerSearch() = runTest {
        val firstGate = CompletableDeferred<Unit>()
        val dataSource = FakeAppDataSource()
        dataSource.searchGates["Alpha"] = firstGate
        val controller = AppStateController(dataSource)

        val firstSearch = launch { controller.search("Alpha") }
        runCurrent()
        controller.search("Beta")
        firstGate.complete(Unit)
        firstSearch.join()

        val state = controller.state.value
        assertEquals(AppScreen.SEARCH, state.screen)
        assertEquals("Beta", state.searchQuery)
        assertEquals(listOf("book-2"), state.searchResults.map { it.id })
    }

    @Test
    fun openBookWithSessionDirectlyOpensFirstEpisode() = runTest {
        val dataSource = FakeAppDataSource(restoredSession = AuthSession("demo", "token-demo", "Bearer"))
        dataSource.watchHistory = emptyList()
        val controller = AppStateController(dataSource)
        val book = dataSource.books.first()

        controller.restoreSession()
        dataSource.calls.clear()
        controller.openBook(book)

        val state = controller.state.value
        assertEquals(AppScreen.PLAYER, state.screen)
        assertSame(book, state.selectedBook)
        assertEquals(listOf(1, 2), state.episodes.map { it.number })
        assertEquals(1, state.selectedEpisode?.number)
        assertEquals("https://media.local/book-1/1.m3u8", state.currentVideoUrl?.url)
        assertEquals(PlaybackStatus.READY, state.playback.status)
        assertEquals(listOf("episodes:book-1", "history", "snapshot:book-1:1", "video:book-1:1", "like-status:book-1", "favorite-status:book-1", "comments:book-1"), dataSource.calls)
    }

    @Test
    fun openBookWithHistoryResumesWatchedEpisode() = runTest {
        val dataSource = FakeAppDataSource(restoredSession = AuthSession("demo", "token-demo", "Bearer"))
        dataSource.watchHistory = listOf(WatchRecord(bookId = "book-1", bookTitle = "Alpha", episode = 2, progressPercent = 60))
        dataSource.snapshot = WatchEpisodeSnapshot(
            bookId = "book-1",
            episode = 2,
            positionSeconds = 90,
            durationSeconds = 180,
            progressPercent = 50,
            awardedStages = listOf(25),
        )
        val controller = AppStateController(dataSource)

        controller.restoreSession()
        dataSource.calls.clear()
        controller.openBook(dataSource.books.first())

        val state = controller.state.value
        assertEquals(AppScreen.PLAYER, state.screen)
        assertEquals(2, state.selectedEpisode?.number)
        assertEquals(90, state.playback.positionSeconds)
        assertEquals(50, state.playback.progressPercent)
        assertEquals(25, state.playback.lastReportedProgressPercent)
        assertEquals(listOf("episodes:book-1", "history", "snapshot:book-1:2", "video:book-1:2", "like-status:book-1", "favorite-status:book-1", "comments:book-1"), dataSource.calls)
    }

    @Test
    fun openBookWithCompletedHistoryStartsNextEpisode() = runTest {
        val dataSource = FakeAppDataSource(restoredSession = AuthSession("demo", "token-demo", "Bearer"))
        dataSource.watchHistory = listOf(WatchRecord(bookId = "book-1", bookTitle = "Alpha", episode = 1, progressPercent = 100))
        val controller = AppStateController(dataSource)

        controller.restoreSession()
        dataSource.calls.clear()
        controller.openBook(dataSource.books.first())

        val state = controller.state.value
        assertEquals(AppScreen.PLAYER, state.screen)
        assertEquals(2, state.selectedEpisode?.number)
        assertEquals("https://media.local/book-1/2.m3u8", state.currentVideoUrl?.url)
        assertEquals(listOf("episodes:book-1", "history", "snapshot:book-1:2", "video:book-1:2", "like-status:book-1", "favorite-status:book-1", "comments:book-1"), dataSource.calls)
    }

    @Test
    fun openBookWithCompletedLastEpisodeKeepsLastEpisode() = runTest {
        val dataSource = FakeAppDataSource(restoredSession = AuthSession("demo", "token-demo", "Bearer"))
        dataSource.watchHistory = listOf(WatchRecord(bookId = "book-1", bookTitle = "Alpha", episode = 2, progressPercent = 100))
        val controller = AppStateController(dataSource)

        controller.restoreSession()
        dataSource.calls.clear()
        controller.openBook(dataSource.books.first())

        val state = controller.state.value
        assertEquals(AppScreen.PLAYER, state.screen)
        assertEquals(2, state.selectedEpisode?.number)
        assertEquals(listOf("episodes:book-1", "history", "snapshot:book-1:2", "video:book-1:2", "like-status:book-1", "favorite-status:book-1", "comments:book-1"), dataSource.calls)
    }

    @Test
    fun openWatchRecordLoadsBookAndOpensRecordedEpisodeFromAccount() = runTest {
        val dataSource = FakeAppDataSource(restoredSession = AuthSession("demo", "token-demo", "Bearer"))
        val controller = AppStateController(dataSource)
        val record = WatchRecord(bookId = "book-1", bookTitle = "Alpha", episode = 2, progressPercent = 60)

        controller.restoreSession()
        controller.openAccount()
        dataSource.calls.clear()
        controller.openWatchRecord(record)

        val state = controller.state.value
        assertEquals(AppScreen.PLAYER, state.screen)
        assertEquals("book-1", state.selectedBook?.id)
        assertEquals(2, state.selectedEpisode?.number)
        assertEquals(AppScreen.ACCOUNT, state.playerReturnScreen)
        assertEquals("https://media.local/book-1/2.m3u8", state.currentVideoUrl?.url)
        assertEquals(listOf("book:book-1", "episodes:book-1", "snapshot:book-1:2", "video:book-1:2", "like-status:book-1", "favorite-status:book-1", "comments:book-1"), dataSource.calls)
    }

    @Test
    fun openWatchRecordFallsBackWhenRecordedEpisodeIsMissing() = runTest {
        val dataSource = FakeAppDataSource(restoredSession = AuthSession("demo", "token-demo", "Bearer"))
        val controller = AppStateController(dataSource)
        val record = WatchRecord(bookId = "book-1", bookTitle = "Alpha", episode = 99, progressPercent = 60)

        controller.restoreSession()
        controller.openAccount()
        dataSource.calls.clear()
        controller.openWatchRecord(record)

        val state = controller.state.value
        assertEquals(AppScreen.PLAYER, state.screen)
        assertEquals(1, state.selectedEpisode?.number)
        assertEquals("https://media.local/book-1/1.m3u8", state.currentVideoUrl?.url)
    }

    @Test
    fun openWatchRecordFailureStaysOnAccountAndShowsContentError() = runTest {
        val dataSource = FakeAppDataSource(restoredSession = AuthSession("demo", "token-demo", "Bearer"))
        dataSource.bookError = IllegalStateException("book missing")
        val controller = AppStateController(dataSource)
        val record = WatchRecord(bookId = "book-1", bookTitle = "Alpha", episode = 2, progressPercent = 60)

        controller.restoreSession()
        controller.openAccount()
        dataSource.calls.clear()
        controller.openWatchRecord(record)

        val state = controller.state.value
        assertEquals(AppScreen.ACCOUNT, state.screen)
        assertEquals("内容暂时加载失败，可以稍后刷新。", state.errorMessage)
        assertNull(state.currentVideoUrl)
        assertEquals(listOf("book:book-1"), dataSource.calls)
    }

    @Test
    fun openWatchRecordWithoutSessionPromptsLoginAndDoesNotLoadBook() = runTest {
        val dataSource = FakeAppDataSource(restoredSession = null)
        val controller = AppStateController(dataSource)
        val record = WatchRecord(bookId = "book-1", bookTitle = "Alpha", episode = 2, progressPercent = 60)

        controller.openAccount()
        dataSource.calls.clear()
        controller.openWatchRecord(record)

        val state = controller.state.value
        assertEquals(AppScreen.ACCOUNT, state.screen)
        assertTrue(state.authPromptVisible)
        assertFalse(state.isLoading)
        assertTrue(dataSource.calls.isEmpty())
    }

    @Test
    fun openBookFallsBackToFirstEpisodeWhenHistoryFails() = runTest {
        val dataSource = FakeAppDataSource(restoredSession = AuthSession("demo", "token-demo", "Bearer"))
        dataSource.accountError = IllegalStateException("history unavailable")
        val controller = AppStateController(dataSource)

        controller.restoreSession()
        dataSource.calls.clear()
        controller.openBook(dataSource.books.first())

        val state = controller.state.value
        assertEquals(AppScreen.PLAYER, state.screen)
        assertEquals(1, state.selectedEpisode?.number)
        assertNull(state.errorMessage)
        assertEquals(listOf("episodes:book-1", "history", "snapshot:book-1:1", "video:book-1:1", "like-status:book-1", "favorite-status:book-1", "comments:book-1"), dataSource.calls)
    }

    @Test
    fun openBookWithoutSessionKeepsCurrentScreenAndDefersFirstEpisode() = runTest {
        val dataSource = FakeAppDataSource(restoredSession = null)
        val controller = AppStateController(dataSource)

        controller.showSearch()
        controller.openBook(dataSource.books.first())

        val state = controller.state.value
        assertEquals(AppScreen.SEARCH, state.screen)
        assertEquals("book-1", state.selectedBook?.id)
        assertEquals(listOf(1, 2), state.episodes.map { it.number })
        assertTrue(state.authPromptVisible)
        assertEquals(1, state.pendingPlaybackEpisode?.number)
        assertNull(state.currentVideoUrl)
        assertEquals(PlaybackStatus.IDLE, state.playback.status)
        assertEquals(listOf("episodes:book-1"), dataSource.calls)
    }

    @Test
    fun openBookWithNoEpisodesKeepsCurrentScreenAndReportsContentError() = runTest {
        val dataSource = FakeAppDataSource(restoredSession = AuthSession("demo", "token-demo", "Bearer"))
        dataSource.episodes = emptyList()
        val controller = AppStateController(dataSource)

        controller.restoreSession()
        dataSource.calls.clear()
        controller.openBook(dataSource.books.first())

        val state = controller.state.value
        assertEquals(AppScreen.HOME, state.screen)
        assertEquals("book-1", state.selectedBook?.id)
        assertTrue(state.episodes.isEmpty())
        assertNull(state.selectedEpisode)
        assertNull(state.currentVideoUrl)
        assertEquals("内容暂时加载失败，可以稍后刷新。", state.errorMessage)
        assertEquals(listOf("episodes:book-1"), dataSource.calls)
    }

    @Test
    fun repeatedRestoreSessionDoesNotResetCurrentScreen() = runTest {
        val dataSource = FakeAppDataSource(
            restoredSession = AuthSession(username = "demo", token = "token-demo", tokenType = "Bearer"),
        )
        val controller = AppStateController(dataSource)

        controller.restoreSession()
        controller.openBook(dataSource.books.first())
        dataSource.calls.clear()
        controller.restoreSession()

        val state = controller.state.value
        assertEquals(AppScreen.PLAYER, state.screen)
        assertEquals("book-1", state.selectedBook?.id)
        assertEquals(listOf(1, 2), state.episodes.map { it.number })
        assertTrue(dataSource.calls.isEmpty())
    }

    @Test
    fun slowerOpenBookResultDoesNotOverwriteNewerBook() = runTest {
        val firstGate = CompletableDeferred<Unit>()
        val dataSource = FakeAppDataSource(restoredSession = AuthSession("demo", "token-demo", "Bearer"))
        dataSource.episodeGates["book-1"] = firstGate
        val controller = AppStateController(dataSource)
        val firstBook = dataSource.books.first()
        val secondBook = dataSource.books.last()

        controller.restoreSession()
        dataSource.calls.clear()
        val firstOpen = launch { controller.openBook(firstBook) }
        runCurrent()
        controller.openBook(secondBook)
        firstGate.complete(Unit)
        firstOpen.join()

        val state = controller.state.value
        assertEquals(AppScreen.PLAYER, state.screen)
        assertEquals("book-2", state.selectedBook?.id)
        assertEquals(listOf(1, 2), state.episodes.map { it.number })
        assertEquals(1, state.selectedEpisode?.number)
    }

    @Test
    fun slowerOpenBookHistoryDoesNotOverwriteNewerBook() = runTest {
        val firstHistoryGate = CompletableDeferred<Unit>()
        val dataSource = FakeAppDataSource(restoredSession = AuthSession("demo", "token-demo", "Bearer"))
        dataSource.historyGates += firstHistoryGate
        val controller = AppStateController(dataSource)
        val firstBook = dataSource.books.first()
        val secondBook = dataSource.books.last()

        controller.restoreSession()
        dataSource.calls.clear()
        val firstOpen = launch { controller.openBook(firstBook) }
        runCurrent()
        controller.openBook(secondBook)
        firstHistoryGate.complete(Unit)
        firstOpen.join()

        val state = controller.state.value
        assertEquals(AppScreen.PLAYER, state.screen)
        assertEquals("book-2", state.selectedBook?.id)
        assertEquals(1, state.selectedEpisode?.number)
        assertEquals("https://media.local/book-2/1.m3u8", state.currentVideoUrl?.url)
        assertFalse(state.isLoading)
    }

    @Test
    fun slowerOpenBookVideoUrlDoesNotOverwriteNewerBook() = runTest {
        val firstVideoGate = CompletableDeferred<Unit>()
        val dataSource = FakeAppDataSource(restoredSession = AuthSession("demo", "token-demo", "Bearer"))
        dataSource.videoGates["book-1:1"] = firstVideoGate
        val controller = AppStateController(dataSource)
        val firstBook = dataSource.books.first()
        val secondBook = dataSource.books.last()

        controller.restoreSession()
        dataSource.calls.clear()
        val firstOpen = launch { controller.openBook(firstBook) }
        runCurrent()
        controller.openBook(secondBook)
        firstVideoGate.complete(Unit)
        firstOpen.join()

        val state = controller.state.value
        assertEquals(AppScreen.PLAYER, state.screen)
        assertEquals("book-2", state.selectedBook?.id)
        assertEquals(1, state.selectedEpisode?.number)
        assertEquals("https://media.local/book-2/1.m3u8", state.currentVideoUrl?.url)
        assertFalse(state.isLoading)
    }

    @Test
    fun slowerAccountRefreshDoesNotReturnUserToAccountAfterNavigatingAway() = runTest {
        val accountGate = CompletableDeferred<Unit>()
        val dataSource = FakeAppDataSource(
            restoredSession = AuthSession(username = "demo", token = "token-demo", tokenType = "Bearer"),
        )
        dataSource.accountGate = accountGate
        val controller = AppStateController(dataSource)
        controller.restoreSession()
        dataSource.calls.clear()

        val accountOpen = launch { controller.openAccount() }
        runCurrent()
        controller.openHome()
        accountGate.complete(Unit)
        accountOpen.join()

        assertEquals(AppScreen.HOME, controller.state.value.screen)
    }

    @Test
    fun slowerFavoritesLoadDoesNotReturnUserToFavoritesAfterBack() = runTest {
        val favoritesGate = CompletableDeferred<Unit>()
        val dataSource = FakeAppDataSource(
            restoredSession = AuthSession(username = "demo", token = "token-demo", tokenType = "Bearer"),
        )
        dataSource.favoritesGate = favoritesGate
        dataSource.favoritesList = listOf(dataSource.book("book-fav", "Love"))
        val controller = AppStateController(dataSource)
        controller.restoreSession()
        dataSource.calls.clear()

        val favoritesOpen = launch { controller.openFavorites() }
        runCurrent()
        controller.backToAccount()
        favoritesGate.complete(Unit)
        favoritesOpen.join()

        assertEquals(AppScreen.ACCOUNT, controller.state.value.screen)
        assertTrue(controller.state.value.favorites.isEmpty())
    }

    @Test
    fun openPlayerSelectsEpisodeAndLoadsVideoUrl() = runTest {
        val dataSource = FakeAppDataSource(restoredSession = AuthSession("demo", "token-demo", "Bearer"))
        val controller = AppStateController(dataSource)
        val book = dataSource.books.first()

        controller.restoreSession()
        dataSource.calls.clear()
        controller.openBook(book)

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
        assertEquals(listOf("episodes:book-1", "history", "snapshot:book-1:1", "video:book-1:1", "like-status:book-1", "favorite-status:book-1", "comments:book-1"), dataSource.calls)
    }

    @Test
    fun openPlayerSwitchesEpisodeInsideCurrentBook() = runTest {
        val dataSource = FakeAppDataSource(restoredSession = AuthSession("demo", "token-demo", "Bearer"))
        val controller = AppStateController(dataSource)

        controller.restoreSession()
        dataSource.calls.clear()
        controller.openBook(dataSource.books.first())
        dataSource.calls.clear()
        controller.openPlayer(dataSource.episodes[1])

        val state = controller.state.value
        assertEquals(AppScreen.PLAYER, state.screen)
        assertEquals(2, state.selectedEpisode?.number)
        assertEquals("https://media.local/book-1/2.m3u8", state.currentVideoUrl?.url)
        assertEquals(180, state.playback.durationSeconds)
        assertEquals(listOf("snapshot:book-1:2", "video:book-1:2", "like-status:book-1", "favorite-status:book-1", "comments:book-1"), dataSource.calls)
    }

    @Test
    fun slowerOpenPlayerVideoUrlDoesNotOverwriteNewerEpisodeSelection() = runTest {
        val firstVideoGate = CompletableDeferred<Unit>()
        val dataSource = FakeAppDataSource(restoredSession = AuthSession("demo", "token-demo", "Bearer"))
        dataSource.videoGates["book-1:2"] = firstVideoGate
        val controller = AppStateController(dataSource)

        controller.restoreSession()
        controller.openBook(dataSource.books.first())
        dataSource.calls.clear()
        val firstSwitch = launch { controller.openPlayer(dataSource.episodes[1]) }
        runCurrent()
        controller.openPlayer(dataSource.episodes[0])
        firstVideoGate.complete(Unit)
        firstSwitch.join()

        val state = controller.state.value
        assertEquals(AppScreen.PLAYER, state.screen)
        assertEquals(1, state.selectedEpisode?.number)
        assertEquals("https://media.local/book-1/1.m3u8", state.currentVideoUrl?.url)
        assertFalse(state.isLoading)
    }

    @Test
    fun openPlayerWithoutSessionShowsAuthPromptAndDefersEpisode() = runTest {
        val dataSource = FakeAppDataSource(restoredSession = null)
        val controller = AppStateController(dataSource)

        controller.openBook(dataSource.books.first())

        val state = controller.state.value
        assertEquals(AppScreen.HOME, state.screen)
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
        controller.login("+1", "4155550101", "Password123")

        val state = controller.state.value
        assertEquals(AppScreen.PLAYER, state.screen)
        assertEquals("+14155550101", state.session?.username)
        assertFalse(state.authPromptVisible)
        assertNull(state.pendingPlaybackEpisode)
        assertEquals("https://media.local/book-1/1.m3u8", state.currentVideoUrl?.url)
        assertEquals(listOf("episodes:book-1", "login:+1:4155550101", "history", "snapshot:book-1:1", "video:book-1:1", "like-status:book-1", "favorite-status:book-1", "comments:book-1"), dataSource.calls)
    }

    @Test
    fun loginAfterDeferredPlaybackUsesWatchHistoryResumeEpisode() = runTest {
        val dataSource = FakeAppDataSource(restoredSession = null)
        dataSource.watchHistory = listOf(WatchRecord(bookId = "book-1", bookTitle = "Alpha", episode = 2, progressPercent = 60))
        dataSource.snapshot = WatchEpisodeSnapshot(
            bookId = "book-1",
            episode = 2,
            positionSeconds = 90,
            durationSeconds = 180,
            progressPercent = 50,
            awardedStages = listOf(25),
        )
        val controller = AppStateController(dataSource)

        controller.openBook(dataSource.books.first())
        controller.login("+1", "4155550101", "Password123")

        val state = controller.state.value
        assertEquals(AppScreen.PLAYER, state.screen)
        assertEquals("+14155550101", state.session?.username)
        assertEquals(2, state.selectedEpisode?.number)
        assertEquals(90, state.playback.positionSeconds)
        assertEquals("https://media.local/book-1/2.m3u8", state.currentVideoUrl?.url)
        assertEquals(listOf("episodes:book-1", "login:+1:4155550101", "history", "snapshot:book-1:2", "video:book-1:2", "like-status:book-1", "favorite-status:book-1", "comments:book-1"), dataSource.calls)
    }

    @Test
    fun loginAfterDeferredPlaybackStartsNextEpisodeWhenHistoryIsComplete() = runTest {
        val dataSource = FakeAppDataSource(restoredSession = null)
        dataSource.watchHistory = listOf(WatchRecord(bookId = "book-1", bookTitle = "Alpha", episode = 1, progressPercent = 100))
        val controller = AppStateController(dataSource)

        controller.openBook(dataSource.books.first())
        controller.login("+1", "4155550101", "Password123")

        val state = controller.state.value
        assertEquals(AppScreen.PLAYER, state.screen)
        assertEquals(2, state.selectedEpisode?.number)
        assertEquals("https://media.local/book-1/2.m3u8", state.currentVideoUrl?.url)
        assertEquals(listOf("episodes:book-1", "login:+1:4155550101", "history", "snapshot:book-1:2", "video:book-1:2", "like-status:book-1", "favorite-status:book-1", "comments:book-1"), dataSource.calls)
    }

    @Test
    fun loginAfterDeferredPlaybackFallsBackToPendingEpisodeWhenHistoryFails() = runTest {
        val dataSource = FakeAppDataSource(restoredSession = null)
        dataSource.accountError = IllegalStateException("history unavailable")
        val controller = AppStateController(dataSource)

        controller.openBook(dataSource.books.first())
        controller.login("+1", "4155550101", "Password123")

        val state = controller.state.value
        assertEquals(AppScreen.PLAYER, state.screen)
        assertEquals(1, state.selectedEpisode?.number)
        assertEquals("https://media.local/book-1/1.m3u8", state.currentVideoUrl?.url)
        assertEquals(listOf("episodes:book-1", "login:+1:4155550101", "history", "snapshot:book-1:1", "video:book-1:1", "like-status:book-1", "favorite-status:book-1", "comments:book-1"), dataSource.calls)
    }

    @Test
    fun openBookKeepsGlobalLoadingWhileAuthenticatedPlaybackIsResolving() = runTest {
        val videoGate = CompletableDeferred<Unit>()
        val dataSource = FakeAppDataSource(restoredSession = AuthSession("demo", "token-demo", "Bearer"))
        dataSource.videoGates["book-1:1"] = videoGate
        val controller = AppStateController(dataSource)

        controller.restoreSession()
        dataSource.calls.clear()
        val openJob = launch { controller.openBook(dataSource.books.first()) }
        runCurrent()

        val loadingState = controller.state.value
        assertEquals("book-1", loadingState.selectedBook?.id)
        assertEquals(AppScreen.HOME, loadingState.screen)
        assertTrue(loadingState.isLoading)
        assertNull(loadingState.currentVideoUrl)

        videoGate.complete(Unit)
        openJob.join()

        val readyState = controller.state.value
        assertEquals(AppScreen.PLAYER, readyState.screen)
        assertFalse(readyState.isLoading)
    }

    @Test
    fun backToPlaybackSourceReturnsToOriginatingScreen() = runTest {
        val dataSource = FakeAppDataSource(restoredSession = AuthSession("demo", "token-demo", "Bearer"))
        dataSource.watchHistory = listOf(dataSource.watchRecord())
        val controller = AppStateController(dataSource)

        controller.restoreSession()
        controller.showSearch()
        controller.openBook(dataSource.books.first())
        controller.backToPlaybackSource()

        assertEquals(AppScreen.SEARCH, controller.state.value.screen)
    }

    @Test
    fun backToPlaybackSourceDefaultsToHome() {
        val controller = AppStateController(FakeAppDataSource())

        controller.backToPlaybackSource()

        assertEquals(AppScreen.HOME, controller.state.value.screen)
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
        controller.updatePlaybackPosition(positionSeconds = 240, durationSeconds = 200)

        val state = controller.state.value
        assertEquals(200, state.playback.positionSeconds)
        assertEquals(200, state.playback.durationSeconds)
        assertEquals(100, state.playback.progressPercent)
        assertEquals(listOf("episodes:book-1", "history", "snapshot:book-1:1", "video:book-1:1", "like-status:book-1", "favorite-status:book-1", "comments:book-1"), dataSource.calls)
    }

    @Test
    fun updatePlaybackPositionClampsNegativePositionToZero() = runTest {
        val dataSource = FakeAppDataSource(restoredSession = AuthSession("demo", "token-demo", "Bearer"))
        dataSource.watchHistory = listOf(dataSource.watchRecord())
        val controller = AppStateController(dataSource)

        controller.restoreSession()
        dataSource.calls.clear()
        controller.openBook(dataSource.books.first())
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
        dataSource.watchHistory = listOf(dataSource.watchRecord())
        val controller = AppStateController(dataSource)

        controller.restoreSession()
        dataSource.calls.clear()
        controller.openBook(dataSource.books.first())
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
            listOf("episodes:book-1", "history", "snapshot:book-1:1", "video:book-1:1", "like-status:book-1", "favorite-status:book-1", "comments:book-1", "progress:book-1:1:150", "history", "points"),
            dataSource.calls,
        )
    }

    @Test
    fun reportProgressSilentlyAtRewardStageDoesNotUseGlobalLoading() = runTest {
        val dataSource = FakeAppDataSource(restoredSession = AuthSession("demo", "token-demo", "Bearer"))
        dataSource.watchHistory = listOf(dataSource.watchRecord())
        val controller = AppStateController(dataSource)

        controller.restoreSession()
        dataSource.calls.clear()
        controller.openBook(dataSource.books.first())
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
            listOf("episodes:book-1", "history", "snapshot:book-1:1", "video:book-1:1", "like-status:book-1", "favorite-status:book-1", "comments:book-1", "progress:book-1:1:50", "history", "points"),
            dataSource.calls,
        )
    }

    @Test
    fun reportProgressSilentlyCanSettleMultipleCrossedStages() = runTest {
        val dataSource = FakeAppDataSource(restoredSession = AuthSession("demo", "token-demo", "Bearer"))
        dataSource.watchHistory = listOf(dataSource.watchRecord())
        val controller = AppStateController(dataSource)

        controller.restoreSession()
        dataSource.calls.clear()
        controller.openBook(dataSource.books.first())
        controller.updatePlaybackPosition(positionSeconds = 40, durationSeconds = 200)
        controller.reportProgressSilently(positionSeconds = 160, durationSeconds = 200)

        val state = controller.state.value
        assertFalse(state.isLoading)
        assertEquals(160, state.playback.positionSeconds)
        assertEquals(80, state.playback.progressPercent)
        assertEquals(75, state.playback.lastReportedProgressPercent)
        assertEquals(160, state.playback.lastReportedPositionSeconds)
        assertEquals(listOf("episodes:book-1", "history", "snapshot:book-1:1", "video:book-1:1", "like-status:book-1", "favorite-status:book-1", "comments:book-1", "progress:book-1:1:160", "history", "points"), dataSource.calls)
    }

    @Test
    fun reportProgressSilentlySkipsDuplicateCallsWhileReporting() = runTest {
        val dataSource = FakeAppDataSource(restoredSession = AuthSession("demo", "token-demo", "Bearer"))
        dataSource.watchHistory = listOf(dataSource.watchRecord())
        val controller = AppStateController(dataSource)

        controller.restoreSession()
        dataSource.calls.clear()
        controller.openBook(dataSource.books.first())
        dataSource.progressGate = CompletableDeferred()

        val job = launch { controller.reportProgressSilently(positionSeconds = 50, durationSeconds = 200) }
        runCurrent()
        controller.reportProgressSilently(positionSeconds = 60, durationSeconds = 200)

        val reportingState = controller.state.value
        assertFalse(reportingState.isLoading)
        assertEquals(true, reportingState.playback.isRewardReporting)
        assertEquals(listOf("episodes:book-1", "history", "snapshot:book-1:1", "video:book-1:1", "like-status:book-1", "favorite-status:book-1", "comments:book-1", "progress:book-1:1:50"), dataSource.calls)

        dataSource.progressGate?.complete(Unit)
        job.join()

        assertEquals(25, controller.state.value.playback.lastReportedProgressPercent)
        assertEquals(
            listOf("episodes:book-1", "history", "snapshot:book-1:1", "video:book-1:1", "like-status:book-1", "favorite-status:book-1", "comments:book-1", "progress:book-1:1:50", "history", "points"),
            dataSource.calls,
        )
    }

    @Test
    fun reportProgressSilentlyFailureKeepsPlaybackAndDoesNotShowGlobalError() = runTest {
        val dataSource = FakeAppDataSource(restoredSession = AuthSession("demo", "token-demo", "Bearer"))
        dataSource.watchHistory = listOf(dataSource.watchRecord())
        val controller = AppStateController(dataSource)

        controller.restoreSession()
        dataSource.calls.clear()
        controller.openBook(dataSource.books.first())
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
        assertEquals(listOf("episodes:book-1", "history", "snapshot:book-1:1", "video:book-1:1", "like-status:book-1", "favorite-status:book-1", "comments:book-1", "progress:book-1:1:50"), dataSource.calls)
    }

    @Test
    fun reportProgressSilentlySkipsUntilNextUnreportedStage() = runTest {
        val dataSource = FakeAppDataSource(restoredSession = AuthSession("demo", "token-demo", "Bearer"))
        dataSource.watchHistory = listOf(dataSource.watchRecord())
        val controller = AppStateController(dataSource)

        controller.restoreSession()
        dataSource.calls.clear()
        controller.openBook(dataSource.books.first())
        controller.reportProgressSilently(positionSeconds = 150, durationSeconds = 200)
        controller.reportProgressSilently(positionSeconds = 160, durationSeconds = 200)
        controller.reportProgressSilently(positionSeconds = 200, durationSeconds = 200)

        val state = controller.state.value
        assertEquals(100, state.playback.lastReportedProgressPercent)
        assertEquals(2, dataSource.calls.count { it.startsWith("progress:") })
        assertEquals(
            listOf(
                "episodes:book-1",
                "history",
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
        dataSource.watchHistory = listOf(dataSource.watchRecord())
        val controller = AppStateController(dataSource)

        controller.restoreSession()
        dataSource.calls.clear()
        controller.openBook(dataSource.books.first())
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
        assertEquals(listOf("episodes:book-1", "history", "snapshot:book-1:1", "video:book-1:1", "like-status:book-1", "favorite-status:book-1", "comments:book-1", "video:book-1:1"), dataSource.calls)
    }

    @Test
    fun loadAccountSnapshotRefreshesHistoryPointsAndOrders() = runTest {
        val dataSource = FakeAppDataSource()
        val controller = AppStateController(dataSource)

        controller.loadAccountSnapshot()

        val state = controller.state.value
        assertEquals(AppScreen.ACCOUNT, state.screen)
        assertEquals(25, state.pointAccount?.balance)
        assertEquals("TRC20", state.wallet?.network)
        assertEquals(100, state.withdrawalSummary?.minimumPoints)
        assertEquals(listOf("RO202606270001"), state.orders.map { it.orderNo })
        assertEquals(listOf("history", "points", "orders", "wallet", "withdrawal-summary", "withdrawals", "transfers"), dataSource.calls)
    }

    @Test
    fun loadAccountSnapshotKeepsCoreAccountDataWhenCommercialEndpointsFail() = runTest {
        val dataSource = FakeAppDataSource()
        dataSource.walletError = ApiClientException(404, null, "resource not found")
        dataSource.withdrawalSummaryError = ApiClientException(404, null, "resource not found")
        dataSource.withdrawalsError = ApiClientException(404, null, "resource not found")
        dataSource.transfersError = ApiClientException(404, null, "resource not found")
        val controller = AppStateController(dataSource)

        controller.loadAccountSnapshot()

        val state = controller.state.value
        assertEquals(AppScreen.ACCOUNT, state.screen)
        assertEquals(25, state.pointAccount?.balance)
        assertEquals(listOf("RO202606270001"), state.orders.map { it.orderNo })
        assertNull(state.wallet)
        assertNull(state.withdrawalSummary)
        assertTrue(state.withdrawals.isEmpty())
        assertTrue(state.pointTransfers.isEmpty())
        assertNull(state.errorMessage)
        assertFalse(state.isLoading)
    }

    @Test
    fun loadAccountSnapshotLoadsContinueWatchingBooksForPreviewCards() = runTest {
        val dataSource = FakeAppDataSource()
        dataSource.watchHistory = listOf(
            WatchRecord(bookId = "book-1", bookTitle = "Alpha", episode = 1, progressPercent = 35),
            WatchRecord(bookId = "book-2", bookTitle = "Beta", episode = 2, progressPercent = 70),
            WatchRecord(bookId = "book-3", bookTitle = "Gamma", episode = 3, progressPercent = 15),
        )
        dataSource.books = listOf(
            dataSource.book("book-1", "Alpha"),
            dataSource.book("book-2", "Beta"),
            dataSource.book("book-3", "Gamma"),
        )
        val controller = AppStateController(dataSource)

        controller.loadAccountSnapshot()

        val state = controller.state.value
        assertEquals(listOf("book-1", "book-2"), state.continueWatchingBooks.keys.toList())
        assertEquals(
            listOf("history", "points", "orders", "wallet", "withdrawal-summary", "withdrawals", "transfers", "book:book-1", "book:book-2"),
            dataSource.calls,
        )
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
    fun homeSilentRefreshDoesNotReturnToHomeAfterOpeningAccount() = runTest {
        val dataSource = FakeAppDataSource()
        val controller = AppStateController(dataSource)
        controller.refreshHome()
        dataSource.homeGate = CompletableDeferred()

        val homeOpen = launch { controller.openHome() }
        runCurrent()
        controller.openAccount()
        dataSource.homeGate?.complete(Unit)
        homeOpen.join()

        assertEquals(AppScreen.ACCOUNT, controller.state.value.screen)
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
    fun homeInitialLoadDoesNotReturnToHomeAfterOpeningAccount() = runTest {
        val dataSource = FakeAppDataSource()
        dataSource.homeGate = CompletableDeferred()
        val controller = AppStateController(dataSource)

        val homeOpen = launch { controller.openHome() }
        runCurrent()
        controller.openAccount()
        dataSource.homeGate?.complete(Unit)
        homeOpen.join()

        assertEquals(AppScreen.ACCOUNT, controller.state.value.screen)
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
        assertEquals(
            listOf("history", "points", "orders", "wallet", "withdrawal-summary", "withdrawals", "transfers", "history"),
            dataSource.calls,
        )
    }

    @Test
    fun accountSilentRefreshDoesNotReturnToAccountAfterUserLeaves() = runTest {
        val dataSource = FakeAppDataSource(restoredSession = AuthSession("demo", "token-demo", "Bearer"))
        dataSource.watchHistory = listOf(dataSource.watchRecord())
        val controller = AppStateController(dataSource)
        controller.restoreSession()
        dataSource.calls.clear()
        controller.loadAccountSnapshot()
        dataSource.accountGate = CompletableDeferred()

        val job = launch { controller.openAccount() }
        runCurrent()
        controller.openHome()
        dataSource.accountGate?.complete(Unit)
        job.join()

        assertEquals(AppScreen.HOME, controller.state.value.screen)
    }

    @Test
    fun checkApiHealthWritesDiagnosticsState() = runTest {
        val dataSource = FakeAppDataSource()
        val controller = AppStateController(dataSource)

        controller.checkApiHealth()

        val state = controller.state.value
        assertEquals("https://shortlink.hjj888.cc/api/app", state.apiBaseUrl)
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
            controller.login("+1", "4155550101", "Password123")
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
        assertEquals(listOf("language:load", "restore", "credentials:load", "home:cache:load", "home", "home:cache:save"), dataSource.calls)
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
        assertEquals(listOf("language:load", "restore", "credentials:load", "home:cache:load", "home", "home:cache:save"), dataSource.calls)
    }

    @Test
    fun sessionRestoreNetworkLoadDoesNotReturnToHomeAfterOpeningAccount() = runTest {
        val dataSource = FakeAppDataSource()
        dataSource.homeGate = CompletableDeferred()
        val controller = AppStateController(dataSource)

        val restore = launch { controller.restoreSession() }
        runCurrent()
        controller.openAccount()
        dataSource.homeGate?.complete(Unit)
        restore.join()

        assertEquals(AppScreen.ACCOUNT, controller.state.value.screen)
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
        assertEquals(listOf("language:load", "restore", "credentials:load", "home:cache:load", "home"), dataSource.calls)
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
        assertEquals(listOf("language:load", "restore", "credentials:load", "home:cache:load", "home", "home:cache:save"), dataSource.calls)
        assertEquals(listOf(dataSource.books), dataSource.savedHomeShelves)
    }

    @Test
    fun sessionRestoreSilentRefreshDoesNotReturnToHomeAfterOpeningAccount() = runTest {
        val cached = listOf(FakeAppDataSource().book("cached-1", "Cached"))
        val dataSource = FakeAppDataSource(cachedHomeShelf = cached)
        dataSource.homeGate = CompletableDeferred()
        val controller = AppStateController(dataSource)

        val restore = launch { controller.restoreSession() }
        runCurrent()
        controller.openAccount()
        dataSource.homeGate?.complete(Unit)
        restore.join()

        assertEquals(AppScreen.ACCOUNT, controller.state.value.screen)
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
        assertEquals(listOf("language:load", "restore", "credentials:load", "home:cache:load", "home"), dataSource.calls)
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
        controller.login("+1", "4155550101", "Password123")

        val state = controller.state.value
        assertEquals(AppScreen.ACCOUNT, state.screen)
        assertEquals("+14155550101", state.session?.username)
        assertFalse(state.authPromptVisible)
        assertEquals(25, state.pointAccount?.balance)
        assertEquals(listOf("RO202606270001"), state.orders.map { it.orderNo })
        assertEquals(listOf("login:+1:4155550101", "history", "points", "orders", "wallet", "withdrawal-summary", "withdrawals", "transfers"), dataSource.calls)
    }

    @Test
    fun passwordChangeVerificationUsesAuthenticatedPhoneSmsEndpoint() = runTest {
        val dataSource = FakeAppDataSource(
            restoredSession = AuthSession(
                username = "+442075550101",
                token = "token-demo",
                tokenType = "Bearer",
                phoneE164 = "+442075550101",
            ),
        )
        val controller = AppStateController(dataSource)
        controller.restoreSession()
        dataSource.calls.clear()

        controller.sendPasswordChangeVerification()

        assertEquals(listOf("password:sms"), dataSource.calls)
        assertEquals("Verification code sent. Enter the latest 6-digit code within 120 seconds.", controller.state.value.errorMessage)
    }

    @Test
    fun changePasswordClearsSessionSavedCredentialsAndProtectedAccountSnapshot() = runTest {
        val dataSource = FakeAppDataSource(
            restoredSession = AuthSession(
                username = "+14155550101",
                token = "token-demo",
                tokenType = "Bearer",
                phoneE164 = "+14155550101",
            ),
            savedCredentials = SavedCredentials(
                username = "+14155550101",
                countryCode = "+1",
                phoneNumber = "4155550101",
                password = "OldPassword123",
                rememberPassword = true,
            ),
        )
        val controller = AppStateController(dataSource)
        controller.restoreSession()
        controller.loadAccountSnapshot()
        dataSource.calls.clear()

        controller.changePassword("OldPassword123", "NewPassword123", "000000")

        val state = controller.state.value
        assertNull(state.session)
        assertNull(state.savedCredentials)
        assertNull(dataSource.savedCredentials)
        assertNull(state.pointAccount)
        assertNull(state.wallet)
        assertTrue(state.watchHistory.isEmpty())
        assertEquals("Password changed. Sign in again with the new password.", state.errorMessage)
        assertEquals(listOf("password:change", "clear"), dataSource.calls)
    }

    @Test
    fun commercialAccountActionsRefreshSnapshotAndShowSuccessMessage() = runTest {
        val dataSource = FakeAppDataSource(
            restoredSession = AuthSession(
                username = "+14155550101",
                token = "token-demo",
                tokenType = "Bearer",
                phoneE164 = "+14155550101",
            ),
        )
        val controller = AppStateController(dataSource)
        controller.restoreSession()
        dataSource.calls.clear()

        controller.transferPoints("+442075550101", 5)

        val state = controller.state.value
        assertEquals(AppScreen.ACCOUNT, state.screen)
        assertEquals("Transfer submitted.", state.errorMessage)
        assertEquals(
            listOf("transfer:+442075550101:5", "history", "points", "orders", "wallet", "withdrawal-summary", "withdrawals", "transfers"),
            dataSource.calls,
        )
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
        assertEquals(listOf("language:load", "restore", "credentials:load", "home:cache:load", "home", "home:cache:save", "clear"), dataSource.calls)
    }

    @Test
    fun setLanguagePersistsPreferenceRefreshesHomeAndClearsSearchResults() = runTest {
        val dataSource = FakeAppDataSource()
        val controller = AppStateController(dataSource)
        controller.search("Alpha")
        dataSource.calls.clear()

        controller.setLanguage(AppLanguage.TRADITIONAL_CHINESE)

        val state = controller.state.value
        assertEquals(AppLanguage.TRADITIONAL_CHINESE, state.language)
        assertEquals(AppScreen.HOME, state.screen)
        assertEquals("", state.searchQuery)
        assertEquals(emptyList(), state.searchResults)
        assertEquals(listOf("language:save:zh-TW", "home", "home:cache:save"), dataSource.calls)
    }

    @Test
    fun setLanguageKeepsUiLanguageWhenHomeRefreshFails() = runTest {
        val dataSource = FakeAppDataSource()
        val controller = AppStateController(dataSource)
        controller.search("Alpha")
        dataSource.calls.clear()
        dataSource.homeError = IllegalStateException("content unavailable")

        controller.setLanguage(AppLanguage.TRADITIONAL_CHINESE)

        val state = controller.state.value
        assertEquals(AppLanguage.TRADITIONAL_CHINESE, state.language)
        assertEquals(AppScreen.HOME, state.screen)
        assertEquals("", state.searchQuery)
        assertEquals(emptyList(), state.searchResults)
        assertEquals("内容暂时加载失败，可以稍后刷新。", state.errorMessage)
        assertEquals(listOf("language:save:zh-TW", "home"), dataSource.calls)
    }

    @Test
    fun toggleLikeFlipsInteractionStateForCurrentBook() = runTest {
        val dataSource = FakeAppDataSource(restoredSession = AuthSession("demo", "token-demo", "Bearer"))
        val controller = AppStateController(dataSource)
        controller.restoreSession()
        controller.openBook(dataSource.book("book-1", "Alpha"))
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
        controller.toggleFavorite()

        val state = controller.state.value
        assertTrue(state.interaction.favorited)
        assertEquals(1, state.interaction.favoriteCount)
    }

    @Test
    fun socialActionsDoNotShowGlobalLoadingWhileRequestIsInFlight() = runTest {
        val socialGate = CompletableDeferred<Unit>()
        val dataSource = FakeAppDataSource(restoredSession = AuthSession("demo", "token-demo", "Bearer"))
        dataSource.socialGate = socialGate
        val controller = AppStateController(dataSource)
        controller.restoreSession()
        controller.openBook(dataSource.book("book-1", "Alpha"))

        val likeJob = launch { controller.toggleLike() }
        runCurrent()

        assertFalse(controller.state.value.isLoading)

        socialGate.complete(Unit)
        likeJob.join()
        assertTrue(controller.state.value.interaction.liked)
    }

    @Test
    fun submitCommentAppendsCommentAndRefreshesList() = runTest {
        val dataSource = FakeAppDataSource(restoredSession = AuthSession("demo", "token-demo", "Bearer"))
        val controller = AppStateController(dataSource)
        controller.restoreSession()
        controller.openBook(dataSource.book("book-1", "Alpha"))

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
        private val registerError: Throwable? = null,
        private val smsError: Throwable? = null,
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
        var bookError: Throwable? = null
        var homeGate: CompletableDeferred<Unit>? = null
        val searchGates = mutableMapOf<String, CompletableDeferred<Unit>>()
        val episodeGates = mutableMapOf<String, CompletableDeferred<Unit>>()
        val historyGates = mutableListOf<CompletableDeferred<Unit>?>()
        val videoGates = mutableMapOf<String, CompletableDeferred<Unit>>()
        var cachedHomeShelf: List<BookSummary> = cachedHomeShelf
        val savedHomeShelves = mutableListOf<List<BookSummary>>()
        var accountError: Throwable? = null
        var accountGate: CompletableDeferred<Unit>? = null
        var watchHistory: List<WatchRecord> = emptyList()
        var progressError: Throwable? = null
        var progressGate: CompletableDeferred<Unit>? = null
        var pointBalance: Int = 25
        var snapshot: WatchEpisodeSnapshot = WatchEpisodeSnapshot.empty("book-1", 1)
        var snapshotError: Throwable? = null
        var episodes = listOf(
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
        var walletError: Throwable? = null
        var withdrawalSummaryError: Throwable? = null
        var withdrawalsError: Throwable? = null
        var transfersError: Throwable? = null
        var socialGate: CompletableDeferred<Unit>? = null
        var favoritesGate: CompletableDeferred<Unit>? = null
        private val liked = mutableSetOf<String>()
        private val favorited = mutableSetOf<String>()
        private val commentStore = mutableListOf<Comment>()
        var favoritesList: List<BookSummary> = emptyList()
        var language: AppLanguage = AppLanguage.ENGLISH

        override val apiBaseUrl: String = "https://shortlink.hjj888.cc/api/app"

        override suspend fun checkSystemHealth(): ApiHealthStatus {
            calls += "health"
            healthError?.let { throw it }
            return ApiHealthStatus(status = "UP", service = "fake-backend")
        }

        override suspend fun login(countryCode: String, phoneNumber: String, password: String): AuthSession {
            calls += "login:$countryCode:$phoneNumber"
            loginError?.let { throw it }
            val username = "$countryCode$phoneNumber"
            return AuthSession(username = username, token = "token-$username", tokenType = "Bearer")
        }

        override suspend fun register(
            countryCode: String,
            phoneNumber: String,
            password: String,
            verificationCode: String,
        ): RegisterSimulationResult {
            calls += "register:$countryCode:$phoneNumber"
            registerError?.let { throw it }
            return RegisterSimulationResult("SIMULATED")
        }

        override suspend fun sendAuthSms(
            purpose: SmsVerificationPurpose,
            countryCode: String,
            phoneNumber: String,
        ): SmsSendResult {
            calls += "sms:${purpose.name}:$countryCode:$phoneNumber"
            smsError?.let { throw it }
            return SmsSendResult(120)
        }

        override suspend fun sendPasswordChangeVerification(): SmsSendResult {
            calls += "password:sms"
            return SmsSendResult(120)
        }

        override suspend fun changePassword(oldPassword: String, newPassword: String, verificationCode: String) {
            calls += "password:change"
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
            searchGates[query]?.await()
            return books.filter { it.title.contains(query, ignoreCase = true) }
        }

        override suspend fun loadBook(bookId: String): BookSummary {
            calls += "book:$bookId"
            bookError?.let { throw it }
            return books.first { it.id == bookId }
        }

        override suspend fun loadEpisodes(book: BookSummary): List<EpisodeSummary> {
            calls += "episodes:${book.id}"
            episodeGates[book.id]?.await()
            return episodes
        }

        override suspend fun loadVideoUrl(book: BookSummary, episode: EpisodeSummary): VideoUrl {
            calls += "video:${book.id}:${episode.number}"
            videoGates["${book.id}:${episode.number}"]?.await()
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
            if (historyGates.isNotEmpty()) {
                historyGates.removeAt(0)?.await()
            }
            accountGate?.await()
            accountError?.let { throw it }
            return watchHistory
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

        override suspend fun loadWallet(): WalletInfo {
            calls += "wallet"
            walletError?.let { throw it }
            accountError?.let { throw it }
            return WalletInfo("TRC20", "TQ5nNnCnY5Yx7QJk3n4a9b4b8r8t9v1abc", "2026-07-07T00:00:00Z")
        }

        override suspend fun sendWalletVerification(purpose: SmsVerificationPurpose): SmsSendResult {
            calls += "wallet-sms:${purpose.name}"
            return SmsSendResult(120)
        }

        override suspend fun bindWallet(walletAddress: String, verificationCode: String): WalletInfo {
            calls += "wallet:bind:$walletAddress"
            return WalletInfo("TRC20", walletAddress, "2026-07-07T00:00:00Z")
        }

        override suspend fun unbindWallet(verificationCode: String): WalletInfo {
            calls += "wallet:unbind"
            return WalletInfo("TRC20", null, null)
        }

        override suspend fun submitBankCard(holderName: String, cardNumber: String) {
            calls += "bank-card"
            throw IllegalStateException("Bank card withdrawal is not supported")
        }

        override suspend fun loadWithdrawalSummary(): WithdrawalSummary {
            calls += "withdrawal-summary"
            withdrawalSummaryError?.let { throw it }
            accountError?.let { throw it }
            return WithdrawalSummary(pointBalance, 0, pointBalance, 100, "0.001", "TQ5nNnCnY5Yx7QJk3n4a9b4b8r8t9v1abc")
        }

        override suspend fun loadWithdrawals(): List<WithdrawalRecord> {
            calls += "withdrawals"
            withdrawalsError?.let { throw it }
            accountError?.let { throw it }
            return emptyList()
        }

        override suspend fun submitWithdrawal(pointAmount: Int): WithdrawalRecord {
            calls += "withdraw:$pointAmount"
            return WithdrawalRecord(
                id = "withdrawal-1",
                pointAmount = pointAmount,
                usdtAmount = "0.1",
                usdtPerPoint = "0.001",
                network = "TRC20",
                walletAddress = "TQ5nNnCnY5Yx7QJk3n4a9b4b8r8t9v1abc",
                status = "PENDING",
                txHash = null,
                adminNote = null,
                createdAt = "2026-07-07T00:00:00Z",
                reviewedAt = null,
            )
        }

        override suspend fun loadPointTransfers(): List<PointTransferRecord> {
            calls += "transfers"
            transfersError?.let { throw it }
            accountError?.let { throw it }
            return emptyList()
        }

        override suspend fun transferPoints(recipientAccount: String, pointAmount: Int): PointTransferRecord {
            calls += "transfer:$recipientAccount:$pointAmount"
            return PointTransferRecord("transfer-1", "OUT", "+14155550101", recipientAccount, pointAmount, "2026-07-07T00:00:00Z")
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

        override suspend fun loadLanguagePreference(): AppLanguage {
            calls += "language:load"
            return language
        }

        override suspend fun saveLanguagePreference(language: AppLanguage) {
            calls += "language:save:${language.locale}"
            this.language = language
        }

        override suspend fun toggleLike(book: BookSummary): SocialToggleResult {
            calls += "like:${book.id}"
            socialGate?.await()
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
            socialGate?.await()
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
            socialGate?.await()
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
            favoritesGate?.await()
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

        fun watchRecord(
            bookId: String = "book-1",
            bookTitle: String = "Alpha",
            episode: Int = 1,
            progressPercent: Int = 75,
        ): WatchRecord =
            WatchRecord(bookId = bookId, bookTitle = bookTitle, episode = episode, progressPercent = progressPercent)
    }
}
