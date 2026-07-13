package com.reelshort.app.state

import com.reelshort.app.data.ApiHealthStatus
import com.reelshort.app.data.AppLanguage
import com.reelshort.app.data.AuthSession
import com.reelshort.app.data.BookInteractionState
import com.reelshort.app.data.BookSummary
import com.reelshort.app.data.Comment
import com.reelshort.app.data.EpisodeSummary
import com.reelshort.app.data.PointAccount
import com.reelshort.app.data.PointTransferRecord
import com.reelshort.app.data.RechargeOrderSummary
import com.reelshort.app.data.SavedCredentials
import com.reelshort.app.data.VideoUrl
import com.reelshort.app.data.WatchRecord
import com.reelshort.app.data.WalletInfo
import com.reelshort.app.data.WithdrawalRecord
import com.reelshort.app.data.WithdrawalSummary

enum class AppScreen {
    LOGIN,
    HOME,
    SEARCH,
    DETAIL,
    PLAYER,
    ACCOUNT,
    FAVORITES,
}

enum class AuthMode {
    LOGIN,
    REGISTER,
}

enum class AccountOperation {
    WALLET_VERIFICATION,
    PASSWORD_VERIFICATION,
    WALLET_MUTATION,
    WITHDRAWAL,
    POINT_TRANSFER,
    PASSWORD_CHANGE,
}

enum class UiMessageType {
    SUCCESS,
    ERROR,
    INFO,
}

data class AppUiState(
    val screen: AppScreen = AppScreen.HOME,
    val language: AppLanguage = AppLanguage.DEFAULT,
    val authMode: AuthMode = AuthMode.LOGIN,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val messageType: UiMessageType? = null,
    val apiBaseUrl: String = "",
    val apiHealthStatus: ApiHealthStatus? = null,
    val session: AuthSession? = null,
    val savedCredentials: SavedCredentials? = null,
    val authPromptVisible: Boolean = false,
    val authSmsCountdownSeconds: Int = 0,
    val authSmsCountdownTrigger: Long = 0,
    val walletSmsCountdownSeconds: Int = 0,
    val walletSmsCountdownTrigger: Long = 0,
    val passwordSmsCountdownSeconds: Int = 0,
    val passwordSmsCountdownTrigger: Long = 0,
    val pendingPlaybackEpisode: EpisodeSummary? = null,
    val homeShelf: List<BookSummary> = emptyList(),
    val isHomeRefreshing: Boolean = false,
    val searchQuery: String = "",
    val searchResults: List<BookSummary> = emptyList(),
    val selectedBook: BookSummary? = null,
    val episodes: List<EpisodeSummary> = emptyList(),
    val selectedEpisode: EpisodeSummary? = null,
    val currentVideoUrl: VideoUrl? = null,
    val playback: PlaybackState = PlaybackState(),
    val playerReturnScreen: AppScreen? = null,
    val watchHistory: List<WatchRecord> = emptyList(),
    val continueWatchingBooks: Map<String, BookSummary> = emptyMap(),
    val pointAccount: PointAccount? = null,
    val orders: List<RechargeOrderSummary> = emptyList(),
    val wallet: WalletInfo? = null,
    val walletMutationVersion: Long = 0,
    val withdrawalSubmissionVersion: Long = 0,
    val accountOperation: AccountOperation? = null,
    val withdrawalSummary: WithdrawalSummary? = null,
    val withdrawals: List<WithdrawalRecord> = emptyList(),
    val pointTransfers: List<PointTransferRecord> = emptyList(),
    val interaction: BookInteractionState = BookInteractionState(),
    val comments: List<Comment> = emptyList(),
    val favorites: List<BookSummary> = emptyList(),
)
