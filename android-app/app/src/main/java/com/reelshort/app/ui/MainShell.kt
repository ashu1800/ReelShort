package com.reelshort.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.reelshort.app.data.BookSummary
import com.reelshort.app.data.EpisodeSummary
import com.reelshort.app.state.AppScreen
import com.reelshort.app.state.AppUiState
import com.reelshort.app.ui.format.navigationLabel
import com.reelshort.app.ui.format.primaryTabs
import com.reelshort.app.ui.screens.account.AccountScreen
import com.reelshort.app.ui.screens.detail.DetailScreen
import com.reelshort.app.ui.screens.home.HomeScreen
import com.reelshort.app.ui.screens.player.PlayerScreen
import com.reelshort.app.ui.screens.search.SearchScreen
import com.reelshort.app.ui.theme.AppBackground
import com.reelshort.app.ui.theme.NavBarBackground
import com.reelshort.app.ui.theme.NavItemSelectedIcon
import com.reelshort.app.ui.theme.PrimaryGold
import com.reelshort.app.ui.theme.TextSecondary

@Composable
internal fun MainShell(
    state: AppUiState,
    onScreenSelected: (AppScreen) -> Unit,
    onLogout: () -> Unit,
    onSearch: (String) -> Unit,
    onOpenBook: (BookSummary) -> Unit,
    onOpenPlayer: (EpisodeSummary) -> Unit,
    onUpdatePlaybackPosition: (Int, Int) -> Unit,
    onAutoReportProgress: (Int, Int) -> Unit,
    onCheckApiHealth: () -> Unit,
    onShowAuthPrompt: () -> Unit,
) {
    AppBackground {
        Scaffold(
            containerColor = Color.Transparent,
            bottomBar = {
                NavigationBar(containerColor = NavBarBackground, tonalElevation = 0.dp) {
                    primaryTabs.forEach { screen ->
                        NavigationBarItem(
                            selected = state.screen == screen,
                            onClick = { onScreenSelected(screen) },
                            icon = { NavigationIcon(screen) },
                            label = { Text(screen.navigationLabel, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = NavItemSelectedIcon,
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
                androidx.compose.animation.Crossfade(
                    targetState = state.screen,
                    animationSpec = androidx.compose.animation.core.tween(durationMillis = 220),
                    label = "screen-transition",
                ) { screen ->
                    when (screen) {
                        AppScreen.LOGIN -> Unit
                        AppScreen.HOME -> HomeScreen(state.homeShelf, onOpenBook)
                        AppScreen.SEARCH -> SearchScreen(state, onSearch, onOpenBook)
                        AppScreen.DETAIL -> DetailScreen(state.selectedBook, state.episodes, onOpenPlayer)
                        AppScreen.PLAYER -> PlayerScreen(state, onUpdatePlaybackPosition, onAutoReportProgress)
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
