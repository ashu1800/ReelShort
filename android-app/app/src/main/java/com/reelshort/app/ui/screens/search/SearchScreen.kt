package com.reelshort.app.ui.screens.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.reelshort.app.data.BookSummary
import com.reelshort.app.state.AppUiState
import com.reelshort.app.ui.components.BookRow
import com.reelshort.app.ui.components.EmptyState
import com.reelshort.app.ui.components.SectionHeader
import com.reelshort.app.ui.components.SurfacePanel
import com.reelshort.app.ui.components.goldTextFieldColors
import com.reelshort.app.ui.format.searchEmptyState
import com.reelshort.app.ui.theme.OnPrimaryDark
import com.reelshort.app.ui.theme.PrimaryGold

@Composable
internal fun SearchScreen(
    state: AppUiState,
    onSearch: (String) -> Unit,
    onOpenBook: (BookSummary) -> Unit,
) {
    var query by remember(state.searchQuery) { mutableStateOf(state.searchQuery) }

    LazyColumn(contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            SectionHeader("搜索短剧", "输入标题或关键词")
        }
        item {
            SurfacePanel(contentPadding = PaddingValues(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        label = { Text("搜索") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        colors = goldTextFieldColors(),
                    )
                    Spacer(Modifier.width(10.dp))
                    Button(
                        onClick = { onSearch(query) },
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryGold, contentColor = OnPrimaryDark),
                    ) {
                        Text("搜索")
                    }
                }
            }
        }
        val emptyState = searchEmptyState(state.searchQuery, state.searchResults.size)
        if (emptyState != null) {
            item { EmptyState(emptyState) }
        }
        items(state.searchResults, key = { it.id }) { book ->
            BookRow(book = book, onClick = { onOpenBook(book) })
        }
    }
}
