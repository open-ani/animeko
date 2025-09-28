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

/**
 * Decode raw image bytes to [ImageBitmap] for preview.
 */
expect fun decodeImageBitmap(bytes: ByteArray): ImageBitmap

data class CropRect(val x: Int, val y: Int, val size: Int)

/**
 * Crop a square region from [imageData] and return JPEG bytes of [outputSize] x [outputSize].
 */
expect fun cropImageToSquare(imageData: ByteArray, crop: CropRect, outputSize: Int, jpegQuality: Int = 90): ByteArray

