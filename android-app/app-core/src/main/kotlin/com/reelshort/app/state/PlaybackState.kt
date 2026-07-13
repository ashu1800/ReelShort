package com.reelshort.app.state

import com.reelshort.app.data.BookSummary
import com.reelshort.app.data.EpisodeSummary
import com.reelshort.app.data.VideoUrl
import com.reelshort.app.data.WatchEpisodeSnapshot
import com.reelshort.app.data.WatchProgressReport
import com.reelshort.app.data.WatchRewardStatus
import kotlin.math.abs
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
    val lastPersistedProgressPercent: Int = 0,
    val isRewardReporting: Boolean = false,
    val rewardReportError: Boolean = false,
    val rewardClaimed: Boolean = false,
    val rewardStatus: WatchRewardStatus = WatchRewardStatus.NOT_COMPLETE,
    val awardedPoints: Int = 0,
    val rewardAwardVersion: Long = 0,
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

    fun withReportedProgress(
        positionSeconds: Int,
        progressPercent: Int,
        rewardClaimed: Boolean = this.rewardClaimed,
        rewardStatus: WatchRewardStatus = this.rewardStatus,
        awardedPoints: Int = this.awardedPoints,
    ): PlaybackState =
        copy(
            lastReportedPositionSeconds = maxOf(positionSeconds, 0),
            lastPersistedProgressPercent = progressPercent.coerceIn(0, 100),
            rewardReportError = false,
            rewardClaimed = rewardClaimed || rewardStatus.isClaimed(),
            rewardStatus = rewardStatus,
            awardedPoints = awardedPoints.coerceAtLeast(0),
        )

    fun withSnapshot(snapshot: WatchEpisodeSnapshot): PlaybackState =
        withPosition(snapshot.positionSeconds, snapshot.durationSeconds.takeIf { it > 0 } ?: durationSeconds).withReportedProgress(
            positionSeconds = snapshot.positionSeconds,
            progressPercent = snapshot.progressPercent,
            rewardClaimed = snapshot.rewardClaimed,
            rewardStatus = snapshot.rewardStatus,
            awardedPoints = snapshot.awardedPoints,
        )

    fun withProgressReport(report: WatchProgressReport): PlaybackState {
        val awarded = report.awardedPoints.coerceAtLeast(0)
        return withPosition(report.positionSeconds, report.durationSeconds.takeIf { it > 0 } ?: durationSeconds)
            .withReportedProgress(
                positionSeconds = report.positionSeconds,
                progressPercent = report.progressPercent,
                rewardClaimed = report.rewardClaimed,
                rewardStatus = report.rewardStatus,
                awardedPoints = awarded,
            )
            .copy(rewardAwardVersion = if (awarded > 0) rewardAwardVersion + 1 else rewardAwardVersion)
    }

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

const val PlaybackProgressSaveIntervalSeconds = 15

fun shouldPersistPlaybackProgress(
    positionSeconds: Int,
    durationSeconds: Int,
    lastReportedPositionSeconds: Int,
    rewardClaimed: Boolean,
): Boolean {
    if (durationSeconds <= 0 || positionSeconds < 0) {
        return false
    }
    val safePosition = positionSeconds.coerceIn(0, durationSeconds)
    val completed = safePosition >= durationSeconds
    if (completed) {
        return !rewardClaimed || safePosition != lastReportedPositionSeconds
    }
    return abs(safePosition - lastReportedPositionSeconds) >= PlaybackProgressSaveIntervalSeconds
}
