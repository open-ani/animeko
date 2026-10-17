/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.models.subject

import me.him188.ani.app.domain.episode.EpisodeAirTime
import me.him188.ani.app.domain.episode.EpisodeCompletionContext.resolveEpisodeAirTime
import me.him188.ani.datasources.api.PackedDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

/**
 * [SubjectRecurrence] 作为 [resolveEpisodeAirTime] 输入时的行为.
 *
 * 原先是 `SubjectRecurrence.calculateEpisodeAirTime` (本地时区, ±1 天容差, 匹配不到返回 `null`) 的用例;
 * 该函数已被统一算法取代, 用例按新语义改写, 每个用例注明了与旧期望的差异.
 */
class SubjectRecurrenceTest {
    private fun exact(iso: String) = EpisodeAirTime(Instant.parse(iso), exact = true)
    private fun dayStart(iso: String) = EpisodeAirTime(Instant.parse(iso), exact = false)

    @Test
    fun `first episode on the start date returns startTime`() {
        // 首播 2025-01-03T00:00Z = 09:00 JST, 与旧期望一致
        val sut = SubjectRecurrence(startTime = Instant.parse("2025-01-03T00:00:00Z"), interval = 7.days)
        assertEquals(exact("2025-01-03T00:00:00Z"), resolveEpisodeAirTime(PackedDate(2025, 1, 3), sut))
    }

    @Test
    fun `n-th episode returns startTime plus n intervals`() {
        // 首播 2025-01-01T12:00Z = 21:00 JST; 5 个周期后 = 2025-02-05T12:00Z, 与旧期望一致
        val sut = SubjectRecurrence(Instant.parse("2025-01-01T12:00:00Z"), 7.days)
        assertEquals(exact("2025-02-05T12:00:00Z"), resolveEpisodeAirTime(PackedDate(2025, 2, 5), sut))
    }

    @Test
    fun `date before the premiere by more than 30h falls back to day precision`() {
        // 旧期望: startTime. 统一算法只在首播晚于该日 00:00 (JST) 不超过 30h 时取首播, 这里是 33h
        val sut = SubjectRecurrence(Instant.parse("2025-03-10T00:00:00Z"), 7.days)
        assertEquals(dayStart("2025-03-08T15:00:00Z"), resolveEpisodeAirTime(PackedDate(2025, 3, 9), sut))
    }

    @Test
    fun `day after a slot is not matched to that slot`() {
        // 旧期望 (±1 天容差): startTime. 统一算法没有 "前一个周期时刻" 分支, 不会返回早于上映日 00:00 的时刻
        val sut = SubjectRecurrence(Instant.parse("2024-06-01T00:00:00Z"), 14.days)
        assertEquals(dayStart("2024-06-01T15:00:00Z"), resolveEpisodeAirTime(PackedDate(2024, 6, 2), sut))
    }

    @Test
    fun `date far from any slot falls back instead of returning null`() {
        // 旧期望: null (剧集会永远显示为未开播). 现在以 Bangumi 的日期为准, 只是没有精确时刻
        val sut = SubjectRecurrence(Instant.parse("2025-02-01T00:00:00Z"), 7.days)
        assertEquals(dayStart("2025-02-02T15:00:00Z"), resolveEpisodeAirTime(PackedDate(2025, 2, 3), sut))
    }

    @Test
    fun `zero or negative interval falls back instead of returning null`() {
        // bangumi-data 的 P0D (一次性放送) 存为 interval = 0. 旧期望: null
        val start = Instant.parse("2025-05-01T00:00:00Z")
        val anyDate = PackedDate(2025, 5, 1)
        assertEquals(
            dayStart("2025-04-30T15:00:00Z"),
            resolveEpisodeAirTime(anyDate, SubjectRecurrence(start, Duration.ZERO)),
            "zero interval",
        )
        assertEquals(
            dayStart("2025-04-30T15:00:00Z"),
            resolveEpisodeAirTime(anyDate, SubjectRecurrence(start, (-7).days)),
            "negative interval",
        )
    }
}
