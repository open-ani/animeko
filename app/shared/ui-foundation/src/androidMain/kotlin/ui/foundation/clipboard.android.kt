/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.foundation

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.ui.platform.AndroidClipboard
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard

actual fun textClipEntryOf(text: String): ClipEntry {
    return ClipEntry(ClipData(text, arrayOf("text/plain"), ClipData.Item(text)))
}

actual suspend fun ClipEntry.readText(): String? {
    if (clipData.itemCount == 0) return null
    return clipData.getItemAt(0)?.text?.toString()
}

actual fun Clipboard.transformTextOnWrite(transform: (String) -> String?): Clipboard =
    if (this is AndroidClipboard) {
        AndroidTextTransformingClipboard(this, transform)
    } else {
        TextTransformingClipboard(this, transform)
    }

/**
 * Compose 通过 `nativeClipboardManager` 判断能否粘贴, 它要求剪贴板是 [AndroidClipboard], 否则抛出异常.
 * [AndroidClipboard] 标注了 `@VisibleForTesting`, 但它是读取原生剪贴板的唯一途径.
 */
@SuppressLint("VisibleForTests")
private class AndroidTextTransformingClipboard(
    private val delegate: AndroidClipboard,
    private val transform: (String) -> String?,
) : AndroidClipboard {
    override val clipboardManager: ClipboardManager get() = delegate.clipboardManager

    override suspend fun getClipEntry(): ClipEntry? = delegate.getClipEntry()

    override suspend fun setClipEntry(clipEntry: ClipEntry?) {
        delegate.setClipEntry(clipEntry.transformText(transform))
    }
}
