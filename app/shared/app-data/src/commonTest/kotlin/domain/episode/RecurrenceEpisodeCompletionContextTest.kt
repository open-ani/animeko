/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.episode

import kotlinx.datetime.Instant
import me.him188.ani.app.data.models.subject.SubjectRecurrence
import me.him188.ani.app.domain.episode.EpisodeCompletionContext.mapAirDate
import me.him188.ani.datasources.api.PackedDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.days

class EpisodeCompletionParamsTest {
    @Test
    fun `mapAirDate null recurrence`() {
        val airDate = PackedDate(2024, 11, 20)
        assertEquals(
            Instant.parse("2024-11-20T00:00:00+09:00"),
            null.mapAirDate(airDate = airDate),
        )
    }

    @Test
    fun `mapAirDate match a little before one week`() {
        val airDate = PackedDate(2024, 11, 20)
        val recurrence = SubjectRecurrence(
            startTime = Instant.parse("2024-11-14T10:00:00+09:00"),
            interval = 7.days,
        )

        assertEquals(
            Instant.parse("2024-11-21T10:00:00+09:00"),
            recurrence.mapAirDate(airDate = airDate),
        )
    }

    @Test
    fun `mapAirDate match a little after one week`() {
        val airDate = PackedDate(2024, 11, 22)
        val recurrence = SubjectRecurrence(
            startTime = Instant.parse("2024-11-14T10:00:00+09:00"),
            interval = 7.days,
        )

        assertEquals(
            Instant.parse("2024-11-21T10:00:00+09:00"),
            recurrence.mapAirDate(airDate = airDate),
        )
    }

    @Test
    fun `mapAirDate match a little before initial startTime`() {
        val airDate = PackedDate(2024, 11, 13)
        val recurrence = SubjectRecurrence(
            startTime = Instant.parse("2024-11-14T10:00:00+09:00"),
            interval = 7.days,
        )

        assertEquals(
            Instant.parse("2024-11-14T10:00:00+09:00"),
            recurrence.mapAirDate(airDate = airDate),
        )
    }

    @Test
    fun `mapAirDate match a little after initial startTime`() {
        val airDate = PackedDate(2024, 11, 15)
        val recurrence = SubjectRecurrence(
            startTime = Instant.parse("2024-11-14T10:00:00+09:00"),
            interval = 7.days,
        )

        assertEquals(
            Instant.parse("2024-11-14T10:00:00+09:00"),
            recurrence.mapAirDate(airDate = airDate),
        )
    }

    @Test
    fun `mapAirDate match a little after two weeks`() {
        val airDate = PackedDate(2024, 11, 29)
        val recurrence = SubjectRecurrence(
            startTime = Instant.parse("2024-11-14T10:00:00+09:00"),
            interval = 7.days,
        )

        assertEquals(
            Instant.parse("2024-11-28T10:00:00+09:00"),
            recurrence.mapAirDate(airDate = airDate),
        )
    }
}
