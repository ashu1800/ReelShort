package com.reelshort.app.data

data class AuthSession(
    val username: String,
    val token: String,
    val tokenType: String,
)

data class BookSummary(
    val id: String,
    val title: String,
    val filteredTitle: String,
    val coverUrl: String?,
    val description: String,
    val chapterCount: Int,
)

data class EpisodeSummary(val number: Int, val chapterId: String, val durationSeconds: Int = 0)

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
)

data class WatchRecord(
    val bookId: String,
    val bookTitle: String,
    val episode: Int,
    val progressPercent: Int,
)

data class PointAccount(
    val balance: Int,
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
