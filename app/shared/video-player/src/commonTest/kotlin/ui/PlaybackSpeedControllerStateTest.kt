/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.videoplayer.ui

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import me.him188.ani.app.ui.framework.takeSnapshot
import org.openani.mediamp.InternalForInheritanceMediampApi
import org.openani.mediamp.features.PlaybackSpeed
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

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
 * Unit tests for [PlaybackSpeedControllerState].
 */
class PlaybackSpeedControllerStateTest {

    @Test
    fun `requires - speedList not empty`() = runTest {
        val speed = TestPlaybackSpeed(initial = 1f)
        assertFailsWith<IllegalArgumentException> {
            PlaybackSpeedControllerState(
                playbackSpeed = speed,
                speedProvider = { emptyList() },
                scope = backgroundScope,
            )
        }
    }

    @Test
    fun `requires - speedList monotonic increasing`() = runTest {
        val speed = TestPlaybackSpeed(initial = 1f)
        // Non-monotonic: 0.5f -> 0.75f -> 0.7f
        assertFailsWith<IllegalArgumentException> {
            PlaybackSpeedControllerState(
                playbackSpeed = speed,
                speedProvider = { listOf(0.5f, 0.75f, 0.7f, 1f) },
                scope = backgroundScope,
            )
        }
    }

    @Test
    fun `requires - speedList contain 1_0f`() = runTest {
        val speed = TestPlaybackSpeed(initial = 0.5f)
        // Missing 1f
        assertFailsWith<IllegalArgumentException> {
            PlaybackSpeedControllerState(
                playbackSpeed = speed,
                speedProvider = { listOf(0.5f, 0.75f, 2f) },
                scope = backgroundScope,
            )
        }
    }

    @Test
    fun `init - reading currentSpeed from playbackSpeed`() = runTest {
        val speed = TestPlaybackSpeed(initial = 1.25f)
        val state = PlaybackSpeedControllerState(
            playbackSpeed = speed,
            speedProvider = { listOf(0.5f, 1f, 1.25f, 1.5f) },
            scope = backgroundScope,
        )
        takeSnapshot()
        // Immediately read currentSpeed
        assertEquals(1.25f, state.currentSpeed)
        assertEquals(2, state.currentIndex) // 2 => index of 1.25f
    }

    @Test
    fun `collect - external changes reflect in currentSpeed`() = runTest {
        val speed = TestPlaybackSpeed(initial = 1f)
        val state = PlaybackSpeedControllerState(
            playbackSpeed = speed,
            speedProvider = { listOf(0.5f, 1f, 1.5f) },
            scope = backgroundScope,
        )
        takeSnapshot()
        assertEquals(1f, state.currentSpeed)

        // Launch a concurrent job to change speed externally
        val job = launch {
            speed.set(1.5f)
        }
        job.join()
        takeSnapshot()

        // currentSpeed should update
        assertEquals(1.5f, state.currentSpeed)
        assertEquals(2, state.currentIndex)
    }

    @Test
    fun `speedUp - in list and not at the last index`() = runTest {
        val speed = TestPlaybackSpeed(initial = 1f)
        val state = PlaybackSpeedControllerState(
            playbackSpeed = speed,
            speedProvider = { listOf(0.75f, 1f, 1.25f, 1.5f) },
            scope = backgroundScope,
        )
        takeSnapshot()
        state.speedUp()
        takeSnapshot()
        assertEquals(1.25f, state.currentSpeed)
        assertEquals(2, state.currentIndex)
    }

    @Test
    fun `speedUp - in list but at the last index`() = runTest {
        val speed = TestPlaybackSpeed(initial = 1.5f)
        val state = PlaybackSpeedControllerState(
            playbackSpeed = speed,
            speedProvider = { listOf(0.75f, 1f, 1.25f, 1.5f) },
            scope = backgroundScope,
        )
        takeSnapshot()
        // Already at last => Speed won't go beyond 1.5
        state.speedUp()
        takeSnapshot()
        assertEquals(1.5f, state.currentSpeed)
        assertEquals(3, state.currentIndex)
    }

    @Test
    fun `speedUp - currentSpeed not in list`() = runTest {
        val speed = TestPlaybackSpeed(initial = 1.3f)
        val state = PlaybackSpeedControllerState(
            playbackSpeed = speed,
            speedProvider = { listOf(0.5f, 1f, 1.25f, 1.5f, 2f) },
            scope = backgroundScope,
        )
        takeSnapshot()
        // 1.3f is not in list
        assertEquals(1.3f, state.currentSpeed)
        assertEquals(1, state.currentIndex)
        
        state.speedUp()
        takeSnapshot()
        // nearestUp is 1.5f
        assertEquals(1.25f, state.currentSpeed)
        assertEquals(2, state.currentIndex)
    }

    @Test
    fun `speedUp - currentSpeed above highest in list`() = runTest {
        val speed = TestPlaybackSpeed(initial = 3f)
        val state = PlaybackSpeedControllerState(
            playbackSpeed = speed,
            speedProvider = { listOf(0.5f, 1f, 2f) },
            scope = backgroundScope,
        )
        takeSnapshot()
        // 3f not in list => nearestUp doesn't exist => fallback to last
        state.speedUp()
        takeSnapshot()
        assertEquals(2f, state.currentSpeed)
        assertEquals(2, state.currentIndex)
    }

    @Test
    fun `speedDown - in list but not at the first index`() = runTest {
        val speed = TestPlaybackSpeed(initial = 1.25f)
        val state = PlaybackSpeedControllerState(
            playbackSpeed = speed,
            speedProvider = { listOf(0.75f, 1f, 1.25f, 1.5f) },
            scope = backgroundScope,
        )
        takeSnapshot()
        state.speedDown()
        takeSnapshot()
        assertEquals(1f, state.currentSpeed)
        assertEquals(1, state.currentIndex)
    }

    @Test
    fun `speedDown - in list but already at first index`() = runTest {
        val speed = TestPlaybackSpeed(initial = 0.75f)
        val state = PlaybackSpeedControllerState(
            playbackSpeed = speed,
            speedProvider = { listOf(0.75f, 1f, 1.25f) },
            scope = backgroundScope,
        )
        takeSnapshot()
        state.speedDown()
        takeSnapshot()
        // Stays at 0.75f
        assertEquals(0.75f, state.currentSpeed)
        assertEquals(0, state.currentIndex)
    }

    @Test
    fun `speedDown - currentSpeed not in list`() = runTest {
        val speed = TestPlaybackSpeed(initial = 0.8f)
        val state = PlaybackSpeedControllerState(
            playbackSpeed = speed,
            speedProvider = { listOf(0.5f, 1f, 1.5f) },
            scope = backgroundScope,
        )
        takeSnapshot()
        // 0.8f not in list => currentIndex = -1
        state.speedDown()
        takeSnapshot()
        // nearestDown is 0.5f
        assertEquals(0.5f, state.currentSpeed)
        assertEquals(0, state.currentIndex)
    }

    @Test
    fun `speedDown - currentSpeed below lowest in list`() = runTest {
        val speed = TestPlaybackSpeed(initial = 0.25f)
        val state = PlaybackSpeedControllerState(
            playbackSpeed = speed,
            speedProvider = { listOf(0.5f, 1f, 2f) },
            scope = backgroundScope,
        )
        takeSnapshot()
        // nearestDown doesn't exist => fallback to first => 0.5f
        state.speedDown()
        takeSnapshot()
        assertEquals(0.5f, state.currentSpeed)
        assertEquals(0, state.currentIndex)
    }

    @Test
    fun `setSpeed - valid index`() = runTest {
        val speed = TestPlaybackSpeed(initial = 1f)
        val state = PlaybackSpeedControllerState(
            playbackSpeed = speed,
            speedProvider = { listOf(0.5f, 1f, 2f) },
            scope = backgroundScope,
        )
        takeSnapshot()
        state.setSpeed(2)
        takeSnapshot()
        assertEquals(2f, state.currentSpeed)
        assertEquals(2, state.currentIndex)
    }

    @Test
    fun `setSpeed - invalid index`() = runTest {
        val speed = TestPlaybackSpeed(initial = 1f)
        val state = PlaybackSpeedControllerState(
            playbackSpeed = speed,
            speedProvider = { listOf(0.5f, 1f, 2f) },
            scope = backgroundScope,
        )
        takeSnapshot()
        assertFailsWith<IllegalArgumentException> {
            state.setSpeed(999) // out of range
        }
    }

    @Test
    fun `setSpeed - directly by value`() = runTest {
        val speed = TestPlaybackSpeed(initial = 1f)
        val state = PlaybackSpeedControllerState(
            playbackSpeed = speed,
            speedProvider = { listOf(0.5f, 1f, 1.5f) },
            scope = backgroundScope,
        )
        takeSnapshot()
        state.setSpeed(1.5f)
        takeSnapshot()
        assertEquals(1.5f, state.currentSpeed)
        assertEquals(2, state.currentIndex)
    }

    @Test
    fun `reset - sets speed to 1_0f`() = runTest {
        val speed = TestPlaybackSpeed(initial = 0.5f)
        val state = PlaybackSpeedControllerState(
            playbackSpeed = speed,
            speedProvider = { listOf(0.5f, 1f, 1.5f) },
            scope = backgroundScope,
        )
        takeSnapshot()
        state.reset()
        takeSnapshot()
        assertEquals(1f, state.currentSpeed)
        assertEquals(1, state.currentIndex)
    }
}
