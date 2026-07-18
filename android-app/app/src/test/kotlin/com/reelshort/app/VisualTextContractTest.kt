package com.reelshort.app
import com.reelshort.app.ui.format.primaryTabs
import com.reelshort.app.ui.format.navigationLabel
import com.reelshort.app.ui.format.usesGlobalTopBar
import com.reelshort.app.ui.format.AccountDashboardSection
import com.reelshort.app.ui.format.AccountDetailSheet
import com.reelshort.app.ui.format.accountDashboardSections
import com.reelshort.app.ui.format.accountPrimaryActionLabels
import com.reelshort.app.ui.format.accountContinueWatchingLimit
import com.reelshort.app.ui.format.accountContinueWatchingRowsAreClickable
import com.reelshort.app.ui.format.accountContinueWatchingUsesPosterCards
import com.reelshort.app.ui.format.accountDetailSheetTitle
import com.reelshort.app.ui.format.accountPrimaryActionSheet
import com.reelshort.app.ui.format.appBrandName
import com.reelshort.app.ui.format.guestAccountEntryLabels
import com.reelshort.app.ui.format.authPromptTitle
import com.reelshort.app.ui.format.authBottomSheetAvoidsNavigationBar
import com.reelshort.app.ui.format.AuthSheetCopy
import com.reelshort.app.ui.format.authRegisterEnabled
import com.reelshort.app.ui.format.authSheetCopy
import com.reelshort.app.ui.format.authSinglePrimaryAction
import com.reelshort.app.ui.format.authCaptchaAnswerLabel
import com.reelshort.app.ui.format.commercialSheetAutoDismissesAfterSubmit
import com.reelshort.app.ui.format.guestAccountEntryAuthModes
import com.reelshort.app.ui.format.rememberPasswordLabel
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
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class VisualTextContractTest {

    @Test
    fun appUsesShortLinkAsVisibleBrandName() {
        assertEquals("ShortLink", appBrandName())
        assertEquals("ShortLink user", strings(AppLanguage.ENGLISH).accountUserFallback)
        assertEquals("Signed in · ShortLink", strings(AppLanguage.ENGLISH).accountLoggedInStatus)
    }

    @Test
    fun launcherIconUsesShortVideoFirstMetaphor() {
        val foreground = readSourceFile(
            "src/main/res/drawable/ic_launcher_foreground.xml",
            "app/src/main/res/drawable/ic_launcher_foreground.xml",
            "android-app/app/src/main/res/drawable/ic_launcher_foreground.xml",
        )

        assertTrue(foreground.contains("vertical_video_card"))
        assertTrue(foreground.contains("play_triangle"))
        assertTrue(foreground.contains("link_badge"))
    }

    @Test
    fun bottomNavigationUsesReadableLabels() {
        val labels = primaryTabs.map { it.navigationLabel }

        assertEquals(listOf("Home", "Discover", "Me"), labels)
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
        assertEquals(listOf("Favorites", "Points", "Watch history", "Withdraw"), accountPrimaryActionLabels())
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
        assertEquals("VIP Orders", accountDetailSheetTitle(AccountDetailSheet.ORDERS, AppLanguage.ENGLISH))
        assertEquals("Withdrawals", accountDetailSheetTitle(AccountDetailSheet.WITHDRAWALS, AppLanguage.ENGLISH))
        assertEquals("Point records", accountDetailSheetTitle(AccountDetailSheet.POINT_RECORDS))
        assertEquals("Watch history", accountDetailSheetTitle(AccountDetailSheet.WATCH_HISTORY))
        assertEquals("VIP Orders", accountDetailSheetTitle(AccountDetailSheet.ORDERS))
        assertEquals("Withdrawals", accountDetailSheetTitle(AccountDetailSheet.WITHDRAWALS))
    }

    @Test
    fun guestAccountPageShowsAuthEntryLabels() {
        assertEquals(listOf("Sign in", "Create account"), guestAccountEntryLabels())
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
        assertEquals("Sign in to keep watching", authPromptTitle(hasPendingPlayback = true))
        assertEquals("Sign in to view your account", authPromptTitle(hasPendingPlayback = false))
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
        assertEquals("Remember password", rememberPasswordLabel())
    }

    @Test
    fun authFormProvidesLocalizedRememberPasswordEntryInEnglish() {
        assertEquals("Remember password", rememberPasswordLabel(AppLanguage.ENGLISH))
    }

    @Test
    fun authFormControlsAreModeSpecific() {
        assertEquals(
            listOf("username", "password", "rememberPassword", "primary:Sign in", "secondary:New here? Create account"),
            authFormControls(AuthMode.LOGIN, AppLanguage.ENGLISH),
        )
        assertEquals(
            listOf("username", "password", "confirmPassword", "captchaImage", "captchaAnswer", "primary:Create account", "secondary:Already have access? Sign in"),
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
                subtitle = "Pick a username, set a password, and solve the captcha.",
                primaryAction = "Create account",
                secondaryAction = "Already have access? Sign in",
            ),
            authSheetCopy(AuthMode.REGISTER, hasPendingPlayback = false, language = AppLanguage.ENGLISH),
        )
    }

    @Test
    fun captchaRegisterRequiresUsernamePasswordConfirmAndCaptcha() {
        assertEquals(false, authRegisterEnabled(isLoading = false, username = "", password = "Password123", confirmPassword = "Password123", captchaAnswer = "1234", captchaLoaded = true))
        assertEquals(false, authRegisterEnabled(isLoading = false, username = "newuser", password = "short", confirmPassword = "short", captchaAnswer = "1234", captchaLoaded = true))
        assertEquals(false, authRegisterEnabled(isLoading = false, username = "newuser", password = "Password123", confirmPassword = "different", captchaAnswer = "1234", captchaLoaded = true))
        assertEquals(false, authRegisterEnabled(isLoading = false, username = "newuser", password = "Password123", confirmPassword = "Password123", captchaAnswer = "", captchaLoaded = true))
        assertEquals(false, authRegisterEnabled(isLoading = false, username = "newuser", password = "Password123", confirmPassword = "Password123", captchaAnswer = "1234", captchaLoaded = false))
        assertEquals(true, authRegisterEnabled(isLoading = false, username = "newuser", password = "Password123", confirmPassword = "Password123", captchaAnswer = "1234", captchaLoaded = true))
        assertEquals(false, authRegisterEnabled(isLoading = true, username = "newuser", password = "Password123", confirmPassword = "Password123", captchaAnswer = "1234", captchaLoaded = true))
    }

    @Test
    fun captchaAnswerLabelUsesNeutralCopy() {
        assertEquals("Captcha answer", authCaptchaAnswerLabel(AppLanguage.ENGLISH))
    }

    @Test
    fun commercialAccountSheetsKeepFormOpenAfterSubmit() {
        assertEquals(false, commercialSheetAutoDismissesAfterSubmit())
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
        assertEquals("EP 01", episodeNumberLabel(1))
        assertEquals("EP 12", episodeNumberLabel(12))
        assertEquals("EP 99", episodeNumberLabel(99))
        assertEquals("Play", episodeRowActionLabel())
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

    private fun readSourceFile(vararg candidates: String): String {
        val roots = generateSequence(Path.of(System.getProperty("user.dir")).toAbsolutePath()) { it.parent }
        roots.forEach { root ->
            candidates.forEach { candidate ->
                val path = root.resolve(candidate)
                if (Files.exists(path)) {
                    return String(Files.readAllBytes(path))
                }
            }
        }
        error("Could not find source file: ${candidates.joinToString()}")
    }
}
