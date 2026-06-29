package com.reelshort.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * 应用圆角体系。统一此前散落的 10/14/16/18/20/22/24/26 等 10 种圆角值到三档。
 */
internal val AppShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
)

/** 完全圆角（胶囊/药丸形）。 */
internal val PillShape = RoundedCornerShape(99.dp)

/** 底部弹窗顶部圆角。 */
internal val BottomSheetShape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
