/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.foundation

import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.awtTransferable
import kotlinx.io.files.Path
import java.awt.datatransfer.DataFlavor
import java.io.File
import java.io.IOException

@Suppress("UNCHECKED_CAST")
actual fun processDragAndDropEventImpl(event: DragAndDropEvent): DragAndDropContent {
    val transferable = event.awtTransferable

    if (transferable.isDataFlavorSupported(DataFlavor.stringFlavor)) {
        try {
            val data = transferable.getTransferData(DataFlavor.stringFlavor) as String
            return DragAndDropContent.PlainText(data)
        } catch (_: IOException) {
            // This flavor type of data is not available
        }
    }

    if (transferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
        try {
            val data = transferable.getTransferData(DataFlavor.javaFileListFlavor) as List<File>
            return DragAndDropContent.FileList(data.map { Path(it.absolutePath) })
        } catch (_: IOException) {
            // This flavor type of data is not available
        }
    }

    return DragAndDropContent.Unsupported
}