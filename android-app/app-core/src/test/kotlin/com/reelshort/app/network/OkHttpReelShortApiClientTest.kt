package com.reelshort.app.network

import com.reelshort.app.config.ApiConfig
import com.reelshort.app.data.AuthSession
import com.reelshort.app.data.WatchRewardStatus
import com.reelshort.app.session.InMemorySessionStore
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.OkHttpClient
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class OkHttpReelShortApiClientTest {
    @Test
    fun systemHealthCheckUsesPublicEndpointWithoutBearerToken() = runTest {
        MockWebServer().use { server ->
            server.enqueue(successBody("""{ "status": "UP", "service": "reelshort-backend" }"""))
            val client = client(server, token = "token-123")

            val status = client.checkSystemHealth()
            val request = server.takeRequest()

            assertEquals("/api/system/health", request.path)
            assertEquals(null, request.getHeader("Authorization"))
            assertEquals("UP", status.status)
            assertEquals("reelshort-backend", status.service)
        }
    }

    @Test
    fun backendErrorCodeThrowsApiClientException() = runTest {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setBody("""
                        {
                          "code": 401,
                          "message": "invalid credentials",
                          "data": null,
                          "requestId": "req-1",
                          "timestamp": "2026-06-27T15:30:00+08:00"
                        }
                    """.trimIndent())
            )
            val client = OkHttpReelShortApiClient(
                config = ApiConfig(server.url("/api/app").toString()),
                httpClient = OkHttpClient(),
            )

            val error = assertFailsWith<ApiClientException> {
                client.login("demo", "bad-password")
            }

            assertEquals(200, error.statusCode)
            assertEquals(401, error.code)
            assertEquals("invalid credentials", error.message)
        }
    }

    @Test
    fun httpErrorResponseExtractsBackendMessage() = runTest {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse()
                    .setResponseCode(401)
                    .setBody("""
                        {
                          "code": 401,
                          "message": "invalid username or password",
                          "path": "/api/app/auth/login",
                          "requestId": "req-1",
                          "timestamp": "2026-06-29T10:20:00+08:00"
                        }
                    """.trimIndent())
            )
            val client = OkHttpReelShortApiClient(
                config = ApiConfig(server.url("/api/app").toString()),
                httpClient = OkHttpClient(),
            )

            val error = assertFailsWith<ApiClientException> {
                client.login("demo", "bad-password")
            }

            assertEquals(401, error.statusCode)
            assertEquals(401, error.code)
            assertEquals("invalid username or password", error.message)
        }
    }

    @Test
    fun loginPostsCredentialsAndParsesSession() = runTest {
        MockWebServer().use { server ->
            server.enqueue(successBody("""
                {
                  "username": "demo",
                  "token": "token-123",
                  "tokenType": "Bearer"
                }
            """.trimIndent()))
            val client = client(server)

            val session = client.login("demo", "Password123")
            val request = server.takeRequest()

            assertEquals("/api/app/auth/login", request.path)
            assertEquals("POST", request.method)
            assertEquals("""{"username":"demo","password":"Password123"}""", request.body.readUtf8())
            assertEquals("demo", session.username)
            assertEquals("token-123", session.token)
        }
    }

    @Test
    fun homeShelfParsesBooksFromSpringBootResponse() = runTest {
        MockWebServer().use { server ->
            server.enqueue(successBody("""
                [
                  {
                    "bookId": "book-1",
                    "title": "Alpha",
                    "filteredTitle": "alpha",
                    "coverUrl": "https://example.com/alpha.jpg",
                    "description": "Alpha description.",
                    "chapterCount": 12
                  }
                ]
            """.trimIndent()))
            val client = client(server, token = "token-123")

            val books = client.getHomeShelf("zh-TW")
            val request = server.takeRequest()

            assertEquals("/api/app/home/recommend?locale=zh-TW", request.path)
            assertEquals(null, request.getHeader("Authorization"))
            assertEquals("book-1", books.single().id)
            assertEquals("alpha", books.single().filteredTitle)
            assertEquals("https://example.com/alpha.jpg", books.single().coverUrl)
            assertEquals("Alpha description.", books.single().description)
        }
    }

    @Test
    fun protectedRequestsCanReadBearerTokenFromSessionStore() = runTest {
        MockWebServer().use { server ->
            server.enqueue(successBody("""{ "balance": 18 }"""))
            server.enqueue(successBody("[]"))
            val sessionStore = InMemorySessionStore(
                AuthSession(username = "demo", token = "stored-token", tokenType = "Bearer"),
            )
            val client = OkHttpReelShortApiClient(
                config = ApiConfig(server.url("/api/app").toString()),
                httpClient = OkHttpClient(),
                tokenProvider = { sessionStore.loadSession()?.token },
            )

            client.getPointAccount()
            val request = server.takeRequest()

            assertEquals("/api/app/points/account", request.path)
            assertEquals("Bearer stored-token", request.getHeader("Authorization"))
        }
    }

    @Test
    fun fetchCaptchaUsesPublicEndpointWithoutBearerToken() = runTest {
        MockWebServer().use { server ->
            server.enqueue(successBody("""
                {
                  "captchaId": "captcha-1",
                  "imageBase64": "base64-image"
                }
            """.trimIndent()))
            val client = client(server, token = "token-123")

            val challenge = client.fetchCaptcha()
            val request = server.takeRequest()

            assertEquals("/api/app/auth/captcha", request.path)
            assertEquals("GET", request.method)
            assertEquals(null, request.getHeader("Authorization"))
            assertEquals("captcha-1", challenge.captchaId)
            assertEquals("base64-image", challenge.imageBase64)
        }
    }

    @Test
    fun searchEncodesKeywordQueryParameter() = runTest {
        MockWebServer().use { server ->
            server.enqueue(successBody("[]"))
            val client = client(server, token = "token-123")

            val result = client.search("Alpha Love", "zh-TW")
            val request = server.takeRequest()

            assertEquals("/api/app/content/search?keywords=Alpha%20Love&locale=zh-TW", request.path)
            assertEquals(null, request.getHeader("Authorization"))
            assertEquals(emptyList(), result)
        }
    }

    @Test
    fun bookDetailUsesPublicContentEndpointWithLocale() = runTest {
        MockWebServer().use { server ->
            server.enqueue(successBody("""
                {
                  "bookId": "book 1",
                  "title": "Alpha",
                  "filteredTitle": "alpha",
                  "coverUrl": "https://example.com/alpha.jpg",
                  "description": "Alpha description.",
                  "chapterCount": 12
                }
            """.trimIndent()))
            val client = client(server, token = "token-123")

            val book = client.getBook("book 1", "zh-TW")
            val request = server.takeRequest()

            assertEquals("/api/app/content/books/book%201?locale=zh-TW", request.path)
            assertEquals(null, request.getHeader("Authorization"))
            assertEquals("book 1", book.id)
            assertEquals("Alpha", book.title)
            assertEquals("alpha", book.filteredTitle)
        }
    }

    @Test
    fun contentEpisodesArePublicButVideoUrlRequiresBearerToken() = runTest {
        MockWebServer().use { server ->
            server.enqueue(successBody("""
                [
                  { "episode": 1, "chapterId": "chapter-1", "title": "Opening Trap", "description": "A deal goes wrong." }
                ]
            """.trimIndent()))
            server.enqueue(successBody("""
                {
                  "videoUrl": "https://cdn.example.com/book-1/1.m3u8",
                  "episode": 1,
                  "duration": 180,
                  "nextEpisode": { "episode": 2, "chapterId": "chapter-2" }
                }
            """.trimIndent()))
            val client = client(server, token = "token-123")

            val episodes = client.getEpisodes("book 1", "alpha", "zh-TW")
            val video = client.getVideoUrl("book 1", 1, "alpha", "chapter 1", "zh-TW")
            val episodesRequest = server.takeRequest()
            val videoRequest = server.takeRequest()

            assertEquals("/api/app/content/books/book%201/episodes?filteredTitle=alpha&locale=zh-TW", episodesRequest.path)
            assertEquals(null, episodesRequest.getHeader("Authorization"))
            assertEquals(1, episodes.single().number)
            assertEquals("chapter-1", episodes.single().chapterId)
            assertEquals("Opening Trap", episodes.single().title)
            assertEquals("A deal goes wrong.", episodes.single().description)
            assertEquals("/api/app/content/books/book%201/episodes/1/play?filteredTitle=alpha&chapterId=chapter%201&locale=zh-TW", videoRequest.path)
            assertEquals("Bearer token-123", videoRequest.getHeader("Authorization"))
            assertEquals("https://cdn.example.com/book-1/1.m3u8", video.url)
            assertEquals(180, video.durationSeconds)
        }
    }

    @Test
    fun episodeSnapshotMapsPlaybackAndRewardStages() = runTest {
        MockWebServer().use { server ->
            server.enqueue(successBody("""
                {
                  "bookId": "book-1",
                  "episodeNum": 2,
                  "positionSeconds": 90,
                  "durationSeconds": 120,
                  "progressPercent": 75,
                  "awardedStages": [],
                  "rewardClaimed": true,
                  "rewardStatus": "AWARDED",
                  "awardedPoints": 2
                }
            """.trimIndent()))
            val client = client(server, token = "token-123")

            val snapshot = client.getEpisodeSnapshot("book-1", 2)
            val request = server.takeRequest()

            assertEquals("/api/app/watch/books/book-1/episodes/2/snapshot", request.path)
            assertEquals("Bearer token-123", request.getHeader("Authorization"))
            assertEquals(90, snapshot.positionSeconds)
            assertEquals(75, snapshot.progressPercent)
            assertEquals(emptyList(), snapshot.awardedStages)
            assertEquals(true, snapshot.rewardClaimed)
            assertEquals(WatchRewardStatus.AWARDED, snapshot.rewardStatus)
            assertEquals(2, snapshot.awardedPoints)
        }
    }

    @Test
    fun watchProgressPostsBackendContractFields() = runTest {
        MockWebServer().use { server ->
            server.enqueue(successBody("""
                {
                  "id": "00000000-0000-0000-0000-000000000001",
                  "bookId": "book-1",
                  "bookTitle": "Alpha",
                  "filteredTitle": "alpha",
                  "episodeNum": 1,
                  "chapterId": "chapter-1",
                  "positionSeconds": 90,
                  "durationSeconds": 180,
                  "progressPercent": 50,
                  "awardedStages": [],
                  "rewardClaimed": true,
                  "rewardStatus": "AWARDED",
                  "awardedPoints": 3,
                  "updatedAt": "2026-06-27T15:30:00+08:00"
                }
            """.trimIndent()))
            val client = client(server, token = "token-123")

            val report = client.reportWatchProgress(
                bookId = "book-1",
                bookTitle = "Alpha",
                filteredTitle = "alpha",
                episode = 1,
                chapterId = "chapter-1",
                positionSeconds = 90,
                durationSeconds = 180,
            )
            val request = server.takeRequest()

            assertEquals("/api/app/watch/progress", request.path)
            assertEquals("Bearer token-123", request.getHeader("Authorization"))
            assertEquals(
                """{"bookId":"book-1","bookTitle":"Alpha","filteredTitle":"alpha","episodeNum":1,"chapterId":"chapter-1","positionSeconds":90,"durationSeconds":180}""",
                request.body.readUtf8(),
            )
            assertEquals(50, report.progressPercent)
            assertEquals("chapter-1", report.chapterId)
            assertEquals(true, report.rewardClaimed)
            assertEquals(WatchRewardStatus.AWARDED, report.rewardStatus)
            assertEquals(3, report.awardedPoints)
        }
    }

    @Test
    fun legacyEpisodeSnapshotDefaultsMissingRewardFields() = runTest {
        MockWebServer().use { server ->
            server.enqueue(successBody("""
                {
                  "bookId": "book-1",
                  "episodeNum": 2,
                  "positionSeconds": 30,
                  "durationSeconds": 120,
                  "progressPercent": 25,
                  "awardedStages": [25]
                }
            """.trimIndent()))
            val client = client(server, token = "token-123")

            val snapshot = client.getEpisodeSnapshot("book-1", 2)

            assertEquals(false, snapshot.rewardClaimed)
            assertEquals(WatchRewardStatus.NOT_COMPLETE, snapshot.rewardStatus)
            assertEquals(0, snapshot.awardedPoints)
        }
    }

    @Test
    fun legacyCompletedSnapshotIsTreatedAsAlreadyClaimed() = runTest {
        MockWebServer().use { server ->
            server.enqueue(successBody("""
                {
                  "bookId": "book-1",
                  "episodeNum": 2,
                  "positionSeconds": 120,
                  "durationSeconds": 120,
                  "progressPercent": 100,
                  "awardedStages": [25, 50, 75, 100]
                }
            """.trimIndent()))
            val client = client(server, token = "token-123")

            val snapshot = client.getEpisodeSnapshot("book-1", 2)

            assertEquals(true, snapshot.rewardClaimed)
            assertEquals(WatchRewardStatus.ALREADY_CLAIMED, snapshot.rewardStatus)
        }
    }

    @Test
    fun withdrawalSummaryMapsCurrencyConversionAndKeepsOldResponseCompatible() = runTest {
        MockWebServer().use { server ->
            server.enqueue(successBody("""
                {
                  "balance": 5000,
                  "frozenPoints": 200,
                  "availablePoints": 4800,
                  "minimumPoints": 3600,
                  "usdtPerPoint": "0.002777778",
                  "cnyPerPoint": "0.02",
                  "cnyPerUsd": "7.2",
                  "minimumUsd": "10",
                  "walletAddress": "TTest"
                }
            """.trimIndent()))
            server.enqueue(successBody("""
                {
                  "balance": 100,
                  "frozenPoints": 0,
                  "availablePoints": 100,
                  "minimumPoints": 50,
                  "usdtPerPoint": "0.001",
                  "walletAddress": null
                }
            """.trimIndent()))
            val client = client(server, token = "token-123")

            val current = client.getWithdrawalSummary()
            val legacy = client.getWithdrawalSummary()

            assertEquals("0.02", current.cnyPerPoint)
            assertEquals("7.2", current.cnyPerUsd)
            assertEquals("10", current.minimumUsd)
            assertEquals(null, legacy.cnyPerPoint)
            assertEquals(null, legacy.cnyPerUsd)
            assertEquals(null, legacy.minimumUsd)
        }
    }

    @Test
    fun pointsAndOrdersAreMappedFromProtectedEndpoints() = runTest {
        MockWebServer().use { server ->
            server.enqueue(successBody("""{ "balance": 18 }"""))
            server.enqueue(successBody("""
                [
                  {
                    "id": "00000000-0000-0000-0000-000000000002",
                    "amount": 1,
                    "balanceAfter": 18,
                    "source": "WATCH_REWARD",
                    "bookId": "book-1",
                    "episodeNum": 1,
                    "stage": 50,
                    "reason": "watch",
                    "createdAt": "2026-06-27T15:30:00+08:00"
                  }
                ]
            """.trimIndent()))
            server.enqueue(successBody("""
                [
                  {
                    "id": "00000000-0000-0000-0000-000000000003",
                    "userId": "00000000-0000-0000-0000-000000000004",
                    "orderNo": "RO202606270001",
                    "amountCents": 990,
                    "pointAmount": 99,
                    "status": "CREATED",
                    "paymentChannel": null,
                    "createdAt": "2026-06-27T15:30:00+08:00",
                    "updatedAt": "2026-06-27T15:30:00+08:00"
                  }
                ]
            """.trimIndent()))
            val client = client(server, token = "token-123")

            val account = client.getPointAccount()
            val orders = client.getOrders()

            assertEquals(18, account.balance)
            assertEquals(1, account.records.single().amount)
            assertEquals("RO202606270001", orders.single().orderNo)
            assertEquals(99, orders.single().pointAmount)
            assertEquals("/api/app/points/account", server.takeRequest().path)
            assertEquals("/api/app/points/records", server.takeRequest().path)
            assertEquals("/api/app/orders", server.takeRequest().path)
        }
    }

    @Test
    fun toggleLikePostsAuthenticatedAndParsesResult() = runTest {
        MockWebServer().use { server ->
            server.enqueue(successBody("""{ "active": true, "count": 1 }"""))
            val client = client(server, token = "token-123")

            val result = client.toggleLike("book-1")

            val request = server.takeRequest()
            assertEquals("POST", request.method)
            assertEquals("/api/app/social/books/book-1/like", request.path)
            assertEquals("Bearer token-123", request.getHeader("Authorization"))
            assertEquals(true, result.active)
            assertEquals(1, result.count)
        }
    }

    @Test
    fun listCommentsReadsGuestEndpointWithoutBearerToken() = runTest {
        MockWebServer().use { server ->
            server.enqueue(successBody("""
                [
                  {
                    "id": "00000000-0000-0000-0000-000000000010",
                    "username": "social-carol",
                    "content": "nice drama",
                    "createdAt": "2026-06-29T10:28:00Z"
                  }
                ]
            """.trimIndent()))
            val client = client(server, token = null)

            val comments = client.listComments("book-1")

            val request = server.takeRequest()
            assertEquals("/api/app/social/books/book-1/comments", request.path)
            assertEquals(null, request.getHeader("Authorization"))
            assertEquals(1, comments.size)
            assertEquals("nice drama", comments.single().content)
        }
    }

    @Test
    fun addCommentPostsAuthenticatedBody() = runTest {
        MockWebServer().use { server ->
            server.enqueue(successBody("""
                {
                  "id": "00000000-0000-0000-0000-000000000011",
                  "username": "demo",
                  "content": "great",
                  "createdAt": "2026-06-29T10:28:00Z"
                }
            """.trimIndent()))
            val client = client(server, token = "token-123")

            val comment = client.addComment("book-1", "great")

            val request = server.takeRequest()
            assertEquals("POST", request.method)
            assertEquals("/api/app/social/books/book-1/comments", request.path)
            val body = request.body.readUtf8()
            assert(body.contains("\"content\":\"great\""))
            assertEquals("great", comment.content)
        }
    }

    @Test
    fun toggleFavoritePostsSnapshotBodyAndParsesResult() = runTest {
        MockWebServer().use { server ->
            server.enqueue(successBody("""{ "active": true, "count": 1 }"""))
            val client = client(server, token = "token-123")

            val result = client.toggleFavorite("book-1", "Love Story", "love-story", "http://cover", 12)

            val request = server.takeRequest()
            assertEquals("/api/app/social/books/book-1/favorite", request.path)
            val body = request.body.readUtf8()
            assert(body.contains("\"bookTitle\":\"Love Story\""))
            assert(body.contains("\"chapterCount\":12"))
            assertEquals(true, result.active)
        }
    }

    @Test
    fun listMyFavoritesMapsToBookSummary() = runTest {
        MockWebServer().use { server ->
            server.enqueue(successBody("""
                [
                  {
                    "bookId": "book-fav",
                    "bookTitle": "Love Story",
                    "filteredTitle": "love-story",
                    "coverUrl": "http://cover",
                    "chapterCount": 12,
                    "createdAt": "2026-06-29T10:28:00Z"
                  }
                ]
            """.trimIndent()))
            val client = client(server, token = "token-123")

            val favorites = client.listMyFavorites()

            assertEquals("/api/app/social/my/favorites", server.takeRequest().path)
            assertEquals(1, favorites.size)
            assertEquals("book-fav", favorites.single().id)
            assertEquals(12, favorites.single().chapterCount)
        }
    }

    private fun client(server: MockWebServer, token: String? = null): OkHttpReelShortApiClient =
        OkHttpReelShortApiClient(
            config = ApiConfig(server.url("/api/app").toString()),
            httpClient = OkHttpClient(),
            tokenProvider = { token },
        )

    private fun successBody(dataJson: String): MockResponse = MockResponse()
        .setResponseCode(200)
        .setBody("""
            {
              "code": 0,
              "message": "success",
              "data": $dataJson,
              "requestId": "req-1",
              "timestamp": "2026-06-27T15:30:00+08:00"
            }
        """.trimIndent())

}
