package com.reelshort.app.ui.screens.auth

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import android.graphics.BitmapFactory
import android.util.Base64
import com.reelshort.app.data.AppLanguage
import com.reelshort.app.state.AuthMode
import com.reelshort.app.state.AppUiState
import com.reelshort.app.ui.components.AccentLine
import com.reelshort.app.ui.components.GoldOutlinedButton
import com.reelshort.app.ui.components.LoginTextField
import com.reelshort.app.ui.components.TextFieldKind
import com.reelshort.app.ui.components.PrimaryActionButton
import com.reelshort.app.ui.components.RememberPasswordRow
import com.reelshort.app.ui.components.SecondaryActionTextButton
import com.reelshort.app.ui.components.SurfacePanel
import com.reelshort.app.ui.format.authRegisterEnabled
import com.reelshort.app.ui.format.authSheetCopy
import com.reelshort.app.ui.format.appBrandName
import com.reelshort.app.ui.format.strings
import com.reelshort.app.ui.theme.AppBackground
import com.reelshort.app.ui.theme.PrimaryGold
import com.reelshort.app.ui.theme.TextSecondary

@Composable
internal fun LoginScreen(
    state: AppUiState,
    onLogin: (String, String, Boolean) -> Unit,
    onRegister: (String, String, String, String) -> Unit,
    onFetchCaptcha: () -> Unit,
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
                        onLogin = onLogin,
                        onRegister = onRegister,
                        onFetchCaptcha = onFetchCaptcha,
                        onShowRegister = {},
                        onShowLogin = {},
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
    onLogin: (String, String, Boolean) -> Unit,
    onRegister: (String, String, String, String) -> Unit,
    onFetchCaptcha: () -> Unit,
    onShowRegister: () -> Unit,
    onShowLogin: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    androidx.compose.animation.AnimatedVisibility(
        visible = visible,
        enter = androidx.compose.animation.slideInVertically { it } + androidx.compose.animation.fadeIn(),
        exit = androidx.compose.animation.slideOutVertically { it } + androidx.compose.animation.fadeOut(),
        modifier = modifier,
    ) {
        val panelCopy = authSheetCopy(
            mode = state.authMode,
            hasPendingPlayback = state.pendingPlaybackEpisode != null,
            language = state.language,
        )
        androidx.compose.material3.Surface(
            modifier = Modifier.fillMaxWidth(),
            color = com.reelshort.app.ui.theme.Panel,
            contentColor = com.reelshort.app.ui.theme.TextPrimary,
            border = androidx.compose.foundation.BorderStroke(1.dp, com.reelshort.app.ui.theme.Divider),
            shape = com.reelshort.app.ui.theme.BottomSheetShape,
        ) {
            AuthForm(
                state = state,
                title = panelCopy.title,
                subtitle = panelCopy.subtitle,
                onLogin = onLogin,
                onRegister = onRegister,
                onFetchCaptcha = onFetchCaptcha,
                onShowRegister = onShowRegister,
                onShowLogin = onShowLogin,
                onDismiss = onDismiss,
                modifier = Modifier
                    .padding(horizontal = 22.dp, vertical = 20.dp)
                    .navigationBarsPadding()
                    .imePadding()
                    .verticalScroll(rememberScrollState()),
            )
        }
    }
}

@Composable
private fun BrandLockup(language: AppLanguage) {
    val copy = strings(language)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(appBrandName(), style = MaterialTheme.typography.displaySmall)
        Text(copy.authBrandSubtitle, style = MaterialTheme.typography.titleMedium, color = TextSecondary)
        AccentLine()
    }
}

/**
 * 统一的认证表单，复用全屏登录与底部弹窗两处此前重复的实现。
 */
@Composable
internal fun AuthForm(
    state: AppUiState,
    title: String,
    subtitle: String,
    onLogin: (String, String, Boolean) -> Unit,
    onRegister: (String, String, String, String) -> Unit,
    onFetchCaptcha: () -> Unit,
    onShowRegister: () -> Unit,
    onShowLogin: () -> Unit,
    onDismiss: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val copy = strings(state.language)
    var username by remember(state.savedCredentials) { mutableStateOf(state.savedCredentials?.username ?: "") }
    var password by remember(state.savedCredentials) { mutableStateOf(state.savedCredentials?.password.orEmpty()) }
    var confirmPassword by remember { mutableStateOf("") }
    var captchaAnswer by remember { mutableStateOf("") }
    var rememberPassword by remember(state.savedCredentials) { mutableStateOf(state.savedCredentials?.rememberPassword == true) }

    val isLoginMode = state.authMode == AuthMode.LOGIN

    // 注册模式下：进入注册模式或验证码尚未加载时，自动拉取一次图形验证码。
    LaunchedEffect(state.authMode) {
        if (!isLoginMode && state.captchaImageBase64.isBlank()) {
            onFetchCaptcha()
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
        LoginTextField(
            value = username,
            onValueChange = { username = it },
            label = copy.authUsernameLabel,
            enabled = !state.isLoading,
            kind = TextFieldKind.PHONE,
        )
        LoginTextField(
            value = password,
            onValueChange = { password = it },
            label = copy.authPasswordLabel,
            enabled = !state.isLoading,
            isPassword = true,
            kind = TextFieldKind.PASSWORD,
        )
        if (isLoginMode) {
            RememberPasswordRow(
                checked = rememberPassword,
                onCheckedChange = { rememberPassword = it },
                enabled = !state.isLoading,
                language = state.language,
            )
            PrimaryActionButton(
                text = if (state.isLoading) copy.authLoginLoading else copy.authLoginAction,
                enabled = !state.isLoading && username.isNotBlank() && password.isNotBlank(),
                onClick = { onLogin(username, password, rememberPassword) },
            )
            SecondaryActionTextButton(
                text = copy.authRegisterSecondary,
                onClick = onShowRegister,
                enabled = !state.isLoading,
            )
        } else {
            LoginTextField(
                value = confirmPassword,
                onValueChange = { confirmPassword = it },
                label = copy.authConfirmPasswordLabel,
                enabled = !state.isLoading,
                isPassword = true,
                kind = TextFieldKind.PASSWORD,
            )
            CaptchaBlock(
                imageBase64 = state.captchaImageBase64,
                captchaAnswer = captchaAnswer,
                onAnswerChange = { captchaAnswer = it },
                onRefresh = onFetchCaptcha,
                language = state.language,
                enabled = !state.isLoading,
                isLoading = state.isLoading,
            )
            PrimaryActionButton(
                text = copy.authRegisterAction,
                enabled = authRegisterEnabled(
                    isLoading = state.isLoading,
                    username = username,
                    password = password,
                    confirmPassword = confirmPassword,
                    captchaAnswer = captchaAnswer,
                    captchaLoaded = state.captchaImageBase64.isNotBlank(),
                ),
                onClick = { onRegister(username, password, state.captchaId, captchaAnswer) },
            )
            SecondaryActionTextButton(
                text = copy.authLoginSecondary,
                onClick = onShowLogin,
                enabled = !state.isLoading,
            )
        }
    }
}

@Composable
private fun CaptchaBlock(
    imageBase64: String,
    captchaAnswer: String,
    onAnswerChange: (String) -> Unit,
    onRefresh: () -> Unit,
    language: AppLanguage,
    enabled: Boolean,
    isLoading: Boolean,
) {
    val copy = strings(language)
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LoginTextField(
            value = captchaAnswer,
            onValueChange = onAnswerChange,
            label = copy.authCaptchaAnswerLabel,
            enabled = enabled,
            kind = TextFieldKind.VERIFICATION_CODE,
            modifier = Modifier.weight(1f),
        )
        CaptchaImage(
            imageBase64 = imageBase64,
            onRefresh = onRefresh,
            refreshLabel = copy.authCaptchaRefresh,
            loadingLabel = copy.authCaptchaLoading,
            enabled = enabled && !isLoading,
        )
    }
}

@Composable
private fun CaptchaImage(
    imageBase64: String,
    onRefresh: () -> Unit,
    refreshLabel: String,
    loadingLabel: String,
    enabled: Boolean,
) {
    val bitmap = remember(imageBase64) {
        runCatching {
            // Backend returns "data:image/png;base64,..." — strip the data URI prefix
            val pureBase64 = if (imageBase64.contains(",")) imageBase64.substringAfter(",") else imageBase64
            val bytes = Base64.decode(pureBase64, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        }.getOrNull()
    }
    Box(
        modifier = Modifier.size(width = 110.dp, height = 52.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = refreshLabel,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .clickable(enabled = enabled, onClick = onRefresh),
            )
        } else {
            Text(
                if (imageBase64.isBlank()) loadingLabel else refreshLabel,
                color = TextSecondary,
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
    GoldOutlinedButton(
        text = refreshLabel,
        enabled = enabled,
        onClick = onRefresh,
        modifier = Modifier.height(52.dp),
        contentColor = PrimaryGold,
    )
}

internal fun authFormControls(mode: AuthMode, language: AppLanguage): List<String> {
    val copy = strings(language)
    return when (mode) {
        AuthMode.LOGIN -> listOf(
            "username",
            "password",
            "rememberPassword",
            "primary:${copy.authLoginAction}",
            "secondary:${copy.authRegisterSecondary}",
        )
        AuthMode.REGISTER -> listOf(
            "username",
            "password",
            "confirmPassword",
            "captchaImage",
            "captchaAnswer",
            "primary:${copy.authRegisterAction}",
            "secondary:${copy.authLoginSecondary}",
        )
    }
}
