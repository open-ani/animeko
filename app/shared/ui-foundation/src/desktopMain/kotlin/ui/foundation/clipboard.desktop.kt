/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.foundation

import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.asAwtTransferable
import kotlinx.coroutines.Dispatchers
import me.him188.ani.utils.coroutines.IO_
import me.him188.ani.utils.coroutines.runInterruptible
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection

actual fun textClipEntryOf(text: String): ClipEntry {
    return ClipEntry(StringSelection(text))
}

actual suspend fun Clipboard.getClipEntryText(): String? {
    val selection = getClipEntry()?.asAwtTransferable as? StringSelection ?: return null
    return runInterruptible(Dispatchers.IO_) {
        selection.getTransferData(DataFlavor.stringFlavor)?.toString()
    }
}
