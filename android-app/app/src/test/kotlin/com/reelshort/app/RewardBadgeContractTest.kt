package com.reelshort.app
import com.reelshort.app.data.AppLanguage
import com.reelshort.app.data.WatchRewardStatus
import com.reelshort.app.ui.format.RewardBadgeVisualState
import com.reelshort.app.ui.format.rewardBadgeState
import com.reelshort.app.ui.format.rewardBadgeContentDescription
import com.reelshort.app.ui.format.rewardBadgeInfoBody
import com.reelshort.app.ui.format.rewardBadgeInfoTitle
import com.reelshort.app.ui.format.rewardBadgeAwardLabel
import com.reelshort.app.ui.format.playerSecondaryActionLabels

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class RewardBadgeContractTest {
    @Test
    fun zeroProgressExplainsCompletionReward() {
        val state = rewardBadgeState(
            progressPercent = 0,
            estimatedRewardPoints = 2,
            isReporting = false,
            hasError = false,
            language = AppLanguage.ENGLISH,
        )

        assertEquals("Finish watching to earn", state.accessibilityText)
        assertEquals("2", state.pointsText)
        assertEquals(12f, state.pointsFontSizeSp)
        assertEquals(0f, state.ringProgress)
        assertEquals(RewardBadgeVisualState.WAITING, state.visualState)
    }

    @Test
    fun inProgressExplainsThatCompletionUnlocksReward() {
        val state = rewardBadgeState(
            progressPercent = 18,
            estimatedRewardPoints = 2,
            isReporting = false,
            hasError = false,
            language = AppLanguage.ENGLISH,
        )

        assertEquals("Finish watching to earn", state.accessibilityText)
        assertEquals(0.18f, state.ringProgress)
        assertEquals(RewardBadgeVisualState.WAITING, state.visualState)
    }

    @Test
    fun quarterProgressDoesNotBecomeARewardStage() {
        val state = rewardBadgeState(
            progressPercent = 25,
            estimatedRewardPoints = 2,
            isReporting = false,
            hasError = false,
            language = AppLanguage.ENGLISH,
        )

        assertEquals("Finish watching to earn", state.accessibilityText)
        assertEquals(0.25f, state.ringProgress)
        assertEquals(RewardBadgeVisualState.WAITING, state.visualState)
    }

    @Test
    fun previousReportedPercentDoesNotCreateAnotherRewardStage() {
        val state = rewardBadgeState(
            progressPercent = 30,
            estimatedRewardPoints = 2,
            isReporting = false,
            hasError = false,
            language = AppLanguage.ENGLISH,
        )

        assertEquals("Finish watching to earn", state.accessibilityText)
        assertEquals(0.3f, state.ringProgress)
        assertEquals(RewardBadgeVisualState.WAITING, state.visualState)
    }

    @Test
    fun reportingStageUsesReportingVisualState() {
        val state = rewardBadgeState(
            progressPercent = 75,
            estimatedRewardPoints = 2,
            isReporting = true,
            hasError = false,
            language = AppLanguage.ENGLISH,
        )

        assertEquals("Syncing", state.accessibilityText)
        assertEquals(0.75f, state.ringProgress)
        assertEquals(RewardBadgeVisualState.REPORTING, state.visualState)
    }

    @Test
    fun completedEpisodeShowsCheckmark() {
        val state = rewardBadgeState(
            progressPercent = 100,
            estimatedRewardPoints = 2,
            isReporting = false,
            hasError = false,
            language = AppLanguage.ENGLISH,
            rewardClaimed = true,
        )

        assertEquals("Points claimed", state.accessibilityText)
        assertEquals(1f, state.ringProgress)
        assertEquals(RewardBadgeVisualState.COMPLETED, state.visualState)
    }

    @Test
    fun failedReportKeepsCurrentStageWithErrorVisualState() {
        val state = rewardBadgeState(
            progressPercent = 25,
            estimatedRewardPoints = 2,
            isReporting = false,
            hasError = true,
            language = AppLanguage.ENGLISH,
        )

        assertEquals("Retry later", state.accessibilityText)
        assertEquals(0.25f, state.ringProgress)
        assertEquals(RewardBadgeVisualState.ERROR, state.visualState)
    }

    @Test
    fun rewardBadgeInfoSheetCopyIsLocalized() {
        assertEquals("Reward progress", rewardBadgeInfoTitle(AppLanguage.ENGLISH))
        assertEquals("Finish the episode to receive points automatically. If syncing fails, keep watching and we will retry.", rewardBadgeInfoBody(AppLanguage.ENGLISH))
        assertEquals("+1 pt", rewardBadgeAwardLabel(1, AppLanguage.ENGLISH))
        assertEquals("+3 pt", rewardBadgeAwardLabel(3, AppLanguage.ENGLISH))
    }

    @Test
    fun dailyLimitStatusHasDedicatedCopy() {
        val state = rewardBadgeState(
            progressPercent = 100,
            estimatedRewardPoints = 2,
            isReporting = false,
            hasError = false,
            language = AppLanguage.ENGLISH,
            rewardClaimed = true,
            rewardStatus = WatchRewardStatus.DAILY_LIMIT_REACHED,
        )

        assertEquals("Daily limit reached", state.accessibilityText)
        assertEquals(RewardBadgeVisualState.DAILY_LIMIT, state.visualState)
        assertEquals(1f, state.ringProgress)
    }

    @Test
    fun rewardIndicatorKeepsNumericContentSeparateFromAccessibleStatus() {
        val state = rewardBadgeState(
            progressPercent = 18,
            estimatedRewardPoints = 2,
            isReporting = false,
            hasError = false,
            language = AppLanguage.ENGLISH,
        )

        assertEquals("2", state.pointsText)
        assertEquals("Reward progress: Finish watching to earn, 2 points", rewardBadgeContentDescription(state, AppLanguage.ENGLISH))
    }

    @Test
    fun rewardIndicatorShrinksLongPointValuesToFitTheRing() {
        assertEquals(12f, rewardBadgeState(0, 99, false, false).pointsFontSizeSp)
        assertEquals(10f, rewardBadgeState(0, 100, false, false).pointsFontSizeSp)
        assertEquals(8f, rewardBadgeState(0, 1000, false, false).pointsFontSizeSp)
    }

    @Test
    fun playerPrimaryActionsDoNotExposeManualReportCopy() {
        val labels = playerSecondaryActionLabels()

        assertEquals(listOf("Refresh stream"), labels)
        assertFalse(labels.any { label ->
            val normalized = label.lowercase()
            normalized.contains("report current progress") ||
                normalized.contains("sync 25%") ||
                normalized.contains("reportable")
        })
    }
}
