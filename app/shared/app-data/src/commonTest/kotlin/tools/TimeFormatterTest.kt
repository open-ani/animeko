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
import kotlinx.datetime.format.char
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class TimeFormatterTest {

    private val fixedTime = Instant.parse("2020-01-01T10:00:00Z")
    private val formatter = LocalDateTime.Format {
        year()
        char('-')
        monthNumber()
        char('-')
        dayOfMonth()
        char(' ')
        hour()
        char(':')
        minute()
        char(':')
        second()
    }

    private val timeFormatter = TimeFormatter(
        formatterWithTime = formatter,
        getTimeNow = { fixedTime },
    )

    @Test
    fun testJustNow() {
        assertEquals("刚刚", timeFormatter.format(fixedTime))
    }

    @Test
    fun testSecondsAgo() {
        val timestamp = Instant.parse("2020-01-01T09:59:30Z")
        assertEquals("30 秒前", timeFormatter.format(timestamp))
    }

    @Test
    fun testSecondsLater() {
        val timestamp = Instant.parse("2020-01-01T10:00:30Z")
        assertEquals("30 秒后", timeFormatter.format(timestamp))
    }

    @Test
    fun testMinutesAgo() {
        val timestamp = Instant.parse("2020-01-01T09:58:00Z")
        assertEquals("2 分钟前", timeFormatter.format(timestamp))
    }

    @Test
    fun testMinutesLater() {
        val timestamp = Instant.parse("2020-01-01T10:02:00Z")
        assertEquals("2 分钟后", timeFormatter.format(timestamp))
    }

    @Test
    fun testHoursAgo() {
        val timestamp = Instant.parse("2020-01-01T08:00:00Z")
        assertEquals("2 小时前", timeFormatter.format(timestamp))
    }

    @Test
    fun testHoursLater() {
        val timestamp = Instant.parse("2020-01-01T12:00:00Z")
        assertEquals("2 小时后", timeFormatter.format(timestamp))
    }

    @Test
    fun testDaysAgo() {
        val timestamp = Instant.parse("2019-12-31T10:00:00Z")
        assertEquals("1 天前", timeFormatter.format(timestamp))
    }
    @Test
    fun testDaysLater() {
        val timestamp = Instant.parse("2020-01-02T11:00:00Z")
        assertEquals("1 天后", timeFormatter.format(timestamp))
    }

    @Test
    fun testUsingFormatter() {
        // 超出相对时间的窗口后, 按系统时区输出完整日期时间.
        // 取距 now 12 天的本地时间: 任何时区偏移 (-12h..+14h) 下与 now 的差都超过 2 天, 一定走这个分支;
        // 期望值用同一个系统时区往返得到, 因此与运行机器的时区无关.
        val local = LocalDateTime(2019, 12, 20, 10, 0, 0)
        val timestamp = local.toInstant(TimeZone.currentSystemDefault())
        assertEquals("2019-12-20 10:00:00", timeFormatter.format(timestamp))
    }
}
