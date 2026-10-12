package com.reelshort.app.qr

import com.google.zxing.BinaryBitmap
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class QrMatrixEncoderTest {
    private val encoder = QrMatrixEncoder()
    private val paymentAddress = "TQ5nNnCnY5Yx7QJk3n4a9b4b8r8t9v1abc"

    @Test
    fun encodesSquareMatrixWithThreeFinderPatterns() {
        val matrix = encoder.encode(paymentAddress)

        assertTrue(matrix.size >= 21, "二维码至少为 version 1（21 个模块）")
        assertEquals(matrix.size % 4, 1, "二维码模块数必须满足 4n + 1")
        assertTrue(matrix.darkModuleCount > 0)

        // 三个角上的定位图案：7x7 深色外框 + 内部 3x3 深色块（矩阵含静默区，符号从 margin 开始）。
        val margin = QrMatrixEncoder.DEFAULT_MARGIN_MODULES
        val symbolSize = matrix.size - 2 * margin
        assertFinderPatternAt(matrix, originColumn = margin, originRow = margin)
        assertFinderPatternAt(matrix, originColumn = margin + symbolSize - 7, originRow = margin)
        assertFinderPatternAt(matrix, originColumn = margin, originRow = margin + symbolSize - 7)
    }

    @Test
    fun keepsMarginModulesAroundTheSymbol() {
        val withoutMargin = QrMatrixEncoder(marginModules = 0).encode(paymentAddress)
        val withMargin = encoder.encode(paymentAddress)

        assertEquals(withoutMargin.size + 2 * QrMatrixEncoder.DEFAULT_MARGIN_MODULES, withMargin.size)
        assertTrue(!withMargin.isDark(0, 0), "静默区必须是浅色，扫码器才能识别边界")
    }

    @Test
    fun encodingIsDeterministicForTheSameContent() {
        val first = encoder.encode(paymentAddress)
        val second = encoder.encode(paymentAddress)

        assertEquals(first.size, second.size)
        assertEquals(first.darkModuleCount, second.darkModuleCount)
    }

    @Test
    fun raisesErrorCorrectionLevelWithoutChangingPayload() {
        val high = QrMatrixEncoder(errorCorrection = QrErrorCorrection.HIGH).encode(paymentAddress)

        assertTrue(high.size >= encoder.encode(paymentAddress).size)
    }

    @Test
    fun decodesBackToTheOriginalPaymentAddress() {
        val (pixels, dimension) = encoder.encode(paymentAddress).toLuminancePixels(scale = 8)
        val bitmap = BinaryBitmap(HybridBinarizer(RGBLuminanceSource(dimension, dimension, pixels)))

        assertEquals(paymentAddress, MultiFormatReader().decode(bitmap).text)
    }

    @Test
    fun rejectsBlankContent() {
        assertFailsWith<IllegalArgumentException> { encoder.encode("   ") }
    }

    private fun assertFinderPatternAt(matrix: QrMatrix, originColumn: Int, originRow: Int) {
        for (row in 0 until 7) {
            for (column in 0 until 7) {
                val onOuterRing = row == 0 || row == 6 || column == 0 || column == 6
                val insideCore = row in 2..4 && column in 2..4
                val expected = onOuterRing || insideCore
                assertEquals(
                    expected,
                    matrix.isDark(originColumn + column, originRow + row),
                    "定位图案模块不匹配：($column, $row)",
                )
            }
        }
    }

    /** 按 [scale] 放大为 ARGB 像素（白底黑块），供 zxing 解码器做回环校验。 */
    private fun QrMatrix.toLuminancePixels(scale: Int): Pair<IntArray, Int> {
        val dimension = size * scale
        val pixels = IntArray(dimension * dimension) { WHITE }
        for (row in 0 until size) {
            for (column in 0 until size) {
                if (!isDark(column, row)) continue
                for (offsetY in 0 until scale) {
                    for (offsetX in 0 until scale) {
                        val x = column * scale + offsetX
                        val y = row * scale + offsetY
                        pixels[y * dimension + x] = BLACK
                    }
                }
            }
        }
        return pixels to dimension
    }

    private companion object {
        const val WHITE = 0xFFFFFFFF.toInt()
        const val BLACK = 0xFF000000.toInt()
    }
}
