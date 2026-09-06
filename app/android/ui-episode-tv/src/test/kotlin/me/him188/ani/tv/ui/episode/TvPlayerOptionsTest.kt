/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.tv.ui.episode

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TvPlayerOptionsTest {
    private val sources = listOf("one", "two", "three").map {
        TvSourceGroup(it, it, it, null, "完成", false, emptyList())
    }

    @Test
    fun `source navigation follows mode then source hierarchy`() {
        var state = TvSourceSelectionState(groups = sources)
        state = state.moveHorizontally(-1)
        assertEquals(TvSourceMode.Simple, state.mode)
        state = state.moveHorizontally(1)
        assertEquals(TvSourceMode.Detailed, state.mode)
        assertEquals("one", state.selectedGroup?.instanceId)
        state = state.moveHorizontally(1)
        assertEquals("two", state.selectedGroup?.instanceId)
        state = state.moveHorizontally(1).moveHorizontally(1)
        assertEquals("three", state.selectedGroup?.instanceId)
        state = state.moveHorizontally(-1).moveHorizontally(-1).moveHorizontally(-1)
        assertEquals(TvSourceMode.Simple, state.mode)
    }

    @Test
    fun `empty source list still supports both modes`() {
        val state = TvSourceSelectionState().moveHorizontally(1)
        assertEquals(TvSourceMode.Detailed, state.mode)
        assertEquals(TvSourceMode.Simple, state.moveHorizontally(-1).mode)
    }

    @Test
    fun `cancelled automatic skip never seeks and resets with media`() {
        val controller = TvAutoSkipController()
        val chapters = listOf(TvChapter("OP", 60_000, 85_000))
        val seeks = mutableListOf<Long>()
        assertEquals(TvSkipPrompt("OP", 5), controller.update(55_000, 1_400_000, chapters, true, seeks::add))
        controller.cancel()
        assertNull(controller.update(60_000, 1_400_000, chapters, true, seeks::add))
        assertTrue(seeks.isEmpty())
        controller.reset()
        controller.update(55_000, 1_400_000, chapters, true, seeks::add)
        controller.update(60_000, 1_400_000, chapters, true, seeks::add)
        assertEquals(listOf(145_000L), seeks)
    }

    @Test
    fun `disabled skipping and non opening chapters do not seek`() {
        val controller = TvAutoSkipController()
        val seeks = mutableListOf<Long>()
        assertNull(controller.update(60_000, 1_400_000, listOf(TvChapter("OP", 60_000, 85_000)), false, seeks::add))
        assertNull(controller.update(60_000, 1_400_000, listOf(TvChapter("Part", 60_000, 300_000)), true, seeks::add))
        assertTrue(seeks.isEmpty())
    }

    @Test
    fun `late chapter rules still show cancellable countdown`() {
        val controller = TvAutoSkipController()
        val chapters = listOf(TvChapter("OP", 60_000, 85_000))
        val seeks = mutableListOf<Long>()
        assertEquals(TvSkipPrompt("OP", 5), controller.update(60_000, 1_400_000, chapters, true, seeks::add))
        assertEquals(TvSkipPrompt("OP", 2), controller.update(63_000, 1_400_000, chapters, true, seeks::add))
        assertTrue(seeks.isEmpty())
        controller.cancel()
        assertNull(controller.update(65_000, 1_400_000, chapters, true, seeks::add))
        assertTrue(seeks.isEmpty())
    }

    @Test
    fun `short episode uses shorter chapter length and skips once`() {
        val controller = TvAutoSkipController()
        val chapters = listOf(TvChapter("OP", 60_000, 60_000))
        val seeks = mutableListOf<Long>()
        controller.update(55_000, 800_000, chapters, true, seeks::add)
        controller.update(60_000, 800_000, chapters, true, seeks::add)
        controller.update(60_000, 800_000, chapters, true, seeks::add)
        assertEquals(listOf(120_000L), seeks)
    }
}
