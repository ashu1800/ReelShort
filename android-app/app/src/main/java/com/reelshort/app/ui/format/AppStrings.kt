package com.reelshort.app.ui.format

import com.reelshort.app.data.AppLanguage

internal data class AppStrings(
    val homeTab: String,
    val searchTab: String,
    val accountTab: String,
    val searchTitle: String,
    val searchSubtitle: String,
    val searchHint: String,
    val searchAction: String,
    val searchTagsTitle: String,
    val searchHeroEyebrow: String,
    val searchResultTitle: String,
    val searchResultCountSuffix: String,
    val searchEmptyTitle: String,
    val searchInitialHint: String,
    val searchEmpty: String,
    val languageTitle: String,
    val languageSubtitle: String,
)

internal fun strings(language: AppLanguage): AppStrings =
    when (language) {
        AppLanguage.ENGLISH -> AppStrings(
            homeTab = "Home",
            searchTab = "Discover",
            accountTab = "Me",
            searchTitle = "Discover",
            searchSubtitle = "Start with a trope, role, or keyword.",
            searchHint = "Search romance, CEO, revenge...",
            searchAction = "Search",
            searchTagsTitle = "Popular lanes",
            searchHeroEyebrow = "Curated discovery",
            searchResultTitle = "Results",
            searchResultCountSuffix = "dramas",
            searchEmptyTitle = "Find your next drama",
            searchInitialHint = "Choose a preset tag or search by title.",
            searchEmpty = "No dramas matched this search.",
            languageTitle = "Language",
            languageSubtitle = "English / 繁體中文",
        )
        AppLanguage.TRADITIONAL_CHINESE -> AppStrings(
            homeTab = "首頁",
            searchTab = "探索",
            accountTab = "我的",
            searchTitle = "探索內容",
            searchSubtitle = "從題材、角色或關鍵詞開始。",
            searchHint = "搜尋愛情、霸總、復仇...",
            searchAction = "搜尋",
            searchTagsTitle = "熱門方向",
            searchHeroEyebrow = "精選探索",
            searchResultTitle = "搜尋結果",
            searchResultCountSuffix = "部短劇",
            searchEmptyTitle = "發現短劇",
            searchInitialHint = "選擇預設標籤，或輸入短劇名稱。",
            searchEmpty = "沒有找到符合條件的短劇。",
            languageTitle = "語言",
            languageSubtitle = "English / 繁體中文",
        )
    }

internal fun searchDiscoveryTags(language: AppLanguage): List<String> =
    searchDiscoveryGroups(language).flatMap { it.tags }

internal data class SearchDiscoveryGroup(
    val title: String,
    val tags: List<String>,
)

internal fun searchDiscoveryGroups(language: AppLanguage): List<SearchDiscoveryGroup> =
    when (language) {
        AppLanguage.ENGLISH -> listOf(
            SearchDiscoveryGroup("Story moods", listOf("Love", "Revenge", "Secret", "Family")),
            SearchDiscoveryGroup("Power roles", listOf("Billionaire", "CEO", "Boss", "Queen", "Doctor")),
            SearchDiscoveryGroup("Relationships", listOf("Marriage", "Contract", "Pregnant")),
            SearchDiscoveryGroup("Fantasy heat", listOf("Mafia", "Werewolf", "Alpha", "Luna")),
        )
        AppLanguage.TRADITIONAL_CHINESE -> listOf(
            SearchDiscoveryGroup("故事情緒", listOf("愛情", "復仇", "秘密", "家庭")),
            SearchDiscoveryGroup("強勢角色", listOf("億萬富翁", "霸總", "老闆", "女王", "醫生")),
            SearchDiscoveryGroup("關係張力", listOf("婚姻", "契約", "懷孕")),
            SearchDiscoveryGroup("幻想熱點", listOf("黑幫", "狼人", "Alpha", "Luna")),
        )
    }
