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
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.scale
import coil3.Image
import coil3.toBitmap

actual fun Image.toComposeImageBitmap(): ImageBitmap {
    return this.toBitmap().asImageBitmap()
}

actual fun ImageBitmap.resize(width: Int): ImageBitmap {
    val androidBitmap = this.asAndroidBitmap()
    val newWidth = width
    val newHeight = androidBitmap.height * newWidth / width
    return androidBitmap.scale(newWidth, newHeight).asImageBitmap()
}
