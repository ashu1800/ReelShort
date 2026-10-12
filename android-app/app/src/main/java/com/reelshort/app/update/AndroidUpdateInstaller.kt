package com.reelshort.app.update

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File

class AndroidUpdateInstaller(
    private val context: Context,
) {
    fun canRequestPackageInstalls(): Boolean = context.packageManager.canRequestPackageInstalls()

    fun unknownSourcesSettingsIntent(): Intent =
        Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}"),
        )

    fun install(apkFile: File) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.update-files",
            apkFile,
        )
        val baseIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, APK_MIME_TYPE)
            clipData = ClipData.newRawUri("ShortLink update", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        // 优先显式交给系统包安装器，避免被其它 App 处理 ACTION_VIEW 抢走安装意图；
        // 设备上没有该系统包（部分定制 ROM）时回退到隐式 ACTION_VIEW。
        val explicitIntent = Intent(baseIntent).setPackage(SYSTEM_PACKAGE_INSTALLER)
        try {
            context.startActivity(explicitIntent)
        } catch (_: ActivityNotFoundException) {
            context.startActivity(baseIntent)
        }
        scheduleCacheCleanup(apkFile)
    }

    /**
     * 安装意图触发后清理 `cache/updates`：
     * - 立刻删除同目录下历史遗留的其它 APK，避免旧版本包无限堆积；
     * - 本次交付给安装器的包延迟删除，安装器需要先读取该 content URI，立即删除会让安装失败。
     */
    private fun scheduleCacheCleanup(apkFile: File) {
        val updatesDir = apkFile.parentFile ?: return
        updatesDir.listFiles()?.forEach { file ->
            if (file.isFile && file != apkFile) {
                file.delete()
            }
        }
        Handler(Looper.getMainLooper()).postDelayed(
            { apkFile.delete() },
            INSTALLED_APK_CLEANUP_DELAY_MILLIS,
        )
    }

    companion object {
        private const val APK_MIME_TYPE = "application/vnd.android.package-archive"

        /** AOSP 与多数国产 ROM 的系统安装器包名。 */
        private const val SYSTEM_PACKAGE_INSTALLER = "com.android.packageinstaller"

        /** 安装器读取 APK 后才允许删除：60s 足够覆盖用户确认安装的时间窗。 */
        private const val INSTALLED_APK_CLEANUP_DELAY_MILLIS = 60_000L
    }
}
