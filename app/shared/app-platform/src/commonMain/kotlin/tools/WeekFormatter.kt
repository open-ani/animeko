/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.tools

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.number
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * [WeekFormatter] 的文案. 默认值为简体中文, 便于测试和非 UI 场景使用;
 * UI 层应通过 `stringResource` 构造本地化实例.
 */
data class WeekFormatterStrings(
    val today: String = "今天",
    val tomorrow: String = "明天",
    val dayAfterTomorrow: String = "后天",
    val monday: String = "周一",
    val tuesday: String = "周二",
    val wednesday: String = "周三",
    val thursday: String = "周四",
    val friday: String = "周五",
    val saturday: String = "周六",
    val sunday: String = "周日",
    val nextWeek: String = "下%1\$s",
    val sameYearDateFormat: String = "%1\$d 月 %2\$d 日",
    val otherYearDateFormat: String = "%3\$d 年 %1\$d 月 %2\$d 日",
)

internal fun String.withWeekday(weekday: String): String = replace("%1\$s", weekday)

internal fun String.withValues(vararg values: Long): String {
    var result = this
    values.forEachIndexed { index, value ->
        result = result.replace("%${index + 1}\$d", value.toString())
    }
    return result
}

class WeekFormatter(
    private val getTimeNow: () -> Instant = { Clock.System.now() },
    private val strings: WeekFormatterStrings = WeekFormatterStrings(),
) {
    fun format(instance: Instant, timeZone: TimeZone = TimeZone.currentSystemDefault()): String {
        return format(instance.toLocalDateTime(timeZone).date)
    }

    /**
     * 如果是本周内, 则显示今天/明天/后天/周几;
     * 如果是下周, 则显示 "下周几";
     * 其他情况, 包括任意过去时间, 都显示为 "年-月-日", 会尽量省略年份.
     */
    fun format(
        targetDate: LocalDate,
        timeZone: TimeZone = TimeZone.currentSystemDefault(),
    ): String {
        val now = getTimeNow().toLocalDateTime(timeZone).date

        val startOfThisWeek = now.minus(now.dayOfWeek.ordinal, DateTimeUnit.DAY)
        val startOfNextWeek = startOfThisWeek.plus(7, DateTimeUnit.DAY)

        return when {
            targetDate in startOfThisWeek..<startOfNextWeek -> {
                val dayOfWeek = targetDate.dayOfWeek
                when (dayOfWeek.ordinal - now.dayOfWeek.ordinal) {
                    0 -> strings.today
                    1 -> strings.tomorrow
                    2 -> strings.dayAfterTomorrow
                    else -> dayLabel(dayOfWeek.ordinal + 1)
                }
            }

            targetDate >= startOfNextWeek && targetDate < startOfNextWeek.plus(7, DateTimeUnit.DAY) -> {
                strings.nextWeek.withWeekday(dayLabel(targetDate.dayOfWeek.ordinal + 1))
            }

            else -> {
                if (targetDate.year == now.year) {
                    strings.sameYearDateFormat.withValues(
                        targetDate.month.number.toLong(),
                        targetDate.day.toLong(),
                    )
                } else {
                    strings.otherYearDateFormat.withValues(
                        targetDate.month.number.toLong(),
                        targetDate.day.toLong(),
                        targetDate.year.toLong(),
                    )
                }
            }
        }
    }

    private fun dayLabel(day: Int): String {
        return when (day) {
            1 -> strings.monday
            2 -> strings.tuesday
            3 -> strings.wednesday
            4 -> strings.thursday
            5 -> strings.friday
            6 -> strings.saturday
            7 -> strings.sunday
            else -> throw IllegalArgumentException("Invalid day: $day")
        }
    }

    companion object {
        val System = WeekFormatter()
    }
}
