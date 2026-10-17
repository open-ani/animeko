/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.videoplayer.ui.gesture

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.MutableStateFlow
import me.him188.ani.app.ui.framework.AniComposeUiTest
import me.him188.ani.app.ui.framework.assertScreenshot
import me.him188.ani.app.ui.framework.runAniComposeUiTest
import org.openani.mediamp.InternalForInheritanceMediampApi
import org.openani.mediamp.features.PlaybackSpeed
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(InternalForInheritanceMediampApi::class)
class LongPressFastSkipTest {
    private class TestPlaybackSpeed : PlaybackSpeed {
        override val valueFlow = MutableStateFlow(1.3f)
        override val value: Float get() = valueFlow.value
        val updates = mutableListOf<Float>()

        override fun set(speed: Float) {
            updates += speed
            valueFlow.value = speed
        }
    }

    private class Fixture {
        val speed = TestPlaybackSpeed()
        val indicator = GestureIndicatorState()
        val fastSkip = PlayerFastSkipState(speed, indicator, fastForwardSpeed = 2.5f).fastSkipState
        val visible = mutableStateOf(true)
        val pointerType = mutableStateOf(PointerType.Touch)

        fun assertRestored(expectedUpdates: List<Float>) {
            assertEquals(1.3f, speed.value)
            assertFalse(indicator.visible)
            assertEquals(expectedUpdates, speed.updates)
        }
    }

    private fun runGestureTest(block: AniComposeUiTest.(Fixture) -> Unit) = runAniComposeUiTest {
        val fixture = Fixture()
        setContent {
            MaterialTheme {
                if (fixture.visible.value) {
                    Box(
                        Modifier.size(320.dp, 160.dp).testTag("target")
                            .background(Color(0xFF202020))
                            .longPressFastSkip(fixture.fastSkip, SkipDirection.FORWARD, fixture.pointerType.value),
                        contentAlignment = Alignment.Center,
                    ) {
                        GestureIndicator(fixture.indicator)
                    }
                }
            }
        }
        block(fixture)
    }

    private fun AniComposeUiTest.startLongPress(fixture: Fixture) {
        onNodeWithTag("target").performTouchInput { down(center) }
        mainClock.advanceTimeBy(600)
        runOnIdle {
            assertEquals(2.5f, fixture.speed.value)
            assertTrue(fixture.indicator.visible)
        }
    }

    @Test
    fun `normal release restores speed and hides indicator`() = runGestureTest { fixture ->
        startLongPress(fixture)
        onNodeWithTag("target").performTouchInput { up() }
        runOnIdle { fixture.assertRestored(listOf(2.5f, 1.3f)) }
    }

    @Test
    fun `cancellation restores speed and next long press still works`() = runGestureTest { fixture ->
        startLongPress(fixture)
        mainClock.advanceTimeBy(1_000)
        onNodeWithText("2.50x").assertIsDisplayed()
        // Skiko's TouchInjectionScope.cancel() is a no-op. Changing the pointerInput key
        // actually cancels its coroutine, as density/view-configuration changes also do.
        runOnIdle { fixture.pointerType.value = PointerType.Mouse }
        mainClock.advanceTimeBy(2_000)
        runOnIdle { fixture.assertRestored(listOf(2.5f, 1.3f)) }
        onNodeWithTag("target").assertScreenshot("/screenshots/long-press-fast-skip/cancelled.png")

        onNodeWithTag("target").performTouchInput { up() }
        runOnIdle { fixture.pointerType.value = PointerType.Touch }
        startLongPress(fixture)
        onNodeWithTag("target").performTouchInput { up() }
        runOnIdle { fixture.assertRestored(listOf(2.5f, 1.3f, 2.5f, 1.3f)) }
    }

    @Test
    fun `cancellation before timeout prevents delayed acceleration`() = runGestureTest { fixture ->
        onNodeWithTag("target").performTouchInput { down(center) }
        mainClock.advanceTimeBy(100)
        runOnIdle { fixture.pointerType.value = PointerType.Mouse }
        mainClock.advanceTimeBy(2_000)
        runOnIdle { fixture.assertRestored(emptyList()) }

        onNodeWithTag("target").performTouchInput { up() }
        runOnIdle { fixture.pointerType.value = PointerType.Touch }
        startLongPress(fixture)
        onNodeWithTag("target").performTouchInput { up() }
        runOnIdle { fixture.assertRestored(listOf(2.5f, 1.3f)) }
    }

    @Test
    fun `removing gesture host restores speed`() = runGestureTest { fixture ->
        startLongPress(fixture)
        runOnIdle { fixture.visible.value = false }
        runOnIdle { fixture.assertRestored(listOf(2.5f, 1.3f)) }
    }

    @Test
    fun `short tap does not change speed`() = runGestureTest { fixture ->
        onNodeWithTag("target").performTouchInput { down(center) }
        mainClock.advanceTimeBy(100)
        onNodeWithTag("target").performTouchInput { up() }
        mainClock.advanceTimeBy(2_000)
        runOnIdle { fixture.assertRestored(emptyList()) }
    }

    @Test
    fun `moving before timeout does not accelerate`() = runGestureTest { fixture ->
        onNodeWithTag("target").performTouchInput {
            down(center)
            moveTo(center + Offset(width / 4f, 0f))
        }
        mainClock.advanceTimeBy(600)
        onNodeWithTag("target").performTouchInput { up() }
        runOnIdle { fixture.assertRestored(emptyList()) }
    }
}
