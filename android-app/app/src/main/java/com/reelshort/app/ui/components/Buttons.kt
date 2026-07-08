package com.reelshort.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.reelshort.app.ui.theme.Divider
import com.reelshort.app.ui.theme.OnPrimaryDark
import com.reelshort.app.ui.theme.PrimaryGold
import com.reelshort.app.ui.theme.TextPrimary
import androidx.compose.ui.graphics.Color

@Composable
internal fun PrimaryActionButton(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth(),
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(52.dp),
        enabled = enabled,
        shape = MaterialTheme.shapes.medium,
        colors = ButtonDefaults.buttonColors(
            containerColor = PrimaryGold,
            contentColor = OnPrimaryDark,
            disabledContainerColor = Color(0xFF3A3329),
            disabledContentColor = Color(0xFF827667),
        ),
    ) {
        Text(text, style = MaterialTheme.typography.titleMedium)
    }
}

/**
 * 金色描边的次级按钮，统一此前重复的 OutlinedButton + BorderStroke(Divider) 样板。
 */
@Composable
internal fun GoldOutlinedButton(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentColor: androidx.compose.ui.graphics.Color = TextPrimary,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        border = BorderStroke(1.dp, Divider),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = contentColor),
    ) {
        Text(text)
    }
}
