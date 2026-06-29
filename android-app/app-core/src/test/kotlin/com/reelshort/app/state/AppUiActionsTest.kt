package com.reelshort.app.state

import com.reelshort.app.data.AppDataSource
import com.reelshort.app.data.ApiHealthStatus
import com.reelshort.app.data.AuthSession
import com.reelshort.app.data.BookSummary
import com.reelshort.app.data.EpisodeSummary
import com.reelshort.app.data.PointAccount
import com.reelshort.app.data.PointRecord
import com.reelshort.app.data.RechargeOrderSummary
import com.reelshort.app.data.VideoUrl
import com.reelshort.app.data.WatchEpisodeSnapshot
import com.reelshort.app.data.WatchProgressReport
import com.reelshort.app.data.WatchRecord
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

class AppUiActionsTest {
    @Test
    fun loginDelegatesToControllerAndLoadsHome() = runTest {
        val dataSource = FakeAppDataSource()
        val controller = AppStateController(dataSource)
        val actions = AppUiActions(controller)

        actions.login("demo", "Password123")

        assertEquals(AppScreen.HOME, controller.state.value.screen)
        assertEquals("demo", controller.state.value.session?.username)
        assertEquals(listOf("login:demo", "home"), dataSource.calls)
    }

    @Test
    fun searchDelegatesToControllerAndRecordsQuery() = runTest {
        val dataSource = FakeAppDataSource()
        val controller = AppStateController(dataSource)
        val actions = AppUiActions(controller)

        actions.search("Alpha")

        assertEquals(AppScreen.SEARCH, controller.state.value.screen)
        assertEquals("Alpha", controller.state.value.searchQuery)
        assertEquals(listOf("book-1"), controller.state.value.searchResults.map { it.id })
    }

    @Test
    fun showSearchOpensSearchWithoutCallingDataSource() = runTest {
        val dataSource = FakeAppDataSource()
        val controller = AppStateController(dataSource)
        val actions = AppUiActions(controller)

        actions.showSearch()

        assertEquals(AppScreen.SEARCH, controller.state.value.screen)
        assertEquals(emptyList(), dataSource.calls)
    }

    @Test
    fun tabOpenActionsUseNonBlockingControllerEntrypoints() = runTest {
        val dataSource = FakeAppDataSource()
        val controller = AppStateController(dataSource)
        val actions = AppUiActions(controller)

        actions.openHome()
        actions.openAccount()

        assertEquals(AppScreen.ACCOUNT, controller.state.value.screen)
        assertEquals(listOf("home"), dataSource.calls)
    }

    @Test
    fun openBookDelegatesToControllerAndSelectsBook() = runTest {
        val dataSource = FakeAppDataSource()
        val controller = AppStateController(dataSource)
        val actions = AppUiActions(controller)
        val book = dataSource.books.first()

        actions.openBook(book)

        assertEquals(AppScreen.DETAIL, controller.state.value.screen)
        assertSame(book, controller.state.value.selectedBook)
        assertEquals(listOf(1, 2), controller.state.value.episodes.map { it.number })
    }

    @Test
    fun playbackActionsDelegateToController() = runTest {
        val dataSource = FakeAppDataSource()
        val controller = AppStateController(dataSource)
        val actions = AppUiActions(controller)

        actions.login("demo", "Password123")
        dataSource.calls.clear()
        actions.openBook(dataSource.books.first())
        actions.openPlayer(dataSource.episodes.first())
        actions.updatePlaybackPosition(positionSeconds = 100, durationSeconds = 200)
        dataSource.videoUrlVersion = 2
        actions.refreshPlaybackUrl()
        actions.reportProgress(
            positionSeconds = controller.state.value.playback.positionSeconds,
            durationSeconds = controller.state.value.playback.durationSeconds,
        )

        val state = controller.state.value
        assertEquals(100, state.playback.positionSeconds)
        assertEquals(50, state.playback.progressPercent)
        assertEquals(100, state.playback.lastReportedPositionSeconds)
        assertEquals(50, dataSource.lastProgress?.progressPercent)
        assertEquals("https://media.local/book-1/1-v2.m3u8", state.playback.videoUrl?.url)
        assertEquals(
            listOf(
                "episodes:book-1",
                "snapshot:book-1:1",
                "video:book-1:1",
                "video:book-1:1",
                "progress:book-1:1:100",
                "history",
                "points",
            ),
            dataSource.calls,
        )
    }

    @Test
    fun logoutDelegatesToControllerAndResetsState() = runTest {
        val dataSource = FakeAppDataSource()
        val controller = AppStateController(dataSource)
        val actions = AppUiActions(controller)
        actions.login("demo", "Password123")

        actions.logout()

        assertEquals(AppScreen.HOME, controller.state.value.screen)
        assertNull(controller.state.value.session)
        assertEquals(listOf("login:demo", "home", "clear"), dataSource.calls)
    }

    @Test
    fun checkApiHealthDelegatesToController() = runTest {
        val dataSource = FakeAppDataSource()
        val actions = AppUiActions(AppStateController(dataSource))

        actions.checkApiHealth()

        assertEquals("UP", actions.state.value.apiHealthStatus?.status)
        assertEquals(listOf("health"), dataSource.calls)
    }

    private class FakeAppDataSource : AppDataSource {
        val books = listOf(
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
        val episodes = listOf(
            EpisodeSummary(number = 1, chapterId = "chapter-1", durationSeconds = 200),
            EpisodeSummary(number = 2, chapterId = "chapter-2", durationSeconds = 180),
        )
        val calls = mutableListOf<String>()
        var videoUrlVersion: Int = 1
        var lastProgress: WatchProgressReport? = null

        override val apiBaseUrl: String = "http://66.42.99.110:18080/api/app"

        override suspend fun checkSystemHealth(): ApiHealthStatus {
            calls += "health"
            return ApiHealthStatus(status = "UP", service = "fake-backend")
        }

        override suspend fun login(username: String, password: String): AuthSession {
            calls += "login:$username"
            return AuthSession(username = username, token = "token-$username", tokenType = "Bearer")
        }

        override suspend fun register(username: String, password: String): AuthSession {
            calls += "register:$username"
            return AuthSession(username = username, token = "token-$username", tokenType = "Bearer")
        }

        override suspend fun loadHomeShelf(): List<BookSummary> {
            calls += "home"
            return books
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
            return VideoUrl("https://media.local/${book.id}/${episode.number}$suffix.m3u8", "application/vnd.apple.mpegurl", episode.number, episode.durationSeconds)
        }

        override suspend fun loadEpisodeSnapshot(book: BookSummary, episode: EpisodeSummary): WatchEpisodeSnapshot {
            calls += "snapshot:${book.id}:${episode.number}"
            return WatchEpisodeSnapshot.empty(book.id, episode.number)
        }

        override suspend fun reportWatchProgress(
            book: BookSummary,
            episode: EpisodeSummary,
            positionSeconds: Int,
            durationSeconds: Int,
        ): WatchProgressReport {
            calls += "progress:${book.id}:${episode.number}:$positionSeconds"
            val percent = if (durationSeconds <= 0) 0 else ((positionSeconds.toDouble() / durationSeconds) * 100).toInt()
            val progress = WatchProgressReport(
                book.id,
                book.title,
                book.filteredTitle,
                episode.number,
                episode.chapterId,
                positionSeconds,
                durationSeconds,
                percent,
            )
            lastProgress = progress
            return progress
        }

        override suspend fun loadWatchHistory(): List<WatchRecord> {
            calls += "history"
            return listOf(WatchRecord(bookId = "book-1", bookTitle = "Alpha", episode = 1, progressPercent = 75))
        }

        override suspend fun loadPointAccount(): PointAccount {
            calls += "points"
            return PointAccount(balance = 25, records = listOf(PointRecord(amount = 5, reason = "WATCH_REWARD")))
        }

        override suspend fun loadOrders(): List<RechargeOrderSummary> {
            calls += "orders"
            return listOf(RechargeOrderSummary(orderNo = "RO202606270001", amountCents = 100, pointAmount = 10, status = "CREATED"))
        }

        override suspend fun restoreSession(): AuthSession? = null

        override suspend fun clearSession() {
            calls += "clear"
        }
    }
}
