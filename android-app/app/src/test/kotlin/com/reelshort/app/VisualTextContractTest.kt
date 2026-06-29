package com.reelshort.app
import com.reelshort.app.ui.format.primaryTabs
import com.reelshort.app.ui.format.navigationLabel
import com.reelshort.app.ui.format.usesGlobalTopBar
import com.reelshort.app.ui.format.accountEntryLabels
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

import com.reelshort.app.state.AppScreen
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class VisualTextContractTest {

    @Test
    fun bottomNavigationUsesReadableLabels() {
        val labels = primaryTabs.map { it.navigationLabel }

        assertEquals(listOf("首页", "搜索", "账户"), labels)
        assertTrue(labels.all { it.length >= 2 })
    }

    @Test
    fun primaryTabsDoNotUseGlobalTopBar() {
        val topBarScreens = primaryTabs.filter { it.usesGlobalTopBar }

        assertEquals(emptyList(), topBarScreens)
    }

    @Test
    fun accountPageUsesMeStyleEntryLabels() {
        assertEquals(
            listOf("积分余额", "观看记录", "积分流水", "充值订单", "开发诊断", "退出登录"),
            accountEntryLabels(),
        )
    }

    @Test
    fun guestAccountPageShowsAuthEntryLabels() {
        assertEquals(listOf("登录", "注册"), guestAccountEntryLabels())
    }

    @Test
    fun authPromptTitleMatchesTriggerContext() {
        assertEquals("登录后继续播放", authPromptTitle(hasPendingPlayback = true))
        assertEquals("登录后查看账户", authPromptTitle(hasPendingPlayback = false))
    }

    @Test
    fun authFormProvidesRememberPasswordEntry() {
        assertEquals("记住密码", rememberPasswordLabel())
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
}
