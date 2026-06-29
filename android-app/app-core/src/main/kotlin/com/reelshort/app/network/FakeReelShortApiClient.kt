package com.reelshort.app.network

import com.reelshort.app.data.AuthSession
import com.reelshort.app.data.ApiHealthStatus
import com.reelshort.app.data.BookSummary
import com.reelshort.app.data.Comment
import com.reelshort.app.data.EpisodeSummary
import com.reelshort.app.data.PointAccount
import com.reelshort.app.data.PointRecord
import com.reelshort.app.data.RechargeOrderSummary
import com.reelshort.app.data.SocialToggleResult
import com.reelshort.app.data.VideoUrl
import com.reelshort.app.data.WatchEpisodeSnapshot
import com.reelshort.app.data.WatchProgressReport
import com.reelshort.app.data.WatchRecord

class FakeReelShortApiClient : ReelShortApiClient {
    private val books = listOf(
        BookSummary("book-1", "Fated to My Forbidden Alpha", "fated-alpha", null, "狼人、契约和短剧高能反转", 62),
        BookSummary("book-2", "The Billionaire's Secret", "billionaire-secret", null, "都市爱情与身份反转", 48),
        BookSummary("book-3", "My Mafia Protector", "mafia-protector", null, "动作、悬疑和快节奏剧情", 55),
    )

    private val likedBooks = mutableSetOf<String>()
    private val favoriteBooks = mutableSetOf<String>()
    private val comments = mutableListOf<Comment>()

    override suspend fun checkSystemHealth(): ApiHealthStatus =
        ApiHealthStatus(status = "UP", service = "fake-reelshort-backend")

    override suspend fun login(username: String, password: String): AuthSession = AuthSession(
        username = username,
        token = "fake-token-$username",
        tokenType = "Bearer",
    )

    override suspend fun register(username: String, password: String): AuthSession = login(username, password)

    override suspend fun getHomeShelf(): List<BookSummary> = books

    override suspend fun search(query: String): List<BookSummary> =
        books.filter { it.title.contains(query, ignoreCase = true) || query.isBlank() }

    override suspend fun getEpisodes(bookId: String, filteredTitle: String): List<EpisodeSummary> =
        (1..8).map {
            EpisodeSummary(
                number = it,
                chapterId = "chapter-$it",
                title = "第 $it 集",
                description = "短剧第 $it 集剧情简介",
                durationSeconds = 180 + it * 12,
            )
        }

    override suspend fun getVideoUrl(bookId: String, episode: Int, filteredTitle: String, chapterId: String): VideoUrl =
        VideoUrl("https://springboot.local/video/$bookId/$episode.m3u8", "application/vnd.apple.mpegurl", episode, 180)

    override suspend fun getEpisodeSnapshot(bookId: String, episode: Int): WatchEpisodeSnapshot =
        WatchEpisodeSnapshot.empty(bookId, episode)

    override suspend fun reportWatchProgress(
        bookId: String,
        bookTitle: String,
        filteredTitle: String,
        episode: Int,
        chapterId: String,
        positionSeconds: Int,
        durationSeconds: Int,
    ): WatchProgressReport {
        val progressPercent = if (durationSeconds <= 0) 0 else (positionSeconds * 100 / durationSeconds).coerceIn(0, 100)
        return WatchProgressReport(bookId, bookTitle, filteredTitle, episode, chapterId,
            positionSeconds, durationSeconds, progressPercent)
    }

    override suspend fun getWatchHistory(): List<WatchRecord> =
        listOf(WatchRecord("book-1", "Fated to My Forbidden Alpha", 3, 58))

    override suspend fun getPointAccount(): PointAccount = PointAccount(
        balance = 18,
        records = listOf(
            PointRecord(1, "观看达到 25%"),
            PointRecord(1, "观看达到 50%"),
            PointRecord(10, "后台活动赠送"),
        ),
    )

    override suspend fun getOrders(): List<RechargeOrderSummary> =
        listOf(RechargeOrderSummary("RO202606270001", 990, 99, "CREATED"))

    override suspend fun toggleLike(bookId: String): SocialToggleResult {
        if (!likedBooks.add(bookId)) likedBooks.remove(bookId)
        return SocialToggleResult(likedBooks.contains(bookId), likedBooks.size)
    }

    override suspend fun getLikeStatus(bookId: String): SocialToggleResult =
        SocialToggleResult(likedBooks.contains(bookId), likedBooks.count { true })

    override suspend fun toggleFavorite(
        bookId: String,
        bookTitle: String,
        filteredTitle: String,
        coverUrl: String?,
        chapterCount: Int,
    ): SocialToggleResult {
        if (!favoriteBooks.add(bookId)) favoriteBooks.remove(bookId)
        return SocialToggleResult(favoriteBooks.contains(bookId), favoriteBooks.size)
    }

    override suspend fun getFavoriteStatus(bookId: String): SocialToggleResult =
        SocialToggleResult(favoriteBooks.contains(bookId), favoriteBooks.count { true })

    override suspend fun addComment(bookId: String, content: String): Comment {
        val comment = Comment("comment-${comments.size + 1}", "guest", content, "2026-06-29T00:00:00Z")
        comments.add(comment)
        return comment
    }

    override suspend fun listComments(bookId: String): List<Comment> = comments.toList()

    override suspend fun listMyFavorites(): List<BookSummary> =
        books.filter { it.id in favoriteBooks }
}
