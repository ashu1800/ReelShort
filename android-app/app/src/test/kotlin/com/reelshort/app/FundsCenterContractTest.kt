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

        assertTrue(funds.contains("todayCreditedPoints"))
        assertTrue(funds.contains("fundsWithdrawalAction"))
        assertTrue(funds.contains("fundsVipRechargeAction"))
        assertTrue(funds.contains("fillMaxWidth()"))
        assertTrue(withdrawal.contains("WithdrawalScreen"))
        assertTrue(vip.contains("VipRechargeScreen"))
    }

    @Test
    fun accountPageHasOneFundsEntryAndNoStandaloneWithdrawalOrVipAction() {
        val account = sourceRoot.resolve("ui/screens/account/AccountScreen.kt").readText()

        assertTrue(account.contains("onOpenFunds"))
        assertFalse(account.contains("onOpenWithdrawalForm"))
        assertFalse(account.contains("onShowVipPaywall"))
    }
}
