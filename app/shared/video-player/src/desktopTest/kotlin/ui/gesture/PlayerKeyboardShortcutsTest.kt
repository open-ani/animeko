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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import me.him188.ani.app.data.models.preference.PlayerShortcut
import me.him188.ani.app.data.models.preference.PlayerShortcutAction
import me.him188.ani.app.data.models.preference.PlayerShortcutConfig
import me.him188.ani.app.ui.framework.AniComposeUiTest
import me.him188.ani.app.ui.framework.runAniComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals

class PlayerKeyboardShortcutsTest {
    private class Fixture {
        val seeks = mutableListOf<Int>()
        val speeds = mutableListOf<Float>()
        val volumeUps = mutableListOf<Boolean>()
        val volumeDowns = mutableListOf<Boolean>()
        var pauseToggles = 0
        var fullscreenToggles = 0
        var danmakuToggles = 0
        var statsToggles = 0
        val config = mutableStateOf(PlayerShortcutConfig.Default)
        val currentSpeed = mutableStateOf<Float?>(1f)
        val volumeEnabled = mutableStateOf(true)
    }

    private fun runShortcutTest(block: AniComposeUiTest.(Fixture) -> Unit) = runAniComposeUiTest {
        val fixture = Fixture()
        val focusRequester = FocusRequester()
        setContent {
            MaterialTheme {
                val seekerState = remember {
                    SwipeSeekerState(
                        screenWidthPx = 1000,
                        swipeSeekerConfig = SwipeSeekerConfig(),
                        density = Density(1f),
                        onSeek = fixture.seeks::add,
                    )
                }
                Box(
                    Modifier.size(320.dp, 160.dp).testTag("target")
                        .playerKeyboardShortcuts(
                            shortcuts = fixture.config.value,
                            seekerState = seekerState,
                            fastSkipState = null,
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
        onNodeWithTag("target").performKeyInput {
            pressKey(Key.Spacebar) // no longer bound
            pressKey(Key.K)
            pressKey(Key.F) // needs Ctrl now
            keyDown(Key.CtrlLeft)
            pressKey(Key.F)
            keyUp(Key.CtrlLeft)
        }
        waitForIdle()
        runOnIdle {
            assertEquals(1, fixture.pauseToggles)
            assertEquals(1, fixture.fullscreenToggles)
        }
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
        onNodeWithTag("target").performKeyInput {
            pressKey(Key.D)
            pressKey(Key.DirectionUp)
        }
        waitForIdle()
        runOnIdle {
            assertEquals(emptyList(), fixture.speeds)
            assertEquals(emptyList(), fixture.volumeUps)
        }
    }
}
