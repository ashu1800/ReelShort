package com.reelshort.app.data

data class AuthSession(
    val username: String,
    val token: String,
    val tokenType: String,
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
)

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
            )
    }
}

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
