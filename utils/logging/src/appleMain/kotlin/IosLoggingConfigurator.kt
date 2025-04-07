/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.utils.logging

import kotlinx.io.files.FileSystem
import kotlinx.io.files.Path
import me.him188.ani.utils.logging.writer.DailyRollingFileLogWriter
import me.him188.ani.utils.logging.writer.DarwinLogWriter
import kotlin.concurrent.Volatile

object IosLoggingConfigurator {
    @Volatile
    private var _configuredFactory: LoggerFactory? = null

    // For unit tests only
    private val fallbackLoggerFactory = DefaultLoggerFactory(
        listOf(
            DarwinLogWriter(
                logLevel = LogLevel.TRACE,
            ),
        ),
    )

    val factory: LoggerFactory
        get() = _configuredFactory ?: fallbackLoggerFactory

    fun configure(logsDir: Path, fileSystem: FileSystem) {
        _configuredFactory = DefaultLoggerFactory(
            listOf(
                DarwinLogWriter(
                    logLevel = LogLevel.TRACE,
                ),
                DailyRollingFileLogWriter(
                    logsDir = logsDir,
                    fileSystem = fileSystem,
                ),
            ),
        )
    }
}