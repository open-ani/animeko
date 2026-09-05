/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.platform.window

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WindowsWindowCompositionModeTest {
    @Test
    fun `Windows 10 custom title bar keeps transparent Skia layer`() {
        val mode = windowsWindowCompositionMode(
            windowsBuildNumber = WINDOWS_11_MIN_BUILD_NUMBER - 1,
            fullscreen = false,
        )

        assertEquals(-1, mode.frameMargin)
        assertTrue(mode.skiaLayerTransparency)
    }

    @Test
    fun `borderless fullscreen resets glass frame and is opaque on Windows 10`() {
        val mode = windowsWindowCompositionMode(
            windowsBuildNumber = WINDOWS_11_MIN_BUILD_NUMBER - 1,
            fullscreen = true,
        )

        assertEquals(0, mode.frameMargin)
        assertFalse(mode.skiaLayerTransparency)
    }

    @Test
    fun `Windows 11 Skia layer remains opaque outside fullscreen`() {
        val mode = windowsWindowCompositionMode(
            windowsBuildNumber = WINDOWS_11_MIN_BUILD_NUMBER,
            fullscreen = false,
        )

        assertEquals(-1, mode.frameMargin)
        assertFalse(mode.skiaLayerTransparency)
    }

    @Test
    fun `unknown Windows build uses opaque fallback`() {
        assertFalse(
            windowsWindowCompositionMode(
                windowsBuildNumber = null,
                fullscreen = false,
            ).skiaLayerTransparency,
        )
    }
}
