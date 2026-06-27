package com.reelshort.app.state

import com.reelshort.app.data.AppDataSource
import com.reelshort.app.data.AuthSession
import com.reelshort.app.data.BookSummary
import com.reelshort.app.data.EpisodeSummary
import com.reelshort.app.data.PointAccount
import com.reelshort.app.data.PointRecord
import com.reelshort.app.data.RechargeOrderSummary
import com.reelshort.app.data.VideoUrl
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
    fun logoutDelegatesToControllerAndResetsState() = runTest {
        val dataSource = FakeAppDataSource()
        val controller = AppStateController(dataSource)
        val actions = AppUiActions(controller)
        actions.login("demo", "Password123")

        actions.logout()

        assertEquals(AppScreen.LOGIN, controller.state.value.screen)
        assertNull(controller.state.value.session)
        assertEquals(listOf("login:demo", "home", "clear"), dataSource.calls)
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
        private val episodes = listOf(
            EpisodeSummary(number = 1, chapterId = "chapter-1", durationSeconds = 200),
            EpisodeSummary(number = 2, chapterId = "chapter-2", durationSeconds = 180),
        )
        val calls = mutableListOf<String>()

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

        override suspend fun loadVideoUrl(book: BookSummary, episode: EpisodeSummary): VideoUrl =
            VideoUrl("https://media.local/${book.id}/${episode.number}.m3u8", "application/vnd.apple.mpegurl", episode.number, episode.durationSeconds)

        override suspend fun reportWatchProgress(
            book: BookSummary,
            episode: EpisodeSummary,
            positionSeconds: Int,
            durationSeconds: Int,
        ): WatchProgressReport =
            WatchProgressReport(book.id, book.title, book.filteredTitle, episode.number, episode.chapterId, positionSeconds, durationSeconds, 75)

        override suspend fun loadWatchHistory(): List<WatchRecord> =
            listOf(WatchRecord(bookId = "book-1", bookTitle = "Alpha", episode = 1, progressPercent = 75))

        override suspend fun loadPointAccount(): PointAccount =
            PointAccount(balance = 25, records = listOf(PointRecord(amount = 5, reason = "WATCH_REWARD")))

        override suspend fun loadOrders(): List<RechargeOrderSummary> =
            listOf(RechargeOrderSummary(orderNo = "RO202606270001", amountCents = 100, pointAmount = 10, status = "CREATED"))

        override suspend fun restoreSession(): AuthSession? = null

        override suspend fun clearSession() {
            calls += "clear"
        }
    }
}
