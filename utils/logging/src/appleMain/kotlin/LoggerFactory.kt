/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.utils.logging

import me.him188.ani.utils.logging.writer.LogWriter

// Don't implement new LoggerFactory. UI assumes it's DefaultLoggerFactory using a `as` cast.
sealed interface LoggerFactory {
    fun createLogger(name: String): Logger
}

class DefaultLoggerFactory(
    val writers: List<LogWriter>,
) : LoggerFactory {
    override fun createLogger(name: String): Logger {
        return LoggerImplByWriters(name, writers)
    }
}
