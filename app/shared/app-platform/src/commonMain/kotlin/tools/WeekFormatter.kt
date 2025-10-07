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

class WeekFormatter(
    private val getTimeNow: () -> Instant = { Clock.System.now() },
) {
    fun format(instance: Instant, timeZone: TimeZone = TimeZone.currentSystemDefault()): String {
        return format(instance.toLocalDateTime(timeZone).date)
    }

    /**
     * 如果是本周内, 则显示周几;
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
                    0 -> "今天"
                    1 -> "明天"
                    2 -> "后天"
                    else -> dayToChinese(dayOfWeek.ordinal + 1)
                }
            }

            targetDate >= startOfNextWeek && targetDate < startOfNextWeek.plus(7, DateTimeUnit.DAY) -> {
                val dayOfWeek = targetDate.dayOfWeek
                "下${dayToChinese(dayOfWeek.ordinal + 1)}"
            }

            else -> {
                if (targetDate.year == now.year) {
                    "${targetDate.month.number} 月 ${targetDate.day} 日"
                } else {
                    "${targetDate.year} 年 ${targetDate.month.number} 月 ${targetDate.day} 日"
                }
            }
        }
    }

    private fun dayToChinese(day: Int): String {
        return when (day) {
            1 -> "周一"
            2 -> "周二"
            3 -> "周三"
            4 -> "周四"
            5 -> "周五"
            6 -> "周六"
            7 -> "周日"
            else -> throw IllegalArgumentException("Invalid day: $day")
        }
    }

    companion object {
        val System = WeekFormatter()
    }
}
