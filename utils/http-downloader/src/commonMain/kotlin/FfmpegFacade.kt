/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.utils.httpdownloader

expect class FFmpegResult {
    val exitCode: Int
    val isSuccess: Boolean
}

expect class FFmpegLogMessage {
    val level: Int
    val line: String
    val isError: Boolean
}

expect class FFmpegKit() {
    suspend fun execute(args: List<String>): FFmpegResult

    companion object {
        fun setLogHandler(handler: (FFmpegLogMessage) -> Unit)
    }
}
