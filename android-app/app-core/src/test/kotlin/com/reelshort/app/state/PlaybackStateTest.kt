package com.reelshort.app.state

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlaybackStateTest {
    @Test
    fun progressSaveUsesFifteenSecondPositionWindow() {
        assertFalse(shouldPersistPlaybackProgress(14, 200, 0, rewardClaimed = false))
        assertTrue(shouldPersistPlaybackProgress(15, 200, 0, rewardClaimed = false))
        assertFalse(shouldPersistPlaybackProgress(29, 200, 15, rewardClaimed = false))
        assertTrue(shouldPersistPlaybackProgress(30, 200, 15, rewardClaimed = false))
    }

    @Test
    fun completionIsSavedImmediatelyButAlreadyClaimedCompletionIsNotRepeated() {
        assertTrue(shouldPersistPlaybackProgress(200, 200, 15, rewardClaimed = false))
        assertTrue(shouldPersistPlaybackProgress(200, 200, 15, rewardClaimed = true))
        assertFalse(shouldPersistPlaybackProgress(200, 200, 200, rewardClaimed = true))
    }

    @Test
    fun invalidDurationCannotTriggerProgressSave() {
        assertFalse(shouldPersistPlaybackProgress(15, 0, 0, rewardClaimed = false))
        assertFalse(shouldPersistPlaybackProgress(-1, 200, 0, rewardClaimed = false))
    }
}
