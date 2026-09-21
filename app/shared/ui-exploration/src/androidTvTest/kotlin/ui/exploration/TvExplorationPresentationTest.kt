/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */
package me.him188.ani.leanback.ui.exploration

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TvExplorationPresentationTest {
    @Test
    fun carouselWrapsBothWaysAndKeepsIdentityAfterReorder() {
        assertEquals(30, nextFeaturedSubjectId(listOf(10, 20, 30), 10, -1))
        assertEquals(10, nextFeaturedSubjectId(listOf(10, 20, 30), 30, 1))
        assertEquals(10, nextFeaturedSubjectId(listOf(20, 10, 30), 20, 1))
        assertEquals(10, nextFeaturedSubjectId(listOf(10), 10, 1))
        assertNull(nextFeaturedSubjectId(emptyList(), 10, 1))
    }
}
