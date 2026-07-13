package com.reelshort.app.update

import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import java.io.Closeable
import java.nio.file.Files
import kotlin.io.path.exists
import kotlin.io.path.readBytes
import kotlin.test.Test
import kotlin.test.AfterTest
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class OkHttpReleaseDownloaderTest : Closeable {
    private val server = MockWebServer().also { it.start() }
    private val downloader = OkHttpReleaseDownloader(OkHttpClient())

    @Test
    fun streamsFileAndReportsKnownProgress() = runTest {
        val content = ByteArray(48_000) { (it % 251).toByte() }
        server.enqueue(MockResponse().setBody(Buffer().write(content)))
        val destination = Files.createTempDirectory("update-download").resolve("app.apk.part")
        val progress = mutableListOf<DownloadProgress>()

        downloader.download(
            url = server.url("/app.apk").toString(),
            destination = destination.toFile(),
            maxBytes = 100_000,
            onProgress = progress::add,
        )

        assertContentEquals(content, destination.readBytes())
        assertEquals(content.size.toLong(), progress.last().downloadedBytes)
        assertEquals(content.size.toLong(), progress.last().totalBytes)
    }

    @Test
    fun reportsUnknownTotalForChunkedResponse() = runTest {
        server.enqueue(MockResponse().setChunkedBody("chunked response", 4))
        val destination = Files.createTempDirectory("update-download").resolve("app.apk.part")
        val progress = mutableListOf<DownloadProgress>()

        downloader.download(server.url("/app.apk").toString(), destination.toFile(), 100_000, progress::add)

        assertNull(progress.last().totalBytes)
    }

    @Test
    fun rejectsOversizedDownloadAndDeletesPartialFile() = runTest {
        server.enqueue(MockResponse().setBody(Buffer().write(ByteArray(2_048))))
        val destination = Files.createTempDirectory("update-download").resolve("app.apk.part")

        assertFailsWith<ReleaseUpdateException.DownloadTooLarge> {
            downloader.download(server.url("/app.apk").toString(), destination.toFile(), 1_024) {}
        }

        assertEquals(false, destination.exists())
    }

    @Test
    fun parsesSha256SidecarAndRejectsInvalidContent() = runTest {
        val digest = "a".repeat(64)
        server.enqueue(MockResponse().setBody("$digest  ShortLink-v0.2.0.apk\n"))
        server.enqueue(MockResponse().setBody("not-a-digest"))

        assertEquals(digest, downloader.fetchSha256(server.url("/valid.sha256").toString()))
        assertFailsWith<ReleaseUpdateException.InvalidChecksum> {
            downloader.fetchSha256(server.url("/invalid.sha256").toString())
        }
    }

    @AfterTest
    override fun close() {
        server.shutdown()
    }
}
