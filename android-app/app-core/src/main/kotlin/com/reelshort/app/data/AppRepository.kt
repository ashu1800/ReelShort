package com.reelshort.app.data

import com.reelshort.app.network.ReelShortApiClient

class AppRepository(private val apiClient: ReelShortApiClient) : AppDataSource {
    var currentToken: String? = null
        private set

    override suspend fun login(username: String, password: String): AuthSession {
        val session = apiClient.login(username, password)
        currentToken = session.token
        return session
    }

    override suspend fun register(username: String, password: String): AuthSession {
        val session = apiClient.register(username, password)
        currentToken = session.token
        return session
    }

    override suspend fun loadHomeShelf(): List<BookSummary> = apiClient.getHomeShelf()

    override suspend fun search(query: String): List<BookSummary> = apiClient.search(query)

    override suspend fun loadEpisodes(book: BookSummary): List<EpisodeSummary> = apiClient.getEpisodes(book.id, book.filteredTitle)

    override suspend fun loadVideoUrl(book: BookSummary, episode: EpisodeSummary): VideoUrl =
        apiClient.getVideoUrl(book.id, episode.number, book.filteredTitle, episode.chapterId)

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
}
