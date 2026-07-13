package com.reelshort.app

import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.autofill.AutofillType
import androidx.compose.ui.ExperimentalComposeUiApi
import com.reelshort.app.ui.components.TextFieldKind
import com.reelshort.app.ui.components.keyboardTypeFor
import com.reelshort.app.ui.components.passwordFieldsAllowVisibilityToggle
import com.reelshort.app.ui.components.autofillTypesFor
import com.reelshort.app.ui.format.bankCardEntryCollectsSensitiveData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalComposeUiApi::class)
class FormInputContractTest {
    @Test
    fun formFieldsUsePurposeSpecificKeyboards() {
        assertEquals(KeyboardType.Phone, keyboardTypeFor(TextFieldKind.PHONE))
        assertEquals(KeyboardType.NumberPassword, keyboardTypeFor(TextFieldKind.VERIFICATION_CODE))
        assertEquals(KeyboardType.Number, keyboardTypeFor(TextFieldKind.POINT_AMOUNT))
        assertEquals(KeyboardType.Password, keyboardTypeFor(TextFieldKind.PASSWORD))
        assertEquals(KeyboardType.Text, keyboardTypeFor(TextFieldKind.TEXT))
    }

    @Test
    fun passwordFieldsCanBeRevealedTemporarily() {
        assertTrue(passwordFieldsAllowVisibilityToggle())
    }

    @Test
    fun phoneAndPasswordFieldsExposeAutofillPurpose() {
        assertEquals(listOf(AutofillType.PhoneNumber), autofillTypesFor(TextFieldKind.PHONE))
        assertEquals(listOf(AutofillType.Password), autofillTypesFor(TextFieldKind.PASSWORD))
        assertEquals(emptyList<AutofillType>(), autofillTypesFor(TextFieldKind.VERIFICATION_CODE))
    }

    @Test
    fun unsupportedBankCardEntryDoesNotCollectSensitiveData() {
        assertFalse(bankCardEntryCollectsSensitiveData())
    }
}
