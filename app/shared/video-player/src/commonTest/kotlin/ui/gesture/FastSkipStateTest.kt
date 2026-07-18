/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.videoplayer.ui.gesture

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.openani.mediamp.InternalForInheritanceMediampApi
import org.openani.mediamp.features.PlaybackSpeed
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(InternalForInheritanceMediampApi::class)
private class TestPlaybackSpeed(initial: Float) : PlaybackSpeed {
    private val _flow = MutableStateFlow(initial)
    override val valueFlow = _flow.asStateFlow()
    override val value: Float
        get() = _flow.value

    override fun set(speed: Float) {
        _flow.value = speed
    }
}

/**
 * Unit tests for [PlayerFastSkipState] / [FastSkipState].
 */
class FastSkipStateTest {
    private fun createState(
        speed: TestPlaybackSpeed,
        fastForwardSpeed: Float = 2.5f,
    ): FastSkipState = PlayerFastSkipState(
        playbackSpeed = speed,
        gestureIndicatorState = GestureIndicatorState(),
        fastForwardSpeed = fastForwardSpeed,
    ).fastSkipState

    @Test
    fun `start switches to absolute fast forward speed`() {
        val speed = TestPlaybackSpeed(initial = 1.3f)
        val state = createState(speed, fastForwardSpeed = 2.5f)

        state.startSkipping(SkipDirection.FORWARD)
        assertEquals(2.5f, speed.value)
    }

    @Test
    fun `stop restores global speed`() {
        val speed = TestPlaybackSpeed(initial = 1.3f)
        val state = createState(speed)

        val ticket = state.startSkipping(SkipDirection.FORWARD)
        state.stopSkipping(ticket)
        assertEquals(1.3f, speed.value)
    }

}
