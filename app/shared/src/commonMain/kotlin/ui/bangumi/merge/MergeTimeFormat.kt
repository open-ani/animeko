/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.bangumi.merge

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/**
 * 合并界面时间戳的紧凑格式, 对照 Figma 设计稿:
 * - 今天: `13:40` / `09:12` (时分都补零到两位)
 * - 昨天: `昨 22:10` ([yesterdayLabel] 由调用方本地化)
 * - 今年: `7/25`
 * - 更早: `2024/7/25`
 */
fun formatMergeTime(
    instant: Instant,
    now: Instant,
    timeZone: TimeZone,
    yesterdayLabel: String,
): String {
    val dateTime = instant.toLocalDateTime(timeZone)
    val nowDate = now.toLocalDateTime(timeZone).date
    val date = dateTime.date
    val timeText = "${dateTime.hour.toString().padStart(2, '0')}:${dateTime.minute.toString().padStart(2, '0')}"
    return when {
        date == nowDate -> timeText
        date == nowDate.minus(1, DateTimeUnit.DAY) -> "$yesterdayLabel $timeText"
        date.year == nowDate.year -> "${date.month.number}/${date.day}"
        else -> "${date.year}/${date.month.number}/${date.day}"
    }
}
