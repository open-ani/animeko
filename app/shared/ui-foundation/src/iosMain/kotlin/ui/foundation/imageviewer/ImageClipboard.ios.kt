/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.foundation.imageviewer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.him188.ani.utils.io.readBytes
import platform.Foundation.NSData
import platform.Foundation.create
import platform.UIKit.UIImage
import platform.UIKit.UIPasteboard

@Composable
actual fun rememberImageClipboard(): ImageClipboard? = remember { IosImageClipboard }

private object IosImageClipboard : ImageClipboard {
    @OptIn(ExperimentalForeignApi::class)
    override suspend fun copy(file: ImageViewerExportedFile) {
        val bytes = withContext(Dispatchers.Default) { file.path.readBytes() }
        val data = bytes.usePinned { pinned ->
            NSData.create(bytes = pinned.addressOf(0), length = bytes.size.toULong())
        }
        val image = UIImage(data = data) ?: error("Cannot decode image ${file.fileName}")
        UIPasteboard.generalPasteboard.image = image
    }
}
