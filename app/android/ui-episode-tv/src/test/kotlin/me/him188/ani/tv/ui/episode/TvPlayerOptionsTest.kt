/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.tv.ui.episode

import androidx.compose.runtime.saveable.SaverScope
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
        val state = TvSourceDialogState()
        state.moveHorizontally(-1, sources)
        assertEquals(TvSourceMode.Simple, state.mode)
        state.moveHorizontally(1, sources)
        assertEquals(TvSourceMode.Detailed, state.mode)
        assertEquals("one", state.selectedGroup(sources)?.instanceId)
        state.moveHorizontally(1, sources)
        assertEquals("two", state.selectedGroup(sources)?.instanceId)
        repeat(2) { state.moveHorizontally(1, sources) }
        assertEquals("three", state.selectedGroup(sources)?.instanceId)
        repeat(3) { state.moveHorizontally(-1, sources) }
        assertEquals(TvSourceMode.Simple, state.mode)
    }

    @Test
    fun `empty source list still supports both modes`() {
        val state = TvSourceDialogState()
        state.moveHorizontally(1, emptyList())
        assertEquals(TvSourceMode.Detailed, state.mode)
        state.moveHorizontally(-1, emptyList())
        assertEquals(TvSourceMode.Simple, state.mode)
    }

    @Test
    fun `source browsing survives result refresh and reordering`() {
        val state = TvSourceDialogState(TvSourceMode.Detailed, "two", showExcluded = true)
        assertNull(state.selectedGroup(emptyList()))
        assertEquals("two", state.selectedGroup(sources.reversed())?.instanceId)
        assertEquals("one", state.selectedGroup(sources.take(1))?.instanceId)
        assertEquals(TvSourceMode.Detailed, state.mode)
        assertTrue(state.showExcluded)
    }

    @Test
    fun `source browsing choices are saveable without results`() {
        val state = TvSourceDialogState(TvSourceMode.Detailed, "two", showExcluded = true)
        val scope = object : SaverScope {
            override fun canBeSaved(value: Any): Boolean = true
        }
        val saved = with(TvSourceDialogState.Saver) { scope.save(state) }
        val restored = requireNotNull(TvSourceDialogState.Saver.restore(requireNotNull(saved)))
        assertEquals(TvSourceMode.Detailed, restored.mode)
        assertEquals("two", restored.selectedGroup(sources)?.instanceId)
        assertTrue(restored.showExcluded)
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
