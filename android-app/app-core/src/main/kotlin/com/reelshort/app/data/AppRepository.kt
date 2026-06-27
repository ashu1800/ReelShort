package com.reelshort.app.data

import com.reelshort.app.network.ReelShortApiClient

class AppRepository(private val apiClient: ReelShortApiClient) {
    var currentToken: String? = null
        private set

    suspend fun login(username: String, password: String): AuthSession {
        val session = apiClient.login(username, password)
        currentToken = session.token
        return session
    }

    suspend fun register(username: String, password: String): AuthSession {
        val session = apiClient.register(username, password)
        currentToken = session.token
        return session
    }

    suspend fun loadHomeShelf(): List<BookSummary> = apiClient.getHomeShelf()

    suspend fun search(query: String): List<BookSummary> = apiClient.search(query)

    suspend fun loadEpisodes(bookId: String): List<EpisodeSummary> = apiClient.getEpisodes(bookId)

    suspend fun loadVideoUrl(bookId: String, episode: Int): VideoUrl = apiClient.getVideoUrl(bookId, episode)

    suspend fun reportWatchProgress(
        bookId: String,
        episode: Int,
        positionSeconds: Int,
        durationSeconds: Int,
    ): WatchProgressReport = apiClient.reportWatchProgress(bookId, episode, positionSeconds, durationSeconds)

    suspend fun loadWatchHistory(): List<WatchRecord> = apiClient.getWatchHistory()

    suspend fun loadPointAccount(): PointAccount = apiClient.getPointAccount()

    suspend fun loadOrders(): List<RechargeOrderSummary> = apiClient.getOrders()
}
