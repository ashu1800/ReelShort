package com.reelshort.app.network.dto

import kotlinx.serialization.Serializable

@Serializable
data class BackendApiResponse<T>(
    val code: Int,
    val message: String,
    val data: T? = null,
)

@Serializable
data class AuthRequestDto(
    val username: String,
    val password: String,
)

@Serializable
data class RegisterRequestDto(
    val username: String,
    val password: String,
    val captchaId: String,
    val captchaAnswer: String,
)

@Serializable
data class AuthSessionDto(
    val username: String,
    val token: String,
    val tokenType: String,
    val userId: String? = null,
    val phoneE164: String? = null,
)

@Serializable
data class CaptchaResponseDto(
    val captchaId: String,
    val imageBase64: String,
)

@Serializable
data class PasswordChangeRequestDto(
    val oldPassword: String,
    val newPassword: String,
)

@Serializable
data class ApiHealthStatusDto(
    val status: String,
    val service: String? = null,
)

@Serializable
data class ContentBookDto(
    val bookId: String,
    val title: String,
    val filteredTitle: String,
    val coverUrl: String? = null,
    val description: String = "",
    val chapterCount: Int,
)

@Serializable
data class ContentEpisodeDto(
    val episode: Int,
    val chapterId: String,
    val title: String = "",
    val description: String = "",
)

@Serializable
data class ContentVideoDto(
    val videoUrl: String,
    val episode: Int,
    val duration: Int,
    val nextEpisode: ContentEpisodeDto? = null,
)

@Serializable
data class WatchProgressRequestDto(
    val bookId: String,
    val bookTitle: String,
    val filteredTitle: String,
    val episodeNum: Int,
    val chapterId: String,
    val positionSeconds: Int,
    val durationSeconds: Int,
)

@Serializable
data class WatchRecordDto(
    val bookId: String,
    val bookTitle: String,
    val filteredTitle: String,
    val episodeNum: Int,
    val chapterId: String,
    val positionSeconds: Int,
    val durationSeconds: Int,
    val progressPercent: Int,
    val rewardClaimed: Boolean = false,
    val rewardStatus: String? = null,
    val awardedPoints: Int = 0,
)

@Serializable
data class WatchEpisodeSnapshotDto(
    val bookId: String,
    val episodeNum: Int,
    val positionSeconds: Int,
    val durationSeconds: Int,
    val progressPercent: Int,
    val awardedStages: List<Int> = emptyList(),
    val rewardClaimed: Boolean = false,
    val rewardStatus: String? = null,
    val awardedPoints: Int = 0,
)

@Serializable
data class PointAccountDto(
    val balance: Int,
    val frozenPoints: Int = 0,
    val availablePoints: Int = balance - frozenPoints,
)

@Serializable
data class PointRecordDto(
    val amount: Int,
    val reason: String? = null,
)

@Serializable
data class RechargeOrderDto(
    val orderNo: String,
    val amountCents: Int,
    val pointAmount: Int,
    val status: String,
)

@Serializable
data class WalletResponseDto(
    val network: String,
    val walletAddress: String? = null,
    val updatedAt: String? = null,
    val vipUntil: String? = null,
    val vipPriceUsdt: String? = null,
)

@Serializable
data class WalletBindRequestDto(
    val walletAddress: String,
)

@Serializable
data class VipOrderDto(
    val id: String,
    val orderNo: String,
    val usdtAmount: String,
    val status: String,
    val paymentMethod: String,
    val txHash: String? = null,
    val createdAt: String,
    val confirmedAt: String? = null,
)

@Serializable
data class BankCardBindRequestDto(
    val cardNumber: String,
    val expiryMonth: String,
    val expiryYear: String,
    val cvv: String,
    val holderName: String,
)

@Serializable
data class WithdrawalSummaryDto(
    val balance: Int,
    val frozenPoints: Int,
    val availablePoints: Int,
    val minimumPoints: Int,
    val usdtPerPoint: String,
    val walletAddress: String? = null,
    val cnyPerPoint: String? = null,
    val cnyPerUsd: String? = null,
    val minimumUsd: String? = null,
)

@Serializable
data class WithdrawalDto(
    val id: String,
    val pointAmount: Int,
    val usdtAmount: String,
    val usdtPerPoint: String,
    val network: String,
    val walletAddress: String,
    val status: String,
    val txHash: String? = null,
    val adminNote: String? = null,
    val createdAt: String,
    val reviewedAt: String? = null,
)

@Serializable
data class WithdrawalCreateRequestDto(
    val pointAmount: Int,
)

@Serializable
data class PointTransferDto(
    val id: String,
    val direction: String,
    val senderAccount: String,
    val recipientAccount: String,
    val pointAmount: Int,
    val createdAt: String,
)

@Serializable
data class PointTransferRequestDto(
    val recipientAccount: String,
    val pointAmount: Int,
)

@Serializable
data class SocialToggleDto(
    val active: Boolean,
    val count: Int,
)

@Serializable
data class FavoriteRequestDto(
    val bookTitle: String,
    val filteredTitle: String,
    val coverUrl: String? = null,
    val chapterCount: Int,
)

@Serializable
data class CommentRequestDto(
    val content: String,
)

@Serializable
data class CommentDto(
    val id: String,
    val username: String,
    val content: String,
    val createdAt: String,
)

@Serializable
data class FavoriteBookDto(
    val bookId: String,
    val bookTitle: String,
    val filteredTitle: String,
    val coverUrl: String? = null,
    val chapterCount: Int,
    val createdAt: String,
)
