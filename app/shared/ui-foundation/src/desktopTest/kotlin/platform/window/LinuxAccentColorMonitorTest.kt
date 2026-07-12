/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.platform.window

import androidx.compose.ui.graphics.Color
import org.freedesktop.dbus.types.Variant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LinuxAccentColorMonitorTest {
    @Test
    fun `converts portal RGB tuple to Compose color`() {
        assertEquals(
            Color(0.25f, 0.5f, 0.75f),
            Variant(PortalAccentColor(0.25, 0.5, 0.75)).toComposeColor(),
        )
    }

    @Test
    fun `unwraps deprecated Read nested variant`() {
        assertEquals(
            Color(0.25f, 0.5f, 0.75f),
            Variant(Variant(PortalAccentColor(0.25, 0.5, 0.75))).toComposeColor(),
        )
    }

    @Test
    fun `converts dynamically decoded portal tuple`() {
        assertEquals(
            Color(0.25f, 0.5f, 0.75f),
            Variant(arrayOf(0.25, 0.5, 0.75), "(ddd)").toComposeColor(),
        )
    }

    @Test
    fun `rejects out of range portal color`() {
        assertNull(Variant(PortalAccentColor(1.1, 0.5, 0.75)).toComposeColor())
    }
}
