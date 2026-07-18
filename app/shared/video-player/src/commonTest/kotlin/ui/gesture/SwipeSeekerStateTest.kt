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
import androidx.compose.ui.unit.IntSize
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SwipeSeekerStateTest {
    private val containerSize = IntSize(width = 1000, height = 600)

    @Test
    fun `releasing in top corner cancels seek`() {
        val seeks = mutableListOf<Int>()
        val state = SwipeSeekerState(screenWidthPx = containerSize.width, onSeek = seeks::add)

        state.onSwipeStarted()
        state.onSwipeOffset(500f)
        state.updateCancellation(Offset(100f, 100f), containerSize)

        assertTrue(state.isCancelled)
        state.onSwipeStopped()
        assertEquals(emptyList(), seeks)
        assertFalse(state.isSeeking)
        assertFalse(state.isCancelled)
    }

    @Test
    fun `pointer entering cancel area before drag recognition is retained`() {
        val seeks = mutableListOf<Int>()
        val state = SwipeSeekerState(screenWidthPx = containerSize.width, onSeek = seeks::add)

        state.updateCancellation(Offset(100f, 100f), containerSize)
        state.onSwipeStarted()
        state.onSwipeOffset(500f)

        assertTrue(state.isCancelled)
        state.onSwipeStopped()
        assertEquals(emptyList(), seeks)
    }

    @Test
    fun `moving out of top corner resumes seek`() {
        val seeks = mutableListOf<Int>()
        val state = SwipeSeekerState(screenWidthPx = containerSize.width, onSeek = seeks::add)

        state.onSwipeStarted()
        state.onSwipeOffset(500f)
        state.updateCancellation(Offset(900f, 100f), containerSize)
        assertTrue(state.isCancelled)

        state.updateCancellation(Offset(500f, 300f), containerSize)
        assertFalse(state.isCancelled)
        state.onSwipeStopped()
        assertEquals(listOf(49), seeks)
    }

    @Test
    fun `top center is outside cancel area`() {
        val state = SwipeSeekerState(screenWidthPx = containerSize.width, onSeek = {})

        state.onSwipeStarted()
        state.updateCancellation(Offset(500f, 100f), containerSize)

        assertFalse(state.isCancelled)
    }

    @Test
    fun `invalid cancel area input is outside cancel area`() {
        val config = SwipeSeekerConfig.Default

        assertFalse(config.isInCancelArea(Offset.Unspecified, containerSize))
        assertFalse(config.isInCancelArea(Offset.Zero, IntSize.Zero))
        assertFalse(config.isInCancelArea(Offset.Zero, IntSize(width = -1, height = 600)))
        assertFalse(config.isInCancelArea(Offset.Zero, IntSize(width = 1000, height = -1)))
    }
}
