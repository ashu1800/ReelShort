package com.reelshort.app.network

import com.reelshort.app.data.AuthSession
import com.reelshort.app.data.ApiHealthStatus
import com.reelshort.app.data.BookSummary
import com.reelshort.app.data.CaptchaChallenge
import com.reelshort.app.data.Comment
import com.reelshort.app.data.EpisodeSummary
import com.reelshort.app.data.PointAccount
import com.reelshort.app.data.RechargeOrderSummary
import com.reelshort.app.data.SocialToggleResult
import com.reelshort.app.data.VideoUrl
import com.reelshort.app.data.VipOrder
import com.reelshort.app.data.WatchEpisodeSnapshot
import com.reelshort.app.data.WatchProgressReport
import com.reelshort.app.data.WatchRecord
import com.reelshort.app.data.WalletInfo
import com.reelshort.app.data.WithdrawalRecord
import com.reelshort.app.data.WithdrawalSummary
import com.reelshort.app.data.PointTransferRecord

interface ReelShortApiClient {
    suspend fun checkSystemHealth(): ApiHealthStatus

    suspend fun login(username: String, password: String): AuthSession

    suspend fun register(
        username: String,
        password: String,
        captchaId: String,
        captchaAnswer: String,
    ): AuthSession

    suspend fun fetchCaptcha(): CaptchaChallenge

    suspend fun changePassword(oldPassword: String, newPassword: String)

    suspend fun getHomeShelf(locale: String = "en"): List<BookSummary>

    suspend fun search(query: String, locale: String = "en"): List<BookSummary>

    suspend fun getBook(bookId: String, locale: String = "en"): BookSummary

    suspend fun getEpisodes(bookId: String, filteredTitle: String, locale: String = "en"): List<EpisodeSummary>

    suspend fun getVideoUrl(
        bookId: String,
        episode: Int,
        filteredTitle: String,
        chapterId: String,
        locale: String = "en",
    ): VideoUrl

    suspend fun getEpisodeSnapshot(bookId: String, episode: Int): WatchEpisodeSnapshot

    suspend fun reportWatchProgress(
        bookId: String,
        bookTitle: String,
        filteredTitle: String,
        episode: Int,
        chapterId: String,
        positionSeconds: Int,
        durationSeconds: Int,
    ): WatchProgressReport

    suspend fun getWatchHistory(): List<WatchRecord>

    suspend fun getPointAccount(): PointAccount

    suspend fun getOrders(): List<RechargeOrderSummary>

    suspend fun getWallet(): WalletInfo

    suspend fun bindWallet(walletAddress: String): WalletInfo

    suspend fun unbindWallet(): WalletInfo

    suspend fun createVipOrder(): VipOrder

    suspend fun getVipOrders(): List<VipOrder>

    suspend fun getLatestVipOrder(): VipOrder?

    suspend fun submitBankCard(holderName: String, cardNumber: String, expiryMonth: String, expiryYear: String, cvv: String)

    suspend fun getWithdrawalSummary(): WithdrawalSummary

    suspend fun getWithdrawals(): List<WithdrawalRecord>

    suspend fun submitWithdrawal(pointAmount: Int): WithdrawalRecord

    suspend fun getPointTransfers(): List<PointTransferRecord>

    suspend fun transferPoints(recipientAccount: String, pointAmount: Int): PointTransferRecord

    suspend fun toggleLike(bookId: String): SocialToggleResult

    suspend fun getLikeStatus(bookId: String): SocialToggleResult

    suspend fun toggleFavorite(
        bookId: String,
        bookTitle: String,
        filteredTitle: String,
        coverUrl: String?,
        chapterCount: Int,
    ): SocialToggleResult

    suspend fun getFavoriteStatus(bookId: String): SocialToggleResult

    suspend fun addComment(bookId: String, content: String): Comment

    suspend fun listComments(bookId: String): List<Comment>

    suspend fun listMyFavorites(): List<BookSummary>
}
