package com.reelshort.app

import com.reelshort.app.ui.format.walletSheetShouldDismiss
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WalletSheetContractTest {
    @Test
    fun visibleWalletSheetDismissesWhenMutationVersionAdvances() {
        assertTrue(
            walletSheetShouldDismiss(
                visible = true,
                lastHandledVersion = 2,
                currentVersion = 3,
            ),
        )
    }

    @Test
    fun hiddenWalletSheetDoesNotDismissForCompletedMutation() {
        assertFalse(
            walletSheetShouldDismiss(
                visible = false,
                lastHandledVersion = 2,
                currentVersion = 3,
            ),
        )
    }

    @Test
    fun visibleWalletSheetStaysOpenWithoutNewCompletedMutation() {
        assertFalse(
            walletSheetShouldDismiss(
                visible = true,
                lastHandledVersion = 3,
                currentVersion = 3,
            ),
        )
    }
}
