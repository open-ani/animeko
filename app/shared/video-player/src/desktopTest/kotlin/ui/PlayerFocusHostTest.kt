/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.videoplayer.ui

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.unit.dp
import me.him188.ani.app.ui.framework.AniComposeUiTest
import me.him188.ani.app.ui.framework.runAniComposeUiTest
import kotlin.test.Test

class PlayerFocusHostTest {
    private class Fixture(
        /** Whether the page root also applies [restorePlayerFocusWhenCleared], as the episode page does. */
        val pageLevel: Boolean,
    ) {
        val focusState = PlayerFocusState()
        val overlayVisible = mutableStateOf(false)
        lateinit var focusManager: FocusManager
    }

    /**
     * Mirrors [VideoScaffold]: the player and an overlay (e.g. a side sheet) share a [restorePlayerFocusWhenCleared]
     * root. "outside" stands for page content next to a non-fullscreen player.
     */
    private fun runFocusTest(
        pageLevel: Boolean = false,
        block: AniComposeUiTest.(Fixture) -> Unit,
    ) = runAniComposeUiTest {
        val fixture = Fixture(pageLevel)
        setContent {
            fixture.focusManager = LocalFocusManager.current
            Column(
                if (fixture.pageLevel) Modifier.restorePlayerFocusWhenCleared(fixture.focusState) else Modifier,
            ) {
                Box(Modifier.size(80.dp).testTag("outside").focusable())
                Column(Modifier.restorePlayerFocusWhenCleared(fixture.focusState)) {
                    Box(
                        Modifier.size(320.dp, 160.dp)
                            .testTag("player")
                            .playerFocusHost(fixture.focusState, reapplyKey = null)
                            .focusable(),
                    )
                    if (fixture.overlayVisible.value) {
                        Box(Modifier.size(80.dp).testTag("overlayItem").focusable())
                        Box(
                            Modifier.size(80.dp).testTag("overlayTextInput")
                                .playerTextInputFocus(fixture.focusState)
                                .focusable(),
                        )
                    }
                }
            }
        }
        waitForIdle()
        onNodeWithTag("player").assertIsFocused()
        block(fixture)
    }

    private fun AniComposeUiTest.focusOverlayItem(fixture: Fixture, tag: String = "overlayItem") {
        fixture.overlayVisible.value = true
        waitForIdle()
        onNodeWithTag(tag).requestFocus()
        waitForIdle()
        onNodeWithTag(tag).assertIsFocused()
    }

    @Test
    fun `restores player focus when the focused overlay is removed`() = runFocusTest { fixture ->
        focusOverlayItem(fixture)

        fixture.overlayVisible.value = false
        waitForIdle()
        onNodeWithTag("player").assertIsFocused()
    }

    @Test
    fun `restores player focus when focus inside the player is cleared`() = runFocusTest { fixture ->
        focusOverlayItem(fixture)

        fixture.focusManager.clearFocus()
        waitForIdle()
        onNodeWithTag("player").assertIsFocused()
    }

    @Test
    fun `does not take back focus moved outside the player`() = runFocusTest { fixture ->
        focusOverlayItem(fixture)

        onNodeWithTag("outside").requestFocus()
        waitForIdle()
        fixture.overlayVisible.value = false
        waitForIdle()
        onNodeWithTag("outside").assertIsFocused()
        onNodeWithTag("player").assertIsNotFocused()
    }

    @Test
    fun `does not take focus from another target inside the player`() = runFocusTest { fixture ->
        focusOverlayItem(fixture)
        // Moving within the player is not a clear: the newly focused overlay item keeps focus.
        onNodeWithTag("player").requestFocus()
        waitForIdle()
        onNodeWithTag("overlayItem").requestFocus()
        waitForIdle()
        onNodeWithTag("overlayItem").assertIsFocused()
    }

    @Test
    fun `keeps text input preference when its focus is cleared`() = runFocusTest { fixture ->
        focusOverlayItem(fixture, "overlayTextInput")

        fixture.focusManager.clearFocus()
        waitForIdle()
        onNodeWithTag("player").assertIsNotFocused()
    }

    @Test
    fun `player-only scope leaves focus cleared outside the player`() = runFocusTest { fixture ->
        onNodeWithTag("outside").requestFocus()
        waitForIdle()

        fixture.focusManager.clearFocus()
        waitForIdle()
        onNodeWithTag("player").assertIsNotFocused()
    }

    @Test
    fun `page scope restores player focus when page content focus is cleared`() =
        runFocusTest(pageLevel = true) { fixture ->
            // e.g. clicking the comment entry, then dismissing the comment editor, which clears focus
            onNodeWithTag("outside").requestFocus()
            waitForIdle()
            onNodeWithTag("outside").assertIsFocused()

            fixture.focusManager.clearFocus()
            waitForIdle()
            onNodeWithTag("player").assertIsFocused()
        }

    @Test
    fun `page scope does not take focus from page content`() = runFocusTest(pageLevel = true) {
        onNodeWithTag("outside").requestFocus()
        waitForIdle()
        onNodeWithTag("outside").assertIsFocused()
        onNodeWithTag("player").assertIsNotFocused()
    }
}
