/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.watchtogether

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.onNodeWithTag
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.framework.exists
import me.him188.ani.app.ui.framework.runAniComposeUiTest
import kotlin.test.Test

class WatchTogetherOverlayContentTest {
    /**
     * 系统小窗展示整个应用窗口, 应用级浮层不随播放器页最小化, 因此小窗期间不绘制气泡与对话框.
     */
    @Test
    fun `popup and dialog are not drawn in picture in picture`() = runAniComposeUiTest {
        var isInPictureInPicture by mutableStateOf(false)
        val playerController = WatchTogetherPlayerController()
        setContent {
            ProvideCompositionLocalsForPreview {
                CompositionLocalProvider(LocalWatchTogetherPlayerController provides playerController) {
                    Box {
                        WatchTogetherOverlayContent(
                            state = WatchTogetherUiState.Initial.copy(featureEnabled = true),
                            isInPictureInPicture = isInPictureInPicture,
                            bubblePositionState = rememberDraggableBubblePositionState(),
                            toastHostState = remember { SnackbarHostState() },
                            onBubbleClick = {},
                            dialogVisible = true,
                            onIntent = {},
                            onLogin = {},
                            onDismissRequest = {},
                        )
                    }
                }
            }
        }

        onNodeWithTag(WATCH_TOGETHER_BUBBLE_TEST_TAG).assertExists()
        onNodeWithTag(WATCH_TOGETHER_DIALOG_TEST_TAG).assertExists()

        runOnIdle { isInPictureInPicture = true }

        waitUntil { !onNodeWithTag(WATCH_TOGETHER_BUBBLE_TEST_TAG).exists() }
        onNodeWithTag(WATCH_TOGETHER_DIALOG_TEST_TAG).assertDoesNotExist()

        // 退出小窗后恢复原状, 用户原本打开着的对话框不会丢失
        runOnIdle { isInPictureInPicture = false }

        waitUntil { onNodeWithTag(WATCH_TOGETHER_BUBBLE_TEST_TAG).exists() }
        onNodeWithTag(WATCH_TOGETHER_DIALOG_TEST_TAG).assertExists()
    }
}
