/// Navigation and account-dashboard contract.
///
/// Port of `android-app/app/src/main/java/com/reelshort/app/ui/format/NavigationContract.kt`.
library;

import '../../data/models/app_language.dart';
import '../../state/app_ui_state.dart';
import 'app_strings.dart';

/// The four bottom-bar destinations, in order.
const List<AppScreen> primaryTabs = <AppScreen>[
  AppScreen.home,
  AppScreen.search,
  AppScreen.funds,
  AppScreen.account,
];

/// The shell deliberately has no global top app bar.
bool usesGlobalTopBar(AppScreen screen) => false;

/// How blocking loads are surfaced.
///
/// The Kotlin app uses a centred modal dialog. The redesign replaces it with
/// skeletons plus an inline progress bar, which is an intentional deviation
/// recorded in `docs/parity-matrix.md`; the enum is kept so the contract (and its
/// test) remains explicit about what the original did.
enum LoadingFeedbackMode { centerDialog }

LoadingFeedbackMode loadingFeedbackMode() => LoadingFeedbackMode.centerDialog;

enum TabRefreshMode { cacheFirstBackgroundRefresh, localSwitch }

Map<AppScreen, TabRefreshMode> primaryTabRefreshModes() =>
    const <AppScreen, TabRefreshMode>{
      AppScreen.home: TabRefreshMode.cacheFirstBackgroundRefresh,
      AppScreen.search: TabRefreshMode.localSwitch,
      AppScreen.account: TabRefreshMode.cacheFirstBackgroundRefresh,
    };

enum AccountDashboardSection {
  identity,
  primaryActions,
  continueWatching,
  secondarySettings,
}

enum AccountDetailSheet { pointRecords, watchHistory, orders, withdrawals }

List<AccountDashboardSection> accountDashboardSections() =>
    const <AccountDashboardSection>[
      AccountDashboardSection.identity,
      AccountDashboardSection.primaryActions,
      AccountDashboardSection.continueWatching,
      AccountDashboardSection.secondarySettings,
    ];

List<String> accountPrimaryActionLabels([
  AppLanguage language = AppLanguage.defaultLanguage,
]) {
  final AppStrings copy = strings(language);
  return <String>[
    copy.accountFavoritesTitle,
    copy.accountPointsTitle,
    copy.accountWatchHistoryTitle,
    copy.accountWithdrawTitle,
  ];
}

int accountContinueWatchingLimit() => 2;

bool accountContinueWatchingRowsAreClickable() => true;

bool accountContinueWatchingUsesPosterCards() => true;

AccountDetailSheet? accountPrimaryActionSheet(
  String label, [
  AppLanguage language = AppLanguage.defaultLanguage,
]) {
  final AppStrings copy = strings(language);
  if (label == copy.accountPointsTitle) {
    return AccountDetailSheet.pointRecords;
  }
  if (label == copy.accountWatchHistoryTitle) {
    return AccountDetailSheet.watchHistory;
  }
  if (label == copy.accountOrdersTitle) {
    return AccountDetailSheet.orders;
  }
  return null;
}

String accountDetailSheetTitle(
  AccountDetailSheet sheet, [
  AppLanguage language = AppLanguage.defaultLanguage,
]) {
  final AppStrings copy = strings(language);
  return switch (sheet) {
    AccountDetailSheet.pointRecords => copy.accountPointRecordsTitle,
    AccountDetailSheet.watchHistory => copy.accountWatchHistoryTitle,
    AccountDetailSheet.orders => copy.accountOrdersTitle,
    AccountDetailSheet.withdrawals => copy.accountWithdrawalsTitle,
  };
}

String accountEmptyDetailMessage(
  AccountDetailSheet sheet, [
  AppLanguage language = AppLanguage.defaultLanguage,
]) {
  final AppStrings copy = strings(language);
  return switch (sheet) {
    // The Kotlin source reuses the record title here; kept verbatim.
    AccountDetailSheet.pointRecords => copy.accountPointRecordsTitle,
    AccountDetailSheet.watchHistory => copy.accountRecentWatchSubtitle,
    AccountDetailSheet.orders => copy.accountOrdersReserved,
    AccountDetailSheet.withdrawals => copy.accountWithdrawalRecordsEmpty,
  };
}

String navigationLabel(
  AppScreen screen, [
  AppLanguage language = AppLanguage.defaultLanguage,
]) {
  final AppStrings copy = strings(language);
  return switch (screen) {
    AppScreen.home => copy.homeTab,
    AppScreen.search => copy.searchTab,
    AppScreen.account => copy.accountTab,
    AppScreen.login => copy.loginScreen,
    AppScreen.detail => copy.detailScreen,
    AppScreen.player => copy.playerScreen,
    AppScreen.favorites => copy.favoritesScreen,
    AppScreen.funds => copy.fundsTitle,
    AppScreen.withdrawal => copy.accountWithdrawTitle,
    AppScreen.vipRecharge => copy.vipPayTitle,
  };
}
