// GENERATED FILE - DO NOT EDIT BY HAND.
//
// Source of truth: android-app/app/src/main/java/com/reelshort/app/ui/format/AppStrings.kt
// Regenerate with: python tool/port_app_strings.py
//
// The Flutter rewrite keeps every user-visible English string of the Kotlin app
// in one place, exactly like the original. test/core/generated_contract_test.dart
// compares every value below against test/core/app_strings_golden.json, which the
// same generator snapshots straight from the Kotlin source.

import 'package:meta/meta.dart';

import '../../data/models/app_language.dart';

/// All user-visible English copy of the app.
///
/// The app is English-only (see [AppLanguage]); the field set is intentionally
/// identical to the Kotlin `AppStrings` data class so that copy cannot drift
/// between the two clients.
@immutable
class AppStrings {
  final String homeTab;
  final String searchTab;
  final String accountTab;
  final String loginScreen;
  final String detailScreen;
  final String playerScreen;
  final String favoritesScreen;
  final String searchTitle;
  final String searchSubtitle;
  final String searchHint;
  final String searchAction;
  final String searchTagsTitle;
  final String searchHeroEyebrow;
  final String searchResultTitle;
  final String searchResultCountSuffix;
  final String searchEmptyTitle;
  final String searchInitialHint;
  final String searchEmpty;
  final String loadingDialogTitle;
  final String homeHeaderTitle;
  final String homeHeaderSubtitleSuffix;
  final String favoritesHeaderTitle;
  final String favoritesHeaderSubtitleSuffix;
  final String diagnosticsUnknownLabel;
  final String diagnosticsUnknownMessage;
  final String diagnosticsUpLabel;
  final String diagnosticsUpMessagePrefix;
  final String diagnosticsDownLabel;
  final String diagnosticsDownMessagePrefix;
  final String homeEmptyTitle;
  final String homeEmptyMessage;
  final String homeEmptyAction;
  final String favoritesEmptyTitle;
  final String favoritesEmptyMessage;
  final String searchDefaultEmptyTitle;
  final String searchDefaultEmptyMessage;
  final String searchNoResultsTitle;
  final String searchNoResultsMessagePrefix;
  final String searchNoResultsMessageSuffix;
  final String searchRetryAction;
  final String detailEmptyTitle;
  final String detailEmptyMessage;
  final String detailEmptyAction;
  final String detailUnavailableTitle;
  final String detailUnavailableMessagePrefix;
  final String detailUnavailableMessageSuffix;
  final String detailUnavailableAction;
  final String accountFavoritesTitle;
  final String accountPointsTitle;
  final String accountWatchHistoryTitle;
  final String accountPointRecordsTitle;
  final String accountOrdersTitle;
  final String accountWalletTitle;
  final String accountWalletNetworkHint;
  final String accountWithdrawTitle;
  final String fundsTitle;
  final String fundsSubtitle;
  final String fundsTotalPointsLabel;
  final String fundsTodayCreditedPointsLabel;
  final String fundsFrozenPointsLabel;
  final String fundsAvailablePointsLabel;
  final String fundsWithdrawalAction;
  final String fundsWithdrawalSubtitle;
  final String fundsVipRechargeAction;
  final String fundsVipRechargeSubtitle;
  final String fundsLoginRequired;
  final String accountChangePasswordTitle;
  final String accountBankCardTitle;
  final String authContinue;
  final String accountWithdrawalsTitle;
  final String accountWithdrawalRecordsTitle;
  final String accountWithdrawalRecordsEmpty;
  final String accountSignOutTitle;
  final String accountContactSupportTitle;
  final String accountContactSupportEmail;
  final String accountContactSupportClose;
  final String accountGuestSignIn;
  final String accountGuestRegister;
  final String accountGuestSignInSubtitle;
  final String accountGuestRegisterSubtitle;
  final String accountFavoritesSubtitle;
  final String accountPointsSuffix;
  final String accountRecentCountPrefix;
  final String accountRecordsSuffix;
  final String accountOrdersReserved;
  final String accountRecentWatchTitle;
  final String accountRecentWatchSubtitle;
  final String accountUserFallback;
  final String accountGuestUser;
  final String accountLoggedInStatus;
  final String accountGuestStatus;
  final String accountGuestPill;
  final String accountGuestRewardHint;
  final String accountSignOutSubtitle;
  final String accountSignInSuccessHint;
  final String authLoginTitle;
  final String authLoginSubtitle;
  final String authRegisterTitle;
  final String authRegisterSubtitle;
  final String authBottomSheetPlaybackSubtitle;
  final String authBottomSheetAccountSubtitle;
  final String authBrandSubtitle;
  final String authClose;
  final String authUsernameLabel;
  final String authPasswordLabel;
  final String authLoginLoading;
  final String authLoginAction;
  final String authLoginSecondary;
  final String authRegisterAction;
  final String authRegisterSecondary;
  final String authCaptchaAnswerLabel;
  final String authCaptchaRefresh;
  final String authCaptchaLoading;
  final String authConfirmPasswordLabel;
  final String authPromptPlayback;
  final String authPromptAccount;
  final String rememberPassword;
  final String playerEpisodePrefix;
  final String playerEpisodeUnit;
  final String playerPlayAction;
  final String playerLoadingPrefix;
  final String playerLoadingSuffix;
  final String playerEpisodeSelectorPrefix;
  final String playerEpisodeSelectorCompleted;
  final String playerEpisodeSelectorTotalPrefix;
  final String playerEpisodeSelectorTotalSuffix;
  final String playerRefreshAction;
  final String playerRetryAction;
  final String playerNextEpisodeAction;
  final String playerBackAction;
  final String playerRewardError;
  final String playerVideoError;
  final String playerFavoriteLabel;
  final String playerCurrentEpisodePrefix;
  final String playerEpisodeStatusCurrent;
  final String playerEpisodeStatusWatched;
  final String playerExpandEpisodes;
  final String playerClose;
  final String playerCommentsTitlePrefix;
  final String playerCommentsEmpty;
  final String playerCommentPlaceholder;
  final String playerSend;
  final String listEpisodePrefix;
  final String listPointsChangeFallback;
  final String listPointRecordSubtitle;
  final String listCurrencySymbol;
  final String listPointsLabel;
  final String accountFrozenAvailableLabel;
  final String accountWalletNoBound;
  final String accountWalletAddressLabel;
  final String accountWalletBindAction;
  final String accountWalletReplaceAction;
  final String accountWalletUnbindAction;
  final String accountVipStatusActive;
  final String accountVipStatusInactive;
  final String accountVipExpiryPrefix;
  final String accountVipUnlockAction;
  final String accountVipPriceSuffix;
  final String accountVipNoOrders;
  final String vipActiveLabel;
  final String vipExpiryLabel;
  final String vipPayTitle;
  final String vipPayAmount;
  final String vipPayAddress;
  final String vipPayCopy;
  final String vipPayOrderNo;
  final String vipPayStatusPending;
  final String vipPayStatusConfirmed;
  final String vipPayStatusRejected;
  final String vipPayHint;
  final String vipPayQrHint;
  final String vipPayExpiresIn;
  final String vipPayExpired;
  final String vipPayUnlockEpisodePrefix;
  final String vipPayLockedHint;
  final String vipPayRefresh;
  final String vipPayRefreshing;
  final String vipPayCreating;
  final String vipRecordsTitle;
  final String accountWithdrawAvailableLabel;
  final String accountWithdrawMinimumLabel;
  final String accountWithdrawWalletRequired;
  final String accountWithdrawPointAmountLabel;
  final String accountWithdrawArrivalHint;
  final String accountWithdrawSubmitAction;
  final String accountPasswordNewLabel;
  final String accountBankCardUnsupported;
  final String posterEpisodeUnit;
  final String bookRowFallbackSuffix;

  const AppStrings({
    required this.homeTab,
    required this.searchTab,
    required this.accountTab,
    required this.loginScreen,
    required this.detailScreen,
    required this.playerScreen,
    required this.favoritesScreen,
    required this.searchTitle,
    required this.searchSubtitle,
    required this.searchHint,
    required this.searchAction,
    required this.searchTagsTitle,
    required this.searchHeroEyebrow,
    required this.searchResultTitle,
    required this.searchResultCountSuffix,
    required this.searchEmptyTitle,
    required this.searchInitialHint,
    required this.searchEmpty,
    required this.loadingDialogTitle,
    required this.homeHeaderTitle,
    required this.homeHeaderSubtitleSuffix,
    required this.favoritesHeaderTitle,
    required this.favoritesHeaderSubtitleSuffix,
    required this.diagnosticsUnknownLabel,
    required this.diagnosticsUnknownMessage,
    required this.diagnosticsUpLabel,
    required this.diagnosticsUpMessagePrefix,
    required this.diagnosticsDownLabel,
    required this.diagnosticsDownMessagePrefix,
    required this.homeEmptyTitle,
    required this.homeEmptyMessage,
    required this.homeEmptyAction,
    required this.favoritesEmptyTitle,
    required this.favoritesEmptyMessage,
    required this.searchDefaultEmptyTitle,
    required this.searchDefaultEmptyMessage,
    required this.searchNoResultsTitle,
    required this.searchNoResultsMessagePrefix,
    required this.searchNoResultsMessageSuffix,
    required this.searchRetryAction,
    required this.detailEmptyTitle,
    required this.detailEmptyMessage,
    required this.detailEmptyAction,
    required this.detailUnavailableTitle,
    required this.detailUnavailableMessagePrefix,
    required this.detailUnavailableMessageSuffix,
    required this.detailUnavailableAction,
    required this.accountFavoritesTitle,
    required this.accountPointsTitle,
    required this.accountWatchHistoryTitle,
    required this.accountPointRecordsTitle,
    required this.accountOrdersTitle,
    required this.accountWalletTitle,
    required this.accountWalletNetworkHint,
    required this.accountWithdrawTitle,
    required this.fundsTitle,
    required this.fundsSubtitle,
    required this.fundsTotalPointsLabel,
    required this.fundsTodayCreditedPointsLabel,
    required this.fundsFrozenPointsLabel,
    required this.fundsAvailablePointsLabel,
    required this.fundsWithdrawalAction,
    required this.fundsWithdrawalSubtitle,
    required this.fundsVipRechargeAction,
    required this.fundsVipRechargeSubtitle,
    required this.fundsLoginRequired,
    required this.accountChangePasswordTitle,
    required this.accountBankCardTitle,
    required this.authContinue,
    required this.accountWithdrawalsTitle,
    required this.accountWithdrawalRecordsTitle,
    required this.accountWithdrawalRecordsEmpty,
    required this.accountSignOutTitle,
    required this.accountContactSupportTitle,
    required this.accountContactSupportEmail,
    required this.accountContactSupportClose,
    required this.accountGuestSignIn,
    required this.accountGuestRegister,
    required this.accountGuestSignInSubtitle,
    required this.accountGuestRegisterSubtitle,
    required this.accountFavoritesSubtitle,
    required this.accountPointsSuffix,
    required this.accountRecentCountPrefix,
    required this.accountRecordsSuffix,
    required this.accountOrdersReserved,
    required this.accountRecentWatchTitle,
    required this.accountRecentWatchSubtitle,
    required this.accountUserFallback,
    required this.accountGuestUser,
    required this.accountLoggedInStatus,
    required this.accountGuestStatus,
    required this.accountGuestPill,
    required this.accountGuestRewardHint,
    required this.accountSignOutSubtitle,
    required this.accountSignInSuccessHint,
    required this.authLoginTitle,
    required this.authLoginSubtitle,
    required this.authRegisterTitle,
    required this.authRegisterSubtitle,
    required this.authBottomSheetPlaybackSubtitle,
    required this.authBottomSheetAccountSubtitle,
    required this.authBrandSubtitle,
    required this.authClose,
    required this.authUsernameLabel,
    required this.authPasswordLabel,
    required this.authLoginLoading,
    required this.authLoginAction,
    required this.authLoginSecondary,
    required this.authRegisterAction,
    required this.authRegisterSecondary,
    required this.authCaptchaAnswerLabel,
    required this.authCaptchaRefresh,
    required this.authCaptchaLoading,
    required this.authConfirmPasswordLabel,
    required this.authPromptPlayback,
    required this.authPromptAccount,
    required this.rememberPassword,
    required this.playerEpisodePrefix,
    required this.playerEpisodeUnit,
    required this.playerPlayAction,
    required this.playerLoadingPrefix,
    required this.playerLoadingSuffix,
    required this.playerEpisodeSelectorPrefix,
    required this.playerEpisodeSelectorCompleted,
    required this.playerEpisodeSelectorTotalPrefix,
    required this.playerEpisodeSelectorTotalSuffix,
    required this.playerRefreshAction,
    required this.playerRetryAction,
    required this.playerNextEpisodeAction,
    required this.playerBackAction,
    required this.playerRewardError,
    required this.playerVideoError,
    required this.playerFavoriteLabel,
    required this.playerCurrentEpisodePrefix,
    required this.playerEpisodeStatusCurrent,
    required this.playerEpisodeStatusWatched,
    required this.playerExpandEpisodes,
    required this.playerClose,
    required this.playerCommentsTitlePrefix,
    required this.playerCommentsEmpty,
    required this.playerCommentPlaceholder,
    required this.playerSend,
    required this.listEpisodePrefix,
    required this.listPointsChangeFallback,
    required this.listPointRecordSubtitle,
    required this.listCurrencySymbol,
    required this.listPointsLabel,
    required this.accountFrozenAvailableLabel,
    required this.accountWalletNoBound,
    required this.accountWalletAddressLabel,
    required this.accountWalletBindAction,
    required this.accountWalletReplaceAction,
    required this.accountWalletUnbindAction,
    required this.accountVipStatusActive,
    required this.accountVipStatusInactive,
    required this.accountVipExpiryPrefix,
    required this.accountVipUnlockAction,
    required this.accountVipPriceSuffix,
    required this.accountVipNoOrders,
    required this.vipActiveLabel,
    required this.vipExpiryLabel,
    required this.vipPayTitle,
    required this.vipPayAmount,
    required this.vipPayAddress,
    required this.vipPayCopy,
    required this.vipPayOrderNo,
    required this.vipPayStatusPending,
    required this.vipPayStatusConfirmed,
    required this.vipPayStatusRejected,
    required this.vipPayHint,
    required this.vipPayQrHint,
    required this.vipPayExpiresIn,
    required this.vipPayExpired,
    required this.vipPayUnlockEpisodePrefix,
    required this.vipPayLockedHint,
    required this.vipPayRefresh,
    required this.vipPayRefreshing,
    required this.vipPayCreating,
    required this.vipRecordsTitle,
    required this.accountWithdrawAvailableLabel,
    required this.accountWithdrawMinimumLabel,
    required this.accountWithdrawWalletRequired,
    required this.accountWithdrawPointAmountLabel,
    required this.accountWithdrawArrivalHint,
    required this.accountWithdrawSubmitAction,
    required this.accountPasswordNewLabel,
    required this.accountBankCardUnsupported,
    required this.posterEpisodeUnit,
    required this.bookRowFallbackSuffix,
  });
}

const AppStrings _english = AppStrings(
    homeTab: 'Home',
    searchTab: 'Discover',
    accountTab: 'Me',
    loginScreen: 'Sign in',
    detailScreen: 'Details',
    playerScreen: 'Player',
    favoritesScreen: 'Favorites',
    searchTitle: 'Discover',
    searchSubtitle: 'Start with a trope, role, or keyword.',
    searchHint: 'Search romance, CEO, revenge...',
    searchAction: 'Search',
    searchTagsTitle: 'Popular lanes',
    searchHeroEyebrow: 'Curated discovery',
    searchResultTitle: 'Results',
    searchResultCountSuffix: 'dramas',
    searchEmptyTitle: 'Find your next drama',
    searchInitialHint: 'Choose a preset tag or search by title.',
    searchEmpty: 'No dramas matched this search.',
    loadingDialogTitle: 'Loading',
    homeHeaderTitle: 'Today\'s picks',
    homeHeaderSubtitleSuffix: 'dramas selected for you',
    favoritesHeaderTitle: 'Favorites',
    favoritesHeaderSubtitleSuffix: 'saved dramas',
    diagnosticsUnknownLabel: 'Unchecked',
    diagnosticsUnknownMessage: 'Tap refresh to verify the emulator can reach the Spring Boot API.',
    diagnosticsUpLabel: 'Connected',
    diagnosticsUpMessagePrefix: 'Backend ',
    diagnosticsDownLabel: 'Unavailable',
    diagnosticsDownMessagePrefix: 'Backend health is ',
    homeEmptyTitle: 'No picks right now',
    homeEmptyMessage: 'The provider did not return recommendations yet. Try search instead.',
    homeEmptyAction: 'Go to search',
    favoritesEmptyTitle: 'No favorites yet',
    favoritesEmptyMessage: 'Tap favorite on the player and saved dramas will show up here.',
    searchDefaultEmptyTitle: 'Discover dramas',
    searchDefaultEmptyMessage: 'Search by title, role, or keyword to find your next drama.',
    searchNoResultsTitle: 'No matching dramas',
    searchNoResultsMessagePrefix: 'Nothing matched “',
    searchNoResultsMessageSuffix: '”. Try another keyword.',
    searchRetryAction: 'Search again',
    detailEmptyTitle: 'Pick a drama first',
    detailEmptyMessage: 'Open a title from Home or Discover to see its episodes here.',
    detailEmptyAction: 'Back to home',
    detailUnavailableTitle: 'Episodes unavailable',
    detailUnavailableMessagePrefix: '“',
    detailUnavailableMessageSuffix: '” has no playable episodes right now. Try again later or choose another title.',
    detailUnavailableAction: 'Choose another drama',
    accountFavoritesTitle: 'Favorites',
    accountPointsTitle: 'Points',
    accountWatchHistoryTitle: 'Watch history',
    accountPointRecordsTitle: 'Point records',
    accountOrdersTitle: 'VIP Orders',
    accountWalletTitle: 'Cold wallet',
    accountWalletNetworkHint: 'ERC20 wallets only',
    accountWithdrawTitle: 'Withdraw',
    fundsTitle: 'Funds center',
    fundsSubtitle: 'Points, withdrawals, and VIP membership',
    fundsTotalPointsLabel: 'Total points',
    fundsTodayCreditedPointsLabel: 'Credited today',
    fundsFrozenPointsLabel: 'Frozen points',
    fundsAvailablePointsLabel: 'Available points',
    fundsWithdrawalAction: 'Withdraw',
    fundsWithdrawalSubtitle: 'Convert points to USDT',
    fundsVipRechargeAction: 'VIP recharge',
    fundsVipRechargeSubtitle: 'Open VIP membership',
    fundsLoginRequired: 'Sign in to view your points',
    accountChangePasswordTitle: 'Change password',
    accountBankCardTitle: 'Bank card',
    authContinue: 'Continue',
    accountWithdrawalsTitle: 'Withdrawals',
    accountWithdrawalRecordsTitle: 'Withdrawal records',
    accountWithdrawalRecordsEmpty: 'No withdrawal records yet',
    accountSignOutTitle: 'Sign out',
    accountContactSupportTitle: 'Contact support',
    accountContactSupportEmail: 'shortlink@gmail.com',
    accountContactSupportClose: 'Close',
    accountGuestSignIn: 'Sign in',
    accountGuestRegister: 'Create account',
    accountGuestSignInSubtitle: 'Sign in to watch dramas and earn rewards',
    accountGuestRegisterSubtitle: 'Create an account to keep your progress',
    accountFavoritesSubtitle: 'Saved dramas',
    accountPointsSuffix: ' pts',
    accountRecentCountPrefix: 'Recent ',
    accountRecordsSuffix: ' items',
    accountOrdersReserved: 'No VIP orders yet',
    accountRecentWatchTitle: 'Continue watching',
    accountRecentWatchSubtitle: 'Pick up from your last episode',
    accountUserFallback: 'ShortLink user',
    accountGuestUser: 'Guest',
    accountLoggedInStatus: 'Signed in · ShortLink',
    accountGuestStatus: 'Sign in to keep watch progress and rewards',
    accountGuestPill: 'Guest mode',
    accountGuestRewardHint: 'Browse Home and Discover before signing in',
    accountSignOutSubtitle: 'Sign out of this account',
    accountSignInSuccessHint: 'Signed in',
    authLoginTitle: 'Welcome back',
    authLoginSubtitle: 'Sign in with your username to continue.',
    authRegisterTitle: 'Create your account',
    authRegisterSubtitle: 'Pick a username, set a password of at least 8 characters, and solve the captcha.',
    authBottomSheetPlaybackSubtitle: 'Playback, rewards, and account data require sign in.',
    authBottomSheetAccountSubtitle: 'Sign in to view points, watch history, and orders.',
    authBrandSubtitle: 'Stream short dramas anytime, anywhere',
    authClose: 'Close',
    authUsernameLabel: 'Username',
    authPasswordLabel: 'Password',
    authLoginLoading: 'Signing in',
    authLoginAction: 'Sign in',
    authLoginSecondary: 'Already have access? Sign in',
    authRegisterAction: 'Create account',
    authRegisterSecondary: 'New here? Create account',
    authCaptchaAnswerLabel: 'Captcha answer',
    authCaptchaRefresh: 'Refresh captcha',
    authCaptchaLoading: 'Loading captcha',
    authConfirmPasswordLabel: 'Confirm password',
    authPromptPlayback: 'Sign in to keep watching',
    authPromptAccount: 'Sign in to view your account',
    rememberPassword: 'Remember password',
    playerEpisodePrefix: 'EP ',
    playerEpisodeUnit: '',
    playerPlayAction: 'Play',
    playerLoadingPrefix: 'Loading ',
    playerLoadingSuffix: '...',
    playerEpisodeSelectorPrefix: 'Episodes',
    playerEpisodeSelectorCompleted: 'Completed',
    playerEpisodeSelectorTotalPrefix: 'Total ',
    playerEpisodeSelectorTotalSuffix: '',
    playerRefreshAction: 'Refresh stream',
    playerRetryAction: 'Retry',
    playerNextEpisodeAction: 'Next episode',
    playerBackAction: 'Back',
    playerRewardError: 'Reward claim failed. You can keep watching.',
    playerVideoError: 'Video failed to load. Please try again later.',
    playerFavoriteLabel: 'Favorite',
    playerCurrentEpisodePrefix: 'Current ',
    playerEpisodeStatusCurrent: 'Current',
    playerEpisodeStatusWatched: 'Watched',
    playerExpandEpisodes: 'Open episode list',
    playerClose: 'Close',
    playerCommentsTitlePrefix: 'Comments ',
    playerCommentsEmpty: 'No comments yet. Be the first one here.',
    playerCommentPlaceholder: 'Write a comment…',
    playerSend: 'Send',
    listEpisodePrefix: 'Episode ',
    listPointsChangeFallback: 'Point update',
    listPointRecordSubtitle: 'Point record',
    listCurrencySymbol: '\$',
    listPointsLabel: 'pts',
    accountFrozenAvailableLabel: 'Frozen / available',
    accountWalletNoBound: 'No wallet bound',
    accountWalletAddressLabel: 'Wallet address',
    accountWalletBindAction: 'Bind wallet',
    accountWalletReplaceAction: 'Replace wallet',
    accountWalletUnbindAction: 'Unbind',
    accountVipStatusActive: 'VIP active',
    accountVipStatusInactive: 'VIP not active',
    accountVipExpiryPrefix: 'Expires ',
    accountVipUnlockAction: 'Unlock VIP',
    accountVipPriceSuffix: ' USDT / 30 days',
    accountVipNoOrders: 'No VIP orders yet.',
    vipActiveLabel: 'Status: Active',
    vipExpiryLabel: 'Expires: ',
    vipPayTitle: 'VIP Payment',
    vipPayAmount: 'Amount: ',
    vipPayAddress: 'Send USDT (TRC20) to:',
    vipPayCopy: 'Copy',
    vipPayOrderNo: 'Order: ',
    vipPayStatusPending: 'Awaiting confirmation',
    vipPayStatusConfirmed: 'Confirmed',
    vipPayStatusRejected: 'Rejected',
    vipPayHint: 'After sending USDT, admin will confirm shortly. VIP activates instantly after confirmation.',
    vipPayQrHint: 'Scan to pay',
    vipPayExpiresIn: 'Expires in',
    vipPayExpired: 'Order expired. Please create a new order.',
    vipPayUnlockEpisodePrefix: 'Unlock ',
    vipPayLockedHint: 'VIP membership is required to continue watching.',
    vipPayRefresh: 'Refresh status',
    vipPayRefreshing: 'Refreshing...',
    vipPayCreating: 'Creating order...',
    vipRecordsTitle: 'VIP Orders',
    accountWithdrawAvailableLabel: 'Available',
    accountWithdrawMinimumLabel: 'Min',
    accountWithdrawWalletRequired: 'Bind a cold wallet before withdrawal.',
    accountWithdrawPointAmountLabel: 'Point amount',
    accountWithdrawArrivalHint: 'Arrives in 1 day',
    accountWithdrawSubmitAction: 'Submit withdrawal',
    accountPasswordNewLabel: 'New password (8+ characters)',
    accountBankCardUnsupported: 'Bank card withdrawal is not supported. This entry is reserved.',
    posterEpisodeUnit: ' eps',
    bookRowFallbackSuffix: ' episodes',
);

/// Resolves the copy table for [language].
AppStrings strings(AppLanguage language) => switch (language) {
  AppLanguage.english => _english,
};

/// Every field of [strings]'s table for [language], keyed by field name.
///
/// Generated alongside the table so a contract test can compare *all* fields
/// against the golden rather than a hand-picked slice: a hand-maintained list
/// silently stops checking whatever it forgets to add.
Map<String, String> copyValues(AppLanguage language) {
  final AppStrings copy = strings(language);
  return <String, String>{
    'homeTab': copy.homeTab,
    'searchTab': copy.searchTab,
    'accountTab': copy.accountTab,
    'loginScreen': copy.loginScreen,
    'detailScreen': copy.detailScreen,
    'playerScreen': copy.playerScreen,
    'favoritesScreen': copy.favoritesScreen,
    'searchTitle': copy.searchTitle,
    'searchSubtitle': copy.searchSubtitle,
    'searchHint': copy.searchHint,
    'searchAction': copy.searchAction,
    'searchTagsTitle': copy.searchTagsTitle,
    'searchHeroEyebrow': copy.searchHeroEyebrow,
    'searchResultTitle': copy.searchResultTitle,
    'searchResultCountSuffix': copy.searchResultCountSuffix,
    'searchEmptyTitle': copy.searchEmptyTitle,
    'searchInitialHint': copy.searchInitialHint,
    'searchEmpty': copy.searchEmpty,
    'loadingDialogTitle': copy.loadingDialogTitle,
    'homeHeaderTitle': copy.homeHeaderTitle,
    'homeHeaderSubtitleSuffix': copy.homeHeaderSubtitleSuffix,
    'favoritesHeaderTitle': copy.favoritesHeaderTitle,
    'favoritesHeaderSubtitleSuffix': copy.favoritesHeaderSubtitleSuffix,
    'diagnosticsUnknownLabel': copy.diagnosticsUnknownLabel,
    'diagnosticsUnknownMessage': copy.diagnosticsUnknownMessage,
    'diagnosticsUpLabel': copy.diagnosticsUpLabel,
    'diagnosticsUpMessagePrefix': copy.diagnosticsUpMessagePrefix,
    'diagnosticsDownLabel': copy.diagnosticsDownLabel,
    'diagnosticsDownMessagePrefix': copy.diagnosticsDownMessagePrefix,
    'homeEmptyTitle': copy.homeEmptyTitle,
    'homeEmptyMessage': copy.homeEmptyMessage,
    'homeEmptyAction': copy.homeEmptyAction,
    'favoritesEmptyTitle': copy.favoritesEmptyTitle,
    'favoritesEmptyMessage': copy.favoritesEmptyMessage,
    'searchDefaultEmptyTitle': copy.searchDefaultEmptyTitle,
    'searchDefaultEmptyMessage': copy.searchDefaultEmptyMessage,
    'searchNoResultsTitle': copy.searchNoResultsTitle,
    'searchNoResultsMessagePrefix': copy.searchNoResultsMessagePrefix,
    'searchNoResultsMessageSuffix': copy.searchNoResultsMessageSuffix,
    'searchRetryAction': copy.searchRetryAction,
    'detailEmptyTitle': copy.detailEmptyTitle,
    'detailEmptyMessage': copy.detailEmptyMessage,
    'detailEmptyAction': copy.detailEmptyAction,
    'detailUnavailableTitle': copy.detailUnavailableTitle,
    'detailUnavailableMessagePrefix': copy.detailUnavailableMessagePrefix,
    'detailUnavailableMessageSuffix': copy.detailUnavailableMessageSuffix,
    'detailUnavailableAction': copy.detailUnavailableAction,
    'accountFavoritesTitle': copy.accountFavoritesTitle,
    'accountPointsTitle': copy.accountPointsTitle,
    'accountWatchHistoryTitle': copy.accountWatchHistoryTitle,
    'accountPointRecordsTitle': copy.accountPointRecordsTitle,
    'accountOrdersTitle': copy.accountOrdersTitle,
    'accountWalletTitle': copy.accountWalletTitle,
    'accountWalletNetworkHint': copy.accountWalletNetworkHint,
    'accountWithdrawTitle': copy.accountWithdrawTitle,
    'fundsTitle': copy.fundsTitle,
    'fundsSubtitle': copy.fundsSubtitle,
    'fundsTotalPointsLabel': copy.fundsTotalPointsLabel,
    'fundsTodayCreditedPointsLabel': copy.fundsTodayCreditedPointsLabel,
    'fundsFrozenPointsLabel': copy.fundsFrozenPointsLabel,
    'fundsAvailablePointsLabel': copy.fundsAvailablePointsLabel,
    'fundsWithdrawalAction': copy.fundsWithdrawalAction,
    'fundsWithdrawalSubtitle': copy.fundsWithdrawalSubtitle,
    'fundsVipRechargeAction': copy.fundsVipRechargeAction,
    'fundsVipRechargeSubtitle': copy.fundsVipRechargeSubtitle,
    'fundsLoginRequired': copy.fundsLoginRequired,
    'accountChangePasswordTitle': copy.accountChangePasswordTitle,
    'accountBankCardTitle': copy.accountBankCardTitle,
    'authContinue': copy.authContinue,
    'accountWithdrawalsTitle': copy.accountWithdrawalsTitle,
    'accountWithdrawalRecordsTitle': copy.accountWithdrawalRecordsTitle,
    'accountWithdrawalRecordsEmpty': copy.accountWithdrawalRecordsEmpty,
    'accountSignOutTitle': copy.accountSignOutTitle,
    'accountContactSupportTitle': copy.accountContactSupportTitle,
    'accountContactSupportEmail': copy.accountContactSupportEmail,
    'accountContactSupportClose': copy.accountContactSupportClose,
    'accountGuestSignIn': copy.accountGuestSignIn,
    'accountGuestRegister': copy.accountGuestRegister,
    'accountGuestSignInSubtitle': copy.accountGuestSignInSubtitle,
    'accountGuestRegisterSubtitle': copy.accountGuestRegisterSubtitle,
    'accountFavoritesSubtitle': copy.accountFavoritesSubtitle,
    'accountPointsSuffix': copy.accountPointsSuffix,
    'accountRecentCountPrefix': copy.accountRecentCountPrefix,
    'accountRecordsSuffix': copy.accountRecordsSuffix,
    'accountOrdersReserved': copy.accountOrdersReserved,
    'accountRecentWatchTitle': copy.accountRecentWatchTitle,
    'accountRecentWatchSubtitle': copy.accountRecentWatchSubtitle,
    'accountUserFallback': copy.accountUserFallback,
    'accountGuestUser': copy.accountGuestUser,
    'accountLoggedInStatus': copy.accountLoggedInStatus,
    'accountGuestStatus': copy.accountGuestStatus,
    'accountGuestPill': copy.accountGuestPill,
    'accountGuestRewardHint': copy.accountGuestRewardHint,
    'accountSignOutSubtitle': copy.accountSignOutSubtitle,
    'accountSignInSuccessHint': copy.accountSignInSuccessHint,
    'authLoginTitle': copy.authLoginTitle,
    'authLoginSubtitle': copy.authLoginSubtitle,
    'authRegisterTitle': copy.authRegisterTitle,
    'authRegisterSubtitle': copy.authRegisterSubtitle,
    'authBottomSheetPlaybackSubtitle': copy.authBottomSheetPlaybackSubtitle,
    'authBottomSheetAccountSubtitle': copy.authBottomSheetAccountSubtitle,
    'authBrandSubtitle': copy.authBrandSubtitle,
    'authClose': copy.authClose,
    'authUsernameLabel': copy.authUsernameLabel,
    'authPasswordLabel': copy.authPasswordLabel,
    'authLoginLoading': copy.authLoginLoading,
    'authLoginAction': copy.authLoginAction,
    'authLoginSecondary': copy.authLoginSecondary,
    'authRegisterAction': copy.authRegisterAction,
    'authRegisterSecondary': copy.authRegisterSecondary,
    'authCaptchaAnswerLabel': copy.authCaptchaAnswerLabel,
    'authCaptchaRefresh': copy.authCaptchaRefresh,
    'authCaptchaLoading': copy.authCaptchaLoading,
    'authConfirmPasswordLabel': copy.authConfirmPasswordLabel,
    'authPromptPlayback': copy.authPromptPlayback,
    'authPromptAccount': copy.authPromptAccount,
    'rememberPassword': copy.rememberPassword,
    'playerEpisodePrefix': copy.playerEpisodePrefix,
    'playerEpisodeUnit': copy.playerEpisodeUnit,
    'playerPlayAction': copy.playerPlayAction,
    'playerLoadingPrefix': copy.playerLoadingPrefix,
    'playerLoadingSuffix': copy.playerLoadingSuffix,
    'playerEpisodeSelectorPrefix': copy.playerEpisodeSelectorPrefix,
    'playerEpisodeSelectorCompleted': copy.playerEpisodeSelectorCompleted,
    'playerEpisodeSelectorTotalPrefix': copy.playerEpisodeSelectorTotalPrefix,
    'playerEpisodeSelectorTotalSuffix': copy.playerEpisodeSelectorTotalSuffix,
    'playerRefreshAction': copy.playerRefreshAction,
    'playerRetryAction': copy.playerRetryAction,
    'playerNextEpisodeAction': copy.playerNextEpisodeAction,
    'playerBackAction': copy.playerBackAction,
    'playerRewardError': copy.playerRewardError,
    'playerVideoError': copy.playerVideoError,
    'playerFavoriteLabel': copy.playerFavoriteLabel,
    'playerCurrentEpisodePrefix': copy.playerCurrentEpisodePrefix,
    'playerEpisodeStatusCurrent': copy.playerEpisodeStatusCurrent,
    'playerEpisodeStatusWatched': copy.playerEpisodeStatusWatched,
    'playerExpandEpisodes': copy.playerExpandEpisodes,
    'playerClose': copy.playerClose,
    'playerCommentsTitlePrefix': copy.playerCommentsTitlePrefix,
    'playerCommentsEmpty': copy.playerCommentsEmpty,
    'playerCommentPlaceholder': copy.playerCommentPlaceholder,
    'playerSend': copy.playerSend,
    'listEpisodePrefix': copy.listEpisodePrefix,
    'listPointsChangeFallback': copy.listPointsChangeFallback,
    'listPointRecordSubtitle': copy.listPointRecordSubtitle,
    'listCurrencySymbol': copy.listCurrencySymbol,
    'listPointsLabel': copy.listPointsLabel,
    'accountFrozenAvailableLabel': copy.accountFrozenAvailableLabel,
    'accountWalletNoBound': copy.accountWalletNoBound,
    'accountWalletAddressLabel': copy.accountWalletAddressLabel,
    'accountWalletBindAction': copy.accountWalletBindAction,
    'accountWalletReplaceAction': copy.accountWalletReplaceAction,
    'accountWalletUnbindAction': copy.accountWalletUnbindAction,
    'accountVipStatusActive': copy.accountVipStatusActive,
    'accountVipStatusInactive': copy.accountVipStatusInactive,
    'accountVipExpiryPrefix': copy.accountVipExpiryPrefix,
    'accountVipUnlockAction': copy.accountVipUnlockAction,
    'accountVipPriceSuffix': copy.accountVipPriceSuffix,
    'accountVipNoOrders': copy.accountVipNoOrders,
    'vipActiveLabel': copy.vipActiveLabel,
    'vipExpiryLabel': copy.vipExpiryLabel,
    'vipPayTitle': copy.vipPayTitle,
    'vipPayAmount': copy.vipPayAmount,
    'vipPayAddress': copy.vipPayAddress,
    'vipPayCopy': copy.vipPayCopy,
    'vipPayOrderNo': copy.vipPayOrderNo,
    'vipPayStatusPending': copy.vipPayStatusPending,
    'vipPayStatusConfirmed': copy.vipPayStatusConfirmed,
    'vipPayStatusRejected': copy.vipPayStatusRejected,
    'vipPayHint': copy.vipPayHint,
    'vipPayQrHint': copy.vipPayQrHint,
    'vipPayExpiresIn': copy.vipPayExpiresIn,
    'vipPayExpired': copy.vipPayExpired,
    'vipPayUnlockEpisodePrefix': copy.vipPayUnlockEpisodePrefix,
    'vipPayLockedHint': copy.vipPayLockedHint,
    'vipPayRefresh': copy.vipPayRefresh,
    'vipPayRefreshing': copy.vipPayRefreshing,
    'vipPayCreating': copy.vipPayCreating,
    'vipRecordsTitle': copy.vipRecordsTitle,
    'accountWithdrawAvailableLabel': copy.accountWithdrawAvailableLabel,
    'accountWithdrawMinimumLabel': copy.accountWithdrawMinimumLabel,
    'accountWithdrawWalletRequired': copy.accountWithdrawWalletRequired,
    'accountWithdrawPointAmountLabel': copy.accountWithdrawPointAmountLabel,
    'accountWithdrawArrivalHint': copy.accountWithdrawArrivalHint,
    'accountWithdrawSubmitAction': copy.accountWithdrawSubmitAction,
    'accountPasswordNewLabel': copy.accountPasswordNewLabel,
    'accountBankCardUnsupported': copy.accountBankCardUnsupported,
    'posterEpisodeUnit': copy.posterEpisodeUnit,
    'bookRowFallbackSuffix': copy.bookRowFallbackSuffix,
  };
}

/// A themed cluster of discovery keywords shown on the Discover screen.
@immutable
class SearchDiscoveryGroup {
  const SearchDiscoveryGroup({required this.title, required this.tags});

  final String title;
  final List<String> tags;
}

/// Every discovery keyword, flattened.
List<String> searchDiscoveryTags(AppLanguage language) => searchDiscoveryGroups(
  language,
).expand((SearchDiscoveryGroup group) => group.tags).toList();

/// The discovery keyword groups of the Discover screen.
List<SearchDiscoveryGroup> searchDiscoveryGroups(AppLanguage language) =>
    switch (language) {
      AppLanguage.english => const <SearchDiscoveryGroup>[
  SearchDiscoveryGroup(title: 'Story moods', tags: <String>['Love', 'Revenge', 'Secret', 'Family']),
  SearchDiscoveryGroup(title: 'Power roles', tags: <String>['Billionaire', 'CEO', 'Boss', 'Queen', 'Doctor']),
  SearchDiscoveryGroup(title: 'Relationships', tags: <String>['Marriage', 'Contract', 'Pregnant']),
  SearchDiscoveryGroup(title: 'Fantasy heat', tags: <String>['Mafia', 'Werewolf', 'Alpha', 'Luna']),
      ],
    };

/// Whether the Discover screen should render results before the hero/tags.
bool searchShowsResultsFirst(String query, int resultCount) =>
    query.trim().isNotEmpty || resultCount > 0;
