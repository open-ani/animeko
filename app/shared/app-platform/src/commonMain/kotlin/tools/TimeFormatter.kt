package me.him188.ani.app.tools

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format.DateTimeFormat
import kotlinx.datetime.format.char
import kotlinx.datetime.toLocalDateTime

private val yyyyMMdd = LocalDateTime.Format {
    year()
    char('-')
    monthNumber()
    char('-')
    dayOfMonth()
    char(' ')
    hour()
    char(':')
    minute()
}

/**
 * @see formatDateTime
 */
// TimeFormatterTest
class TimeFormatter(
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
            else -> getFormatter(showTime).format(instant.toLocalDateTime(TimeZone.currentSystemDefault()))
        }
    }

    private fun getFormatter(showTime: Boolean) =
        if (showTime) formatterWithTime else formatterWithoutTime
}
