/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.utils.httpdownloader

actual class FFmpegResult {
    actual val exitCode: Int get() = -1
    actual val isSuccess: Boolean get() = false
}

actual class FFmpegLogMessage {
    actual val level: Int get() = 0
    actual val line: String get() = ""
    actual val isError: Boolean get() = level <= 24
}

actual class FFmpegKit actual constructor() {
    actual suspend fun execute(args: List<String>): FFmpegResult {
        throw UnsupportedOperationException("FFmpeg is not available in the browser build")
    }

    actual companion object {
        actual fun setLogHandler(handler: (FFmpegLogMessage) -> Unit) {
        }
    }
}
