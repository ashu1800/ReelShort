package com.reelshort.app

import com.reelshort.app.ui.format.episodeSelectorLabel
import com.reelshort.app.ui.format.playerLoadingLabel
import kotlin.test.Test
import kotlin.test.assertEquals

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
}
