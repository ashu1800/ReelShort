package com.reelshort.app.data

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
    override val apiBaseUrl: String = "",
) : AppDataSource {
    var currentToken: String? = null
        private set

    override suspend fun checkSystemHealth(): ApiHealthStatus = apiClient.checkSystemHealth()

    override suspend fun login(countryCode: String, phoneNumber: String, password: String): AuthSession {
        val session = apiClient.login(countryCode, phoneNumber, password)
        sessionStore.saveSession(session)
        currentToken = session.token
        return session
    }

    override suspend fun register(
        countryCode: String,
        phoneNumber: String,
        password: String,
        verificationCode: String,
    ): RegisterSimulationResult = apiClient.register(countryCode, phoneNumber, password, verificationCode)

    override suspend fun sendAuthSms(
        purpose: SmsVerificationPurpose,
        countryCode: String,
        phoneNumber: String,
    ): SmsSendResult = apiClient.sendAuthSms(purpose, countryCode, phoneNumber)

    override suspend fun changePassword(oldPassword: String, newPassword: String, verificationCode: String) {
        apiClient.changePassword(oldPassword, newPassword, verificationCode)
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

    override suspend fun sendWalletVerification(purpose: SmsVerificationPurpose): SmsSendResult =
        apiClient.sendWalletVerification(purpose)

    override suspend fun bindWallet(walletAddress: String, verificationCode: String): WalletInfo =
        apiClient.bindWallet(walletAddress, verificationCode)

    override suspend fun unbindWallet(verificationCode: String): WalletInfo =
        apiClient.unbindWallet(verificationCode)

    override suspend fun submitBankCard(holderName: String, cardNumber: String) {
        apiClient.submitBankCard(holderName, cardNumber)
    }

    override suspend fun loadWithdrawalSummary(): WithdrawalSummary = apiClient.getWithdrawalSummary()

    override suspend fun loadWithdrawals(): List<WithdrawalRecord> = apiClient.getWithdrawals()

    override suspend fun submitWithdrawal(pointAmount: Int): WithdrawalRecord =
        apiClient.submitWithdrawal(pointAmount)

    override suspend fun loadPointTransfers(): List<PointTransferRecord> = apiClient.getPointTransfers()

    override suspend fun transferPoints(recipientAccount: String, pointAmount: Int): PointTransferRecord =
        apiClient.transferPoints(recipientAccount, pointAmount)

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
