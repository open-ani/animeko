/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.utils.logging.writer

import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.io.Sink
import kotlinx.io.buffered
import kotlinx.io.files.FileSystem
import kotlinx.io.files.Path
import kotlinx.io.writeString
import me.him188.ani.utils.logging.LogLevel
import me.him188.ani.utils.logging.LogLineFormatter
import platform.Foundation.NSError
import platform.Foundation.NSFileManager
import kotlin.concurrent.Volatile
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * A simple daily rolling file log writer:
 * - Writes to a file with pattern: app.<yyyy-MM-dd>.log
 * - Rotates when date changes
 * - Keeps up to [maxHistory] logs, deleting older logs.
 */
class DailyRollingFileLogWriter(
    private val logsDir: Path,
    private val fileSystem: FileSystem,
    private val maxHistory: Int = 3,
    private val formatter: LogLineFormatter = LogLineFormatter(),
    private val logLevel: LogLevel = LogLevel.TRACE,
    private val filePathForDate: (LocalDate) -> Path = { date ->
        val month = date.monthNumber.toString().padStart(2, '0')
        val day = date.dayOfMonth.toString().padStart(2, '0')
        Path(logsDir, "app.${date.year}-$month-$day.log")
    }
) : LogWriter, SynchronizedObject() {
    // Keep track of current date + file handle
    @Volatile
    private var currentDate: LocalDate? = null

    @Volatile
    private var currentFileHandle: Sink? = null

    override fun writeLog(level: LogLevel, tag: String, message: String?, throwable: Throwable?) {
        val line = formatter.formatLine(level, tag, message, throwable)
        synchronized(this) {
            rotateIfNeeded()
            writeLine(line)
        }
    }

    override fun isLoggingEnabled(level: LogLevel): Boolean {
        return level >= logLevel
    }

    fun getTodayLogFile(): Path {
        val today = Clock.System.now().localDate()
        return filePathForDate(today)
    }

    private fun writeLine(text: String) {
        currentFileHandle?.let {
            it.writeString(text)
            it.writeString("\n")
        }
    }

    /**
     * If the day has changed, close the old file,
     * open a new one, and clean up older logs.
     */
    private fun rotateIfNeeded() {
        val today = Clock.System.now().localDate()
        if (today != currentDate) {
            // Close old
            currentFileHandle?.close()
            currentFileHandle = null

            // Update current date
            currentDate = today

            // Open new
            val newFilePath = filePathForDate(today)
            newFilePath.parent?.let {
                fileSystem.createDirectories(it, mustCreate = false)
            }
            currentFileHandle = fileSystem.sink(newFilePath, append = true).buffered()

            // Clean old logs
            cleanOldLogs()
        }
    }

    /**
     * Keep only the last [maxHistory] logs, remove older ones.
     * Here we look for files matching `app.yyyy-MM-dd.log`.
     */
    @OptIn(ExperimentalForeignApi::class)
    private fun cleanOldLogs() {
        val fileManager = NSFileManager.Companion.defaultManager
        memScoped {
            val errorPtr = alloc<ObjCObjectVar<NSError?>>()
            val contents = fileManager.contentsOfDirectoryAtPath(logsDir.toString(), errorPtr.ptr) ?: emptyList<Any>()
            val logFiles = contents.mapNotNull { it as? String }
                .filter { it.startsWith("app.") && it.endsWith(".log") }
                .sortedDescending() // sort so newest is first

            // Keep the first maxHistory, delete the rest
            val oldLogs = logFiles.drop(maxHistory)
            oldLogs.forEach { fileName ->
                val path = Path(logsDir, fileName).toString()
                fileManager.removeItemAtPath(path, errorPtr.ptr)
            }
        }
    }
}

/**
 * Helper to convert current time to LocalDate in local time zone
 */
private fun Instant.localDate(timeZone: TimeZone = TimeZone.currentSystemDefault()): LocalDate {
    return this.toLocalDateTime(timeZone).date
}
