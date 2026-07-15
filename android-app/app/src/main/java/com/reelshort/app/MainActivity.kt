package com.reelshort.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import androidx.activity.compose.BackHandler
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.util.UnstableApi
import com.reelshort.app.state.AppScreen
import com.reelshort.app.ui.MainShell
import com.reelshort.app.ui.ReelShortViewModel
import com.reelshort.app.ui.components.GeoBlockedScreen
import com.reelshort.app.ui.components.LoadingDialog
import com.reelshort.app.ui.components.TopErrorToast
import com.reelshort.app.ui.components.UpdateDialog
import com.reelshort.app.ui.format.strings
import com.reelshort.app.ui.format.updateStrings
import com.reelshort.app.ui.screens.auth.AuthBottomSheet
import com.reelshort.app.ui.theme.ReelShortTheme
import kotlinx.coroutines.delay
import com.reelshort.app.update.UpdateState

@UnstableApi
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.rgb(8, 10, 15)),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.rgb(8, 10, 15)),
        )
        setContent {
            val viewModel: ReelShortViewModel = viewModel(factory = ReelShortViewModel.factory(application))
            ReelShortApp(viewModel)
        }
    }
}

@Composable
@UnstableApi
private fun ReelShortApp(viewModel: ReelShortViewModel) {
    val state by viewModel.state.collectAsState()
    val updateState by viewModel.updateState.collectAsState()
    val updateCopy = updateStrings(state.language)
    val installPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) {
        viewModel.onInstallPermissionResult()
    }

    LaunchedEffect(viewModel) {
        viewModel.bootstrap()
    }
    LaunchedEffect(state.errorMessage) {
        if (state.errorMessage != null) {
            delay(4_000)
            viewModel.clearError()
        }
    }
    LaunchedEffect(updateState) {
        val current = updateState
        if (current is UpdateState.UpToDate || (current is UpdateState.Failed && current.release == null)) {
            delay(4_000)
            viewModel.dismissUpdate()
        }
    }
    LaunchedEffect(updateState) {
        if (updateState is UpdateState.PermissionRequired) {
            installPermissionLauncher.launch(viewModel.unknownSourcesSettingsIntent())
        }
    }

    // 回调 lambda 用 remember 固化为稳定实例，避免每次重组创建新 lambda 导致下游级联重组（O2）。
    val onScreenSelected = remember(viewModel) { viewModel::selectScreen }
    val onLogout = remember(viewModel) { viewModel::logout }
    val onSearch = remember(viewModel) { viewModel::search }
    val onOpenBook = remember(viewModel) { viewModel::openBook }
    val onOpenWatchRecord = remember(viewModel) { viewModel::openWatchRecord }
    val onOpenPlayer = remember(viewModel) { viewModel::openPlayer }
    val onUpdatePlaybackPosition = remember(viewModel) { viewModel::updatePlaybackPosition }
    val onAutoReportProgress = remember(viewModel) { viewModel::reportProgressSilently }
    val onToggleLike = remember(viewModel) { viewModel::toggleLike }
    val onToggleFavorite = remember(viewModel) { viewModel::toggleFavorite }
    val onSubmitComment = remember(viewModel) { { content: String -> viewModel.submitComment(content) } }
    val onOpenFavorites = remember(viewModel) { viewModel::openFavorites }
    val onBackFromPlayer = remember(viewModel) { viewModel::backFromPlayer }
    val onBackFromFavorites = remember(viewModel) { viewModel::backFromFavorites }
    val onCheckForUpdate = remember(viewModel) { viewModel::checkForUpdate }
    val onDownloadUpdate = remember(viewModel) { viewModel::downloadUpdate }
    val onCancelUpdateDownload = remember(viewModel) { viewModel::cancelUpdateDownload }
    val onInstallUpdate = remember(viewModel) { viewModel::installUpdate }
    val onDismissUpdate = remember(viewModel) { viewModel::dismissUpdate }
    val onShowAuthPrompt = remember(viewModel) { viewModel::showAuthPrompt }
    val onShowRegisterAuthPrompt = remember(viewModel) {
        {
            viewModel.showRegisterAuthMode()
            viewModel.showAuthPrompt()
        }
    }
    val onRefreshHome = remember(viewModel) { viewModel::refreshHome }
    val onSetLanguage = remember(viewModel) { viewModel::setLanguage }
    val onBindWallet = remember(viewModel) { viewModel::bindWallet }
    val onUnbindWallet = remember(viewModel) { viewModel::unbindWallet }
    val onCreateVipOrder = remember(viewModel) { viewModel::createVipOrder }
    val onSubmitWithdrawal = remember(viewModel) { viewModel::submitWithdrawal }
    val onTransferPoints = remember(viewModel) { viewModel::transferPoints }
    val onChangePassword = remember(viewModel) { viewModel::changePassword }
    val onSubmitBankCard = remember(viewModel) { viewModel::submitBankCard }

    // 播放器与收藏页为全屏沉浸式，系统返回键回到上一级而非退出 App
    BackHandler(enabled = state.screen == AppScreen.PLAYER) {
        viewModel.backFromPlayer()
    }
    BackHandler(enabled = state.screen == AppScreen.FAVORITES) {
        viewModel.backFromFavorites()
    }
    val onLogin = remember(viewModel) { { u: String, p: String, r: Boolean -> viewModel.login(u, p, r) } }
    val onRegister = remember(viewModel) { { u: String, p: String, captchaId: String, answer: String -> viewModel.register(u, p, captchaId, answer) } }
    val onFetchCaptcha = remember(viewModel) { viewModel::fetchCaptcha }
    val onDismissAuthPrompt = remember(viewModel) { viewModel::dismissAuthPrompt }
    val onShowRegisterAuthMode = remember(viewModel) { viewModel::showRegisterAuthMode }
    val onShowLoginAuthMode = remember(viewModel) { viewModel::showLoginAuthMode }
    val onErrorDismiss = remember(viewModel) { viewModel::clearError }

    ReelShortTheme {
        Box(modifier = Modifier.fillMaxSize()) {
            if (state.geoBlocked) {
                val copy = strings(state.language)
                GeoBlockedScreen(
                    title = copy.geoBlockedTitle,
                    message = copy.geoBlockedMessage,
                    onExit = { android.os.Process.killProcess(android.os.Process.myPid()) },
                )
                return@Box
            }
            MainShell(
                state = state,
                onScreenSelected = onScreenSelected,
                onLogout = onLogout,
                onSearch = onSearch,
                onOpenBook = onOpenBook,
                onOpenWatchRecord = onOpenWatchRecord,
                onOpenPlayer = onOpenPlayer,
                onUpdatePlaybackPosition = onUpdatePlaybackPosition,
                onAutoReportProgress = onAutoReportProgress,
                onToggleLike = onToggleLike,
                onToggleFavorite = onToggleFavorite,
                onSubmitComment = onSubmitComment,
                onOpenFavorites = onOpenFavorites,
                onBackFromPlayer = onBackFromPlayer,
                onBackFromFavorites = onBackFromFavorites,
                appVersionLabel = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                isCheckingForUpdate = (updateState as? UpdateState.Checking)?.manual == true,
                onCheckForUpdate = onCheckForUpdate,
                onShowAuthPrompt = onShowAuthPrompt,
                onShowRegisterAuthPrompt = onShowRegisterAuthPrompt,
                onRefreshHome = onRefreshHome,
                onSetLanguage = onSetLanguage,
                onBindWallet = onBindWallet,
                onUnbindWallet = onUnbindWallet,
                onCreateVipOrder = onCreateVipOrder,
                onSubmitWithdrawal = onSubmitWithdrawal,
                onTransferPoints = onTransferPoints,
                onChangePassword = onChangePassword,
                onSubmitBankCard = onSubmitBankCard,
            )
            AuthBottomSheet(
                visible = state.authPromptVisible,
                state = state,
                onLogin = onLogin,
                onRegister = onRegister,
                onFetchCaptcha = onFetchCaptcha,
                onShowRegister = onShowRegisterAuthMode,
                onShowLogin = onShowLoginAuthMode,
                onDismiss = onDismissAuthPrompt,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .zIndex(2f),
            )
            TopErrorToast(
                message = state.errorMessage,
                type = state.messageType ?: com.reelshort.app.state.UiMessageType.ERROR,
                onDismiss = onErrorDismiss,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .zIndex(2f),
            )
            LoadingDialog(
                visible = state.isLoading,
                language = state.language,
                modifier = Modifier
                    .align(Alignment.Center)
                    .zIndex(1f),
            )
            UpdateDialog(
                state = updateState,
                language = state.language,
                onDownload = onDownloadUpdate,
                onCancelDownload = onCancelUpdateDownload,
                onInstall = onInstallUpdate,
                onOpenSettings = { installPermissionLauncher.launch(viewModel.unknownSourcesSettingsIntent()) },
                onDismiss = onDismissUpdate,
            )
            val updateMessage = when (val current = updateState) {
                is UpdateState.UpToDate -> updateCopy.latestVersion
                is UpdateState.Failed -> if (current.release == null) updateCopy.checkFailed else null
                else -> null
            }
            TopErrorToast(
                message = updateMessage,
                type = if (updateState is UpdateState.UpToDate) {
                    com.reelshort.app.state.UiMessageType.SUCCESS
                } else {
                    com.reelshort.app.state.UiMessageType.ERROR
                },
                onDismiss = onDismissUpdate,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .zIndex(3f),
            )
        }
    }
}
