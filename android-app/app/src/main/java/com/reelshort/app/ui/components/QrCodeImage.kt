package com.reelshort.app.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import com.reelshort.app.qr.QrMatrixEncoder

/**
 * 本地渲染的二维码图片：内容只在本机编码，不再把收款地址交给第三方二维码图片服务。
 */
@Composable
internal fun QrCodeImage(
    content: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
) {
    val bitmap = remember(content) { qrCodeBitmap(content) }
    if (bitmap == null) {
        return
    }
    Image(
        bitmap = bitmap.asImageBitmap(),
        contentDescription = contentDescription,
        modifier = modifier,
        // 二维码必须保持硬边缘，缩放时不能做平滑插值，否则会影响扫码识别率。
        filterQuality = FilterQuality.None,
        contentScale = ContentScale.Fit,
    )
}

/** 把 [content] 编码为白底黑块的方形 Bitmap；内容无法编码时返回 null 而不是崩溃。 */
internal fun qrCodeBitmap(content: String, targetSizePx: Int = QR_BITMAP_SIZE_PX): Bitmap? {
    if (content.isBlank()) {
        return null
    }
    val matrix = runCatching { QrMatrixEncoder().encode(content) }.getOrNull() ?: return null
    val scale = maxOf(1, targetSizePx / matrix.size)
    val dimension = matrix.size * scale
    val pixels = IntArray(dimension * dimension) { QR_LIGHT_PIXEL }
    for (row in 0 until matrix.size) {
        for (column in 0 until matrix.size) {
            if (!matrix.isDark(column, row)) {
                continue
            }
            for (offsetY in 0 until scale) {
                val start = (row * scale + offsetY) * dimension + column * scale
                for (offsetX in 0 until scale) {
                    pixels[start + offsetX] = QR_DARK_PIXEL
                }
            }
        }
    }
    return Bitmap.createBitmap(pixels, dimension, dimension, Bitmap.Config.ARGB_8888)
}

private const val QR_BITMAP_SIZE_PX = 512
private const val QR_LIGHT_PIXEL = 0xFFFFFFFF.toInt()
private const val QR_DARK_PIXEL = 0xFF000000.toInt()
