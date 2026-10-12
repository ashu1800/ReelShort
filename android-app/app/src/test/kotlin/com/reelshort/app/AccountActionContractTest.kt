package com.reelshort.app

import com.reelshort.app.ui.format.AccountAction
import com.reelshort.app.ui.format.accountActionRequiresConfirmation
import com.reelshort.app.ui.format.strings
import com.reelshort.app.ui.format.withdrawalPointAmountPlaceholder
import com.reelshort.app.ui.format.withdrawalConversionLines
import com.reelshort.app.data.AppLanguage
import com.reelshort.app.data.WithdrawalSummary
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path

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

    @Test
    fun withdrawalAmountFieldShowsArrivalHintOnlyWhenEmptyAndUnfocused() {
        assertEquals("Arrives in 1 day", strings(AppLanguage.ENGLISH).accountWithdrawArrivalHint)
        assertEquals("Arrives in 1 day", withdrawalPointAmountPlaceholder("", isFocused = false, AppLanguage.ENGLISH))
        assertEquals(null, withdrawalPointAmountPlaceholder("", isFocused = true, AppLanguage.ENGLISH))
        assertEquals(null, withdrawalPointAmountPlaceholder("100", isFocused = false, AppLanguage.ENGLISH))
    }

    @Test
    fun withdrawalAmountFieldDoesNotDependOnMaterialFocusedPlaceholder() {
        val textFieldSource = readSourceFile(
            "src/main/java/com/reelshort/app/ui/components/TextField.kt",
            "app/src/main/java/com/reelshort/app/ui/components/TextField.kt",
            "android-app/app/src/main/java/com/reelshort/app/ui/components/TextField.kt",
        )
        // 提现数量输入框的唯一实现在资金中心的提现页（Me 页旧的无二次确认提现弹层已删除）。
        val withdrawalSource = readSourceFile(
            "src/main/java/com/reelshort/app/ui/screens/account/WithdrawalScreen.kt",
            "app/src/main/java/com/reelshort/app/ui/screens/account/WithdrawalScreen.kt",
            "android-app/app/src/main/java/com/reelshort/app/ui/screens/account/WithdrawalScreen.kt",
        )

        assertTrue(textFieldSource.contains("idlePlaceholder: String? = null"))
        assertTrue(textFieldSource.contains("val fieldLabel = idlePlaceholder ?: label"))
        assertFalse(textFieldSource.contains("placeholder = placeholder"))
        assertTrue(withdrawalSource.contains("idlePlaceholder = withdrawalPointAmountPlaceholder(pointsText, pointsFocused, language)"))
    }

    private fun readSourceFile(vararg candidates: String): String {
        val roots = generateSequence(Path.of(System.getProperty("user.dir")).toAbsolutePath()) { it.parent }
        roots.forEach { root ->
            candidates.forEach { candidate ->
                val path = root.resolve(candidate)
                if (Files.exists(path)) {
                    return String(Files.readAllBytes(path))
                }
            }
        }
        error("Could not find source file: ${candidates.joinToString()}")
    }
}
