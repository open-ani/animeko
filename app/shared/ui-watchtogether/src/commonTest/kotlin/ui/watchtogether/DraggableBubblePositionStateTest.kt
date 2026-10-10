/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
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

    @Test
    fun `layout settle is not recorded while the container size differs`() {
        val state = DraggableBubblePositionState(placement(Offset(884f, 650f)))
        val shrunk = IntSize(300, 200)

        // 容器尺寸变化后 targetFor 给出的是夹到边缘的临时位置, 不应该被记住
        val target = state.targetFor(shrunk, bubbleSize, margin)!!
        state.settleIfSameContainer(target, shrunk, bubbleSize)

        assertEquals(placement(Offset(884f, 650f)), state.settledPlacement)
    }

    @Test
    fun `layout settle is recorded while the container size is unchanged`() {
        val state = DraggableBubblePositionState(placement(Offset(884f, 650f)))
        val resizedBubble = IntSize(120, 60)

        // 同一个容器里气泡自己变宽 (例如房间里显示人数) 时, 新位置要记下来
        val target = state.targetFor(oldContainer, resizedBubble, margin)!!
        state.settleIfSameContainer(target, oldContainer, resizedBubble)

        assertEquals(SettledBubblePlacement(target, oldContainer, resizedBubble), state.settledPlacement)
    }

    @Test
    fun `user drag is recorded even after the container size changed`() {
        val state = DraggableBubblePositionState(placement(Offset(884f, 650f)))
        val shrunk = IntSize(300, 200)

        state.settle(Offset(16f, 16f), shrunk, bubbleSize)

        assertEquals(SettledBubblePlacement(Offset(16f, 16f), shrunk, bubbleSize), state.settledPlacement)
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
