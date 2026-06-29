package com.reelshort.app.ui.screens.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.reelshort.app.data.BookSummary
import com.reelshort.app.ui.components.EmptyState
import com.reelshort.app.ui.components.PosterCard
import com.reelshort.app.ui.components.SectionHeader
import com.reelshort.app.ui.format.homeEmptyState

@Composable
internal fun HomeScreen(books: List<BookSummary>, onOpenBook: (BookSummary) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            SectionHeader("今日推荐", "为你整理 ${books.size} 部短剧")
        }
        if (books.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) { EmptyState(homeEmptyState()) }
        }
        items(books, key = { it.id }) { book ->
            PosterCard(book = book, onClick = { onOpenBook(book) })
        }
    }
}
