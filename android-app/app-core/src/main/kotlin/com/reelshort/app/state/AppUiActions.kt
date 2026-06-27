package com.reelshort.app.state

import com.reelshort.app.data.BookSummary
import com.reelshort.app.data.EpisodeSummary

class AppUiActions(private val controller: AppStateController) {

    val state = controller.state

    suspend fun restoreSession() = controller.restoreSession()

    suspend fun login(username: String, password: String) = controller.login(username, password)

    suspend fun register(username: String, password: String) = controller.register(username, password)

    suspend fun refreshHome() = controller.refreshHome()

    suspend fun search(query: String) = controller.search(query)

    fun showSearch() = controller.showSearch()

    suspend fun openBook(book: BookSummary) = controller.openBook(book)

    suspend fun openPlayer(episode: EpisodeSummary) = controller.openPlayer(episode)

    fun updatePlaybackPosition(positionSeconds: Int, durationSeconds: Int) =
        controller.updatePlaybackPosition(positionSeconds, durationSeconds)

    suspend fun refreshPlaybackUrl() = controller.refreshPlaybackUrl()

    suspend fun reportProgress(positionSeconds: Int, durationSeconds: Int) =
        controller.reportProgress(positionSeconds, durationSeconds)

    suspend fun loadAccount() = controller.loadAccountSnapshot()

    suspend fun logout() = controller.logout()

    fun clearError() = controller.clearError()
}
