package com.reelshort.app

import com.reelshort.app.ui.format.AccountAction
import com.reelshort.app.ui.format.accountActionRequiresConfirmation
import com.reelshort.app.ui.format.withdrawalConversionLines
import com.reelshort.app.data.AppLanguage
import com.reelshort.app.data.WithdrawalSummary
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

class AccountActionContractTest {
    @Test
    fun irreversibleAccountActionsRequireConfirmation() {
        assertTrue(accountActionRequiresConfirmation(AccountAction.WALLET_UNBIND))
        assertTrue(accountActionRequiresConfirmation(AccountAction.POINT_TRANSFER))
        assertTrue(accountActionRequiresConfirmation(AccountAction.WITHDRAWAL))
    }

    @Test
    fun reversibleAccountActionsSubmitWithoutConfirmation() {
        assertFalse(accountActionRequiresConfirmation(AccountAction.WALLET_BIND_OR_REPLACE))
        assertFalse(accountActionRequiresConfirmation(AccountAction.SEND_VERIFICATION))
    }

    @Test
    fun withdrawalConversionShowsCnyMinimumUsdAndEstimatedUsdt() {
        val summary = WithdrawalSummary(
            balance = 5000,
            frozenPoints = 200,
            availablePoints = 4800,
            minimumPoints = 3600,
            usdtPerPoint = "0.002777778",
            walletAddress = "TTest",
            cnyPerPoint = "0.02",
            cnyPerUsd = "7.2",
            minimumUsd = "10",
        )
        val lines = withdrawalConversionLines(summary, 4000, AppLanguage.ENGLISH)
        assertEquals(true, lines.any { it.contains("0.02 CNY") })
        assertEquals(true, lines.any { it.contains("10 USD") })
        assertEquals(true, lines.any { it.contains("11.111111 USDT") })
    }

    @Test
    fun withdrawalConversionFallsBackWhenNewCurrencyFieldsAreMissing() {
        val legacySummary = WithdrawalSummary(
            balance = 100,
            frozenPoints = 0,
            availablePoints = 100,
            minimumPoints = 50,
            usdtPerPoint = "0.001",
            walletAddress = null,
        )

        val lines = withdrawalConversionLines(legacySummary, 60, AppLanguage.ENGLISH)

        assertEquals(1, lines.size)
        assertEquals(true, lines.single().contains("Min 50"))
        assertEquals(true, lines.single().contains("0.001 USDT"))
    }
}
