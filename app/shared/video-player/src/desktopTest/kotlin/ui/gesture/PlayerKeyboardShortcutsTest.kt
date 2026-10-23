/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.videoplayer.ui.gesture

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performKeyPress
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.dp
import me.him188.ani.app.data.models.preference.PlayerShortcut
import me.him188.ani.app.data.models.preference.PlayerShortcutAction
import me.him188.ani.app.data.models.preference.PlayerShortcutConfig
import me.him188.ani.app.ui.framework.AniComposeUiTest
import me.him188.ani.app.ui.framework.runAniComposeUiTest
import me.him188.ani.app.videoplayer.ui.PlayerKeyboardState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Drives [rememberPlayerKeyboardShortcuts] through [PlayerKeyboardState.dispatch], the same path the
 * player page uses, so custom bindings are verified together with the scope's modifier handling.
 */
@OptIn(InternalComposeUiApi::class)
class PlayerKeyboardShortcutsTest {
    private class Fixture {
        val keyboard = PlayerKeyboardState()
        val seeks = mutableListOf<Int>()
        val speeds = mutableListOf<Float>()
        val volumeUps = mutableListOf<Boolean>()
        val volumeDowns = mutableListOf<Boolean>()
        var pauseToggles = 0
        var fullscreenToggles = 0
        var danmakuToggles = 0
        var statsToggles = 0
        var skipStarts = 0
        var skipStops = 0
        val fastSkip = FastSkipState({ skipStarts++ }, { skipStops++ })
        val config = mutableStateOf(PlayerShortcutConfig.Default)
        val currentSpeed = mutableStateOf<Float?>(1f)
        val volumeEnabled = mutableStateOf(true)
    }

    private fun runShortcutTest(block: AniComposeUiTest.(Fixture) -> Unit) = runAniComposeUiTest {
        val fixture = Fixture()
        val focusRequester = FocusRequester()
        setContent {
            MaterialTheme {
                val seekerState = rememberSwipeSeekerState(320, onSeek = { fixture.seeks += it })
                val handler = rememberPlayerKeyboardShortcuts(
                    shortcuts = fixture.config.value,
                    seekerState = seekerState,
                    fastSkipState = fixture.fastSkip,
                    currentPlaybackSpeed = fixture.currentSpeed.value,
                    playbackSpeedRange = 0.5f..3f,
                    onPlaybackSpeedChanged = fixture.speeds::add,
                    volumeEnabled = fixture.volumeEnabled.value,
                    onVolumeUp = fixture.volumeUps::add,
                    onVolumeDown = fixture.volumeDowns::add,
                    onTogglePauseResume = { fixture.pauseToggles++ },
                    onToggleFullscreen = { fixture.fullscreenToggles++ },
                    onToggleDanmaku = { fixture.danmakuToggles++ },
                    onTogglePlayerStats = { fixture.statsToggles++ },
                )
                DisposableEffect(handler) {
                    fixture.keyboard.register(handler)
                    onDispose { fixture.keyboard.unregister(handler) }
                }
                Box(
                    Modifier.size(320.dp, 160.dp).testTag("target")
                        .onPreviewKeyEvent { fixture.keyboard.dispatch(it) }
                        .focusRequester(focusRequester)
                        .focusable(),
                )
            }
        }
        runOnIdle { focusRequester.requestFocus() }
        waitForIdle()
        block(fixture)
    }

    @Test
    fun `default bindings drive the callbacks`() = runShortcutTest { fixture ->
        onNodeWithTag("target").performKeyInput {
            pressKey(Key.Spacebar)
            pressKey(Key.F)
            pressKey(Key.B)
            pressKey(Key.I)
            pressKey(Key.DirectionLeft)
            pressKey(Key.DirectionRight)
            pressKey(Key.DirectionUp)
            pressKey(Key.DirectionDown)
            pressKey(Key.D)
            pressKey(Key.A)
            pressKey(Key.S)
            pressKey(Key.Two)
            pressKey(Key.NumPad3)
        }
        waitForIdle()
        runOnIdle {
            assertEquals(1, fixture.pauseToggles)
            assertEquals(1, fixture.fullscreenToggles)
            assertEquals(1, fixture.danmakuToggles)
            assertEquals(1, fixture.statsToggles)
            assertEquals(listOf(-5, 5), fixture.seeks)
            assertEquals(listOf(false), fixture.volumeUps)
            assertEquals(listOf(false), fixture.volumeDowns)
            assertEquals(listOf(1.25f, 0.75f, 1f, 2f, 3f), fixture.speeds)
        }
    }

    @Test
    fun `custom bindings replace the defaults`() = runShortcutTest { fixture ->
        runOnIdle {
            fixture.config.value = PlayerShortcutConfig.Default
                .withShortcuts(PlayerShortcutAction.TOGGLE_PAUSE_RESUME, listOf(PlayerShortcut("K")))
                .withShortcuts(PlayerShortcutAction.TOGGLE_FULLSCREEN, listOf(PlayerShortcut("F", ctrl = true)))
        }
        waitForIdle()
        val target = onNodeWithTag("target")
        target.performKeyInput {
            pressKey(Key.Spacebar) // no longer bound
            pressKey(Key.K)
        }
        // plain F is unbound now and passes through to the page
        assertFalse(target.performKeyPress(KeyEvent(Key.F, KeyEventType.KeyDown)))
        assertFalse(target.performKeyPress(KeyEvent(Key.F, KeyEventType.KeyUp)))
        // Ctrl+F is a real binding: consumed, and repeats while held do not cancel it
        assertTrue(target.performKeyPress(KeyEvent(Key.F, KeyEventType.KeyDown, isCtrlPressed = true)))
        assertTrue(target.performKeyPress(KeyEvent(Key.F, KeyEventType.KeyDown, isCtrlPressed = true)))
        assertTrue(target.performKeyPress(KeyEvent(Key.F, KeyEventType.KeyUp, isCtrlPressed = true)))
        waitForIdle()
        runOnIdle {
            assertEquals(1, fixture.pauseToggles)
            assertEquals(1, fixture.fullscreenToggles)
        }
    }

    @Test
    fun `unmatched modified press passes through and cancels a held shortcut`() = runShortcutTest { fixture ->
        mainClock.autoAdvance = false
        val target = onNodeWithTag("target")
        target.performKeyInput { keyDown(Key.DirectionRight) }
        mainClock.advanceTimeBy(300)
        assertEquals(1, fixture.skipStarts)
        assertFalse(target.performKeyPress(KeyEvent(Key.DirectionRight, KeyEventType.KeyDown, isCtrlPressed = true)))
        assertEquals(1, fixture.skipStops)
        target.performKeyInput { keyUp(Key.DirectionRight) }
        assertTrue(fixture.seeks.isEmpty())
        // Ctrl+Space never reaches the plain Space binding
        assertFalse(target.performKeyPress(KeyEvent(Key.Spacebar, KeyEventType.KeyDown, isCtrlPressed = true)))
        assertFalse(target.performKeyPress(KeyEvent(Key.Spacebar, KeyEventType.KeyUp, isCtrlPressed = true)))
        assertEquals(0, fixture.pauseToggles)
    }

    @Test
    fun `releasing the modifier before the key still completes the shortcut`() = runShortcutTest { fixture ->
        runOnIdle {
            fixture.config.value = PlayerShortcutConfig.Default
                .withShortcuts(PlayerShortcutAction.SEEK_FORWARD, listOf(PlayerShortcut("DirectionRight", ctrl = true)))
                .withShortcuts(PlayerShortcutAction.TOGGLE_FULLSCREEN, listOf(PlayerShortcut("F", ctrl = true)))
        }
        waitForIdle()
        mainClock.autoAdvance = false
        val target = onNodeWithTag("target")
        // hold Ctrl+Right into fast skip, then let go of Ctrl before the arrow
        assertTrue(target.performKeyPress(KeyEvent(Key.DirectionRight, KeyEventType.KeyDown, isCtrlPressed = true)))
        mainClock.advanceTimeBy(300)
        assertEquals(1, fixture.skipStarts)
        assertTrue(target.performKeyPress(KeyEvent(Key.DirectionRight, KeyEventType.KeyUp)))
        assertEquals(1, fixture.skipStops)
        assertTrue(fixture.seeks.isEmpty())
        // a toggle bound to Ctrl+F fires although Ctrl is already up when F is released
        assertTrue(target.performKeyPress(KeyEvent(Key.F, KeyEventType.KeyDown, isCtrlPressed = true)))
        assertTrue(target.performKeyPress(KeyEvent(Key.F, KeyEventType.KeyUp)))
        assertEquals(1, fixture.fullscreenToggles)
    }

    @Test
    fun `shift on volume keys requests fine adjustment`() = runShortcutTest { fixture ->
        onNodeWithTag("target").performKeyInput {
            keyDown(Key.ShiftLeft)
            pressKey(Key.DirectionUp)
            pressKey(Key.DirectionDown)
            keyUp(Key.ShiftLeft)
        }
        waitForIdle()
        runOnIdle {
            assertEquals(listOf(true), fixture.volumeUps)
            assertEquals(listOf(true), fixture.volumeDowns)
        }
    }

    @Test
    fun `unavailable actions are not consumed`() = runShortcutTest { fixture ->
        runOnIdle {
            fixture.currentSpeed.value = null
            fixture.volumeEnabled.value = false
        }
        waitForIdle()
        val target = onNodeWithTag("target")
        assertFalse(target.performKeyPress(KeyEvent(Key.D, KeyEventType.KeyDown)))
        assertFalse(target.performKeyPress(KeyEvent(Key.DirectionUp, KeyEventType.KeyDown)))
        runOnIdle {
            assertEquals(emptyList(), fixture.speeds)
            assertEquals(emptyList(), fixture.volumeUps)
        }
    }
}
