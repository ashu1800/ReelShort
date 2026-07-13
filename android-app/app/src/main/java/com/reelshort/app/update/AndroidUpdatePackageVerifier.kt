package com.reelshort.app.update

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import java.io.File
import java.security.MessageDigest

data class PackageIdentity(
    val packageName: String,
    val versionCode: Long,
    val signingCertificateSha256: Set<String>,
)

class AndroidUpdatePackageVerifier(
    private val currentIdentity: () -> PackageIdentity,
    private val archiveIdentity: (File) -> PackageIdentity?,
) : UpdatePackageVerifier {
    override suspend fun verify(apkFile: File) {
        val current = currentIdentity()
        val archive = archiveIdentity(apkFile) ?: throw SecurityException("Downloaded APK cannot be parsed")
        if (archive.packageName != current.packageName) throw SecurityException("Downloaded APK package does not match")
        if (archive.versionCode <= current.versionCode) throw SecurityException("Downloaded APK version is not newer")
        if (current.signingCertificateSha256.isEmpty() || archive.signingCertificateSha256.isEmpty()) {
            throw SecurityException("APK signing certificate is missing")
        }
        if (archive.signingCertificateSha256 != current.signingCertificateSha256) {
            throw SecurityException("APK signing certificate does not match")
        }
    }

    companion object {
        fun create(context: Context): AndroidUpdatePackageVerifier {
            val appContext = context.applicationContext
            val packageManager = appContext.packageManager
            return AndroidUpdatePackageVerifier(
                currentIdentity = {
                    packageManager.readInstalledIdentity(appContext.packageName)
                        ?: throw SecurityException("Current package identity is unavailable")
                },
                archiveIdentity = packageManager::readArchiveIdentity,
            )
        }
    }
}

@Suppress("DEPRECATION")
private fun PackageManager.readInstalledIdentity(packageName: String): PackageIdentity? =
    runCatching {
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            PackageManager.GET_SIGNATURES
        }
        getPackageInfo(packageName, flags).toIdentity()
    }.getOrNull()

@Suppress("DEPRECATION")
private fun PackageManager.readArchiveIdentity(apkFile: File): PackageIdentity? =
    runCatching {
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            PackageManager.GET_SIGNATURES
        }
        getPackageArchiveInfo(apkFile.absolutePath, flags)?.toIdentity()
    }.getOrNull()

@Suppress("DEPRECATION")
private fun PackageInfo.toIdentity(): PackageIdentity {
    val certificates = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        val signatures = signingInfo?.let { info ->
            if (info.hasMultipleSigners()) info.apkContentsSigners else info.signingCertificateHistory
        }
        signatures.orEmpty().map { it.toByteArray().sha256Hex() }.toSet()
    } else {
        signatures.orEmpty().map { it.toByteArray().sha256Hex() }.toSet()
    }
    val code = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) longVersionCode else versionCode.toLong()
    return PackageIdentity(packageName, code, certificates)
}

private fun ByteArray.sha256Hex(): String =
    MessageDigest.getInstance("SHA-256")
        .digest(this)
        .joinToString(separator = "") { byte -> "%02x".format(byte) }
