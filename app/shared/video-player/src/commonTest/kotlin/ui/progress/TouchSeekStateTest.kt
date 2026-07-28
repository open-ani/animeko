/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.videoplayer.ui.progress

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import me.him188.ani.app.videoplayer.ui.gesture.SwipeSeekerConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TouchSeekStateTest {
    private fun create(
        threshold: Float = 144f,
        onChanged: (TouchSeekState.State) -> Unit = {},
    ) = TouchSeekState(
        swipeSeekerConfig = SwipeSeekerConfig(cancelVerticalDragDistance = threshold.dp),
        density = Density(1f),
        onStateChanged = onChanged,
    )

    @Test
    fun `crossing the threshold cancels and moving back resumes`() {
        val changes = mutableListOf<TouchSeekState.State>()
        val state = create(onChanged = changes::add)
        val startY = 300f
        val threshold = 144f

        state.onPointerDown(Offset(100f, startY))
        state.start()

        assertFalse(state.move(Offset(200f, startY - threshold)))
        assertTrue(state.move(Offset(200f, startY - threshold - 1f)))
        assertFalse(state.move(Offset(300f, startY - threshold - 2f)))
        assertTrue(state.move(Offset(300f, startY - threshold + 1f)))

        assertEquals(
            listOf(
                TouchSeekState.State.Seeking,
                TouchSeekState.State.Cancelling,
                TouchSeekState.State.Seeking,
            ),
            changes,
        )
    }

    @Test
    fun `stop returns cancellation and resets the drag origin`() {
        val state = create()

        state.onPointerDown(Offset(100f, 300f))
        state.start()
        state.move(Offset(200f, 155f))
        assertTrue(state.stop())

        state.start()
        assertFalse(state.move(Offset(200f, 0f)))
        assertFalse(state.stop())
    }

    @Test
    fun `starting without a press cannot cancel`() {
        val state = create()

        state.start()
        assertFalse(state.move(Offset(200f, -1000f)))
        assertEquals(TouchSeekState.State.Seeking, state.state)
        assertFalse(state.stop())
    }
}
