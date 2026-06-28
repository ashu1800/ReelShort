package com.reelshort.app

import androidx.media3.common.C
import kotlin.test.Test
import kotlin.test.assertEquals

class MediaProgressContractTest {
    @Test
    fun negativeMediaPositionConvertsToZeroSeconds() {
        assertEquals(0, mediaPositionSeconds(-500))
    }

    @Test
    fun mediaPositionMillisecondsConvertToWholeSeconds() {
        assertEquals(1, mediaPositionSeconds(1_500))
        assertEquals(12, mediaPositionSeconds(12_900))
    }

    @Test
    fun unknownMediaDurationFallsBackToBackendDuration() {
        assertEquals(180, mediaDurationSeconds(C.TIME_UNSET, fallbackDurationSeconds = 180))
        assertEquals(0, mediaDurationSeconds(C.TIME_UNSET, fallbackDurationSeconds = -10))
    }

    @Test
    fun knownMediaDurationMillisecondsConvertToWholeSeconds() {
        assertEquals(181, mediaDurationSeconds(181_900, fallbackDurationSeconds = 120))
        assertEquals(0, mediaDurationSeconds(-1, fallbackDurationSeconds = 120))
    }
}
