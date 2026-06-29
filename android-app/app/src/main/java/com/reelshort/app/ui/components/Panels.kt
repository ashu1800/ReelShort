package com.reelshort.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.reelshort.app.ui.format.ContentEmptyState
import com.reelshort.app.ui.theme.Divider
import com.reelshort.app.ui.theme.Panel
import com.reelshort.app.ui.theme.PillShape
import com.reelshort.app.ui.theme.PrimaryGold
import com.reelshort.app.ui.theme.TextPrimary
import com.reelshort.app.ui.theme.TextSecondary

@Composable
internal fun SurfacePanel(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(18.dp),
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = Panel.copy(alpha = 0.92f),
        contentColor = TextPrimary,
        border = BorderStroke(1.dp, Divider),
        shape = MaterialTheme.shapes.large,
    ) {
        Box(modifier = Modifier.padding(contentPadding)) {
            content()
        }
    }
}

@Composable
internal fun AccentLine() {
    Box(
        modifier = Modifier
            .width(72.dp)
            .height(4.dp)
            .clip(PillShape)
            .background(PrimaryGold),
    )
}

/**
 * 半透明金色药丸标签。复用 [goldCapsule] 视觉语言。
 */
@Composable
internal fun MetaPill(text: String) {
    Surface(
        color = androidx.compose.ui.graphics.Color(0x1AFFC46B),
        border = BorderStroke(1.dp, androidx.compose.ui.graphics.Color(0x44FFC46B)),
        shape = PillShape,
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            color = PrimaryGold,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
        )
    }
}

@Composable
internal fun SectionHeader(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.headlineSmall, color = TextPrimary)
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
    }
}

@Composable
internal fun EmptyState(state: ContentEmptyState) {
    SurfacePanel {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 112.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.Start,
        ) {
            Text(state.title, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
            Spacer(Modifier.height(8.dp))
            Text(state.message, color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
            if (state.actionLabel != null) {
                Spacer(Modifier.height(12.dp))
                MetaPill(state.actionLabel)
            }
        }
    }
}
