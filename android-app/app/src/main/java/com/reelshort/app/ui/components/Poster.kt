package com.reelshort.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.reelshort.app.ui.format.coverUrlOrNull
import com.reelshort.app.ui.format.posterInitials
import com.reelshort.app.ui.theme.PosterFallbackText
import com.reelshort.app.ui.theme.WhiteEdge

@Composable
internal fun PosterBlock(title: String, coverUrl: String?, modifier: Modifier = Modifier) {
    val normalizedCoverUrl = coverUrl.coverUrlOrNull()
    var showFallback by remember(normalizedCoverUrl) { mutableStateOf(true) }

    Box(
        modifier = modifier
            .clip(MaterialTheme.shapes.medium)
            .background(verticalGradient(PosterGradient))
            .border(1.dp, WhiteEdge, MaterialTheme.shapes.medium),
        contentAlignment = Alignment.BottomStart,
    ) {
        if (normalizedCoverUrl != null) {
            AsyncImage(
                model = normalizedCoverUrl,
                contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
                onLoading = { showFallback = true },
                onError = { showFallback = true },
                onSuccess = { showFallback = false },
            )
        }
        if (showFallback) {
            Text(
                title.posterInitials(),
                modifier = Modifier.padding(10.dp),
                color = PosterFallbackText,
                style = MaterialTheme.typography.titleLarge,
            )
        }
    }
}
