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
import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class LogClipboardTest {
    @Test
    fun copiesCompleteSmallLog() = runTest {
        withLog("日志 😀\nsecond line") { file ->
            var copied: String? = null
            assertEquals(LogCopyResult.Copied, copyLogToClipboard(file) { copied = it })
            assertEquals(file.readText(), copied)
        }
    }

    @Test
    fun copiesLogAtCharacterLimit() = runTest {
        withLog("中".repeat(MAX_LOG_CLIPBOARD_CHARS)) { file ->
            var copied: String? = null
            assertEquals(LogCopyResult.Copied, copyLogToClipboard(file) { copied = it })
            assertEquals(file.readText(), copied)
        }
    }

    @Test
    fun rejectsOversizedLogWithoutChangingClipboard() = runTest {
        for (size in listOf(MAX_LOG_CLIPBOARD_CHARS + 1, 8 * 1024 * 1024)) {
            withLog("x".repeat(size)) { file ->
                var copied = "previous clipboard"
                assertEquals(LogCopyResult.TooLarge, copyLogToClipboard(file) { copied = it })
                assertEquals("previous clipboard", copied)
            }
        }
    }

    @Test
    fun handlesClipboardServiceFailure() = runTest {
        withLog("small log") { file ->
            assertEquals(LogCopyResult.Failed, copyLogToClipboard(file) {
                throw RuntimeException("Clipboard service rejected the transaction")
            })
        }
    }

    @Test
    fun handlesMissingLog() = runTest {
        withLog("") { file ->
            file.delete()
            var copied = false
            assertEquals(LogCopyResult.Failed, copyLogToClipboard(file) { copied = true })
            assertEquals(false, copied)
        }
    }

    @Test
    fun preservesCancellation() = runTest {
        withLog("small log") { file ->
            assertFailsWith<CancellationException> {
                copyLogToClipboard(file) { throw CancellationException("Screen closed") }
            }
        }
    }

    private suspend fun withLog(text: String, block: suspend (File) -> Unit) {
        val file = File.createTempFile("animeko-log-", ".log")
        try {
            file.writeText(text)
            block(file)
        } finally {
            file.delete()
        }
    }
}
