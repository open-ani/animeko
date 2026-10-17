/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.foundation

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.autoreleasepool
import kotlinx.cinterop.useContents
import kotlinx.cinterop.usePinned
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.Foundation.NSData
import platform.Foundation.create
import platform.UIKit.UIColor
import platform.UIKit.UIGraphicsImageRenderer
import platform.UIKit.UIGraphicsImageRendererFormat
import platform.UIKit.UIGraphicsImageRendererFormatRangeStandard
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.UIKit.UIRectFill
import platform.posix.memcpy
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
actual fun transcodeImageToJpeg(
    imageData: ByteArray,
    maxShortSide: Int,
    maxLongSide: Int,
    jpegQuality: Int,
): ByteArray? {
    if (imageData.isEmpty()) return null
    return autoreleasepool {
        val data = imageData.usePinned { pinned ->
            NSData.create(bytes = pinned.addressOf(0), length = imageData.size.toULong())
        }
        // scale 为 1, size 就是按 imageOrientation 摆正后的像素尺寸
        val image = UIImage.imageWithData(data) ?: return@autoreleasepool null
        val (width, height) = image.size.useContents { width to height }
        if (width < 1 || height < 1) return@autoreleasepool null
        val scale = minOf(
            1.0,
            maxShortSide / min(width, height),
            maxLongSide / max(width, height),
        )
        val outWidth = (width * scale).roundToInt().coerceAtLeast(1).toDouble()
        val outHeight = (height * scale).roundToInt().coerceAtLeast(1).toDouble()

        val format = UIGraphicsImageRendererFormat().apply {
            this.scale = 1.0
            opaque = true
            // 输出标准色域 (sRGB) 的 JPEG, 不使用广色域
            preferredRange = UIGraphicsImageRendererFormatRangeStandard
        }
        val rendered = UIGraphicsImageRenderer(size = CGSizeMake(outWidth, outHeight), format = format)
            .imageWithActions { _ ->
                val bounds = CGRectMake(0.0, 0.0, outWidth, outHeight)
                // JPEG 没有透明通道, 透明部分填充为白色
                UIColor.whiteColor.setFill()
                UIRectFill(bounds)
                // drawInRect 按 imageOrientation 摆正
                image.drawInRect(bounds)
            }
        val jpeg = UIImageJPEGRepresentation(rendered, jpegQuality.coerceIn(1, 100) / 100.0)
            ?.takeIf { it.length > 0u }
            ?: return@autoreleasepool null
        ByteArray(jpeg.length.toInt()).apply {
            usePinned { memcpy(it.addressOf(0), jpeg.bytes, jpeg.length) }
        }
    }
}
