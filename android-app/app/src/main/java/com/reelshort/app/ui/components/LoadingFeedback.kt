package com.reelshort.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import com.reelshort.app.data.AppLanguage
import com.reelshort.app.ui.format.strings
import com.reelshort.app.ui.theme.Divider
import com.reelshort.app.ui.theme.PrimaryGold
import com.reelshort.app.ui.theme.TextPrimary

@Composable
internal fun LoadingDialog(
    visible: Boolean,
    language: AppLanguage = AppLanguage.TRADITIONAL_CHINESE,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier,
    ) {
        Surface(
            color = Color(0xF211151E),
            contentColor = TextPrimary,
            border = BorderStroke(1.dp, Divider),
            shape = RoundedCornerShape(22.dp),
        ) {
            LoadingContent(language = language, modifier = Modifier.padding(horizontal = 22.dp, vertical = 18.dp))
        }
    }
}

@Composable
private fun LoadingContent(
    language: AppLanguage,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 3.dp, color = PrimaryGold)
        Text(strings(language).loadingDialogTitle, color = TextPrimary, style = MaterialTheme.typography.titleMedium)
    }
}
