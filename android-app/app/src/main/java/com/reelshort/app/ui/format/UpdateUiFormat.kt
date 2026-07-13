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
    val releaseNotes: String,
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
        releaseNotes = "What's new",
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
    AppLanguage.TRADITIONAL_CHINESE -> UpdateStrings(
        versionTitle = "應用程式版本",
        checkForUpdates = "檢查",
        checking = "正在檢查…",
        latestVersion = "目前已是最新版本。",
        checkFailed = "無法檢查更新，請確認網路連線後重試。",
        availableTitle = "發現新版本",
        publishedLabel = "發布時間",
        sizeLabel = "下載大小",
        releaseNotes = "更新內容",
        updateNow = "立即更新",
        later = "稍後",
        downloading = "正在下載更新",
        verifying = "正在驗證更新…",
        cancel = "取消",
        retry = "重試",
        permissionTitle = "允許安裝應用程式",
        permissionBody = "Android 需要允許 ShortLink 安裝未知應用程式。請在設定中開啟權限後返回此處。",
        openSettings = "開啟設定",
        readyTitle = "可以安裝",
        readyBody = "APK 已通過完整性、套件名稱、版本與簽章驗證。Android 將要求你確認安裝。",
        install = "安裝",
        storageError = "儲存空間不足，無法下載此更新。",
        verificationError = "下載的 APK 未通過安全驗證，已將檔案刪除。",
        invalidReleaseError = "此 GitHub Release 不完整或格式無效。",
        downloadError = "無法下載更新，請重試。",
        installError = "Android 無法開啟安裝程式，請確認安裝權限後重試。",
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
