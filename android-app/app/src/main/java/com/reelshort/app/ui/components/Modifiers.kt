package com.reelshort.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.reelshort.app.ui.theme.AppBackgroundColor
import com.reelshort.app.ui.theme.GoldStroke
import com.reelshort.app.ui.theme.GoldSurfaceSoft
import com.reelshort.app.ui.theme.PosterGradientBottom
import com.reelshort.app.ui.theme.PosterGradientTop
import com.reelshort.app.ui.theme.PrimaryGoldDark
import com.reelshort.app.ui.theme.PrimaryGold
import com.reelshort.app.ui.theme.PlayerGradientBottom
import com.reelshort.app.ui.theme.PlayerGradientTop

/**
 * 半透明金色胶囊背景（统一 MetaPill / play 图标容器 / AccountMenuRow 高亮图标背景）。
 */
internal fun Modifier.goldCapsule(strong: Boolean = false, shape: Shape = RectangleShape): Modifier =
    this
        .clip(shape)
        .background(if (strong) androidx.compose.ui.graphics.Color(0x26FFC46B) else GoldSurfaceSoft)
        .border(BorderStroke(1.dp, GoldStroke), shape)

/** 应用根背景渐变（上暖下冷，营造影院氛围）。 */
internal val AppBackgroundGradient = listOf(Color(0xFF15110F), AppBackgroundColor, Color(0xFF06070B))

/** 播放器表面渐变。 */
internal val PlayerGradient = listOf(PlayerGradientTop, PlayerGradientBottom)

/** 海报 fallback 渐变。 */
internal val PosterGradient = listOf(PosterGradientTop, PrimaryGoldDark, PosterGradientBottom)

/** 账户头像金色渐变。 */
internal val AvatarGradient = listOf(PrimaryGold, PrimaryGoldDark)

internal fun verticalGradient(colors: List<Color>): Brush = Brush.verticalGradient(colors)
