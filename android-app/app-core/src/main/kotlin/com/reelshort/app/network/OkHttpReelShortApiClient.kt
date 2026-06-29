package com.reelshort.app.network

import com.reelshort.app.config.ApiConfig
import com.reelshort.app.data.ApiHealthStatus
import com.reelshort.app.data.AuthSession
import com.reelshort.app.data.BookSummary
import com.reelshort.app.data.EpisodeSummary
import com.reelshort.app.data.PointAccount
import com.reelshort.app.data.PointRecord
import com.reelshort.app.data.RechargeOrderSummary
import com.reelshort.app.data.VideoUrl
import com.reelshort.app.data.WatchProgressReport
import com.reelshort.app.data.WatchRecord
import com.reelshort.app.network.dto.AuthRequestDto
import com.reelshort.app.network.dto.AuthSessionDto
import com.reelshort.app.network.dto.ApiHealthStatusDto
import com.reelshort.app.network.dto.BackendApiResponse
import com.reelshort.app.network.dto.ContentBookDto
import com.reelshort.app.network.dto.ContentEpisodeDto
import com.reelshort.app.network.dto.ContentVideoDto
import com.reelshort.app.network.dto.PointAccountDto
import com.reelshort.app.network.dto.PointRecordDto
import com.reelshort.app.network.dto.RechargeOrderDto
import com.reelshort.app.network.dto.WatchProgressRequestDto
import com.reelshort.app.network.dto.WatchRecordDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.HttpUrl.Companion.toHttpUrl

class OkHttpReelShortApiClient(
    private val config: ApiConfig = ApiConfig.default(),
    private val httpClient: OkHttpClient = OkHttpClient(),
    private val json: Json = Json { ignoreUnknownKeys = true },
    private val tokenProvider: suspend () -> String? = { null },
) : ReelShortApiClient {

    override suspend fun checkSystemHealth(): ApiHealthStatus =
        execute<ApiHealthStatusDto>(
            Request.Builder()
                .url(config.systemHealthUrl)
                .get()
                .build(),
        ).toDomain()

    override suspend fun login(username: String, password: String): AuthSession =
        post<AuthRequestDto, AuthSessionDto>("/auth/login", AuthRequestDto(username, password)).toDomain()

    override suspend fun register(username: String, password: String): AuthSession =
        post<AuthRequestDto, AuthSessionDto>("/auth/register", AuthRequestDto(username, password)).toDomain()

    override suspend fun getHomeShelf(): List<BookSummary> =
        get<List<ContentBookDto>>("/home/recommend", authenticated = true).map { it.toDomain() }

    override suspend fun search(query: String): List<BookSummary> =
        get<List<ContentBookDto>>("/content/search", mapOf("keywords" to query), authenticated = true)
            .map { it.toDomain() }

    override suspend fun getEpisodes(bookId: String, filteredTitle: String): List<EpisodeSummary> =
        get<List<ContentEpisodeDto>>(
            listOf("content", "books", bookId, "episodes"),
            mapOf("filteredTitle" to filteredTitle),
            authenticated = true,
        ).map { it.toDomain() }

    override suspend fun getVideoUrl(bookId: String, episode: Int, filteredTitle: String, chapterId: String): VideoUrl =
        get<ContentVideoDto>(
            listOf("content", "books", bookId, "episodes", episode.toString(), "play"),
            mapOf("filteredTitle" to filteredTitle, "chapterId" to chapterId),
            authenticated = true,
        ).toDomain()

    override suspend fun reportWatchProgress(
        bookId: String,
        bookTitle: String,
        filteredTitle: String,
        episode: Int,
        chapterId: String,
        positionSeconds: Int,
        durationSeconds: Int,
    ): WatchProgressReport =
        post<WatchProgressRequestDto, WatchRecordDto>(
            "/watch/progress",
            WatchProgressRequestDto(
                bookId = bookId,
                bookTitle = bookTitle,
                filteredTitle = filteredTitle,
                episodeNum = episode,
                chapterId = chapterId,
                positionSeconds = positionSeconds,
                durationSeconds = durationSeconds,
            ),
            authenticated = true,
        ).toProgressReport()

    override suspend fun getWatchHistory(): List<WatchRecord> =
        get<List<WatchRecordDto>>("/watch/history", authenticated = true).map { it.toWatchRecord() }

    override suspend fun getPointAccount(): PointAccount {
        val account = get<PointAccountDto>("/points/account", authenticated = true)
        val records = get<List<PointRecordDto>>("/points/records", authenticated = true)
        return PointAccount(account.balance, records.map { it.toDomain() })
    }

    override suspend fun getOrders(): List<RechargeOrderSummary> =
        get<List<RechargeOrderDto>>("/orders", authenticated = true).map { it.toDomain() }

    private suspend inline fun <reified RequestDto, reified ResponseDto> post(
        path: String,
        requestDto: RequestDto,
        authenticated: Boolean = false,
    ): ResponseDto = execute(
        Request.Builder()
            .url("${config.baseUrl}$path")
            .post(json.encodeToString(requestDto).toRequestBody(JSON_MEDIA_TYPE))
            .applyAuthentication(authenticated)
            .build(),
    )

    private suspend inline fun <reified ResponseDto> get(
        path: String,
        queryParameters: Map<String, String> = emptyMap(),
        authenticated: Boolean = false,
    ): ResponseDto = get(path.trim('/').split('/').filter { it.isNotBlank() }, queryParameters, authenticated)

    private suspend inline fun <reified ResponseDto> get(
        pathSegments: List<String>,
        queryParameters: Map<String, String> = emptyMap(),
        authenticated: Boolean = false,
    ): ResponseDto {
        val builder = config.baseUrl.toHttpUrl().newBuilder()
        pathSegments.forEach { segment -> builder.addPathSegment(segment) }
        queryParameters.forEach { (name, value) -> builder.addQueryParameter(name, value) }
        return execute(Request.Builder().url(builder.build()).applyAuthentication(authenticated).build())
    }

    private suspend inline fun <reified T> execute(request: Request): T = withContext(Dispatchers.IO) {
        httpClient.newCall(request).execute().use { response ->
            val body = response.body?.string()
                ?: throw ApiClientException(response.code, null, "empty response body")
            if (!response.isSuccessful) {
                throw ApiClientException(response.code, null, body.ifBlank { response.message })
            }
            val apiResponse = json.decodeFromString<BackendApiResponse<T>>(body)
            if (apiResponse.code != 0) {
                throw ApiClientException(response.code, apiResponse.code, apiResponse.message)
            }
            apiResponse.data ?: throw ApiClientException(response.code, apiResponse.code, "empty response data")
        }
    }

    private fun AuthSessionDto.toDomain(): AuthSession = AuthSession(username, token, tokenType)

    private fun ApiHealthStatusDto.toDomain(): ApiHealthStatus = ApiHealthStatus(status, service)

    private fun ContentBookDto.toDomain(): BookSummary =
        BookSummary(bookId, title, filteredTitle, coverUrl, "", chapterCount)

    private fun ContentEpisodeDto.toDomain(): EpisodeSummary = EpisodeSummary(episode, chapterId)

    private fun ContentVideoDto.toDomain(): VideoUrl =
        VideoUrl(videoUrl, "application/vnd.apple.mpegurl", episode, duration)

    private fun WatchRecordDto.toProgressReport(): WatchProgressReport =
        WatchProgressReport(bookId, bookTitle, filteredTitle, episodeNum, chapterId, positionSeconds, durationSeconds,
            progressPercent)

    private fun WatchRecordDto.toWatchRecord(): WatchRecord =
        WatchRecord(bookId, bookTitle, episodeNum, progressPercent)

    private fun PointRecordDto.toDomain(): PointRecord = PointRecord(amount, reason)

    private fun RechargeOrderDto.toDomain(): RechargeOrderSummary =
        RechargeOrderSummary(orderNo, amountCents, pointAmount, status)

    private suspend fun Request.Builder.applyAuthentication(authenticated: Boolean): Request.Builder {
        if (!authenticated) {
            return this
        }
        val token = tokenProvider() ?: throw ApiClientException(0, null, "missing bearer token")
        return header("Authorization", "Bearer $token")
    }

    private companion object {
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
