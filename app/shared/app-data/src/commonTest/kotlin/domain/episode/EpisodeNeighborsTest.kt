/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.episode

import me.him188.ani.datasources.api.EpisodeType
import me.him188.ani.datasources.api.EpisodeType.MainStory
import me.him188.ani.datasources.api.EpisodeType.SP
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class EpisodeNeighborsTest {
    private data class Ep(val id: Int, val type: EpisodeType?)

    private fun List<Ep>.neighbor(currentEpisodeId: Int, offset: Int): Int? =
        EpisodeCollections.findNeighborEpisode(this, currentEpisodeId, offset, { it.id }, { it.type })?.id

    // 正片 1, SP 1, 正片 2, SP 2, 正片 3: 类型之间按序号混排
    private val interleaved = listOf(
        Ep(1, MainStory), Ep(101, SP), Ep(2, MainStory), Ep(102, SP), Ep(3, MainStory),
    )

    @Test
    fun `next of main episode skips SP`() {
        assertEquals(2, interleaved.neighbor(1, 1))
        assertEquals(3, interleaved.neighbor(2, 1))
    }

    @Test
    fun `next of SP is the next SP`() {
        assertEquals(102, interleaved.neighbor(101, 1))
    }

    @Test
    fun `previous stays in the same type`() {
        assertEquals(1, interleaved.neighbor(2, -1))
        assertEquals(101, interleaved.neighbor(102, -1))
    }

    @Test
    fun `no neighbor across types at the boundaries`() {
        val grouped = listOf(Ep(1, MainStory), Ep(2, MainStory), Ep(101, SP), Ep(102, SP))
        assertNull(grouped.neighbor(2, 1))
        assertNull(grouped.neighbor(101, -1))
        assertNull(grouped.neighbor(1, -1))
        assertNull(grouped.neighbor(102, 1))
    }

    @Test
    fun `larger offset counts only episodes of the same type`() {
        assertEquals(3, interleaved.neighbor(1, 2))
        assertEquals(1, interleaved.neighbor(3, -2))
        assertNull(interleaved.neighbor(101, 2))
        assertEquals(101, interleaved.neighbor(101, 0))
    }

    @Test
    fun `unknown current episode has no neighbor`() {
        assertNull(interleaved.neighbor(999, 1))
    }

    @Test
    fun `episodes without type are neighbors of each other`() {
        val list = listOf(Ep(1, MainStory), Ep(201, null), Ep(2, MainStory), Ep(202, null))
        assertEquals(202, list.neighbor(201, 1))
        assertEquals(2, list.neighbor(1, 1))
    }
}
