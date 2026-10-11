/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.subject.episode.list

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.framework.runAniComposeUiTest
import me.him188.ani.datasources.api.EpisodeSort
import me.him188.ani.utils.platform.annotations.TestOnly
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(TestOnly::class, ExperimentalTestApi::class)
class EpisodeListDialogTest {
    /**
     * 点击剧集会跳转到播放页, 对话框必须在跳转前先请求关闭.
     * 调用方用 rememberSaveable 记住对话框是否打开, 不关闭的话这个状态会留在返回栈里,
     * 播放页做预测性返回手势时下层页面预览会重新弹出对话框, 系统随即取消手势. #3573
     */
    @Test
    fun `clicking an episode requests dismiss before invoking onEpisodeClick`() = runAniComposeUiTest {
        val events = mutableListOf<String>()
        var clicked: EpisodeListItem? = null
        setContent {
            ProvideCompositionLocalsForPreview {
                EpisodeListDialog(
                    TestEpisodeListUiState,
                    onDismissRequest = { events += "dismiss" },
                    onCacheClick = {},
                    onEpisodeClick = {
                        events += "click"
                        clicked = it
                    },
                    onCollectionUpdate = {},
                )
            }
        }

        onNodeWithText("03").performClick()

        assertEquals(listOf("dismiss", "click"), events)
        assertEquals(EpisodeSort(3), clicked?.sort)
    }
}
