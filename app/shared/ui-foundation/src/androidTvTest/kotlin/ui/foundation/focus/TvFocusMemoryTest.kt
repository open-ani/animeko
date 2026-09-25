/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 * Use of this source code is governed by the GNU AGPLv3 license.
 */
package me.him188.ani.tv.ui.foundation.focus

import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame

class TvFocusMemoryTest {
    @Test
    fun `registration can be remembered on activation without replacing the current content`() {
        val memory = TvFocusMemory()
        val content = TvFocusTarget(null)
        memory.register("content", content)
        memory.remember("content")
        val settings = TvFocusTarget(null)
        memory.register("settings", settings)
        assertSame(content, assertNotNull(memory.restoration())())
        memory.remember("settings")
        assertSame(settings, assertNotNull(memory.restoration())())
    }

    @Test
    fun `saved identity resolves a recreated node without retaining its old requester`() {
        val memory = TvFocusMemory()
        val old = TvFocusTarget(null)
        memory.register("card", old)
        memory.reportFocused("card", old)
        val restore = assertNotNull(memory.restoration())
        memory.unregister("card", old)
        assertNull(restore())
        val replacement = TvFocusTarget(null)
        memory.register("card", replacement)
        assertSame(replacement, restore())
        memory.unregister("card", old)
        assertSame(replacement, restore())
    }

    @Test
    fun `fallback focus does not change an already captured restoration identity`() {
        val memory = TvFocusMemory()
        val target = TvFocusTarget(null)
        memory.register("saved", target)
        memory.reportFocused("saved", target)
        val restore = assertNotNull(memory.restoration())
        memory.reportFocused("fallback", TvFocusTarget(null))
        assertSame(target, restore())
    }

    @Test
    fun `unnamed focus supports same-node restoration and clearing memory`() {
        val memory = TvFocusMemory()
        val target = TvFocusTarget(null).apply { attach(Any()) }
        memory.reportFocused(null, target)
        assertSame(target, assertNotNull(memory.restoration())())
        memory.clear()
        assertNull(memory.lastId)
        assertNull(memory.restoration())
    }
}
