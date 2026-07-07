package com.reelshort.app.ui.format

import com.reelshort.app.data.AppLanguage
import com.reelshort.app.state.AppScreen

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

internal fun accountEntryLabels(language: AppLanguage = AppLanguage.TRADITIONAL_CHINESE): List<String> {
    val copy = strings(language)
    return listOf(
        copy.accountFavoritesTitle,
        copy.accountPointsTitle,
        copy.accountWatchHistoryTitle,
        copy.accountPointRecordsTitle,
        copy.accountOrdersTitle,
        copy.accountDiagnosticsTitle,
        copy.accountSignOutTitle,
    )
}

internal enum class AccountDashboardSection {
    IDENTITY,
    PRIMARY_ACTIONS,
    CONTINUE_WATCHING,
    SECONDARY_SETTINGS,
}

internal enum class AccountDetailSheet {
    POINT_RECORDS,
    WATCH_HISTORY,
    ORDERS,
}

internal fun accountDashboardSections(): List<AccountDashboardSection> =
    listOf(
        AccountDashboardSection.IDENTITY,
        AccountDashboardSection.PRIMARY_ACTIONS,
        AccountDashboardSection.CONTINUE_WATCHING,
        AccountDashboardSection.SECONDARY_SETTINGS,
    )

internal fun accountPrimaryActionLabels(language: AppLanguage = AppLanguage.TRADITIONAL_CHINESE): List<String> {
    val copy = strings(language)
    return listOf(
        copy.accountFavoritesTitle,
        copy.accountPointsTitle,
        copy.accountWatchHistoryTitle,
        copy.accountOrdersTitle,
    )
}

internal fun accountContinueWatchingLimit(): Int = 2

internal fun accountContinueWatchingRowsAreClickable(): Boolean = true

internal fun accountContinueWatchingUsesPosterCards(): Boolean = true

internal fun accountPrimaryActionSheet(label: String, language: AppLanguage = AppLanguage.ENGLISH): AccountDetailSheet? {
    val copy = strings(language)
    return when (label) {
        copy.accountPointsTitle -> AccountDetailSheet.POINT_RECORDS
        copy.accountWatchHistoryTitle -> AccountDetailSheet.WATCH_HISTORY
        copy.accountOrdersTitle -> AccountDetailSheet.ORDERS
        else -> null
    }
}

internal fun accountDetailSheetTitle(
    sheet: AccountDetailSheet,
    language: AppLanguage = AppLanguage.TRADITIONAL_CHINESE,
): String {
    val copy = strings(language)
    return when (sheet) {
        AccountDetailSheet.POINT_RECORDS -> copy.accountPointRecordsTitle
        AccountDetailSheet.WATCH_HISTORY -> copy.accountWatchHistoryTitle
        AccountDetailSheet.ORDERS -> copy.accountOrdersTitle
    }
}

internal val AppScreen.navigationLabel: String
    get() = navigationLabel(AppLanguage.TRADITIONAL_CHINESE)

internal fun AppScreen.navigationLabel(language: AppLanguage): String =
    when (this) {
        AppScreen.HOME -> strings(language).homeTab
        AppScreen.SEARCH -> strings(language).searchTab
        AppScreen.ACCOUNT -> strings(language).accountTab
        AppScreen.LOGIN -> strings(language).loginScreen
        AppScreen.DETAIL -> strings(language).detailScreen
        AppScreen.PLAYER -> strings(language).playerScreen
        AppScreen.FAVORITES -> strings(language).favoritesScreen
    }
