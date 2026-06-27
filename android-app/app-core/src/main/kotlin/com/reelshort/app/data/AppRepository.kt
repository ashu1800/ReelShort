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

    suspend fun loadEpisodes(book: BookSummary): List<EpisodeSummary> = apiClient.getEpisodes(book.id, book.filteredTitle)

    suspend fun loadVideoUrl(book: BookSummary, episode: EpisodeSummary): VideoUrl =
        apiClient.getVideoUrl(book.id, episode.number, book.filteredTitle, episode.chapterId)

    suspend fun reportWatchProgress(
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

    suspend fun loadWatchHistory(): List<WatchRecord> = apiClient.getWatchHistory()

    suspend fun loadPointAccount(): PointAccount = apiClient.getPointAccount()

    suspend fun loadOrders(): List<RechargeOrderSummary> = apiClient.getOrders()
}
