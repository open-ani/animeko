/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.search

import me.him188.ani.app.data.models.schedule.AnimeSeason
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class SubjectSearchQueryTest {
    @Test
    fun `season cannot be set without year`() {
        assertFailsWith<IllegalArgumentException> {
            SubjectSearchQuery("", season = AnimeSeason.SPRING)
        }
    }

    @Test
    fun `clearing year also clears dependent season`() {
        val query = SubjectSearchQuery("", year = 2024, season = AnimeSeason.SPRING)

        val cleared = query.withYearFilter(null)

        assertNull(cleared.year)
        assertNull(cleared.season)
    }

    @Test
    fun `switching to another year keeps the selected season`() {
        val query = SubjectSearchQuery("", year = 2024, season = AnimeSeason.SPRING)

        val switched = query.withYearFilter(2025)

        assertEquals(2025, switched.year)
        assertEquals(AnimeSeason.SPRING, switched.season)
    }
}
