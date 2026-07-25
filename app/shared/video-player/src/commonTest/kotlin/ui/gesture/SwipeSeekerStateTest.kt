/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.videoplayer.ui.gesture

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SwipeSeekerStateTest {
    private val screenWidthPx = 1000

    @Test
    fun `cancellation follows the configured threshold and can be resumed`() {
        val seeks = mutableListOf<Int>()
        val threshold = 288f
        val startY = 400f
        val state = SwipeSeekerState(
            screenWidthPx = screenWidthPx,
            swipeSeekerConfig = SwipeSeekerConfig(cancelVerticalDragDistance = threshold.dp),
            density = Density(1f),
            onSeek = seeks::add,
        )

        state.onPointerDown(Offset(500f, startY))
        state.onSwipeStarted()
        state.onSwipeOffset(500f)

        state.updateCancellation(Offset(700f, startY - threshold))
        assertFalse(state.isCancelled)

        state.updateCancellation(Offset(700f, startY - threshold - 1f))
        assertTrue(state.isCancelled)

        state.updateCancellation(Offset(700f, startY - threshold + 1f))
        assertFalse(state.isCancelled)
        state.onSwipeStopped()

        assertEquals(listOf(49), seeks)
    }

    @Test
    fun `releasing beyond the cancellation threshold does not seek`() {
        val seeks = mutableListOf<Int>()
        val threshold = 144f
        val startY = 300f
        val state = SwipeSeekerState(
            screenWidthPx = screenWidthPx,
            swipeSeekerConfig = SwipeSeekerConfig(cancelVerticalDragDistance = threshold.dp),
            density = Density(1f),
            onSeek = seeks::add,
        )

        state.onPointerDown(Offset(500f, startY))
        state.onSwipeStarted()
        state.onSwipeOffset(500f)
        state.updateCancellation(Offset(700f, startY - threshold - 1f))
        state.onSwipeStopped()

        assertEquals(emptyList(), seeks)
        assertFalse(state.isSeeking)
        assertFalse(state.isCancelled)
    }
}
