package com.reelshort.app.data

interface AppDataSource {
    val apiBaseUrl: String

    suspend fun checkSystemHealth(): ApiHealthStatus

    suspend fun login(username: String, password: String): AuthSession

    suspend fun register(username: String, password: String): AuthSession

    suspend fun loadHomeShelf(): List<BookSummary>

    suspend fun search(query: String): List<BookSummary>

    suspend fun loadEpisodes(book: BookSummary): List<EpisodeSummary>

    suspend fun loadVideoUrl(book: BookSummary, episode: EpisodeSummary): VideoUrl

    suspend fun loadEpisodeSnapshot(book: BookSummary, episode: EpisodeSummary): WatchEpisodeSnapshot

    suspend fun reportWatchProgress(
        book: BookSummary,
        episode: EpisodeSummary,
        positionSeconds: Int,
        durationSeconds: Int,
    ): WatchProgressReport

    suspend fun loadWatchHistory(): List<WatchRecord>

    suspend fun loadPointAccount(): PointAccount

    suspend fun loadOrders(): List<RechargeOrderSummary>

    suspend fun toggleLike(book: BookSummary): SocialToggleResult

    suspend fun loadLikeStatus(book: BookSummary): SocialToggleResult

    suspend fun toggleFavorite(book: BookSummary): SocialToggleResult

    suspend fun loadFavoriteStatus(book: BookSummary): SocialToggleResult

    suspend fun addComment(book: BookSummary, content: String): Comment

    suspend fun listComments(book: BookSummary): List<Comment>

    suspend fun loadMyFavorites(): List<BookSummary>

    suspend fun restoreSession(): AuthSession?

    suspend fun clearSession()

    suspend fun loadSavedCredentials(): SavedCredentials?

    suspend fun saveCredentials(credentials: SavedCredentials)

    suspend fun clearSavedCredentials()
}
