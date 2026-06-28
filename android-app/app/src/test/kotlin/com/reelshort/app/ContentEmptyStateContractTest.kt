package com.reelshort.app

import com.reelshort.app.data.BookSummary
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ContentEmptyStateContractTest {
    @Test
    fun homeEmptyStateGuidesUserToSearch() {
        val state = homeEmptyState()

        assertEquals("今日暂无推荐", state.title)
        assertEquals("内容源暂时没有返回推荐短剧，可以先搜索片名或关键词。", state.message)
        assertEquals("去搜索", state.actionLabel)
    }

    @Test
    fun searchEmptyStateBeforeQueryGuidesInput() {
        val state = assertNotNull(searchEmptyState(query = "", resultCount = 0))

        assertEquals("发现短剧", state.title)
        assertEquals("输入剧名、角色或关键词，快速找到想看的短剧。", state.message)
        assertNull(state.actionLabel)
    }

    @Test
    fun searchEmptyStateAfterZeroResultsIncludesKeyword() {
        val state = assertNotNull(searchEmptyState(query = "Alpha Love", resultCount = 0))

        assertEquals("没有找到相关短剧", state.title)
        assertEquals("没有匹配“Alpha Love”的内容，换个关键词再试。", state.message)
        assertEquals("重新搜索", state.actionLabel)
    }

    @Test
    fun searchEmptyStateIsAbsentWhenResultsExist() {
        assertNull(searchEmptyState(query = "Alpha", resultCount = 2))
    }

    @Test
    fun detailEmptyStateBeforeSelectionGuidesSelection() {
        val state = assertNotNull(detailEmptyState(book = null, episodeCount = 0))

        assertEquals("先选择一部短剧", state.title)
        assertEquals("从首页推荐或搜索结果进入详情后，这里会展示分集列表。", state.message)
        assertEquals("返回首页", state.actionLabel)
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

        assertEquals("分集暂不可用", state.title)
        assertEquals("“Alpha Love”暂时没有可播放分集，可以稍后刷新或选择其他短剧。", state.message)
        assertEquals("换一部", state.actionLabel)
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
