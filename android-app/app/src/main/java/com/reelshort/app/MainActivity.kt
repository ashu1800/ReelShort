package com.reelshort.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.reelshort.app.session.InMemorySessionStore
import com.reelshort.app.state.AppScreen
import com.reelshort.app.state.AppStateController
import com.reelshort.app.state.AppUiActions
import com.reelshort.app.state.AppUiState
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val actions = remember { AndroidAppFactory.createActions() }
            ReelShortApp(actions)
        }
    }
}

private object AndroidAppFactory {
    fun createActions(): AppUiActions {
        val sessionStore = InMemorySessionStore()
        lateinit var repository: AppRepository
        val apiClient = OkHttpReelShortApiClient(
            config = ApiConfig.default(),
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

    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
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
                    onReportProgress = {
                        val duration = state.currentVideoUrl?.durationSeconds ?: state.selectedEpisode?.durationSeconds ?: 200
                        scope.launch { actions.reportProgress((duration * 75) / 100, duration) }
                    },
                )
            }
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text("ReelShort", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        Text("聚合播放平台", style = MaterialTheme.typography.titleMedium, color = Color.Gray)
        ErrorBanner(state.errorMessage, onClearError)
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(
            value = username,
            onValueChange = { username = it },
            label = { Text("用户名") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = !state.isLoading,
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("密码") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = !state.isLoading,
            visualTransformation = PasswordVisualTransformation(),
        )
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = { onLogin(username, password) },
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.isLoading && username.isNotBlank() && password.isNotBlank(),
        ) {
            Text(if (state.isLoading) "登录中" else "登录")
        }
        TextButton(
            onClick = { onRegister(username, password) },
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.isLoading && username.isNotBlank() && password.isNotBlank(),
        ) {
            Text("注册新账号")
        }
    }
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
    onReportProgress: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.screen.title) },
                actions = {
                    OutlinedButton(onClick = onLogout, contentPadding = PaddingValues(horizontal = 12.dp)) {
                        Text("退出")
                    }
                },
            )
        },
        bottomBar = {
            NavigationBar {
                primaryTabs.forEach { screen ->
                    NavigationBarItem(
                        selected = state.screen == screen,
                        onClick = { onScreenSelected(screen) },
                        icon = { Text(screen.icon) },
                        label = { Text(screen.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    )
                }
            }
        },
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            Column {
                if (state.isLoading) {
                    LoadingStrip()
                }
                ErrorBanner(state.errorMessage, onClearError)
                when (state.screen) {
                    AppScreen.LOGIN -> Unit
                    AppScreen.HOME -> HomeScreen(state.homeShelf, onOpenBook)
                    AppScreen.SEARCH -> SearchScreen(state, onSearch, onOpenBook)
                    AppScreen.DETAIL -> DetailScreen(state.selectedBook, state.episodes, onOpenPlayer)
                    AppScreen.PLAYER -> PlayerScreen(state.selectedBook, state.selectedEpisode, state.currentVideoUrl?.url, onReportProgress)
                    AppScreen.ACCOUNT -> AccountScreen(state.watchHistory, state.pointAccount?.balance ?: 0, state.pointAccount?.records ?: emptyList(), state.orders)
                }
            }
        }
    }
}

@Composable
private fun LoadingStrip() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        Spacer(Modifier.width(10.dp))
        Text("正在加载")
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
            .padding(top = 12.dp)
            .background(Color(0xFFFFF1F2))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(message, modifier = Modifier.weight(1f), color = Color(0xFFB91C1C))
        TextButton(onClick = onClearError) {
            Text("关闭")
        }
    }
}

@Composable
private fun HomeScreen(books: List<BookSummary>, onOpenBook: (BookSummary) -> Unit) {
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            SectionHeader("今日推荐", "来自 Spring Boot 首页推荐接口")
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

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("搜索剧集") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                )
                Spacer(Modifier.width(8.dp))
                Button(onClick = { onSearch(query) }) {
                    Text("搜索")
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
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            SectionHeader(book.title, "${book.chapterCount} 集 · ${book.description}")
        }
        items(episodes) { episode ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenPlayer(episode) },
            ) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("第 ${episode.number} 集", modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                    Text("${episode.durationSeconds / 60} 分钟", color = Color.Gray)
                }
            }
        }
    }
}

@Composable
private fun PlayerScreen(
    book: BookSummary?,
    episode: EpisodeSummary?,
    videoUrl: String?,
    onReportProgress: () -> Unit,
) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .background(Color(0xFF202124)),
            contentAlignment = Alignment.Center,
        ) {
            Text("HLS 播放器待接入", color = Color.White, style = MaterialTheme.typography.titleLarge)
        }
        Text(book?.title ?: "未选择剧集", style = MaterialTheme.typography.titleLarge)
        Text("第 ${episode?.number ?: 0} 集")
        Text(videoUrl ?: "暂无播放地址", maxLines = 2, overflow = TextOverflow.Ellipsis, color = Color.Gray)
        Button(onClick = onReportProgress, enabled = episode != null && videoUrl != null) {
            Text("上报 75% 观看进度")
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
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { SectionHeader("积分余额", "$balance") }
        item { SectionHeader("观看记录", "最近 ${records.size} 条") }
        items(records) { record -> WatchRecordRow(record) }
        item { SectionHeader("积分流水", "最近 ${pointRecords.size} 条") }
        items(pointRecords) { record -> PointRecordRow(record) }
        item { SectionHeader("充值订单", "当前展示商业化预留数据") }
        items(orders) { order -> OrderRow(order) }
    }
}

@Composable
private fun WatchRecordRow(record: WatchRecord) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(record.bookTitle, fontWeight = FontWeight.SemiBold)
                Text("第 ${record.episode} 集", color = Color.Gray)
            }
            Text("${record.progressPercent}%")
        }
    }
}

@Composable
private fun PointRecordRow(record: PointRecord) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(record.reason ?: "积分变动", modifier = Modifier.weight(1f))
            Text(if (record.amount > 0) "+${record.amount}" else "${record.amount}")
        }
    }
}

@Composable
private fun OrderRow(order: RechargeOrderSummary) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(order.orderNo, fontWeight = FontWeight.SemiBold)
            Text("金额 ¥${order.amountCents / 100}.${(order.amountCents % 100).toString().padStart(2, '0')}")
            Text("状态 ${order.status}", color = Color.Gray)
        }
    }
}

@Composable
private fun BookRow(book: BookSummary, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF8F8FA)),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(book.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(book.description.ifBlank { "${book.chapterCount} 集短剧" }, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text("${book.chapterCount} 集", color = Color.Gray)
        }
    }
}

@Composable
private fun SectionHeader(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
    }
}

@Composable
private fun EmptyState(message: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(message, color = Color.Gray)
    }
}

private val primaryTabs = listOf(AppScreen.HOME, AppScreen.SEARCH, AppScreen.ACCOUNT)

private val AppScreen.title: String
    get() = when (this) {
        AppScreen.LOGIN -> "登录"
        AppScreen.HOME -> "首页"
        AppScreen.SEARCH -> "搜索"
        AppScreen.DETAIL -> "详情"
        AppScreen.PLAYER -> "播放"
        AppScreen.ACCOUNT -> "账户"
    }

private val AppScreen.icon: String
    get() = when (this) {
        AppScreen.LOGIN -> "登"
        AppScreen.HOME -> "首"
        AppScreen.SEARCH -> "搜"
        AppScreen.DETAIL -> "剧"
        AppScreen.PLAYER -> "播"
        AppScreen.ACCOUNT -> "账"
    }
