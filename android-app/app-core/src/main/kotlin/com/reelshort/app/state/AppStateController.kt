package com.reelshort.app.state

import com.reelshort.app.data.AppDataSource
import com.reelshort.app.data.BookSummary
import com.reelshort.app.data.EpisodeSummary
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class AppStateController(private val dataSource: AppDataSource) {
    private val mutableState = MutableStateFlow(AppUiState())

    val state: StateFlow<AppUiState> = mutableState.asStateFlow()

    suspend fun restoreSession() = runWithLoading {
        val session = dataSource.restoreSession()
        if (session == null) {
            mutableState.update {
                it.copy(
                    screen = AppScreen.LOGIN,
                    session = null,
                    isLoading = false,
                )
            }
            return@runWithLoading
        }

        try {
            val homeShelf = dataSource.loadHomeShelf()
            mutableState.update {
                it.copy(
                    screen = AppScreen.HOME,
                    session = session,
                    homeShelf = homeShelf,
                    isLoading = false,
                )
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            dataSource.clearSession()
            throw error
        }
    }

    suspend fun login(username: String, password: String) = runWithLoading {
        val session = dataSource.login(username, password)
        val homeShelf = dataSource.loadHomeShelf()
        mutableState.update {
            it.copy(
                screen = AppScreen.HOME,
                session = session,
                homeShelf = homeShelf,
                isLoading = false,
            )
        }
    }

    suspend fun register(username: String, password: String) = runWithLoading {
        val session = dataSource.register(username, password)
        val homeShelf = dataSource.loadHomeShelf()
        mutableState.update {
            it.copy(
                screen = AppScreen.HOME,
                session = session,
                homeShelf = homeShelf,
                isLoading = false,
            )
        }
    }

    suspend fun refreshHome() = runWithLoading {
        val homeShelf = dataSource.loadHomeShelf()
        mutableState.update {
            it.copy(
                screen = AppScreen.HOME,
                homeShelf = homeShelf,
                isLoading = false,
            )
        }
    }

    suspend fun search(query: String) = runWithLoading {
        val results = dataSource.search(query)
        mutableState.update {
            it.copy(
                screen = AppScreen.SEARCH,
                searchQuery = query,
                searchResults = results,
                isLoading = false,
            )
        }
    }

    suspend fun openBook(book: BookSummary) = runWithLoading {
        val episodes = dataSource.loadEpisodes(book)
        mutableState.update {
            it.copy(
                screen = AppScreen.DETAIL,
                selectedBook = book,
                episodes = episodes,
                selectedEpisode = null,
                currentVideoUrl = null,
                isLoading = false,
            )
        }
    }

    suspend fun openPlayer(episode: EpisodeSummary) = runWithLoading {
        val book = requireSelectedBook()
        val videoUrl = dataSource.loadVideoUrl(book, episode)
        mutableState.update {
            it.copy(
                screen = AppScreen.PLAYER,
                selectedEpisode = episode,
                currentVideoUrl = videoUrl,
                isLoading = false,
            )
        }
    }

    suspend fun reportProgress(positionSeconds: Int, durationSeconds: Int) = runWithLoading {
        val current = state.value
        val book = current.selectedBook ?: throw IllegalStateException("未选择剧集")
        val episode = current.selectedEpisode ?: throw IllegalStateException("未选择分集")
        dataSource.reportWatchProgress(book, episode, positionSeconds, durationSeconds)
        val history = dataSource.loadWatchHistory()
        val points = dataSource.loadPointAccount()
        mutableState.update {
            it.copy(
                watchHistory = history,
                pointAccount = points,
                isLoading = false,
            )
        }
    }

    suspend fun loadAccountSnapshot() = runWithLoading {
        val history = dataSource.loadWatchHistory()
        val points = dataSource.loadPointAccount()
        val orders = dataSource.loadOrders()
        mutableState.update {
            it.copy(
                screen = AppScreen.ACCOUNT,
                watchHistory = history,
                pointAccount = points,
                orders = orders,
                isLoading = false,
            )
        }
    }

    fun clearError() {
        mutableState.update { it.copy(errorMessage = null) }
    }

    suspend fun logout() = runWithLoading {
        dataSource.clearSession()
        mutableState.value = AppUiState()
    }

    private suspend fun runWithLoading(block: suspend () -> Unit) {
        mutableState.update { it.copy(isLoading = true, errorMessage = null) }
        try {
            block()
        } catch (error: CancellationException) {
            mutableState.update { it.copy(isLoading = false) }
            throw error
        } catch (error: Throwable) {
            mutableState.update {
                it.copy(
                    isLoading = false,
                    errorMessage = error.message ?: error::class.simpleName ?: "请求失败",
                )
            }
        }
    }

    private fun requireSelectedBook(): BookSummary =
        state.value.selectedBook ?: throw IllegalStateException("未选择剧集")
}
