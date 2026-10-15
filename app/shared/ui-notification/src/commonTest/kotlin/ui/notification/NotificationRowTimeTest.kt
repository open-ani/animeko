/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.notification

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.UtcOffset
import kotlinx.datetime.asTimeZone
import kotlinx.datetime.toInstant
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.notification_time_date
import me.him188.ani.app.ui.lang.notification_time_hours_ago
import me.him188.ani.app.ui.lang.notification_time_just_now
import me.him188.ani.app.ui.lang.notification_time_minutes_ago
import me.him188.ani.app.ui.lang.notification_time_month_day
import me.him188.ani.app.ui.lang.notification_time_yesterday
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class NotificationRowTimeTest {
    private val timeZone = UtcOffset(hours = 8).asTimeZone()

    /**
     * 当地时间 2026-10-13 20:00.
     */
    private val now = Instant.parse("2026-10-13T12:00:00Z")

    private fun local(dateTime: String, zone: TimeZone = timeZone): Instant =
        LocalDateTime.parse(dateTime).toInstant(zone)

    private fun rowTime(time: Instant, now: Instant = this.now, zone: TimeZone = timeZone): NotificationRowTime =
        notificationRowTime(time.toEpochMilliseconds(), now, zone)

    @Test
    fun `less than a minute ago is just now`() {
        assertEquals(NotificationRowTime.JustNow, rowTime(now))
        assertEquals(NotificationRowTime.JustNow, rowTime(now - 59.seconds))
    }

    @Test
    fun `later today because of clock skew is just now`() {
        assertEquals(NotificationRowTime.JustNow, rowTime(now + 30.seconds))
        assertEquals(NotificationRowTime.JustNow, rowTime(local("2026-10-13T23:59")))
    }

    @Test
    fun `minutes within the first hour`() {
        assertEquals(NotificationRowTime.MinutesAgo(1), rowTime(now - 1.minutes))
        assertEquals(NotificationRowTime.MinutesAgo(59), rowTime(now - 59.minutes - 59.seconds))
    }

    @Test
    fun `minutes are kept across midnight`() {
        assertEquals(
            NotificationRowTime.MinutesAgo(20),
            rowTime(local("2026-10-12T23:50"), now = local("2026-10-13T00:10")),
        )
    }

    @Test
    fun `hours for earlier today`() {
        assertEquals(NotificationRowTime.HoursAgo(1), rowTime(now - 1.hours))
        assertEquals(NotificationRowTime.HoursAgo(19), rowTime(now - 19.hours - 59.minutes))
        assertEquals(NotificationRowTime.HoursAgo(20), rowTime(local("2026-10-13T00:00")))
    }

    @Test
    fun `previous calendar day is yesterday`() {
        assertEquals(NotificationRowTime.Yesterday, rowTime(local("2026-10-12T23:59")))
        assertEquals(NotificationRowTime.Yesterday, rowTime(local("2026-10-12T00:00")))
        // 不到两小时, 但已经是前一天
        assertEquals(
            NotificationRowTime.Yesterday,
            rowTime(local("2026-10-12T23:00"), now = local("2026-10-13T00:30")),
        )
    }

    @Test
    fun `earlier this year shows month and day`() {
        assertEquals(NotificationRowTime.ThisYear(LocalDate(2026, 10, 11)), rowTime(local("2026-10-11T23:59")))
        assertEquals(NotificationRowTime.ThisYear(LocalDate(2026, 1, 1)), rowTime(local("2026-01-01T00:00")))
    }

    @Test
    fun `other years show the full date`() {
        assertEquals(NotificationRowTime.OtherYear(LocalDate(2025, 12, 31)), rowTime(local("2025-12-31T23:59")))
        assertEquals(NotificationRowTime.OtherYear(LocalDate(2024, 2, 29)), rowTime(local("2024-02-29T12:00")))
    }

    @Test
    fun `yesterday across new year`() {
        val newYear = local("2026-01-01T09:00")
        assertEquals(NotificationRowTime.Yesterday, rowTime(local("2025-12-31T22:00"), now = newYear))
        assertEquals(
            NotificationRowTime.OtherYear(LocalDate(2025, 12, 30)),
            rowTime(local("2025-12-30T22:00"), now = newYear),
        )
    }

    @Test
    fun `dates after today show the date`() {
        assertEquals(NotificationRowTime.ThisYear(LocalDate(2026, 10, 14)), rowTime(local("2026-10-14T08:00")))
        assertEquals(NotificationRowTime.OtherYear(LocalDate(2027, 1, 2)), rowTime(local("2027-01-02T08:00")))
    }

    @Test
    fun `calendar days follow the time zone`() {
        val time = Instant.parse("2026-10-12T17:00:00Z")
        // UTC: 10-12 17:00, 现在是 10-13 12:00
        assertEquals(NotificationRowTime.Yesterday, rowTime(time, zone = TimeZone.UTC))
        // UTC+8: 10-13 01:00, 现在是 10-13 20:00
        assertEquals(NotificationRowTime.HoursAgo(19), rowTime(time))
    }

    @Test
    fun `relative times use string resources`() {
        assertEquals(NotificationText.Res(Lang.notification_time_just_now), notificationRowTimeText(NotificationRowTime.JustNow))
        assertEquals(
            NotificationText.Res(Lang.notification_time_minutes_ago, listOf(NotificationText.Plain("5"))),
            notificationRowTimeText(NotificationRowTime.MinutesAgo(5)),
        )
        assertEquals(
            NotificationText.Res(Lang.notification_time_hours_ago, listOf(NotificationText.Plain("3"))),
            notificationRowTimeText(NotificationRowTime.HoursAgo(3)),
        )
        assertEquals(
            NotificationText.Res(Lang.notification_time_yesterday),
            notificationRowTimeText(NotificationRowTime.Yesterday),
        )
    }

    @Test
    fun `dates pass year month day and english month abbreviation`() {
        val args = listOf("2025", "1", "5", "Jan").map { NotificationText.Plain(it) }
        assertEquals(
            NotificationText.Res(Lang.notification_time_month_day, args),
            notificationRowTimeText(NotificationRowTime.ThisYear(LocalDate(2025, 1, 5))),
        )
        assertEquals(
            NotificationText.Res(Lang.notification_time_date, args),
            notificationRowTimeText(NotificationRowTime.OtherYear(LocalDate(2025, 1, 5))),
        )
        assertEquals(
            NotificationText.Plain("Dec"),
            (notificationRowTimeText(NotificationRowTime.ThisYear(LocalDate(2026, 12, 31))) as NotificationText.Res)
                .args.last(),
        )
    }
}
