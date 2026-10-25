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
 * [TimeFormatter] 的相对时间文案. 默认值为简体中文, 便于测试和非 UI 场景使用;
 * UI 层应通过 `stringResource` 构造本地化实例.
 */
data class TimeFormatterStrings(
    val justNow: String = "刚刚",
    val secondsAgo: String = "%1\$d 秒前",
    val secondsLater: String = "%1\$d 秒后",
    val minutesAgo: String = "%1\$d 分钟前",
    val minutesLater: String = "%1\$d 分钟后",
    val hoursAgo: String = "%1\$d 小时前",
    val hoursLater: String = "%1\$d 小时后",
    val daysAgo: String = "%1\$d 天前",
    val daysLater: String = "%1\$d 天后",
)

internal fun String.withValue(value: Long): String = replace("%1\$d", value.toString())

/**
 * @see formatDateTime
 */
// TimeFormatterTest
class TimeFormatter(
    private val formatterWithTime: DateTimeFormat<LocalDateTime> = yyyyMMdd,
    private val formatterWithoutTime: DateTimeFormat<LocalDateTime> = yyyyMMdd,
    private val getTimeNow: () -> Instant = { Clock.System.now() },
    private val strings: TimeFormatterStrings = TimeFormatterStrings(),
) {
    fun format(timestamp: Long, showTime: Boolean = true): String {
        return format(Instant.fromEpochMilliseconds(timestamp), showTime)
    }

    fun format(instant: Instant, showTime: Boolean = true): String {
        val now = getTimeNow()

        // written by ChatGPT
        return when (val differenceInSeconds = (now - instant).inWholeSeconds) {
            in 0..1L -> strings.justNow
            in 0..59 -> strings.secondsAgo.withValue(differenceInSeconds)
            in -60..0 -> strings.secondsLater.withValue(-differenceInSeconds)
            in 60..<3600 -> strings.minutesAgo.withValue(differenceInSeconds / 60)
            in -3600..-60 -> strings.minutesLater.withValue(-differenceInSeconds / 60)
            in 3600..<86400 -> strings.hoursAgo.withValue(differenceInSeconds / 3600)
            in -86400..<-3600 -> strings.hoursLater.withValue(-differenceInSeconds / 3600)
            in 86400..<86400 * 2 -> strings.daysAgo.withValue(differenceInSeconds / 86400)
            in -86400 * 2..<-86400 -> strings.daysLater.withValue(-differenceInSeconds / 86400)
            else -> getFormatter(showTime).format(instant.toLocalDateTime(TimeZone.currentSystemDefault()))
        }
    }

    private fun getFormatter(showTime: Boolean) =
        if (showTime) formatterWithTime else formatterWithoutTime
}
