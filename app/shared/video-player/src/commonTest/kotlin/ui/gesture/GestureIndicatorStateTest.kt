/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.videoplayer.ui.gesture

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GestureIndicatorStateTest {
    @Test
    fun `seek cancellation remains visible until stopped`() {
        val state = GestureIndicatorState()

        val ticket = state.startSeekCancellation()
        assertTrue(state.visible)
        assertTrue(state.seekCancelled)

        state.stopSeekCancellation(ticket)
        assertFalse(state.visible)
    }

    @Test
    fun `showing regular seek clears cancellation state`() = runTest {
        val state = GestureIndicatorState()
        state.startSeekCancellation()

        state.showSeeking(deltaSeconds = 10)

        assertFalse(state.seekCancelled)
    }
}
