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
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.LocalClipboard
import kotlinx.coroutines.Dispatchers
import me.him188.ani.utils.coroutines.IO_
import me.him188.ani.utils.coroutines.runInterruptible
import me.him188.ani.utils.io.absolutePath
import java.awt.Image
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.awt.datatransfer.UnsupportedFlavorException
import java.io.File
import javax.imageio.ImageIO

/**
 * 同时提供图片 (粘贴到聊天软件, 文档) 和文件 (粘贴到访达 / 资源管理器) 两种 flavor.
 */
@Composable
actual fun rememberImageClipboard(): ImageClipboard? {
    val clipboard = LocalClipboard.current
    return remember(clipboard) { AwtImageClipboard(clipboard) }
}

private class AwtImageClipboard(private val clipboard: Clipboard) : ImageClipboard {
    override suspend fun copy(file: ImageViewerExportedFile) {
        val awtFile = File(file.path.absolutePath)
        // WebP 等 ImageIO 不认识的格式解码失败时只提供文件 flavor
        val image = runInterruptible(Dispatchers.IO_) {
            runCatching { ImageIO.read(awtFile) }.getOrNull()
        }
        clipboard.setClipEntry(ClipEntry(ImageFileTransferable(image, awtFile)))
    }
}

private class ImageFileTransferable(
    private val image: Image?,
    private val file: File,
) : Transferable {
    private val flavors = listOfNotNull(
        image?.let { DataFlavor.imageFlavor },
        DataFlavor.javaFileListFlavor,
    ).toTypedArray()

    override fun getTransferDataFlavors(): Array<DataFlavor> = flavors

    override fun isDataFlavorSupported(flavor: DataFlavor): Boolean = flavor in flavors

    override fun getTransferData(flavor: DataFlavor): Any = when {
        flavor == DataFlavor.imageFlavor && image != null -> image
        flavor == DataFlavor.javaFileListFlavor -> listOf(file)
        else -> throw UnsupportedFlavorException(flavor)
    }
}
