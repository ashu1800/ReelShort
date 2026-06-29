package com.reelshort.app.ui.format

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
)

internal fun rewardBadgeState(
    progressPercent: Int,
    lastReportedProgressPercent: Int,
    isReporting: Boolean,
    hasError: Boolean,
): RewardBadgeState {
    val progress = progressPercent.coerceIn(0, 100)
    val reported = lastReportedProgressPercent.coerceIn(0, 100)
    val targetStage = RewardBadgeStages.firstOrNull { it > reported }
        ?: return RewardBadgeState(
            displayText = "✓",
            ringProgress = 1f,
            visualState = RewardBadgeVisualState.COMPLETED,
        )
    val visualState = when {
        hasError -> RewardBadgeVisualState.ERROR
        isReporting -> RewardBadgeVisualState.REPORTING
        progress >= targetStage -> RewardBadgeVisualState.READY
        else -> RewardBadgeVisualState.WAITING
    }
    return RewardBadgeState(
        displayText = targetStage.toString(),
        ringProgress = (progress.toFloat() / targetStage.toFloat()).coerceIn(0f, 1f),
        visualState = visualState,
    )
}
