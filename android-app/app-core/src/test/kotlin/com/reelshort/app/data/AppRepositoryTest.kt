package com.reelshort.app.data

import com.reelshort.app.network.FakeReelShortApiClient
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AppRepositoryTest {
    @Test
    fun loginStoresBearerTokenForLaterRequests() = runTest {
        val repository = AppRepository(FakeReelShortApiClient())

        val session = repository.login("demo", "Password123")

        assertEquals("demo", session.username)
        assertEquals("Bearer", session.tokenType)
        assertEquals(session.token, repository.currentToken)
    }

    @Test
    fun homeShelfAndSearchUseSpringBootFacingClientBoundary() = runTest {
        val repository = AppRepository(FakeReelShortApiClient())

        val home = repository.loadHomeShelf()
        val search = repository.search("Alpha")

        assertTrue(home.isNotEmpty())
        assertEquals(listOf("book-1"), search.map { it.id })
    }

    @Test
    fun episodesAndVideoUrlAreLoadedThroughRepository() = runTest {
        val repository = AppRepository(FakeReelShortApiClient())

        val episodes = repository.loadEpisodes("book-1")
        val videoUrl = repository.loadVideoUrl("book-1", 1)

        assertEquals(8, episodes.size)
        assertEquals(1, episodes.first().number)
        assertTrue(videoUrl.url.startsWith("https://springboot.local/video/"))
    }

    @Test
    fun watchProgressPointsAndOrdersAreDelegatedToClient() = runTest {
        val repository = AppRepository(FakeReelShortApiClient())

        val report = repository.reportWatchProgress("book-1", 1, 135, 180)
        val history = repository.loadWatchHistory()
        val points = repository.loadPointAccount()
        val orders = repository.loadOrders()

        assertEquals(75, report.progressPercent)
        assertNotNull(history.firstOrNull { it.bookId == "book-1" })
        assertEquals(18, points.balance)
        assertEquals("RO202606270001", orders.single().orderNo)
    }
}
