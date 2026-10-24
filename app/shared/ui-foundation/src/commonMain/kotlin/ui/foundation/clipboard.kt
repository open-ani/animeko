/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.foundation

import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.NativeClipboard

expect fun textClipEntryOf(text: String): ClipEntry

suspend inline fun Clipboard.setClipEntryText(text: String) = setClipEntry(textClipEntryOf(text))
suspend fun Clipboard.getClipEntryText(): String? = getClipEntry()?.readText()

/**
 * 读取 [ClipEntry] 中的纯文本. 没有文本或无法读取时返回 `null`.
 */
expect suspend fun ClipEntry.readText(): String?

/**
 * 返回包装了这个剪贴板的 [Clipboard]: 写入的条目中有文本时, 先用 [transform] 转换文本, 再以纯文本条目写入.
 * [transform] 返回 `null` 时原样写入, 保留条目中的其他内容 (如样式).
 *
 * 读取和对平台剪贴板的访问 (例如 Compose 判断能否粘贴) 都交给这个剪贴板, 所以可以通过 `LocalClipboard` 提供给文本框,
 * 改变复制和剪切放到剪贴板中的文字.
 */
expect fun Clipboard.transformTextOnWrite(transform: (String) -> String?): Clipboard

/**
 * [transformTextOnWrite] 写入前对条目的转换.
 */
internal suspend fun ClipEntry?.transformText(transform: (String) -> String?): ClipEntry? {
    val entry = this ?: return null
    val text = entry.readText() ?: return entry
    val transformed = transform(text) ?: return entry
    return textClipEntryOf(transformed)
}

/**
 * [transformTextOnWrite] 的通用实现. Android 上的系统剪贴板需要另外实现 `AndroidClipboard`.
 */
internal class TextTransformingClipboard(
    private val delegate: Clipboard,
    private val transform: (String) -> String?,
) : Clipboard {
    override suspend fun getClipEntry(): ClipEntry? = delegate.getClipEntry()

    override suspend fun setClipEntry(clipEntry: ClipEntry?) {
        delegate.setClipEntry(clipEntry.transformText(transform))
    }

    @Suppress("DEPRECATION")
    @Deprecated("Use platform-specific extension to get platform reference")
    override val nativeClipboard: NativeClipboard
        get() = delegate.nativeClipboard
}
