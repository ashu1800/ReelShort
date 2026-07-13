package com.reelshort.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import com.reelshort.app.data.AppLanguage
import com.reelshort.app.data.BookSummary
import com.reelshort.app.ui.format.strings
import com.reelshort.app.ui.format.posterOverlayTitleMaxLines
import com.reelshort.app.ui.format.posterCardContentDescription
import com.reelshort.app.ui.theme.PrimaryGold
import com.reelshort.app.ui.theme.TextPrimary

/**
 * 首页海报网格卡片：以竖版海报为视觉主体，标题与集数覆盖在底部渐变遮罩上。
 */
@Composable
internal fun PosterCard(
    book: BookSummary,
    onClick: () -> Unit,
    language: AppLanguage = AppLanguage.TRADITIONAL_CHINESE,
) {
    val copy = strings(language)
    val fontScale = LocalDensity.current.fontScale
    val compactOverlay = fontScale >= 1.3f
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = tween(durationMillis = 100),
        label = "poster-card-press",
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(2f / 3f)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(MaterialTheme.shapes.medium)
            .clearAndSetSemantics {
                contentDescription = posterCardContentDescription(book.title, book.chapterCount, language)
            }
            .clickable(interactionSource = interaction, indication = LocalIndication.current, onClick = onClick),
    ) {
        PosterBlock(book.title, book.coverUrl, Modifier.matchParentSize(), contentDescription = null)
        // 底部渐变遮罩，保证标题在任何封面上都可读
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        0.45f to Color.Transparent,
                        1f to Color(0xE6000000),
                    ),
                ),
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                book.title,
                color = TextPrimary,
                style = if (compactOverlay) MaterialTheme.typography.labelMedium else MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = posterOverlayTitleMaxLines(fontScale),
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = null,
                    tint = PrimaryGold,
                )
                Text(
                    "${book.chapterCount}${copy.posterEpisodeUnit}",
                    color = PrimaryGold,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}
