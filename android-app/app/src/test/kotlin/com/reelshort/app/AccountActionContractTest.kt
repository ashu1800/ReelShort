package com.reelshort.app

import com.reelshort.app.ui.format.AccountAction
import com.reelshort.app.ui.format.accountActionRequiresConfirmation
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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
}
