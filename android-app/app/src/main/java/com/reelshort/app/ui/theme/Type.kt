package com.reelshort.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.FontWeight

/**
 * 应用字体排版。把此前散落在 30+ 处手写的 FontWeight.Black/Bold/SemiBold 固化到对应角色，
 * 让标题/正文层级一致。字号沿用 Material3 默认。
 */
private val defaults = Typography()

internal val AppTypography = Typography(
    displaySmall = defaults.displaySmall.copy(fontWeight = FontWeight.Black),
    headlineSmall = defaults.headlineSmall.copy(fontWeight = FontWeight.Black),
    headlineMedium = defaults.headlineMedium.copy(fontWeight = FontWeight.Black),
    titleLarge = defaults.titleLarge.copy(fontWeight = FontWeight.Bold),
    titleMedium = defaults.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    bodyLarge = defaults.bodyLarge.copy(fontWeight = FontWeight.Medium),
)
