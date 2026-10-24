/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.foundation

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.os.Build
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

actual fun decodeImageBitmap(bytes: ByteArray): ImageBitmap {
    val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    return bmp.asImageBitmap()
}

actual fun cropImageToSquare(imageData: ByteArray, crop: CropRect, outputSize: Int, jpegQuality: Int): ByteArray {
    val src = BitmapFactory.decodeByteArray(imageData, 0, imageData.size)
    val safeSize = min(min(crop.size, src.width), src.height)
    val safeX = min(max(0, crop.x), max(0, src.width - safeSize))
    val safeY = min(max(0, crop.y), max(0, src.height - safeSize))

    val cropped = Bitmap.createBitmap(src, safeX, safeY, safeSize, safeSize)
    val scaled = cropped.scale(outputSize, outputSize)

    val baos = ByteArrayOutputStream()
    scaled.compress(Bitmap.CompressFormat.JPEG, jpegQuality.coerceIn(1, 100), baos)
    return baos.toByteArray()
}

actual fun transcodeImageToJpeg(
    imageData: ByteArray,
    maxShortSide: Int,
    maxLongSide: Int,
    jpegQuality: Int,
): ByteArray? {
    // BitmapFactory 不按 EXIF 方向摆正, 也不支持 HEIF
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return null
    val decoded = try {
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(ByteBuffer.wrap(imageData))) { decoder, info, _ ->
            // 尺寸已按 EXIF 方向摆正, 解码时直接缩小, 不必先解出原图
            val width = info.size.width
            val height = info.size.height
            val scale = minOf(
                1.0,
                maxShortSide.toDouble() / min(width, height),
                maxLongSide.toDouble() / max(width, height),
            )
            if (scale < 1.0) {
                decoder.setTargetSize(
                    (width * scale).roundToInt().coerceAtLeast(1),
                    (height * scale).roundToInt().coerceAtLeast(1),
                )
            }
            // 硬件位图不能读取像素, 绘制和编码都需要软件位图
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
    } catch (e: IOException) {
        // 格式不支持或文件损坏
        return null
    }

    val opaque = if (decoded.hasAlpha()) {
        // JPEG 没有透明通道, 直接编码时透明部分会变成黑色
        createBitmap(decoded.width, decoded.height).also { bitmap ->
            Canvas(bitmap).apply {
                drawColor(Color.WHITE)
                drawBitmap(decoded, 0f, 0f, null)
            }
            decoded.recycle()
        }
    } else {
        decoded
    }
    return try {
        ByteArrayOutputStream().use { out ->
            opaque.compress(Bitmap.CompressFormat.JPEG, jpegQuality.coerceIn(1, 100), out)
            out.toByteArray()
        }
    } finally {
        opaque.recycle()
    }
}
