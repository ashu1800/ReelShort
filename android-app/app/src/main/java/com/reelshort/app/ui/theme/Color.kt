package com.reelshort.app.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * 应用调色板。所有 UI 颜色的唯一来源，禁止在 Composable 内联 [Color] 字面量。
 */
internal val AppBackgroundColor = Color(0xFF080A0F)
internal val Panel = Color(0xFF11151E)
internal val PanelSoft = Color(0xFF171C27)
internal val Divider = Color(0xFF252C3A)
internal val PrimaryGold = Color(0xFFFFC46B)
internal val PrimaryGoldDark = Color(0xFFB9802C)
internal val TextPrimary = Color(0xFFF8FAFC)
internal val TextSecondary = Color(0xFFA7B0C0)
internal val DangerSurface = Color(0xFF37191D)
internal val DangerText = Color(0xFFFFB4BC)

/**
 * 金色背景上的深棕文字。统一此前散落、数值不一致的 0xFF281600 / 0xFF221400 / 0xFF241500 / 0xFF1A1203。
 */
internal val OnPrimaryDark = Color(0xFF221400)

/** 金色描边半透明值（用于 [com.reelshort.app.ui.components.goldCapsule] 等金色胶囊）。 */
internal val GoldStroke = Color(0x44FFC46B)
internal val GoldSurfaceSoft = Color(0x1AFFC46B)
internal val GoldSurfaceStrong = Color(0x26FFC46B)
internal val AccountHeroScrim = Color(0x33191610)
internal val TranslucentWhiteSurface = Color(0x1FFFFFFF)
internal val WhiteEdge = Color(0x55FFFFFF)

/** 导航栏底色（带不透明度）。 */
internal val NavBarBackground = Color(0xF20B0E14)
internal val NavItemSelectedIcon = Color(0xFF1A1203)

/** 错误条边框。 */
internal val DangerBorder = Color(0xFF663038)

/** 播放器表面渐变端点色。 */
internal val PlayerGradientTop = Color(0xFF2B1E12)
internal val PlayerGradientBottom = Color(0xFF07080C)

/** 海报 fallback 渐变端点色。 */
internal val PosterGradientTop = Color(0xFFFFD38B)
internal val PosterGradientBottom = Color(0xFF24130A)
internal val PosterFallbackText = Color(0xFF1C1004)
