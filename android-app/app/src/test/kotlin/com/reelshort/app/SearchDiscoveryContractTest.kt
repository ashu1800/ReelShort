package com.reelshort.app

import com.reelshort.app.data.AppLanguage
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
    fun traditionalChineseSearchDiscoveryUsesLocalizedTags() {
        val tags = searchDiscoveryTags(AppLanguage.TRADITIONAL_CHINESE)
        val groups = searchDiscoveryGroups(AppLanguage.TRADITIONAL_CHINESE)

        assertEquals("探索內容", strings(AppLanguage.TRADITIONAL_CHINESE).searchTitle)
        assertEquals(listOf("故事情緒", "強勢角色", "關係張力", "幻想熱點"), groups.map { it.title })
        assertEquals(listOf("愛情", "復仇", "秘密", "家庭"), tags.take(4))
        assertTrue(tags.contains("霸總"))
        assertTrue(tags.contains("狼人"))
        assertTrue(tags.contains("女王"))
    }

    @Test
    fun searchCopyIncludesCommercialDiscoveryFeedback() {
        val english = strings(AppLanguage.ENGLISH)
        val traditionalChinese = strings(AppLanguage.TRADITIONAL_CHINESE)

        assertEquals("Curated discovery", english.searchHeroEyebrow)
        assertEquals("Results", english.searchResultTitle)
        assertEquals("dramas", english.searchResultCountSuffix)
        assertEquals("Find your next drama", english.searchEmptyTitle)
        assertEquals("精選探索", traditionalChinese.searchHeroEyebrow)
        assertEquals("搜尋結果", traditionalChinese.searchResultTitle)
        assertEquals("部短劇", traditionalChinese.searchResultCountSuffix)
        assertEquals("發現短劇", traditionalChinese.searchEmptyTitle)
    }
}
