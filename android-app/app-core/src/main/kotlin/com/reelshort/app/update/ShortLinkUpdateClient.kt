package com.reelshort.app.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.Buffer
import java.net.URI

class ShortLinkUpdateClient(
    private val httpClient: OkHttpClient,
    private val endpoint: String = DEFAULT_ENDPOINT,
    private val userAgent: String,
    private val json: Json = Json { ignoreUnknownKeys = true },
) : ReleaseUpdateClient {
    override suspend fun fetchLatestStable(): ReleaseInfo = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(endpoint)
            .header("Accept", "application/json")
            .header("User-Agent", userAgent)
            .get()
            .build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw ReleaseUpdateException.Http(response.code)
            val payload = response.body ?: throw ReleaseUpdateException.InvalidRelease("empty response")
            val manifest = runCatching {
                json.decodeFromString<UpdateManifest>(payload.readUtf8Limited(MAX_JSON_BYTES))
            }.getOrElse {
                throw ReleaseUpdateException.InvalidRelease("malformed JSON")
            }
            manifest.toReleaseInfo()
        }
    }

    private fun UpdateManifest.toReleaseInfo(): ReleaseInfo {
        val version = SemanticVersion.parse(versionName)
            ?: throw ReleaseUpdateException.InvalidRelease("versionName must use X.Y.Z")
        if (versionName != version.toString()) {
            throw ReleaseUpdateException.InvalidRelease("versionName must be canonical")
        }
        if (versionCode <= 0) throw ReleaseUpdateException.InvalidRelease("versionCode must be positive")
        if (sizeBytes <= 0) throw ReleaseUpdateException.InvalidRelease("APK size must be positive")
        if (sha256SizeBytes <= 0) throw ReleaseUpdateException.InvalidRelease("checksum size must be positive")

        val tagName = "v$version"
        val apkName = "ShortLink-$tagName.apk"
        val checksumName = "$apkName.sha256"
        validateAssetUrl(apkUrl, apkName)
        validateAssetUrl(sha256Url, checksumName)

        return ReleaseInfo(
            tagName = tagName,
            version = version,
            title = title.ifBlank { "ShortLink $tagName" },
            body = releaseNotes.take(MAX_RELEASE_NOTES_CHARS),
            publishedAt = publishedAt,
            apkAsset = ReleaseAsset(apkName, apkUrl, APK_CONTENT_TYPE, sizeBytes),
            sha256Asset = ReleaseAsset(checksumName, sha256Url, "text/plain", sha256SizeBytes),
        )
    }

    private fun validateAssetUrl(value: String, expectedName: String) {
        val uri = runCatching { URI(value) }.getOrNull()
            ?: throw ReleaseUpdateException.InvalidRelease("asset URL is invalid")
        if (uri.scheme != "https" || uri.port != -1 || uri.userInfo != null) {
            throw ReleaseUpdateException.InvalidRelease("asset URL must be HTTPS without port or userinfo")
        }
        // COS pre-signed download URLs are served from <bucket>.cos.<region>.myqcloud.com and carry
        // signing query parameters, so we only pin to the COS domain suffix and require the path to
        // end with the expected object file name.
        val host = uri.host
        if (host == null || !host.endsWith(COS_HOST_SUFFIX)) {
            throw ReleaseUpdateException.InvalidRelease("asset URL must be a COS domain")
        }
        val path = uri.rawPath ?: ""
        if (uri.rawFragment != null || !path.endsWith("/$expectedName")) {
            throw ReleaseUpdateException.InvalidRelease("asset URL path is invalid")
        }
    }

    private fun okhttp3.ResponseBody.readUtf8Limited(maxBytes: Long): String {
        contentLength().takeIf { it >= 0 }?.let {
            if (it > maxBytes) throw ReleaseUpdateException.InvalidRelease("response is too large")
        }
        val source = source()
        val buffer = Buffer()
        var total = 0L
        while (true) {
            val read = source.read(buffer, minOf(8_192L, maxBytes + 1 - total))
            if (read == -1L) break
            total += read
            if (total > maxBytes) throw ReleaseUpdateException.InvalidRelease("response is too large")
        }
        return buffer.readUtf8()
    }

    @Serializable
    private data class UpdateManifest(
        val versionName: String,
        val versionCode: Long,
        val title: String = "",
        val releaseNotes: String = "",
        val publishedAt: String = "",
        val apkUrl: String,
        val sha256Url: String,
        val sizeBytes: Long,
        val sha256SizeBytes: Long,
        val minimumVersionCode: Long? = null,
        val mandatory: Boolean = false,
    )

    companion object {
        const val DEFAULT_ENDPOINT = "https://shortlink.hjj888.cc/api/app/release/latest"
        private const val COS_HOST_SUFFIX = ".myqcloud.com"
        private const val APK_CONTENT_TYPE = "application/vnd.android.package-archive"
        private const val MAX_JSON_BYTES = 65_536L
        private const val MAX_RELEASE_NOTES_CHARS = 4_000
    }
}
