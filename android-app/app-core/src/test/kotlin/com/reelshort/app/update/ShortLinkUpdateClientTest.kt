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
import kotlin.test.assertNotNull

class ShortLinkUpdateClientTest : Closeable {
    private val server = MockWebServer().also { it.start() }
    private val client = ShortLinkUpdateClient(
        httpClient = OkHttpClient(),
        endpoint = server.url("/api/app/release/latest").toString(),
        userAgent = "ShortLink-Android/0.4.2",
    )

    @Test
    fun parsesManifestWithCosPresignedUrls() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(manifestJson()))

        val release = client.fetchLatestStable()

        assertNotNull(release)
        assertEquals("v0.4.2", release.tagName)
        assertEquals(SemanticVersion(0, 4, 2), release.version)
        assertEquals("ShortLink-v0.4.2.apk", release.apkAsset.name)
        assertEquals("ShortLink-v0.4.2.apk.sha256", release.sha256Asset.name)
        assertEquals(12_345_678, release.apkAsset.sizeBytes)
        // Presigned URL with query string is accepted
        assertEquals(
            "https://update-1259596907.cos.ap-chengdu.myqcloud.com/releases/android/ShortLink-v0.4.2.apk?q-sign-algorithm=sha1",
            release.apkAsset.downloadUrl,
        )
    }

    @Test
    fun rejectsNonHttpsAssetUrl() = runTest {
        server.enqueue(MockResponse().setBody(manifestJson(apkUrl = "http://update-1259596907.cos.ap-chengdu.myqcloud.com/releases/android/ShortLink-v0.4.2.apk")))

        assertFailsWith<ReleaseUpdateException.InvalidRelease> { client.fetchLatestStable() }
    }

    @Test
    fun rejectsNonCosHost() = runTest {
        server.enqueue(MockResponse().setBody(manifestJson(apkUrl = "https://evil.example.com/releases/android/ShortLink-v0.4.2.apk")))

        assertFailsWith<ReleaseUpdateException.InvalidRelease> { client.fetchLatestStable() }
    }

    @Test
    fun rejectsAssetUrlNotEndingInExpectedFileName() = runTest {
        server.enqueue(MockResponse().setBody(manifestJson(apkUrl = "https://update-1259596907.cos.ap-chengdu.myqcloud.com/releases/android/different.apk")))

        assertFailsWith<ReleaseUpdateException.InvalidRelease> { client.fetchLatestStable() }
    }

    @Test
    fun rejectsNonCanonicalVersionName() = runTest {
        server.enqueue(MockResponse().setBody(manifestJson(versionName = "0.4")))

        assertFailsWith<ReleaseUpdateException.InvalidRelease> { client.fetchLatestStable() }
    }

    @Test
    fun mapsHttpFailureToHttpException() = runTest {
        server.enqueue(MockResponse().setResponseCode(502).setBody("bad gateway"))

        val error = assertFailsWith<ReleaseUpdateException.Http> { client.fetchLatestStable() }
        assertEquals(502, error.statusCode)
    }

    private fun manifestJson(
        versionName: String = "0.4.2",
        apkUrl: String = "https://update-1259596907.cos.ap-chengdu.myqcloud.com/releases/android/ShortLink-v0.4.2.apk?q-sign-algorithm=sha1",
    ): String = """
        {
          "versionName": "$versionName",
          "versionCode": 6,
          "title": "ShortLink v0.4.2",
          "releaseNotes": "Bug fixes",
          "publishedAt": "2026-07-14T10:00:00Z",
          "apkUrl": "$apkUrl",
          "sha256Url": "https://update-1259596907.cos.ap-chengdu.myqcloud.com/releases/android/ShortLink-v0.4.2.apk.sha256?q-sign-algorithm=sha1",
          "sizeBytes": 12345678,
          "sha256SizeBytes": 65,
          "apkSha256": "abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789",
          "minimumVersionCode": 1,
          "mandatory": false
        }
    """.trimIndent()

    @AfterTest
    override fun close() {
        server.shutdown()
    }
}
