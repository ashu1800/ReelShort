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
data class AuthSessionDto(
    val username: String,
    val token: String,
    val tokenType: String,
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
)

@Serializable
data class WatchEpisodeSnapshotDto(
    val bookId: String,
    val episodeNum: Int,
    val positionSeconds: Int,
    val durationSeconds: Int,
    val progressPercent: Int,
    val awardedStages: List<Int> = emptyList(),
)

@Serializable
data class PointAccountDto(val balance: Int)

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
