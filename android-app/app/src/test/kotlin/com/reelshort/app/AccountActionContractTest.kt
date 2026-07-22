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
    fun withdrawalConversionShowsMinimumsAndEstimatedUsdtWithoutRate() {
        val summary = WithdrawalSummary(
            balance = 5000,
            frozenPoints = 200,
            availablePoints = 4800,
            minimumPoints = 5,
            usdtPerPoint = "0.0028",
            usdtPer50Points = "0.14",
            minimumUsdt = "0.01",
            walletAddress = "0xTest",
            feePercent = 10,
        )
        val lines = withdrawalConversionLines(summary, 4000, AppLanguage.ENGLISH)
        assertEquals(true, lines.any { it.contains("Min 5 pts / 0.01 USDT") })
        assertEquals(true, lines.any { it.contains("Estimated 10.08 USDT") })
        assertEquals(true, lines.any { it.contains("Fee: 10% (400 pts)") })
        assertEquals(false, lines.any { it.contains("50 pts") || it.contains("0.14") })
        assertEquals(false, lines.any { it.contains("CNY") || it.contains("USD ") })
    }

    @Test
    fun withdrawalConversionRoundsEstimatedUsdtDownToCents() {
        val summary = WithdrawalSummary(
            balance = 100,
            frozenPoints = 0,
            availablePoints = 100,
            minimumPoints = 1,
            usdtPerPoint = "0.12459111",
            usdtPer50Points = "6.22955556",
            minimumUsdt = "0.01",
            walletAddress = null,
            feePercent = 10,
        )

        val lines = withdrawalConversionLines(summary, 101, AppLanguage.ENGLISH)

        assertEquals(true, lines.any { it.contains("Estimated 11.21 USDT") })
    }
}
