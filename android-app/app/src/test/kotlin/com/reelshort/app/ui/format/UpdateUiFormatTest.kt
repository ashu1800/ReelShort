package com.reelshort.app.ui.format

import com.reelshort.app.data.AppLanguage
import com.reelshort.app.update.DownloadProgress
import com.reelshort.app.update.ReleaseAsset
import com.reelshort.app.update.ReleaseInfo
import com.reelshort.app.update.SemanticVersion
import com.reelshort.app.update.UpdateFailureReason
import com.reelshort.app.update.UpdateState
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UpdateUiFormatTest {
    private val release = ReleaseInfo(
        "v0.2.0",
        SemanticVersion(0, 2, 0),
        "ShortLink v0.2.0",
        "Notes",
        "2026-07-13T12:00:00Z",
        ReleaseAsset("ShortLink-v0.2.0.apk", "https://github.com/app.apk", "application/vnd.android.package-archive", 10_485_760),
        ReleaseAsset("ShortLink-v0.2.0.apk.sha256", "https://github.com/app.sha256", "text/plain", 64),
    )

    @Test
    fun formatsByteCountsAndProgress() {
        assertEquals("1.5 KB", formatUpdateBytes(1_536))
        assertEquals("10.0 MB", formatUpdateBytes(10_485_760))
        assertEquals(25, updateProgressPercent(DownloadProgress(25, 100)))
        assertNull(updateProgressPercent(DownloadProgress(25, null)))
        assertNull(updateProgressPercent(DownloadProgress(25, 0)))
    }

    @Test
    fun exposesEnglishAndTraditionalChineseUpdateCopy() {
        assertEquals("App version", updateStrings(AppLanguage.ENGLISH).versionTitle)
        assertEquals("Check", updateStrings(AppLanguage.ENGLISH).checkForUpdates)
        assertEquals("應用程式版本", updateStrings(AppLanguage.TRADITIONAL_CHINESE).versionTitle)
        assertTrue(updateStrings(AppLanguage.ENGLISH).permissionBody.contains("unknown apps"))
    }

    @Test
    fun dialogOnlyAppearsForActionableReleaseStates() {
        assertFalse(shouldShowUpdateDialog(UpdateState.Idle))
        assertFalse(shouldShowUpdateDialog(UpdateState.Checking(manual = true)))
        assertFalse(shouldShowUpdateDialog(UpdateState.UpToDate(SemanticVersion(0, 2, 0))))
        assertTrue(shouldShowUpdateDialog(UpdateState.Available(release)))
        assertTrue(shouldShowUpdateDialog(UpdateState.Downloading(release, DownloadProgress(1, 2))))
        assertTrue(shouldShowUpdateDialog(UpdateState.Verifying(release)))
        assertTrue(shouldShowUpdateDialog(UpdateState.ReadyToInstall(release, File("app.apk"))))
        assertTrue(shouldShowUpdateDialog(UpdateState.PermissionRequired(release, File("app.apk"))))
        assertTrue(shouldShowUpdateDialog(UpdateState.Failed(UpdateFailureReason.DOWNLOAD, true, release)))
        assertFalse(shouldShowUpdateDialog(UpdateState.Failed(UpdateFailureReason.NETWORK, true)))
    }
}
