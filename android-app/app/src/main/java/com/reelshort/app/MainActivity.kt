package com.reelshort.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Search
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
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.reelshort.app.config.ApiConfig
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
        lateinit var repository: AppRepository
        val apiClient = OkHttpReelShortApiClient(
            config = ApiConfig(BuildConfig.REELSHORT_API_BASE_URL),
            tokenProvider = { repository.currentToken },
        )
        repository = AppRepository(apiClient, sessionStore)
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

    ReelShortTheme {
        if (state.screen == AppScreen.LOGIN) {
            LoginScreen(
                state = state,
                onLogin = { username, password -> scope.launch { actions.login(username, password) } },
                onRegister = { username, password -> scope.launch { actions.register(username, password) } },
                onClearError = actions::clearError,
            )
        } else {
            MainShell(
                state = state,
                onScreenSelected = { screen ->
                    scope.launch {
                        when (screen) {
                            AppScreen.HOME -> actions.refreshHome()
                            AppScreen.SEARCH -> actions.showSearch()
                            AppScreen.ACCOUNT -> actions.loadAccount()
                            else -> Unit
                        }
                    }
                },
                onLogout = { scope.launch { actions.logout() } },
                onClearError = actions::clearError,
                onSearch = { query -> scope.launch { actions.search(query) } },
                onOpenBook = { book -> scope.launch { actions.openBook(book) } },
                onOpenPlayer = { episode -> scope.launch { actions.openPlayer(episode) } },
                onUpdatePlaybackPosition = { position, duration -> actions.updatePlaybackPosition(position, duration) },
                onRefreshPlaybackUrl = { scope.launch { actions.refreshPlaybackUrl() } },
                onReportProgress = {
                    val playback = state.playback
                    scope.launch { actions.reportProgress(playback.positionSeconds, playback.durationSeconds) }
                },
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
    onClearError: () -> Unit,
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
                        ErrorBanner(state.errorMessage, onClearError)
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainShell(
    state: AppUiState,
    onScreenSelected: (AppScreen) -> Unit,
    onLogout: () -> Unit,
    onClearError: () -> Unit,
    onSearch: (String) -> Unit,
    onOpenBook: (BookSummary) -> Unit,
    onOpenPlayer: (EpisodeSummary) -> Unit,
    onUpdatePlaybackPosition: (Int, Int) -> Unit,
    onRefreshPlaybackUrl: () -> Unit,
    onReportProgress: () -> Unit,
) {
    AppBackground {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(state.screen.title, fontWeight = FontWeight.Bold)
                            Text("ReelShort", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                        }
                    },
                    actions = {
                        OutlinedButton(
                            onClick = onLogout,
                            contentPadding = PaddingValues(horizontal = 14.dp),
                            border = BorderStroke(1.dp, Divider),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary),
                        ) {
                            Text("退出")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        titleContentColor = TextPrimary,
                    ),
                )
            },
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
            Column(modifier = Modifier.padding(padding)) {
                if (state.isLoading) {
                    LoadingStrip()
                }
                ErrorBanner(state.errorMessage, onClearError)
                when (state.screen) {
                    AppScreen.LOGIN -> Unit
                    AppScreen.HOME -> HomeScreen(state.homeShelf, onOpenBook)
                    AppScreen.SEARCH -> SearchScreen(state, onSearch, onOpenBook)
                    AppScreen.DETAIL -> DetailScreen(state.selectedBook, state.episodes, onOpenPlayer)
                    AppScreen.PLAYER -> PlayerScreen(state, onUpdatePlaybackPosition, onRefreshPlaybackUrl, onReportProgress)
                    AppScreen.ACCOUNT -> AccountScreen(state.watchHistory, state.pointAccount?.balance ?: 0, state.pointAccount?.records ?: emptyList(), state.orders)
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
private fun LoadingStrip() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0x33252C3A))
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = PrimaryGold)
        Spacer(Modifier.width(10.dp))
        Text("正在加载", color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ErrorBanner(message: String?, onClearError: () -> Unit) {
    if (message == null) {
        return
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(DangerSurface)
            .border(1.dp, Color(0xFF663038), RoundedCornerShape(14.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(message, modifier = Modifier.weight(1f), color = DangerText, maxLines = 3, overflow = TextOverflow.Ellipsis)
        TextButton(onClick = onClearError) {
            Text("关闭", color = DangerText)
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
            item { EmptyState("暂无推荐内容") }
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
        if (state.searchResults.isEmpty()) {
            item { EmptyState("暂无搜索结果") }
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
    if (book == null) {
        EmptyState("请选择剧集")
        return
    }
    LazyColumn(contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            BookHero(book)
        }
        item {
            SectionHeader("剧集列表", "共 ${episodes.size} 集")
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

    LazyColumn(contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
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
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("HLS 播放器待接入", color = TextPrimary, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("第 ${episode?.number ?: 0} 集", color = TextSecondary)
                }
            }
        }
        item {
            SectionHeader(book?.title ?: "未选择剧集", "时长 ${duration.formatSeconds()} · 进度 ${playback.progressPercent}%")
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
                            Text("模拟 25%")
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
private fun AccountScreen(
    records: List<WatchRecord>,
    balance: Int,
    pointRecords: List<PointRecord>,
    orders: List<RechargeOrderSummary>,
) {
    LazyColumn(contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            SurfacePanel {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("积分余额", color = TextSecondary)
                    Text("$balance", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Black, color = PrimaryGold)
                }
            }
        }
        item { SectionHeader("观看记录", "最近 ${records.size} 条") }
        if (records.isEmpty()) {
            item { EmptyState("暂无观看记录") }
        }
        items(records) { record -> WatchRecordRow(record) }
        item { SectionHeader("积分流水", "最近 ${pointRecords.size} 条") }
        items(pointRecords) { record -> PointRecordRow(record) }
        item { SectionHeader("充值订单", "商业化预留") }
        items(orders) { order -> OrderRow(order) }
    }
}

@Composable
private fun BookHero(book: BookSummary) {
    SurfacePanel {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            PosterBlock(book.title, Modifier.size(width = 92.dp, height = 124.dp))
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
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("第 ${episode.number} 集", modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
            Text("${episode.durationSeconds.coerceAtLeast(0) / 60} 分钟", color = TextSecondary)
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
            PosterBlock(book.title, Modifier.size(width = 76.dp, height = 104.dp))
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
private fun PosterBlock(title: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFFFFD38B), PrimaryGoldDark, Color(0xFF24130A)),
                ),
            )
            .border(1.dp, Color(0x55FFFFFF), RoundedCornerShape(18.dp))
            .padding(10.dp),
        contentAlignment = Alignment.BottomStart,
    ) {
        Text(title.posterInitials(), color = Color(0xFF1C1004), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
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
private fun EmptyState(message: String) {
    SurfacePanel {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 96.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(message, color = TextSecondary)
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

private val AppScreen.title: String
    get() = when (this) {
        AppScreen.LOGIN -> "登录"
        AppScreen.HOME -> "首页"
        AppScreen.SEARCH -> "搜索"
        AppScreen.DETAIL -> "详情"
        AppScreen.PLAYER -> "播放"
        AppScreen.ACCOUNT -> "账户"
    }

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
