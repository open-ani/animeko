/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.foundation

import androidx.compose.ui.graphics.ImageBitmap

/**
 * Decode raw image bytes to [ImageBitmap] for preview.
 */
expect fun decodeImageBitmap(bytes: ByteArray): ImageBitmap

data class CropRect(val x: Int, val y: Int, val size: Int)

/**
 * Crop a square region from [imageData] and return JPEG bytes of [outputSize] x [outputSize].
 */
expect fun cropImageToSquare(imageData: ByteArray, crop: CropRect, outputSize: Int, jpegQuality: Int = 90): ByteArray

/**
 * 用平台的解码器解码 [imageData], 按 EXIF 方向摆正, 等比缩小到短边不超过 [maxShortSide]、长边不超过 [maxLongSide] (不放大),
 * 编码为 JPEG. 用于在上传前把对方不接受的格式 (例如 HEIC 照片) 转成 JPEG.
 *
 * 只保留第一帧, 透明部分填充为白色. 解码和编码是耗时的同步计算, 不要在主线程调用.
 *
 * - Android: 使用 `ImageDecoder`, 需要 Android 9 (API 28) 及以上;
 * - iOS: 使用 UIKit, 支持系统能解码的所有格式;
 * - 桌面端: 不支持, 总是返回 `null`. Skia 没有 HEIF 解码器.
 *
 * @return 平台不支持或无法解码时为 `null`.
 */
expect fun transcodeImageToJpeg(
    imageData: ByteArray,
    maxShortSide: Int,
    maxLongSide: Int,
    jpegQuality: Int = 90,
): ByteArray?
