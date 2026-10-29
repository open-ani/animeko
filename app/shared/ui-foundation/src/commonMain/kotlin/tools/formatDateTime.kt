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
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import me.him188.ani.app.platform.currentSystemTimeZone
import kotlin.time.Instant

val LocalTimeFormatter = androidx.compose.runtime.compositionLocalOf<TimeFormatter> {
    error("No TimeFormatter provided")
}

/**
 * 绝对日期时间显示使用的时区. App 根节点提供用户选择的时区; 偏好跟随系统时即为系统时区.
 *
 * 默认值是设备系统时区, 供未提供该值的预览与测试使用.
 * 相对时间 ("5 分钟前") 与时区无关, 不受影响.
 */
val LocalTimeZone: ProvidableCompositionLocal<TimeZone> = staticCompositionLocalOf { currentSystemTimeZone() }

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
    val timeZone = LocalTimeZone.current
    return remember(dateTime, showTime, timeZone) {
        val instant = dateTime.toInstant(timeZone)
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
    val timeZone = LocalTimeZone.current
    return remember(timestamp, timeZone) {
        if (timestamp == 0L) ""
        else WeekFormatter.System.format(Instant.fromEpochMilliseconds(timestamp), timeZone)
    }
}
