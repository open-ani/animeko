/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.videoplayer.ui.progress

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.framework.runAniComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PlayerControllerBarTest {
    @Test
    fun `vertical split arranges existing controller slots for the lower pane`() = runAniComposeUiTest {
        setContent {
            ProvideCompositionLocalsForPreview {
                Box(Modifier.size(width = 400.dp, height = 400.dp)) {
                    PlayerControllerBar(
                        startActions = {
                            Box(Modifier.offset(x = 36.dp).size(56.dp).testTag(TAG_START_ACTIONS))
                            Box(Modifier.offset(x = 36.dp).size(56.dp).testTag(TAG_NEXT_ACTION))
                        },
                        progressIndicator = {
                            Box(Modifier.size(width = 80.dp, height = 20.dp).testTag(TAG_PROGRESS_INDICATOR))
                        },
                        progressSlider = {
                            Box(Modifier.fillMaxWidth().height(24.dp).testTag(TAG_PROGRESS_SLIDER))
                        },
                        danmakuEditor = {
                            Box(Modifier.size(40.dp).testTag(TAG_DANMAKU_TOGGLE))
                            Box(Modifier.size(width = 280.dp, height = 40.dp).testTag(TAG_DANMAKU_EDITOR))
                        },
                        endActions = {
                            Box(Modifier.size(width = 120.dp, height = 40.dp).testTag(TAG_END_ACTIONS))
                        },
                        expanded = true,
                        topActions = {
                            Box(Modifier.size(width = 96.dp, height = 40.dp).testTag(TAG_TOP_ACTIONS))
                        },
                        layout = PlayerControllerBarLayout.VerticalSplit,
                        modifier = Modifier.fillMaxSize().testTag(TAG_CONTROLLER),
                    )
                }
            }
        }

        val controllerBounds = boundsOf(TAG_CONTROLLER)
        val topActionsBounds = boundsOf(TAG_TOP_ACTIONS)
        val startActionsBounds = boundsOf(TAG_START_ACTIONS)
        val nextActionBounds = boundsOf(TAG_NEXT_ACTION)
        val danmakuToggleBounds = boundsOf(TAG_DANMAKU_TOGGLE)
        val danmakuEditorBounds = boundsOf(TAG_DANMAKU_EDITOR)
        val progressSliderBounds = boundsOf(TAG_PROGRESS_SLIDER)
        val progressIndicatorBounds = boundsOf(TAG_PROGRESS_INDICATOR)
        val endActionsBounds = boundsOf(TAG_END_ACTIONS)

        assertTrue(topActionsBounds.right <= controllerBounds.right)
        assertTrue(topActionsBounds.bottom <= startActionsBounds.top)
        assertEquals(controllerBounds.center.x, startActionsBounds.center.x, absoluteTolerance = 1f)
        assertEquals(16f, nextActionBounds.left - startActionsBounds.right, absoluteTolerance = 1f)
        assertTrue(startActionsBounds.bottom <= danmakuEditorBounds.top)
        assertTrue(danmakuToggleBounds.right <= danmakuEditorBounds.left)
        assertEquals(danmakuToggleBounds.center.y, danmakuEditorBounds.center.y, absoluteTolerance = 1f)
        assertTrue(progressSliderBounds.top - danmakuEditorBounds.bottom >= 12f)
        assertTrue(progressSliderBounds.bottom <= progressIndicatorBounds.top)
        assertTrue(progressIndicatorBounds.right <= endActionsBounds.left)
        assertTrue(endActionsBounds.bottom <= controllerBounds.bottom)
    }

    private fun SemanticsNodeInteractionsProvider.boundsOf(tag: String) =
        onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot

    private companion object {
        const val TAG_CONTROLLER = "Controller"
        const val TAG_TOP_ACTIONS = "TopActions"
        const val TAG_START_ACTIONS = "StartActions"
        const val TAG_NEXT_ACTION = "NextAction"
        const val TAG_PROGRESS_INDICATOR = "ProgressIndicator"
        const val TAG_PROGRESS_SLIDER = "ProgressSlider"
        const val TAG_DANMAKU_TOGGLE = "DanmakuToggle"
        const val TAG_DANMAKU_EDITOR = "DanmakuEditor"
        const val TAG_END_ACTIONS = "EndActions"
    }
}
