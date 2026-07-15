package com.reelshort.app.data

import com.reelshort.app.network.GeoIpClient
import com.reelshort.app.network.ReelShortApiClient
import com.reelshort.app.session.CredentialStore
import com.reelshort.app.session.HomeShelfStore
import com.reelshort.app.session.InMemoryCredentialStore
import com.reelshort.app.session.InMemoryHomeShelfStore
import com.reelshort.app.session.InMemoryLanguagePreferenceStore
import com.reelshort.app.session.InMemorySessionStore
import com.reelshort.app.session.LanguagePreferenceStore
import com.reelshort.app.session.SessionStore

class AppRepository(
    private val apiClient: ReelShortApiClient,
    private val sessionStore: SessionStore = InMemorySessionStore(),
    private val credentialStore: CredentialStore = InMemoryCredentialStore(),
    private val homeShelfStore: HomeShelfStore = InMemoryHomeShelfStore(),
    private val languagePreferenceStore: LanguagePreferenceStore = InMemoryLanguagePreferenceStore(),
    private val geoIpClient: GeoIpClient = GeoIpClient(),
    override val apiBaseUrl: String = "",
) : AppDataSource {
    var currentToken: String? = null
        private set

    override suspend fun checkSystemHealth(): ApiHealthStatus = apiClient.checkSystemHealth()

    override suspend fun checkGeoIp(): String? = geoIpClient.detectCountryCode()

    override suspend fun login(username: String, password: String): AuthSession {
        val session = apiClient.login(username, password)
        sessionStore.saveSession(session)
        currentToken = session.token
        return session
    }

    override suspend fun register(
        username: String,
        password: String,
        captchaId: String,
        captchaAnswer: String,
    ): AuthSession {
        val session = apiClient.register(username, password, captchaId, captchaAnswer)
        sessionStore.saveSession(session)
        currentToken = session.token
        return session
    }

    override suspend fun fetchCaptcha(): CaptchaChallenge = apiClient.fetchCaptcha()

    override suspend fun changePassword(oldPassword: String, newPassword: String) {
        apiClient.changePassword(oldPassword, newPassword)
    }

    override suspend fun loadHomeShelf(): List<BookSummary> =
        apiClient.getHomeShelf(loadLanguagePreference().locale)

    override suspend fun loadCachedHomeShelf(): List<BookSummary> = homeShelfStore.loadHomeShelf()

    override suspend fun saveCachedHomeShelf(shelf: List<BookSummary>) {
        homeShelfStore.saveHomeShelf(shelf)
    }

    override suspend fun search(query: String): List<BookSummary> =
        apiClient.search(query, loadLanguagePreference().locale)

    override suspend fun loadBook(bookId: String): BookSummary =
        apiClient.getBook(bookId, loadLanguagePreference().locale)

    override suspend fun loadEpisodes(book: BookSummary): List<EpisodeSummary> =
        apiClient.getEpisodes(book.id, book.filteredTitle, loadLanguagePreference().locale)

    override suspend fun loadVideoUrl(book: BookSummary, episode: EpisodeSummary): VideoUrl =
        apiClient.getVideoUrl(book.id, episode.number, book.filteredTitle, episode.chapterId, loadLanguagePreference().locale)

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

    override suspend fun loadWallet(): WalletInfo = apiClient.getWallet()

    override suspend fun bindWallet(walletAddress: String): WalletInfo =
        apiClient.bindWallet(walletAddress)

    override suspend fun unbindWallet(): WalletInfo = apiClient.unbindWallet()

    override suspend fun createVipOrder(): VipOrder = apiClient.createVipOrder()

    override suspend fun loadVipOrders(): List<VipOrder> = apiClient.getVipOrders()

    override suspend fun loadLatestVipOrder(): VipOrder? = apiClient.getLatestVipOrder()

    override suspend fun submitBankCard(holderName: String, cardNumber: String, expiryMonth: String, expiryYear: String, cvv: String) {
        apiClient.submitBankCard(holderName, cardNumber, expiryMonth, expiryYear, cvv)
    }

    override suspend fun loadWithdrawalSummary(): WithdrawalSummary = apiClient.getWithdrawalSummary()

    override suspend fun loadWithdrawals(): List<WithdrawalRecord> = apiClient.getWithdrawals()

    override suspend fun submitWithdrawal(pointAmount: Int): WithdrawalRecord =
        apiClient.submitWithdrawal(pointAmount)

    override suspend fun toggleLike(book: BookSummary): SocialToggleResult = apiClient.toggleLike(book.id)

    override suspend fun loadLikeStatus(book: BookSummary): SocialToggleResult = apiClient.getLikeStatus(book.id)

    override suspend fun toggleFavorite(book: BookSummary): SocialToggleResult =
        apiClient.toggleFavorite(book.id, book.title, book.filteredTitle, book.coverUrl, book.chapterCount)

    override suspend fun loadFavoriteStatus(book: BookSummary): SocialToggleResult =
        apiClient.getFavoriteStatus(book.id)

    override suspend fun addComment(book: BookSummary, content: String): Comment =
        apiClient.addComment(book.id, content)

    override suspend fun listComments(book: BookSummary): List<Comment> = apiClient.listComments(book.id)

    override suspend fun loadMyFavorites(): List<BookSummary> = apiClient.listMyFavorites()

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

    override suspend fun loadLanguagePreference(): AppLanguage =
        languagePreferenceStore.loadLanguage()

    override suspend fun saveLanguagePreference(language: AppLanguage) {
        languagePreferenceStore.saveLanguage(language)
    }
}
