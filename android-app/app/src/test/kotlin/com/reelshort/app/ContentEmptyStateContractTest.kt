package com.reelshort.app
import com.reelshort.app.ui.format.ContentEmptyState
import com.reelshort.app.ui.format.homeEmptyState
import com.reelshort.app.ui.format.searchEmptyState
import com.reelshort.app.ui.format.detailEmptyState

import com.reelshort.app.data.BookSummary
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ContentEmptyStateContractTest {
    @Test
    fun homeEmptyStateGuidesUserToSearch() {
        val state = homeEmptyState()

        assertEquals("今日暫無推薦", state.title)
        assertEquals("內容源暫時沒有返回推薦短劇，可以先搜尋片名或關鍵詞。", state.message)
        assertEquals("去搜尋", state.actionLabel)
    }

    @Test
    fun searchEmptyStateBeforeQueryGuidesInput() {
        val state = assertNotNull(searchEmptyState(query = "", resultCount = 0))

        assertEquals("發現短劇", state.title)
        assertEquals("輸入劇名、角色或關鍵詞，快速找到想看的短劇。", state.message)
        assertNull(state.actionLabel)
    }

    @Test
    fun searchEmptyStateAfterZeroResultsIncludesKeyword() {
        val state = assertNotNull(searchEmptyState(query = "Alpha Love", resultCount = 0))

        assertEquals("沒有找到相關短劇", state.title)
        assertEquals("沒有匹配“Alpha Love”的內容，換個關鍵詞再試。", state.message)
        assertEquals("重新搜尋", state.actionLabel)
    }

    @Test
    fun searchEmptyStateIsAbsentWhenResultsExist() {
        assertNull(searchEmptyState(query = "Alpha", resultCount = 2))
    }

    @Test
    fun detailEmptyStateBeforeSelectionGuidesSelection() {
        val state = assertNotNull(detailEmptyState(book = null, episodeCount = 0))

        assertEquals("先選擇一部短劇", state.title)
        assertEquals("從首頁推薦或搜尋結果進入詳情後，這裡會展示分集列表。", state.message)
        assertEquals("返回首頁", state.actionLabel)
    }

    @Test
    fun detailEmptyStateForSelectedBookWithoutEpisodesMentionsTitle() {
        val book = BookSummary(
            id = "book-1",
            title = "Alpha Love",
            filteredTitle = "alpha-love",
            coverUrl = null,
            description = "",
            chapterCount = 0,
        )

        val state = assertNotNull(detailEmptyState(book = book, episodeCount = 0))

        assertEquals("分集暫不可用", state.title)
        assertEquals("“Alpha Love”暫時沒有可播放分集，可以稍後刷新或選擇其他短劇。", state.message)
        assertEquals("換一部", state.actionLabel)
    }

    @Test
    fun detailEmptyStateIsAbsentWhenEpisodesExist() {
        val book = BookSummary(
            id = "book-1",
            title = "Alpha Love",
            filteredTitle = "alpha-love",
            coverUrl = null,
            description = "",
            chapterCount = 1,
        )

        assertNull(detailEmptyState(book = book, episodeCount = 1))
    }
}
