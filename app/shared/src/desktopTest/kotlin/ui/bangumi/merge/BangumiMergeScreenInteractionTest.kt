/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.bangumi.merge

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import kotlinx.coroutines.runBlocking
import me.him188.ani.app.domain.bangumi.merge.BangumiMergeConflictKey
import me.him188.ani.app.domain.bangumi.merge.BangumiMergeFieldId
import me.him188.ani.app.domain.bangumi.merge.BangumiMergePlan
import me.him188.ani.app.domain.bangumi.merge.BangumiMergeSide
import me.him188.ani.app.domain.bangumi.merge.conflictKeys
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.framework.runAniComposeUiTest
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.bangumi_merge_auto_merged
import me.him188.ani.app.ui.lang.bangumi_merge_confirmed_progress
import me.him188.ani.app.ui.lang.bangumi_merge_deleted_collection
import me.him188.ani.app.ui.lang.bangumi_merge_synced
import me.him188.ani.utils.platform.annotations.TestOnly
import org.jetbrains.compose.resources.getString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * 合并收藏界面的交互测试: 单元格选择 / 全选 / 采用较新的 / 应用门控.
 */
@OptIn(TestOnly::class)
class BangumiMergeScreenInteractionTest {
    private val now = Instant.fromEpochMilliseconds(1_753_000_000_000)

    @Test
    fun `UI-01 点击单元格选择一侧并更新进度`() = runAniComposeUiTest {
        val progressText3 = runBlocking { getString(Lang.bangumi_merge_confirmed_progress, 3, 6) }
        val progressText4 = runBlocking { getString(Lang.bangumi_merge_confirmed_progress, 4, 6) }

        var state by mutableStateOf(createTestBangumiMergeUiState(now))
        setContent {
            ProvideCompositionLocalsForPreview {
                BangumiMergeScreen(
                    state = state,
                    onSelect = { key, side -> state = state.copy(choices = state.choices + (key to side)) },
                    onAdoptNewer = {},
                    onSelectAll = {},
                    onApply = {},
                    onRetry = {},
                    onNavigateBack = {},
                    layoutParams = BangumiMergeLayoutParams.Compact,
                    getTimeNow = { now },
                )
            }
        }

        onNodeWithTag(BangumiMergeTestTags.PROGRESS_TEXT).assertIsDisplayed()
        onNodeWithText(progressText3).assertIsDisplayed()

        // 选择 芙莉莲(2) 的进度冲突: 点击 Bangumi 侧.
        val episodeKey = state.plan!!.conflictGroups.first { it.subjectId == 2 }
            .conflictKeys.first { it.fieldId is BangumiMergeFieldId.Episode }
        onNodeWithTag(BangumiMergeTestTags.cell(episodeKey, BangumiMergeSide.BANGUMI)).performClick()

        runOnIdle {
            assertEquals(BangumiMergeSide.BANGUMI, state.choices[episodeKey])
        }
        onNodeWithText(progressText4).assertIsDisplayed()
    }

    @Test
    fun `UI-02 未全部确认时应用按钮禁用 点击触发闪烁提示回调`() = runAniComposeUiTest {
        var state by mutableStateOf(createTestBangumiMergeUiState(now))
        var applied = false
        setContent {
            ProvideCompositionLocalsForPreview {
                BangumiMergeScreen(
                    state = state,
                    onSelect = { key, side -> state = state.copy(choices = state.choices + (key to side)) },
                    onAdoptNewer = {},
                    onSelectAll = {},
                    onApply = { applied = true },
                    onRetry = {},
                    onNavigateBack = {},
                    layoutParams = BangumiMergeLayoutParams.Compact,
                    getTimeNow = { now },
                )
            }
        }

        onNodeWithTag(BangumiMergeTestTags.APPLY_BUTTON).assertIsNotEnabled()
        // 点击禁用按钮所在区域不会触发 onApply.
        onNodeWithTag(BangumiMergeTestTags.APPLY_BLOCKED_OVERLAY).performClick()
        runOnIdle { assertEquals(false, applied) }
    }

    @Test
    fun `UI-03 全部确认后应用按钮可用并触发 onApply`() = runAniComposeUiTest {
        val base = createTestBangumiMergeUiState(now)
        val allKeys = base.plan!!.conflictGroups.flatMap { it.conflictKeys }
        var state by mutableStateOf(
            base.copy(choices = allKeys.associateWith { BangumiMergeSide.ANIMEKO }),
        )
        var applied = false
        setContent {
            ProvideCompositionLocalsForPreview {
                BangumiMergeScreen(
                    state = state,
                    onSelect = { _, _ -> },
                    onAdoptNewer = {},
                    onSelectAll = {},
                    onApply = { applied = true },
                    onRetry = {},
                    onNavigateBack = {},
                    layoutParams = BangumiMergeLayoutParams.Compact,
                    getTimeNow = { now },
                )
            }
        }

        onNodeWithTag(BangumiMergeTestTags.APPLY_BUTTON).assertIsEnabled().performClick()
        runOnIdle { assertTrue(applied) }
    }

    @Test
    fun `UI-04 列头全选触发回调`() = runAniComposeUiTest {
        var state by mutableStateOf(createTestBangumiMergeUiState(now, choices = emptyMap()))
        var selectAllSide: BangumiMergeSide? = null
        setContent {
            ProvideCompositionLocalsForPreview {
                BangumiMergeScreen(
                    state = state,
                    onSelect = { _, _ -> },
                    onAdoptNewer = {},
                    onSelectAll = { side -> selectAllSide = side },
                    onApply = {},
                    onRetry = {},
                    onNavigateBack = {},
                    layoutParams = BangumiMergeLayoutParams.Compact,
                    getTimeNow = { now },
                )
            }
        }

        onNodeWithTag(BangumiMergeTestTags.SELECT_ALL_REMOTE).performClick()
        runOnIdle { assertEquals(BangumiMergeSide.BANGUMI, selectAllSide) }

        onNodeWithTag(BangumiMergeTestTags.SELECT_ALL_LOCAL).performClick()
        runOnIdle { assertEquals(BangumiMergeSide.ANIMEKO, selectAllSide) }
    }

    @Test
    fun `UI-05 采用较新的触发回调`() = runAniComposeUiTest {
        var adopted = false
        setContent {
            ProvideCompositionLocalsForPreview {
                BangumiMergeScreen(
                    state = createTestBangumiMergeUiState(now, choices = emptyMap()),
                    onSelect = { _, _ -> },
                    onAdoptNewer = { adopted = true },
                    onSelectAll = {},
                    onApply = {},
                    onRetry = {},
                    onNavigateBack = {},
                    layoutParams = BangumiMergeLayoutParams.Compact,
                    getTimeNow = { now },
                )
            }
        }

        onNodeWithTag(BangumiMergeTestTags.ADOPT_NEWER).performClick()
        runOnIdle { assertTrue(adopted) }
    }

    @Test
    fun `UI-06 空计划展示完全同步空态`() = runAniComposeUiTest {
        val syncedText = runBlocking { getString(Lang.bangumi_merge_synced) }
        setContent {
            ProvideCompositionLocalsForPreview {
                BangumiMergeScreen(
                    state = BangumiMergeUiState.Initial.copy(
                        isLoading = false,
                        plan = BangumiMergePlan.Empty,
                    ),
                    onSelect = { _, _ -> },
                    onAdoptNewer = {},
                    onSelectAll = {},
                    onApply = {},
                    onRetry = {},
                    onNavigateBack = {},
                    layoutParams = BangumiMergeLayoutParams.Compact,
                    getTimeNow = { now },
                )
            }
        }

        onNodeWithTag(BangumiMergeTestTags.EMPTY_STATE).assertIsDisplayed()
        onNodeWithText(syncedText).assertIsDisplayed()
    }

    @Test
    fun `UI-07 自动合并明细可展开`() = runAniComposeUiTest {
        val state = createTestBangumiMergeUiState(now)
        setContent {
            ProvideCompositionLocalsForPreview {
                BangumiMergeScreen(
                    state = state,
                    onSelect = { _, _ -> },
                    onAdoptNewer = {},
                    onSelectAll = {},
                    onApply = {},
                    onRetry = {},
                    onNavigateBack = {},
                    layoutParams = BangumiMergeLayoutParams.Table,
                    getTimeNow = { now },
                )
            }
        }

        // 展开前明细行不可见.
        onNodeWithText("夏日口袋").assertDoesNotExist()
        onNodeWithTag(BangumiMergeTestTags.AUTO_MERGED_TOGGLE).performClick()
        onNodeWithText("夏日口袋").assertIsDisplayed()
    }

    @Test
    fun `UI-08 破坏性值以删除文案展示`() = runAniComposeUiTest {
        val deletedText = runBlocking { getString(Lang.bangumi_merge_deleted_collection) }
        setContent {
            ProvideCompositionLocalsForPreview {
                BangumiMergeScreen(
                    state = createTestBangumiMergeUiState(now, choices = emptyMap()),
                    onSelect = { _, _ -> },
                    onAdoptNewer = {},
                    onSelectAll = {},
                    onApply = {},
                    onRetry = {},
                    onNavigateBack = {},
                    layoutParams = BangumiMergeLayoutParams.Compact,
                    getTimeNow = { now },
                )
            }
        }

        // 上伊那牡丹: 本地已删除收藏.
        onNodeWithText(deletedText).assertIsDisplayed()
    }

    @Test
    fun `UI-09 仅自动合并时展示自动合并数量且可直接应用`() = runAniComposeUiTest {
        val base = createTestBangumiMergeUiState(now, choices = emptyMap())
        val autoOnlyPlan = base.plan!!.copy(conflictGroups = emptyList())
        val autoText = runBlocking { getString(Lang.bangumi_merge_auto_merged, autoOnlyPlan.autoMerged.size) }
        val progress00 = runBlocking { getString(Lang.bangumi_merge_confirmed_progress, 0, 0) }
        var applied = false
        setContent {
            ProvideCompositionLocalsForPreview {
                BangumiMergeScreen(
                    state = BangumiMergeUiState.Initial.copy(isLoading = false, plan = autoOnlyPlan),
                    onSelect = { _, _ -> },
                    onAdoptNewer = {},
                    onSelectAll = {},
                    onApply = { applied = true },
                    onRetry = {},
                    onNavigateBack = {},
                    layoutParams = BangumiMergeLayoutParams.Compact,
                    getTimeNow = { now },
                )
            }
        }

        // 没有冲突: 不展示 "0/0" 进度与 "采用较新的".
        onNodeWithText(progress00).assertDoesNotExist()
        onNodeWithTag(BangumiMergeTestTags.ADOPT_NEWER).assertDoesNotExist()
        // 底栏展示自动合并数量.
        onNodeWithTag(BangumiMergeTestTags.PROGRESS_TEXT).assertTextEquals(autoText)
        // 无需逐项确认, 可直接应用.
        onNodeWithTag(BangumiMergeTestTags.APPLY_BUTTON).assertIsEnabled().performClick()
        runOnIdle { assertTrue(applied) }
    }
}
