/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.videoplayer.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.framework.doesNotExist
import me.him188.ani.app.ui.framework.exists
import me.him188.ani.app.ui.framework.runAniComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class VideoScaffoldVideoOnlyTest {
    @Test
    fun `switching video only keeps video and hides other layers`() = runAniComposeUiTest {
        var videoOnly by mutableStateOf(false)
        var videoEnterCount = 0
        var videoLeaveCount = 0
        val controllerState = PlayerControllerState(ControllerVisibility.Visible)

        setContent {
            ProvideCompositionLocalsForPreview {
                VideoScaffold(
                    expanded = true,
                    modifier = Modifier.size(640.dp, 360.dp),
                    videoOnly = videoOnly,
                    controllerState = controllerState,
                    topBar = { Box(Modifier.size(10.dp).testTag(TAG_TOP_BAR)) },
                    video = {
                        DisposableEffect(Unit) {
                            videoEnterCount++
                            onDispose { videoLeaveCount++ }
                        }
                        Box(Modifier.size(10.dp).testTag(TAG_VIDEO))
                    },
                )
            }
        }
        assertTrue { onNodeWithTag(TAG_TOP_BAR).exists() }

        videoOnly = true
        waitForIdle()
        assertTrue { onNodeWithTag(TAG_VIDEO).exists() }
        assertTrue { onNodeWithTag(TAG_TOP_BAR).doesNotExist() }

        videoOnly = false
        waitForIdle()
        assertTrue { onNodeWithTag(TAG_TOP_BAR).exists() }

        runOnIdle {
            assertEquals(1, videoEnterCount)
            assertEquals(0, videoLeaveCount)
        }
    }

    private companion object {
        const val TAG_VIDEO = "video"
        const val TAG_TOP_BAR = "topBar"
    }
}
