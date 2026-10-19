/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.subject.episode

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertLeftPositionInRootIsEqualTo
import androidx.compose.ui.test.assertTopPositionInRootIsEqualTo
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import me.him188.ani.app.ui.framework.doesNotExist
import me.him188.ani.app.ui.framework.runAniComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EpisodeScreenLayoutTest {
    private var mode by mutableStateOf(EpisodeScreenLayoutMode.COMPACT)
    private var videoEnterCount = 0
    private var videoLeaveCount = 0

    @Composable
    private fun Content() {
        EpisodeScreenLayout(
            mode,
            video = {
                DisposableEffect(Unit) {
                    videoEnterCount++
                    onDispose { videoLeaveCount++ }
                }
                Box(Modifier.fillMaxWidth().height(VIDEO_HEIGHT).testTag(TAG_VIDEO))
            },
            secondary = {
                Box(Modifier.fillMaxSize().testTag(TAG_SECONDARY))
            },
            Modifier.requiredSize(WIDTH, HEIGHT),
        )
    }

    @Test
    fun `video is kept when switching modes`() = runAniComposeUiTest {
        setContent { Content() }

        for (next in listOf(
            EpisodeScreenLayoutMode.VIDEO_ONLY,
            EpisodeScreenLayoutMode.WIDE,
            EpisodeScreenLayoutMode.COMPACT,
            EpisodeScreenLayoutMode.WIDE,
            EpisodeScreenLayoutMode.VIDEO_ONLY,
            EpisodeScreenLayoutMode.COMPACT,
        )) {
            mode = next
            waitForIdle()
        }

        runOnIdle {
            assertEquals(1, videoEnterCount)
            assertEquals(0, videoLeaveCount)
        }
    }

    @Test
    fun `compact places secondary below video`() = runAniComposeUiTest {
        mode = EpisodeScreenLayoutMode.COMPACT
        setContent { Content() }

        onNodeWithTag(TAG_VIDEO).assertBounds(0.dp, 0.dp, WIDTH, VIDEO_HEIGHT)
        onNodeWithTag(TAG_SECONDARY).assertBounds(0.dp, VIDEO_HEIGHT, WIDTH, HEIGHT - VIDEO_HEIGHT)
    }

    @Test
    fun `wide places sidebar on the right`() = runAniComposeUiTest {
        mode = EpisodeScreenLayoutMode.WIDE
        setContent { Content() }

        // 宽度的 1/4 为 250dp, 不足侧边栏最小宽度 340dp
        val sidebarWidth = 340.dp
        onNodeWithTag(TAG_VIDEO).assertBounds(0.dp, 0.dp, WIDTH - sidebarWidth, HEIGHT)
        onNodeWithTag(TAG_SECONDARY).assertBounds(WIDTH - sidebarWidth, 0.dp, sidebarWidth, HEIGHT)
    }

    @Test
    fun `mobile fullscreen hides secondary content regardless of sidebar preference`() {
        for (expanded in listOf(false, true)) {
            for (sidebarVisible in listOf(false, true)) {
                assertEquals(
                    EpisodeScreenLayoutMode.VIDEO_ONLY,
                    episodeScreenLayoutMode(true, expanded, sidebarVisible, isDesktop = false),
                )
            }
        }
    }

    @Test
    fun `compact desktop fullscreen only shows video`() {
        for (sidebarVisible in listOf(false, true)) {
            assertEquals(
                EpisodeScreenLayoutMode.VIDEO_ONLY,
                episodeScreenLayoutMode(true, false, sidebarVisible, isDesktop = true),
            )
        }
    }

    @Test
    fun `video only fills the layout without secondary`() = runAniComposeUiTest {
        mode = EpisodeScreenLayoutMode.VIDEO_ONLY
        setContent { Content() }

        onNodeWithTag(TAG_VIDEO).assertBounds(0.dp, 0.dp, WIDTH, HEIGHT)
        assertTrue { onNodeWithTag(TAG_SECONDARY).doesNotExist() }
    }

    private fun SemanticsNodeInteraction.assertBounds(left: Dp, top: Dp, width: Dp, height: Dp) {
        assertLeftPositionInRootIsEqualTo(left)
        assertTopPositionInRootIsEqualTo(top)
        assertWidthIsEqualTo(width)
        assertHeightIsEqualTo(height)
    }

    private companion object {
        const val TAG_VIDEO = "video"
        const val TAG_SECONDARY = "secondary"
        val WIDTH = 1000.dp
        val HEIGHT = 600.dp
        val VIDEO_HEIGHT = 225.dp
    }
}
