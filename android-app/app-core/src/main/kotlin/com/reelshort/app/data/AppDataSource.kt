package com.reelshort.app.data

interface AppDataSource {
    val apiBaseUrl: String

    suspend fun checkSystemHealth(): ApiHealthStatus

    suspend fun checkGeoIp(): String?

    suspend fun login(username: String, password: String): AuthSession

    suspend fun register(
        username: String,
        password: String,
        captchaId: String,
        captchaAnswer: String,
    ): AuthSession

    suspend fun fetchCaptcha(): CaptchaChallenge

    suspend fun changePassword(oldPassword: String, newPassword: String)

    suspend fun loadHomeShelf(): List<BookSummary>

    suspend fun loadCachedHomeShelf(): List<BookSummary>

    suspend fun saveCachedHomeShelf(shelf: List<BookSummary>)

    suspend fun search(query: String): List<BookSummary>

    suspend fun loadBook(bookId: String): BookSummary

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

    suspend fun loadWallet(): WalletInfo

    suspend fun bindWallet(walletAddress: String): WalletInfo

    suspend fun unbindWallet(): WalletInfo

    suspend fun createVipOrder(): VipOrder

    suspend fun loadVipOrders(): List<VipOrder>

    suspend fun submitBankCard(holderName: String, cardNumber: String)

    suspend fun loadWithdrawalSummary(): WithdrawalSummary

    suspend fun loadWithdrawals(): List<WithdrawalRecord>

    suspend fun submitWithdrawal(pointAmount: Int): WithdrawalRecord

    suspend fun loadPointTransfers(): List<PointTransferRecord>

    suspend fun transferPoints(recipientAccount: String, pointAmount: Int): PointTransferRecord

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

    suspend fun loadLanguagePreference(): AppLanguage

    suspend fun saveLanguagePreference(language: AppLanguage)
}
