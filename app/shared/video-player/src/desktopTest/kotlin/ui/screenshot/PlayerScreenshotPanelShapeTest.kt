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
import kotlin.test.assertFalse

class PlayerScreenshotPanelShapeTest {
    @Test
    fun `lobe extending right spans both regions`() {
        val a = Rect(0f, 0f, 215f, 104f)
        val b = Rect(187f, 48f, 319f, 104f) // 底边与 A 对齐, 一端藏在 A 里, 向右伸出
        val path = screenshotPanelOutline(a, b, cornerRadius = 20f, neckRadius = 12f, lobeExtendsRight = true)
        assertFalse(path.isEmpty)
        assertRect(Rect(0f, 0f, 319f, 104f), path.getBounds())
    }

    @Test
    fun `lobe extending left is the mirror image`() {
        val a = Rect(104f, 0f, 319f, 104f)
        val b = Rect(0f, 48f, 132f, 104f) // 向左伸出
        val path = screenshotPanelOutline(a, b, cornerRadius = 20f, neckRadius = 12f, lobeExtendsRight = false)
        assertFalse(path.isEmpty)
        assertRect(Rect(0f, 0f, 319f, 104f), path.getBounds())
    }

    @Test
    fun `tiny panels clamp the radii instead of folding`() {
        val a = Rect(0f, 0f, 30f, 30f)
        val b = Rect(20f, 10f, 60f, 30f)
        val path = screenshotPanelOutline(a, b, cornerRadius = 40f, neckRadius = 40f, lobeExtendsRight = true)
        assertFalse(path.isEmpty)
        assertRect(Rect(0f, 0f, 60f, 30f), path.getBounds())
    }
}
