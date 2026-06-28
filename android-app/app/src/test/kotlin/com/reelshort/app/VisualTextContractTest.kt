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
}
