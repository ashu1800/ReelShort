package com.reelshort.app
import com.reelshort.app.ui.format.primaryTabs
import com.reelshort.app.ui.format.navigationLabel
import com.reelshort.app.ui.format.usesGlobalTopBar
import com.reelshort.app.ui.format.AccountDashboardSection
import com.reelshort.app.ui.format.AccountDetailSheet
import com.reelshort.app.ui.format.accountEntryLabels
import com.reelshort.app.ui.format.accountDashboardSections
import com.reelshort.app.ui.format.accountPrimaryActionLabels
import com.reelshort.app.ui.format.accountContinueWatchingLimit
import com.reelshort.app.ui.format.accountContinueWatchingRowsAreClickable
import com.reelshort.app.ui.format.accountContinueWatchingUsesPosterCards
import com.reelshort.app.ui.format.accountDetailSheetTitle
import com.reelshort.app.ui.format.accountPrimaryActionSheet
import com.reelshort.app.ui.format.guestAccountEntryLabels
import com.reelshort.app.ui.format.authPromptTitle
import com.reelshort.app.ui.format.rememberPasswordLabel
import com.reelshort.app.ui.format.LoadingFeedbackMode
import com.reelshort.app.ui.format.loadingFeedbackMode
import com.reelshort.app.ui.format.TabRefreshMode
import com.reelshort.app.ui.format.primaryTabRefreshModes
import com.reelshort.app.ui.format.episodeNumberLabel
import com.reelshort.app.ui.format.episodeRowActionLabel
import com.reelshort.app.ui.format.episodeSubtitle
import com.reelshort.app.ui.format.playerSurfaceAspectRatio
import com.reelshort.app.ui.format.playerStartsAutomatically
import com.reelshort.app.ui.format.playerSecondaryActionLabels
import com.reelshort.app.ui.format.strings
import com.reelshort.app.data.AppLanguage

import com.reelshort.app.state.AppScreen
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class VisualTextContractTest {

    @Test
    fun bottomNavigationUsesReadableLabels() {
        val labels = primaryTabs.map { it.navigationLabel }

        assertEquals(listOf("首頁", "探索", "我的"), labels)
        assertTrue(labels.all { it.length >= 2 })
    }

    @Test
    fun bottomNavigationUsesDefaultEnglishLabelsWhenLanguageIsEnglish() {
        val labels = primaryTabs.map { it.navigationLabel(AppLanguage.ENGLISH) }

        assertEquals(listOf("Home", "Discover", "Me"), labels)
    }

    @Test
    fun allPrimaryScreenLabelsAreLocalizedForEnglish() {
        assertEquals("Sign in", AppScreen.LOGIN.navigationLabel(AppLanguage.ENGLISH))
        assertEquals("Details", AppScreen.DETAIL.navigationLabel(AppLanguage.ENGLISH))
        assertEquals("Player", AppScreen.PLAYER.navigationLabel(AppLanguage.ENGLISH))
        assertEquals("Favorites", AppScreen.FAVORITES.navigationLabel(AppLanguage.ENGLISH))
    }

    @Test
    fun primaryTabsDoNotUseGlobalTopBar() {
        val topBarScreens = primaryTabs.filter { it.usesGlobalTopBar }

        assertEquals(emptyList(), topBarScreens)
    }

    @Test
    fun accountPageUsesMeStyleEntryLabels() {
        assertEquals(
            listOf("我的收藏", "積分餘額", "觀看記錄", "積分流水", "充值訂單", "開發診斷", "退出登入"),
            accountEntryLabels(),
        )
    }

    @Test
    fun accountPageUsesLocalizedEntryLabelsInEnglish() {
        assertEquals(
            listOf("Favorites", "Points", "Watch history", "Point records", "Orders", "Diagnostics", "Sign out"),
            accountEntryLabels(AppLanguage.ENGLISH),
        )
    }

    @Test
    fun accountDashboardUsesCommercialMePageHierarchy() {
        assertEquals(
            listOf(
                AccountDashboardSection.IDENTITY,
                AccountDashboardSection.PRIMARY_ACTIONS,
                AccountDashboardSection.CONTINUE_WATCHING,
                AccountDashboardSection.SECONDARY_SETTINGS,
            ),
            accountDashboardSections(),
        )
    }

    @Test
    fun accountDashboardPromotesFourPrimaryActions() {
        assertEquals(
            listOf("Favorites", "Points", "Watch history", "Orders"),
            accountPrimaryActionLabels(AppLanguage.ENGLISH),
        )
        assertEquals(
            listOf("我的收藏", "積分餘額", "觀看記錄", "充值訂單"),
            accountPrimaryActionLabels(),
        )
    }

    @Test
    fun accountDashboardUsesCompactContinueWatchingPreview() {
        assertEquals(2, accountContinueWatchingLimit())
    }

    @Test
    fun accountDashboardContinueWatchingRowsAreClickable() {
        assertEquals(true, accountContinueWatchingRowsAreClickable())
    }

    @Test
    fun accountDashboardContinueWatchingUsesPosterCards() {
        assertEquals(true, accountContinueWatchingUsesPosterCards())
    }

    @Test
    fun accountDashboardPrimaryActionsExposeDetailSheets() {
        assertEquals(null, accountPrimaryActionSheet("Favorites"))
        assertEquals(AccountDetailSheet.POINT_RECORDS, accountPrimaryActionSheet("Points"))
        assertEquals(AccountDetailSheet.WATCH_HISTORY, accountPrimaryActionSheet("Watch history"))
        assertEquals(AccountDetailSheet.ORDERS, accountPrimaryActionSheet("Orders"))
    }

    @Test
    fun accountDetailSheetsHaveLocalizedTitles() {
        assertEquals("Point records", accountDetailSheetTitle(AccountDetailSheet.POINT_RECORDS, AppLanguage.ENGLISH))
        assertEquals("Watch history", accountDetailSheetTitle(AccountDetailSheet.WATCH_HISTORY, AppLanguage.ENGLISH))
        assertEquals("Orders", accountDetailSheetTitle(AccountDetailSheet.ORDERS, AppLanguage.ENGLISH))
        assertEquals("積分流水", accountDetailSheetTitle(AccountDetailSheet.POINT_RECORDS))
        assertEquals("觀看記錄", accountDetailSheetTitle(AccountDetailSheet.WATCH_HISTORY))
        assertEquals("充值訂單", accountDetailSheetTitle(AccountDetailSheet.ORDERS))
    }

    @Test
    fun guestAccountPageShowsAuthEntryLabels() {
        assertEquals(listOf("登入", "註冊"), guestAccountEntryLabels())
    }

    @Test
    fun guestAccountPageShowsLocalizedAuthEntryLabelsInEnglish() {
        assertEquals(listOf("Sign in", "Create account"), guestAccountEntryLabels(AppLanguage.ENGLISH))
    }

    @Test
    fun authPromptTitleMatchesTriggerContext() {
        assertEquals("登入後繼續播放", authPromptTitle(hasPendingPlayback = true))
        assertEquals("登入後查看帳戶", authPromptTitle(hasPendingPlayback = false))
    }

    @Test
    fun authPromptTitleIsLocalizedForEnglish() {
        assertEquals("Sign in to keep watching", authPromptTitle(hasPendingPlayback = true, language = AppLanguage.ENGLISH))
        assertEquals("Sign in to view your account", authPromptTitle(hasPendingPlayback = false, language = AppLanguage.ENGLISH))
    }

    @Test
    fun authFormProvidesRememberPasswordEntry() {
        assertEquals("記住密碼", rememberPasswordLabel())
    }

    @Test
    fun authFormProvidesLocalizedRememberPasswordEntryInEnglish() {
        assertEquals("Remember password", rememberPasswordLabel(AppLanguage.ENGLISH))
    }

    @Test
    fun loadingFeedbackUsesCenteredDialog() {
        assertEquals(LoadingFeedbackMode.CENTER_DIALOG, loadingFeedbackMode())
    }

    @Test
    fun bottomNavigationUsesNonBlockingRefreshForCachedTabs() {
        assertEquals(
            mapOf(
                AppScreen.HOME to TabRefreshMode.CACHE_FIRST_BACKGROUND_REFRESH,
                AppScreen.SEARCH to TabRefreshMode.LOCAL_SWITCH,
                AppScreen.ACCOUNT to TabRefreshMode.CACHE_FIRST_BACKGROUND_REFRESH,
            ),
            primaryTabRefreshModes(),
        )
    }

    @Test
    fun episodeRowsUseNumberOnlyDisplayWithoutDuration() {
        assertEquals("第 01 集", episodeNumberLabel(1))
        assertEquals("第 12 集", episodeNumberLabel(12))
        assertEquals("第 99 集", episodeNumberLabel(99))
        assertEquals("播放", episodeRowActionLabel())
    }

    @Test
    fun episodeRowsUseEpisodeDescriptionWithBookFallback() {
        assertEquals(
            "A deal goes wrong.",
            episodeSubtitle(episodeDescription = "A deal goes wrong.", bookDescription = "Book intro."),
        )
        assertEquals(
            "Book intro.",
            episodeSubtitle(episodeDescription = "", bookDescription = "Book intro."),
        )
        assertEquals("", episodeSubtitle(episodeDescription = " ", bookDescription = " "))
    }

    @Test
    fun playerUsesPortraitFirstAutoplayContract() {
        assertEquals(9f / 16f, playerSurfaceAspectRatio())
        assertEquals(true, playerStartsAutomatically())
    }

    @Test
    fun playerSecondaryActionsAndSharedLoadingCopyAreLocalizedForEnglish() {
        assertEquals(listOf("Refresh stream"), playerSecondaryActionLabels(AppLanguage.ENGLISH))
        assertEquals("Loading", strings(AppLanguage.ENGLISH).loadingDialogTitle)
    }
}
