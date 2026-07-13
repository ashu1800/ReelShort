package com.reelshort.app.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.Buffer
import java.net.URI

class GitHubReleaseUpdateClient(
    private val httpClient: OkHttpClient,
    private val endpoint: String = DEFAULT_ENDPOINT,
    private val userAgent: String,
    private val json: Json = Json { ignoreUnknownKeys = true },
) : ReleaseUpdateClient {
    override suspend fun fetchLatestStable(): ReleaseInfo? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(endpoint)
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .header("User-Agent", userAgent)
            .get()
            .build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw ReleaseUpdateException.Http(response.code)
            val payload = response.body ?: throw ReleaseUpdateException.InvalidRelease("empty response")
            val dto = runCatching { json.decodeFromString<GitHubReleaseDto>(payload.readUtf8Limited(MAX_JSON_BYTES)) }
                .getOrElse { throw ReleaseUpdateException.InvalidRelease("malformed JSON") }
            dto.toDomain()
        }
    }

    private fun GitHubReleaseDto.toDomain(): ReleaseInfo? {
        if (draft || prerelease) return null
        if (!tagName.startsWith("v")) throw ReleaseUpdateException.InvalidRelease("tag must start with v")
        val version = SemanticVersion.parse(tagName)
            ?: throw ReleaseUpdateException.InvalidRelease("tag must use vX.Y.Z")
        if (tagName != "v$version") throw ReleaseUpdateException.InvalidRelease("non-canonical tag")
        val apkName = "ShortLink-$tagName.apk"
        val checksumName = "$apkName.sha256"
        val apk = assets.singleNamed(apkName)
        val checksum = assets.singleNamed(checksumName)
        return ReleaseInfo(
            tagName = tagName,
            version = version,
            title = name.ifBlank { "ShortLink $tagName" },
            body = body.take(MAX_RELEASE_NOTES_CHARS),
            publishedAt = publishedAt,
            apkAsset = apk.toDomain(),
            sha256Asset = checksum.toDomain(),
        )
    }

    private fun List<GitHubAssetDto>.singleNamed(expectedName: String): GitHubAssetDto {
        val matches = filter { it.name == expectedName }
        if (matches.size != 1) {
            throw ReleaseUpdateException.InvalidRelease("expected exactly one $expectedName asset")
        }
        return matches.single()
    }

    private fun GitHubAssetDto.toDomain(): ReleaseAsset {
        val uri = runCatching { URI(downloadUrl) }.getOrNull()
        if (uri?.scheme != "https" || uri.host != "github.com") {
            throw ReleaseUpdateException.InvalidRelease("asset URL must use github.com HTTPS")
        }
        if (size <= 0) throw ReleaseUpdateException.InvalidRelease("asset size must be positive")
        return ReleaseAsset(name, downloadUrl, contentType, size)
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
    private data class GitHubReleaseDto(
        @SerialName("tag_name") val tagName: String,
        val name: String = "",
        val body: String = "",
        @SerialName("published_at") val publishedAt: String = "",
        val draft: Boolean = false,
        val prerelease: Boolean = false,
        val assets: List<GitHubAssetDto> = emptyList(),
    )

    @Serializable
    private data class GitHubAssetDto(
        val name: String,
        @SerialName("browser_download_url") val downloadUrl: String,
        @SerialName("content_type") val contentType: String = "",
        val size: Long,
    )

    companion object {
        const val DEFAULT_ENDPOINT = "https://api.github.com/repos/ashu1800/ReelShort/releases/latest"
        private const val MAX_JSON_BYTES = 1_048_576L
        private const val MAX_RELEASE_NOTES_CHARS = 4_000
    }
}
