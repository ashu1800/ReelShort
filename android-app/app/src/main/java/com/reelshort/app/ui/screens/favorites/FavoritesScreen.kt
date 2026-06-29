package com.reelshort.app.ui.screens.favorites

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.reelshort.app.data.BookSummary
import com.reelshort.app.ui.components.EmptyState
import com.reelshort.app.ui.components.PosterCard
import com.reelshort.app.ui.components.SectionHeader
import com.reelshort.app.ui.format.favoritesEmptyState

@Composable
internal fun FavoritesScreen(favorites: List<BookSummary>, onOpenBook: (BookSummary) -> Unit, onBack: () -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            SectionHeader("我的收藏", "已收藏 ${favorites.size} 部短剧")
        }
        if (favorites.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) { EmptyState(favoritesEmptyState()) }
        }
        items(favorites, key = { it.id }) { book ->
            PosterCard(book = book, onClick = { onOpenBook(book) })
        }
    }
}
