package com.reelshort.app.ui.format

import com.reelshort.app.data.AppLanguage
import com.reelshort.app.data.WatchRewardStatus

internal enum class RewardBadgeVisualState {
    WAITING,
    READY,
    REPORTING,
    COMPLETED,
    DAILY_LIMIT,
    UNAVAILABLE,
    ERROR,
}

internal data class RewardBadgeState(
    val displayText: String,
    val ringProgress: Float,
    val visualState: RewardBadgeVisualState,
    val hasLeadingIcon: Boolean = true,
)

internal fun rewardBadgeState(
    progressPercent: Int,
    isReporting: Boolean,
    hasError: Boolean,
    language: AppLanguage = AppLanguage.ENGLISH,
    rewardClaimed: Boolean = false,
    rewardStatus: WatchRewardStatus = WatchRewardStatus.NOT_COMPLETE,
): RewardBadgeState {
    val progress = progressPercent.coerceIn(0, 100)
    val visualState = when {
        hasError -> RewardBadgeVisualState.ERROR
        isReporting -> RewardBadgeVisualState.REPORTING
        rewardStatus == WatchRewardStatus.DAILY_LIMIT_REACHED -> RewardBadgeVisualState.DAILY_LIMIT
        rewardStatus == WatchRewardStatus.DURATION_UNAVAILABLE -> RewardBadgeVisualState.UNAVAILABLE
        rewardClaimed || rewardStatus == WatchRewardStatus.AWARDED ||
            rewardStatus == WatchRewardStatus.AWARDED_PARTIAL ||
            rewardStatus == WatchRewardStatus.ALREADY_CLAIMED -> RewardBadgeVisualState.COMPLETED
        progress >= 100 -> RewardBadgeVisualState.READY
        else -> RewardBadgeVisualState.WAITING
    }
    val displayText = when (visualState) {
        RewardBadgeVisualState.REPORTING -> rewardBadgeSyncingLabel(language)
        RewardBadgeVisualState.ERROR -> rewardBadgeRetryLabel(language)
        RewardBadgeVisualState.READY -> rewardBadgeReadyLabel(language)
        RewardBadgeVisualState.COMPLETED -> rewardBadgeCompleteLabel(language)
        RewardBadgeVisualState.DAILY_LIMIT -> rewardBadgeDailyLimitLabel(language)
        RewardBadgeVisualState.UNAVAILABLE -> rewardBadgeUnavailableLabel(language)
        RewardBadgeVisualState.WAITING -> rewardBadgeWaitingLabel(language)
    }
    return RewardBadgeState(
        displayText = displayText,
        ringProgress = (progress.toFloat() / 100f).coerceIn(0f, 1f),
        visualState = visualState,
    )
}

internal fun rewardBadgeIncludesProgressRing(state: RewardBadgeState): Boolean =
    state.visualState != RewardBadgeVisualState.COMPLETED &&
        state.visualState != RewardBadgeVisualState.DAILY_LIMIT

internal fun rewardBadgeContentDescription(state: RewardBadgeState, language: AppLanguage): String =
    "${rewardBadgeInfoTitle(language)}: ${state.displayText}"

internal fun rewardBadgeInfoTitle(language: AppLanguage): String =
    when (language) {
        AppLanguage.ENGLISH -> "Reward progress"
    }

internal fun rewardBadgeInfoBody(language: AppLanguage): String =
    when (language) {
        AppLanguage.ENGLISH -> "Finish the episode to receive points automatically. If syncing fails, keep watching and we will retry."
    }

internal fun rewardBadgeAwardLabel(points: Int, language: AppLanguage): String =
    when (language) {
        AppLanguage.ENGLISH -> "+${points.coerceAtLeast(0)} pt"
    }

private fun rewardBadgeWaitingLabel(language: AppLanguage): String =
    when (language) {
        AppLanguage.ENGLISH -> "Finish watching to earn"
    }

private fun rewardBadgeReadyLabel(language: AppLanguage): String =
    when (language) {
        AppLanguage.ENGLISH -> "Episode complete"
    }

private fun rewardBadgeSyncingLabel(language: AppLanguage): String =
    when (language) {
        AppLanguage.ENGLISH -> "Syncing"
    }

private fun rewardBadgeCompleteLabel(language: AppLanguage): String =
    when (language) {
        AppLanguage.ENGLISH -> "Points claimed"
    }

private fun rewardBadgeDailyLimitLabel(language: AppLanguage): String =
    when (language) {
        AppLanguage.ENGLISH -> "Daily limit reached"
    }

private fun rewardBadgeUnavailableLabel(language: AppLanguage): String =
    when (language) {
        AppLanguage.ENGLISH -> "Reward unavailable"
    }

private fun rewardBadgeRetryLabel(language: AppLanguage): String =
    when (language) {
        AppLanguage.ENGLISH -> "Retry later"
    }
