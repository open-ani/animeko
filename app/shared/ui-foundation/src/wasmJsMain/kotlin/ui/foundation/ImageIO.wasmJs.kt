package me.him188.ani.app.ui.foundation

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import org.jetbrains.skia.Rect
import org.jetbrains.skia.SamplingMode
import org.jetbrains.skia.Surface

actual fun decodeImageBitmap(bytes: ByteArray): ImageBitmap = Image.makeFromEncoded(bytes).toComposeImageBitmap()

actual fun cropImageToSquare(imageData: ByteArray, crop: CropRect, outputSize: Int, jpegQuality: Int): ByteArray {
    val image = Image.makeFromEncoded(imageData)

    val safeSize = kotlin.math.min(kotlin.math.min(crop.size, image.width), image.height)
    val safeX = kotlin.math.min(kotlin.math.max(0, crop.x), kotlin.math.max(0, image.width - safeSize))
    val safeY = kotlin.math.min(kotlin.math.max(0, crop.y), kotlin.math.max(0, image.height - safeSize))

    val surface = Surface.makeRasterN32Premul(outputSize, outputSize)
    val canvas = surface.canvas
    val srcRect = Rect.makeXYWH(safeX.toFloat(), safeY.toFloat(), safeSize.toFloat(), safeSize.toFloat())
    val dstRect = Rect.makeWH(outputSize.toFloat(), outputSize.toFloat())
    canvas.drawImageRect(image, srcRect, dstRect, SamplingMode.DEFAULT, null, true)

    val result = surface.makeImageSnapshot().encodeToData(EncodedImageFormat.JPEG, jpegQuality.coerceIn(1, 100))
        ?: error("Failed to encode cropped image")
    return result.bytes
}
