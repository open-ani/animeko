/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.notification

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.Lifecycle
import kotlinx.coroutines.delay
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format.MonthNames
import kotlinx.datetime.format.char
import kotlinx.datetime.minus
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import me.him188.ani.app.ui.foundation.effects.OnLifecycleEvent
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.notification_time_date
import me.him188.ani.app.ui.lang.notification_time_detail_date_time
import me.him188.ani.app.ui.lang.notification_time_detail_month_day_time
import me.him188.ani.app.ui.lang.notification_time_hours_ago
import me.him188.ani.app.ui.lang.notification_time_just_now
import me.him188.ani.app.ui.lang.notification_time_minutes_ago
import me.him188.ani.app.ui.lang.notification_time_month_day
import me.him188.ani.app.ui.lang.notification_time_yesterday
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * 通知和公告的时间所用的当前时间与时区. 预览和测试中固定, 让列表中的相对时间不随运行时刻变化.
 *
 * @param timeZoneOverride 为 `null` 时使用系统时区.
 */
class NotificationTimeContext(
    val clock: Clock = Clock.System,
    private val timeZoneOverride: TimeZone? = null,
) {
    val timeZone: TimeZone get() = timeZoneOverride ?: TimeZone.currentSystemDefault()
}

val LocalNotificationTimeContext = staticCompositionLocalOf { NotificationTimeContext() }

/**
 * 列表中的相对时间所用的当前时间, 由 [ProvideNotificationNow] 定时更新. 为 `null` 时各行在首次显示时读取
 * [NotificationTimeContext.clock], 之后不再更新.
 */
val LocalNotificationNow = compositionLocalOf<Instant?> { null }

/**
 * 每分钟更新一次 [LocalNotificationNow], 并在回到前台时立即更新, 使列表中的 "刚刚", "5 分钟前" 和 "昨天" 随时间变化.
 * 在列表页面提供一次, 而不是每行各自计时.
 */
@Composable
fun ProvideNotificationNow(content: @Composable () -> Unit) {
    val context = LocalNotificationTimeContext.current
    val now = remember(context) { mutableStateOf(context.clock.now()) }
    LaunchedEffect(context) {
        while (true) {
            delay(1.minutes)
            now.value = context.clock.now()
        }
    }
    OnLifecycleEvent { event ->
        if (event == Lifecycle.Event.ON_START) now.value = context.clock.now()
    }
    CompositionLocalProvider(LocalNotificationNow provides now.value, content = content)
}

/**
 * 列表行中的时间.
 *
 * 列表行的时间与标题在同一行, 因此尽量短: 今天的通知用相对时间, 之前的只显示日期.
 * 公告列表只显示日期, 见 [announcementRowTime]; 详情页显示日期和时间, 见 [NotificationDetailTime].
 */
@Immutable
sealed interface NotificationRowTime {
    /**
     * 一分钟以内. 也包括今天内因设备时钟偏差而晚于现在的时间.
     */
    @Immutable
    data object JustNow : NotificationRowTime

    /**
     * 一小时以内. 跨过零点时仍按分钟显示.
     */
    @Immutable
    data class MinutesAgo(val minutes: Long) : NotificationRowTime

    /**
     * 今天, 且超过一小时.
     */
    @Immutable
    data class HoursAgo(val hours: Long) : NotificationRowTime

    @Immutable
    data object Yesterday : NotificationRowTime

    /**
     * 今年的其他日期, 不显示年份.
     */
    @Immutable
    data class ThisYear(val date: LocalDate) : NotificationRowTime

    /**
     * 其他年份的日期.
     */
    @Immutable
    data class OtherYear(val date: LocalDate) : NotificationRowTime
}

/**
 * 计算 [timeMillis] 在列表行中的显示方式. 日期按 [timeZone] 中的日历日划分.
 */
fun notificationRowTime(
    timeMillis: Long,
    now: Instant,
    timeZone: TimeZone,
): NotificationRowTime {
    val time = Instant.fromEpochMilliseconds(timeMillis)
    val date = time.toLocalDateTime(timeZone).date
    val today = now.toLocalDateTime(timeZone).date
    val elapsed = now - time
    return when {
        date > today -> dateOnly(date, today)
        elapsed < 1.minutes -> NotificationRowTime.JustNow
        elapsed < 1.hours -> NotificationRowTime.MinutesAgo(elapsed.inWholeMinutes)
        date == today -> NotificationRowTime.HoursAgo(elapsed.inWholeHours)
        date == today.minus(1, DateTimeUnit.DAY) -> NotificationRowTime.Yesterday
        else -> dateOnly(date, today)
    }
}

/**
 * 计算公告列表行中的日期: 不用相对时间, 只有 [NotificationRowTime.ThisYear] 和 [NotificationRowTime.OtherYear].
 */
fun announcementRowTime(
    timeMillis: Long,
    now: Instant,
    timeZone: TimeZone,
): NotificationRowTime = dateOnly(
    Instant.fromEpochMilliseconds(timeMillis).toLocalDateTime(timeZone).date,
    now.toLocalDateTime(timeZone).date,
)

private fun dateOnly(date: LocalDate, today: LocalDate): NotificationRowTime =
    if (date.year == today.year) NotificationRowTime.ThisYear(date) else NotificationRowTime.OtherYear(date)

/**
 * 列表行时间的文本.
 *
 * 日期的格式化参数依次为年、月、日和英文月份缩写, 各语言的字符串只引用自己需要的参数,
 * 例如中文为 "10 月 11 日", 英文为 "Oct 11".
 */
fun notificationRowTimeText(time: NotificationRowTime): NotificationText = when (time) {
    NotificationRowTime.JustNow -> NotificationText.Res(Lang.notification_time_just_now)
    is NotificationRowTime.MinutesAgo -> NotificationText.Res(
        Lang.notification_time_minutes_ago,
        listOf(NotificationText.Plain(time.minutes.toString())),
    )

    is NotificationRowTime.HoursAgo -> NotificationText.Res(
        Lang.notification_time_hours_ago,
        listOf(NotificationText.Plain(time.hours.toString())),
    )

    NotificationRowTime.Yesterday -> NotificationText.Res(Lang.notification_time_yesterday)
    is NotificationRowTime.ThisYear -> NotificationText.Res(Lang.notification_time_month_day, dateArgs(time.date))
    is NotificationRowTime.OtherYear -> NotificationText.Res(Lang.notification_time_date, dateArgs(time.date))
}

private fun dateArgs(date: LocalDate): List<NotificationText> = listOf(
    NotificationText.Plain(date.year.toString()),
    NotificationText.Plain(date.month.number.toString()),
    NotificationText.Plain(date.day.toString()),
    NotificationText.Plain(MonthNames.ENGLISH_ABBREVIATED.names[date.month.number - 1]),
)

/**
 * 列表行中的时间, 见 [NotificationRowTime].
 */
@Composable
fun formatNotificationRowTime(timeMillis: Long): String {
    val context = LocalNotificationTimeContext.current
    val now = LocalNotificationNow.current
    val time = remember(timeMillis, context, now) {
        notificationRowTime(timeMillis, now ?: context.clock.now(), context.timeZone)
    }
    return notificationRowTimeText(time).resolve()
}

/**
 * 公告列表行中的日期, 见 [announcementRowTime].
 */
@Composable
fun formatAnnouncementRowTime(timeMillis: Long): String {
    val context = LocalNotificationTimeContext.current
    val time = remember(timeMillis, context) {
        announcementRowTime(timeMillis, context.clock.now(), context.timeZone)
    }
    return notificationRowTimeText(time).resolve()
}

/**
 * 详情页中的日期和时间, 精确到分钟. 例如中文为 "10 月 11 日 19:02", 不是今年的带年份: "2025 年 10 月 11 日 19:02".
 *
 * 详情页中的所有时间 (通知和公告的时间、举报和处理的时间、被举报评论的发表时间) 都用这个格式.
 *
 * @property withYear 不是今年时为 `true`.
 */
@Immutable
data class NotificationDetailTime(
    val dateTime: LocalDateTime,
    val withYear: Boolean,
)

/**
 * 计算 [timeMillis] 在详情页中的显示方式. 年份按 [timeZone] 中的日期判断.
 */
fun notificationDetailTime(
    timeMillis: Long,
    now: Instant,
    timeZone: TimeZone,
): NotificationDetailTime {
    val dateTime = Instant.fromEpochMilliseconds(timeMillis).toLocalDateTime(timeZone)
    return NotificationDetailTime(dateTime, withYear = dateTime.year != now.toLocalDateTime(timeZone).year)
}

private val HourMinuteFormat = LocalTime.Format {
    hour()
    char(':')
    minute()
}

/**
 * 详情页时间的文本.
 *
 * 格式化参数依次为年、月、日、英文月份缩写和 24 小时制的 `时:分`, 各语言的字符串只引用自己需要的参数.
 */
fun notificationDetailTimeText(time: NotificationDetailTime): NotificationText = NotificationText.Res(
    if (time.withYear) Lang.notification_time_detail_date_time else Lang.notification_time_detail_month_day_time,
    dateArgs(time.dateTime.date) + NotificationText.Plain(HourMinuteFormat.format(time.dateTime.time)),
)

/**
 * 详情页中的日期和时间, 见 [NotificationDetailTime].
 */
@Composable
fun formatNotificationDetailTime(timeMillis: Long): String {
    val context = LocalNotificationTimeContext.current
    val time = remember(timeMillis, context) {
        notificationDetailTime(timeMillis, context.clock.now(), context.timeZone)
    }
    return notificationDetailTimeText(time).resolve()
}
