/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.foundation

import android.content.ClipData
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard

actual fun textClipEntryOf(text: String): ClipEntry {
    return ClipEntry(ClipData(text, arrayOf("text/plain"), ClipData.Item(text)))
}

actual suspend fun Clipboard.getClipEntryText(): String? {
    return getClipEntry()?.clipData?.getItemAt(0)?.text?.toString()
}
