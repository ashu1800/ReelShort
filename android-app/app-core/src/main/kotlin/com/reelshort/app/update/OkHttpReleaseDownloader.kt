package com.reelshort.app.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.Buffer
import java.io.File

class OkHttpReleaseDownloader(
    private val httpClient: OkHttpClient,
) : ReleaseDownloader {
    override suspend fun download(
        url: String,
        destination: File,
        maxBytes: Long,
        onProgress: (DownloadProgress) -> Unit,
    ) = withContext(Dispatchers.IO) {
        destination.parentFile?.mkdirs()
        destination.delete()
        try {
            val request = Request.Builder().url(url).header("User-Agent", "ShortLink-Android-Updater").build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw ReleaseUpdateException.Http(response.code)
                val body = response.body ?: throw ReleaseUpdateException.InvalidRelease("empty download")
                val contentLength = body.contentLength().takeIf { it >= 0 }
                if (contentLength != null && contentLength > maxBytes) {
                    throw ReleaseUpdateException.DownloadTooLarge(maxBytes)
                }
                body.byteStream().use { input ->
                    destination.outputStream().buffered().use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        var downloaded = 0L
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val read = input.read(buffer)
                            if (read == -1) break
                            downloaded += read
                            if (downloaded > maxBytes) throw ReleaseUpdateException.DownloadTooLarge(maxBytes)
                            output.write(buffer, 0, read)
                            onProgress(DownloadProgress(downloaded, contentLength))
                        }
                    }
                }
            }
        } catch (error: Throwable) {
            destination.delete()
            throw error
        }
    }

    override suspend fun fetchSha256(url: String): String = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).header("User-Agent", "ShortLink-Android-Updater").build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw ReleaseUpdateException.Http(response.code)
            val body = response.body ?: throw ReleaseUpdateException.InvalidChecksum()
            val length = body.contentLength()
            if (length > MAX_CHECKSUM_BYTES) throw ReleaseUpdateException.InvalidChecksum()
            val source = body.source()
            val buffer = Buffer()
            var total = 0L
            while (true) {
                val read = source.read(buffer, minOf(256L, MAX_CHECKSUM_BYTES + 1 - total))
                if (read == -1L) break
                total += read
                if (total > MAX_CHECKSUM_BYTES) throw ReleaseUpdateException.InvalidChecksum()
            }
            val value = buffer.readUtf8().trim()
            CHECKSUM_PATTERN.matchEntire(value)?.groupValues?.get(1)?.lowercase()
                ?: throw ReleaseUpdateException.InvalidChecksum()
        }
    }

    companion object {
        private const val MAX_CHECKSUM_BYTES = 1_024L
        private val CHECKSUM_PATTERN = Regex("^([a-fA-F0-9]{64})(?:\\s+\\*?[^\\r\\n]+)?$")
    }
}
