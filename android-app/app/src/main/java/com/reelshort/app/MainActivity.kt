package com.reelshort.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.reelshort.app.ui.MainShell
import com.reelshort.app.ui.ReelShortViewModel
import com.reelshort.app.ui.components.LoadingDialog
import com.reelshort.app.ui.components.TopErrorToast
import com.reelshort.app.ui.screens.auth.AuthBottomSheet
import com.reelshort.app.ui.theme.ReelShortTheme
import kotlinx.coroutines.delay

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
private fun ReelShortApp(viewModel: ReelShortViewModel) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(viewModel) {
        viewModel.bootstrap()
    }
    LaunchedEffect(state.errorMessage) {
        if (state.errorMessage != null) {
            delay(4_000)
            viewModel.clearError()
        }
    }

    // 回调 lambda 用 remember 固化为稳定实例，避免每次重组创建新 lambda 导致下游级联重组（O2）。
    val onScreenSelected = remember(viewModel) { viewModel::selectScreen }
    val onLogout = remember(viewModel) { viewModel::logout }
    val onSearch = remember(viewModel) { viewModel::search }
    val onOpenBook = remember(viewModel) { viewModel::openBook }
    val onOpenPlayer = remember(viewModel) { viewModel::openPlayer }
    val onUpdatePlaybackPosition = remember(viewModel) { viewModel::updatePlaybackPosition }
    val onAutoReportProgress = remember(viewModel) { viewModel::reportProgressSilently }
    val onCheckApiHealth = remember(viewModel) { viewModel::checkApiHealth }
    val onShowAuthPrompt = remember(viewModel) { viewModel::showAuthPrompt }
    val onLogin = remember(viewModel) { { u: String, p: String, r: Boolean -> viewModel.login(u, p, r) } }
    val onRegister = remember(viewModel) { { u: String, p: String, r: Boolean -> viewModel.register(u, p, r) } }
    val onDismissAuthPrompt = remember(viewModel) { viewModel::dismissAuthPrompt }
    val onErrorDismiss = remember(viewModel) { viewModel::clearError }

    ReelShortTheme {
        Box(modifier = Modifier.fillMaxSize()) {
            MainShell(
                state = state,
                onScreenSelected = onScreenSelected,
                onLogout = onLogout,
                onSearch = onSearch,
                onOpenBook = onOpenBook,
                onOpenPlayer = onOpenPlayer,
                onUpdatePlaybackPosition = onUpdatePlaybackPosition,
                onAutoReportProgress = onAutoReportProgress,
                onCheckApiHealth = onCheckApiHealth,
                onShowAuthPrompt = onShowAuthPrompt,
            )
            AuthBottomSheet(
                visible = state.authPromptVisible,
                state = state,
                onLogin = onLogin,
                onRegister = onRegister,
                onDismiss = onDismissAuthPrompt,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .zIndex(2f),
            )
            TopErrorToast(
                message = state.errorMessage,
                onDismiss = onErrorDismiss,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .zIndex(2f),
            )
            LoadingDialog(
                visible = state.isLoading,
                modifier = Modifier
                    .align(Alignment.Center)
                    .zIndex(1f),
            )
        }
    }
}
