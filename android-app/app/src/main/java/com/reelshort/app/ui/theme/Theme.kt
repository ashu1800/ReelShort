package com.reelshort.app.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.LocalContentColor

private val ReelShortColorScheme = darkColorScheme(
    primary = PrimaryGold,
    onPrimary = OnPrimaryDark,
    secondary = PrimaryGoldDark,
    background = AppBackgroundColor,
    onBackground = TextPrimary,
    surface = Panel,
    onSurface = TextPrimary,
    surfaceVariant = PanelSoft,
    onSurfaceVariant = TextSecondary,
    error = DangerText,
)

@Composable
internal fun ReelShortTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ReelShortColorScheme,
        shapes = AppShapes,
        typography = AppTypography,
        content = content,
    )
}

@Composable
internal fun AppBackground(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF15110F), AppBackgroundColor, Color(0xFF06070B)),
                ),
            ),
    ) {
        CompositionLocalProvider(LocalContentColor provides TextPrimary) {
            content()
        }
    }
}
