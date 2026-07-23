package com.reelshort.app.update

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

enum class UpdateFailureReason {
    NETWORK,
    INVALID_RELEASE,
    STORAGE,
    DOWNLOAD,
    VERIFICATION,
    INSTALL,
}

sealed interface UpdateState {
    data object Idle : UpdateState
    data class Checking(val manual: Boolean) : UpdateState
    data class Available(val release: ReleaseInfo) : UpdateState
    data class Downloading(val release: ReleaseInfo, val progress: DownloadProgress) : UpdateState
    data class Verifying(val release: ReleaseInfo) : UpdateState
    data class PermissionRequired(val release: ReleaseInfo, val apkFile: File) : UpdateState
    data class ReadyToInstall(val release: ReleaseInfo, val apkFile: File) : UpdateState
    data class UpToDate(val currentVersion: SemanticVersion) : UpdateState
    data class Failed(
        val reason: UpdateFailureReason,
        val manual: Boolean,
        val release: ReleaseInfo? = null,
    ) : UpdateState
}

sealed interface InstallRequest {
    data object RequestPermission : InstallRequest
    data class Install(val apkFile: File) : InstallRequest
}

class UpdateCoordinator(
    private val releaseClient: ReleaseUpdateClient,
    private val downloader: ReleaseDownloader,
    private val currentVersion: SemanticVersion,
    private val availableBytes: (File) -> Long = { it.usableSpace },
    private val maxApkBytes: Long = MAX_APK_BYTES,
) {
    private val mutableState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = mutableState.asStateFlow()

    suspend fun checkForUpdate(manual: Boolean) {
        if (mutableState.value is UpdateState.Checking || mutableState.value is UpdateState.Downloading) return
        mutableState.value = UpdateState.Checking(manual)
        try {
            val release = releaseClient.fetchLatestStable()
            mutableState.value = when {
                release == null -> if (manual) UpdateState.UpToDate(currentVersion) else UpdateState.Idle
                release.version > currentVersion -> UpdateState.Available(release)
                manual -> UpdateState.UpToDate(currentVersion)
                else -> UpdateState.Idle
            }
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            mutableState.value = if (manual) {
                UpdateState.Failed(error.toFailureReason(), manual = true)
            } else {
                UpdateState.Idle
            }
        }
    }

    suspend fun downloadAvailable(cacheDir: File) {
        val release = when (val current = mutableState.value) {
            is UpdateState.Available -> current.release
            is UpdateState.Failed -> current.release
            else -> null
        } ?: return
        val updatesDir = File(cacheDir, "updates").also { it.mkdirs() }
        val partialFile = File(updatesDir, "${release.apkAsset.name}.part")
        val finalFile = File(updatesDir, release.apkAsset.name)
        partialFile.delete()
        finalFile.delete()
        try {
            if (release.apkAsset.sizeBytes > maxApkBytes) {
                throw ReleaseUpdateException.DownloadTooLarge(maxApkBytes)
            }
            val requiredBytes = release.apkAsset.sizeBytes + MIN_FREE_SPACE_BYTES
            if (availableBytes(updatesDir) < requiredBytes) throw InsufficientStorageException()
            mutableState.value = UpdateState.Downloading(release, DownloadProgress(0, release.apkAsset.sizeBytes))
            val expectedChecksum = downloader.fetchSha256(release.sha256Asset.downloadUrl)
            downloader.download(release.apkAsset.downloadUrl, partialFile, maxApkBytes) { progress ->
                mutableState.value = UpdateState.Downloading(release, progress)
            }
            mutableState.value = UpdateState.Verifying(release)
            if (partialFile.length() != release.apkAsset.sizeBytes) {
                throw SecurityException("Downloaded APK size does not match release metadata")
            }
            if (!sha256(partialFile).equals(expectedChecksum, ignoreCase = true)) {
                throw ReleaseUpdateException.InvalidChecksum()
            }
            moveAtomically(partialFile, finalFile)
            mutableState.value = UpdateState.ReadyToInstall(release, finalFile)
        } catch (error: Throwable) {
            partialFile.delete()
            finalFile.delete()
            if (error is CancellationException) throw error
            mutableState.value = UpdateState.Failed(
                reason = error.toFailureReason(),
                manual = true,
                release = release,
            )
        }
    }

    fun downloadCancelled() {
        val release = when (val current = mutableState.value) {
            is UpdateState.Downloading -> current.release
            is UpdateState.Verifying -> current.release
            else -> null
        }
        if (release != null) mutableState.value = UpdateState.Available(release)
    }

    fun prepareInstall(canRequestPackageInstalls: Boolean): InstallRequest? =
        when (val current = mutableState.value) {
            is UpdateState.ReadyToInstall -> if (canRequestPackageInstalls) {
                InstallRequest.Install(current.apkFile)
            } else {
                mutableState.value = UpdateState.PermissionRequired(current.release, current.apkFile)
                InstallRequest.RequestPermission
            }
            is UpdateState.PermissionRequired -> if (canRequestPackageInstalls) {
                mutableState.value = UpdateState.ReadyToInstall(current.release, current.apkFile)
                InstallRequest.Install(current.apkFile)
            } else {
                InstallRequest.RequestPermission
            }
            else -> null
        }

    fun onInstallPermissionResult(granted: Boolean): InstallRequest? {
        val current = mutableState.value as? UpdateState.PermissionRequired ?: return null
        if (!granted) return null
        mutableState.value = UpdateState.ReadyToInstall(current.release, current.apkFile)
        return InstallRequest.Install(current.apkFile)
    }

    fun installationFailed() {
        val current = mutableState.value
        val release = when (current) {
            is UpdateState.ReadyToInstall -> current.release
            is UpdateState.PermissionRequired -> current.release
            else -> null
        } ?: return
        mutableState.value = UpdateState.Failed(UpdateFailureReason.INSTALL, manual = true, release = release)
    }

    fun dismiss() {
        if (mutableState.value !is UpdateState.Downloading && mutableState.value !is UpdateState.Verifying) {
            mutableState.value = UpdateState.Idle
        }
    }

    private fun moveAtomically(source: File, target: File) {
        try {
            Files.move(
                source.toPath(),
                target.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun Throwable.toFailureReason(): UpdateFailureReason = when (this) {
        is InsufficientStorageException, is ReleaseUpdateException.DownloadTooLarge -> UpdateFailureReason.STORAGE
        is ReleaseUpdateException.InvalidChecksum, is SecurityException -> UpdateFailureReason.VERIFICATION
        is ReleaseUpdateException.InvalidRelease -> UpdateFailureReason.INVALID_RELEASE
        is ReleaseUpdateException.Http -> UpdateFailureReason.NETWORK
        else -> UpdateFailureReason.DOWNLOAD
    }

    private class InsufficientStorageException : Exception()

    companion object {
        const val MAX_APK_BYTES = 250L * 1024L * 1024L
        private const val MIN_FREE_SPACE_BYTES = 32L * 1024L * 1024L
    }
}

fun sha256(content: ByteArray): String =
    MessageDigest.getInstance("SHA-256")
        .digest(content)
        .joinToString(separator = "") { byte -> "%02x".format(byte) }

fun sha256(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().buffered().use { input ->
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val read = input.read(buffer)
            if (read == -1) break
            digest.update(buffer, 0, read)
        }
    }
    return digest.digest().joinToString(separator = "") { byte -> "%02x".format(byte) }
}
