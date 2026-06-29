package com.reelshort.app.state

import com.reelshort.app.data.ApiHealthStatus
import com.reelshort.app.data.AuthSession
import com.reelshort.app.data.BookSummary
import com.reelshort.app.data.EpisodeSummary
import com.reelshort.app.data.PointAccount
import com.reelshort.app.data.RechargeOrderSummary
import com.reelshort.app.data.SavedCredentials
import com.reelshort.app.data.VideoUrl
import com.reelshort.app.data.WatchRecord

enum class AppScreen {
    LOGIN,
    HOME,
    SEARCH,
    DETAIL,
    PLAYER,
    ACCOUNT,
}

data class AppUiState(
    val screen: AppScreen = AppScreen.HOME,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val apiBaseUrl: String = "",
    val apiHealthStatus: ApiHealthStatus? = null,
    val session: AuthSession? = null,
    val savedCredentials: SavedCredentials? = null,
    val authPromptVisible: Boolean = false,
    val pendingPlaybackEpisode: EpisodeSummary? = null,
    val homeShelf: List<BookSummary> = emptyList(),
    val searchQuery: String = "",
    val searchResults: List<BookSummary> = emptyList(),
    val selectedBook: BookSummary? = null,
    val episodes: List<EpisodeSummary> = emptyList(),
    val selectedEpisode: EpisodeSummary? = null,
    val currentVideoUrl: VideoUrl? = null,
    val playback: PlaybackState = PlaybackState(),
    val watchHistory: List<WatchRecord> = emptyList(),
    val pointAccount: PointAccount? = null,
    val orders: List<RechargeOrderSummary> = emptyList(),
)
