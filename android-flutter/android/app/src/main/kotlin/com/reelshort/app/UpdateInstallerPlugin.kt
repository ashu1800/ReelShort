package com.reelshort.app

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.core.content.FileProvider
import io.flutter.plugin.common.BinaryMessenger
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.Result
import java.io.File

/**
 * Flutter bridge for the self-update install step.
 *
 * Platform side of
 * `lib/update/android_update_installer.dart`; reproduces the behaviour of
 * `android-app/app/src/main/java/com/reelshort/app/update/AndroidUpdateInstaller.kt`
 * one method at a time:
 *
 *  * `canRequestPackageInstalls` — `PackageManager.canRequestPackageInstalls()`;
 *  * `requestInstallPermission` — `ACTION_MANAGE_UNKNOWN_APP_SOURCES` with
 *    `package:<applicationId>` data and `FLAG_ACTIVITY_NEW_TASK`;
 *  * `install` — `ACTION_VIEW` on the `FileProvider` URI of the staged APK
 *    (authority `<applicationId>.update-files`, cache path `updates/`),
 *    MIME `application/vnd.android.package-archive`, flags
 *    `FLAG_GRANT_READ_URI_PERMISSION | FLAG_ACTIVITY_NEW_TASK`, explicit
 *    `com.android.packageinstaller` first with an implicit fallback. The call
 *    answers `true` once an installer activity started and `false` (never
 *    throws) when none resolved.
 *
 * After the installer starts, cache cleanup mirrors the Kotlin original:
 * sibling files inside the APK's directory are deleted immediately and the
 * delivered APK is deleted after 60 000 ms, once the installer has read the
 * content URI.
 *
 * # Registration
 *
 * The plugin is self-contained; `MainActivity.kt` needs exactly one line inside
 * `configureFlutterEngine`:
 *
 * ```kotlin
 * override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
 *     super.configureFlutterEngine(flutterEngine)
 *     UpdateInstallerPlugin.registerWith(this, flutterEngine.dartExecutor.binaryMessenger)
 * }
 * ```
 *
 * (The one-line `UpdateInstallerPlugin.registerWith(...)` call is the required
 * registration; this file must not import anything else from the app.)
 */
object UpdateInstallerPlugin {
    /** Channel name shared with [com.reelshort.app.update] Dart installer. */
    const val CHANNEL_NAME: String = "com.reelshort.app/update_installer"

    private const val APK_MIME_TYPE: String = "application/vnd.android.package-archive"

    /** AOSP and most vendor ROMs ship the system installer under this package. */
    private const val SYSTEM_PACKAGE_INSTALLER: String = "com.android.packageinstaller"

    /** The installer reads the APK before deletion; 60 s covers the user confirm window. */
    private const val INSTALLED_APK_CLEANUP_DELAY_MILLIS: Long = 60_000L

    /** Installs the method call handler on [messenger] for [activity]. */
    fun registerWith(activity: Activity, messenger: BinaryMessenger) {
        val channel = MethodChannel(messenger, CHANNEL_NAME)
        channel.setMethodCallHandler { call, result ->
            when (call.method) {
                "canRequestPackageInstalls" -> result.success(
                    activity.packageManager.canRequestPackageInstalls(),
                )

                "requestInstallPermission" -> requestInstallPermission(activity, result)

                "install" -> install(activity, call.arguments, result)

                else -> result.notImplemented()
            }
        }
    }

    /** Opens the "install unknown apps" screen for this package. */
    private fun requestInstallPermission(activity: Activity, result: Result) {
        val intent = Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${activity.packageName}"),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            activity.startActivity(intent)
            result.success(null)
        } catch (error: ActivityNotFoundException) {
            result.error("no_settings_activity", "No activity handles unknown-app sources", null)
        }
    }

    /**
     * Starts the system package installer for the APK at the requested path.
     *
     * Answers `success(true)` when an installer activity started, `success(false)`
     * when nothing resolved the intent, and `error` only for malformed arguments.
     */
    private fun install(activity: Activity, arguments: Any?, result: Result) {
        val path = (arguments as? Map<*, *>)?.get("path") as? String
        if (path.isNullOrEmpty()) {
            result.error("invalid_argument", "install requires a non-empty APK path", null)
            return
        }
        val apkFile = File(path)
        val context: Context = activity.applicationContext
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.update-files",
            apkFile,
        )
        val baseIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, APK_MIME_TYPE)
            clipData = ClipData.newRawUri("ShortLink update", uri)
            addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK,
            )
        }
        // Hand the install to the system package installer explicitly first so no
        // other ACTION_VIEW handler can claim it; ROMs without that package fall
        // back to the implicit intent.
        val explicitIntent = Intent(baseIntent).setPackage(SYSTEM_PACKAGE_INSTALLER)
        val started: Boolean = try {
            context.startActivity(explicitIntent)
            true
        } catch (error: ActivityNotFoundException) {
            try {
                context.startActivity(baseIntent)
                true
            } catch (fallback: ActivityNotFoundException) {
                false
            }
        }
        if (started) {
            scheduleCacheCleanup(apkFile)
        }
        result.success(started)
    }

    /**
     * Cache cleanup after the install intent fired: delete stale sibling APKs
     * immediately, and this APK once the installer has read it (60 s).
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
}
