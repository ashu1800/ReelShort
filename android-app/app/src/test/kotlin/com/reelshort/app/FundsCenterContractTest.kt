package com.reelshort.app

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FundsCenterContractTest {
    private val sourceRoot = File("src/main/java/com/reelshort/app")

    @Test
    fun fundsAreaUsesDedicatedScreensAndFullWidthActions() {
        val funds = sourceRoot.resolve("ui/screens/account/FundsScreen.kt").readText()
        val withdrawal = sourceRoot.resolve("ui/screens/account/WithdrawalScreen.kt").readText()
        val vip = sourceRoot.resolve("ui/screens/account/VipRechargeScreen.kt").readText()
        val navigation = sourceRoot.resolve("ui/format/NavigationContract.kt").readText()

        assertTrue(funds.contains("todayCreditedPoints"))
        assertTrue(funds.contains("fundsWithdrawalAction"))
        assertTrue(funds.contains("fundsVipRechargeAction"))
        assertTrue(funds.contains("vipOrders"))
        assertTrue(funds.contains("vipRecordsTitle"))
        assertTrue(funds.contains("fillMaxWidth()"))
        assertTrue(navigation.contains("listOf(AppScreen.HOME, AppScreen.SEARCH, AppScreen.FUNDS, AppScreen.ACCOUNT)"))
        assertTrue(withdrawal.contains("WithdrawalScreen"))
        assertTrue(vip.contains("VipRechargeScreen"))
        assertFalse(vip.contains("state.vipOrders"))
    }

    @Test
    fun accountPageHasOneFundsEntryAndNoStandaloneWithdrawalOrVipAction() {
        val account = sourceRoot.resolve("ui/screens/account/AccountScreen.kt").readText()

        assertFalse(account.contains("onOpenFunds"))
        assertFalse(account.contains("fundsEntryTitle"))
        assertFalse(account.contains("onOpenWithdrawalForm"))
        assertFalse(account.contains("onShowVipPaywall"))
    }
}
