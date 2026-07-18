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

        assertEquals("No picks right now", state.title)
        assertEquals("The provider did not return recommendations yet. Try search instead.", state.message)
        assertEquals("Go to search", state.actionLabel)
    }

    @Test
    fun searchEmptyStateBeforeQueryGuidesInput() {
        val state = assertNotNull(searchEmptyState(query = "", resultCount = 0))

        assertEquals("Discover dramas", state.title)
        assertEquals("Search by title, role, or keyword to find your next drama.", state.message)
        assertNull(state.actionLabel)
    }

    @Test
    fun searchEmptyStateAfterZeroResultsIncludesKeyword() {
        val state = assertNotNull(searchEmptyState(query = "Alpha Love", resultCount = 0))

        assertEquals("No matching dramas", state.title)
        assertEquals("Nothing matched “Alpha Love”. Try another keyword.", state.message)
        assertEquals("Search again", state.actionLabel)
    }

    @Test
    fun searchEmptyStateIsAbsentWhenResultsExist() {
        assertNull(searchEmptyState(query = "Alpha", resultCount = 2))
    }

    @Test
    fun detailEmptyStateBeforeSelectionGuidesSelection() {
        val state = assertNotNull(detailEmptyState(book = null, episodeCount = 0))

        assertEquals("Pick a drama first", state.title)
        assertEquals("Open a title from Home or Discover to see its episodes here.", state.message)
        assertEquals("Back to home", state.actionLabel)
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

        assertEquals("Episodes unavailable", state.title)
        assertEquals("“Alpha Love” has no playable episodes right now. Try again later or choose another title.", state.message)
        assertEquals("Choose another drama", state.actionLabel)
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
