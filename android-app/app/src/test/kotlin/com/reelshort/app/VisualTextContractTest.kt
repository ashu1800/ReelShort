package com.reelshort.app
import com.reelshort.app.ui.format.primaryTabs
import com.reelshort.app.ui.format.navigationLabel
import com.reelshort.app.ui.format.usesGlobalTopBar
import com.reelshort.app.ui.format.AccountDashboardSection
import com.reelshort.app.ui.format.AccountDetailSheet
import com.reelshort.app.ui.format.accountEntryLabels
import com.reelshort.app.ui.format.accountDashboardSections
import com.reelshort.app.ui.format.accountPrimaryActionLabels
import com.reelshort.app.ui.format.accountContinueWatchingLimit
import com.reelshort.app.ui.format.accountContinueWatchingRowsAreClickable
import com.reelshort.app.ui.format.accountContinueWatchingUsesPosterCards
import com.reelshort.app.ui.format.accountDetailSheetTitle
import com.reelshort.app.ui.format.accountPrimaryActionSheet
import com.reelshort.app.ui.format.guestAccountEntryLabels
import com.reelshort.app.ui.format.authPromptTitle
import com.reelshort.app.ui.format.authBottomSheetAvoidsNavigationBar
import com.reelshort.app.ui.format.AuthSheetCopy
import com.reelshort.app.ui.format.authRegisterEnabled
import com.reelshort.app.ui.format.authSmsSendEnabled
import com.reelshort.app.ui.format.authSmsCountdownStartsAfterSuccessfulSend
import com.reelshort.app.ui.format.authSheetCopy
import com.reelshort.app.ui.format.authSinglePrimaryAction
import com.reelshort.app.ui.format.authVerificationCodeLabel
import com.reelshort.app.ui.format.commercialSheetAutoDismissesAfterSubmit
import com.reelshort.app.ui.format.guestAccountEntryAuthModes
import com.reelshort.app.ui.format.rememberPasswordLabel
import com.reelshort.app.ui.format.supportedPhoneCountryCodes
import com.reelshort.app.ui.format.smsVerificationSeconds
import com.reelshort.app.ui.format.LoadingFeedbackMode
import com.reelshort.app.ui.format.loadingFeedbackMode
import com.reelshort.app.ui.format.TabRefreshMode
import com.reelshort.app.ui.format.primaryTabRefreshModes
import com.reelshort.app.ui.format.episodeNumberLabel
import com.reelshort.app.ui.format.episodeRowActionLabel
import com.reelshort.app.ui.format.episodeSubtitle
import com.reelshort.app.ui.format.playerSurfaceAspectRatio
import com.reelshort.app.ui.format.playerStartsAutomatically
import com.reelshort.app.ui.format.playerSecondaryActionLabels
import com.reelshort.app.ui.format.strings
import com.reelshort.app.data.AppLanguage
import com.reelshort.app.state.AuthMode
import com.reelshort.app.ui.screens.auth.authFormControls

import com.reelshort.app.state.AppScreen
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class VisualTextContractTest {

    @Test
    fun bottomNavigationUsesReadableLabels() {
        val labels = primaryTabs.map { it.navigationLabel }

        assertEquals(listOf("首頁", "探索", "我的"), labels)
        assertTrue(labels.all { it.length >= 2 })
    }

    @Test
    fun bottomNavigationUsesDefaultEnglishLabelsWhenLanguageIsEnglish() {
        val labels = primaryTabs.map { it.navigationLabel(AppLanguage.ENGLISH) }

        assertEquals(listOf("Home", "Discover", "Me"), labels)
    }

    @Test
    fun allPrimaryScreenLabelsAreLocalizedForEnglish() {
        assertEquals("Sign in", AppScreen.LOGIN.navigationLabel(AppLanguage.ENGLISH))
        assertEquals("Details", AppScreen.DETAIL.navigationLabel(AppLanguage.ENGLISH))
        assertEquals("Player", AppScreen.PLAYER.navigationLabel(AppLanguage.ENGLISH))
        assertEquals("Favorites", AppScreen.FAVORITES.navigationLabel(AppLanguage.ENGLISH))
    }

    @Test
    fun primaryTabsDoNotUseGlobalTopBar() {
        val topBarScreens = primaryTabs.filter { it.usesGlobalTopBar }

        assertEquals(emptyList(), topBarScreens)
    }

    @Test
    fun accountPageUsesMeStyleEntryLabels() {
        assertEquals(
            listOf("我的收藏", "積分餘額", "觀看記錄", "積分流水", "充值訂單", "冷錢包", "提現", "積分交易", "交易記錄", "修改密碼", "銀行卡", "開發診斷", "退出登入"),
            accountEntryLabels(),
        )
    }

    @Test
    fun accountPageUsesLocalizedEntryLabelsInEnglish() {
        assertEquals(
            listOf("Favorites", "Points", "Watch history", "Point records", "Orders", "Cold wallet", "Withdraw", "Transfer points", "Transfer records", "Change password", "Bank card", "Diagnostics", "Sign out"),
            accountEntryLabels(AppLanguage.ENGLISH),
        )
    }

    @Test
    fun accountDashboardUsesCommercialMePageHierarchy() {
        assertEquals(
            listOf(
                AccountDashboardSection.IDENTITY,
                AccountDashboardSection.PRIMARY_ACTIONS,
                AccountDashboardSection.CONTINUE_WATCHING,
                AccountDashboardSection.SECONDARY_SETTINGS,
            ),
            accountDashboardSections(),
        )
    }

    @Test
    fun accountDashboardPromotesFourPrimaryActions() {
        assertEquals(
            listOf("Favorites", "Points", "Watch history", "Withdraw"),
            accountPrimaryActionLabels(AppLanguage.ENGLISH),
        )
        assertEquals(
            listOf("我的收藏", "積分餘額", "觀看記錄", "提現"),
            accountPrimaryActionLabels(),
        )
    }

    @Test
    fun accountDashboardUsesCompactContinueWatchingPreview() {
        assertEquals(2, accountContinueWatchingLimit())
    }

    @Test
    fun accountDashboardContinueWatchingRowsAreClickable() {
        assertEquals(true, accountContinueWatchingRowsAreClickable())
    }

    @Test
    fun accountDashboardContinueWatchingUsesPosterCards() {
        assertEquals(true, accountContinueWatchingUsesPosterCards())
    }

    @Test
    fun accountDashboardPrimaryActionsExposeDetailSheets() {
        assertEquals(null, accountPrimaryActionSheet("Favorites"))
        assertEquals(AccountDetailSheet.POINT_RECORDS, accountPrimaryActionSheet("Points"))
        assertEquals(AccountDetailSheet.WATCH_HISTORY, accountPrimaryActionSheet("Watch history"))
        assertEquals(AccountDetailSheet.WITHDRAWALS, accountPrimaryActionSheet("Withdraw"))
    }

    @Test
    fun accountDetailSheetsHaveLocalizedTitles() {
        assertEquals("Point records", accountDetailSheetTitle(AccountDetailSheet.POINT_RECORDS, AppLanguage.ENGLISH))
        assertEquals("Watch history", accountDetailSheetTitle(AccountDetailSheet.WATCH_HISTORY, AppLanguage.ENGLISH))
        assertEquals("Orders", accountDetailSheetTitle(AccountDetailSheet.ORDERS, AppLanguage.ENGLISH))
        assertEquals("Withdrawals", accountDetailSheetTitle(AccountDetailSheet.WITHDRAWALS, AppLanguage.ENGLISH))
        assertEquals("Transfer records", accountDetailSheetTitle(AccountDetailSheet.TRANSFERS, AppLanguage.ENGLISH))
        assertEquals("積分流水", accountDetailSheetTitle(AccountDetailSheet.POINT_RECORDS))
        assertEquals("觀看記錄", accountDetailSheetTitle(AccountDetailSheet.WATCH_HISTORY))
        assertEquals("充值訂單", accountDetailSheetTitle(AccountDetailSheet.ORDERS))
        assertEquals("提現記錄", accountDetailSheetTitle(AccountDetailSheet.WITHDRAWALS))
        assertEquals("交易記錄", accountDetailSheetTitle(AccountDetailSheet.TRANSFERS))
    }

    @Test
    fun guestAccountPageShowsAuthEntryLabels() {
        assertEquals(listOf("登入", "註冊"), guestAccountEntryLabels())
    }

    @Test
    fun guestAccountPageShowsLocalizedAuthEntryLabelsInEnglish() {
        assertEquals(listOf("Sign in", "Create account"), guestAccountEntryLabels(AppLanguage.ENGLISH))
    }

    @Test
    fun guestAccountEntriesOpenMatchingAuthModes() {
        assertEquals(listOf(AuthMode.LOGIN, AuthMode.REGISTER), guestAccountEntryAuthModes())
    }

    @Test
    fun authPromptTitleMatchesTriggerContext() {
        assertEquals("登入後繼續播放", authPromptTitle(hasPendingPlayback = true))
        assertEquals("登入後查看帳戶", authPromptTitle(hasPendingPlayback = false))
    }

    @Test
    fun authPromptTitleIsLocalizedForEnglish() {
        assertEquals("Sign in to keep watching", authPromptTitle(hasPendingPlayback = true, language = AppLanguage.ENGLISH))
        assertEquals("Sign in to view your account", authPromptTitle(hasPendingPlayback = false, language = AppLanguage.ENGLISH))
    }

    @Test
    fun authBottomSheetKeepsActionsAboveSystemNavigation() {
        assertEquals(true, authBottomSheetAvoidsNavigationBar())
    }

    @Test
    fun authBottomSheetUsesOnePrimaryActionAtATime() {
        assertEquals(true, authSinglePrimaryAction())
    }

    @Test
    fun authFormProvidesRememberPasswordEntry() {
        assertEquals("記住密碼", rememberPasswordLabel())
    }

    @Test
    fun authFormProvidesLocalizedRememberPasswordEntryInEnglish() {
        assertEquals("Remember password", rememberPasswordLabel(AppLanguage.ENGLISH))
    }

    @Test
    fun authFormControlsAreModeSpecific() {
        assertEquals(
            listOf("phone", "password", "rememberPassword", "primary:Sign in", "secondary:New here? Create account"),
            authFormControls(AuthMode.LOGIN, AppLanguage.ENGLISH),
        )
        assertEquals(
            listOf("phone", "password", "verificationCode", "sendCode", "primary:Create account", "secondary:Already have access? Sign in"),
            authFormControls(AuthMode.REGISTER, AppLanguage.ENGLISH),
        )
    }

    @Test
    fun authBottomSheetCopyFollowsCurrentMode() {
        assertEquals(
            AuthSheetCopy(
                title = "Sign in to view your account",
                subtitle = "Sign in to view points, watch history, and orders.",
                primaryAction = "Sign in",
                secondaryAction = "New here? Create account",
            ),
            authSheetCopy(AuthMode.LOGIN, hasPendingPlayback = false, language = AppLanguage.ENGLISH),
        )
        assertEquals(
            AuthSheetCopy(
                title = "Create your account",
                subtitle = "Use a non-mainland phone number. Verification is simulated for this build.",
                primaryAction = "Create account",
                secondaryAction = "Already have access? Sign in",
            ),
            authSheetCopy(AuthMode.REGISTER, hasPendingPlayback = false, language = AppLanguage.ENGLISH),
        )
    }

    @Test
    fun phoneAuthUsesSupportedNonMainlandCountryCodesAndMockSmsTimeout() {
        val codes = supportedPhoneCountryCodes()

        assertEquals("+1", codes.first().code)
        assertTrue(codes.none { it.code == "+86" })
        assertTrue(codes.map { it.code }.containsAll(listOf("+44", "+61", "+852", "+886", "+81", "+82")))
        assertEquals(120, smsVerificationSeconds())
    }

    @Test
    fun phoneRegisterRequiresEnteredVerificationCode() {
        assertEquals(false, authRegisterEnabled(isLoading = false, phoneNumber = "4155550101", password = "Password123", verificationCode = ""))
        assertEquals(false, authRegisterEnabled(isLoading = false, phoneNumber = "4155550101", password = "Password123", verificationCode = "12345"))
        assertEquals(true, authRegisterEnabled(isLoading = false, phoneNumber = "4155550101", password = "Password123", verificationCode = "000000"))
        assertEquals(false, authRegisterEnabled(isLoading = true, phoneNumber = "4155550101", password = "Password123", verificationCode = "000000"))
    }

    @Test
    fun phoneRegisterSmsRequiresPhoneAndValidPassword() {
        assertEquals(false, authSmsSendEnabled(isLoading = false, smsCountdown = 0, phoneNumber = "", password = "Password123"))
        assertEquals(false, authSmsSendEnabled(isLoading = false, smsCountdown = 0, phoneNumber = "4155550101", password = "short"))
        assertEquals(false, authSmsSendEnabled(isLoading = false, smsCountdown = 120, phoneNumber = "4155550101", password = "Password123"))
        assertEquals(true, authSmsSendEnabled(isLoading = false, smsCountdown = 0, phoneNumber = "4155550101", password = "Password123"))
    }

    @Test
    fun authVerificationCodeLabelDoesNotExposeMockCodeAsLabel() {
        assertEquals("Verification code", authVerificationCodeLabel(AppLanguage.ENGLISH))
        assertEquals("驗證碼", authVerificationCodeLabel(AppLanguage.TRADITIONAL_CHINESE))
    }

    @Test
    fun authSmsCountdownStartsOnlyAfterSuccessfulSendContract() {
        assertEquals(true, authSmsCountdownStartsAfterSuccessfulSend())
    }

    @Test
    fun commercialAccountSheetsKeepFormOpenAfterSubmit() {
        assertEquals(false, commercialSheetAutoDismissesAfterSubmit())
    }

    @Test
    fun commercialAccountActionsHaveLocalizedCopy() {
        assertEquals("Received", strings(AppLanguage.ENGLISH).accountTransferInLabel)
        assertEquals("Sent", strings(AppLanguage.ENGLISH).accountTransferOutLabel)
        assertEquals("轉入", strings(AppLanguage.TRADITIONAL_CHINESE).accountTransferInLabel)
        assertEquals("轉出", strings(AppLanguage.TRADITIONAL_CHINESE).accountTransferOutLabel)
    }

    @Test
    fun loadingFeedbackUsesCenteredDialog() {
        assertEquals(LoadingFeedbackMode.CENTER_DIALOG, loadingFeedbackMode())
    }

    @Test
    fun bottomNavigationUsesNonBlockingRefreshForCachedTabs() {
        assertEquals(
            mapOf(
                AppScreen.HOME to TabRefreshMode.CACHE_FIRST_BACKGROUND_REFRESH,
                AppScreen.SEARCH to TabRefreshMode.LOCAL_SWITCH,
                AppScreen.ACCOUNT to TabRefreshMode.CACHE_FIRST_BACKGROUND_REFRESH,
            ),
            primaryTabRefreshModes(),
        )
    }

    @Test
    fun episodeRowsUseNumberOnlyDisplayWithoutDuration() {
        assertEquals("第 01 集", episodeNumberLabel(1))
        assertEquals("第 12 集", episodeNumberLabel(12))
        assertEquals("第 99 集", episodeNumberLabel(99))
        assertEquals("播放", episodeRowActionLabel())
    }

    @Test
    fun episodeRowsUseEpisodeDescriptionWithBookFallback() {
        assertEquals(
            "A deal goes wrong.",
            episodeSubtitle(episodeDescription = "A deal goes wrong.", bookDescription = "Book intro."),
        )
        assertEquals(
            "Book intro.",
            episodeSubtitle(episodeDescription = "", bookDescription = "Book intro."),
        )
        assertEquals("", episodeSubtitle(episodeDescription = " ", bookDescription = " "))
    }

    @Test
    fun playerUsesPortraitFirstAutoplayContract() {
        assertEquals(9f / 16f, playerSurfaceAspectRatio())
        assertEquals(true, playerStartsAutomatically())
    }

    @Test
    fun playerSecondaryActionsAndSharedLoadingCopyAreLocalizedForEnglish() {
        assertEquals(listOf("Refresh stream"), playerSecondaryActionLabels(AppLanguage.ENGLISH))
        assertEquals("Loading", strings(AppLanguage.ENGLISH).loadingDialogTitle)
    }
}
