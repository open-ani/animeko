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
import me.him188.ani.app.ui.lang.notification_time_detail_date_time
import me.him188.ani.app.ui.lang.notification_time_detail_month_day_time
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * 详情页的日期时间和公告列表的日期. 当前时间固定为当地时间 2026-10-13 20:00 (UTC+8).
 */
class NotificationDetailTimeTest {
    private val timeZone = UtcOffset(hours = 8).asTimeZone()
    private val now = Instant.parse("2026-10-13T12:00:00Z")

    private fun local(dateTime: String, zone: TimeZone = timeZone): Instant =
        LocalDateTime.parse(dateTime).toInstant(zone)

    private fun detailTime(time: Instant, now: Instant = this.now, zone: TimeZone = timeZone) =
        notificationDetailTime(time.toEpochMilliseconds(), now, zone)

    private fun announcementTime(time: Instant, now: Instant = this.now) =
        announcementRowTime(time.toEpochMilliseconds(), now, timeZone)

    @Test
    fun `detail times of this year have no year`() {
        assertEquals(
            NotificationDetailTime(LocalDateTime.parse("2026-10-11T19:02"), withYear = false),
            detailTime(local("2026-10-11T19:02")),
        )
        assertEquals(
            NotificationDetailTime(LocalDateTime.parse("2026-01-01T00:00"), withYear = false),
            detailTime(local("2026-01-01T00:00")),
        )
        // 详情不使用相对时间
        assertEquals(
            NotificationDetailTime(LocalDateTime.parse("2026-10-13T19:59"), withYear = false),
            detailTime(now - 1.minutes),
        )
    }

    @Test
    fun `detail times of other years have the year`() {
        assertEquals(
            NotificationDetailTime(LocalDateTime.parse("2025-12-31T23:59"), withYear = true),
            detailTime(local("2025-12-31T23:59")),
        )
        assertEquals(
            NotificationDetailTime(LocalDateTime.parse("2027-01-01T08:00"), withYear = true),
            detailTime(local("2027-01-01T08:00")),
        )
    }

    @Test
    fun `detail years follow the time zone`() {
        val time = Instant.parse("2025-12-31T17:00:00Z")
        val newYear = Instant.parse("2026-01-01T02:00:00Z")
        // UTC+8: 2026-01-01 01:00, 现在是 2026-01-01 10:00
        assertEquals(
            NotificationDetailTime(LocalDateTime.parse("2026-01-01T01:00"), withYear = false),
            detailTime(time, now = newYear),
        )
        // UTC: 2025-12-31 17:00, 现在是 2026-01-01 02:00
        assertEquals(
            NotificationDetailTime(LocalDateTime.parse("2025-12-31T17:00"), withYear = true),
            detailTime(time, now = newYear, zone = TimeZone.UTC),
        )
    }

    @Test
    fun `detail text passes date parts and zero padded time`() {
        val args = listOf("2025", "1", "5", "Jan", "09:05").map { NotificationText.Plain(it) }
        assertEquals(
            NotificationText.Res(Lang.notification_time_detail_month_day_time, args),
            notificationDetailTimeText(NotificationDetailTime(LocalDateTime.parse("2025-01-05T09:05"), withYear = false)),
        )
        assertEquals(
            NotificationText.Res(Lang.notification_time_detail_date_time, args),
            notificationDetailTimeText(NotificationDetailTime(LocalDateTime.parse("2025-01-05T09:05"), withYear = true)),
        )
    }

    @Test
    fun `announcement rows always show the date`() {
        assertEquals(NotificationRowTime.ThisYear(LocalDate(2026, 10, 13)), announcementTime(now))
        assertEquals(NotificationRowTime.ThisYear(LocalDate(2026, 10, 13)), announcementTime(now - 5.minutes))
        assertEquals(NotificationRowTime.ThisYear(LocalDate(2026, 10, 12)), announcementTime(local("2026-10-12T23:00")))
        assertEquals(NotificationRowTime.ThisYear(LocalDate(2026, 1, 1)), announcementTime(local("2026-01-01T00:00")))
        assertEquals(NotificationRowTime.OtherYear(LocalDate(2025, 12, 31)), announcementTime(local("2025-12-31T23:59")))
    }
}
