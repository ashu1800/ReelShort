package com.reelshort.app.ui.format

import androidx.media3.common.C
import com.reelshort.app.data.AppLanguage
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

internal fun episodeNumberLabel(
    number: Int,
    language: AppLanguage = AppLanguage.TRADITIONAL_CHINESE,
): String {
    val copy = strings(language)
    return "${copy.playerEpisodePrefix}${number.coerceAtLeast(0).toString().padStart(2, '0')}${copy.playerEpisodeUnit}"
}

internal fun episodeTitle(
    episode: EpisodeSummary,
    language: AppLanguage = AppLanguage.TRADITIONAL_CHINESE,
): String =
    episode.title.trim().takeIf { it.isNotBlank() }
        ?.let { "${episodeNumberLabel(episode.number, language)} · $it" }
        ?: episodeNumberLabel(episode.number, language)

internal fun episodeSubtitle(episodeDescription: String, bookDescription: String): String =
    episodeDescription.trim().ifBlank { bookDescription.trim() }

internal fun episodeRowActionLabel(language: AppLanguage = AppLanguage.TRADITIONAL_CHINESE): String =
    strings(language).playerPlayAction

internal fun playerSurfaceAspectRatio(): Float = 9f / 16f

internal fun playerStartsAutomatically(): Boolean = true

internal fun playerLoadingLabel(
    episodeNumber: Int,
    language: AppLanguage = AppLanguage.TRADITIONAL_CHINESE,
): String {
    val copy = strings(language)
    return "${copy.playerLoadingPrefix}${episodeNumberLabel(episodeNumber, language)}${copy.playerLoadingSuffix}"
}

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

internal fun episodeSelectorLabel(
    totalEpisodes: Int,
    language: AppLanguage = AppLanguage.TRADITIONAL_CHINESE,
): String {
    val copy = strings(language)
    return "${copy.playerEpisodeSelectorPrefix} · ${copy.playerEpisodeSelectorCompleted} · ${copy.playerEpisodeSelectorTotalPrefix}${totalEpisodes.coerceAtLeast(0)}${copy.playerEpisodeSelectorTotalSuffix}"
}

internal fun playerSecondaryActionLabels(language: AppLanguage = AppLanguage.TRADITIONAL_CHINESE): List<String> =
    listOf(strings(language).playerRefreshAction)

internal fun guestAccountEntryLabels(language: AppLanguage = AppLanguage.TRADITIONAL_CHINESE): List<String> {
    val copy = strings(language)
    return listOf(copy.accountGuestSignIn, copy.accountGuestRegister)
}

internal fun authPromptTitle(
    hasPendingPlayback: Boolean,
    language: AppLanguage = AppLanguage.TRADITIONAL_CHINESE,
): String =
    if (hasPendingPlayback) strings(language).authPromptPlayback else strings(language).authPromptAccount

internal fun rememberPasswordLabel(language: AppLanguage = AppLanguage.TRADITIONAL_CHINESE): String =
    strings(language).rememberPassword

internal fun String.posterInitials(): String =
    trim()
        .split(Regex("\\s+"))
        .filter { it.isNotBlank() }
        .take(2)
        .joinToString("") { it.first().uppercaseChar().toString() }
        .ifBlank { "RS" }
