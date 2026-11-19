/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.foundation.input

import androidx.compose.ui.input.key.Key
import me.him188.ani.app.data.models.preference.PlayerShortcut
import me.him188.ani.app.data.models.preference.PlayerShortcutConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlayerShortcutKeysTest {
    @Test
    fun `ids and keys are unique and map both ways`() {
        val all = PlayerShortcutKeys.all
        assertEquals(all.size, all.map { it.id }.toSet().size)
        assertEquals(all.size, all.map { it.key }.toSet().size)
        for (info in all) {
            assertEquals(info, PlayerShortcutKeys.findById(info.id))
            assertEquals(info, PlayerShortcutKeys.findByKey(info.key))
        }
    }

    @Test
    fun `every default binding uses a supported key`() {
        for ((action, shortcuts) in PlayerShortcutConfig.DEFAULT_BINDINGS) {
            for (shortcut in shortcuts) {
                assertNotNull(shortcut.composeKey, "$action binds unknown key ${shortcut.key}")
            }
        }
    }

    @Test
    fun `reserved keys are not bindable`() {
        for (key in listOf(Key.Enter, Key.NumPadEnter, Key.Escape, Key.Tab, Key.DirectionCenter, Key.ShiftLeft, Key.CtrlLeft)) {
            assertNull(PlayerShortcutKeys.findByKey(key), "$key must not be bindable")
        }
        assertTrue(PlayerShortcutKeys.isModifierKey(Key.ShiftRight))
        assertFalse(PlayerShortcutKeys.isModifierKey(Key.A))
    }

    @Test
    fun `matches requires exact modifiers unless shift is ignored`() {
        val up = PlayerShortcut("DirectionUp")
        assertTrue(up.matches(Key.DirectionUp, ctrl = false, shift = false, alt = false, meta = false))
        assertFalse(up.matches(Key.DirectionUp, ctrl = false, shift = true, alt = false, meta = false))
        assertTrue(up.matches(Key.DirectionUp, ctrl = false, shift = true, alt = false, meta = false, ignoreShift = true))
        assertFalse(up.matches(Key.DirectionUp, ctrl = true, shift = false, alt = false, meta = false, ignoreShift = true))
        assertFalse(up.matches(Key.DirectionDown, ctrl = false, shift = false, alt = false, meta = false))

        val ctrlShiftF = PlayerShortcut("F", ctrl = true, shift = true)
        assertTrue(ctrlShiftF.matches(Key.F, ctrl = true, shift = true, alt = false, meta = false))
        assertFalse(ctrlShiftF.matches(Key.F, ctrl = true, shift = false, alt = false, meta = false))
    }

    @Test
    fun `unknown key id never matches`() {
        assertFalse(PlayerShortcut("NoSuchKey").matches(Key.A, ctrl = false, shift = false, alt = false, meta = false))
        assertNull(PlayerShortcut("NoSuchKey").composeKey)
    }

    @Test
    fun `display text uses labels and platform modifier style`() {
        assertEquals("Space", PlayerShortcut("Spacebar").displayText(isMacOs = false))
        assertEquals("Ctrl + Alt + Shift + Meta + F", PlayerShortcut("F", ctrl = true, shift = true, alt = true, meta = true).displayText(isMacOs = false))
        assertEquals("⌃⌥⇧⌘F", PlayerShortcut("F", ctrl = true, shift = true, alt = true, meta = true).displayText(isMacOs = true))
        assertEquals("Num 1", PlayerShortcut("NumPad1").displayText(isMacOs = false))
        assertEquals("←", PlayerShortcut("DirectionLeft").displayText(isMacOs = true))
        // unknown ids fall back to the raw id so that nothing is silently hidden
        assertEquals("Shift + NoSuchKey", PlayerShortcut("NoSuchKey", shift = true).displayText(isMacOs = false))
    }
}
