package com.reelshort.app.ui.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import com.reelshort.app.data.AppLanguage
import com.reelshort.app.update.DownloadProgress
import com.reelshort.app.update.ReleaseAsset
import com.reelshort.app.update.ReleaseInfo
import com.reelshort.app.update.SemanticVersion
import com.reelshort.app.update.UpdateState
import com.reelshort.app.ui.theme.ReelShortTheme
import org.junit.Rule
import org.junit.Test

class UpdateDialogTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val release = ReleaseInfo(
        "v0.2.0",
        SemanticVersion(0, 2, 0),
        "ShortLink v0.2.0",
        "Faster and safer updates.",
        "2026-07-13T12:00:00Z",
        ReleaseAsset("ShortLink-v0.2.0.apk", "https://github.com/app.apk", "application/vnd.android.package-archive", 10_485_760),
        ReleaseAsset("ShortLink-v0.2.0.apk.sha256", "https://github.com/app.sha256", "text/plain", 64),
    )

    @Test
    fun availableReleaseHidesReleaseNotesAndShowsActions() {
        show(UpdateState.Available(release))

        composeRule.onNodeWithText("A new version is available").assertIsDisplayed()
        composeRule.onAllNodesWithText("What's new").assertCountEquals(0)
        composeRule.onAllNodesWithText("Faster and safer updates.").assertCountEquals(0)
        composeRule.onNodeWithText("Update now").assertIsDisplayed()
        composeRule.onNodeWithText("Later").assertIsDisplayed()
    }

    @Test
    fun downloadShowsProgressAndCancelAction() {
        show(UpdateState.Downloading(release, DownloadProgress(5_242_880, 10_485_760)))

        composeRule.onNodeWithText("50% · 5.0 MB / 10.0 MB").assertIsDisplayed()
        composeRule.onNodeWithText("Cancel").assertIsDisplayed()
    }

    @Test
    fun permissionStateExplainsSystemSettingsAction() {
        show(UpdateState.PermissionRequired(release, java.io.File("app.apk")))

        composeRule.onNodeWithText("Allow app installation").assertIsDisplayed()
        composeRule.onNodeWithText("Open settings").assertIsDisplayed()
    }

    private fun show(state: UpdateState) {
        composeRule.setContent {
            ReelShortTheme {
                UpdateDialog(
                    state = state,
                    language = AppLanguage.ENGLISH,
                    onDownload = {},
                    onCancelDownload = {},
                    onInstall = {},
                    onOpenSettings = {},
                    onDismiss = {},
                )
            }
        }
    }
}
