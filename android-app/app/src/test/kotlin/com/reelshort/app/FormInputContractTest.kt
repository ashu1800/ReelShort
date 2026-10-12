package com.reelshort.app

import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.autofill.AutofillType
import androidx.compose.ui.ExperimentalComposeUiApi
import com.reelshort.app.data.AppLanguage
import com.reelshort.app.ui.components.TextFieldKind
import com.reelshort.app.ui.components.keyboardTypeFor
import com.reelshort.app.ui.components.passwordFieldsAllowVisibilityToggle
import com.reelshort.app.ui.components.autofillTypesFor
import com.reelshort.app.ui.format.strings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path

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

    /**
     * P1-4 真实契约：Me 页的银行卡入口只是只读的"暂不支持"说明，不再打开任何采集弹层。
     */
    @Test
    fun mePageKeepsBankCardEntryAsReadOnlyUnsupportedNotice() {
        val accountSource = readSourceFile(
            "src/main/java/com/reelshort/app/ui/screens/account/AccountScreen.kt",
            "app/src/main/java/com/reelshort/app/ui/screens/account/AccountScreen.kt",
            "android-app/app/src/main/java/com/reelshort/app/ui/screens/account/AccountScreen.kt",
        )

        assertTrue(accountSource.contains("subtitle = copy.accountBankCardUnsupported"))
        assertFalse(accountSource.contains("BankCardBottomSheet"))
        assertFalse(accountSource.contains("onSubmitBankCard"))
        assertFalse(accountSource.contains("bankCardSheetVisible"))
        assertTrue(strings(AppLanguage.ENGLISH).accountBankCardUnsupported.isNotBlank())
    }

    /**
     * P1-4 真实契约：整个 App（app + app-core main 源集）不得出现卡号/CVV 采集或上传代码。
     *
     * 说明：此前这里是 `assertFalse(bankCardEntryCollectsSensitiveData())` —— 只断言一个恒为
     * false 的常量，与真实 UI 完全相反（弹层实际采集 cardNumber + CVV 并上传）。现在改为扫描
     * 生产源码，任何卡号/CVV 输入或提交入口都会让本用例失败。
     */
    @Test
    fun productionSourcesNeverCollectOrSubmitCardNumberOrCvv() {
        val bannedTokens = listOf("cvv", "cardnumber", "bankcardbind", "submitbankcard", "/wallet/bank-card")
        val offenders = appCoreMainSources()
            .plus(appMainSources())
            .flatMap { file ->
                val content = String(Files.readAllBytes(file)).lowercase()
                bannedTokens.filter { it in content }.map { "${moduleRoot().relativize(file)} -> $it" }
            }

        assertTrue("生产源码中仍有银行卡敏感信息采集/提交残留：$offenders", offenders.isEmpty())
    }

    private fun moduleRoot(): Path {
        val roots = generateSequence(Path.of(System.getProperty("user.dir")).toAbsolutePath()) { it.parent }
        return roots.firstOrNull { Files.isDirectory(it.resolve("app-core/src/main/kotlin")) }
            ?: error("Could not locate the android-app module root")
    }

    private fun appMainSources(): List<Path> = kotlinSources(moduleRoot().resolve("app/src/main/java"))

    private fun appCoreMainSources(): List<Path> = kotlinSources(moduleRoot().resolve("app-core/src/main/kotlin"))

    private fun kotlinSources(root: Path): List<Path> =
        if (!Files.isDirectory(root)) {
            emptyList()
        } else {
            Files.walk(root).use { stream ->
                stream.filter { Files.isRegularFile(it) && it.fileName.toString().endsWith(".kt") }.toList()
            }
        }

    private fun readSourceFile(vararg candidates: String): String {
        val roots = generateSequence(Path.of(System.getProperty("user.dir")).toAbsolutePath()) { it.parent }
        roots.forEach { root ->
            candidates.forEach { candidate ->
                val path = root.resolve(candidate)
                if (Files.exists(path)) return String(Files.readAllBytes(path))
            }
        }
        error("Could not find source file: ${candidates.joinToString()}")
    }
}
