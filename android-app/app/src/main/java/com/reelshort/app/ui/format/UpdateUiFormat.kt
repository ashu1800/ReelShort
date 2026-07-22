package com.reelshort.app.ui.format

import com.reelshort.app.data.AppLanguage
import com.reelshort.app.update.DownloadProgress
import com.reelshort.app.update.ReleaseInfo
import com.reelshort.app.update.UpdateFailureReason
import com.reelshort.app.update.UpdateState
import java.util.Locale

internal data class UpdateStrings(
    val versionTitle: String,
    val checkForUpdates: String,
    val checking: String,
    val latestVersion: String,
    val checkFailed: String,
    val availableTitle: String,
    val publishedLabel: String,
    val sizeLabel: String,
    val updateNow: String,
    val later: String,
    val downloading: String,
    val verifying: String,
    val cancel: String,
    val retry: String,
    val permissionTitle: String,
    val permissionBody: String,
    val openSettings: String,
    val readyTitle: String,
    val readyBody: String,
    val install: String,
    val storageError: String,
    val verificationError: String,
    val invalidReleaseError: String,
    val downloadError: String,
    val installError: String,
)

internal fun updateStrings(language: AppLanguage): UpdateStrings = when (language) {
    AppLanguage.ENGLISH -> UpdateStrings(
        versionTitle = "App version",
        checkForUpdates = "Check",
        checking = "Checking…",
        latestVersion = "You're using the latest version.",
        checkFailed = "Couldn't check for updates. Check your connection and try again.",
        availableTitle = "A new version is available",
        publishedLabel = "Published",
        sizeLabel = "Download size",
        updateNow = "Update now",
        later = "Later",
        downloading = "Downloading update",
        verifying = "Verifying update…",
        cancel = "Cancel",
        retry = "Retry",
        permissionTitle = "Allow app installation",
        permissionBody = "Android needs permission to install unknown apps from ShortLink. Enable it in Settings, then return here.",
        openSettings = "Open settings",
        readyTitle = "Ready to install",
        readyBody = "The APK passed integrity, package, version, and signing checks. Android will ask you to confirm installation.",
        install = "Install",
        storageError = "There isn't enough storage space for this update.",
        verificationError = "The downloaded APK failed security verification and was deleted.",
        invalidReleaseError = "This GitHub Release is incomplete or invalid.",
        downloadError = "The update couldn't be downloaded. Try again.",
        installError = "Android couldn't open the installer. Check the installation permission and try again.",
    )
}

internal fun formatUpdateBytes(bytes: Long): String {
    val value = bytes.coerceAtLeast(0)
    return when {
        value >= 1024L * 1024L -> String.format(Locale.US, "%.1f MB", value / (1024.0 * 1024.0))
        value >= 1024L -> String.format(Locale.US, "%.1f KB", value / 1024.0)
        else -> "$value B"
    }
}

internal fun updateProgressPercent(progress: DownloadProgress): Int? {
    val total = progress.totalBytes?.takeIf { it > 0 } ?: return null
    return ((progress.downloadedBytes.coerceIn(0, total) * 100) / total).toInt()
}

internal fun shouldShowUpdateDialog(state: UpdateState): Boolean = when (state) {
    is UpdateState.Available,
    is UpdateState.Downloading,
    is UpdateState.Verifying,
    is UpdateState.PermissionRequired,
    is UpdateState.ReadyToInstall -> true
    is UpdateState.Failed -> state.release != null
    else -> false
}

internal fun UpdateState.releaseOrNull(): ReleaseInfo? = when (this) {
    is UpdateState.Available -> release
    is UpdateState.Downloading -> release
    is UpdateState.Verifying -> release
    is UpdateState.PermissionRequired -> release
    is UpdateState.ReadyToInstall -> release
    is UpdateState.Failed -> release
    else -> null
}

internal fun UpdateStrings.failureMessage(reason: UpdateFailureReason): String = when (reason) {
    UpdateFailureReason.NETWORK, UpdateFailureReason.DOWNLOAD -> downloadError
    UpdateFailureReason.INVALID_RELEASE -> invalidReleaseError
    UpdateFailureReason.STORAGE -> storageError
    UpdateFailureReason.VERIFICATION -> verificationError
    UpdateFailureReason.INSTALL -> installError
}
