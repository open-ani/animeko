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
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import kotlinx.coroutines.runBlocking
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.framework.runAniComposeUiTest
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.bangumi_merge_conflict_notification
import me.him188.ani.app.ui.lang.bangumi_merge_conflict_notification_action
import me.him188.ani.app.ui.user.SelfInfoUiState
import me.him188.ani.app.ui.user.TestSelfInfoUiState
import me.him188.ani.utils.platform.annotations.TestOnly
import org.jetbrains.compose.resources.getString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * 主界面 Bangumi 冲突提示 snackbar 的交互测试: 无状态部分 + 接入真实 [me.him188.ani.app.domain.bangumi.BangumiConflictChecker] 的有状态部分.
 */
@OptIn(TestOnly::class)
class BangumiConflictNotifierTest {
    private val now = Instant.fromEpochMilliseconds(1_753_000_000_000)

    // region 无状态

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

    // endregion

    // region 有状态: 接入 checker

    @Test
    fun `NOTIFY-UI-06 会话有效且已绑定时触发检查并展示冲突数`() = runAniComposeUiTest {
        val message = runBlocking { getString(Lang.bangumi_merge_conflict_notification, 6) }
        val repository = FakeBangumiMergeRepository({ createTestBangumiMergeState(now) })
        val checker = createTestConflictChecker(repository)
        setContent {
            ProvideCompositionLocalsForPreview {
                Box(Modifier.fillMaxSize()) {
                    BangumiConflictNotifier(
                        selfInfo = TestSelfInfoUiState,
                        onNavigateToMerge = {},
                        checker = checker,
                    )
                }
            }
        }

        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText(message).fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText(message).assertIsDisplayed()
        assertEquals(6, checker.conflictCount.value)
        // 重组不会重复检查 (checker 节流).
        assertEquals(1, repository.summaryCalls)
    }

    @Test
    fun `NOTIFY-UI-07 点击处理 导航并关闭提示 但不清空计数`() = runAniComposeUiTest {
        val message = runBlocking { getString(Lang.bangumi_merge_conflict_notification, 6) }
        val actionLabel = runBlocking { getString(Lang.bangumi_merge_conflict_notification_action) }
        val repository = FakeBangumiMergeRepository({ createTestBangumiMergeState(now) })
        val checker = createTestConflictChecker(repository)
        var navigated = false
        setContent {
            ProvideCompositionLocalsForPreview {
                Box(Modifier.fillMaxSize()) {
                    BangumiConflictNotifier(
                        selfInfo = TestSelfInfoUiState,
                        onNavigateToMerge = { navigated = true },
                        checker = checker,
                    )
                }
            }
        }

        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText(message).fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText(actionLabel).performClick()
        runOnIdle {
            assertTrue(navigated)
            // 计数保留: 用户不处理直接返回时设置入口仍显示数量; 再提示由 dismissed (以计数为 key) 抑制.
            assertEquals(6, checker.conflictCount.value)
        }
        onNodeWithText(message).assertDoesNotExist()
    }

    @Test
    fun `NOTIFY-UI-08 会话失效时 reset 且不展示`() = runAniComposeUiTest {
        val message = runBlocking { getString(Lang.bangumi_merge_conflict_notification, 6) }
        val repository = FakeBangumiMergeRepository({ createTestBangumiMergeState(now) })
        val checker = createTestConflictChecker(repository)
        var selfInfo by mutableStateOf(TestSelfInfoUiState)
        setContent {
            ProvideCompositionLocalsForPreview {
                Box(Modifier.fillMaxSize()) {
                    BangumiConflictNotifier(
                        selfInfo = selfInfo,
                        onNavigateToMerge = {},
                        checker = checker,
                    )
                }
            }
        }

        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText(message).fetchSemanticsNodes().isNotEmpty() }

        // 登出: 提示关闭, checker 清空.
        selfInfo = selfInfo.copy(isSessionValid = false)
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithText(message).fetchSemanticsNodes().isEmpty() }
        runOnIdle { assertEquals(0, checker.conflictCount.value) }
    }

    @Test
    fun `NOTIFY-UI-09 未绑定 Bangumi 时不检查`() = runAniComposeUiTest {
        val repository = FakeBangumiMergeRepository({ createTestBangumiMergeState(now) })
        val checker = createTestConflictChecker(repository)
        setContent {
            ProvideCompositionLocalsForPreview {
                Box(Modifier.fillMaxSize()) {
                    BangumiConflictNotifier(
                        selfInfo = TestSelfInfoUiState.copy(bangumiConnected = false),
                        onNavigateToMerge = {},
                        checker = checker,
                    )
                }
            }
        }

        waitForIdle()
        // 若错误地启动了检查, 它跑在 Default 线程上: 先等它结束再做否定断言, 否则断言可能在计数增加前就通过.
        runBlocking { checker.joinCheck() }
        runOnIdle {
            assertEquals(0, repository.summaryCalls)
            assertEquals(0, checker.conflictCount.value)
        }
    }

    @Test
    fun `NOTIFY-UI-10 会话加载中不 reset 不检查`() = runAniComposeUiTest {
        val repository = FakeBangumiMergeRepository({ createTestBangumiMergeState(now) })
        val checker = createTestConflictChecker(repository)
        val loading: SelfInfoUiState = TestSelfInfoUiState.copy(isSessionValid = null, bangumiConnected = null)
        setContent {
            ProvideCompositionLocalsForPreview {
                Box(Modifier.fillMaxSize()) {
                    BangumiConflictNotifier(
                        selfInfo = loading,
                        onNavigateToMerge = {},
                        checker = checker,
                    )
                }
            }
        }

        waitForIdle()
        runBlocking { checker.joinCheck() }
        runOnIdle { assertEquals(0, repository.summaryCalls) }
    }

    // endregion
}
