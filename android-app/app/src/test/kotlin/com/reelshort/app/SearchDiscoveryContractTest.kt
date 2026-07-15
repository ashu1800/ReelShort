package com.reelshort.app

import com.reelshort.app.data.AppLanguage
import com.reelshort.app.ui.format.searchShowsResultsFirst
import com.reelshort.app.ui.format.searchDiscoveryGroups
import com.reelshort.app.ui.format.searchDiscoveryTags
import com.reelshort.app.ui.format.strings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SearchDiscoveryContractTest {
    @Test
    fun englishSearchDiscoveryUsesRoleAndGenreTags() {
        val tags = searchDiscoveryTags(AppLanguage.ENGLISH)
        val groups = searchDiscoveryGroups(AppLanguage.ENGLISH)

        assertEquals("Discover", strings(AppLanguage.ENGLISH).searchTitle)
        assertEquals(listOf("Story moods", "Power roles", "Relationships", "Fantasy heat"), groups.map { it.title })
        assertEquals(listOf("Love", "Revenge", "Secret", "Family"), tags.take(4))
        assertTrue(tags.contains("Billionaire"))
        assertTrue(tags.contains("Werewolf"))
        assertTrue(tags.contains("Queen"))
    }

    @Test
    fun searchCopyIncludesCommercialDiscoveryFeedback() {
        val english = strings(AppLanguage.ENGLISH)

        assertEquals("Curated discovery", english.searchHeroEyebrow)
        assertEquals("Results", english.searchResultTitle)
        assertEquals("dramas", english.searchResultCountSuffix)
        assertEquals("Find your next drama", english.searchEmptyTitle)
    }

    @Test
    fun searchResultsMoveAheadOfDiscoveryGroupsOnceUserStartsSearching() {
        assertEquals(false, searchShowsResultsFirst(query = "", resultCount = 0))
        assertEquals(true, searchShowsResultsFirst(query = "Love", resultCount = 0))
        assertEquals(true, searchShowsResultsFirst(query = "", resultCount = 3))
    }
}
