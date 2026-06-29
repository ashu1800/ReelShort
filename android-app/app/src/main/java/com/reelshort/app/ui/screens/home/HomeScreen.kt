package com.reelshort.app.ui.screens.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.reelshort.app.data.BookSummary
import com.reelshort.app.ui.components.BookRow
import com.reelshort.app.ui.components.EmptyState
import com.reelshort.app.ui.components.SectionHeader
import com.reelshort.app.ui.format.homeEmptyState

@Composable
internal fun HomeScreen(books: List<BookSummary>, onOpenBook: (BookSummary) -> Unit) {
    LazyColumn(contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            SectionHeader("今日推荐", "为你整理 ${books.size} 部短剧")
        }
        if (books.isEmpty()) {
            item { EmptyState(homeEmptyState()) }
        }
        items(books, key = { it.id }) { book ->
            BookRow(book = book, onClick = { onOpenBook(book) })
        }
    }
}
