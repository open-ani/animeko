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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.framework.runAniComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 底部控制栏之上的元素 (左下提示、截图面板) 由框架统一避让: 控制栏可见时抬到它上方, 隐藏后回到边距处.
 */
class VideoScaffoldBottomRegionTest {
    @Test
    fun `left bottom tips and screenshot overlay avoid the bottom controller and follow when it hides`() =
        runAniComposeUiTest {
            val controllerState = PlayerControllerState(ControllerVisibility.Visible)
            var reportedBottomControllerHeight = Dp.Unspecified
            setContent {
                ProvideCompositionLocalsForPreview {
                    VideoScaffold(
                        expanded = true,
                        modifier = Modifier.size(640.dp, 360.dp).testTag(TAG_SCAFFOLD),
                        contentWindowInsets = WindowInsets(0),
                        controllerState = controllerState,
                        bottomBar = { Box(Modifier.fillMaxWidth().height(40.dp).testTag(TAG_BOTTOM_BAR)) },
                        leftBottomTips = { Box(Modifier.size(120.dp, 32.dp).testTag(TAG_TIPS)) },
                        screenshotOverlay = { bottomControllerHeight ->
                            reportedBottomControllerHeight = bottomControllerHeight
                        },
                    )
                }
            }
            waitForIdle()

            val scaffold = onNodeWithTag(TAG_SCAFFOLD).getBoundsInRoot()
            val bar = onNodeWithTag(TAG_BOTTOM_BAR).getBoundsInRoot()
            val tips = onNodeWithTag(TAG_TIPS).getBoundsInRoot()
            // 全屏时控制栏本体上方还有 12dp 的留白, 一并算作控制栏占用的高度
            assertDp(bar.top - 12.dp, scaffold.bottom - reportedBottomControllerHeight)
            assertDp(scaffold.left + 16.dp, tips.left)
            assertDp(scaffold.bottom - reportedBottomControllerHeight - 16.dp, tips.bottom)

            controllerState.toggleFullVisible(false)
            waitForIdle()

            onNodeWithTag(TAG_BOTTOM_BAR).assertDoesNotExist()
            assertDp(0.dp, reportedBottomControllerHeight)
            val dropped = onNodeWithTag(TAG_TIPS).getBoundsInRoot()
            assertDp(scaffold.left + 16.dp, dropped.left)
            assertDp(scaffold.bottom - 16.dp, dropped.bottom)
        }

    private fun assertDp(expected: Dp, actual: Dp) {
        assertEquals(expected.value, actual.value, 0.5f)
    }

    private companion object {
        const val TAG_SCAFFOLD = "scaffold"
        const val TAG_BOTTOM_BAR = "bottomBar"
        const val TAG_TIPS = "tips"
    }
}
