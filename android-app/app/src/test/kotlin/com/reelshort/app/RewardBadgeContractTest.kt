package com.reelshort.app
import com.reelshort.app.data.AppLanguage
import com.reelshort.app.data.WatchRewardStatus
import com.reelshort.app.ui.format.RewardBadgeVisualState
import com.reelshort.app.ui.format.rewardBadgeState
import com.reelshort.app.ui.format.rewardBadgeContentDescription
import com.reelshort.app.ui.format.rewardBadgeIncludesProgressRing
import com.reelshort.app.ui.format.rewardBadgeInfoBody
import com.reelshort.app.ui.format.rewardBadgeInfoTitle
import com.reelshort.app.ui.format.rewardBadgeAwardLabel
import com.reelshort.app.ui.format.playerSecondaryActionLabels

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RewardBadgeContractTest {
    @Test
    fun zeroProgressExplainsCompletionReward() {
        val state = rewardBadgeState(
            progressPercent = 0,
            isReporting = false,
            hasError = false,
            language = AppLanguage.ENGLISH,
        )

        assertEquals("Finish watching to earn", state.displayText)
        assertEquals(0f, state.ringProgress)
        assertEquals(RewardBadgeVisualState.WAITING, state.visualState)
    }

    @Test
    fun inProgressExplainsThatCompletionUnlocksReward() {
        val state = rewardBadgeState(
            progressPercent = 18,
            isReporting = false,
            hasError = false,
            language = AppLanguage.ENGLISH,
        )

        assertEquals("Finish watching to earn", state.displayText)
        assertEquals(0.18f, state.ringProgress)
        assertEquals(RewardBadgeVisualState.WAITING, state.visualState)
    }

    @Test
    fun quarterProgressDoesNotBecomeARewardStage() {
        val state = rewardBadgeState(
            progressPercent = 25,
            isReporting = false,
            hasError = false,
            language = AppLanguage.ENGLISH,
        )

        assertEquals("Finish watching to earn", state.displayText)
        assertEquals(0.25f, state.ringProgress)
        assertEquals(RewardBadgeVisualState.WAITING, state.visualState)
    }

    @Test
    fun previousReportedPercentDoesNotCreateAnotherRewardStage() {
        val state = rewardBadgeState(
            progressPercent = 30,
            isReporting = false,
            hasError = false,
            language = AppLanguage.ENGLISH,
        )

        assertEquals("Finish watching to earn", state.displayText)
        assertEquals(0.3f, state.ringProgress)
        assertEquals(RewardBadgeVisualState.WAITING, state.visualState)
    }

    @Test
    fun reportingStageUsesReportingVisualState() {
        val state = rewardBadgeState(
            progressPercent = 75,
            isReporting = true,
            hasError = false,
            language = AppLanguage.ENGLISH,
        )

        assertEquals("Syncing", state.displayText)
        assertEquals(0.75f, state.ringProgress)
        assertEquals(RewardBadgeVisualState.REPORTING, state.visualState)
    }

    @Test
    fun completedEpisodeShowsCheckmark() {
        val state = rewardBadgeState(
            progressPercent = 100,
            isReporting = false,
            hasError = false,
            language = AppLanguage.ENGLISH,
            rewardClaimed = true,
        )

        assertEquals("Points claimed", state.displayText)
        assertEquals(1f, state.ringProgress)
        assertEquals(RewardBadgeVisualState.COMPLETED, state.visualState)
    }

    @Test
    fun failedReportKeepsCurrentStageWithErrorVisualState() {
        val state = rewardBadgeState(
            progressPercent = 25,
            isReporting = false,
            hasError = true,
            language = AppLanguage.ENGLISH,
        )

        assertEquals("Retry later", state.displayText)
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
            isReporting = false,
            hasError = false,
            language = AppLanguage.ENGLISH,
            rewardClaimed = true,
            rewardStatus = WatchRewardStatus.DAILY_LIMIT_REACHED,
        )

        assertEquals("Daily limit reached", state.displayText)
        assertEquals(RewardBadgeVisualState.DAILY_LIMIT, state.visualState)
        assertFalse(rewardBadgeIncludesProgressRing(state))
    }

    @Test
    fun rewardBadgeCapsuleContractIncludesIconProgressAndAccessibleLabel() {
        val state = rewardBadgeState(
            progressPercent = 18,
            isReporting = false,
            hasError = false,
            language = AppLanguage.ENGLISH,
        )

        assertTrue(state.hasLeadingIcon)
        assertTrue(rewardBadgeIncludesProgressRing(state))
        assertEquals("Reward progress: Finish watching to earn", rewardBadgeContentDescription(state, AppLanguage.ENGLISH))
    }

    @Test
    fun playerPrimaryActionsDoNotExposeManualReportCopy() {
        val labels = playerSecondaryActionLabels()

        assertEquals(listOf("刷新地址"), labels)
        assertFalse(labels.any { it.contains("上报当前进度") || it.contains("同步 25%") || it.contains("可上报") })
    }
}
