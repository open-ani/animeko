/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.utils.logging.writer

import me.him188.ani.utils.logging.LogLevel
import me.him188.ani.utils.logging.LogLineFormatter

class PrintLogWriter(
    private val formatter: LogLineFormatter = LogLineFormatter(),
    private val logLevel: LogLevel = LogLevel.TRACE,
    private val print: (String) -> Unit = { println(it) } // Default to standard output
) : LogWriter {
    override fun writeLog(level: LogLevel, tag: String, message: String?, throwable: Throwable?) {
        val line = formatter.formatLine(level, tag, message, throwable)
        print(line)
    }

    override fun isLoggingEnabled(level: LogLevel): Boolean {
        return level >= logLevel
    }
}
