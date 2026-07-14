/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.videoplayer.ui.gesture

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import me.him188.ani.app.ui.framework.runAniComposeUiTest
import me.him188.ani.app.videoplayer.ui.gesture.SwipeSeekerState.Companion.swipeToSeek
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class SwipeToSeekTest {
    @Test
    fun `release in top corner cancels seek`() = runAniComposeUiTest {
        val seeks = mutableListOf<Int>()
        setContent {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val state = rememberSwipeSeekerState(constraints.maxWidth, onSeek = seeks::add)
                Box(
                    Modifier.fillMaxSize()
                        .testTag("swipeTarget")
                        .swipeToSeek(state, Orientation.Horizontal),
                )
            }
        }

        onNodeWithTag("swipeTarget").performTouchInput {
            down(center)
            moveTo(Offset(width * 0.4f, height * 0.5f))
            moveTo(Offset(width * 0.1f, height * 0.1f))
            up()
        }

        assertEquals(emptyList(), seeks)
    }
}
