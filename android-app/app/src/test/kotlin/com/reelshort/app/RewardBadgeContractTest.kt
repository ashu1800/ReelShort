package com.reelshort.app
import com.reelshort.app.ui.format.RewardBadgeVisualState
import com.reelshort.app.ui.format.rewardBadgeState
import com.reelshort.app.ui.format.playerSecondaryActionLabels

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class RewardBadgeContractTest {
    @Test
    fun zeroProgressPointsToFirstRewardStage() {
        val state = rewardBadgeState(
            progressPercent = 0,
            lastReportedProgressPercent = 0,
            isReporting = false,
            hasError = false,
        )

        assertEquals("25", state.displayText)
        assertEquals(0f, state.ringProgress)
        assertEquals(RewardBadgeVisualState.WAITING, state.visualState)
    }

    @Test
    fun reachedStageShowsReadyTarget() {
        val state = rewardBadgeState(
            progressPercent = 25,
            lastReportedProgressPercent = 0,
            isReporting = false,
            hasError = false,
        )

        assertEquals("25", state.displayText)
        assertEquals(1f, state.ringProgress)
        assertEquals(RewardBadgeVisualState.READY, state.visualState)
    }

    @Test
    fun reportedFirstStagePointsToSecondRewardStage() {
        val state = rewardBadgeState(
            progressPercent = 30,
            lastReportedProgressPercent = 25,
            isReporting = false,
            hasError = false,
        )

        assertEquals("50", state.displayText)
        assertEquals(0.6f, state.ringProgress)
        assertEquals(RewardBadgeVisualState.WAITING, state.visualState)
    }

    @Test
    fun reportingStageUsesReportingVisualState() {
        val state = rewardBadgeState(
            progressPercent = 75,
            lastReportedProgressPercent = 50,
            isReporting = true,
            hasError = false,
        )

        assertEquals("75", state.displayText)
        assertEquals(1f, state.ringProgress)
        assertEquals(RewardBadgeVisualState.REPORTING, state.visualState)
    }

    @Test
    fun completedEpisodeShowsCheckmark() {
        val state = rewardBadgeState(
            progressPercent = 100,
            lastReportedProgressPercent = 100,
            isReporting = false,
            hasError = false,
        )

        assertEquals("✓", state.displayText)
        assertEquals(1f, state.ringProgress)
        assertEquals(RewardBadgeVisualState.COMPLETED, state.visualState)
    }

    @Test
    fun failedReportKeepsCurrentStageWithErrorVisualState() {
        val state = rewardBadgeState(
            progressPercent = 25,
            lastReportedProgressPercent = 0,
            isReporting = false,
            hasError = true,
        )

        assertEquals("25", state.displayText)
        assertEquals(1f, state.ringProgress)
        assertEquals(RewardBadgeVisualState.ERROR, state.visualState)
    }

    @Test
    fun playerPrimaryActionsDoNotExposeManualReportCopy() {
        val labels = playerSecondaryActionLabels()

        assertEquals(listOf("刷新地址"), labels)
        assertFalse(labels.any { it.contains("上报当前进度") || it.contains("同步 25%") || it.contains("可上报") })
    }
}
