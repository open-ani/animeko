/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.tools

import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

// GPT 写的
class WeekFormatterTest {

    private val fixedInstant = Instant.parse("2024-08-21T12:00:00Z") // 周三
    private val testTimeZone = TimeZone.of("Asia/Shanghai") // 上海时间为 "2024-08-21T20:00:00"

    // Helper function to create a fixed WeekFormatter for predictable testing
    private fun createFormatter(now: Instant): WeekFormatter {
        return WeekFormatter(getTimeNow = { now })
    }

    @Test
    fun testFormatToday() {
        val formatter = createFormatter(fixedInstant)
        val result = formatter.format(fixedInstant, testTimeZone)
        assertEquals("今天", result)  // 2024-08-21 is a Wednesday (周三)
    }

    @Test
    fun testFormatTomorrow() {
        val formatter = createFormatter(fixedInstant)
        val result = formatter.format(Instant.parse("2024-08-22T12:00:00Z"), testTimeZone)
        assertEquals("明天", result)
    }

    @Test
    fun testFormatTomorrowLessThan24Hours() {
        val formatter = createFormatter(fixedInstant)
        val result = formatter.format(Instant.parse("2024-08-22T01:00:00Z"), testTimeZone)
        assertEquals("明天", result)
    }

    @Test
    fun testFormatThisWeek() {
        val formatter = createFormatter(fixedInstant)
        val instance = Instant.parse("2024-08-24T12:00:00Z")  // 2024-08-24 is Saturday
        val result = formatter.format(instance, testTimeZone)
        assertEquals("周六", result)
    }

    @Test
    fun testFormatNextWeek() {
        val formatter = createFormatter(fixedInstant)
        val instance = Instant.parse("2024-08-28T12:00:00Z")  // 2024-08-28 is next Wednesday
        val result = formatter.format(instance, testTimeZone)
        assertEquals("下周三", result)
    }

    @Test
    fun testFormatDifferentYear() {
        val formatter = createFormatter(fixedInstant)
        val instance = Instant.parse("2023-12-25T12:00:00Z")  // A date in the previous year
        val result = formatter.format(instance, testTimeZone)
        assertEquals("2023 年 12 月 25 日", result)
    }

    @Test
    fun testFormatDifferentMonthSameYear() {
        val formatter = createFormatter(fixedInstant)
        val instance = Instant.parse("2024-11-01T12:00:00Z")  // A date in a different month, same year
        val result = formatter.format(instance, testTimeZone)
        assertEquals("11 月 1 日", result)
    }

    @Test
    fun testFormatFutureDateOutsideNextWeek() {
        val formatter = createFormatter(fixedInstant)
        val instance = Instant.parse("2024-09-15T12:00:00Z")  // Date beyond the next week
        val result = formatter.format(instance, testTimeZone)
        assertEquals("9 月 15 日", result)  // Since the year is the same as now
    }

    @Test
    fun testFormatPastDate() {
        val formatter = createFormatter(fixedInstant)
        val instance = Instant.parse("2024-01-01T12:00:00Z")  // A past date in the same year
        val result = formatter.format(instance, testTimeZone)
        assertEquals("1 月 1 日", result)
    }

    @Test
    fun testFormatNextWeekCrossingYearBoundary() {
        val formatter = createFormatter(Instant.parse("2023-12-29T12:00:00Z"))
        val instance = Instant.parse("2024-01-03T12:00:00Z")  // Date in the first week of the next year
        val result = formatter.format(instance, testTimeZone)
        assertEquals("下周三", result)
    }

    // edge cases

    /*
Edge Case for the End of the Week:

Test Format for Sunday: Confirm how the formatter handles the last day of the week (Sunday) since the transition from one week to another could cause issues, especially in cultures where the week might start on a different day.
Test Format for the Beginning of the Week: Similarly, ensure that the function handles dates at the very start of the week (Monday) correctly.
Edge Case for the End of the Year:

Test Format for December 31st: Ensure that December 31st of the current year correctly formats the date, especially if it’s at the end of the week.
Test Format for January 1st: This should be tested to see how it handles dates that are right at the start of the year.
Different Time Zones:

Test Format with UTC TimeZone: Ensure the formatter works correctly when using UTC as the time zone.
Test Format with a Non-Default TimeZone: Use a different timezone (e.g., TimeZone.of("America/New_York")) to ensure the formatter handles timezone differences appropriately.
Exact Boundary Between Weeks:

Test for the Exact Transition Between This Week and Next Week: A date exactly on the boundary between this week and next week should be tested to ensure it falls into the correct case (e.g., Sunday night at midnight).
     */

    @Test
    fun testFormatSundayThisWeek() {
        val formatter = createFormatter(fixedInstant)
        val instance = Instant.parse("2024-08-25T12:00:00Z")  // 2024-08-25 is Sunday
        val result = formatter.format(instance, testTimeZone)
        assertEquals("周日", result)
    }

    @Test
    fun testFormatMondayNextWeek() {
        val formatter = createFormatter(fixedInstant)
        val instance = Instant.parse("2024-08-26T12:00:00Z")  // 2024-08-26 is next Monday
        val result = formatter.format(instance, testTimeZone)
        assertEquals("下周一", result)
    }

    @Test
    fun testFormatEndOfYear() {
        val formatter = createFormatter(fixedInstant)
        val instance = Instant.parse("2024-12-31T12:00:00Z")
        val result = formatter.format(instance, testTimeZone)
        assertEquals("12 月 31 日", result)
    }

    @Test
    fun testFormatStartOfYear() {
        val formatter = createFormatter(fixedInstant)
        val instance = Instant.parse("2024-01-01T12:00:00Z")
        val result = formatter.format(instance, testTimeZone)
        assertEquals("1 月 1 日", result)
    }

    @Test
    fun testFormatWithDifferentTimeZone() {
        val formatter = createFormatter(fixedInstant)
        val instance = Instant.parse("2024-08-21T12:00:00Z")
        val result = formatter.format(instance, TimeZone.of("America/New_York"))
        assertEquals("今天", result)
    }
}
