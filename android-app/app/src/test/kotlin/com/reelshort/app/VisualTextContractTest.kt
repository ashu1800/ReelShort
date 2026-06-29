package com.reelshort.app

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
}
