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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.reelshort.app.data.BookSummary
import com.reelshort.app.data.EpisodeSummary
import com.reelshort.app.data.PointRecord
import com.reelshort.app.data.RechargeOrderSummary
import com.reelshort.app.data.WatchRecord

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ReelShortApp()
        }
    }
}

@Composable
fun ReelShortApp() {
    var appState by remember { mutableStateOf(AppState.sample()) }

    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            if (!appState.isAuthenticated) {
                LoginScreen(
                    username = appState.username,
                    password = appState.password,
                    onUsernameChange = { appState = appState.copy(username = it) },
                    onPasswordChange = { appState = appState.copy(password = it) },
                    onLogin = { appState = appState.copy(isAuthenticated = true) },
                )
            } else {
                MainShell(
                    state = appState,
                    onScreenSelected = { appState = appState.copy(screen = it) },
                    onLogout = { appState = appState.copy(isAuthenticated = false, screen = AppScreen.Home) },
                    onSearchChange = { appState = appState.copy(searchQuery = it) },
                    onSearch = { appState = appState.copy(screen = AppScreen.Search) },
                    onOpenBook = { appState = appState.copy(selectedBook = it, screen = AppScreen.Detail) },
                    onOpenPlayer = { episode ->
                        appState = appState.copy(selectedEpisode = episode, screen = AppScreen.Player)
                    },
                    onReportProgress = {
                        val episode = appState.selectedEpisode ?: return@MainShell
                        val updatedRecord = WatchRecord(
                            bookId = appState.selectedBook?.id ?: "unknown",
                            bookTitle = appState.selectedBook?.title ?: "Unknown",
                            episode = episode.number,
                            progressPercent = 75,
                        )
                        appState = appState.copy(watchRecords = listOf(updatedRecord) + appState.watchRecords)
                    },
                )
            }
        }
    }
}

@Composable
private fun LoginScreen(
    username: String,
    password: String,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onLogin: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text("ReelShort", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        Text("聚合播放平台", style = MaterialTheme.typography.titleMedium, color = Color.Gray)
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(
            value = username,
            onValueChange = onUsernameChange,
            label = { Text("用户名") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = password,
            onValueChange = onPasswordChange,
            label = { Text("密码") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
        )
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = onLogin,
            modifier = Modifier.fillMaxWidth(),
            enabled = username.isNotBlank() && password.isNotBlank(),
        ) {
            Text("登录")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainShell(
    state: AppState,
    onScreenSelected: (AppScreen) -> Unit,
    onLogout: () -> Unit,
    onSearchChange: (String) -> Unit,
    onSearch: () -> Unit,
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
                AppScreen.primaryTabs.forEach { screen ->
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
            when (state.screen) {
                AppScreen.Home -> HomeScreen(state.books, onOpenBook)
                AppScreen.Search -> SearchScreen(state, onSearchChange, onSearch, onOpenBook)
                AppScreen.Detail -> DetailScreen(state.selectedBook, state.episodes, onOpenPlayer)
                AppScreen.Player -> PlayerScreen(state.selectedBook, state.selectedEpisode, onReportProgress)
                AppScreen.History -> HistoryScreen(state.watchRecords)
                AppScreen.Points -> PointsScreen(state.pointBalance, state.pointRecords)
                AppScreen.Orders -> OrdersScreen(state.orders)
            }
        }
    }
}

@Composable
private fun HomeScreen(books: List<BookSummary>, onOpenBook: (BookSummary) -> Unit) {
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            SectionHeader("今日推荐", "来自 Spring Boot 首页推荐接口的未来数据")
        }
        items(books) { book ->
            BookRow(book = book, onClick = { onOpenBook(book) })
        }
    }
}

@Composable
private fun SearchScreen(
    state: AppState,
    onSearchChange: (String) -> Unit,
    onSearch: () -> Unit,
    onOpenBook: (BookSummary) -> Unit,
) {
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = state.searchQuery,
                    onValueChange = onSearchChange,
                    label = { Text("搜索剧集") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                )
                Spacer(Modifier.width(8.dp))
                Button(onClick = onSearch) {
                    Text("搜索")
                }
            }
        }
        items(state.books.filter { it.title.contains(state.searchQuery, ignoreCase = true) || state.searchQuery.isBlank() }) { book ->
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
            Text("HLS 播放器占位", color = Color.White, style = MaterialTheme.typography.titleLarge)
        }
        Text(book?.title ?: "未选择剧集", style = MaterialTheme.typography.titleLarge)
        Text("第 ${episode?.number ?: 0} 集 · 播放地址未来由 Spring Boot 返回")
        Button(onClick = onReportProgress, enabled = episode != null) {
            Text("上报 75% 观看进度")
        }
    }
}

@Composable
private fun HistoryScreen(records: List<WatchRecord>) {
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { SectionHeader("观看记录", "继续播放最近观看的分集") }
        items(records) { record ->
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
    }
}

@Composable
private fun PointsScreen(balance: Int, records: List<PointRecord>) {
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { SectionHeader("积分余额", "$balance") }
        items(records) { record ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(record.reason, modifier = Modifier.weight(1f))
                    Text(if (record.amount > 0) "+${record.amount}" else "${record.amount}")
                }
            }
        }
    }
}

@Composable
private fun OrdersScreen(orders: List<RechargeOrderSummary>) {
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { SectionHeader("充值订单", "当前只展示商业化预留数据") }
        items(orders) { order ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(order.orderNo, fontWeight = FontWeight.SemiBold)
                    Text("金额 ¥${order.amountCents / 100}.${(order.amountCents % 100).toString().padStart(2, '0')}")
                    Text("状态 ${order.status}", color = Color.Gray)
                }
            }
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
            Text(book.description, maxLines = 2, overflow = TextOverflow.Ellipsis)
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
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(message, color = Color.Gray)
    }
}

private enum class AppScreen(val title: String, val icon: String) {
    Home("首页", "首"),
    Search("搜索", "搜"),
    Detail("详情", "剧"),
    Player("播放", "播"),
    History("记录", "记"),
    Points("积分", "分"),
    Orders("订单", "单");

    companion object {
        val primaryTabs = listOf(Home, Search, History, Points, Orders)
    }
}

private data class AppState(
    val isAuthenticated: Boolean,
    val username: String,
    val password: String,
    val screen: AppScreen,
    val searchQuery: String,
    val selectedBook: BookSummary?,
    val selectedEpisode: EpisodeSummary?,
    val books: List<BookSummary>,
    val episodes: List<EpisodeSummary>,
    val watchRecords: List<WatchRecord>,
    val pointBalance: Int,
    val pointRecords: List<PointRecord>,
    val orders: List<RechargeOrderSummary>,
) {
    companion object {
        fun sample() = AppState(
            isAuthenticated = false,
            username = "demo",
            password = "",
            screen = AppScreen.Home,
            searchQuery = "",
            selectedBook = null,
            selectedEpisode = null,
            books = listOf(
                BookSummary("book-1", "Fated to My Forbidden Alpha", "狼人、契约和短剧高能反转", 62),
                BookSummary("book-2", "The Billionaire's Secret", "都市爱情与身份反转", 48),
                BookSummary("book-3", "My Mafia Protector", "动作、悬疑和快节奏剧情", 55),
            ),
            episodes = (1..8).map { EpisodeSummary(it, 180 + it * 12) },
            watchRecords = listOf(WatchRecord("book-1", "Fated to My Forbidden Alpha", 3, 58)),
            pointBalance = 18,
            pointRecords = listOf(
                PointRecord(1, "观看达到 25%"),
                PointRecord(1, "观看达到 50%"),
                PointRecord(10, "后台活动赠送"),
            ),
            orders = listOf(RechargeOrderSummary("RO202606270001", 990, "CREATED")),
        )
    }
}
