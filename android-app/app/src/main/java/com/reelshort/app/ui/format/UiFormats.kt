package com.reelshort.app.ui.format

import androidx.media3.common.C
import com.reelshort.app.data.EpisodeSummary

internal fun String?.coverUrlOrNull(): String? = this?.trim()?.takeIf { it.isNotEmpty() }

internal fun String?.playableMediaUrlOrNull(): String? =
    this
        ?.trim()
        ?.takeIf { it.startsWith("https://", ignoreCase = true) || it.startsWith("http://", ignoreCase = true) }

internal fun mediaPositionSeconds(positionMs: Long): Int =
    (maxOf(positionMs, 0L) / 1_000L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()

internal fun mediaDurationSeconds(durationMs: Long, fallbackDurationSeconds: Int): Int =
    if (durationMs == C.TIME_UNSET) {
        maxOf(fallbackDurationSeconds, 0)
    } else {
        (maxOf(durationMs, 0L) / 1_000L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }

internal fun episodeNumberLabel(number: Int): String =
    "第 ${number.coerceAtLeast(0).toString().padStart(2, '0')} 集"

internal fun episodeTitle(episode: EpisodeSummary): String =
    episode.title.trim().takeIf { it.isNotBlank() }
        ?.let { "${episodeNumberLabel(episode.number)} · $it" }
        ?: episodeNumberLabel(episode.number)

internal fun episodeSubtitle(episodeDescription: String, bookDescription: String): String =
    episodeDescription.trim().ifBlank { bookDescription.trim() }

internal fun episodeRowActionLabel(): String = "播放"

internal fun playerSurfaceAspectRatio(): Float = 9f / 16f

internal fun playerStartsAutomatically(): Boolean = true

internal fun playerLoadingLabel(episodeNumber: Int): String =
    "加载${episodeNumberLabel(episodeNumber)}..."

internal fun playerLoadingOverlayVisible(
    playableUrl: String?,
    playbackState: Int,
    hasFirstReady: Boolean,
    hasError: Boolean,
): Boolean {
    if (playableUrl == null || hasError) {
        return true
    }
    return playbackState == androidx.media3.common.Player.STATE_IDLE ||
        playbackState == androidx.media3.common.Player.STATE_BUFFERING ||
        !hasFirstReady
}

internal fun episodeSelectorLabel(totalEpisodes: Int): String =
    "选集 · 已完结 · 全${totalEpisodes.coerceAtLeast(0)}集"

internal fun playerSecondaryActionLabels(): List<String> = listOf("刷新地址")

internal fun guestAccountEntryLabels(): List<String> = listOf("登录", "注册")

internal fun authPromptTitle(hasPendingPlayback: Boolean): String =
    if (hasPendingPlayback) "登录后继续播放" else "登录后查看账户"

internal fun rememberPasswordLabel(): String = "记住密码"

internal fun String.posterInitials(): String =
    trim()
        .split(Regex("\\s+"))
        .filter { it.isNotBlank() }
        .take(2)
        .joinToString("") { it.first().uppercaseChar().toString() }
        .ifBlank { "RS" }
