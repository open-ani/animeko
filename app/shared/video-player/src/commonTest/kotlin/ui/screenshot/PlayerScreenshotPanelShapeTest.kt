/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.videoplayer.ui.screenshot

import androidx.compose.ui.geometry.Rect
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class PlayerScreenshotPanelShapeTest {
    private fun assertRect(expected: Rect, actual: Rect, tolerance: Float = 0.5f) {
        assertEquals(expected.left, actual.left, tolerance, "left")
        assertEquals(expected.top, actual.top, tolerance, "top")
        assertEquals(expected.right, actual.right, tolerance, "right")
        assertEquals(expected.bottom, actual.bottom, tolerance, "bottom")
    }

    @Test
    fun `lobe extending right spans both regions`() {
        val a = Rect(0f, 0f, 215f, 104f)
        val b = Rect(0f, 104f, 263f, 160f) // 挂在 A 下方, 左边对齐, 向右伸出
        val path = screenshotPanelOutline(a, b, cornerRadius = 20f, neckRadius = 12f)
        assertFalse(path.isEmpty)
        assertRect(Rect(0f, 0f, 263f, 160f), path.getBounds())
    }

    @Test
    fun `lobe extending left is the mirror image`() {
        val a = Rect(48f, 0f, 263f, 104f)
        val b = Rect(0f, 104f, 263f, 160f) // 右边对齐, 向左伸出
        val path = screenshotPanelOutline(a, b, cornerRadius = 20f, neckRadius = 12f)
        assertFalse(path.isEmpty)
        assertRect(Rect(0f, 0f, 263f, 160f), path.getBounds())
    }

    @Test
    fun `tiny panels clamp the radii instead of folding`() {
        val a = Rect(0f, 0f, 30f, 30f)
        val b = Rect(0f, 30f, 50f, 50f)
        val path = screenshotPanelOutline(a, b, cornerRadius = 40f, neckRadius = 40f)
        assertFalse(path.isEmpty)
        assertRect(Rect(0f, 0f, 50f, 50f), path.getBounds())
    }
}
