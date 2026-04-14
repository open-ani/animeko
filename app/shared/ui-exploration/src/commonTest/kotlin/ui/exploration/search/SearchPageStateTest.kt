/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.exploration.search

import me.him188.ani.app.domain.search.SubjectSearchQuery
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SearchPageStateTest {
    @Test
    fun `withQuery trims whitespace and keeps filter state in sync`() {
        val state = createTestSearchPageState(
            query = SubjectSearchQuery("   ", tags = listOf("百合")),
            hasActiveSearch = false,
        )

        val updated = state.withQuery(state.query.copy(keywords = "  bocchi  "))

        assertEquals("bocchi", updated.query.keywords)
        assertEquals(listOf("百合"), updated.query.tags)
        assertTrue(updated.searchFilterState.chips.any { "百合" in it.selected })
    }

    @Test
    fun `toggleTagSelection replaces same kind when requested`() {
        val state = createTestSearchPageState(
            query = SubjectSearchQuery("", tags = listOf("校园")),
            hasActiveSearch = true,
        )
        val chip = state.searchFilterState.chips.first { "恋爱" in it.values }

        val updated = state.toggleTagSelection(
            tag = chip,
            value = "恋爱",
            unselectOthersOfSameKind = true,
        )

        assertEquals(listOf("恋爱"), updated.query.tags)
    }

    @Test
    fun `toggleTagSelection appends custom tags without clearing others`() {
        val state = createTestSearchPageState(
            query = SubjectSearchQuery("", tags = listOf("百合")),
            hasActiveSearch = true,
        )
        val chip =
            state.searchFilterState.chips.first { it.values.any { value -> value !in state.query.tags.orEmpty() } }
        val value = chip.values.first { it !in state.query.tags.orEmpty() }

        val updated = state.toggleTagSelection(
            tag = chip,
            value = value,
            unselectOthersOfSameKind = false,
        )

        assertEquals(listOf("百合", value), updated.query.tags)
    }
}
