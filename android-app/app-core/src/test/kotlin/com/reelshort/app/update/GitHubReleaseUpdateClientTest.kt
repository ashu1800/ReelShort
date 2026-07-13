package com.reelshort.app.update

import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import java.io.Closeable
import kotlin.test.Test
import kotlin.test.AfterTest
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GitHubReleaseUpdateClientTest : Closeable {
    private val server = MockWebServer().also { it.start() }
    private val client = GitHubReleaseUpdateClient(
        httpClient = OkHttpClient(),
        endpoint = server.url("/repos/ashu1800/ReelShort/releases/latest").toString(),
        userAgent = "ShortLink-Android/0.2.0",
    )

    @Test
    fun parsesStableReleaseAndExactAssets() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(releaseJson()))

        val release = client.fetchLatestStable()

        requireNotNull(release)
        assertEquals("v0.2.0", release.tagName)
        assertEquals(SemanticVersion(0, 2, 0), release.version)
        assertEquals("ShortLink-v0.2.0.apk", release.apkAsset.name)
        assertEquals(42_000_000, release.apkAsset.sizeBytes)
        assertEquals("ShortLink-v0.2.0.apk.sha256", release.sha256Asset.name)
        val request = server.takeRequest()
        assertEquals("application/vnd.github+json", request.getHeader("Accept"))
        assertEquals("ShortLink-Android/0.2.0", request.getHeader("User-Agent"))
    }

    @Test
    fun ignoresDraftAndPreReleaseResponses() = runTest {
        server.enqueue(MockResponse().setBody(releaseJson(draft = true)))
        server.enqueue(MockResponse().setBody(releaseJson(preRelease = true)))

        assertNull(client.fetchLatestStable())
        assertNull(client.fetchLatestStable())
    }

    @Test
    fun rejectsMalformedTagAndMissingOrDuplicateAssets() = runTest {
        server.enqueue(MockResponse().setBody(releaseJson(tag = "0.2")))
        assertFailsWith<ReleaseUpdateException.InvalidRelease> { client.fetchLatestStable() }

        server.enqueue(MockResponse().setBody(releaseJson(includeChecksum = false)))
        assertFailsWith<ReleaseUpdateException.InvalidRelease> { client.fetchLatestStable() }

        server.enqueue(MockResponse().setBody(releaseJson(duplicateApk = true)))
        assertFailsWith<ReleaseUpdateException.InvalidRelease> { client.fetchLatestStable() }
    }

    @Test
    fun mapsHttpFailureWithoutLeakingResponseBody() = runTest {
        server.enqueue(MockResponse().setResponseCode(403).setBody("sensitive upstream body"))

        val error = assertFailsWith<ReleaseUpdateException.Http> { client.fetchLatestStable() }

        assertEquals(403, error.statusCode)
        assertTrue(error.message.orEmpty().contains("403"))
        assertTrue(!error.message.orEmpty().contains("sensitive"))
    }

    private fun releaseJson(
        tag: String = "v0.2.0",
        draft: Boolean = false,
        preRelease: Boolean = false,
        includeChecksum: Boolean = true,
        duplicateApk: Boolean = false,
    ): String {
        val apk = assetJson("ShortLink-$tag.apk", "application/vnd.android.package-archive", 42_000_000)
        val assets = buildList {
            add(apk)
            if (duplicateApk) add(apk)
            if (includeChecksum) add(assetJson("ShortLink-$tag.apk.sha256", "text/plain", 80))
        }.joinToString(",")
        return """
            {
              "tag_name": "$tag",
              "name": "ShortLink $tag",
              "body": "Release notes",
              "published_at": "2026-07-13T12:00:00Z",
              "draft": $draft,
              "prerelease": $preRelease,
              "assets": [$assets]
            }
        """.trimIndent()
    }

    private fun assetJson(name: String, contentType: String, size: Long): String = """
        {
          "name": "$name",
          "browser_download_url": "https://github.com/ashu1800/ReelShort/releases/download/v0.2.0/$name",
          "content_type": "$contentType",
          "size": $size
        }
    """.trimIndent()

    @AfterTest
    override fun close() {
        server.shutdown()
    }
}
