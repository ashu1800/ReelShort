package com.reelshort.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.MonetizationOn
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.media3.common.MediaItem
import androidx.media3.common.C
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.reelshort.app.config.ApiConfig
import com.reelshort.app.data.ApiHealthStatus
import com.reelshort.app.data.AppRepository
import com.reelshort.app.data.BookSummary
import com.reelshort.app.data.EpisodeSummary
import com.reelshort.app.data.PointRecord
import com.reelshort.app.data.RechargeOrderSummary
import com.reelshort.app.data.WatchRecord
import com.reelshort.app.network.OkHttpReelShortApiClient
import com.reelshort.app.session.FileSessionStore
import com.reelshort.app.state.AppScreen
import com.reelshort.app.state.AppStateController
import com.reelshort.app.state.AppUiActions
import com.reelshort.app.state.AppUiState
import com.reelshort.app.state.PlaybackStatus
import com.reelshort.app.state.nextUnreportedRewardStage
import java.io.File
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val AppBackgroundColor = Color(0xFF080A0F)
private val Panel = Color(0xFF11151E)
private val PanelSoft = Color(0xFF171C27)
private val Divider = Color(0xFF252C3A)
private val PrimaryGold = Color(0xFFFFC46B)
private val PrimaryGoldDark = Color(0xFFB9802C)
private val TextPrimary = Color(0xFFF8FAFC)
private val TextSecondary = Color(0xFFA7B0C0)
private val DangerSurface = Color(0xFF37191D)
private val DangerText = Color(0xFFFFB4BC)

internal fun String?.coverUrlOrNull(): String? = this?.trim()?.takeIf { it.isNotEmpty() }

internal fun String?.playableMediaUrlOrNull(): String? =
    this
        ?.trim()
        ?.takeIf { it.startsWith("https://", ignoreCase = true) || it.startsWith("http://", ignoreCase = true) }

internal fun mediaPositionSeconds(positionMs: Long): Int =
    (maxOf(positionMs, 0L) / 1_000L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()

internal fun mediaDurationSeconds(durationMs: Long, fallbackDurationSeconds: Int): Int =
    if (durationMs == C.TIME_UNSET) {
        maxOf(fallbackDurationSeconds, 0)
    } else {
        (maxOf(durationMs, 0L) / 1_000L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }

private val RewardBadgeStages = listOf(25, 50, 75, 100)

internal enum class RewardBadgeVisualState {
    WAITING,
    READY,
    REPORTING,
    COMPLETED,
    ERROR,
}

internal data class RewardBadgeState(
    val displayText: String,
    val ringProgress: Float,
    val visualState: RewardBadgeVisualState,
)

internal fun rewardBadgeState(
    progressPercent: Int,
    lastReportedProgressPercent: Int,
    isReporting: Boolean,
    hasError: Boolean,
): RewardBadgeState {
    val progress = progressPercent.coerceIn(0, 100)
    val reported = lastReportedProgressPercent.coerceIn(0, 100)
    val targetStage = RewardBadgeStages.firstOrNull { it > reported }
        ?: return RewardBadgeState(
            displayText = "✓",
            ringProgress = 1f,
            visualState = RewardBadgeVisualState.COMPLETED,
        )
    val visualState = when {
        hasError -> RewardBadgeVisualState.ERROR
        isReporting -> RewardBadgeVisualState.REPORTING
        progress >= targetStage -> RewardBadgeVisualState.READY
        else -> RewardBadgeVisualState.WAITING
    }
    return RewardBadgeState(
        displayText = targetStage.toString(),
        ringProgress = (progress.toFloat() / targetStage.toFloat()).coerceIn(0f, 1f),
        visualState = visualState,
    )
}

internal fun playerSecondaryActionLabels(): List<String> = listOf("刷新地址")

internal fun guestAccountEntryLabels(): List<String> = listOf("登录", "注册")

internal fun authPromptTitle(hasPendingPlayback: Boolean): String =
    if (hasPendingPlayback) "登录后继续播放" else "登录后查看账户"

internal fun episodeSubtitle(episodeDescription: String, bookDescription: String): String =
    episodeDescription.trim().ifBlank { bookDescription.trim() }

internal fun playerSurfaceAspectRatio(): Float = 9f / 16f

internal fun playerStartsAutomatically(): Boolean = true

internal data class ContentEmptyState(
    val title: String,
    val message: String,
    val actionLabel: String? = null,
)

internal data class ApiDiagnosticsText(
    val label: String,
    val message: String,
    val isUp: Boolean,
)

internal fun apiDiagnosticsText(status: ApiHealthStatus?): ApiDiagnosticsText {
    if (status == null) {
        return ApiDiagnosticsText(
            label = "未检测",
            message = "点击刷新，检查雷电模拟器是否能访问本机 Spring Boot。",
            isUp = false,
        )
    }
    return if (status.status.equals("UP", ignoreCase = true)) {
        val service = status.service?.takeIf { it.isNotBlank() } ?: "Spring Boot"
        ApiDiagnosticsText(
            label = "已连接",
            message = "后端 $service 正常响应。",
            isUp = true,
        )
    } else {
        ApiDiagnosticsText(
            label = "连接异常",
            message = "后端健康状态为 ${status.status}，请确认 Spring Boot 是否启动。",
            isUp = false,
        )
    }
}

internal fun homeEmptyState(): ContentEmptyState =
    ContentEmptyState(
        title = "今日暂无推荐",
        message = "内容源暂时没有返回推荐短剧，可以先搜索片名或关键词。",
        actionLabel = "去搜索",
    )

internal fun searchEmptyState(query: String, resultCount: Int): ContentEmptyState? {
    if (resultCount > 0) {
        return null
    }
    val normalizedQuery = query.trim()
    return if (normalizedQuery.isEmpty()) {
        ContentEmptyState(
            title = "发现短剧",
            message = "输入剧名、角色或关键词，快速找到想看的短剧。",
        )
    } else {
        ContentEmptyState(
            title = "没有找到相关短剧",
            message = "没有匹配“$normalizedQuery”的内容，换个关键词再试。",
            actionLabel = "重新搜索",
        )
    }
}

internal fun detailEmptyState(book: BookSummary?, episodeCount: Int): ContentEmptyState? {
    if (book != null && episodeCount > 0) {
        return null
    }
    return if (book == null) {
        ContentEmptyState(
            title = "先选择一部短剧",
            message = "从首页推荐或搜索结果进入详情后，这里会展示分集列表。",
            actionLabel = "返回首页",
        )
    } else {
        ContentEmptyState(
            title = "分集暂不可用",
            message = "“${book.title}”暂时没有可播放分集，可以稍后刷新或选择其他短剧。",
            actionLabel = "换一部",
        )
    }
}

internal fun episodeNumberLabel(number: Int): String =
    "第 ${number.coerceAtLeast(0).toString().padStart(2, '0')} 集"

internal fun episodeTitle(episode: EpisodeSummary): String =
    episode.title.trim().takeIf { it.isNotBlank() }
        ?.let { "${episodeNumberLabel(episode.number)} · $it" }
        ?: episodeNumberLabel(episode.number)

internal fun episodeRowActionLabel(): String = "播放"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.rgb(8, 10, 15)),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.rgb(8, 10, 15)),
        )
        setContent {
            val actions = remember { AndroidAppFactory.createActions(filesDir) }
            ReelShortApp(actions)
        }
    }
}

private object AndroidAppFactory {
    fun createActions(filesDir: File): AppUiActions {
        val sessionStore = FileSessionStore(File(filesDir, "reelshort-session.json"))
        val apiConfig = ApiConfig(BuildConfig.REELSHORT_API_BASE_URL)
        lateinit var repository: AppRepository
        val apiClient = OkHttpReelShortApiClient(
            config = apiConfig,
            tokenProvider = { repository.currentToken },
        )
        repository = AppRepository(apiClient, sessionStore, apiConfig.baseUrl)
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
                onLogin = { username, password -> scope.launch { actions.login(username, password) } },
                onRegister = { username, password -> scope.launch { actions.register(username, password) } },
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

@Composable
private fun ReelShortTheme(content: @Composable () -> Unit) {
    val colors = darkColorScheme(
        primary = PrimaryGold,
        onPrimary = Color(0xFF281600),
        secondary = Color(0xFF95D5FF),
        background = AppBackgroundColor,
        onBackground = TextPrimary,
        surface = Panel,
        onSurface = TextPrimary,
        surfaceVariant = PanelSoft,
        onSurfaceVariant = TextSecondary,
        error = DangerText,
    )
    MaterialTheme(colorScheme = colors, content = content)
}

@Composable
private fun AppBackground(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF15110F), AppBackgroundColor, Color(0xFF06070B)),
                ),
            ),
    ) {
        CompositionLocalProvider(LocalContentColor provides TextPrimary) {
            content()
        }
    }
}

@Composable
private fun LoginScreen(
    state: AppUiState,
    onLogin: (String, String) -> Unit,
    onRegister: (String, String) -> Unit,
) {
    var username by remember { mutableStateOf(state.session?.username ?: "demo") }
    var password by remember { mutableStateOf("") }

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
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text("账号登录", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text("继续浏览推荐短剧和观看记录", color = TextSecondary)
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
                        PrimaryActionButton(
                            text = if (state.isLoading) "登录中" else "登录",
                            enabled = !state.isLoading && username.isNotBlank() && password.isNotBlank(),
                            onClick = { onLogin(username, password) },
                        )
                        TextButton(
                            onClick = { onRegister(username, password) },
                            enabled = !state.isLoading && username.isNotBlank() && password.isNotBlank(),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("注册新账号", color = PrimaryGold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BrandLockup() {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("ReelShort", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Black)
        Text("聚合短剧播放平台", style = MaterialTheme.typography.titleMedium, color = TextSecondary)
        AccentLine()
    }
}

@Composable
private fun LoginTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    enabled: Boolean,
    isPassword: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        enabled = enabled,
        visualTransformation = if (isPassword) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = PrimaryGold,
            unfocusedBorderColor = Divider,
            focusedLabelColor = PrimaryGold,
            unfocusedLabelColor = TextSecondary,
            focusedTextColor = TextPrimary,
            unfocusedTextColor = TextPrimary,
            cursorColor = PrimaryGold,
        ),
    )
}

@Composable
private fun MainShell(
    state: AppUiState,
    onScreenSelected: (AppScreen) -> Unit,
    onLogout: () -> Unit,
    onSearch: (String) -> Unit,
    onOpenBook: (BookSummary) -> Unit,
    onOpenPlayer: (EpisodeSummary) -> Unit,
    onUpdatePlaybackPosition: (Int, Int) -> Unit,
    onRefreshPlaybackUrl: () -> Unit,
    onAutoReportProgress: (Int, Int) -> Unit,
    onCheckApiHealth: () -> Unit,
    onShowAuthPrompt: () -> Unit,
) {
    AppBackground {
        Scaffold(
            containerColor = Color.Transparent,
            bottomBar = {
                NavigationBar(containerColor = Color(0xF20B0E14), tonalElevation = 0.dp) {
                    primaryTabs.forEach { screen ->
                        NavigationBarItem(
                            selected = state.screen == screen,
                            onClick = { onScreenSelected(screen) },
                            icon = { NavigationIcon(screen) },
                            label = { Text(screen.navigationLabel, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Color(0xFF1A1203),
                                selectedTextColor = PrimaryGold,
                                indicatorColor = PrimaryGold,
                                unselectedIconColor = TextSecondary,
                                unselectedTextColor = TextSecondary,
                            ),
                        )
                    }
                }
            },
        ) { padding ->
            Column(
                modifier = Modifier
                    .padding(padding)
                    .statusBarsPadding(),
            ) {
                when (state.screen) {
                    AppScreen.LOGIN -> Unit
                    AppScreen.HOME -> HomeScreen(state.homeShelf, onOpenBook)
                    AppScreen.SEARCH -> SearchScreen(state, onSearch, onOpenBook)
                    AppScreen.DETAIL -> DetailScreen(state.selectedBook, state.episodes, onOpenPlayer)
                    AppScreen.PLAYER -> PlayerScreen(state, onUpdatePlaybackPosition, onRefreshPlaybackUrl, onAutoReportProgress)
                    AppScreen.ACCOUNT -> AccountScreen(
                        records = state.watchHistory,
                        isLoggedIn = state.session != null,
                        username = state.session?.username.orEmpty(),
                        balance = state.pointAccount?.balance ?: 0,
                        pointRecords = state.pointAccount?.records ?: emptyList(),
                        orders = state.orders,
                        apiBaseUrl = state.apiBaseUrl,
                        apiHealthStatus = state.apiHealthStatus,
                        onCheckApiHealth = onCheckApiHealth,
                        onShowAuthPrompt = onShowAuthPrompt,
                        onLogout = onLogout,
                    )
                }
            }
        }
    }
}

@Composable
private fun NavigationIcon(screen: AppScreen) {
    val imageVector = when (screen) {
        AppScreen.HOME -> Icons.Rounded.Home
        AppScreen.SEARCH -> Icons.Rounded.Search
        AppScreen.ACCOUNT -> Icons.Rounded.AccountCircle
        else -> Icons.Rounded.Home
    }
    Icon(imageVector = imageVector, contentDescription = screen.navigationLabel)
}

@Composable
private fun LoadingDialog(visible: Boolean, modifier: Modifier = Modifier) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier,
    ) {
        Surface(
            color = Color(0xF211151E),
            contentColor = TextPrimary,
            border = BorderStroke(1.dp, Divider),
            shape = RoundedCornerShape(22.dp),
        ) {
            LoadingContent(Modifier.padding(horizontal = 22.dp, vertical = 18.dp))
        }
    }
}

@Composable
private fun AuthBottomSheet(
    visible: Boolean,
    state: AppUiState,
    onLogin: (String, String) -> Unit,
    onRegister: (String, String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically { it } + fadeIn(),
        exit = slideOutVertically { it } + fadeOut(),
        modifier = modifier,
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Panel,
            contentColor = TextPrimary,
            border = BorderStroke(1.dp, Divider),
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        ) {
            AuthFormContent(
                state = state,
                title = authPromptTitle(state.pendingPlaybackEpisode != null),
                subtitle = if (state.pendingPlaybackEpisode != null) {
                    "播放、积分和账户数据需要登录账号。"
                } else {
                    "登录后可以查看积分、观看记录和订单。"
                },
                onLogin = onLogin,
                onRegister = onRegister,
                onDismiss = onDismiss,
                modifier = Modifier.padding(horizontal = 22.dp, vertical = 20.dp),
            )
        }
    }
}

@Composable
private fun AuthFormContent(
    state: AppUiState,
    title: String,
    subtitle: String,
    onLogin: (String, String) -> Unit,
    onRegister: (String, String) -> Unit,
    onDismiss: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    var username by remember { mutableStateOf(state.session?.username ?: "demo") }
    var password by remember { mutableStateOf("") }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
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
        PrimaryActionButton(
            text = if (state.isLoading) "登录中" else "登录",
            enabled = !state.isLoading && username.isNotBlank() && password.isNotBlank(),
            onClick = { onLogin(username, password) },
        )
        OutlinedButton(
            onClick = { onRegister(username, password) },
            enabled = !state.isLoading && username.isNotBlank() && password.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
            border = BorderStroke(1.dp, Divider),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = PrimaryGold),
        ) {
            Text("注册")
        }
    }
}

@Composable
private fun LoadingContent(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 3.dp, color = PrimaryGold)
        Text("正在加载", color = TextPrimary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun TopErrorToast(message: String?, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    AnimatedVisibility(
        visible = message != null,
        enter = slideInVertically { -it } + fadeIn(),
        exit = slideOutVertically { -it } + fadeOut(),
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier
                .statusBarsPadding()
                .padding(horizontal = 18.dp, vertical = 12.dp)
                .widthIn(max = 420.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(DangerSurface)
                .border(1.dp, Color(0xFF663038), RoundedCornerShape(14.dp))
                .clickable(onClick = onDismiss)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = message.orEmpty(),
                color = DangerText,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun HomeScreen(books: List<BookSummary>, onOpenBook: (BookSummary) -> Unit) {
    LazyColumn(contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            SectionHeader("今日推荐", "为你整理 ${books.size} 部短剧")
        }
        if (books.isEmpty()) {
            item { EmptyState(homeEmptyState()) }
        }
        items(books) { book ->
            BookRow(book = book, onClick = { onOpenBook(book) })
        }
    }
}

@Composable
private fun SearchScreen(
    state: AppUiState,
    onSearch: (String) -> Unit,
    onOpenBook: (BookSummary) -> Unit,
) {
    var query by remember(state.searchQuery) { mutableStateOf(state.searchQuery) }

    LazyColumn(contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            SectionHeader("搜索短剧", "输入标题或关键词")
        }
        item {
            SurfacePanel(contentPadding = PaddingValues(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        label = { Text("搜索") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PrimaryGold,
                            unfocusedBorderColor = Divider,
                            focusedLabelColor = PrimaryGold,
                            unfocusedLabelColor = TextSecondary,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            cursorColor = PrimaryGold,
                        ),
                    )
                    Spacer(Modifier.width(10.dp))
                    Button(
                        onClick = { onSearch(query) },
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryGold, contentColor = Color(0xFF221400)),
                    ) {
                        Text("搜索")
                    }
                }
            }
        }
        val emptyState = searchEmptyState(state.searchQuery, state.searchResults.size)
        if (emptyState != null) {
            item { EmptyState(emptyState) }
        }
        items(state.searchResults) { book ->
            BookRow(book = book, onClick = { onOpenBook(book) })
        }
    }
}

@Composable
private fun DetailScreen(
    book: BookSummary?,
    episodes: List<EpisodeSummary>,
    onOpenPlayer: (EpisodeSummary) -> Unit,
) {
    val emptyState = detailEmptyState(book, episodes.size)
    if (book == null) {
        EmptyState(emptyState ?: return)
        return
    }
    LazyColumn(contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            BookHero(book)
        }
        item {
            SectionHeader("剧集列表", "共 ${episodes.size} 集")
        }
        if (emptyState != null) {
            item { EmptyState(emptyState) }
        }
        items(episodes) { episode ->
            EpisodeRow(episode, book.description, onClick = { onOpenPlayer(episode) })
        }
    }
}

@Composable
private fun PlayerScreen(
    state: AppUiState,
    onUpdatePlaybackPosition: (Int, Int) -> Unit,
    onRefreshPlaybackUrl: () -> Unit,
    onAutoReportProgress: (Int, Int) -> Unit,
) {
    val playback = state.playback
    val book = playback.book ?: state.selectedBook
    val episode = playback.episode ?: state.selectedEpisode
    val videoUrl = playback.videoUrl?.url
    val ready = playback.status == PlaybackStatus.READY && episode != null && videoUrl != null
    val duration = playback.durationSeconds.takeIf { it > 0 } ?: episode?.durationSeconds ?: 0
    val badgeState = rewardBadgeState(
        progressPercent = playback.progressPercent,
        lastReportedProgressPercent = playback.lastReportedProgressPercent,
        isReporting = playback.isRewardReporting,
        hasError = playback.rewardReportError,
    )

    LaunchedEffect(
        playback.positionSeconds,
        playback.durationSeconds,
        playback.progressPercent,
        playback.lastReportedProgressPercent,
    ) {
        if (
            playback.status == PlaybackStatus.READY &&
            !playback.isRewardReporting &&
            playback.positionSeconds > 0 &&
            playback.durationSeconds > 0 &&
            nextUnreportedRewardStage(playback.progressPercent, playback.lastReportedProgressPercent) != null
        ) {
            onAutoReportProgress(playback.positionSeconds, playback.durationSeconds)
        }
    }

    LazyColumn(contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            MediaPlayerSurface(
                videoUrl = videoUrl,
                episodeNumber = episode?.number,
                fallbackDurationSeconds = duration,
                initialPositionSeconds = playback.positionSeconds,
                rewardBadgeState = badgeState,
                onProgress = onUpdatePlaybackPosition,
            )
        }
        item {
            SectionHeader(
                book?.title ?: "未选择剧集",
                "${episode?.let { episodeTitle(it) } ?: "分集"} · 进度 ${playback.progressPercent}%",
            )
        }
        item {
            SurfacePanel {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(episode?.let { episodeTitle(it) } ?: "当前分集", fontWeight = FontWeight.SemiBold)
                    val description = episode?.let { episodeSubtitle(it.description, book?.description.orEmpty()) }.orEmpty()
                    if (description.isNotBlank()) {
                        Text(description, color = TextSecondary, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    }
                    Text("已领取 ${playback.lastReportedProgressPercent}%", color = TextSecondary)
                    if (playback.rewardReportError) {
                        Text("积分同步失败，继续播放时会自动重试。", color = DangerText)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        playerSecondaryActionLabels().forEach { label ->
                            OutlinedButton(
                                onClick = onRefreshPlaybackUrl,
                                enabled = ready,
                                border = BorderStroke(1.dp, Divider),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                            ) {
                                Text(label)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MediaPlayerSurface(
    videoUrl: String?,
    episodeNumber: Int?,
    fallbackDurationSeconds: Int,
    initialPositionSeconds: Int,
    rewardBadgeState: RewardBadgeState,
    onProgress: (positionSeconds: Int, durationSeconds: Int) -> Unit,
) {
    val playableUrl = videoUrl.playableMediaUrlOrNull()

    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 430.dp)
                .aspectRatio(playerSurfaceAspectRatio())
                .clip(RoundedCornerShape(24.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFF2B1E12), Color(0xFF07080C)),
                    ),
                )
                .border(1.dp, Divider, RoundedCornerShape(24.dp)),
            contentAlignment = Alignment.Center,
        ) {
            if (playableUrl == null) {
                PlayerPlaceholder(episodeNumber)
            } else {
            val context = LocalContext.current
            val player = remember(playableUrl) {
                ExoPlayer.Builder(context).build().apply {
                    setMediaItem(MediaItem.fromUri(playableUrl))
                    playWhenReady = playerStartsAutomatically()
                    prepare()
                }
            }
            DisposableEffect(player) {
                onDispose { player.release() }
            }
            LaunchedEffect(player) {
                if (initialPositionSeconds > 0) {
                    player.seekTo(initialPositionSeconds * 1_000L)
                }
            }
            LaunchedEffect(player, fallbackDurationSeconds) {
                while (true) {
                    val durationSeconds = mediaDurationSeconds(player.duration, fallbackDurationSeconds)
                    if (durationSeconds > 0) {
                        onProgress(mediaPositionSeconds(player.currentPosition), durationSeconds)
                    }
                    delay(1_000)
                }
            }
            AndroidView(
                factory = { viewContext ->
                    PlayerView(viewContext).apply {
                        useController = true
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                        this.player = player
                    }
                },
                update = { view ->
                    view.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    view.player = player
                },
                onRelease = { view -> view.player = null },
                modifier = Modifier.fillMaxSize(),
            )
        }
            RewardProgressBadge(
                state = rewardBadgeState,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp),
            )
        }
    }
}

@Composable
private fun RewardProgressBadge(
    state: RewardBadgeState,
    modifier: Modifier = Modifier,
) {
    val ringColor = when (state.visualState) {
        RewardBadgeVisualState.WAITING -> Color(0xFF6E7686)
        RewardBadgeVisualState.READY,
        RewardBadgeVisualState.REPORTING,
        RewardBadgeVisualState.COMPLETED -> PrimaryGold
        RewardBadgeVisualState.ERROR -> DangerText
    }
    val backgroundColor = when (state.visualState) {
        RewardBadgeVisualState.COMPLETED -> Color(0xFFE0A94C)
        else -> Color(0xCC080A0F)
    }
    val textColor = when (state.visualState) {
        RewardBadgeVisualState.COMPLETED -> Color(0xFF241500)
        RewardBadgeVisualState.ERROR -> DangerText
        else -> TextPrimary
    }

    Surface(
        modifier = modifier.size(52.dp),
        color = backgroundColor,
        contentColor = textColor,
        shape = RoundedCornerShape(26.dp),
        border = BorderStroke(1.dp, ringColor.copy(alpha = 0.78f)),
    ) {
        Box(contentAlignment = Alignment.Center) {
            CircularProgressIndicator(
                progress = { state.ringProgress },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(4.dp),
                color = ringColor,
                trackColor = Color(0x33FFFFFF),
                strokeWidth = 3.dp,
            )
            Text(
                text = state.displayText,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Black,
                color = textColor,
                maxLines = 1,
                overflow = TextOverflow.Clip,
            )
        }
    }
}

@Composable
private fun PlayerPlaceholder(episodeNumber: Int?) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("等待播放地址", color = TextPrimary, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text("第 ${episodeNumber ?: 0} 集", color = TextSecondary)
    }
}

@Composable
private fun AccountScreen(
    records: List<WatchRecord>,
    isLoggedIn: Boolean,
    username: String,
    balance: Int,
    pointRecords: List<PointRecord>,
    orders: List<RechargeOrderSummary>,
    apiBaseUrl: String,
    apiHealthStatus: ApiHealthStatus?,
    onCheckApiHealth: () -> Unit,
    onShowAuthPrompt: () -> Unit,
    onLogout: () -> Unit,
) {
    val diagnostics = apiDiagnosticsText(apiHealthStatus)
    var showDiagnostics by remember { mutableStateOf(false) }

    LazyColumn(contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            AccountHero(username = username, balance = balance, isLoggedIn = isLoggedIn)
        }
        if (!isLoggedIn) {
            item {
                AccountMenuGroup {
                    guestAccountEntryLabels().forEachIndexed { index, label ->
                        AccountMenuRow(
                            icon = if (label == "登录") Icons.Rounded.AccountCircle else Icons.Rounded.PlayArrow,
                            title = label,
                            subtitle = if (label == "登录") "登录后可播放短剧并获取积分" else "创建账号保存观看进度",
                            onClick = onShowAuthPrompt,
                        )
                        if (index < guestAccountEntryLabels().lastIndex) {
                            AccountMenuDivider()
                        }
                    }
                }
            }
        }
        if (isLoggedIn) item {
            AccountMenuGroup {
                AccountMenuRow(
                    icon = Icons.Rounded.MonetizationOn,
                    title = "积分余额",
                    subtitle = "$balance 积分",
                    trailing = "$balance",
                    highlight = true,
                )
                AccountMenuDivider()
                AccountMenuRow(
                    icon = Icons.Rounded.History,
                    title = "观看记录",
                    subtitle = "最近 ${records.size} 条",
                    trailing = records.firstOrNull()?.let { "${it.progressPercent}%" }.orEmpty(),
                )
                AccountMenuDivider()
                AccountMenuRow(
                    icon = Icons.AutoMirrored.Rounded.ReceiptLong,
                    title = "积分流水",
                    subtitle = "最近 ${pointRecords.size} 条",
                    trailing = pointRecords.firstOrNull()?.let { if (it.amount > 0) "+${it.amount}" else "${it.amount}" }.orEmpty(),
                )
                AccountMenuDivider()
                AccountMenuRow(
                    icon = Icons.AutoMirrored.Rounded.ReceiptLong,
                    title = "充值订单",
                    subtitle = if (orders.isEmpty()) "商业化预留" else "最近 ${orders.size} 笔",
                    trailing = orders.firstOrNull()?.status.orEmpty(),
                )
            }
        }
        item {
            AccountMenuGroup {
                AccountMenuRow(
                    icon = Icons.Rounded.Settings,
                    title = "开发诊断",
                    subtitle = diagnostics.label,
                    trailing = if (showDiagnostics) "收起" else "查看",
                    onClick = { showDiagnostics = !showDiagnostics },
                )
                AnimatedVisibility(visible = showDiagnostics) {
                    Column(
                        modifier = Modifier.padding(start = 52.dp, end = 16.dp, bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(apiBaseUrl.ifBlank { "API 地址未配置" }, color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                        Text(diagnostics.message, color = TextPrimary, style = MaterialTheme.typography.bodyMedium)
                        OutlinedButton(
                            onClick = onCheckApiHealth,
                            border = BorderStroke(1.dp, Divider),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = PrimaryGold),
                        ) {
                            Text("刷新诊断")
                        }
                    }
                }
            }
        }
        if (isLoggedIn) item {
            AccountMenuGroup {
                AccountMenuRow(
                    icon = Icons.AutoMirrored.Rounded.Logout,
                    title = "退出登录",
                    subtitle = "退出当前账号",
                    titleColor = DangerText,
                    onClick = onLogout,
                )
            }
        }
        if (isLoggedIn && records.isNotEmpty()) {
            item { SectionHeader("最近观看", "继续上次的短剧进度") }
            items(records.take(3)) { record -> WatchRecordRow(record) }
        }
    }
}

@Composable
private fun AccountHero(username: String, balance: Int, isLoggedIn: Boolean) {
    val displayName = if (isLoggedIn) username.ifBlank { "ReelShort 用户" } else "游客"
    SurfacePanel {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(68.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Brush.verticalGradient(listOf(PrimaryGold, PrimaryGoldDark)))
                    .border(1.dp, Color(0x55FFFFFF), RoundedCornerShape(18.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    displayName.take(1).uppercase(),
                    color = Color(0xFF221400),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Black,
                )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(displayName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, color = TextPrimary)
                Text(
                    if (isLoggedIn) "已登录 · ReelShort" else "登录后可保存观看进度和积分",
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    MetaPill(if (isLoggedIn) "积分 $balance" else "未登录")
                    Text(
                        if (isLoggedIn) "持续观看可获得奖励" else "可先浏览首页和搜索",
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun AccountMenuGroup(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Panel.copy(alpha = 0.92f),
        contentColor = TextPrimary,
        border = BorderStroke(1.dp, Divider),
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(content = content)
    }
}

@Composable
private fun AccountMenuRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    trailing: String = "",
    highlight: Boolean = false,
    titleColor: Color = TextPrimary,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick == null) Modifier else Modifier.clickable(onClick = onClick))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Surface(
            color = if (highlight) Color(0x26FFC46B) else Color(0x1FFFFFFF),
            shape = RoundedCornerShape(14.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.padding(10.dp).size(22.dp),
                tint = if (titleColor == DangerText) DangerText else PrimaryGold,
            )
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, color = titleColor, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, color = TextSecondary, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (trailing.isNotBlank()) {
            Text(
                trailing,
                color = if (highlight) PrimaryGold else TextSecondary,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (highlight) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun AccountMenuDivider() {
    Box(
        modifier = Modifier
            .padding(start = 68.dp)
            .fillMaxWidth()
            .height(1.dp)
            .background(Divider),
    )
}

@Composable
private fun BookHero(book: BookSummary) {
    SurfacePanel {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            PosterBlock(book.title, book.coverUrl, Modifier.size(width = 92.dp, height = 124.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(book.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(book.description.ifBlank { "${book.chapterCount} 集短剧" }, color = TextSecondary, maxLines = 3, overflow = TextOverflow.Ellipsis)
                MetaPill("${book.chapterCount} 集")
            }
        }
    }
}

@Composable
private fun EpisodeRow(episode: EpisodeSummary, bookDescription: String, onClick: () -> Unit) {
    val subtitle = episodeSubtitle(episode.description, bookDescription)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        color = Panel,
        border = BorderStroke(1.dp, Divider),
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Surface(
                color = Color(0x1AFFC46B),
                contentColor = PrimaryGold,
                border = BorderStroke(1.dp, Color(0x44FFC46B)),
                shape = RoundedCornerShape(14.dp),
            ) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.padding(8.dp).size(20.dp))
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    episodeTitle(episode),
                    color = TextPrimary,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle.isNotBlank()) {
                    Text(
                        subtitle,
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Text(episodeRowActionLabel(), color = PrimaryGold, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
            Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun WatchRecordRow(record: WatchRecord) {
    ListRow(
        title = record.bookTitle,
        subtitle = "第 ${record.episode} 集",
        trailing = "${record.progressPercent}%",
    )
}

@Composable
private fun PointRecordRow(record: PointRecord) {
    ListRow(
        title = record.reason ?: "积分变动",
        subtitle = "积分流水",
        trailing = if (record.amount > 0) "+${record.amount}" else "${record.amount}",
        highlight = record.amount > 0,
    )
}

@Composable
private fun OrderRow(order: RechargeOrderSummary) {
    ListRow(
        title = order.orderNo,
        subtitle = "¥${order.amountCents / 100}.${(order.amountCents % 100).toString().padStart(2, '0')} · ${order.pointAmount} 积分",
        trailing = order.status,
    )
}

@Composable
private fun ListRow(title: String, subtitle: String, trailing: String, highlight: Boolean = false) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Panel,
        border = BorderStroke(1.dp, Divider),
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(subtitle, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(trailing, color = if (highlight) PrimaryGold else TextSecondary, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun BookRow(book: BookSummary, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = Panel),
        border = BorderStroke(1.dp, Divider),
        shape = RoundedCornerShape(22.dp),
    ) {
        Row(modifier = Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            PosterBlock(book.title, book.coverUrl, Modifier.size(width = 76.dp, height = 104.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text(book.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(book.description.ifBlank { "${book.chapterCount} 集短剧" }, color = TextSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    MetaPill("${book.chapterCount} 集")
                    Text("查看", color = PrimaryGold, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun PosterBlock(title: String, coverUrl: String?, modifier: Modifier = Modifier) {
    val normalizedCoverUrl = coverUrl.coverUrlOrNull()
    var showFallback by remember(normalizedCoverUrl) { mutableStateOf(true) }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFFFFD38B), PrimaryGoldDark, Color(0xFF24130A)),
                ),
            )
            .border(1.dp, Color(0x55FFFFFF), RoundedCornerShape(18.dp)),
        contentAlignment = Alignment.BottomStart,
    ) {
        if (normalizedCoverUrl != null) {
            AsyncImage(
                model = normalizedCoverUrl,
                contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
                onLoading = { showFallback = true },
                onError = { showFallback = true },
                onSuccess = { showFallback = false },
            )
        }
        if (showFallback) {
            Text(
                title.posterInitials(),
                modifier = Modifier.padding(10.dp),
                color = Color(0xFF1C1004),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Black,
            )
        }
    }
}

@Composable
private fun SectionHeader(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black, color = TextPrimary)
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
    }
}

@Composable
private fun EmptyState(state: ContentEmptyState) {
    SurfacePanel {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 112.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.Start,
        ) {
            Text(
                state.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                state.message,
                color = TextSecondary,
                style = MaterialTheme.typography.bodyMedium,
            )
            if (state.actionLabel != null) {
                Spacer(Modifier.height(12.dp))
                MetaPill(state.actionLabel)
            }
        }
    }
}

@Composable
private fun PrimaryActionButton(text: String, enabled: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
        enabled = enabled,
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = PrimaryGold, contentColor = Color(0xFF221400), disabledContainerColor = Color(0xFF3A3329), disabledContentColor = Color(0xFF827667)),
    ) {
        Text(text, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SurfacePanel(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(18.dp),
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = Panel.copy(alpha = 0.92f),
        contentColor = TextPrimary,
        border = BorderStroke(1.dp, Divider),
        shape = RoundedCornerShape(24.dp),
    ) {
        Box(modifier = Modifier.padding(contentPadding)) {
            content()
        }
    }
}

@Composable
private fun AccentLine() {
    Box(
        modifier = Modifier
            .width(72.dp)
            .height(4.dp)
            .clip(RoundedCornerShape(100.dp))
            .background(PrimaryGold),
    )
}

@Composable
private fun MetaPill(text: String) {
    Surface(
        color = Color(0x1AFFC46B),
        border = BorderStroke(1.dp, Color(0x44FFC46B)),
        shape = RoundedCornerShape(999.dp),
    ) {
        Text(text, modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp), color = PrimaryGold, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
    }
}

internal val primaryTabs = listOf(AppScreen.HOME, AppScreen.SEARCH, AppScreen.ACCOUNT)

internal val AppScreen.usesGlobalTopBar: Boolean
    get() = false

internal enum class LoadingFeedbackMode {
    CENTER_DIALOG,
}

internal fun loadingFeedbackMode(): LoadingFeedbackMode = LoadingFeedbackMode.CENTER_DIALOG

internal enum class TabRefreshMode {
    CACHE_FIRST_BACKGROUND_REFRESH,
    LOCAL_SWITCH,
}

internal fun primaryTabRefreshModes(): Map<AppScreen, TabRefreshMode> =
    mapOf(
        AppScreen.HOME to TabRefreshMode.CACHE_FIRST_BACKGROUND_REFRESH,
        AppScreen.SEARCH to TabRefreshMode.LOCAL_SWITCH,
        AppScreen.ACCOUNT to TabRefreshMode.CACHE_FIRST_BACKGROUND_REFRESH,
    )

internal fun accountEntryLabels(): List<String> =
    listOf("积分余额", "观看记录", "积分流水", "充值订单", "开发诊断", "退出登录")

internal val AppScreen.navigationLabel: String
    get() = when (this) {
        AppScreen.HOME -> "首页"
        AppScreen.SEARCH -> "搜索"
        AppScreen.ACCOUNT -> "账户"
        AppScreen.LOGIN -> "登录"
        AppScreen.DETAIL -> "详情"
        AppScreen.PLAYER -> "播放"
    }

private fun String.posterInitials(): String =
    trim()
        .split(Regex("\\s+"))
        .filter { it.isNotBlank() }
        .take(2)
        .joinToString("") { it.first().uppercaseChar().toString() }
        .ifBlank { "RS" }

private fun Int.formatSeconds(): String {
    val safeValue = coerceAtLeast(0)
    val minutes = safeValue / 60
    val seconds = safeValue % 60
    return "$minutes:${seconds.toString().padStart(2, '0')}"
}
