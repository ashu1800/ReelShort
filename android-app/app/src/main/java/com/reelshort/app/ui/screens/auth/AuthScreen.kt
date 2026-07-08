package com.reelshort.app.ui.screens.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.reelshort.app.data.AppLanguage
import com.reelshort.app.state.AppUiState
import com.reelshort.app.ui.components.AccentLine
import com.reelshort.app.ui.components.GoldOutlinedButton
import com.reelshort.app.ui.components.LoginTextField
import com.reelshort.app.ui.components.PrimaryActionButton
import com.reelshort.app.ui.components.RememberPasswordRow
import com.reelshort.app.ui.components.SurfacePanel
import com.reelshort.app.ui.format.authPromptTitle
import com.reelshort.app.ui.format.authRegisterEnabled
import com.reelshort.app.ui.format.authSmsSendEnabled
import com.reelshort.app.ui.format.strings
import com.reelshort.app.ui.format.supportedPhoneCountryCodes
import com.reelshort.app.ui.format.smsVerificationSeconds
import com.reelshort.app.ui.theme.AppBackground
import com.reelshort.app.ui.theme.PrimaryGold
import com.reelshort.app.ui.theme.TextSecondary
import kotlinx.coroutines.delay

@Composable
internal fun LoginScreen(
    state: AppUiState,
    onLogin: (String, String, String, Boolean) -> Unit,
    onRegister: (String, String, String, String) -> Unit,
    onSendVerification: (String, String) -> Unit,
) {
    val copy = strings(state.language)
    AppBackground {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 44.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            item {
                BrandLockup(state.language)
                Spacer(Modifier.height(34.dp))
                SurfacePanel {
                    AuthForm(
                        state = state,
                        title = copy.authLoginTitle,
                        subtitle = copy.authLoginSubtitle,
                        registerAsPrimary = false,
                        onLogin = onLogin,
                        onRegister = onRegister,
                        onSendVerification = onSendVerification,
                    )
                }
            }
        }
    }
}

@Composable
internal fun AuthBottomSheet(
    visible: Boolean,
    state: AppUiState,
    onLogin: (String, String, String, Boolean) -> Unit,
    onRegister: (String, String, String, String) -> Unit,
    onSendVerification: (String, String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val copy = strings(state.language)
    androidx.compose.animation.AnimatedVisibility(
        visible = visible,
        enter = androidx.compose.animation.slideInVertically { it } + androidx.compose.animation.fadeIn(),
        exit = androidx.compose.animation.slideOutVertically { it } + androidx.compose.animation.fadeOut(),
        modifier = modifier,
    ) {
        androidx.compose.material3.Surface(
            modifier = Modifier.fillMaxWidth(),
            color = com.reelshort.app.ui.theme.Panel,
            contentColor = com.reelshort.app.ui.theme.TextPrimary,
            border = androidx.compose.foundation.BorderStroke(1.dp, com.reelshort.app.ui.theme.Divider),
            shape = com.reelshort.app.ui.theme.BottomSheetShape,
        ) {
            AuthForm(
                state = state,
                title = authPromptTitle(state.pendingPlaybackEpisode != null, state.language),
                subtitle = if (state.pendingPlaybackEpisode != null) {
                    copy.authBottomSheetPlaybackSubtitle
                } else {
                    copy.authBottomSheetAccountSubtitle
                },
                registerAsPrimary = true,
                onLogin = onLogin,
                onRegister = onRegister,
                onSendVerification = onSendVerification,
                onDismiss = onDismiss,
                modifier = Modifier.padding(horizontal = 22.dp, vertical = 20.dp),
            )
        }
    }
}

@Composable
private fun BrandLockup(language: AppLanguage) {
    val copy = strings(language)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("ReelShort", style = MaterialTheme.typography.displaySmall)
        Text(copy.authBrandSubtitle, style = MaterialTheme.typography.titleMedium, color = TextSecondary)
        AccentLine()
    }
}

/**
 * 统一的认证表单，复用全屏登录与底部弹窗两处此前重复的实现。
 * [registerAsPrimary] 控制注册按钮样式：全屏登录用 TextButton，底部弹窗用 OutlinedButton。
 */
@Composable
internal fun AuthForm(
    state: AppUiState,
    title: String,
    subtitle: String,
    registerAsPrimary: Boolean,
    onLogin: (String, String, String, Boolean) -> Unit,
    onRegister: (String, String, String, String) -> Unit,
    onSendVerification: (String, String) -> Unit,
    onDismiss: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val copy = strings(state.language)
    var countryCode by remember(state.savedCredentials) { mutableStateOf(state.savedCredentials?.countryCode ?: "+1") }
    var phoneNumber by remember(state.savedCredentials) { mutableStateOf(state.savedCredentials?.phoneNumber ?: "") }
    var password by remember(state.savedCredentials) { mutableStateOf(state.savedCredentials?.password.orEmpty()) }
    var verificationCode by remember { mutableStateOf("") }
    var rememberPassword by remember(state.savedCredentials) { mutableStateOf(state.savedCredentials?.rememberPassword == true) }
    var smsCountdown by remember { mutableStateOf(0) }

    LaunchedEffect(smsCountdown) {
        if (smsCountdown > 0) {
            delay(1_000)
            smsCountdown -= 1
        }
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleLarge)
                Text(subtitle, color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
            }
            if (onDismiss != null) {
                TextButton(onClick = onDismiss, enabled = !state.isLoading) {
                    Text(copy.authClose, color = TextSecondary)
                }
            }
        }
        CountryCodeSelector(
            selectedCode = countryCode,
            onSelected = { countryCode = it },
            enabled = !state.isLoading,
        )
        LoginTextField(
            value = phoneNumber,
            onValueChange = { phoneNumber = it.filter(Char::isDigit) },
            label = copy.authUsernameLabel,
            enabled = !state.isLoading,
        )
        LoginTextField(
            value = password,
            onValueChange = { password = it },
            label = copy.authPasswordLabel,
            enabled = !state.isLoading,
            isPassword = true,
        )
        RememberPasswordRow(
            checked = rememberPassword,
            onCheckedChange = { rememberPassword = it },
            enabled = !state.isLoading,
            language = state.language,
        )
        PrimaryActionButton(
            text = if (state.isLoading) copy.authLoginLoading else copy.authLoginAction,
            enabled = !state.isLoading && phoneNumber.isNotBlank() && password.isNotBlank(),
            onClick = { onLogin(countryCode, phoneNumber, password, rememberPassword) },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            LoginTextField(
                value = verificationCode,
                onValueChange = { verificationCode = it.filter(Char::isDigit).take(6) },
                label = "000000",
                enabled = !state.isLoading,
                modifier = Modifier.weight(1f),
            )
            GoldOutlinedButton(
                text = if (smsCountdown > 0) "${smsCountdown}s" else copy.authSendCode,
                enabled = authSmsSendEnabled(state.isLoading, smsCountdown, phoneNumber, password),
                onClick = {
                    smsCountdown = smsVerificationSeconds()
                    onSendVerification(countryCode, phoneNumber)
                },
                contentColor = PrimaryGold,
            )
        }
        if (registerAsPrimary) {
            com.reelshort.app.ui.components.GoldOutlinedButton(
                text = copy.authRegisterAction,
                enabled = authRegisterEnabled(state.isLoading, phoneNumber, password, verificationCode),
                onClick = { onRegister(countryCode, phoneNumber, password, verificationCode) },
                modifier = Modifier.fillMaxWidth(),
                contentColor = PrimaryGold,
            )
        } else {
            TextButton(
                onClick = { onRegister(countryCode, phoneNumber, password, verificationCode) },
                enabled = authRegisterEnabled(state.isLoading, phoneNumber, password, verificationCode),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(copy.authRegisterSecondary, color = PrimaryGold)
            }
        }
    }
}

@Composable
private fun CountryCodeSelector(
    selectedCode: String,
    onSelected: (String) -> Unit,
    enabled: Boolean,
) {
    var expanded by remember { mutableStateOf(false) }
    val codes = supportedPhoneCountryCodes()
    Column {
        GoldOutlinedButton(
            text = codes.firstOrNull { it.code == selectedCode }?.let { "${it.code} · ${it.label}" } ?: selectedCode,
            enabled = enabled,
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth(),
            contentColor = PrimaryGold,
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            codes.forEach { option ->
                DropdownMenuItem(
                    text = { Text("${option.code} · ${option.label}") },
                    onClick = {
                        expanded = false
                        onSelected(option.code)
                    },
                )
            }
        }
    }
}
