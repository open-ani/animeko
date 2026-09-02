/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.bangumi.merge

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithText
import kotlinx.coroutines.runBlocking
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.framework.runAniComposeUiTest
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.bangumi_merge_conflict_notification
import me.him188.ani.app.ui.main.BottomNotifierStack
import me.him188.ani.utils.platform.annotations.TestOnly
import org.jetbrains.compose.resources.getString
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * 主界面底部的通知堆叠 ([BottomNotifierStack]): 更新提示的 snackbar 与 Bangumi 冲突提示的 snackbar 各自持有 host,
 * 两者都是 Indefinite, 必须竖向堆叠而不是都锚在 BottomCenter 互相遮挡.
 */
@OptIn(TestOnly::class)
class BangumiConflictNotifierStackTest {
    private companion object {
        const val UPDATE_MESSAGE = "有新版本 5.0.0 可用"
    }

    @Test
    fun `NOTIFY-UI-11 冲突提示与更新提示的 snackbar 竖向堆叠, 不重叠`() = runAniComposeUiTest {
        val conflictMessage = runBlocking { getString(Lang.bangumi_merge_conflict_notification, 3) }
        setContent {
            ProvideCompositionLocalsForPreview {
                Box(Modifier.fillMaxSize()) {
                    BottomNotifierStack(
                        Modifier.matchParentSize(),
                        top = {
                            // 手机上的更新提示: 自己的 host, Indefinite
                            val hostState = remember { SnackbarHostState() }
                            LaunchedEffect(hostState) {
                                hostState.showSnackbar(UPDATE_MESSAGE, duration = SnackbarDuration.Indefinite)
                            }
                            SnackbarHost(hostState, Modifier.align(Alignment.BottomCenter))
                        },
                        bottom = {
                            BangumiConflictNotifierContent(
                                conflictCount = 3,
                                onResolveClick = {},
                            )
                        },
                    )
                }
            }
        }

        onNodeWithText(UPDATE_MESSAGE).assertIsDisplayed()
        onNodeWithText(conflictMessage).assertIsDisplayed()
        val update = onNodeWithText(UPDATE_MESSAGE).getBoundsInRoot()
        val conflict = onNodeWithText(conflictMessage).getBoundsInRoot()
        // 更新提示在上, 冲突提示在下, 两者不相交
        assertTrue(
            update.bottom <= conflict.top,
            "update snackbar $update should be entirely above conflict snackbar $conflict",
        )
    }
}
