package com.reelshort.app

import android.content.Context
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import com.reelshort.app.config.ApiConfig
import com.reelshort.app.data.AppRepository
import com.reelshort.app.network.OkHttpReelShortApiClient
import com.reelshort.app.session.FileSessionStore
import com.reelshort.app.state.AppScreen
import com.reelshort.app.state.AppStateController
import com.reelshort.app.state.AppUiActions
import com.reelshort.app.ui.MainShell
import com.reelshort.app.ui.components.LoadingDialog
import com.reelshort.app.ui.components.TopErrorToast
import com.reelshort.app.ui.screens.auth.AuthBottomSheet
import com.reelshort.app.ui.theme.ReelShortTheme
import java.io.File
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.rgb(8, 10, 15)),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.rgb(8, 10, 15)),
        )
        setContent {
            val actions = remember { AndroidAppFactory.createActions(applicationContext) }
            ReelShortApp(actions)
        }
    }
}

private object AndroidAppFactory {
    fun createActions(context: Context): AppUiActions {
        val filesDir = context.filesDir
        val sessionStore = FileSessionStore(File(filesDir, "reelshort-session.json"))
        val credentialStore = AndroidCredentialStore.create(context)
        val apiConfig = ApiConfig(BuildConfig.REELSHORT_API_BASE_URL)
        lateinit var repository: AppRepository
        val apiClient = OkHttpReelShortApiClient(
            config = apiConfig,
            tokenProvider = { repository.currentToken },
        )
        repository = AppRepository(apiClient, sessionStore, credentialStore, apiConfig.baseUrl)
        return AppUiActions(AppStateController(repository))
    }
}

@Composable
fun ReelShortApp(actions: AppUiActions) {
    val state by actions.state.collectAsState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(actions) {
        actions.restoreSession()
    }
    LaunchedEffect(state.errorMessage) {
        if (state.errorMessage != null) {
            delay(2_000)
            actions.clearError()
        }
    }

    ReelShortTheme {
        Box(modifier = Modifier.fillMaxSize()) {
            MainShell(
                state = state,
                onScreenSelected = { screen ->
                    scope.launch {
                        when (screen) {
                            AppScreen.HOME -> actions.openHome()
                            AppScreen.SEARCH -> actions.showSearch()
                            AppScreen.ACCOUNT -> actions.openAccount()
                            else -> Unit
                        }
                    }
                },
                onLogout = { scope.launch { actions.logout() } },
                onSearch = { query -> scope.launch { actions.search(query) } },
                onOpenBook = { book -> scope.launch { actions.openBook(book) } },
                onOpenPlayer = { episode -> scope.launch { actions.openPlayer(episode) } },
                onUpdatePlaybackPosition = { position, duration -> actions.updatePlaybackPosition(position, duration) },
                onRefreshPlaybackUrl = { scope.launch { actions.refreshPlaybackUrl() } },
                onAutoReportProgress = { position, duration ->
                    scope.launch { actions.reportProgressSilently(position, duration) }
                },
                onCheckApiHealth = { scope.launch { actions.checkApiHealth() } },
                onShowAuthPrompt = actions::showAuthPrompt,
            )
            AuthBottomSheet(
                visible = state.authPromptVisible,
                state = state,
                onLogin = { username, password, rememberPassword ->
                    scope.launch { actions.login(username, password, rememberPassword) }
                },
                onRegister = { username, password, rememberPassword ->
                    scope.launch { actions.register(username, password, rememberPassword) }
                },
                onDismiss = actions::dismissAuthPrompt,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .zIndex(2f),
            )
            TopErrorToast(
                message = state.errorMessage,
                onDismiss = actions::clearError,
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
