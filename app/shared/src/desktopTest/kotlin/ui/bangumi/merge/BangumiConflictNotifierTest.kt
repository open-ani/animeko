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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import kotlinx.coroutines.runBlocking
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.framework.runAniComposeUiTest
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.bangumi_merge_conflict_notification
import me.him188.ani.app.ui.lang.bangumi_merge_conflict_notification_action
import org.jetbrains.compose.resources.getString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 主界面 Bangumi 冲突提示 snackbar 的交互测试.
 */
class BangumiConflictNotifierTest {

    @Test
    fun `NOTIFY-UI-01 有冲突时展示提示与动作`() = runAniComposeUiTest {
        val message = runBlocking { getString(Lang.bangumi_merge_conflict_notification, 6) }
        val actionLabel = runBlocking { getString(Lang.bangumi_merge_conflict_notification_action) }
        setContent {
            ProvideCompositionLocalsForPreview {
                Box(Modifier.fillMaxSize()) {
                    BangumiConflictNotifierContent(
                        conflictCount = 6,
                        onResolveClick = {},
                    )
                }
            }
        }

        onNodeWithText(message).assertIsDisplayed()
        onNodeWithText(actionLabel).assertIsDisplayed()
    }

    @Test
    fun `NOTIFY-UI-02 点击动作触发导航回调`() = runAniComposeUiTest {
        val actionLabel = runBlocking { getString(Lang.bangumi_merge_conflict_notification_action) }
        var navigated = false
        setContent {
            ProvideCompositionLocalsForPreview {
                Box(Modifier.fillMaxSize()) {
                    BangumiConflictNotifierContent(
                        conflictCount = 3,
                        onResolveClick = { navigated = true },
                    )
                }
            }
        }

        onNodeWithText(actionLabel).performClick()
        runOnIdle { assertTrue(navigated) }
    }

    @Test
    fun `NOTIFY-UI-03 无冲突时不展示提示`() = runAniComposeUiTest {
        val message = runBlocking { getString(Lang.bangumi_merge_conflict_notification, 0) }
        setContent {
            ProvideCompositionLocalsForPreview {
                Box(Modifier.fillMaxSize()) {
                    BangumiConflictNotifierContent(
                        conflictCount = 0,
                        onResolveClick = {},
                    )
                }
            }
        }

        onNodeWithText(message).assertDoesNotExist()
    }

    @Test
    fun `NOTIFY-UI-04 点击动作后提示关闭 同计数重组不重新出现`() = runAniComposeUiTest {
        val message = runBlocking { getString(Lang.bangumi_merge_conflict_notification, 3) }
        val actionLabel = runBlocking { getString(Lang.bangumi_merge_conflict_notification_action) }
        var clicks = 0
        var unrelatedState by mutableStateOf(0)
        setContent {
            ProvideCompositionLocalsForPreview {
                Box(Modifier.fillMaxSize()) {
                    // 读取无关状态, 使其变化时触发重组.
                    Box(Modifier.testTag("recomposeProbe$unrelatedState"))
                    BangumiConflictNotifierContent(
                        conflictCount = 3,
                        onResolveClick = { clicks++ },
                    )
                }
            }
        }

        onNodeWithText(actionLabel).performClick()
        runOnIdle { assertEquals(1, clicks) }
        onNodeWithText(message).assertDoesNotExist()

        // 计数不变时, 无关重组不会让提示重新出现.
        unrelatedState = 1
        waitForIdle()
        onNodeWithText(message).assertDoesNotExist()
    }

    @Test
    fun `NOTIFY-UI-05 冲突数变化后重新提示`() = runAniComposeUiTest {
        val message3 = runBlocking { getString(Lang.bangumi_merge_conflict_notification, 3) }
        val message5 = runBlocking { getString(Lang.bangumi_merge_conflict_notification, 5) }
        val actionLabel = runBlocking { getString(Lang.bangumi_merge_conflict_notification_action) }
        var count by mutableStateOf(3)
        setContent {
            ProvideCompositionLocalsForPreview {
                Box(Modifier.fillMaxSize()) {
                    BangumiConflictNotifierContent(
                        conflictCount = count,
                        onResolveClick = {},
                    )
                }
            }
        }

        // 关闭当前提示 (点击动作).
        onNodeWithText(actionLabel).performClick()
        onNodeWithText(message3).assertDoesNotExist()

        // 冲突数变化: dismissed 以计数为 key, 重新提示.
        count = 5
        waitForIdle()
        onNodeWithText(message5).assertIsDisplayed()
    }
}
