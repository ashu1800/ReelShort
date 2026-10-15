// GENERATED FILE - DO NOT EDIT BY HAND.
//
// Produced by tool/port_app_ui_state.py from
// android-app/app-core/src/main/kotlin/com/reelshort/app/state/AppUiState.kt
// Regenerate with: python tool/port_app_ui_state.py
//
// Field names, types and defaults are a 1:1 port of the Kotlin state class.
library;

import '../data/models/app_language.dart';
import '../data/models/app_models.dart';
import 'playback_state.dart';

enum AppScreen {
  login,
  home,
  search,
  detail,
  player,
  account,
  funds,
  withdrawal,
  vipRecharge,
  favorites,
}

enum AuthMode {
  login,
  register,
}

enum AccountOperation {
  walletVerification,
  passwordVerification,
  walletMutation,
  withdrawal,
  pointTransfer,
  passwordChange,
}

enum UiMessageType {
  success,
  error,
  info,
}

class AppUiState {
  const AppUiState({
    this.screen = AppScreen.home,
    this.language = AppLanguage.defaultLanguage,
    this.authMode = AuthMode.login,
    this.isLoading = false,
    this.errorMessage,
    this.messageTrigger = 0,
    this.messageType,
    this.apiBaseUrl = '',
    this.apiHealthStatus,
    this.session,
    this.savedCredentials,
    this.authPromptVisible = false,
    this.captchaId = '',
    this.captchaImageBase64 = '',
    this.pendingPlaybackEpisode,
    this.vipActive = false,
    this.vipPaywallVisible = false,
    this.vipPaywallLoading = false,
    this.pendingVipEpisode,
    this.homeShelf = const <BookSummary>[],
    this.isHomeRefreshing = false,
    this.searchQuery = '',
    this.searchResults = const <BookSummary>[],
    this.selectedBook,
    this.episodes = const <EpisodeSummary>[],
    this.selectedEpisode,
    this.currentVideoUrl,
    this.playback = const PlaybackState(),
    this.playerReturnScreen,
    this.watchHistory = const <WatchRecord>[],
    this.continueWatchingBooks = const <String, BookSummary>{},
    this.pointAccount,
    this.orders = const <RechargeOrderSummary>[],
    this.wallet,
    this.walletMutationVersion = 0,
    this.vipUntil,
    this.vipPriceUsdt = '',
    this.vipCollectionAddress = '',
    this.latestVipOrder,
    this.vipOrders = const <VipOrder>[],
    this.withdrawalSubmissionVersion = 0,
    this.accountOperation,
    this.withdrawalSummary,
    this.withdrawals = const <WithdrawalRecord>[],
    this.interaction = const BookInteractionState(),
    this.comments = const <Comment>[],
    this.favorites = const <BookSummary>[],
  });

  final AppScreen screen;
  final AppLanguage language;
  final AuthMode authMode;
  final bool isLoading;
  final String? errorMessage;
  final int messageTrigger;
  final UiMessageType? messageType;
  final String apiBaseUrl;
  final ApiHealthStatus? apiHealthStatus;
  final AuthSession? session;
  final SavedCredentials? savedCredentials;
  final bool authPromptVisible;
  final String captchaId;
  final String captchaImageBase64;
  final EpisodeSummary? pendingPlaybackEpisode;
  final bool vipActive;
  final bool vipPaywallVisible;
  final bool vipPaywallLoading;
  final EpisodeSummary? pendingVipEpisode;
  final List<BookSummary> homeShelf;
  final bool isHomeRefreshing;
  final String searchQuery;
  final List<BookSummary> searchResults;
  final BookSummary? selectedBook;
  final List<EpisodeSummary> episodes;
  final EpisodeSummary? selectedEpisode;
  final VideoUrl? currentVideoUrl;
  final PlaybackState playback;
  final AppScreen? playerReturnScreen;
  final List<WatchRecord> watchHistory;
  final Map<String, BookSummary> continueWatchingBooks;
  final PointAccount? pointAccount;
  final List<RechargeOrderSummary> orders;
  final WalletInfo? wallet;
  final int walletMutationVersion;
  final String? vipUntil;
  final String vipPriceUsdt;
  final String vipCollectionAddress;
  final VipOrder? latestVipOrder;
  final List<VipOrder> vipOrders;
  final int withdrawalSubmissionVersion;
  final AccountOperation? accountOperation;
  final WithdrawalSummary? withdrawalSummary;
  final List<WithdrawalRecord> withdrawals;
  final BookInteractionState interaction;
  final List<Comment> comments;
  final List<BookSummary> favorites;

  /// Sentinel that lets [copyWith] distinguish "argument omitted" from
  /// "explicitly set this nullable field to null" - the equivalent of Kotlin's
  /// generated `copy()`.
  static const Object _noChange = Object();

  AppUiState copyWith({
    AppScreen? screen,
    AppLanguage? language,
    AuthMode? authMode,
    bool? isLoading,
    Object? errorMessage = _noChange,
    int? messageTrigger,
    Object? messageType = _noChange,
    String? apiBaseUrl,
    Object? apiHealthStatus = _noChange,
    Object? session = _noChange,
    Object? savedCredentials = _noChange,
    bool? authPromptVisible,
    String? captchaId,
    String? captchaImageBase64,
    Object? pendingPlaybackEpisode = _noChange,
    bool? vipActive,
    bool? vipPaywallVisible,
    bool? vipPaywallLoading,
    Object? pendingVipEpisode = _noChange,
    List<BookSummary>? homeShelf,
    bool? isHomeRefreshing,
    String? searchQuery,
    List<BookSummary>? searchResults,
    Object? selectedBook = _noChange,
    List<EpisodeSummary>? episodes,
    Object? selectedEpisode = _noChange,
    Object? currentVideoUrl = _noChange,
    PlaybackState? playback,
    Object? playerReturnScreen = _noChange,
    List<WatchRecord>? watchHistory,
    Map<String, BookSummary>? continueWatchingBooks,
    Object? pointAccount = _noChange,
    List<RechargeOrderSummary>? orders,
    Object? wallet = _noChange,
    int? walletMutationVersion,
    Object? vipUntil = _noChange,
    String? vipPriceUsdt,
    String? vipCollectionAddress,
    Object? latestVipOrder = _noChange,
    List<VipOrder>? vipOrders,
    int? withdrawalSubmissionVersion,
    Object? accountOperation = _noChange,
    Object? withdrawalSummary = _noChange,
    List<WithdrawalRecord>? withdrawals,
    BookInteractionState? interaction,
    List<Comment>? comments,
    List<BookSummary>? favorites,
  }) => AppUiState(
    screen: screen ?? this.screen,
    language: language ?? this.language,
    authMode: authMode ?? this.authMode,
    isLoading: isLoading ?? this.isLoading,
    errorMessage: identical(errorMessage, _noChange)
        ? this.errorMessage
        : errorMessage as String?,
    messageTrigger: messageTrigger ?? this.messageTrigger,
    messageType: identical(messageType, _noChange)
        ? this.messageType
        : messageType as UiMessageType?,
    apiBaseUrl: apiBaseUrl ?? this.apiBaseUrl,
    apiHealthStatus: identical(apiHealthStatus, _noChange)
        ? this.apiHealthStatus
        : apiHealthStatus as ApiHealthStatus?,
    session: identical(session, _noChange)
        ? this.session
        : session as AuthSession?,
    savedCredentials: identical(savedCredentials, _noChange)
        ? this.savedCredentials
        : savedCredentials as SavedCredentials?,
    authPromptVisible: authPromptVisible ?? this.authPromptVisible,
    captchaId: captchaId ?? this.captchaId,
    captchaImageBase64: captchaImageBase64 ?? this.captchaImageBase64,
    pendingPlaybackEpisode: identical(pendingPlaybackEpisode, _noChange)
        ? this.pendingPlaybackEpisode
        : pendingPlaybackEpisode as EpisodeSummary?,
    vipActive: vipActive ?? this.vipActive,
    vipPaywallVisible: vipPaywallVisible ?? this.vipPaywallVisible,
    vipPaywallLoading: vipPaywallLoading ?? this.vipPaywallLoading,
    pendingVipEpisode: identical(pendingVipEpisode, _noChange)
        ? this.pendingVipEpisode
        : pendingVipEpisode as EpisodeSummary?,
    homeShelf: homeShelf ?? this.homeShelf,
    isHomeRefreshing: isHomeRefreshing ?? this.isHomeRefreshing,
    searchQuery: searchQuery ?? this.searchQuery,
    searchResults: searchResults ?? this.searchResults,
    selectedBook: identical(selectedBook, _noChange)
        ? this.selectedBook
        : selectedBook as BookSummary?,
    episodes: episodes ?? this.episodes,
    selectedEpisode: identical(selectedEpisode, _noChange)
        ? this.selectedEpisode
        : selectedEpisode as EpisodeSummary?,
    currentVideoUrl: identical(currentVideoUrl, _noChange)
        ? this.currentVideoUrl
        : currentVideoUrl as VideoUrl?,
    playback: playback ?? this.playback,
    playerReturnScreen: identical(playerReturnScreen, _noChange)
        ? this.playerReturnScreen
        : playerReturnScreen as AppScreen?,
    watchHistory: watchHistory ?? this.watchHistory,
    continueWatchingBooks: continueWatchingBooks ?? this.continueWatchingBooks,
    pointAccount: identical(pointAccount, _noChange)
        ? this.pointAccount
        : pointAccount as PointAccount?,
    orders: orders ?? this.orders,
    wallet: identical(wallet, _noChange)
        ? this.wallet
        : wallet as WalletInfo?,
    walletMutationVersion: walletMutationVersion ?? this.walletMutationVersion,
    vipUntil: identical(vipUntil, _noChange)
        ? this.vipUntil
        : vipUntil as String?,
    vipPriceUsdt: vipPriceUsdt ?? this.vipPriceUsdt,
    vipCollectionAddress: vipCollectionAddress ?? this.vipCollectionAddress,
    latestVipOrder: identical(latestVipOrder, _noChange)
        ? this.latestVipOrder
        : latestVipOrder as VipOrder?,
    vipOrders: vipOrders ?? this.vipOrders,
    withdrawalSubmissionVersion: withdrawalSubmissionVersion ?? this.withdrawalSubmissionVersion,
    accountOperation: identical(accountOperation, _noChange)
        ? this.accountOperation
        : accountOperation as AccountOperation?,
    withdrawalSummary: identical(withdrawalSummary, _noChange)
        ? this.withdrawalSummary
        : withdrawalSummary as WithdrawalSummary?,
    withdrawals: withdrawals ?? this.withdrawals,
    interaction: interaction ?? this.interaction,
    comments: comments ?? this.comments,
    favorites: favorites ?? this.favorites,
  );
}
