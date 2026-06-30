package com.reelshort.app

import com.reelshort.app.ui.format.episodeSelectorLabel
import com.reelshort.app.ui.format.playerLoadingLabel
import com.reelshort.app.ui.format.playerLoadingOverlayVisible
import androidx.media3.common.Player
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EpisodeSelectorTextContractTest {
    @Test
    fun episodeSelectorLabelShowsCompletionAndTotalEpisodeCount() {
        assertEquals("选集 · 已完结 · 全113集", episodeSelectorLabel(113))
    }

    @Test
    fun episodeSelectorLabelNeverShowsNegativeEpisodeCount() {
        assertEquals("选集 · 已完结 · 全0集", episodeSelectorLabel(-1))
    }

    @Test
    fun playerLoadingLabelShowsEpisodeNumber() {
        assertEquals("加载第 02 集...", playerLoadingLabel(2))
    }

    @Test
    fun playerLoadingLabelClampsInvalidEpisodeNumber() {
        assertEquals("加载第 00 集...", playerLoadingLabel(-5))
    }

    @Test
    fun playerLoadingOverlayDoesNotShowForReadyPlayerEvenWhenMediaIsLoading() {
        assertFalse(
            playerLoadingOverlayVisible(
                playableUrl = "https://media.local/1.m3u8",
                playbackState = Player.STATE_READY,
                hasFirstReady = true,
                hasError = false,
            ),
        )
    }

    @Test
    fun playerLoadingOverlayShowsBeforeFirstReadyAndDuringBuffering() {
        assertTrue(
            playerLoadingOverlayVisible(
                playableUrl = "https://media.local/1.m3u8",
                playbackState = Player.STATE_IDLE,
                hasFirstReady = false,
                hasError = false,
            ),
        )
        assertTrue(
            playerLoadingOverlayVisible(
                playableUrl = "https://media.local/1.m3u8",
                playbackState = Player.STATE_BUFFERING,
                hasFirstReady = true,
                hasError = false,
            ),
        )
    }

    @Test
    fun playerLoadingOverlayShowsForMissingUrlOrError() {
        assertTrue(
            playerLoadingOverlayVisible(
                playableUrl = null,
                playbackState = Player.STATE_IDLE,
                hasFirstReady = false,
                hasError = false,
            ),
        )
        assertTrue(
            playerLoadingOverlayVisible(
                playableUrl = "https://media.local/1.m3u8",
                playbackState = Player.STATE_READY,
                hasFirstReady = true,
                hasError = true,
            ),
        )
    }
}
