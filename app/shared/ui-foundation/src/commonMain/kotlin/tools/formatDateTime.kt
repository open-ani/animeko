/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.tools

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.time.Instant

val LocalTimeFormatter = androidx.compose.runtime.compositionLocalOf<TimeFormatter> {
    error("No TimeFormatter provided")
}

/**
 * @see TimeFormatter
 * @see LocalTimeFormatter
 *
 * @param showTime 当距今比较远时, 是否展示时间
 */
@Composable
fun formatDateTime(
    timestamp: Long, // millis
    showTime: Boolean = true,
): String {
    val formatter by rememberUpdatedState(LocalTimeFormatter.current)
    return remember(timestamp, showTime) {
        if (timestamp == 0L) ""
        else formatter.format(timestamp, showTime)
    }
}

@Composable
fun formatDateTime(
    dateTime: LocalDateTime,
    showTime: Boolean = true,
): String {
    val formatter by rememberUpdatedState(LocalTimeFormatter.current)
    return remember(dateTime, showTime) {
        val instant = dateTime.toInstant(TimeZone.currentSystemDefault())
        if (instant.toEpochMilliseconds() == 0L) ""
        else formatter.format(instant, showTime)
    }
}

/**
 * @see WeekFormatter
 */
@Composable
fun formatDateAsWeek(
    timestamp: Long, // millis
    showTime: Boolean = true,
): String {
    return remember(timestamp, showTime) {
        if (timestamp == 0L) ""
        else WeekFormatter.System.format(Instant.fromEpochMilliseconds(timestamp))
    }
}
