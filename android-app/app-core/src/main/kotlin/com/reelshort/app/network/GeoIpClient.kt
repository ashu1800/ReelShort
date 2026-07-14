package com.reelshort.app.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Detects the user's IP geographic location via a free Geo-IP API. Used by the app to block access
 * from mainland China (client-side only). On any failure (timeout, parse error, network), returns
 * null — fail-open to avoid blocking legitimate users.
 */
class GeoIpClient(
    private val httpClient: OkHttpClient = defaultClient(),
    private val endpoint: String = "https://ipapi.co/json/",
    private val json: Json = Json { ignoreUnknownKeys = true },
) {

    /**
     * @return the detected country code (ISO 3166-1 alpha-2, e.g. "US", "CN"), or null on any failure.
     */
    suspend fun detectCountryCode(): String? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(endpoint)
                .header("Accept", "application/json")
                .get()
                .build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val body = response.body ?: return@withContext null
                val parsed = runCatching {
                    json.decodeFromString<GeoIpResponse>(body.string())
                }.getOrNull() ?: return@withContext null
                parsed.countryCode?.takeIf { it.isNotBlank() }
            }
        }
        catch (_: Exception) {
            null
        }
    }

    @Serializable
    private data class GeoIpResponse(
        @SerialName("country_code") val countryCode: String? = null,
    )

    companion object {
        /** Block these country codes (ISO 3166-1 alpha-2). */
        val BLOCKED_COUNTRIES = setOf("CN")

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .callTimeout(5, TimeUnit.SECONDS)
            .build()
    }
}
