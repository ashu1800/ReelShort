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

private val WatchRewardStages = listOf(25, 50, 75, 100)

internal data class WatchRewardHint(
    val title: String,
    val message: String,
    val actionReady: Boolean,
)

internal fun watchRewardHint(progressPercent: Int, lastReportedProgressPercent: Int): WatchRewardHint {
    val progress = progressPercent.coerceIn(0, 100)
    val reported = lastReportedProgressPercent.coerceIn(0, 100)
    val readyStages = WatchRewardStages.filter { it > reported && it <= progress }
    val nextStage = WatchRewardStages.firstOrNull { it > reported }
        ?: return WatchRewardHint(
            title = "本集奖励已完成",
            message = "25%、50%、75%、100% 阶段都已上报，继续观看不会重复发放。",
            actionReady = false,
        )

    return if (readyStages.isNotEmpty()) {
        val readyStageLabel = readyStages.joinToString("、") { "$it%" }
        WatchRewardHint(
            title = "可上报领取 $readyStageLabel 奖励",
            message = "本次上报可结算 $readyStageLabel 观看阶段，后端会自动跳过已领取阶段。",
            actionReady = true,
        )
    } else {
        WatchRewardHint(
            title = "下一奖励：${nextStage}%",
            message = "继续观看，距离 ${nextStage}% 阶段还差 ${nextStage - progress}%。",
            actionReady = false,
        )
    }
}

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
            if (state.screen == AppScreen.LOGIN) {
                LoginScreen(
                    state = state,
                    onLogin = { username, password -> scope.launch { actions.login(username, password) } },
                    onRegister = { username, password -> scope.launch { actions.register(username, password) } },
                )
            } else {
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
                    onReportProgress = {
                        val playback = state.playback
                        scope.launch { actions.reportProgress(playback.positionSeconds, playback.durationSeconds) }
                    },
                    onCheckApiHealth = { scope.launch { actions.checkApiHealth() } },
                )
            }
            TopErrorToast(
                message = state.errorMessage,
                onDismiss = actions::clearError,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .zIndex(2f),
            )
            LoadingDialog(
                visible = state.isLoading && state.screen != AppScreen.LOGIN,
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
    onReportProgress: () -> Unit,
    onCheckApiHealth: () -> Unit,
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
                    AppScreen.PLAYER -> PlayerScreen(state, onUpdatePlaybackPosition, onRefreshPlaybackUrl, onReportProgress)
                    AppScreen.ACCOUNT -> AccountScreen(
                        records = state.watchHistory,
                        username = state.session?.username.orEmpty(),
                        balance = state.pointAccount?.balance ?: 0,
                        pointRecords = state.pointAccount?.records ?: emptyList(),
                        orders = state.orders,
                        apiBaseUrl = state.apiBaseUrl,
                        apiHealthStatus = state.apiHealthStatus,
                        onCheckApiHealth = onCheckApiHealth,
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
            EpisodeRow(episode, onClick = { onOpenPlayer(episode) })
        }
    }
}

@Composable
private fun PlayerScreen(
    state: AppUiState,
    onUpdatePlaybackPosition: (Int, Int) -> Unit,
    onRefreshPlaybackUrl: () -> Unit,
    onReportProgress: () -> Unit,
) {
    val playback = state.playback
    val book = playback.book ?: state.selectedBook
    val episode = playback.episode ?: state.selectedEpisode
    val videoUrl = playback.videoUrl?.url
    val ready = playback.status == PlaybackStatus.READY && episode != null && videoUrl != null
    val duration = playback.durationSeconds.takeIf { it > 0 } ?: episode?.durationSeconds ?: 0
    val simulatedPosition = if (duration > 0) duration / 4 else 0
    val rewardHint = watchRewardHint(playback.progressPercent, playback.lastReportedProgressPercent)

    LazyColumn(contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            MediaPlayerSurface(
                videoUrl = videoUrl,
                episodeNumber = episode?.number,
                fallbackDurationSeconds = duration,
                onProgress = onUpdatePlaybackPosition,
            )
        }
        item {
            SectionHeader(book?.title ?: "未选择剧集", "时长 ${duration.formatSeconds()} · 进度 ${playback.progressPercent}%")
        }
        item {
            WatchRewardHintPanel(rewardHint)
        }
        item {
            SurfacePanel {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("播放地址", fontWeight = FontWeight.SemiBold)
                    Text(videoUrl ?: "暂无播放地址", maxLines = 2, overflow = TextOverflow.Ellipsis, color = TextSecondary)
                    Text("已上报 ${playback.lastReportedProgressPercent}% · 位置 ${playback.lastReportedPositionSeconds.formatSeconds()}", color = TextSecondary)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(
                            onClick = { onUpdatePlaybackPosition(simulatedPosition, duration) },
                            enabled = ready && duration > 0,
                            border = BorderStroke(1.dp, Divider),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                        ) {
                            Text("同步 25%")
                        }
                        OutlinedButton(
                            onClick = onRefreshPlaybackUrl,
                            enabled = ready,
                            border = BorderStroke(1.dp, Divider),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                        ) {
                            Text("刷新地址")
                        }
                    }
                    PrimaryActionButton(
                        text = "上报当前进度",
                        enabled = ready && playback.durationSeconds > 0 && playback.positionSeconds > 0,
                        onClick = onReportProgress,
                    )
                }
            }
        }
    }
}

@Composable
private fun WatchRewardHintPanel(hint: WatchRewardHint) {
    SurfacePanel {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(hint.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = TextPrimary)
            Text(hint.message, color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
            MetaPill(if (hint.actionReady) "可上报" else "继续观看")
        }
    }
}

@Composable
private fun MediaPlayerSurface(
    videoUrl: String?,
    episodeNumber: Int?,
    fallbackDurationSeconds: Int,
    onProgress: (positionSeconds: Int, durationSeconds: Int) -> Unit,
) {
    val playableUrl = videoUrl.playableMediaUrlOrNull()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(240.dp)
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
                    playWhenReady = false
                    prepare()
                }
            }
            DisposableEffect(player) {
                onDispose { player.release() }
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
                        this.player = player
                    }
                },
                update = { view -> view.player = player },
                onRelease = { view -> view.player = null },
                modifier = Modifier.fillMaxSize(),
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
    username: String,
    balance: Int,
    pointRecords: List<PointRecord>,
    orders: List<RechargeOrderSummary>,
    apiBaseUrl: String,
    apiHealthStatus: ApiHealthStatus?,
    onCheckApiHealth: () -> Unit,
    onLogout: () -> Unit,
) {
    val diagnostics = apiDiagnosticsText(apiHealthStatus)
    var showDiagnostics by remember { mutableStateOf(false) }

    LazyColumn(contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            AccountHero(username = username, balance = balance)
        }
        item {
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
        item {
            AccountMenuGroup {
                AccountMenuRow(
                    icon = Icons.AutoMirrored.Rounded.Logout,
                    title = "退出登录",
                    subtitle = "退出当前账号并返回登录页",
                    titleColor = DangerText,
                    onClick = onLogout,
                )
            }
        }
        if (records.isNotEmpty()) {
            item { SectionHeader("最近观看", "继续上次的短剧进度") }
            items(records.take(3)) { record -> WatchRecordRow(record) }
        }
    }
}

@Composable
private fun AccountHero(username: String, balance: Int) {
    val displayName = username.ifBlank { "ReelShort 用户" }
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
                Text("已登录 · ReelShort", color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    MetaPill("积分 $balance")
                    Text("持续观看可获得奖励", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
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
private fun EpisodeRow(episode: EpisodeSummary, onClick: () -> Unit) {
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
            Text(
                episodeNumberLabel(episode.number),
                modifier = Modifier.weight(1f),
                color = TextPrimary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Black,
            )
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
