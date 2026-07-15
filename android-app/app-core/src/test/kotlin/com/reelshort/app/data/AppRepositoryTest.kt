package com.reelshort.app.data

import com.reelshort.app.network.FakeReelShortApiClient
import com.reelshort.app.session.FileSessionStore
import com.reelshort.app.session.HomeShelfStore
import com.reelshort.app.session.InMemoryCredentialStore
import com.reelshort.app.session.InMemoryHomeShelfStore
import com.reelshort.app.session.InMemoryLanguagePreferenceStore
import com.reelshort.app.session.InMemorySessionStore
import com.reelshort.app.session.SessionStore
import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
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
    fun loginPersistsSessionForRepositoryRecreation() = runTest {
        val sessionStore = InMemorySessionStore()
        val firstRepository = AppRepository(FakeReelShortApiClient(), sessionStore)

        val session = firstRepository.login("demo", "Password123")
        val restoredRepository = AppRepository(FakeReelShortApiClient(), sessionStore)

        val restored = restoredRepository.restoreSession()

        assertEquals(session, restored)
        assertEquals(session.token, restoredRepository.currentToken)
        assertEquals(session, sessionStore.loadSession())
    }

    @Test
    fun loginPersistsSessionAcrossFileBackedRepositoryRecreation() = runTest {
        val sessionFile = File(createTempDirectory(prefix = "reelshort-repository-session-test").toFile(), "session.json")
        val firstRepository = AppRepository(FakeReelShortApiClient(), FileSessionStore(sessionFile))

        val session = firstRepository.login("demo", "Password123")
        val restoredRepository = AppRepository(FakeReelShortApiClient(), FileSessionStore(sessionFile))

        val restored = restoredRepository.restoreSession()

        assertEquals(session, restored)
        assertEquals(session.token, restoredRepository.currentToken)
    }

    @Test
    fun registerCreatesAndPersistsSession() = runTest {
        val sessionStore = InMemorySessionStore()
        val repository = AppRepository(FakeReelShortApiClient(), sessionStore)

        val session = repository.register("newuser", "Password123", "captcha-1", "123456")

        assertEquals("newuser", session.username)
        assertEquals(session, sessionStore.loadSession())
        assertEquals(session.token, repository.currentToken)
    }

    @Test
    fun clearSessionRemovesStoredSessionAndMemoryToken() = runTest {
        val sessionStore = InMemorySessionStore()
        val repository = AppRepository(FakeReelShortApiClient(), sessionStore)
        repository.login("demo", "Password123")

        repository.clearSession()

        assertNull(repository.currentToken)
        assertNull(sessionStore.loadSession())
    }

    @Test
    fun savedCredentialsUseSeparateCredentialStore() = runTest {
        val credentialStore = InMemoryCredentialStore()
        val repository = AppRepository(FakeReelShortApiClient(), InMemorySessionStore(), credentialStore)
        val credentials = SavedCredentials(username = "demo", password = "Password123", rememberPassword = true)

        repository.saveCredentials(credentials)

        assertEquals(credentials, repository.loadSavedCredentials())

        repository.clearSavedCredentials()

        assertNull(repository.loadSavedCredentials())
    }

    @Test
    fun languagePreferenceDefaultsToEnglishAndPersists() = runTest {
        val languageStore = InMemoryLanguagePreferenceStore()
        val repository = AppRepository(
            FakeReelShortApiClient(),
            InMemorySessionStore(),
            InMemoryCredentialStore(),
            InMemoryHomeShelfStore(),
            languageStore,
        )

        assertEquals(AppLanguage.ENGLISH, repository.loadLanguagePreference())

        repository.saveLanguagePreference(AppLanguage.ENGLISH)

        val restoredRepository = AppRepository(
            FakeReelShortApiClient(),
            InMemorySessionStore(),
            InMemoryCredentialStore(),
            InMemoryHomeShelfStore(),
            languageStore,
        )
        assertEquals(AppLanguage.ENGLISH, restoredRepository.loadLanguagePreference())
    }

    @Test
    fun loginDoesNotSetMemoryTokenWhenSessionPersistenceFails() = runTest {
        val repository = AppRepository(FakeReelShortApiClient(), FailingSessionStore())

        assertFailsWith<IllegalStateException> {
            repository.login("demo", "Password123")
        }

        assertNull(repository.currentToken)
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
    fun homeShelfCachePersistsAcrossRepositoryRecreation() = runTest {
        val homeShelfStore: HomeShelfStore = InMemoryHomeShelfStore()
        val firstRepository = AppRepository(FakeReelShortApiClient(), InMemorySessionStore(), InMemoryCredentialStore(), homeShelfStore)
        val network = firstRepository.loadHomeShelf()
        firstRepository.saveCachedHomeShelf(network)

        val restoredRepository = AppRepository(FakeReelShortApiClient(), InMemorySessionStore(), InMemoryCredentialStore(), homeShelfStore)
        val cached = restoredRepository.loadCachedHomeShelf()

        assertEquals(network, cached)
        assertTrue(cached.isNotEmpty())
    }

    @Test
    fun episodesAndVideoUrlAreLoadedThroughRepository() = runTest {
        val repository = AppRepository(FakeReelShortApiClient())
        val book = repository.loadHomeShelf().first()

        val episodes = repository.loadEpisodes(book)
        val videoUrl = repository.loadVideoUrl(book, episodes.first())

        assertEquals(8, episodes.size)
        assertEquals(1, episodes.first().number)
        assertTrue(videoUrl.url.startsWith("https://springboot.local/video/"))
    }

    @Test
    fun watchProgressPointsAndOrdersAreDelegatedToClient() = runTest {
        val repository = AppRepository(FakeReelShortApiClient())
        val book = repository.loadHomeShelf().first()
        val episode = repository.loadEpisodes(book).first()

        val report = repository.reportWatchProgress(book, episode, 135, 180)
        val history = repository.loadWatchHistory()
        val points = repository.loadPointAccount()
        val orders = repository.loadOrders()

        assertEquals(75, report.progressPercent)
        assertNotNull(history.firstOrNull { it.bookId == "book-1" })
        assertEquals(18, points.balance)
        assertEquals("RO202606270001", orders.single().orderNo)
    }

    @Test
    fun socialActionsAreDelegatedToClient() = runTest {
        val repository = AppRepository(FakeReelShortApiClient())
        val book = repository.loadHomeShelf().first()

        val like = repository.toggleLike(book)
        assertTrue(like.active)
        val favorite = repository.toggleFavorite(book)
        assertTrue(favorite.active)
        val comment = repository.addComment(book, "nice")
        assertEquals("nice", comment.content)
        assertEquals(1, repository.listComments(book).size)
        assertEquals(1, repository.loadMyFavorites().size)
    }

    private class FailingSessionStore : SessionStore {
        override suspend fun loadSession(): AuthSession? = null

        override suspend fun saveSession(session: AuthSession) {
            throw IllegalStateException("session persistence failed")
        }

        override suspend fun clearSession() = Unit
    }
}
