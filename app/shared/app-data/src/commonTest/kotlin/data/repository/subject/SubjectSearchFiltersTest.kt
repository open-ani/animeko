/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.repository.subject

import me.him188.ani.app.data.models.schedule.AnimeSeason
import me.him188.ani.app.domain.search.SubjectSearchQuery
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * 年份/季度筛选到 Bangumi airDates 区间的换算逻辑.
 */
class SubjectSearchFiltersTest {
    @Test
    fun `no year means no air date filter`() {
        assertNull(SubjectSearchQuery("").toBangumiAirDates())
    }

    @Test
    fun `year only covers the whole natural year`() {
        assertEquals(
            listOf(">=2024-01-01", "<2025-01-01"),
            SubjectSearchQuery("", year = 2024).toBangumiAirDates(),
        )
    }

    @Test
    fun `winter season starts in previous December and ends in late February`() {
        // 2024 冬季档: 上年 12 月起播, 到 2024 年 2 月底 (开区间上界为 2024-03-01)
        assertEquals(
            listOf(">=2023-12-01", "<2024-03-01"),
            SubjectSearchQuery("", year = 2024, season = AnimeSeason.WINTER).toBangumiAirDates(),
        )
    }

    @Test
    fun `spring season upper bound is June first`() {
        assertEquals(
            listOf(">=2024-03-01", "<2024-06-01"),
            SubjectSearchQuery("", year = 2024, season = AnimeSeason.SPRING).toBangumiAirDates(),
        )
    }

    @Test
    fun `autumn season upper bound is December first`() {
        // 旧实现曾生成 "<2024-11-31" 这类不存在的日期
        assertEquals(
            listOf(">=2024-09-01", "<2024-12-01"),
            SubjectSearchQuery("", year = 2024, season = AnimeSeason.AUTUMN).toBangumiAirDates(),
        )
    }
}
