/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.models.subject

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import me.him188.ani.datasources.api.PackedDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days


/**
 * Tests for [SubjectRecurrence.calculateEpisodeAirTime].
 */
class SubjectRecurrenceTest {
    private val zone: TimeZone = TimeZone.UTC

    /** Helper to pack a [LocalDate] in the same way production code expects. */
    private fun LocalDate.packed(): PackedDate = PackedDate.parseFromDate(this.toString())

    /** Short alias: add whole–day durations to an [Instant]. */
    private operator fun Instant.plus(days: Int) = this + days.days

    // -------------------------------------------------------------------------
    // Happy‑path cases
    // -------------------------------------------------------------------------

    @Test
    fun `exact-date - first-episode - returns-startTime`() {
        val startDate = LocalDate(2025, 1, 3)
        val startInstant = startDate.atStartOfDayIn(zone)

        val sut = SubjectRecurrence(
            startTime = startInstant,
            interval = 7.days,           // weekly
        )

        val result = sut.calculateEpisodeAirTime(startDate.packed())

        assertEquals(startInstant, result, "Should return the first airing instant")
    }

    @Test
    fun `date-before-start - returns-first`() {
        val startInstant = Instant.parse("2025-03-10T00:00:00Z")
        val sut = SubjectRecurrence(startInstant, 7.days)

        val dayBefore = LocalDate(2025, 3, 9).packed()
        assertEquals(startInstant, sut.calculateEpisodeAirTime(dayBefore), "Dates before premiere must be null")
    }

    @Test
    fun `exact-date - n-th-episode - returns-correct-instant`() {
        val start = Instant.parse("2025-01-01T12:00:00Z")   // midday for safer TZ maths
        val interval = 7.days
        val episodesToSkip = 5L                              // ask for episode #5

        val expectedInstant = start + interval * episodesToSkip.toInt()
        val wantedDate = expectedInstant.toLocalDateTime(zone).date

        val sut = SubjectRecurrence(start, interval)

        val result = sut.calculateEpisodeAirTime(wantedDate.packed())

        assertEquals(expectedInstant, result, "Should return the n‑th episode instant")
    }

    @Test
    fun `off-by-one-day - still-within-tolerance`() {
        val start = LocalDate(2024, 6, 1)
        val startInstant = start.atStartOfDayIn(zone)
        val sut = SubjectRecurrence(startInstant, 14.days)   // bi‑weekly show

        val targetDate = start + DatePeriod(days = 1)        // 24 h after first episode
        val result = sut.calculateEpisodeAirTime(targetDate.packed())

        assertEquals(startInstant, result, "±1 day should still map to the first episode")
    }

    // -------------------------------------------------------------------------
    // Error / edge‑cases
    // -------------------------------------------------------------------------

    @Test
    fun `outside-24h-window - returns-null`() {
        val startInstant = Instant.parse("2025-02-01T00:00:00Z")
        val sut = SubjectRecurrence(startInstant, 7.days)

        // Two days after the first episode -> outside tolerance
        val farDate = (startInstant + 2).toLocalDateTime(zone).date.packed()
        assertNull(sut.calculateEpisodeAirTime(farDate), "More than ±24 h should not match")
    }

    @Test
    fun `zero-or-negative-interval - returns-null`() {
        val sutZero = SubjectRecurrence(
            startTime = Instant.parse("2025-05-01T00:00:00Z"),
            interval = Duration.ZERO,
        )
        val sutNegative = SubjectRecurrence(
            startTime = Instant.parse("2025-05-01T00:00:00Z"),
            interval = (-7).days,
        )
        val anyDate = LocalDate(2025, 5, 1).packed()

        assertNull(sutZero.calculateEpisodeAirTime(anyDate), "Zero interval is invalid")
        assertNull(sutNegative.calculateEpisodeAirTime(anyDate), "Negative interval is invalid")
    }
}
