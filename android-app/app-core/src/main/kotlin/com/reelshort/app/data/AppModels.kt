package com.reelshort.app.data

data class AuthSession(
    val username: String,
    val token: String,
    val tokenType: String,
    val phoneE164: String? = null,
)

data class SavedCredentials(
    val username: String,
    val password: String,
    val rememberPassword: Boolean,
)

data class ApiHealthStatus(
    val status: String,
    val service: String? = null,
)

data class CaptchaChallenge(
    val captchaId: String,
    val imageBase64: String,
)

data class BookSummary(
    val id: String,
    val title: String,
    val filteredTitle: String,
    val coverUrl: String?,
    val description: String,
    val chapterCount: Int,
)

data class EpisodeSummary(
    val number: Int,
    val chapterId: String,
    val title: String = "",
    val description: String = "",
    val durationSeconds: Int = 0,
)

data class VideoUrl(
    val url: String,
    val contentType: String,
    val episode: Int,
    val durationSeconds: Int,
)

data class WatchProgressReport(
    val bookId: String,
    val bookTitle: String,
    val filteredTitle: String,
    val episode: Int,
    val chapterId: String,
    val positionSeconds: Int,
    val durationSeconds: Int,
    val progressPercent: Int,
    val rewardClaimed: Boolean = false,
    val rewardStatus: WatchRewardStatus = WatchRewardStatus.NOT_COMPLETE,
    val awardedPoints: Int = 0,
)

enum class WatchRewardStatus {
    NOT_COMPLETE,
    AWARDED,
    AWARDED_PARTIAL,
    ALREADY_CLAIMED,
    DAILY_LIMIT_REACHED,
    DURATION_UNAVAILABLE,
    UNKNOWN,

    ;

    companion object {
        fun fromApi(value: String?): WatchRewardStatus {
            val normalized = value?.trim()?.uppercase().orEmpty()
            return if (normalized.isBlank()) {
                NOT_COMPLETE
            } else {
                entries.firstOrNull { it.name == normalized } ?: UNKNOWN
            }
        }
    }

    fun isClaimed(): Boolean = this == AWARDED || this == AWARDED_PARTIAL ||
        this == ALREADY_CLAIMED || this == DAILY_LIMIT_REACHED
}

data class WatchRecord(
    val bookId: String,
    val bookTitle: String,
    val episode: Int,
    val progressPercent: Int,
)

data class WatchEpisodeSnapshot(
    val bookId: String,
    val episode: Int,
    val positionSeconds: Int,
    val durationSeconds: Int,
    val progressPercent: Int,
    val awardedStages: List<Int>,
    val rewardClaimed: Boolean = false,
    val rewardStatus: WatchRewardStatus = WatchRewardStatus.NOT_COMPLETE,
    val awardedPoints: Int = 0,
) {
    companion object {
        fun empty(bookId: String, episode: Int): WatchEpisodeSnapshot =
            WatchEpisodeSnapshot(
                bookId = bookId,
                episode = episode,
                positionSeconds = 0,
                durationSeconds = 0,
                progressPercent = 0,
                awardedStages = emptyList(),
                rewardClaimed = false,
                rewardStatus = WatchRewardStatus.NOT_COMPLETE,
                awardedPoints = 0,
            )
    }
}

data class PointAccount(
    val balance: Int,
    val frozenPoints: Int = 0,
    val availablePoints: Int = balance - frozenPoints,
    val records: List<PointRecord>,
)

data class PointRecord(
    val amount: Int,
    val reason: String?,
)

data class RechargeOrderSummary(
    val orderNo: String,
    val amountCents: Int,
    val pointAmount: Int,
    val status: String,
)

data class WalletInfo(
    val network: String,
    val walletAddress: String?,
    val updatedAt: String?,
    val vipUntil: String? = null,
    val vipPriceUsdt: String? = null,
    val vipCollectionAddress: String? = null,
)

data class VipOrder(
    val id: String,
    val orderNo: String,
    val usdtAmount: String,
    val payableAmount: String? = null,
    val status: String,
    val paymentMethod: String,
    val txHash: String?,
    val createdAt: String,
    val confirmedAt: String?,
    val expiresAt: String? = null,
    val receivingNetwork: String? = null,
    val receivingWalletAddress: String? = null,
    val tokenContractAddress: String? = null,
    val baseUsdtAmount: String? = null,
)

data class WithdrawalSummary(
    val balance: Int,
    val frozenPoints: Int,
    val availablePoints: Int,
    val minimumPoints: Int,
    val usdtPerPoint: String,
    val walletAddress: String?,
    val cnyPerPoint: String? = null,
    val cnyPerUsd: String? = null,
    val minimumUsd: String? = null,
    val feePercent: Int = 0,
)

data class WithdrawalRecord(
    val id: String,
    val pointAmount: Int,
    val usdtAmount: String,
    val usdtPerPoint: String,
    val network: String,
    val walletAddress: String,
    val status: String,
    val txHash: String?,
    val adminNote: String?,
    val createdAt: String,
    val reviewedAt: String?,
)

/** 单级文字评论。 */
data class Comment(
    val id: String,
    val username: String,
    val content: String,
    val createdAt: String,
)

/** 当前剧的互动状态：是否已点赞/收藏及计数。 */
data class BookInteractionState(
    val liked: Boolean = false,
    val likeCount: Int = 0,
    val favorited: Boolean = false,
    val favoriteCount: Int = 0,
)

/** 点赞/收藏切换接口返回的单项结果。 */
data class SocialToggleResult(
    val active: Boolean,
    val count: Int,
)
