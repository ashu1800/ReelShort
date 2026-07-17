package com.reelshort.app.ui.format

import androidx.media3.common.C
import com.reelshort.app.data.AppLanguage
import com.reelshort.app.data.EpisodeSummary
import com.reelshort.app.data.WatchRecord
import com.reelshort.app.data.WithdrawalSummary
import com.reelshort.app.state.AuthMode
import com.reelshort.app.state.UiMessageType
import java.math.BigDecimal
import java.math.RoundingMode

internal fun appBrandName(): String = "ShortLink"

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
    language: AppLanguage = AppLanguage.ENGLISH,
): String {
    val copy = strings(language)
    return "${copy.playerEpisodePrefix}${number.coerceAtLeast(0).toString().padStart(2, '0')}${copy.playerEpisodeUnit}"
}

internal fun episodeTitle(
    episode: EpisodeSummary,
    language: AppLanguage = AppLanguage.ENGLISH,
): String =
    episode.title.trim().takeIf { it.isNotBlank() }
        ?.let { "${episodeNumberLabel(episode.number, language)} · $it" }
        ?: episodeNumberLabel(episode.number, language)

internal fun episodeSubtitle(episodeDescription: String, bookDescription: String): String =
    episodeDescription.trim().ifBlank { bookDescription.trim() }

internal fun episodeRowActionLabel(language: AppLanguage = AppLanguage.ENGLISH): String =
    strings(language).playerPlayAction

internal fun playerSurfaceAspectRatio(): Float = 9f / 16f

internal fun playerStartsAutomatically(): Boolean = true

internal fun playerLoadingLabel(
    episodeNumber: Int,
    language: AppLanguage = AppLanguage.ENGLISH,
): String {
    val copy = strings(language)
    return "${copy.playerLoadingPrefix}${episodeNumberLabel(episodeNumber, language)}${copy.playerLoadingSuffix}"
}

internal fun playerLoadingOverlayVisible(
    playableUrl: String?,
    playbackState: Int,
    hasFirstReady: Boolean,
    hasError: Boolean,
): Boolean =
    playerOverlayMode(playableUrl, playbackState, hasFirstReady, hasError) != PlayerOverlayMode.NONE

internal enum class PlayerOverlayMode {
    NONE,
    COVER_LOADING,
    BUFFERING,
    ERROR,
}

internal fun playerOverlayMode(
    playableUrl: String?,
    playbackState: Int,
    hasFirstReady: Boolean,
    hasError: Boolean,
): PlayerOverlayMode {
    if (hasError) {
        return PlayerOverlayMode.ERROR
    }
    if (playableUrl == null || playbackState == androidx.media3.common.Player.STATE_IDLE || !hasFirstReady) {
        return PlayerOverlayMode.COVER_LOADING
    }
    if (playbackState == androidx.media3.common.Player.STATE_BUFFERING) {
        return PlayerOverlayMode.BUFFERING
    }
    return PlayerOverlayMode.NONE
}

internal fun playerErrorNextEpisode(
    currentEpisode: EpisodeSummary?,
    episodes: List<EpisodeSummary>,
): EpisodeSummary? {
    if (currentEpisode == null) {
        return null
    }
    val currentIndex = episodes.indexOfFirst {
        it.number == currentEpisode.number && it.chapterId == currentEpisode.chapterId
    }
    if (currentIndex < 0 || currentIndex >= episodes.lastIndex) {
        return null
    }
    return episodes[currentIndex + 1]
}

internal fun episodeSelectorLabel(
    totalEpisodes: Int,
    language: AppLanguage = AppLanguage.ENGLISH,
): String {
    val copy = strings(language)
    return "${copy.playerEpisodeSelectorPrefix} · ${copy.playerEpisodeSelectorCompleted} · ${copy.playerEpisodeSelectorTotalPrefix}${totalEpisodes.coerceAtLeast(0)}${copy.playerEpisodeSelectorTotalSuffix}"
}

internal enum class EpisodeWatchStatusType {
    NONE,
    CURRENT,
    IN_PROGRESS,
    WATCHED,
}

internal data class EpisodeWatchStatus(
    val type: EpisodeWatchStatusType,
    val progressPercent: Int = 0,
)

internal fun episodeWatchStatus(
    bookId: String?,
    episode: EpisodeSummary,
    selectedEpisode: EpisodeSummary?,
    watchHistory: List<WatchRecord>,
): EpisodeWatchStatus {
    val recordProgress = watchHistory
        .firstOrNull { it.bookId == bookId && it.episode == episode.number }
        ?.progressPercent
        ?.coerceIn(0, 100)
        ?: 0
    val isSelected = selectedEpisode != null &&
        selectedEpisode.number == episode.number &&
        selectedEpisode.chapterId == episode.chapterId
    if (isSelected) {
        return EpisodeWatchStatus(EpisodeWatchStatusType.CURRENT, recordProgress)
    }
    return when {
        recordProgress >= 100 -> EpisodeWatchStatus(EpisodeWatchStatusType.WATCHED, 100)
        recordProgress > 0 -> EpisodeWatchStatus(EpisodeWatchStatusType.IN_PROGRESS, recordProgress)
        else -> EpisodeWatchStatus(EpisodeWatchStatusType.NONE)
    }
}

internal fun episodeWatchStatusLabel(
    status: EpisodeWatchStatus,
    language: AppLanguage = AppLanguage.ENGLISH,
): String =
    when (status.type) {
        EpisodeWatchStatusType.CURRENT -> strings(language).playerEpisodeStatusCurrent
        EpisodeWatchStatusType.WATCHED -> strings(language).playerEpisodeStatusWatched
        EpisodeWatchStatusType.IN_PROGRESS -> "${status.progressPercent.coerceIn(0, 100)}%"
        EpisodeWatchStatusType.NONE -> ""
    }

internal fun playerSecondaryActionLabels(language: AppLanguage = AppLanguage.ENGLISH): List<String> =
    listOf(strings(language).playerRefreshAction)

internal fun guestAccountEntryLabels(language: AppLanguage = AppLanguage.ENGLISH): List<String> {
    val copy = strings(language)
    return listOf(copy.accountGuestSignIn, copy.accountGuestRegister)
}

internal fun guestAccountEntryAuthModes(): List<AuthMode> =
    listOf(AuthMode.LOGIN, AuthMode.REGISTER)

internal fun authPromptTitle(
    hasPendingPlayback: Boolean,
    language: AppLanguage = AppLanguage.ENGLISH,
): String =
    if (hasPendingPlayback) strings(language).authPromptPlayback else strings(language).authPromptAccount

internal fun rememberPasswordLabel(language: AppLanguage = AppLanguage.ENGLISH): String =
    strings(language).rememberPassword

internal fun authCaptchaAnswerLabel(language: AppLanguage = AppLanguage.ENGLISH): String =
    strings(language).authCaptchaAnswerLabel

internal fun authSinglePrimaryAction(): Boolean = true

internal fun authBottomSheetAvoidsNavigationBar(): Boolean = true

internal data class AuthSheetCopy(
    val title: String,
    val subtitle: String,
    val primaryAction: String,
    val secondaryAction: String,
)

internal fun authSheetCopy(
    mode: AuthMode,
    hasPendingPlayback: Boolean,
    language: AppLanguage = AppLanguage.ENGLISH,
): AuthSheetCopy {
    val copy = strings(language)
    return when (mode) {
        AuthMode.LOGIN -> AuthSheetCopy(
            title = authPromptTitle(hasPendingPlayback, language),
            subtitle = if (hasPendingPlayback) copy.authBottomSheetPlaybackSubtitle else copy.authBottomSheetAccountSubtitle,
            primaryAction = copy.authLoginAction,
            secondaryAction = copy.authRegisterSecondary,
        )
        AuthMode.REGISTER -> AuthSheetCopy(
            title = copy.authRegisterTitle,
            subtitle = copy.authRegisterSubtitle,
            primaryAction = copy.authRegisterAction,
            secondaryAction = copy.authLoginSecondary,
        )
    }
}

internal fun authRegisterEnabled(
    isLoading: Boolean,
    username: String,
    password: String,
    confirmPassword: String,
    captchaAnswer: String,
    captchaLoaded: Boolean,
): Boolean =
    !isLoading &&
        username.isNotBlank() &&
        password.length >= 6 &&
        password == confirmPassword &&
        captchaAnswer.isNotBlank() &&
        captchaLoaded

internal fun commercialSheetAutoDismissesAfterSubmit(): Boolean = false

internal fun withdrawalConversionLines(
    summary: WithdrawalSummary?,
    pointAmount: Int,
    language: AppLanguage,
): List<String> {
    val copy = strings(language)
    if (summary == null) {
        return listOf(
            "${copy.accountWithdrawAvailableLabel} 0 ${copy.listPointsLabel} · " +
                "${copy.accountWithdrawMinimumLabel} 0 · ${copy.accountWithdrawRateLabel} = - USDT",
        )
    }
    val cnyPerPoint = summary.cnyPerPoint.toPositiveDecimalOrNull()
    val cnyPerUsd = summary.cnyPerUsd.toPositiveDecimalOrNull()
    val minimumUsd = summary.minimumUsd.toPositiveDecimalOrNull()
    if (cnyPerPoint == null || cnyPerUsd == null || minimumUsd == null) {
        return listOf(
            "${copy.accountWithdrawAvailableLabel} ${summary.availablePoints} ${copy.listPointsLabel} · " +
                "${copy.accountWithdrawMinimumLabel} ${summary.minimumPoints} · " +
                "${copy.accountWithdrawRateLabel} = ${summary.usdtPerPoint} USDT",
        )
    }

    val lines = mutableListOf(
        "${copy.accountWithdrawAvailableLabel} ${summary.availablePoints} ${copy.listPointsLabel} · " +
            "${copy.accountWithdrawMinimumLabel} ${summary.minimumPoints} ${copy.listPointsLabel} / " +
            "${minimumUsd.toUiDecimal()} USD",
        "${copy.accountWithdrawRateLabel} = ${cnyPerPoint.toUiDecimal()} CNY · " +
            "1 USD = ${cnyPerUsd.toUiDecimal()} CNY",
    )
    if (pointAmount > 0) {
        // M6: 用 Long 防溢出（pointAmount * feePercent 在大额时可能超过 Int.MAX_VALUE）
        val fee = if (summary.feePercent > 0) ((pointAmount.toLong() * summary.feePercent + 99) / 100).toInt() else 0
        val withdrawable = pointAmount - fee
        val points = withdrawable.toBigDecimal()
        val cnyAmount = points.multiply(cnyPerPoint)
        val usdtAmount = points.multiply(cnyPerPoint).divide(cnyPerUsd, 6, RoundingMode.HALF_UP)
        val estimateLabel = "Estimated"
        lines += "$estimateLabel ${cnyAmount.toUiDecimal()} CNY ≈ ${usdtAmount.toUiDecimal()} USDT"
        if (fee > 0) {
            lines += "Fee: ${summary.feePercent}% ($fee pts) · You receive: $withdrawable pts · Total deduct: $pointAmount pts"
        }
    }
    return lines
}

internal fun withdrawalStatusLabel(status: String, language: AppLanguage): String =
    when (status.uppercase()) {
        "PENDING" -> "Pending"
        "APPROVED" -> "Completed"
        "REJECTED" -> "Rejected"
        else -> status
    }

internal fun withdrawalRecordDetail(
    status: String,
    adminNote: String?,
    txHash: String?,
    language: AppLanguage,
): String? = when {
    status.equals("REJECTED", ignoreCase = true) && !adminNote.isNullOrBlank() ->
        "Reason: " + adminNote
    status.equals("APPROVED", ignoreCase = true) && !txHash.isNullOrBlank() -> "TX: $txHash"
    else -> null
}

private fun String?.toPositiveDecimalOrNull(): BigDecimal? =
    this?.toBigDecimalOrNull()?.takeIf { it > BigDecimal.ZERO }

private fun BigDecimal.toUiDecimal(): String =
    setScale(6, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()

internal enum class AccountAction {
    SEND_VERIFICATION,
    WALLET_BIND_OR_REPLACE,
    WALLET_UNBIND,
    POINT_TRANSFER,
    WITHDRAWAL,
}

internal fun accountActionRequiresConfirmation(action: AccountAction): Boolean =
    action == AccountAction.WALLET_UNBIND ||
        action == AccountAction.POINT_TRANSFER ||
        action == AccountAction.WITHDRAWAL

internal fun accountSheetStartsFullyExpanded(): Boolean = true

internal fun accountSheetUsesScrollableContent(): Boolean = true

internal fun accountCardsUseContentDrivenHeight(): Boolean = true

internal fun posterOverlayTitleMaxLines(fontScale: Float): Int = if (fontScale >= 1.3f) 1 else 2

internal fun responsivePosterMinimumWidthDp(): Int = 136

internal fun posterCardContentDescription(title: String, episodeCount: Int, language: AppLanguage): String =
    "$title, $episodeCount${strings(language).posterEpisodeUnit}"

internal fun posterImageIsDecorativeInsideCard(): Boolean = true

internal enum class MessageVisualTone {
    SUCCESS,
    ERROR,
    INFO,
}

internal fun messageVisualTone(type: UiMessageType): MessageVisualTone =
    when (type) {
        UiMessageType.SUCCESS -> MessageVisualTone.SUCCESS
        UiMessageType.ERROR -> MessageVisualTone.ERROR
        UiMessageType.INFO -> MessageVisualTone.INFO
    }

internal fun topMessageUsesLiveRegion(): Boolean = true

internal fun bankCardEntryCollectsSensitiveData(): Boolean = false

internal fun accountConfirmationTitle(language: AppLanguage): String = "Confirm action"

internal fun accountConfirmationBody(summary: String, language: AppLanguage): String =
    "Please confirm: $summary. This action may not be reversible."

internal fun accountConfirmationConfirm(language: AppLanguage): String = "Confirm"

internal fun accountConfirmationCancel(language: AppLanguage): String = "Cancel"

internal fun walletSheetShouldDismiss(
    visible: Boolean,
    lastHandledVersion: Long,
    currentVersion: Long,
): Boolean = visible && currentVersion > lastHandledVersion

internal fun String.posterInitials(): String =
    trim()
        .split(Regex("\\s+"))
        .filter { it.isNotBlank() }
        .take(2)
        .joinToString("") { it.first().uppercaseChar().toString() }
        .ifBlank { "RS" }
