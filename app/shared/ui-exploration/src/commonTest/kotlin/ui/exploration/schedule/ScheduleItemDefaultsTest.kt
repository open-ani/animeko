/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.exploration.schedule

import me.him188.ani.app.ui.exploration.schedule.ScheduleItemDefaults.renderEpisodeDisplay
import me.him188.ani.datasources.api.EpisodeSort
import me.him188.ani.datasources.api.EpisodeType
import kotlin.test.Test
import kotlin.test.assertEquals

class ScheduleItemDefaultsTest {
    @Test
    fun `renderEpisodeDisplay sort only`() {
        assertEquals("第 1 话", renderEpisodeDisplay(EpisodeSort(1), null, null))
    }

    @Test
    fun `renderEpisodeDisplay sort with name`() {
        assertEquals("第 1 话  Foo", renderEpisodeDisplay(EpisodeSort(1), null, "Foo"))
    }

    @Test
    fun `renderEpisodeDisplay sort equal ep`() {
        assertEquals("第 1 话", renderEpisodeDisplay(EpisodeSort(1), EpisodeSort(1), null))
    }

    @Test
    fun `renderEpisodeDisplay sort equal ep with name`() {
        assertEquals("第 1 话  Foo", renderEpisodeDisplay(EpisodeSort(1), EpisodeSort(1), "Foo"))
    }

    @Test
    fun `renderEpisodeDisplay sort does not equal ep`() {
        assertEquals("第 1 (12) 话", renderEpisodeDisplay(EpisodeSort(12), EpisodeSort(1), null))
    }

    @Test
    fun `renderEpisodeDisplay sort does not equal ep with name`() {
        assertEquals("第 1 (12) 话  Foo", renderEpisodeDisplay(EpisodeSort(12), EpisodeSort(1), "Foo"))
    }

    @Test
    fun `renderEpisodeDisplay special sort`() {
        assertEquals(
            "OVA01  Foo",
            renderEpisodeDisplay(
                EpisodeSort(1, EpisodeType.OVA),
                EpisodeSort(1, EpisodeType.OVA),
                "Foo",
            ),
        )
    }

    @Test
    fun `renderEpisodeDisplay unknown sort`() {
        assertEquals(
            "剧场版  Foo",
            renderEpisodeDisplay(
                EpisodeSort("剧场版"),
                EpisodeSort("剧场版"),
                "Foo",
            ),
        )
    }
}
