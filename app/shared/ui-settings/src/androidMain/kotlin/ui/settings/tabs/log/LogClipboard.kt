/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.settings.tabs.log

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.him188.ani.utils.logging.logger
import java.io.File

// Android parcels text as UTF-16. Leave room in the shared Binder buffer for other transactions.
internal const val MAX_LOG_CLIPBOARD_CHARS = 128 * 1024

internal enum class LogCopyResult { Copied, TooLarge, Failed }

internal suspend fun copyLogToClipboard(
    file: File,
    setClipboardText: suspend (String) -> Unit,
): LogCopyResult {
    try {
        val text = withContext(Dispatchers.IO) {
            file.bufferedReader().use { reader ->
                // Read one extra character to detect overflow, including files growing during the read.
                val buffer = CharArray(MAX_LOG_CLIPBOARD_CHARS + 1)
                var count = 0
                while (count < buffer.size) {
                    val read = reader.read(buffer, count, buffer.size - count)
                    if (read == -1) break
                    count += read
                }
                if (count > MAX_LOG_CLIPBOARD_CHARS) null else String(buffer, 0, count)
            }
        } ?: return LogCopyResult.TooLarge
        setClipboardText(text)
        return LogCopyResult.Copied
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        logger("LogClipboard").warn("Could not copy today's log to the clipboard", e)
        return LogCopyResult.Failed
    }
}
