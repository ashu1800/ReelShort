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

internal fun accountEntryLabels(): List<String> =
    listOf("我的收藏", "积分余额", "观看记录", "积分流水", "充值订单", "开发诊断", "退出登录")

internal val AppScreen.navigationLabel: String
    get() = navigationLabel(AppLanguage.TRADITIONAL_CHINESE)

internal fun AppScreen.navigationLabel(language: AppLanguage): String =
    when (this) {
        AppScreen.HOME -> strings(language).homeTab
        AppScreen.SEARCH -> strings(language).searchTab
        AppScreen.ACCOUNT -> strings(language).accountTab
        AppScreen.LOGIN -> "登录"
        AppScreen.DETAIL -> "详情"
        AppScreen.PLAYER -> "播放"
        AppScreen.FAVORITES -> "我的收藏"
    }
