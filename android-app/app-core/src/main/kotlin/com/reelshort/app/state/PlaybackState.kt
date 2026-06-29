package com.reelshort.app.state

import com.reelshort.app.data.BookSummary
import com.reelshort.app.data.EpisodeSummary
import com.reelshort.app.data.VideoUrl
import kotlin.math.roundToInt

enum class PlaybackStatus {
    IDLE,
    READY,
}

data class PlaybackState(
    val status: PlaybackStatus = PlaybackStatus.IDLE,
    val book: BookSummary? = null,
    val episode: EpisodeSummary? = null,
    val videoUrl: VideoUrl? = null,
    val positionSeconds: Int = 0,
    val durationSeconds: Int = 0,
    val progressPercent: Int = 0,
    val lastReportedPositionSeconds: Int = 0,
    val lastReportedProgressPercent: Int = 0,
    val isRewardReporting: Boolean = false,
    val rewardReportError: Boolean = false,
) {
    fun withPosition(positionSeconds: Int, durationSeconds: Int = this.durationSeconds): PlaybackState {
        val safeDuration = maxOf(durationSeconds, 0)
        val safePosition = if (safeDuration == 0) {
            maxOf(positionSeconds, 0)
        } else {
            positionSeconds.coerceIn(0, safeDuration)
        }
        val percent = if (safeDuration == 0) {
            0
        } else {
            ((safePosition.toDouble() / safeDuration.toDouble()) * 100).roundToInt().coerceIn(0, 100)
        }
        return copy(
            positionSeconds = safePosition,
            durationSeconds = safeDuration,
            progressPercent = percent,
        )
    }

    fun withVideoUrl(videoUrl: VideoUrl): PlaybackState =
        copy(videoUrl = videoUrl).withPosition(positionSeconds, videoUrl.durationSeconds)

    fun withReportedProgress(positionSeconds: Int, progressPercent: Int): PlaybackState =
        copy(
            lastReportedPositionSeconds = maxOf(positionSeconds, 0),
            lastReportedProgressPercent = progressPercent.coerceIn(0, 100),
            rewardReportError = false,
        )

    fun withRewardReporting(isReporting: Boolean): PlaybackState =
        copy(isRewardReporting = isReporting, rewardReportError = if (isReporting) false else rewardReportError)

    fun withRewardReportError(): PlaybackState =
        copy(isRewardReporting = false, rewardReportError = true)

    companion object {
        fun ready(book: BookSummary, episode: EpisodeSummary, videoUrl: VideoUrl): PlaybackState =
            PlaybackState(
                status = PlaybackStatus.READY,
                book = book,
                episode = episode,
                videoUrl = videoUrl,
                durationSeconds = videoUrl.durationSeconds,
            )
    }
}

val RewardProgressStages = listOf(25, 50, 75, 100)

fun nextUnreportedRewardStage(progressPercent: Int, lastReportedProgressPercent: Int): Int? {
    val progress = progressPercent.coerceIn(0, 100)
    val reported = lastReportedProgressPercent.coerceIn(0, 100)
    return RewardProgressStages.firstOrNull { it > reported && it <= progress }
}
