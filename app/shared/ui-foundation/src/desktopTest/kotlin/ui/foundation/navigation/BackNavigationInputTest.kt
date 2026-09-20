/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.foundation.navigation

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.MouseButton
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.dp
import me.him188.ani.app.ui.framework.runAniComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals

class BackNavigationInputTest {
    @Test
    fun `back side button invokes callback once`() = runAniComposeUiTest {
        var backCount = 0
        setContent {
            Box(
                Modifier
                    .size(100.dp)
                    .onBackNavigationInput { backCount++ }
                    .testTag("target"),
            )
        }

        onNodeWithTag("target").performMouseInput {
            click(button = MouseButton(PointerButton.Back.index))
        }

        runOnIdle {
            assertEquals(1, backCount)
        }
    }

    @Test
    fun `escape invokes callback once`() = runAniComposeUiTest {
        var backCount = 0
        val focusRequester = FocusRequester()
        setContent {
            Box(
                Modifier
                    .size(100.dp)
                    .onBackNavigationInput { backCount++ }
                    .focusRequester(focusRequester)
                    .focusable()
                    .testTag("target"),
            )
        }
        runOnIdle {
            focusRequester.requestFocus()
        }

        onNodeWithTag("target")
            .assertIsFocused()
            .performKeyInput { pressKey(Key.Escape) }

        runOnIdle {
            assertEquals(1, backCount)
        }
    }

    /**
     * 子组件 (或在 key down 时就关闭的子窗口, 例如图片查看器窗口) 消费了 Escape 的 key down 后,
     * 只剩 key up 冒泡上来, 不能触发返回.
     */
    @Test
    fun `escape key up without seeing key down does not invoke callback`() = runAniComposeUiTest {
        var backCount = 0
        val focusRequester = FocusRequester()
        setContent {
            Box(
                Modifier
                    .size(100.dp)
                    .onBackNavigationInput { backCount++ },
            ) {
                Box(
                    Modifier
                        .size(50.dp)
                        .onKeyEvent { it.key == Key.Escape && it.type == KeyEventType.KeyDown }
                        .focusRequester(focusRequester)
                        .focusable()
                        .testTag("child"),
                )
            }
        }
        runOnIdle {
            focusRequester.requestFocus()
        }

        onNodeWithTag("child")
            .assertIsFocused()
            .performKeyInput { pressKey(Key.Escape) }

        runOnIdle {
            assertEquals(0, backCount)
        }
    }

    @Test
    fun `other mouse buttons do not invoke callback`() = runAniComposeUiTest {
        var backCount = 0
        setContent {
            Box(
                Modifier
                    .size(100.dp)
                    .onBackNavigationInput { backCount++ }
                    .testTag("target"),
            )
        }

        onNodeWithTag("target").performMouseInput {
            click(button = MouseButton.Primary)
            click(button = MouseButton.Secondary)
            click(button = MouseButton.Tertiary)
            click(button = MouseButton(PointerButton.Forward.index))
        }

        runOnIdle {
            assertEquals(0, backCount)
        }
    }
}
