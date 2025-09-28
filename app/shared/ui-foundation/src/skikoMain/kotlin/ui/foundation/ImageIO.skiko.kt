/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.foundation

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import org.jetbrains.skia.Rect
import org.jetbrains.skia.SamplingMode
import org.jetbrains.skia.Surface

actual fun decodeImageBitmap(bytes: ByteArray): ImageBitmap {
    return Image.makeFromEncoded(bytes).toComposeImageBitmap()
}

actual fun cropImageToSquare(imageData: ByteArray, crop: CropRect, outputSize: Int, jpegQuality: Int): ByteArray {
    val image = Image.makeFromEncoded(imageData)

    val safeSize = kotlin.math.min(kotlin.math.min(crop.size, image.width), image.height)
    val safeX = kotlin.math.min(kotlin.math.max(0, crop.x), kotlin.math.max(0, image.width - safeSize))
    val safeY = kotlin.math.min(kotlin.math.max(0, crop.y), kotlin.math.max(0, image.height - safeSize))

    // Draw and scale the cropped area directly into output surface
    val surface = Surface.makeRasterN32Premul(outputSize, outputSize)
    val canvas = surface.canvas
    val srcRect = Rect.makeXYWH(safeX.toFloat(), safeY.toFloat(), safeSize.toFloat(), safeSize.toFloat())
    val dstRect = Rect.makeWH(outputSize.toFloat(), outputSize.toFloat())
    canvas.drawImageRect(image, srcRect, dstRect, SamplingMode.DEFAULT, null, true)

    val result = surface.makeImageSnapshot().encodeToData(EncodedImageFormat.JPEG, jpegQuality.coerceIn(1, 100))
        ?: error("Failed to encode cropped image")
    return result.bytes
}
