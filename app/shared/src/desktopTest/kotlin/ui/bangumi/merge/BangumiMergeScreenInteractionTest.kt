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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.runBlocking
import me.him188.ani.app.data.models.bangumi.BangumiConflictField
import me.him188.ani.app.data.models.bangumi.BangumiConflictFieldType
import me.him188.ani.app.data.models.bangumi.BangumiConflictKey
import me.him188.ani.app.data.models.bangumi.BangumiMergeSide
import me.him188.ani.app.data.models.bangumi.BangumiMergeState
import me.him188.ani.app.data.models.bangumi.BangumiSubjectConflict
import me.him188.ani.app.domain.foundation.LoadError
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.framework.runAniComposeUiTest
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.bangumi_merge_auto_merged
import me.him188.ani.app.ui.lang.bangumi_merge_auto_merged_more
import me.him188.ani.app.ui.lang.bangumi_merge_auto_new_collection
import me.him188.ani.app.ui.lang.bangumi_merge_confirmed_progress
import me.him188.ani.app.ui.lang.bangumi_merge_deleted_collection
import me.him188.ani.app.ui.lang.bangumi_merge_no_comment
import me.him188.ani.app.ui.lang.bangumi_merge_score
import me.him188.ani.app.ui.lang.bangumi_merge_sync_in_progress
import me.him188.ani.app.ui.lang.bangumi_merge_sync_in_progress_notice
import me.him188.ani.app.ui.lang.bangumi_merge_synced
import me.him188.ani.app.ui.lang.bangumi_merge_syncing_description
import me.him188.ani.app.ui.lang.bangumi_merge_syncing_title
import me.him188.ani.app.ui.lang.bangumi_merge_tags
import me.him188.ani.app.ui.lang.subject_collection_wish
import me.him188.ani.datasources.api.topic.UnifiedCollectionType
import me.him188.ani.utils.platform.annotations.TestOnly
import org.jetbrains.compose.resources.getString
import kotlin.test.Test
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * 合并收藏界面的交互测试: 单元格选择 / 全选 / 采用较新的 / 应用门控 / 自动合并明细 / 空状态 / 同步中 / 底部 inset 的归属.
 */
@OptIn(TestOnly::class)
class BangumiMergeScreenInteractionTest {
    private val now = Instant.fromEpochMilliseconds(1_753_000_000_000)

    private companion object {
        const val CONTAINER = "container"
        val BOTTOM_INSET = 80.dp
    }

    private val key2Collection = BangumiConflictKey(2, BangumiConflictFieldType.COLLECTION)
    private val key2Rating = BangumiConflictKey(2, BangumiConflictFieldType.RATING)
    private val key3Rating = BangumiConflictKey(3, BangumiConflictFieldType.RATING)
    private val key5Rating = BangumiConflictKey(5, BangumiConflictFieldType.RATING)

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

        // 选择 芙莉莲(2) 的状态冲突: 点击 Bangumi 侧.
        onNodeWithTag(BangumiMergeTestTags.cell(key2Collection, BangumiMergeSide.BANGUMI)).performClick()

        runOnIdle {
            assertEquals(BangumiMergeSide.BANGUMI, state.choices[key2Collection])
        }
        onNodeWithText(progressText4).assertIsDisplayed()
        onNodeWithTag(BangumiMergeTestTags.cell(key2Collection, BangumiMergeSide.BANGUMI)).assertIsSelected()
    }

    @Test
    fun `UI-02 未全部确认时应用按钮禁用 点击覆盖层不触发 onApply`() = runAniComposeUiTest {
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
        // 点击禁用按钮所在区域不会触发 onApply (而是滚动到未决定项).
        onNodeWithTag(BangumiMergeTestTags.APPLY_BLOCKED_OVERLAY).performClick()
        runOnIdle { assertFalse(applied) }
    }

    @Test
    fun `UI-03 全部确认后应用按钮可用并触发 onApply`() = runAniComposeUiTest {
        val base = createTestBangumiMergeUiState(now)
        val allKeys = base.groups.flatMap { it.conflictKeys }
        val state = base.copy(choices = allKeys.associateWith { BangumiMergeSide.ANIMEKO })
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

        onNodeWithTag(BangumiMergeTestTags.APPLY_BLOCKED_OVERLAY).assertDoesNotExist()
        onNodeWithTag(BangumiMergeTestTags.APPLY_BUTTON).assertIsEnabled().performClick()
        runOnIdle { assertTrue(applied) }
    }

    @Test
    fun `UI-04 列头全选触发回调`() = runAniComposeUiTest {
        val state = createTestBangumiMergeUiState(now, choices = emptyMap())
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
    fun `UI-06 无冲突时展示已同步空态 含可展开的自动合并明细 无底栏`() = runAniComposeUiTest {
        val syncedText = runBlocking { getString(Lang.bangumi_merge_synced) }
        val autoText = runBlocking { getString(Lang.bangumi_merge_auto_merged, 12) }
        val moreText = runBlocking { getString(Lang.bangumi_merge_auto_merged_more, 7) }
        val wishText = runBlocking { getString(Lang.subject_collection_wish) }
        val newCollectionText = runBlocking { getString(Lang.bangumi_merge_auto_new_collection, wishText) }
        setContent {
            ProvideCompositionLocalsForPreview {
                BangumiMergeScreen(
                    state = createTestBangumiMergeUiState(
                        now,
                        choices = emptyMap(),
                        mergeState = createTestBangumiMergeSyncedState(now),
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
        onNodeWithTag(BangumiMergeTestTags.LAST_SYNCED).assertIsDisplayed()
        // 没有冲突: 没有底栏 / 进度 / 采用较新的 / 列头.
        onNodeWithTag(BangumiMergeTestTags.APPLY_BUTTON).assertDoesNotExist()
        onNodeWithTag(BangumiMergeTestTags.PROGRESS_TEXT).assertDoesNotExist()
        onNodeWithTag(BangumiMergeTestTags.ADOPT_NEWER).assertDoesNotExist()
        onNodeWithTag(BangumiMergeTestTags.SELECT_ALL_LOCAL).assertDoesNotExist()

        // 自动合并明细在空状态里, 默认收起.
        onNodeWithText(autoText).assertIsDisplayed()
        onNodeWithText("夏日口袋").assertDoesNotExist()
        onNodeWithTag(BangumiMergeTestTags.AUTO_MERGED_TOGGLE).performClick()
        onNodeWithText("夏日口袋").assertIsDisplayed()
        onNodeWithText(newCollectionText).assertIsDisplayed()
        // 服务端只返回了 5 条明细, 总数 12 → "另有 7 项…".
        onNodeWithTag(BangumiMergeTestTags.AUTO_MERGED_MORE).assertTextEquals(moreText)
    }

    @Test
    fun `UI-14 服务端从未同步过时空态展示专用的同步中标题与说明而不是已同步`() = runAniComposeUiTest {
        val syncedText = runBlocking { getString(Lang.bangumi_merge_synced) }
        val syncingTitle = runBlocking { getString(Lang.bangumi_merge_syncing_title) }
        val syncingDescription = runBlocking { getString(Lang.bangumi_merge_syncing_description) }
        val toastText = runBlocking { getString(Lang.bangumi_merge_sync_in_progress) }
        val noticeText = runBlocking { getString(Lang.bangumi_merge_sync_in_progress_notice) }
        setContent {
            ProvideCompositionLocalsForPreview {
                BangumiMergeScreen(
                    state = createTestBangumiMergeUiState(
                        now,
                        choices = emptyMap(),
                        // 首次绑定: 没有冲突, 也还没有同步时间
                        mergeState = createTestBangumiMergeSyncedState(now).copy(lastSyncedAt = null),
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
        // 同步中提示就是标题本身
        onNodeWithTag(BangumiMergeTestTags.SYNC_IN_PROGRESS_NOTICE).assertIsDisplayed().assertTextEquals(syncingTitle)
        onNodeWithText(syncingDescription).assertIsDisplayed()
        // 不复用 409 toast 的 "请稍后再试", 也不重复列表用的提示条
        onNodeWithText(toastText).assertDoesNotExist()
        onNodeWithText(noticeText).assertDoesNotExist()
        onNodeWithText(syncedText).assertDoesNotExist()
        onNodeWithTag(BangumiMergeTestTags.LAST_SYNCED).assertDoesNotExist()
        // 没有冲突: 仍然没有底栏
        onNodeWithTag(BangumiMergeTestTags.APPLY_BUTTON).assertDoesNotExist()
    }

    @Test
    fun `UI-15 无底栏的空态自己避开底部 inset - 滚到底后自动合并明细不进入 inset 区域`() = runAniComposeUiTest {
        setContent {
            ProvideCompositionLocalsForPreview {
                // 矮容器, 保证展开明细后需要滚动
                Box(Modifier.size(412.dp, 400.dp).testTag(CONTAINER)) {
                    BangumiMergeScreen(
                        state = createTestBangumiMergeUiState(
                            now,
                            choices = emptyMap(),
                            mergeState = createTestBangumiMergeSyncedState(now),
                        ),
                        onSelect = { _, _ -> },
                        onAdoptNewer = {},
                        onSelectAll = {},
                        onApply = {},
                        onRetry = {},
                        onNavigateBack = {},
                        windowInsets = WindowInsets(bottom = BOTTOM_INSET),
                        layoutParams = BangumiMergeLayoutParams.Compact,
                        getTimeNow = { now },
                    )
                }
            }
        }

        val container = onNodeWithTag(CONTAINER).getBoundsInRoot()
        onNodeWithTag(BangumiMergeTestTags.AUTO_MERGED_TOGGLE).performScrollTo().performClick()
        onNodeWithTag(BangumiMergeTestTags.AUTO_MERGED_MORE).performScrollTo().assertIsDisplayed()
        val more = onNodeWithTag(BangumiMergeTestTags.AUTO_MERGED_MORE).getBoundsInRoot()
        // 内容区在 inset 之上结束: 滚到底后明细的底边不会进入底部 inset (否则会被系统导航栏遮住)
        assertTrue(
            more.bottom <= container.bottom - BOTTOM_INSET,
            "expected bottom of 'more' (${more.bottom}) <= ${container.bottom - BOTTOM_INSET}",
        )
    }

    @Test
    fun `UI-16 有冲突时底部 inset 由底栏消费 - 按钮在 inset 之上且列表一直延伸到底栏`() = runAniComposeUiTest {
        setContent {
            ProvideCompositionLocalsForPreview {
                Box(Modifier.size(412.dp, 600.dp).testTag(CONTAINER)) {
                    BangumiMergeScreen(
                        state = createTestBangumiMergeUiState(now),
                        onSelect = { _, _ -> },
                        onAdoptNewer = {},
                        onSelectAll = {},
                        onApply = {},
                        onRetry = {},
                        onNavigateBack = {},
                        windowInsets = WindowInsets(bottom = BOTTOM_INSET),
                        layoutParams = BangumiMergeLayoutParams.Compact,
                        getTimeNow = { now },
                    )
                }
            }
        }

        val container = onNodeWithTag(CONTAINER).getBoundsInRoot()
        val bar = onNodeWithTag(BangumiMergeTestTags.BOTTOM_BAR).getBoundsInRoot()
        val apply = onNodeWithTag(BangumiMergeTestTags.APPLY_BUTTON).getBoundsInRoot()
        val list = onNodeWithTag(BangumiMergeTestTags.LIST).getBoundsInRoot()
        // 底栏贴着底边并在内部避开 inset: 按钮在 inset 之上
        assertTrue(abs((bar.bottom - container.bottom).value) <= 1f, "bar $bar should reach container bottom $container")
        assertTrue(apply.bottom <= container.bottom - BOTTOM_INSET, "apply $apply should be above the inset")
        // 列表一直延伸到底栏顶部: inset 没有被内容再消费一次
        assertTrue(abs((list.bottom - bar.top).value) <= 1f, "list $list should end at bar top $bar")
    }

    @Test
    fun `UI-07 有冲突时自动合并明细在列表底部可展开`() = runAniComposeUiTest {
        val moreText = runBlocking { getString(Lang.bangumi_merge_auto_merged_more, 7) }
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

        onNodeWithTag(BangumiMergeTestTags.EMPTY_STATE).assertDoesNotExist()
        // 展开前明细行不可见.
        onNodeWithText("夏日口袋").assertDoesNotExist()
        onNodeWithTag(BangumiMergeTestTags.AUTO_MERGED_TOGGLE).performClick()
        onNodeWithText("夏日口袋").assertIsDisplayed()
        // 明细是列表最后一项, 展开后 "另有 N 项…" 可能超出可视区, 滚动到它.
        onNodeWithTag(BangumiMergeTestTags.AUTO_MERGED_MORE)
            .performScrollTo()
            .assertIsDisplayed()
            .assertTextEquals(moreText)
    }

    @Test
    fun `UI-08 没有自动合并时不展示明细入口`() = runAniComposeUiTest {
        val state = createTestBangumiMergeUiState(
            now,
            mergeState = createTestBangumiMergeState(now).copy(autoMerged = emptyList(), autoMergedTotal = 0),
        )
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
                    layoutParams = BangumiMergeLayoutParams.Compact,
                    getTimeNow = { now },
                )
            }
        }

        onNodeWithTag(BangumiMergeTestTags.AUTO_MERGED_TOGGLE).assertDoesNotExist()
    }

    @Test
    fun `UI-09 Bangumi 侧已删除收藏以删除文案展示`() = runAniComposeUiTest {
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

        // 上伊那牡丹: Bangumi 侧已删除收藏.
        onNodeWithText(deletedText).assertIsDisplayed()
    }

    @Test
    fun `UI-10 评分单元行按规则渲染 评分含短评 仅短评 仅标签`() = runAniComposeUiTest {
        val score8 = runBlocking { getString(Lang.bangumi_merge_score, 8) }
        val score7 = runBlocking { getString(Lang.bangumi_merge_score, 7) }
        val tagsAnimeko = runBlocking { getString(Lang.bangumi_merge_tags, "异世界, 转生") }
        val tagsBangumi = runBlocking { getString(Lang.bangumi_merge_tags, "异世界") }
        val noCommentText = runBlocking { getString(Lang.bangumi_merge_no_comment) }
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
                    layoutParams = BangumiMergeLayoutParams.Table,
                    getTimeNow = { now },
                )
            }
        }

        // 单元格是合并节点 (可点击), 其文本依次为 主文本 [+ 次级文本]; 评分行没有时间.
        // 芙莉莲: 评分不同 → 评分行, 主文本 "N 分", 次级文本为短评.
        onNodeWithTag(BangumiMergeTestTags.cell(key2Rating, BangumiMergeSide.ANIMEKO))
            .assertIsDisplayed()
            .assertTextEquals(score8, "“旅途的意义就在旅途中”")
        onNodeWithTag(BangumiMergeTestTags.cell(key2Rating, BangumiMergeSide.BANGUMI))
            .assertIsDisplayed()
            .assertTextEquals(score7, "“节奏偏慢”")
        // 我的青春恋爱物语: 仅短评不同 → 短评行, 直接显示短评, 不显示评分.
        onNodeWithTag(BangumiMergeTestTags.cell(key3Rating, BangumiMergeSide.ANIMEKO))
            .assertIsDisplayed()
            .assertTextEquals("“世界线收束，神作”")
        onNodeWithTag(BangumiMergeTestTags.cell(key3Rating, BangumiMergeSide.BANGUMI))
            .assertIsDisplayed()
            .assertTextEquals("“二周目细节更多”")
        // 无职转生: 仅标签不同 → 评分行 (两侧都是 8 分), 次级文本 "标签: …", 相同的短评不展示.
        onNodeWithTag(BangumiMergeTestTags.cell(key5Rating, BangumiMergeSide.ANIMEKO))
            .assertIsDisplayed()
            .assertTextEquals(score8, tagsAnimeko)
        onNodeWithTag(BangumiMergeTestTags.cell(key5Rating, BangumiMergeSide.BANGUMI))
            .assertIsDisplayed()
            .assertTextEquals(score8, tagsBangumi)
        onNodeWithText(noCommentText).assertDoesNotExist()
    }

    @Test
    fun `UI-11 服务端同步中 全部确认也不能应用 并展示提示`() = runAniComposeUiTest {
        val base = createTestBangumiMergeUiState(
            now,
            mergeState = createTestBangumiMergeState(now).copy(syncInProgress = true),
        )
        val state = base.copy(choices = base.groups.flatMap { it.conflictKeys }.associateWith { BangumiMergeSide.BANGUMI })
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

        onNodeWithTag(BangumiMergeTestTags.SYNC_IN_PROGRESS_NOTICE).assertIsDisplayed()
        onNodeWithTag(BangumiMergeTestTags.APPLY_BUTTON).assertIsNotEnabled()
        onNodeWithTag(BangumiMergeTestTags.APPLY_BLOCKED_OVERLAY).performClick()
        runOnIdle { assertFalse(applied) }
    }

    @Test
    fun `UI-12 加载中与加载失败时不展示列表与空态`() = runAniComposeUiTest {
        var state by mutableStateOf(BangumiMergeUiState.Initial)
        var retried = false
        setContent {
            ProvideCompositionLocalsForPreview {
                BangumiMergeScreen(
                    state = state,
                    onSelect = { _, _ -> },
                    onAdoptNewer = {},
                    onSelectAll = {},
                    onApply = {},
                    onRetry = { retried = true },
                    onNavigateBack = {},
                    layoutParams = BangumiMergeLayoutParams.Compact,
                    getTimeNow = { now },
                )
            }
        }

        onNodeWithTag(BangumiMergeTestTags.LIST).assertDoesNotExist()
        onNodeWithTag(BangumiMergeTestTags.EMPTY_STATE).assertDoesNotExist()
        onNodeWithTag(BangumiMergeTestTags.APPLY_BUTTON).assertDoesNotExist()

        state = state.copy(isLoading = false, loadError = LoadError.NetworkError)
        waitForIdle()
        onNodeWithTag(BangumiMergeTestTags.LIST).assertDoesNotExist()
        onNodeWithTag(BangumiMergeTestTags.EMPTY_STATE).assertDoesNotExist()
        assertFalse(retried)

        // 加载成功且没有冲突 → 空态.
        state = state.copy(loadError = null, mergeState = BangumiMergeState.Empty)
        waitForIdle()
        onNodeWithTag(BangumiMergeTestTags.EMPTY_STATE).assertIsDisplayed()
        onNodeWithTag(BangumiMergeTestTags.AUTO_MERGED_TOGGLE).assertDoesNotExist()
        onNodeWithTag(BangumiMergeTestTags.LAST_SYNCED).assertDoesNotExist()
    }

    @Test
    fun `UI-13 点击禁用的应用按钮滚动到第一个未决定的条目`() = runAniComposeUiTest {
        // 30 个条目, 前 25 个已解决; 第 26 个是第一个未决定项, 初始时在可视区外 (未被 LazyColumn 组合).
        val mergeState = BangumiMergeState(
            conflicts = (1..30).map { id ->
                BangumiSubjectConflict(
                    subjectId = id,
                    title = "条目 $id",
                    animekoUpdatedAt = now,
                    bangumiUpdatedAt = now,
                    detectedAt = now,
                    fields = listOf(
                        BangumiConflictField.Collection(UnifiedCollectionType.DOING, UnifiedCollectionType.DONE),
                    ),
                )
            },
            autoMerged = emptyList(),
            autoMergedTotal = 0,
            lastSyncedAt = now,
            syncInProgress = false,
        )
        val state = createTestBangumiMergeUiState(
            now,
            choices = (1..25).associate {
                BangumiConflictKey(it, BangumiConflictFieldType.COLLECTION) to BangumiMergeSide.BANGUMI
            },
            mergeState = mergeState,
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

        onNodeWithTag(BangumiMergeTestTags.card(1)).assertIsDisplayed()
        onNodeWithTag(BangumiMergeTestTags.card(26)).assertDoesNotExist()

        onNodeWithTag(BangumiMergeTestTags.APPLY_BUTTON).assertIsNotEnabled()
        onNodeWithTag(BangumiMergeTestTags.APPLY_BLOCKED_OVERLAY).performClick()
        waitForIdle()

        // 滚动到第一个未决定的条目 (而不是应用).
        onNodeWithTag(BangumiMergeTestTags.card(26)).assertIsDisplayed()
        onNodeWithTag(BangumiMergeTestTags.card(1)).assertDoesNotExist()
        runOnIdle { assertFalse(applied) }
    }
}
