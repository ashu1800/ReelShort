package com.reelshort.app.network

import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import java.io.Closeable
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.AfterTest
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GeoIpClientTest : Closeable {
    private val server = MockWebServer().also { it.start() }
    private val client = GeoIpClient(
        httpClient = OkHttpClient.Builder()
            .connectTimeout(2, TimeUnit.SECONDS)
            .readTimeout(2, TimeUnit.SECONDS)
            .build(),
        endpoint = server.url("/json/").toString(),
    )

    @Test
    fun detectsCountryCodeFromResponse() = runTest {
        server.enqueue(MockResponse().setBody("""{"country_code":"US","ip":"1.2.3.4"}"""))
        assertEquals("US", client.detectCountryCode())
    }

    @Test
    fun detectsChinaCountryCode() = runTest {
        server.enqueue(MockResponse().setBody("""{"country_code":"CN","ip":"1.2.3.4"}"""))
        assertEquals("CN", client.detectCountryCode())
    }

    @Test
    fun returnsNullOnHttpError() = runTest {
        server.enqueue(MockResponse().setResponseCode(503).setBody("error"))
        assertNull(client.detectCountryCode())
    }

    @Test
    fun returnsNullOnMalformedJson() = runTest {
        server.enqueue(MockResponse().setBody("not json at all"))
        assertNull(client.detectCountryCode())
    }

    @Test
    fun returnsNullWhenCountryCodeMissing() = runTest {
        server.enqueue(MockResponse().setBody("""{"ip":"1.2.3.4"}"""))
        assertNull(client.detectCountryCode())
    }

    @AfterTest
    override fun close() {
        server.shutdown()
    }
}
