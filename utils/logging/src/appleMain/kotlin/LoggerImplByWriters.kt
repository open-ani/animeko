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

internal class LoggerImplByWriters(
    tag: String,
    private val logWriters: List<LogWriter>,
) : AbstractLogger(tag) {
    override fun writeLog(
        level: LogLevel,
        tag: String,
        message: String?,
        throwable: Throwable?
    ) {
        if (isLoggingEnabled(level)) {
            logWriters.forEach { writer ->
                if (writer.isLoggingEnabled(level)) {
                    writer.writeLog(level, tag, message, throwable)
                }
            }
        }
    }

    override fun isLoggingEnabled(level: LogLevel): Boolean {
        return logWriters.any { it.isLoggingEnabled(level) }
    }
}