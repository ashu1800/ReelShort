package com.reelshort.app

import com.reelshort.app.ui.format.episodeSelectorLabel
import com.reelshort.app.ui.format.episodeWatchStatus
import com.reelshort.app.ui.format.episodeWatchStatusLabel
import com.reelshort.app.ui.format.playerErrorNextEpisode
import com.reelshort.app.ui.format.playerLoadingLabel
import com.reelshort.app.ui.format.playerLoadingOverlayVisible
import com.reelshort.app.ui.format.playerOverlayMode
import com.reelshort.app.ui.format.PlayerOverlayMode
import com.reelshort.app.ui.format.EpisodeWatchStatusType
import androidx.media3.common.Player
import com.reelshort.app.data.AppLanguage
import com.reelshort.app.data.EpisodeSummary
import com.reelshort.app.data.WatchRecord
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EpisodeSelectorTextContractTest {
    @Test
    fun episodeSelectorLabelShowsCompletionAndTotalEpisodeCount() {
        assertEquals("選集 · 已完結 · 全113集", episodeSelectorLabel(113))
    }

    @Test
    fun episodeSelectorLabelNeverShowsNegativeEpisodeCount() {
        assertEquals("選集 · 已完結 · 全0集", episodeSelectorLabel(-1))
    }

    @Test
    fun playerLoadingLabelShowsEpisodeNumber() {
        assertEquals("載入第 02 集...", playerLoadingLabel(2))
    }

    @Test
    fun playerLoadingLabelClampsInvalidEpisodeNumber() {
        assertEquals("載入第 00 集...", playerLoadingLabel(-5))
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

    @Test
    fun playerOverlayModeUsesCoverBeforeFirstReadyAndNoneWhenReady() {
        assertEquals(
            PlayerOverlayMode.COVER_LOADING,
            playerOverlayMode(
                playableUrl = "https://media.local/1.m3u8",
                playbackState = Player.STATE_IDLE,
                hasFirstReady = false,
                hasError = false,
            ),
        )
        assertEquals(
            PlayerOverlayMode.NONE,
            playerOverlayMode(
                playableUrl = "https://media.local/1.m3u8",
                playbackState = Player.STATE_READY,
                hasFirstReady = true,
                hasError = false,
            ),
        )
    }

    @Test
    fun playerOverlayModeSeparatesBufferingAndError() {
        assertEquals(
            PlayerOverlayMode.BUFFERING,
            playerOverlayMode(
                playableUrl = "https://media.local/1.m3u8",
                playbackState = Player.STATE_BUFFERING,
                hasFirstReady = true,
                hasError = false,
            ),
        )
        assertEquals(
            PlayerOverlayMode.ERROR,
            playerOverlayMode(
                playableUrl = "https://media.local/1.m3u8",
                playbackState = Player.STATE_READY,
                hasFirstReady = true,
                hasError = true,
            ),
        )
    }

    @Test
    fun playerErrorNextEpisodeReturnsNextAvailableEpisodeOnly() {
        val episodes = listOf(
            EpisodeSummary(number = 1, chapterId = "chapter-1", title = "", description = ""),
            EpisodeSummary(number = 2, chapterId = "chapter-2", title = "", description = ""),
        )

        assertEquals(episodes[1], playerErrorNextEpisode(episodes[0], episodes))
        assertNull(playerErrorNextEpisode(episodes[1], episodes))
        assertNull(playerErrorNextEpisode(null, episodes))
    }

    @Test
    fun episodeWatchStatusPrioritizesCurrentEpisodeOverHistory() {
        val status = episodeWatchStatus(
            bookId = "book-1",
            episode = EpisodeSummary(number = 2, chapterId = "chapter-2"),
            selectedEpisode = EpisodeSummary(number = 2, chapterId = "chapter-2"),
            watchHistory = listOf(WatchRecord(bookId = "book-1", bookTitle = "Alpha", episode = 2, progressPercent = 100)),
        )

        assertEquals(EpisodeWatchStatusType.CURRENT, status.type)
        assertEquals(100, status.progressPercent)
        assertEquals("Current", episodeWatchStatusLabel(status, AppLanguage.ENGLISH))
    }

    @Test
    fun episodeWatchStatusShowsWatchedAndInProgressLabels() {
        val watched = episodeWatchStatus(
            bookId = "book-1",
            episode = EpisodeSummary(number = 1, chapterId = "chapter-1"),
            selectedEpisode = EpisodeSummary(number = 3, chapterId = "chapter-3"),
            watchHistory = listOf(WatchRecord(bookId = "book-1", bookTitle = "Alpha", episode = 1, progressPercent = 100)),
        )
        val inProgress = episodeWatchStatus(
            bookId = "book-1",
            episode = EpisodeSummary(number = 2, chapterId = "chapter-2"),
            selectedEpisode = EpisodeSummary(number = 3, chapterId = "chapter-3"),
            watchHistory = listOf(WatchRecord(bookId = "book-1", bookTitle = "Alpha", episode = 2, progressPercent = 58)),
        )

        assertEquals(EpisodeWatchStatusType.WATCHED, watched.type)
        assertEquals("Watched", episodeWatchStatusLabel(watched, AppLanguage.ENGLISH))
        assertEquals(EpisodeWatchStatusType.IN_PROGRESS, inProgress.type)
        assertEquals("58%", episodeWatchStatusLabel(inProgress, AppLanguage.ENGLISH))
    }

    @Test
    fun episodeWatchStatusIgnoresOtherBooksAndMissingRecords() {
        val status = episodeWatchStatus(
            bookId = "book-1",
            episode = EpisodeSummary(number = 4, chapterId = "chapter-4"),
            selectedEpisode = EpisodeSummary(number = 3, chapterId = "chapter-3"),
            watchHistory = listOf(WatchRecord(bookId = "book-2", bookTitle = "Beta", episode = 4, progressPercent = 90)),
        )

        assertEquals(EpisodeWatchStatusType.NONE, status.type)
        assertEquals("", episodeWatchStatusLabel(status, AppLanguage.ENGLISH))
    }
}
