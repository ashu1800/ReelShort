package com.reelshort.app
import com.reelshort.app.data.AppLanguage
import com.reelshort.app.ui.format.RewardBadgeVisualState
import com.reelshort.app.ui.format.rewardBadgeState
import com.reelshort.app.ui.format.rewardBadgeContentDescription
import com.reelshort.app.ui.format.rewardBadgeIncludesProgressRing
import com.reelshort.app.ui.format.rewardBadgeInfoBody
import com.reelshort.app.ui.format.rewardBadgeInfoTitle
import com.reelshort.app.ui.format.rewardBadgeStageAwardLabel
import com.reelshort.app.ui.format.playerSecondaryActionLabels

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RewardBadgeContractTest {
    @Test
    fun zeroProgressPointsToFirstRewardStage() {
        val state = rewardBadgeState(
            progressPercent = 0,
            lastReportedProgressPercent = 0,
            isReporting = false,
            hasError = false,
            language = AppLanguage.ENGLISH,
        )

        assertEquals("Reward 0/25%", state.displayText)
        assertEquals(0f, state.ringProgress)
        assertEquals(RewardBadgeVisualState.WAITING, state.visualState)
    }

    @Test
    fun inProgressShowsCurrentProgressAgainstNextStage() {
        val state = rewardBadgeState(
            progressPercent = 18,
            lastReportedProgressPercent = 0,
            isReporting = false,
            hasError = false,
            language = AppLanguage.ENGLISH,
        )

        assertEquals("Reward 18/25%", state.displayText)
        assertEquals(0.72f, state.ringProgress)
        assertEquals(RewardBadgeVisualState.WAITING, state.visualState)
    }

    @Test
    fun reachedStageShowsReadyProgressLabel() {
        val state = rewardBadgeState(
            progressPercent = 25,
            lastReportedProgressPercent = 0,
            isReporting = false,
            hasError = false,
            language = AppLanguage.ENGLISH,
        )

        assertEquals("Reward 25/25%", state.displayText)
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
            language = AppLanguage.ENGLISH,
        )

        assertEquals("Reward 30/50%", state.displayText)
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
            language = AppLanguage.ENGLISH,
        )

        assertEquals("Syncing", state.displayText)
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
            language = AppLanguage.ENGLISH,
        )

        assertEquals("Reward complete", state.displayText)
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
            language = AppLanguage.ENGLISH,
        )

        assertEquals("Retry later", state.displayText)
        assertEquals(1f, state.ringProgress)
        assertEquals(RewardBadgeVisualState.ERROR, state.visualState)
    }

    @Test
    fun rewardBadgeCopyIsLocalizedForTraditionalChinese() {
        val waiting = rewardBadgeState(
            progressPercent = 18,
            lastReportedProgressPercent = 0,
            isReporting = false,
            hasError = false,
            language = AppLanguage.TRADITIONAL_CHINESE,
        )
        val reporting = rewardBadgeState(
            progressPercent = 50,
            lastReportedProgressPercent = 25,
            isReporting = true,
            hasError = false,
            language = AppLanguage.TRADITIONAL_CHINESE,
        )
        val complete = rewardBadgeState(
            progressPercent = 100,
            lastReportedProgressPercent = 100,
            isReporting = false,
            hasError = false,
            language = AppLanguage.TRADITIONAL_CHINESE,
        )
        val error = rewardBadgeState(
            progressPercent = 25,
            lastReportedProgressPercent = 0,
            isReporting = false,
            hasError = true,
            language = AppLanguage.TRADITIONAL_CHINESE,
        )

        assertEquals("獎勵 18/25%", waiting.displayText)
        assertEquals("同步中", reporting.displayText)
        assertEquals("獎勵完成", complete.displayText)
        assertEquals("稍後補發", error.displayText)
    }

    @Test
    fun rewardBadgeInfoSheetCopyIsLocalized() {
        assertEquals("Reward progress", rewardBadgeInfoTitle(AppLanguage.ENGLISH))
        assertEquals("Watch to 25%, 50%, 75%, and 100% to earn points automatically. If syncing fails, keep watching and we will retry.", rewardBadgeInfoBody(AppLanguage.ENGLISH))
        assertEquals("+1 pt", rewardBadgeStageAwardLabel(AppLanguage.ENGLISH))
        assertEquals("獎勵進度", rewardBadgeInfoTitle(AppLanguage.TRADITIONAL_CHINESE))
        assertEquals("觀看到 25%、50%、75%、100% 會自動獲得積分。若同步失敗，繼續觀看時會自動重試。", rewardBadgeInfoBody(AppLanguage.TRADITIONAL_CHINESE))
        assertEquals("+1 積分", rewardBadgeStageAwardLabel(AppLanguage.TRADITIONAL_CHINESE))
    }

    @Test
    fun rewardBadgeCapsuleContractIncludesIconProgressAndAccessibleLabel() {
        val state = rewardBadgeState(
            progressPercent = 18,
            lastReportedProgressPercent = 0,
            isReporting = false,
            hasError = false,
            language = AppLanguage.ENGLISH,
        )

        assertTrue(state.hasLeadingIcon)
        assertTrue(rewardBadgeIncludesProgressRing(state))
        assertEquals("Reward progress: Reward 18/25%", rewardBadgeContentDescription(state, AppLanguage.ENGLISH))
    }

    @Test
    fun playerPrimaryActionsDoNotExposeManualReportCopy() {
        val labels = playerSecondaryActionLabels()

        assertEquals(listOf("刷新地址"), labels)
        assertFalse(labels.any { it.contains("上报当前进度") || it.contains("同步 25%") || it.contains("可上报") })
    }
}
