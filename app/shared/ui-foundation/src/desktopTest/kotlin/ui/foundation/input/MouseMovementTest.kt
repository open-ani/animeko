/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.foundation.input

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import me.him188.ani.app.ui.framework.AniComposeUiTest
import me.him188.ani.app.ui.framework.runAniComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class MouseMovementTest {
    private var movements = 0
    private var large by mutableStateOf(false)

    private fun AniComposeUiTest.setHost() {
        setContent {
            Box(Modifier.size(300.dp).testTag(HOST).onMouseMoved { movements++ }) {
                // 改变大小来触发重新布局
                Box(Modifier.size(if (large) 200.dp else 20.dp))
            }
        }
        waitForIdle()
    }

    private fun AniComposeUiTest.host() = onNodeWithTag(HOST)

    @Test
    fun `moving the mouse is a movement`() = runAniComposeUiTest {
        setHost()
        host().performMouseInput {
            moveTo(center)
            moveBy(Offset(10f, 0f))
            moveBy(Offset(0f, 10f))
        }
        waitForIdle()
        assertEquals(2, movements)
    }

    @Test
    fun `entering is not a movement`() = runAniComposeUiTest {
        setHost()
        host().performMouseInput { moveTo(center) }
        waitForIdle()
        assertEquals(0, movements)
    }

    @Test
    fun `layout change under a resting mouse is not a movement`() = runAniComposeUiTest {
        setHost()
        host().performMouseInput { moveTo(center) }
        waitForIdle()

        large = true
        waitForIdle()
        large = false
        waitForIdle()
        assertEquals(0, movements)

        // 对照: 之后真的移动鼠标
        host().performMouseInput { moveBy(Offset(10f, 0f)) }
        waitForIdle()
        assertEquals(1, movements)
    }

    @Test
    fun `entering again at another position is not a movement`() = runAniComposeUiTest {
        setHost()
        host().performMouseInput {
            enter(center)
            exit(center)
            enter(center + Offset(50f, 0f))
        }
        waitForIdle()
        assertEquals(0, movements)
    }

    @Test
    fun `touch is not a mouse movement`() = runAniComposeUiTest {
        setHost()
        host().performTouchInput {
            down(center)
            moveBy(Offset(30f, 0f))
            up()
        }
        waitForIdle()
        assertEquals(0, movements)
    }

    private companion object {
        const val HOST = "host"
    }
}
