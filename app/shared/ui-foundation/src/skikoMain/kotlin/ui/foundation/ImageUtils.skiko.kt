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
import androidx.compose.ui.graphics.asComposeImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import coil3.Image
import coil3.toBitmap
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Rect
import org.jetbrains.skia.Surface
import org.jetbrains.skia.Image as SkiaImage

actual fun Image.toComposeImageBitmap(): ImageBitmap {
    return this.toBitmap().asComposeImageBitmap()
}

actual fun ImageBitmap.resize(width: Int): ImageBitmap {
    // written by o3-mini

    val skiaBitmap = this.asSkiaBitmap()
    // 根据目标宽度计算新的高度以保持图片宽高比
    val newWidth = width
    val newHeight = (skiaBitmap.height * newWidth) / skiaBitmap.width

    // 创建目标 Surface，通过指定 ImageInfo 来设定输出图片的尺寸和颜色配置
    val imageInfo = ImageInfo.makeN32Premul(newWidth, newHeight)
    val surface = Surface.makeRaster(imageInfo)
    val canvas = surface.canvas

    // 将原始位图转换为 SkiaImage 以便绘制
    val originalImage = SkiaImage.makeFromBitmap(skiaBitmap)
    // 定义原始图片的源矩形（使用整个图片）
    val srcRect = Rect(0f, 0f, skiaBitmap.width.toFloat(), skiaBitmap.height.toFloat())
    // 定义目标矩形，表示缩放后图片在画布上的位置和大小
    val destRect = Rect(0f, 0f, newWidth.toFloat(), newHeight.toFloat())

    // 将原始图片绘制到目标矩形中，自动缩放
    canvas.drawImageRect(originalImage, srcRect, destRect, Paint())

    // 获取缩放后图片的快照，并转换为 Compose 的 ImageBitmap
    return surface.makeImageSnapshot().toComposeImageBitmap()
}