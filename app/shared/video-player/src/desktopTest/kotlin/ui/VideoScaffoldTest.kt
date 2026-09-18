/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.videoplayer.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.remember
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

class VideoScaffoldTest {
    @Test
    fun `vertical split keeps media above the fold and controls below it`() = runAniComposeUiTest {
        setContent {
            ProvideCompositionLocalsForPreview {
                Box(Modifier.size(width = 400.dp, height = 800.dp)) {
                    VideoScaffold(
                        expanded = true,
                        layout = VideoScaffoldLayout.VerticalSplit,
                        modifier = Modifier.fillMaxSize().testTag(TAG_SCAFFOLD),
                        contentWindowInsets = WindowInsets(0.dp),
                        controllerState = remember { PlayerControllerState(ControllerVisibility.Visible) },
                        video = {
                            Box(Modifier.fillMaxSize().testTag(TAG_VIDEO))
                        },
                        gestureHost = {
                            Box(Modifier.fillMaxSize().testTag(TAG_GESTURES))
                        },
                        bottomBar = {
                            Box(Modifier.fillMaxWidth().height(48.dp).testTag(TAG_BOTTOM_BAR))
                        },
                    )
                }
            }
        }

        val scaffoldBounds = boundsOf(TAG_SCAFFOLD)
        val videoBounds = boundsOf(TAG_VIDEO)
        val gestureBounds = boundsOf(TAG_GESTURES)
        val bottomBarBounds = boundsOf(TAG_BOTTOM_BAR)

        assertEquals(scaffoldBounds.top, videoBounds.top, absoluteTolerance = 1f)
        assertEquals(scaffoldBounds.height / 2f, videoBounds.height, absoluteTolerance = 1f)
        assertEquals(scaffoldBounds, gestureBounds)
        assertTrue(bottomBarBounds.top >= scaffoldBounds.center.y)
    }

    @Test
    fun `overlay layout keeps media full size`() = runAniComposeUiTest {
        setContent {
            ProvideCompositionLocalsForPreview {
                Box(Modifier.size(width = 400.dp, height = 800.dp)) {
                    VideoScaffold(
                        expanded = true,
                        modifier = Modifier.fillMaxSize().testTag(TAG_SCAFFOLD),
                        contentWindowInsets = WindowInsets(0.dp),
                        controllerState = remember { PlayerControllerState(ControllerVisibility.Invisible) },
                        video = {
                            Box(Modifier.fillMaxSize().testTag(TAG_VIDEO))
                        },
                    )
                }
            }
        }

        assertEquals(boundsOf(TAG_SCAFFOLD), boundsOf(TAG_VIDEO))
    }

    private fun SemanticsNodeInteractionsProvider.boundsOf(tag: String) =
        onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot

    private companion object {
        const val TAG_SCAFFOLD = "VideoScaffold"
        const val TAG_VIDEO = "Video"
        const val TAG_GESTURES = "Gestures"
        const val TAG_BOTTOM_BAR = "BottomBar"
    }
}
