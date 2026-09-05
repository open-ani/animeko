/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.foundation.interaction

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import me.him188.ani.app.ui.framework.runAniComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ClearFocusOnTapTest {
    @Test
    fun `does not merge a descendant pane title`() = runAniComposeUiTest {
        setContent {
            Box(
                Modifier
                    .size(100.dp)
                    .clearFocusOnUnhandledTap()
                    .testTag("root"),
            ) {
                Box(Modifier.semantics { paneTitle = "Reminder" })
            }
        }

        // Layout Inspector reads the merged tree. This used to throw while merging PaneTitle.
        onNodeWithTag("root").fetchSemanticsNode()
    }

    @Test
    fun `handled descendant tap keeps focus while background tap clears it`() = runAniComposeUiTest {
        val focusRequester = FocusRequester()
        val keyboard = RecordingSoftwareKeyboardController()
        setContent {
            CompositionLocalProvider(LocalSoftwareKeyboardController provides keyboard) {
                Box(
                    Modifier
                        .size(100.dp)
                        .clearFocusOnUnhandledTap()
                        .testTag("root"),
                ) {
                    Box(
                        Modifier
                            .size(40.dp)
                            .focusRequester(focusRequester)
                            .clickable {}
                            .testTag("child"),
                    )
                }
            }
        }

        runOnIdle { focusRequester.requestFocus() }
        onNodeWithTag("child")
            .assertIsFocused()
            .performTouchInput { click() }
            .assertIsFocused()
        runOnIdle { assertEquals(0, keyboard.hideCount) }

        onNodeWithTag("root", useUnmergedTree = true).performTouchInput {
            click(bottomRight - Offset(1f, 1f))
        }
        onNodeWithTag("child").assertIsNotFocused()
        runOnIdle { assertEquals(1, keyboard.hideCount) }
    }

    private class RecordingSoftwareKeyboardController : SoftwareKeyboardController {
        var hideCount = 0
            private set

        override fun show() = Unit

        override fun hide() {
            hideCount++
        }
    }
}
