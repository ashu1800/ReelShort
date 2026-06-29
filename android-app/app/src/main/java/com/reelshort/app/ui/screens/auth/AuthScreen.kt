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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.reelshort.app.state.AppUiState
import com.reelshort.app.ui.components.AccentLine
import com.reelshort.app.ui.components.LoginTextField
import com.reelshort.app.ui.components.PrimaryActionButton
import com.reelshort.app.ui.components.RememberPasswordRow
import com.reelshort.app.ui.components.SurfacePanel
import com.reelshort.app.ui.format.authPromptTitle
import com.reelshort.app.ui.theme.AppBackground
import com.reelshort.app.ui.theme.PrimaryGold
import com.reelshort.app.ui.theme.TextSecondary

@Composable
internal fun LoginScreen(
    state: AppUiState,
    onLogin: (String, String, Boolean) -> Unit,
    onRegister: (String, String, Boolean) -> Unit,
) {
    AppBackground {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 44.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            item {
                BrandLockup()
                Spacer(Modifier.height(34.dp))
                SurfacePanel {
                    AuthForm(
                        state = state,
                        title = "账号登录",
                        subtitle = "继续浏览推荐短剧和观看记录",
                        registerAsPrimary = false,
                        onLogin = onLogin,
                        onRegister = onRegister,
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
    onRegister: (String, String, Boolean) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
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
                title = authPromptTitle(state.pendingPlaybackEpisode != null),
                subtitle = if (state.pendingPlaybackEpisode != null) {
                    "播放、积分和账户数据需要登录账号。"
                } else {
                    "登录后可以查看积分、观看记录和订单。"
                },
                registerAsPrimary = true,
                onLogin = onLogin,
                onRegister = onRegister,
                onDismiss = onDismiss,
                modifier = Modifier.padding(horizontal = 22.dp, vertical = 20.dp),
            )
        }
    }
}

@Composable
private fun BrandLockup() {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("ReelShort", style = MaterialTheme.typography.displaySmall)
        Text("聚合短剧播放平台", style = MaterialTheme.typography.titleMedium, color = TextSecondary)
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
    onLogin: (String, String, Boolean) -> Unit,
    onRegister: (String, String, Boolean) -> Unit,
    onDismiss: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    var username by remember(state.savedCredentials) { mutableStateOf(state.savedCredentials?.username ?: state.session?.username.orEmpty()) }
    var password by remember(state.savedCredentials) { mutableStateOf(state.savedCredentials?.password.orEmpty()) }
    var rememberPassword by remember(state.savedCredentials) { mutableStateOf(state.savedCredentials?.rememberPassword == true) }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleLarge)
                Text(subtitle, color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
            }
            if (onDismiss != null) {
                TextButton(onClick = onDismiss, enabled = !state.isLoading) {
                    Text("关闭", color = TextSecondary)
                }
            }
        }
        LoginTextField(
            value = username,
            onValueChange = { username = it },
            label = "用户名",
            enabled = !state.isLoading,
        )
        LoginTextField(
            value = password,
            onValueChange = { password = it },
            label = "密码",
            enabled = !state.isLoading,
            isPassword = true,
        )
        RememberPasswordRow(
            checked = rememberPassword,
            onCheckedChange = { rememberPassword = it },
            enabled = !state.isLoading,
        )
        PrimaryActionButton(
            text = if (state.isLoading) "登录中" else "登录",
            enabled = !state.isLoading && username.isNotBlank() && password.isNotBlank(),
            onClick = { onLogin(username, password, rememberPassword) },
        )
        if (registerAsPrimary) {
            com.reelshort.app.ui.components.GoldOutlinedButton(
                text = "注册",
                enabled = !state.isLoading && username.isNotBlank() && password.isNotBlank(),
                onClick = { onRegister(username, password, rememberPassword) },
                modifier = Modifier.fillMaxWidth(),
                contentColor = PrimaryGold,
            )
        } else {
            TextButton(
                onClick = { onRegister(username, password, rememberPassword) },
                enabled = !state.isLoading && username.isNotBlank() && password.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("注册新账号", color = PrimaryGold)
            }
        }
    }
}
