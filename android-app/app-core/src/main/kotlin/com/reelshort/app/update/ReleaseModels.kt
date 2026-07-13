package com.reelshort.app.update

import java.io.IOException
import java.io.File

data class ReleaseAsset(
    val name: String,
    val downloadUrl: String,
    val contentType: String,
    val sizeBytes: Long,
)

data class ReleaseInfo(
    val tagName: String,
    val version: SemanticVersion,
    val title: String,
    val body: String,
    val publishedAt: String,
    val apkAsset: ReleaseAsset,
    val sha256Asset: ReleaseAsset,
)

data class DownloadProgress(
    val downloadedBytes: Long,
    val totalBytes: Long?,
)

interface ReleaseUpdateClient {
    suspend fun fetchLatestStable(): ReleaseInfo?
}

interface ReleaseDownloader {
    suspend fun download(
        url: String,
        destination: File,
        maxBytes: Long,
        onProgress: (DownloadProgress) -> Unit,
    )

    suspend fun fetchSha256(url: String): String
}

interface UpdatePackageVerifier {
    suspend fun verify(apkFile: File)
}

sealed class ReleaseUpdateException(message: String, cause: Throwable? = null) : IOException(message, cause) {
    class Http(val statusCode: Int) : ReleaseUpdateException("Update service returned HTTP $statusCode")
    class InvalidRelease(reason: String) : ReleaseUpdateException("Invalid release: $reason")
    class DownloadTooLarge(val maxBytes: Long) : ReleaseUpdateException("Download exceeds $maxBytes bytes")
    class InvalidChecksum : ReleaseUpdateException("Invalid SHA-256 checksum asset")
}
