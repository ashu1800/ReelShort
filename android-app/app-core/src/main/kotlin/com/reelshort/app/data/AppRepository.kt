package com.reelshort.app.data

import com.reelshort.app.network.ReelShortApiClient
import com.reelshort.app.session.CredentialStore
import com.reelshort.app.session.InMemoryCredentialStore
import com.reelshort.app.session.InMemorySessionStore
import com.reelshort.app.session.SessionStore

class AppRepository(
    private val apiClient: ReelShortApiClient,
    private val sessionStore: SessionStore = InMemorySessionStore(),
    private val credentialStore: CredentialStore = InMemoryCredentialStore(),
    override val apiBaseUrl: String = "",
) : AppDataSource {
    var currentToken: String? = null
        private set

    override suspend fun checkSystemHealth(): ApiHealthStatus = apiClient.checkSystemHealth()

    override suspend fun login(username: String, password: String): AuthSession {
        val session = apiClient.login(username, password)
        sessionStore.saveSession(session)
        currentToken = session.token
        return session
    }

    override suspend fun register(username: String, password: String): AuthSession {
        val session = apiClient.register(username, password)
        sessionStore.saveSession(session)
        currentToken = session.token
        return session
    }

    override suspend fun loadHomeShelf(): List<BookSummary> = apiClient.getHomeShelf()

    override suspend fun search(query: String): List<BookSummary> = apiClient.search(query)

    override suspend fun loadEpisodes(book: BookSummary): List<EpisodeSummary> = apiClient.getEpisodes(book.id, book.filteredTitle)

    override suspend fun loadVideoUrl(book: BookSummary, episode: EpisodeSummary): VideoUrl =
        apiClient.getVideoUrl(book.id, episode.number, book.filteredTitle, episode.chapterId)

    override suspend fun loadEpisodeSnapshot(book: BookSummary, episode: EpisodeSummary): WatchEpisodeSnapshot =
        apiClient.getEpisodeSnapshot(book.id, episode.number)

    override suspend fun reportWatchProgress(
        book: BookSummary,
        episode: EpisodeSummary,
        positionSeconds: Int,
        durationSeconds: Int,
    ): WatchProgressReport = apiClient.reportWatchProgress(
        book.id,
        book.title,
        book.filteredTitle,
        episode.number,
        episode.chapterId,
        positionSeconds,
        durationSeconds,
    )

    override suspend fun loadWatchHistory(): List<WatchRecord> = apiClient.getWatchHistory()

    override suspend fun loadPointAccount(): PointAccount = apiClient.getPointAccount()

    override suspend fun loadOrders(): List<RechargeOrderSummary> = apiClient.getOrders()

    override suspend fun restoreSession(): AuthSession? {
        val session = sessionStore.loadSession()
        currentToken = session?.token
        return session
    }

    override suspend fun clearSession() {
        sessionStore.clearSession()
        currentToken = null
    }

    override suspend fun loadSavedCredentials(): SavedCredentials? =
        credentialStore.loadCredentials()

    override suspend fun saveCredentials(credentials: SavedCredentials) {
        credentialStore.saveCredentials(credentials)
    }

    override suspend fun clearSavedCredentials() {
        credentialStore.clearCredentials()
    }
}
