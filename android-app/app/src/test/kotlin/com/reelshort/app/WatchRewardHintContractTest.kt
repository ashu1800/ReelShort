package com.reelshort.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WatchRewardHintContractTest {
    @Test
    fun zeroProgressPointsToFirstRewardStage() {
        val hint = watchRewardHint(progressPercent = 0, lastReportedProgressPercent = 0)

        assertEquals("下一奖励：25%", hint.title)
        assertEquals("继续观看，距离 25% 阶段还差 25%。", hint.message)
        assertFalse(hint.actionReady)
    }

    @Test
    fun progressBeforeStageShowsRemainingPercent() {
        val hint = watchRewardHint(progressPercent = 20, lastReportedProgressPercent = 0)

        assertEquals("下一奖励：25%", hint.title)
        assertEquals("继续观看，距离 25% 阶段还差 5%。", hint.message)
        assertFalse(hint.actionReady)
    }

    @Test
    fun reachedFirstStageButNotReportedIsReady() {
        val hint = watchRewardHint(progressPercent = 25, lastReportedProgressPercent = 0)

        assertEquals("可上报领取 25% 奖励", hint.title)
        assertEquals("本次上报可结算 25% 观看阶段，后端会自动跳过已领取阶段。", hint.message)
        assertTrue(hint.actionReady)
    }

    @Test
    fun reachedNextStageAfterPreviousReportIsReady() {
        val hint = watchRewardHint(progressPercent = 50, lastReportedProgressPercent = 25)

        assertEquals("可上报领取 50% 奖励", hint.title)
        assertEquals("本次上报可结算 50% 观看阶段，后端会自动跳过已领取阶段。", hint.message)
        assertTrue(hint.actionReady)
    }

    @Test
    fun progressPastMultipleUnreportedStagesShowsAllReadyStages() {
        val hint = watchRewardHint(progressPercent = 80, lastReportedProgressPercent = 25)

        assertEquals("可上报领取 50%、75% 奖励", hint.title)
        assertEquals("本次上报可结算 50%、75% 观看阶段，后端会自动跳过已领取阶段。", hint.message)
        assertTrue(hint.actionReady)
    }

    @Test
    fun fullyReportedEpisodeShowsCompleted() {
        val hint = watchRewardHint(progressPercent = 100, lastReportedProgressPercent = 100)

        assertEquals("本集奖励已完成", hint.title)
        assertEquals("25%、50%、75%、100% 阶段都已上报，继续观看不会重复发放。", hint.message)
        assertFalse(hint.actionReady)
    }
}
