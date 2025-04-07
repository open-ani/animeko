/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.utils.logging

import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSTimeZone
import platform.Foundation.localTimeZone

class LogLineFormatter {
    private val logLineDateFormatter = NSDateFormatter().apply {
        dateFormat = "yyyy-MM-dd HH:mm:ss"
        timeZone = NSTimeZone.Companion.localTimeZone
    }

    fun formatLine(
        level: LogLevel,
        tag: String,
        message: String?,
        throwable: Throwable?
    ): String {
        val now = NSDate()
        val dateStr = logLineDateFormatter.stringFromDate(now)
        // Simple pattern:  "2025-04-05 12:34:56 INFO  MyLogger message"
        // Adjust to your preference
        val sb = StringBuilder("$dateStr [${level.name.padEnd(5)}] ${tag.padStart(10)}: ${message.orEmpty()}")
        if (throwable != null) {
            sb.appendLine()
            sb.appendLine(throwable.stackTraceToString())
        }
        return sb.toString()
    }
}