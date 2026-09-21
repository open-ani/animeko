/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.episode

import me.him188.ani.app.data.models.episode.EpisodeInfo
import me.him188.ani.app.data.models.subject.SubjectRecurrence
import me.him188.ani.app.domain.episode.EpisodeCompletionContext.isKnownCompleted
import me.him188.ani.app.domain.episode.EpisodeCompletionContext.isKnownOnAir
import me.him188.ani.app.domain.episode.EpisodeCompletionContext.mapAirDate
import me.him188.ani.app.domain.episode.EpisodeCompletionContext.resolveEpisodeAirTime
import me.him188.ani.datasources.api.EpisodeType
import me.him188.ani.datasources.api.PackedDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant

/**
 * 用生成的测试向量 [EpisodeAirTimeTestVectors] (与服务端相同) 验证 [EpisodeCompletionContext.resolveEpisodeAirTime] 和 [mapAirDate],
 * 以及 [isKnownCompleted] / [isKnownOnAir] 在播出时刻前后 1ms 的翻转.
 */
class EpisodeAirTimeResolverTest {
    /**
     * 服务端只会下发规范化后的 `YYYY-MM-DD` / `YYYY` / 空 (向量中的 `2026-4-26` 等非规范输入在服务端就已被判为无效),
     * 而客户端的 [PackedDate.parseFromDate] 比服务端宽松. 这里先按服务端的规则过滤, 再交给 [PackedDate.parseFromDate],
     * 模拟客户端实际收到的输入.
     */
    private val strictDate = Regex("""\d{4}-\d{2}-\d{2}""")

    private fun EpisodeAirTimeTestVectors.Case.toPackedDate(): PackedDate =
        if (strictDate.matches(airDate)) PackedDate.parseFromDate(airDate) else PackedDate.Invalid

    private fun EpisodeAirTimeTestVectors.Case.toRecurrence(): SubjectRecurrence? {
        val start = recurrenceStartTime
        val interval = intervalMillis
        if (start == null || interval == null) {
            assertTrue(start == null && interval == null, "case $id: recurrence must be all-or-nothing")
            return null
        }
        return SubjectRecurrence(Instant.parse(start), interval.milliseconds)
    }

    @Test
    fun `vectors are present and cover every outcome`() {
        val cases = EpisodeAirTimeTestVectors.cases
        assertTrue(cases.size >= 200, "expected the generated vectors, got ${cases.size}")
        assertTrue(cases.any { it.expected == null })
        assertTrue(cases.any { it.expectedIsExact == true })
        assertTrue(cases.any { it.expectedIsExact == false })
        assertTrue(cases.any { it.recurrenceStartTime == null })
        assertTrue(cases.any { it.intervalMillis == 0L })
    }

    @Test
    fun `every vector resolves to the expected instant and exactness`() {
        val cases = EpisodeAirTimeTestVectors.cases
        val failures = mutableListOf<String>()
        for (case in cases) {
            val actual = resolveEpisodeAirTime(case.toPackedDate(), case.toRecurrence())
            val expected = case.expected?.let { Instant.parse(it) }
            val problem = when {
                expected == null -> if (actual == null) null else "expected null, got $actual"
                actual == null -> "expected $expected (exact=${case.expectedIsExact}), got null"
                actual.instant.toEpochMilliseconds() != expected.toEpochMilliseconds() ->
                    "expected instant $expected, got ${actual.instant}"

                actual.exact != case.expectedIsExact -> "expected exact=${case.expectedIsExact}, got ${actual.exact}"
                else -> null
            }
            if (problem != null) failures += "${case.id} (${case.kind}, airDate='${case.airDate}'): $problem"
        }
        if (failures.isNotEmpty()) {
            fail("${failures.size} of ${cases.size} vectors failed:\n" + failures.joinToString("\n"))
        }
    }

    @Test
    fun `mapAirDate returns the expected instant for every vector`() {
        // 直接与向量的期望值 (epoch millis / null) 比较, 不经过 resolveEpisodeAirTime, 以免测试变成同义反复
        val failures = mutableListOf<String>()
        for (case in EpisodeAirTimeTestVectors.cases) {
            val expectedMillis = case.expected?.let { Instant.parse(it).toEpochMilliseconds() }
            val actualMillis = case.toRecurrence().mapAirDate(case.toPackedDate())?.toEpochMilliseconds()
            if (expectedMillis != actualMillis) {
                failures += "${case.id} (${case.kind}, airDate='${case.airDate}'): expected $expectedMillis, got $actualMillis"
            }
        }
        if (failures.isNotEmpty()) {
            fail("${failures.size} of ${EpisodeAirTimeTestVectors.cases.size} vectors failed:\n" + failures.joinToString("\n"))
        }
    }

    @Test
    fun `lenient PackedDate input is outside the server contract`() {
        // 客户端的 PackedDate.parseFromDate 接受 "2026-4-26", 但服务端只会下发规范化的 "YYYY-MM-DD",
        // 因此向量中此类输入的 expected 为 null 只对规范化后的输入成立. 这里固化客户端本身的宽松行为, 避免误以为是 bug.
        val recurrence = SubjectRecurrence(Instant.parse("2026-04-11T16:00:00Z"), 7.days)
        val lenient = PackedDate.parseFromDate("2026-4-26")
        assertEquals(PackedDate(2026, 4, 26), lenient)
        assertEquals(
            EpisodeAirTime(Instant.parse("2026-04-25T16:00:00Z"), exact = true),
            resolveEpisodeAirTime(lenient, recurrence),
        )
    }

    // ---- predicates -------------------------------------------------------

    private fun episode(airDate: PackedDate) =
        EpisodeInfo(episodeId = 1, type = EpisodeType.MainStory, airDate = airDate)

    private fun assertPredicatesFlipAt(airDate: PackedDate, recurrence: SubjectRecurrence?, instant: Instant) {
        val episode = episode(airDate)
        val before = instant - 1.milliseconds
        val after = instant + 1.milliseconds

        assertFalse(episode.isKnownCompleted(recurrence, before), "1ms before: not completed")
        assertTrue(episode.isKnownOnAir(recurrence, before), "1ms before: on air")

        assertTrue(episode.isKnownCompleted(recurrence, instant), "at the instant: completed")
        assertFalse(episode.isKnownOnAir(recurrence, instant), "at the instant: not on air")

        assertTrue(episode.isKnownCompleted(recurrence, after), "1ms after: completed")
        assertFalse(episode.isKnownOnAir(recurrence, after), "1ms after: not on air")
    }

    @Test
    fun `predicates flip exactly at an exact recurrence slot`() {
        // 545917 これ描いて死ね: 周五 14:30Z 每周, ep2 上映日 2026-07-10 -> 2026-07-10T14:30Z (exact)
        val recurrence = SubjectRecurrence(Instant.parse("2026-07-03T14:30:00Z"), 7.days)
        val airDate = PackedDate(2026, 7, 10)
        val resolved = assertNotNull(resolveEpisodeAirTime(airDate, recurrence))
        assertTrue(resolved.exact)
        assertEquals(Instant.parse("2026-07-10T14:30:00Z"), resolved.instant)
        assertPredicatesFlipAt(airDate, recurrence, resolved.instant)
    }

    @Test
    fun `predicates flip exactly at the day-precision fallback`() {
        // 没有 recurrence: 上映日当天 00:00 JST = 前一天 15:00Z
        val airDate = PackedDate(2026, 7, 10)
        val resolved = assertNotNull(resolveEpisodeAirTime(airDate, null))
        assertFalse(resolved.exact)
        assertEquals(Instant.parse("2026-07-09T15:00:00Z"), resolved.instant)
        assertPredicatesFlipAt(airDate, null, resolved.instant)

        // 有 recurrence 但与 Bangumi 的日期对不上 (bangumi-data 说周二 22:55 JST, Bangumi 记录在周四) 时同样退化为日期精度
        val mismatched = SubjectRecurrence(Instant.parse("2025-12-09T13:55:00Z"), 7.days)
        val thursday = PackedDate(2025, 12, 11)
        val fallback = assertNotNull(resolveEpisodeAirTime(thursday, mismatched))
        assertFalse(fallback.exact)
        assertEquals(Instant.parse("2025-12-10T15:00:00Z"), fallback.instant)
        assertPredicatesFlipAt(thursday, mismatched, fallback.instant)
    }

    @Test
    fun `predicates are both false without an air date`() {
        val recurrence = SubjectRecurrence(Instant.parse("2026-07-03T14:30:00Z"), 7.days)
        val now = Instant.parse("2026-09-04T00:00:00Z")
        for (rec in listOf(recurrence, null)) {
            val episode = episode(PackedDate.Invalid)
            assertFalse(episode.isKnownCompleted(rec, now), "recurrence=$rec")
            assertFalse(episode.isKnownOnAir(rec, now), "recurrence=$rec")
        }
    }

    @Test
    fun `clock-based predicates agree with the explicit-now overloads for far dates`() {
        val recurrence = SubjectRecurrence(Instant.parse("2026-07-03T14:30:00Z"), 7.days)
        val past = episode(PackedDate(2000, 1, 1))
        val future = episode(PackedDate(8888, 1, 1))

        assertTrue(past.isKnownCompleted(recurrence))
        assertFalse(past.isKnownOnAir(recurrence))
        assertFalse(future.isKnownCompleted(recurrence))
        assertTrue(future.isKnownOnAir(recurrence))

        assertTrue(past.isKnownCompleted(null))
        assertFalse(past.isKnownOnAir(null))
        assertFalse(future.isKnownCompleted(null))
        assertTrue(future.isKnownOnAir(null))
    }
}
