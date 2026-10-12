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
    cosBucket: String = DEFAULT_COS_BUCKET,
    cosRegion: String = DEFAULT_COS_REGION,
) : ReleaseUpdateClient {
    /**
     * 只接受本桶的 COS 域名（<bucket>.cos.<region>.myqcloud.com）。
     * 此前只校验 `.myqcloud.com` 后缀，任意其它账号的 COS 桶都能让 App 下载并安装其 APK。
     */
    private val allowedCosHosts: Set<String> = setOf("$cosBucket.cos.$cosRegion.myqcloud.com")

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
            versionCode = versionCode,
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
        // COS 预签名下载链接由 <bucket>.cos.<region>.myqcloud.com 提供并带签名查询参数，
        // 因此这里精确校验桶域名（含地域），再要求路径以期望的对象文件名结尾。
        val host = uri.host
        if (host == null || host !in allowedCosHosts) {
            throw ReleaseUpdateException.InvalidRelease("asset URL must be the ShortLink COS bucket")
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

        /** 默认 COS 桶与地域（与发布脚本 COS_BUCKET/COS_REGION 保持一致，Android 层用 BuildConfig 覆盖）。 */
        const val DEFAULT_COS_BUCKET = "update-1259596907"
        const val DEFAULT_COS_REGION = "ap-chengdu"
        private const val APK_CONTENT_TYPE = "application/vnd.android.package-archive"
        private const val MAX_JSON_BYTES = 65_536L
        private const val MAX_RELEASE_NOTES_CHARS = 4_000
    }
}
