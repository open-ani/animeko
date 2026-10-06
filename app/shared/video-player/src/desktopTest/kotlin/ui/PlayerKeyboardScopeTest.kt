/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.videoplayer.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performKeyPress
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import me.him188.ani.app.ui.framework.AniComposeUiTest
import me.him188.ani.app.ui.framework.runAniComposeUiTest
import me.him188.ani.app.videoplayer.ui.gesture.FastSkipState
import me.him188.ani.app.videoplayer.ui.gesture.rememberPlayerKeyboardShortcuts
import me.him188.ani.app.videoplayer.ui.gesture.rememberSwipeSeekerState
import java.awt.Canvas
import java.awt.event.KeyEvent as AwtKeyEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(InternalComposeUiApi::class)
class PlayerKeyboardScopeTest {
    private class Fixture {
        val controller = PlayerControllerState()
        val overlayVisible = mutableStateOf(false)
        val handlerVisible = mutableStateOf(true)
        val text = mutableStateOf(TextFieldValue("abc", TextRange(3)))
        val speed = mutableStateOf(1f)
        val seeks = mutableListOf<Int>()
        val volume = mutableListOf<Boolean>()
        var pauses = 0
        var fullscreen = 0
        var danmaku = 0
        var stats = 0
        var clicks = 0
        var starts = 0
        var stops = 0
        val fastSkip = FastSkipState({ starts++ }, { stops++ })

        @Composable
        fun RegisterHandler() {
            val seeker = rememberSwipeSeekerState(320, onSeek = { seeks += it })
            val handler = rememberPlayerKeyboardShortcuts(
                seeker, fastSkip, speed.value, 0.5f..3f, { speed.value = it },
                true, { volume += it }, { volume += it },
                { pauses++ }, { fullscreen++ }, { danmaku++ }, { stats++ },
            )
            DisposableEffect(handler) {
                controller.keyboard.register(handler)
                onDispose { controller.keyboard.unregister(handler) }
            }
        }
    }

    private fun runScopeTest(
        nested: Boolean = false,
        field: Int = 0,
        direction: LayoutDirection = LayoutDirection.Ltr,
        block: AniComposeUiTest.(Fixture) -> Unit,
    ) = runAniComposeUiTest {
        val fixture = Fixture()
        setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalLayoutDirection provides direction) {
                    PlayerKeyboardScope(fixture.controller) {
                        Column {
                            Box(Modifier.size(80.dp).testTag("tab").clickable { fixture.clicks++ })
                            val player: @Composable () -> Unit = {
                                Column {
                                    if (fixture.handlerVisible.value) fixture.RegisterHandler()
                                    Box(
                                        Modifier.size(320.dp, 160.dp).testTag("player")
                                            .onPreviewKeyEvent { it.key == Key.Enter || it.key == Key.NumPadEnter }
                                            .playerFocusHost(fixture.controller.focusState, null).focusable(),
                                    )
                                    val fieldModifier = Modifier.testTag("field")
                                    when (field) {
                                        0 -> BasicTextField(
                                            fixture.text.value,
                                            { fixture.text.value = it },
                                            fieldModifier,
                                        )
                                        1 -> TextField(
                                            fixture.text.value,
                                            { fixture.text.value = it },
                                            fieldModifier,
                                        )
                                        else -> OutlinedTextField(
                                            fixture.text.value,
                                            { fixture.text.value = it },
                                            fieldModifier,
                                        )
                                    }
                                }
                            }
                            if (nested) PlayerKeyboardScope(fixture.controller, content = player) else player()
                            if (fixture.overlayVisible.value) {
                                Box(Modifier.size(80.dp).testTag("overlay").focusable())
                            }
                        }
                    }
                }
            }
        }
        waitForIdle()
        onNodeWithTag("player").assertIsFocused()
        block(fixture)
    }

    private fun AniComposeUiTest.press(tag: String, key: Key) {
        onNodeWithTag(tag).performKeyInput { pressKey(key) }
    }

    private fun AniComposeUiTest.assertPlaybackCommands(fixture: Fixture, tag: String) {
        press(tag, Key.Spacebar)
        press(tag, Key.DirectionRight)
        press(tag, Key.DirectionUp)
        assertEquals(1, fixture.pauses)
        assertEquals(listOf(5), fixture.seeks)
        assertEquals(listOf(false), fixture.volume)
        press(tag, Key.Spacebar)
        assertEquals(2, fixture.pauses)
    }

    @Test
    fun `clicked tab receives playback shortcuts before clickable`() = runScopeTest { fixture ->
        onNodeWithTag("tab").performMouseInput { click() }
        onNodeWithTag("tab").assertIsFocused()
        assertPlaybackCommands(fixture, "tab")
        assertEquals(1, fixture.clicks)
    }

    @Test
    fun `player receives playback shortcuts`() = runScopeTest { fixture ->
        assertPlaybackCommands(fixture, "player")
    }

    @Test
    fun `removed focused overlay restores shortcut target`() = runScopeTest { fixture ->
        runOnIdle { fixture.overlayVisible.value = true }
        onNodeWithTag("overlay").requestFocus()
        onNodeWithTag("overlay").assertIsFocused()
        runOnIdle { fixture.overlayVisible.value = false }
        onNodeWithTag("player").assertIsFocused()
        assertPlaybackCommands(fixture, "player")
    }

    private fun textInputTest(field: Int) = runScopeTest(field = field, nested = true) { fixture ->
        onNodeWithTag("field").requestFocus()
        waitForIdle()
        assertTrue(
            fixture.controller.keyboard.activeTextInputSessions > 0,
            "Focused desktop text field starts an intercepted session",
        )
        assertEquals(PlayerFocusTarget.PLAYER, fixture.controller.focusState.preferredTarget)
        // Key events exercise text editing and caret movement without playerTextInputFocus annotations.
        for ((key, character) in listOf(Key.Spacebar to ' ', Key.A to 'a')) {
            onNodeWithTag("field").performKeyInput { keyDown(key) }
            onNodeWithTag("field").performKeyPress(
                KeyEvent(
                    key, KeyEventType.Unknown, codePoint = character.code,
                    nativeEvent = AwtKeyEvent(
                        Canvas(), AwtKeyEvent.KEY_TYPED, 0, 0, AwtKeyEvent.VK_UNDEFINED, character,
                    ),
                ),
            )
            onNodeWithTag("field").performKeyInput { keyUp(key) }
        }
        assertEquals("abc a", fixture.text.value.text)
        press("field", Key.DirectionLeft)
        assertEquals(TextRange(4), fixture.text.value.selection)
        press("field", Key.DirectionRight)
        assertEquals(TextRange(5), fixture.text.value.selection)
        press("field", Key.DirectionUp)
        assertEquals(0, fixture.pauses)
        assertEquals(1f, fixture.speed.value)
        assertTrue(fixture.seeks.isEmpty())
        assertTrue(fixture.volume.isEmpty())
        onNodeWithTag("tab").requestFocus()
        waitForIdle()
        assertEquals(0, fixture.controller.keyboard.activeTextInputSessions)
        assertPlaybackCommands(fixture, "tab")
    }

    @Test
    fun `BasicTextField session suppresses shortcuts`() = textInputTest(0)

    @Test
    fun `TextField session suppresses shortcuts`() = textInputTest(1)

    @Test
    fun `OutlinedTextField session suppresses shortcuts`() = textInputTest(2)

    @Test
    fun `danmaku text input preference suppresses shortcuts`() = runScopeTest { fixture ->
        runOnIdle { fixture.controller.focusState.preferTextInput(this) }
        press("player", Key.Spacebar)
        press("player", Key.DirectionRight)
        press("player", Key.DirectionUp)
        assertEquals(0, fixture.pauses)
        assertTrue(fixture.seeks.isEmpty())
        assertTrue(fixture.volume.isEmpty())
        runOnIdle { fixture.controller.focusState.preferPlayer() }
        assertPlaybackCommands(fixture, "player")
    }

    @Test
    fun `nested scopes dispatch once`() = runScopeTest(nested = true) { fixture ->
        assertPlaybackCommands(fixture, "player")
    }

    @Test
    fun `long press accelerates until release and short press seeks`() = runScopeTest { fixture ->
        mainClock.autoAdvance = false
        onNodeWithTag("player").performKeyInput { keyDown(Key.DirectionRight) }
        mainClock.advanceTimeBy(300)
        assertEquals(1, fixture.starts)
        onNodeWithTag("player").performKeyInput { keyUp(Key.DirectionRight) }
        assertEquals(1, fixture.stops)
        assertTrue(fixture.seeks.isEmpty())
        press("player", Key.DirectionRight)
        assertEquals(listOf(5), fixture.seeks)
    }

    @Test
    fun `typing cancels held fast skip and ignores its release`() = runScopeTest { fixture ->
        mainClock.autoAdvance = false
        onNodeWithTag("player").performKeyInput { keyDown(Key.DirectionRight) }
        mainClock.advanceTimeBy(300)
        assertEquals(1, fixture.starts)
        onNodeWithTag("field").requestFocus()
        mainClock.advanceTimeByFrame()
        waitForIdle()
        assertTrue(fixture.controller.keyboard.activeTextInputSessions > 0)
        assertEquals(1, fixture.stops)
        onNodeWithTag("field").performKeyInput { keyUp(Key.DirectionRight) }
        onNodeWithTag("tab").requestFocus()
        waitForIdle()
        onNodeWithTag("tab").performKeyPress(KeyEvent(Key.DirectionRight, KeyEventType.KeyUp))
        assertTrue(fixture.seeks.isEmpty())
        press("tab", Key.DirectionRight)
        assertEquals(listOf(5), fixture.seeks)
    }

    @Test
    fun `typing before long press timeout cancels seek and acceleration`() = runScopeTest { fixture ->
        mainClock.autoAdvance = false
        onNodeWithTag("player").performKeyInput { keyDown(Key.DirectionRight) }
        onNodeWithTag("field").requestFocus()
        mainClock.advanceTimeBy(300)
        onNodeWithTag("field").performKeyInput { keyUp(Key.DirectionRight) }
        assertEquals(0, fixture.starts)
        assertEquals(0, fixture.stops)
        assertTrue(fixture.seeks.isEmpty())
    }

    @Test
    fun `release of a key pressed while typing cannot seek`() = runScopeTest { fixture ->
        onNodeWithTag("field").requestFocus()
        waitForIdle()
        onNodeWithTag("field").performKeyInput { keyDown(Key.DirectionRight) }
        onNodeWithTag("tab").requestFocus()
        waitForIdle()
        onNodeWithTag("tab").performKeyInput { keyUp(Key.DirectionRight) }
        assertTrue(fixture.seeks.isEmpty())
        press("tab", Key.DirectionRight)
        assertEquals(listOf(5), fixture.seeks)
    }

    @Test
    fun `handler disposal cancels held fast skip`() = runScopeTest { fixture ->
        mainClock.autoAdvance = false
        onNodeWithTag("player").performKeyInput { keyDown(Key.DirectionRight) }
        mainClock.advanceTimeBy(300)
        runOnIdle { fixture.handlerVisible.value = false }
        mainClock.advanceTimeByFrame()
        assertEquals(1, fixture.starts)
        assertEquals(1, fixture.stops)
        onNodeWithTag("player").performKeyInput { keyUp(Key.DirectionRight) }
        assertTrue(fixture.seeks.isEmpty())
    }

    @Test
    fun `RTL horizontal keys seek in layout direction`() = runScopeTest(direction = LayoutDirection.Rtl) { fixture ->
        press("player", Key.DirectionLeft)
        press("player", Key.DirectionRight)
        assertEquals(listOf(5, -5), fixture.seeks)
    }

    @Test
    fun `speed toggles and fine volume use current callbacks`() = runScopeTest { fixture ->
        for (key in listOf(Key.F, Key.B, Key.I)) press("player", key)
        assertEquals(1, fixture.fullscreen)
        assertEquals(1, fixture.danmaku)
        assertEquals(1, fixture.stats)
        for ((key, speed) in listOf(
            Key.Two to 2f, Key.Three to 3f, Key.One to 1f,
            Key.NumPad2 to 2f, Key.NumPad3 to 3f, Key.NumPad1 to 1f,
            Key.D to 1.25f, Key.A to 1f, Key.S to 1f,
        )) {
            press("player", key)
            assertEquals(speed, fixture.speed.value)
        }
        onNodeWithTag("player").performKeyInput {
            keyDown(Key.ShiftLeft)
            pressKey(Key.DirectionUp)
            keyUp(Key.ShiftLeft)
        }
        assertEquals(listOf(true), fixture.volume)
    }

    private fun modifiedShortcutTest(
        key: Key,
        ctrl: Boolean = false,
        alt: Boolean = false,
        meta: Boolean = false,
    ) = runScopeTest { fixture ->
        val player = onNodeWithTag("player")
        for (releaseModifierFirst in listOf(false, true)) {
            assertFalse(
                player.performKeyPress(
                    KeyEvent(key, KeyEventType.KeyDown, isCtrlPressed = ctrl, isAltPressed = alt, isMetaPressed = meta),
                ),
            )
            if (releaseModifierFirst) {
                // A repeat after the modifier is released belongs to the rejected press.
                assertFalse(player.performKeyPress(KeyEvent(key, KeyEventType.KeyDown)))
            }
            assertFalse(
                player.performKeyPress(
                    KeyEvent(
                        key, KeyEventType.KeyUp,
                        isCtrlPressed = ctrl && !releaseModifierFirst,
                        isAltPressed = alt && !releaseModifierFirst,
                        isMetaPressed = meta && !releaseModifierFirst,
                    ),
                ),
            )
        }
        assertEquals(1f, fixture.speed.value)
        assertEquals(0, fixture.pauses)
        assertEquals(0, fixture.fullscreen)
        assertEquals(0, fixture.danmaku)
        assertEquals(0, fixture.stats)
        assertTrue(fixture.seeks.isEmpty())
        assertTrue(fixture.volume.isEmpty())
        assertPlaybackCommands(fixture, "player")
    }

    @Test
    fun `Ctrl A is passed through without changing speed`() = modifiedShortcutTest(Key.A, ctrl = true)

    @Test
    fun `Ctrl Space is passed through without toggling playback`() = modifiedShortcutTest(Key.Spacebar, ctrl = true)

    @Test
    fun `Alt F is passed through without toggling fullscreen`() = modifiedShortcutTest(Key.F, alt = true)

    @Test
    fun `Meta B is passed through without toggling danmaku`() = modifiedShortcutTest(Key.B, meta = true)

    @Test
    fun `modified repeat cancels held fast skip without seeking on release`() = runScopeTest { fixture ->
        mainClock.autoAdvance = false
        val player = onNodeWithTag("player")
        player.performKeyInput { keyDown(Key.DirectionRight) }
        mainClock.advanceTimeBy(300)
        assertEquals(1, fixture.starts)
        assertFalse(
            player.performKeyPress(KeyEvent(Key.DirectionRight, KeyEventType.KeyDown, isCtrlPressed = true)),
        )
        assertEquals(1, fixture.stops)
        player.performKeyInput { keyUp(Key.DirectionRight) }
        assertTrue(fixture.seeks.isEmpty())
        press("player", Key.DirectionRight)
        assertEquals(listOf(5), fixture.seeks)
    }

    @Test
    fun `Shift Up adjusts volume finely on each KeyDown`() = runScopeTest { fixture ->
        val player = onNodeWithTag("player")
        repeat(2) {
            assertTrue(player.performKeyPress(KeyEvent(Key.DirectionUp, KeyEventType.KeyDown, isShiftPressed = true)))
        }
        assertEquals(listOf(true, true), fixture.volume)
        assertTrue(player.performKeyPress(KeyEvent(Key.DirectionUp, KeyEventType.KeyUp, isShiftPressed = true)))
        assertEquals(listOf(true, true), fixture.volume)
    }

    @Test
    fun `Enter activates tab and Tab traverses focus`() = runScopeTest { fixture ->
        onNodeWithTag("tab").requestFocus()
        press("tab", Key.Enter)
        assertEquals(1, fixture.clicks)
        press("tab", Key.Tab)
        onNodeWithTag("player").assertIsFocused()
        press("player", Key.Enter)
        assertEquals(0, fixture.pauses)
    }

    @Test
    fun `last registration wins and stale disposal preserves it`() {
        val keyboard = PlayerKeyboardState()
        var firstCalls = 0
        var secondCalls = 0
        val first = object : PlayerKeyboardHandler {
            override fun invoke(event: KeyEvent): Boolean {
                firstCalls++
                return true
            }
            override fun cancel() {}
        }
        val second = object : PlayerKeyboardHandler {
            override fun invoke(event: KeyEvent): Boolean {
                secondCalls++
                return true
            }
            override fun cancel() {}
        }
        keyboard.register(first)
        keyboard.register(second)
        keyboard.unregister(first)
        keyboard.dispatch(KeyEvent(Key.Spacebar, KeyEventType.KeyDown), PlayerFocusTarget.PLAYER)
        keyboard.dispatch(KeyEvent(Key.Spacebar, KeyEventType.KeyUp), PlayerFocusTarget.PLAYER)
        assertEquals(0, firstCalls)
        assertEquals(2, secondCalls)
        keyboard.unregister(second)
        assertEquals(false, keyboard.dispatch(KeyEvent(Key.Spacebar, KeyEventType.KeyDown), PlayerFocusTarget.PLAYER))
    }

}
