/*
 * Copyright (C) 2026 OpenAni and contributors.
 * Use of this source code is governed by the GNU AGPLv3 license.
 */
package me.him188.ani.tv.ui.foundation.focus

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TvFocusTargetTest {
    @Test
    fun `removing an old node preserves its placed replacement`() {
        val target = TvFocusTarget(null)
        val old = Any()
        val replacement = Any()
        target.attach(old)
        target.place(old, true)
        target.attach(replacement)
        target.place(replacement, true)
        target.detach(old)
        assertTrue(target.ready)
        target.detach(replacement)
        assertFalse(target.attached)
        assertFalse(target.ready)
    }

    @Test
    fun `a replacement needs its own placement before accepting focus`() {
        val target = TvFocusTarget(null)
        val old = Any()
        val replacement = Any()
        target.attach(old)
        target.place(old, true)
        target.attach(replacement)
        target.detach(old)
        assertTrue(target.attached)
        assertFalse(target.ready)
        target.place(replacement, true)
        assertTrue(target.ready)
    }
}
