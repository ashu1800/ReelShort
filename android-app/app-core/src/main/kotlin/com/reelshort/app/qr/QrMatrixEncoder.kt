package com.reelshort.app.qr

import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/**
 * 二维码模块矩阵：只描述"哪些格子是深色"，由 Android 层按需放大渲染为 Bitmap。
 *
 * 刻意不暴露 zxing 类型，保证 app-core 的对外边界仍是纯 Kotlin 值对象。
 */
class QrMatrix internal constructor(
    val size: Int,
    private val darkModules: Array<BooleanArray>,
) {
    init {
        require(size > 0) { "QR matrix size must be positive" }
        require(darkModules.size == size) { "QR matrix rows must match its size" }
    }

    fun isDark(column: Int, row: Int): Boolean {
        require(column in 0 until size && row in 0 until size) { "QR module index out of bounds" }
        return darkModules[row][column]
    }

    val darkModuleCount: Int
        get() = darkModules.sumOf { row -> row.count { it } }
}

/**
 * 纠错级别：与 zxing 的纠错等级一一对应，避免把第三方类型暴露到 app-core 的公开 API 上。
 */
enum class QrErrorCorrection {
    LOW,
    MEDIUM,
    QUARTILE,
    HIGH,
}

/**
 * 纯 JVM 二维码编码器（zxing core，无 Android 依赖）。
 *
 * 用于在本地渲染 VIP 收款地址二维码，替代此前把钱包地址明文交给第三方二维码图片服务的实现。
 */
class QrMatrixEncoder(
    private val errorCorrection: QrErrorCorrection = QrErrorCorrection.MEDIUM,
    private val marginModules: Int = DEFAULT_MARGIN_MODULES,
) {
    fun encode(content: String): QrMatrix {
        require(content.isNotBlank()) { "QR content must not be blank" }
        require(marginModules >= 0) { "QR margin must not be negative" }
        // 宽高传 0：zxing 会直接返回"1 模块 = 1 像素"的矩阵（含静默区），正好是我们要的模块矩阵。
        val bits = QRCodeWriter().encode(
            content,
            BarcodeFormat.QR_CODE,
            0,
            0,
            mapOf(
                EncodeHintType.ERROR_CORRECTION to errorCorrection.toZxingLevel(),
                EncodeHintType.MARGIN to marginModules,
                EncodeHintType.CHARACTER_SET to Charsets.UTF_8.name(),
            ),
        )
        return bits.toQrMatrix()
    }

    private fun QrErrorCorrection.toZxingLevel(): ErrorCorrectionLevel =
        when (this) {
            QrErrorCorrection.LOW -> ErrorCorrectionLevel.L
            QrErrorCorrection.MEDIUM -> ErrorCorrectionLevel.M
            QrErrorCorrection.QUARTILE -> ErrorCorrectionLevel.Q
            QrErrorCorrection.HIGH -> ErrorCorrectionLevel.H
        }

    private fun BitMatrix.toQrMatrix(): QrMatrix =
        QrMatrix(
            size = width,
            darkModules = Array(height) { row -> BooleanArray(width) { column -> this[column, row] } },
        )

    companion object {
        /** 静默区模块数：2 个模块已能被主流扫码器识别，同时避免在弹层里浪费宽度。 */
        const val DEFAULT_MARGIN_MODULES = 2
    }
}
