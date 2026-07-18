/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.videoplayer.ui

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class PlaybackSpeedControlsTest {
    private val range = 0.5f..2.5f

    @Test
    fun `keyboard adjustment adds or subtracts one step`() {
        assertEquals(1.5f, nextPlaybackSpeed(1.3f, range, 1))
        assertEquals(1f, nextPlaybackSpeed(1.3f, range, -1))
    }

    @Test
    fun `preview speed immediately updates the shared UI state`() = runTest {
        val state = PlaybackSpeedControllerState(
            NoOpPlaybackSpeedController,
            scope = backgroundScope,
        )

        state.previewSpeed(1.75f)

        assertEquals(1.75f, state.currentSpeed)
    }
}
