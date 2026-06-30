package com.reelshort.app.ui.screens.search

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.reelshort.app.data.BookSummary
import com.reelshort.app.state.AppUiState
import com.reelshort.app.ui.components.EmptyState
import com.reelshort.app.ui.components.PosterCard
import com.reelshort.app.ui.components.SurfacePanel
import com.reelshort.app.ui.components.goldTextFieldColors
import com.reelshort.app.ui.format.AppStrings
import com.reelshort.app.ui.format.SearchDiscoveryGroup
import com.reelshort.app.ui.format.searchDiscoveryGroups
import com.reelshort.app.ui.format.searchEmptyState
import com.reelshort.app.ui.format.strings
import com.reelshort.app.ui.theme.Divider
import com.reelshort.app.ui.theme.GoldStroke
import com.reelshort.app.ui.theme.GoldSurfaceStrong
import com.reelshort.app.ui.theme.OnPrimaryDark
import com.reelshort.app.ui.theme.Panel
import com.reelshort.app.ui.theme.PanelSoft
import com.reelshort.app.ui.theme.PillShape
import com.reelshort.app.ui.theme.PrimaryGold
import com.reelshort.app.ui.theme.TextPrimary
import com.reelshort.app.ui.theme.TextSecondary

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SearchScreen(
    state: AppUiState,
    onSearch: (String) -> Unit,
    onOpenBook: (BookSummary) -> Unit,
) {
    val copy = strings(state.language)
    val groups = searchDiscoveryGroups(state.language)
    var query by remember(state.searchQuery, state.language) { mutableStateOf(state.searchQuery) }

    fun submit(value: String) {
        val trimmed = value.trim()
        if (trimmed.isNotBlank()) {
            onSearch(trimmed)
        }
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(start = 18.dp, top = 18.dp, end = 18.dp, bottom = 112.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            SearchHeroCard(
                copy = copy,
                query = query,
                onQueryChange = { query = it },
                onSubmit = { submit(query) },
            )
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            DiscoveryGroups(
                title = copy.searchTagsTitle,
                groups = groups,
                onTagSelected = { tag ->
                    query = tag
                    submit(tag)
                },
            )
        }
        if (state.searchQuery.isNotBlank() || state.searchResults.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                SearchResultHeader(copy, state.searchQuery, state.searchResults.size)
            }
        }
        val emptyState = searchEmptyState(state.searchQuery, state.searchResults.size)
        if (emptyState != null || (state.searchQuery.isBlank() && state.searchResults.isEmpty())) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    if (emptyState != null) {
                        EmptyState(emptyState.copy(title = copy.searchEmptyTitle, message = copy.searchEmpty))
                    } else {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = Panel.copy(alpha = 0.7f),
                            shape = RoundedCornerShape(18.dp),
                        ) {
                            Text(
                                copy.searchInitialHint,
                                color = TextSecondary,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(16.dp),
                            )
                        }
                    }
                }
            }
        }
        items(state.searchResults, key = { it.id }) { book ->
            PosterCard(book = book, onClick = { onOpenBook(book) })
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Spacer(Modifier.height(4.dp))
        }
    }
}

@Composable
private fun SearchHeroCard(
    copy: AppStrings,
    query: String,
    onQueryChange: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    SurfacePanel(contentPadding = PaddingValues(0.dp)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(PanelSoft, Panel)))
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Surface(
                    color = GoldSurfaceStrong,
                    border = BorderStroke(1.dp, GoldStroke),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Icon(
                        Icons.Rounded.Search,
                        contentDescription = null,
                        tint = PrimaryGold,
                        modifier = Modifier.padding(12.dp).size(24.dp),
                    )
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Surface(
                        color = GoldSurfaceStrong,
                        border = BorderStroke(1.dp, GoldStroke),
                        shape = PillShape,
                    ) {
                        Text(
                            copy.searchHeroEyebrow,
                            color = PrimaryGold,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            maxLines = 1,
                        )
                    }
                    Text(
                        copy.searchTitle,
                        color = TextPrimary,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        copy.searchSubtitle,
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    placeholder = { Text(copy.searchHint) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    leadingIcon = {
                        Icon(Icons.Rounded.Search, contentDescription = null, tint = PrimaryGold)
                    },
                    colors = goldTextFieldColors(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
                )
                Button(
                    onClick = onSubmit,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PrimaryGold,
                        contentColor = OnPrimaryDark,
                    ),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                ) {
                    Icon(
                        Icons.Rounded.Search,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(copy.searchAction, maxLines = 1)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DiscoveryGroups(
    title: String,
    groups: List<SearchDiscoveryGroup>,
    onTagSelected: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            title,
            color = TextPrimary,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        groups.forEach { group ->
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Panel.copy(alpha = 0.68f),
                border = BorderStroke(1.dp, Divider),
                shape = RoundedCornerShape(16.dp),
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(PrimaryGold, CircleShape),
                        )
                        Text(
                            group.title,
                            color = TextSecondary,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        group.tags.forEach { tag ->
                            AssistChip(
                                onClick = { onTagSelected(tag) },
                                label = { Text(tag, maxLines = 1) },
                                border = BorderStroke(1.dp, GoldStroke),
                                colors = AssistChipDefaults.assistChipColors(
                                    containerColor = GoldSurfaceStrong,
                                    labelColor = TextPrimary,
                                ),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchResultHeader(copy: AppStrings, query: String, resultCount: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                copy.searchResultTitle,
                color = TextPrimary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            if (query.isNotBlank()) {
                Text(
                    query,
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Text(
            "$resultCount ${copy.searchResultCountSuffix}",
            color = PrimaryGold,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}
