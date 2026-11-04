/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.tools

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format.DateTimeFormat
import kotlinx.datetime.format.char
import kotlinx.datetime.toLocalDateTime
import me.him188.ani.app.platform.currentSystemTimeZone
import kotlin.time.Clock
import kotlin.time.Instant

private val yyyyMMdd = LocalDateTime.Format {
    year()
    char('-')
    monthNumber()
    char('-')
    day()
    char(' ')
    hour()
    char(':')
    minute()
}

/**
 * 绝对时间的格式化. 相对时间 (例如"5 分钟前") 与时区无关, 超出相对时间窗口后按 [timeZone] 输出日期时间.
 *
 * @param timeZone 显示用的时区. App 内由 `LocalTimeZone` 提供用户选择的时区;
 * 默认值只用于预览与测试.
 *
 * @see formatDateTime
 */
// TimeFormatterTest
class TimeFormatter(
    private val timeZone: TimeZone = currentSystemTimeZone(),
    private val formatterWithTime: DateTimeFormat<LocalDateTime> = yyyyMMdd,
    private val formatterWithoutTime: DateTimeFormat<LocalDateTime> = yyyyMMdd,
    private val getTimeNow: () -> Instant = { Clock.System.now() },
) {
    fun format(timestamp: Long, showTime: Boolean = true): String {
        return format(Instant.fromEpochMilliseconds(timestamp), showTime)
    }

    fun format(instant: Instant, showTime: Boolean = true): String {
        val now = getTimeNow()

        // written by ChatGPT
        return when (val differenceInSeconds = (now - instant).inWholeSeconds) {
            in 0..1L -> "刚刚"
            in 0..59 -> "$differenceInSeconds 秒前"
            in -60..0 -> "${-differenceInSeconds} 秒后"
            in 60..<3600 -> "${differenceInSeconds / 60} 分钟前"
            in -3600..-60 -> "${-differenceInSeconds / 60} 分钟后"
            in 3600..<86400 -> "${differenceInSeconds / 3600} 小时前"
            in -86400..<-3600 -> "${-differenceInSeconds / 3600} 小时后"
            in 86400..<86400 * 2 -> "${differenceInSeconds / 86400} 天前"
            in -86400 * 2..<-86400 -> "${-differenceInSeconds / 86400} 天后"
            else -> getFormatter(showTime).format(instant.toLocalDateTime(timeZone))
        }
    }

    private fun getFormatter(showTime: Boolean) =
        if (showTime) formatterWithTime else formatterWithoutTime
}
