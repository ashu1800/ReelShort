package com.reelshort.app.ui.format

import com.reelshort.app.data.AppLanguage

private val RewardBadgeStages = listOf(25, 50, 75, 100)

internal enum class RewardBadgeVisualState {
    WAITING,
    READY,
    REPORTING,
    COMPLETED,
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
    lastReportedProgressPercent: Int,
    isReporting: Boolean,
    hasError: Boolean,
    language: AppLanguage = AppLanguage.TRADITIONAL_CHINESE,
): RewardBadgeState {
    val progress = progressPercent.coerceIn(0, 100)
    val reported = lastReportedProgressPercent.coerceIn(0, 100)
    val targetStage = RewardBadgeStages.firstOrNull { it > reported }
        ?: return RewardBadgeState(
            displayText = rewardBadgeCompleteLabel(language),
            ringProgress = 1f,
            visualState = RewardBadgeVisualState.COMPLETED,
        )
    val visualState = when {
        hasError -> RewardBadgeVisualState.ERROR
        isReporting -> RewardBadgeVisualState.REPORTING
        progress >= targetStage -> RewardBadgeVisualState.READY
        else -> RewardBadgeVisualState.WAITING
    }
    val displayText = when (visualState) {
        RewardBadgeVisualState.REPORTING -> rewardBadgeSyncingLabel(language)
        RewardBadgeVisualState.ERROR -> rewardBadgeRetryLabel(language)
        else -> rewardBadgeProgressLabel(progress, targetStage, language)
    }
    return RewardBadgeState(
        displayText = displayText,
        ringProgress = (progress.toFloat() / targetStage.toFloat()).coerceIn(0f, 1f),
        visualState = visualState,
    )
}

internal fun rewardBadgeIncludesProgressRing(state: RewardBadgeState): Boolean =
    state.visualState != RewardBadgeVisualState.COMPLETED

internal fun rewardBadgeContentDescription(state: RewardBadgeState, language: AppLanguage): String =
    "${rewardBadgeInfoTitle(language)}: ${state.displayText}"

internal fun rewardBadgeInfoTitle(language: AppLanguage): String =
    when (language) {
        AppLanguage.ENGLISH -> "Reward progress"
        AppLanguage.TRADITIONAL_CHINESE -> "獎勵進度"
    }

internal fun rewardBadgeInfoBody(language: AppLanguage): String =
    when (language) {
        AppLanguage.ENGLISH -> "Watch to 25%, 50%, 75%, and 100% to earn points automatically. If syncing fails, keep watching and we will retry."
        AppLanguage.TRADITIONAL_CHINESE -> "觀看到 25%、50%、75%、100% 會自動獲得積分。若同步失敗，繼續觀看時會自動重試。"
    }

internal fun rewardBadgeStageAwardLabel(language: AppLanguage): String =
    when (language) {
        AppLanguage.ENGLISH -> "+1 pt"
        AppLanguage.TRADITIONAL_CHINESE -> "+1 積分"
    }

private fun rewardBadgeProgressLabel(progress: Int, targetStage: Int, language: AppLanguage): String =
    when (language) {
        AppLanguage.ENGLISH -> "Reward $progress/$targetStage%"
        AppLanguage.TRADITIONAL_CHINESE -> "獎勵 $progress/$targetStage%"
    }

private fun rewardBadgeSyncingLabel(language: AppLanguage): String =
    when (language) {
        AppLanguage.ENGLISH -> "Syncing"
        AppLanguage.TRADITIONAL_CHINESE -> "同步中"
    }

private fun rewardBadgeCompleteLabel(language: AppLanguage): String =
    when (language) {
        AppLanguage.ENGLISH -> "Reward complete"
        AppLanguage.TRADITIONAL_CHINESE -> "獎勵完成"
    }

private fun rewardBadgeRetryLabel(language: AppLanguage): String =
    when (language) {
        AppLanguage.ENGLISH -> "Retry later"
        AppLanguage.TRADITIONAL_CHINESE -> "稍後補發"
    }
