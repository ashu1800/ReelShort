package com.reelshort.app.ui.screens.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.reelshort.app.data.AppLanguage
import com.reelshort.app.data.BookSummary
import com.reelshort.app.data.EpisodeSummary
import com.reelshort.app.ui.components.EpisodeRow
import com.reelshort.app.ui.components.EmptyState
import com.reelshort.app.ui.components.MetaPill
import com.reelshort.app.ui.components.PosterBlock
import com.reelshort.app.ui.components.SectionHeader
import com.reelshort.app.ui.components.SurfacePanel
import com.reelshort.app.ui.format.detailEmptyState
import com.reelshort.app.ui.format.strings
import com.reelshort.app.ui.theme.TextSecondary

@Composable
internal fun DetailScreen(
    book: BookSummary?,
    episodes: List<EpisodeSummary>,
    language: AppLanguage,
    vipUntil: String?,
    onOpenPlayer: (EpisodeSummary) -> Unit,
) {
    val copy = strings(language)
    val isVip = !vipUntil.isNullOrBlank()
    val emptyState = detailEmptyState(book, episodes.size, language)
    if (book == null) {
        EmptyState(emptyState ?: return)
        return
    }
    LazyColumn(contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            BookHero(book)
        }
        item {
            SectionHeader(copy.detailScreen, "${copy.playerEpisodeSelectorTotalPrefix}${episodes.size}${copy.playerEpisodeSelectorTotalSuffix}")
        }
        if (emptyState != null) {
            item { EmptyState(emptyState) }
        }
        items(episodes, key = { "${it.chapterId}-${it.number}" }) { episode ->
            EpisodeRow(
                episode = episode,
                bookDescription = book.description,
                language = language,
                locked = !isVip && episode.number > 7,
                onClick = { onOpenPlayer(episode) },
            )
        }
    }
}

@Composable
private fun BookHero(book: BookSummary) {
    SurfacePanel {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            PosterBlock(book.title, book.coverUrl, Modifier.size(width = 92.dp, height = 124.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(book.title, style = MaterialTheme.typography.headlineSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(book.description.ifBlank { "${book.chapterCount} short dramas" }, color = TextSecondary, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    MetaPill("${book.chapterCount} eps")
            }
        }
    }
}
