package com.reelshort.app.network

import com.reelshort.app.data.AuthSession
import com.reelshort.app.data.BookSummary
import com.reelshort.app.data.EpisodeSummary
import com.reelshort.app.data.PointAccount
import com.reelshort.app.data.RechargeOrderSummary
import com.reelshort.app.data.VideoUrl
import com.reelshort.app.data.WatchProgressReport
import com.reelshort.app.data.WatchRecord

interface ReelShortApiClient {
    suspend fun login(username: String, password: String): AuthSession

    suspend fun register(username: String, password: String): AuthSession

    suspend fun getHomeShelf(): List<BookSummary>

    suspend fun search(query: String): List<BookSummary>

    suspend fun getEpisodes(bookId: String, filteredTitle: String): List<EpisodeSummary>

    suspend fun getVideoUrl(bookId: String, episode: Int, filteredTitle: String, chapterId: String): VideoUrl

    suspend fun reportWatchProgress(
        bookId: String,
        bookTitle: String,
        filteredTitle: String,
        episode: Int,
        chapterId: String,
        positionSeconds: Int,
        durationSeconds: Int,
    ): WatchProgressReport

    suspend fun getWatchHistory(): List<WatchRecord>

    suspend fun getPointAccount(): PointAccount

    suspend fun getOrders(): List<RechargeOrderSummary>
}
