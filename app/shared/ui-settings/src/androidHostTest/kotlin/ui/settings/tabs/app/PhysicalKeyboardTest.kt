/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.settings.tabs.app

import android.view.InputDevice
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PhysicalKeyboardTest {
    @Test
    fun `alphabetic hardware keyboard counts`() {
        assertTrue(
            isPhysicalKeyboard(
                sources = InputDevice.SOURCE_KEYBOARD,
                keyboardType = InputDevice.KEYBOARD_TYPE_ALPHABETIC,
                isVirtual = false,
            ),
        )
        // a keyboard that also reports other sources, e.g. a keyboard with a touchpad
        assertTrue(
            isPhysicalKeyboard(
                sources = InputDevice.SOURCE_KEYBOARD or InputDevice.SOURCE_TOUCHPAD,
                keyboardType = InputDevice.KEYBOARD_TYPE_ALPHABETIC,
                isVirtual = false,
            ),
        )
    }

    @Test
    fun `virtual keyboards remotes and gamepads do not count`() {
        assertFalse(
            isPhysicalKeyboard(
                sources = InputDevice.SOURCE_KEYBOARD,
                keyboardType = InputDevice.KEYBOARD_TYPE_ALPHABETIC,
                isVirtual = true,
            ),
        )
        assertFalse(
            isPhysicalKeyboard(
                sources = InputDevice.SOURCE_KEYBOARD or InputDevice.SOURCE_DPAD,
                keyboardType = InputDevice.KEYBOARD_TYPE_NON_ALPHABETIC,
                isVirtual = false,
            ),
        )
        assertFalse(
            isPhysicalKeyboard(
                sources = InputDevice.SOURCE_GAMEPAD,
                keyboardType = InputDevice.KEYBOARD_TYPE_ALPHABETIC,
                isVirtual = false,
            ),
        )
    }
}
