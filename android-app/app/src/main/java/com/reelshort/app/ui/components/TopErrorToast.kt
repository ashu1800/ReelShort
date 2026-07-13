package com.reelshort.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.Info
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.reelshort.app.state.UiMessageType
import com.reelshort.app.ui.format.MessageVisualTone
import com.reelshort.app.ui.format.messageVisualTone
import com.reelshort.app.ui.theme.DangerBorder
import com.reelshort.app.ui.theme.DangerSurface
import com.reelshort.app.ui.theme.DangerText
import com.reelshort.app.ui.theme.SuccessSurface
import com.reelshort.app.ui.theme.SuccessText
import com.reelshort.app.ui.theme.SuccessBorder
import com.reelshort.app.ui.theme.InfoSurface
import com.reelshort.app.ui.theme.InfoText
import com.reelshort.app.ui.theme.InfoBorder

@Composable
internal fun TopErrorToast(
    message: String?,
    type: UiMessageType = UiMessageType.ERROR,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tone = messageVisualTone(type)
    val surface = when (tone) {
        MessageVisualTone.SUCCESS -> SuccessSurface
        MessageVisualTone.ERROR -> DangerSurface
        MessageVisualTone.INFO -> InfoSurface
    }
    val foreground = when (tone) {
        MessageVisualTone.SUCCESS -> SuccessText
        MessageVisualTone.ERROR -> DangerText
        MessageVisualTone.INFO -> InfoText
    }
    val outline = when (tone) {
        MessageVisualTone.SUCCESS -> SuccessBorder
        MessageVisualTone.ERROR -> DangerBorder
        MessageVisualTone.INFO -> InfoBorder
    }
    val icon = when (tone) {
        MessageVisualTone.SUCCESS -> Icons.Rounded.CheckCircle
        MessageVisualTone.ERROR -> Icons.Rounded.Error
        MessageVisualTone.INFO -> Icons.Rounded.Info
    }
    AnimatedVisibility(
        visible = message != null,
        enter = slideInVertically { -it } + fadeIn(),
        exit = slideOutVertically { -it } + fadeOut(),
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier
                .statusBarsPadding()
                .padding(horizontal = 18.dp, vertical = 12.dp)
                .widthIn(max = 420.dp)
                .semantics { liveRegion = LiveRegionMode.Polite }
                .clip(RoundedCornerShape(14.dp))
                .background(surface)
                .border(1.dp, outline, RoundedCornerShape(14.dp))
                .clickable(onClick = onDismiss)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, tint = foreground, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text(
                text = message.orEmpty(),
                color = foreground,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
