/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.watchtogether

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import kotlin.test.Test
import kotlin.test.assertEquals

class DraggableBubblePositionStateTest {
    private val oldContainer = IntSize(1_000, 800)
    private val newContainer = IntSize(1_200, 1_000)
    private val bubbleSize = IntSize(100, 50)
    private val margin = 16f

    @Test
    fun `initial placement starts at the right without an origin sentinel`() {
        assertEquals(
            Offset(1_084f, 680f),
            calculateBubbleTarget(
                previous = null,
                containerSize = newContainer,
                bubbleSize = bubbleSize,
                marginPx = margin,
            ),
        )
    }

    @Test
    fun `top left placement remains unchanged after resize`() {
        assertResize(
            oldOffset = Offset(16f, 100f),
            expected = Offset(16f, 100f),
        )
    }

    @Test
    fun `top right placement realigns to the right after resize`() {
        assertResize(
            oldOffset = Offset(884f, 100f),
            expected = Offset(1_084f, 100f),
        )
    }

    @Test
    fun `bottom left placement preserves its bottom gap after resize`() {
        assertResize(
            oldOffset = Offset(16f, 650f),
            expected = Offset(16f, 850f),
        )
    }

    @Test
    fun `bottom right placement preserves its bottom gap and realigns right`() {
        assertResize(
            oldOffset = Offset(884f, 650f),
            expected = Offset(1_084f, 850f),
        )
    }

    @Test
    fun `shrinking the container clamps the placement inside its bounds`() {
        val result = calculateBubbleTarget(
            previous = placement(Offset(884f, 734f)),
            containerSize = IntSize(300, 200),
            bubbleSize = bubbleSize,
            marginPx = margin,
        )

        assertEquals(Offset(184f, 134f), result)
    }

    private fun assertResize(oldOffset: Offset, expected: Offset) {
        assertEquals(
            expected,
            calculateBubbleTarget(
                previous = placement(oldOffset),
                containerSize = newContainer,
                bubbleSize = bubbleSize,
                marginPx = margin,
            ),
        )
    }

    private fun placement(offset: Offset) = SettledBubblePlacement(
        offset = offset,
        containerSize = oldContainer,
        bubbleSize = bubbleSize,
    )
}
