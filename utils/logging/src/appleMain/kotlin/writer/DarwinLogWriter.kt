/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.utils.logging.writer

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ptr
import me.him188.ani.utils.logging.LogLevel
import me.him188.ani.utils.logging.LogLineFormatter
import platform.darwin.*

class DarwinLogWriter(
    private val formatter: LogLineFormatter = LogLineFormatter(),
    private val logLevel: LogLevel = LogLevel.TRACE,
) : LogWriter {
    private val log = os_log_create("me.him188.ani", "default")

    @OptIn(ExperimentalForeignApi::class)
    override fun writeLog(level: LogLevel, tag: String, message: String?, throwable: Throwable?) {
        val line = formatter.formatLine(level, tag, message, throwable)

        if (isLoggingEnabled(level)) {
            _os_log_internal(
                __dso_handle.ptr,
                log,
                level.toDarwinLevel(),
                "%s",
                line
            )
        }
    }

    override fun isLoggingEnabled(level: LogLevel): Boolean {
        return os_log_type_enabled(log, level.toDarwinLevel())
    }
}

private fun LogLevel.toDarwinLevel(): UByte {
    return when (this) {
        LogLevel.TRACE -> OS_LOG_TYPE_DEBUG
        LogLevel.DEBUG -> OS_LOG_TYPE_DEBUG
        LogLevel.INFO -> OS_LOG_TYPE_INFO
        LogLevel.WARN -> OS_LOG_TYPE_ERROR
        LogLevel.ERROR -> OS_LOG_TYPE_FAULT
    }
}
