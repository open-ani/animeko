/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.download

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking
import me.him188.ani.app.domain.media.cache.engine.MediaStats
import me.him188.ani.app.tools.toProgress
import me.him188.ani.app.ui.download.components.DownloadSelectionToolbarTestTags
import me.him188.ani.app.ui.download.components.DownloadStatus
import me.him188.ani.app.ui.download.components.SubjectDownloadGroup
import me.him188.ani.app.ui.download.components.createTestDownloadItem
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.framework.runAniComposeUiTest
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.cache_management_enter_selection_mode
import me.him188.ani.app.ui.lang.cache_management_select_all
import me.him188.ani.app.ui.lang.cache_management_selected_count
import me.him188.ani.datasources.api.topic.UnifiedCollectionType
import me.him188.ani.utils.platform.annotations.TestOnly
import org.jetbrains.compose.resources.getString

@OptIn(TestOnly::class)
class DownloadManagementSelectionTest {
    private val inProgress = createTestDownloadItem(1, initialState = DownloadStatus.IN_PROGRESS)
    private val paused = createTestDownloadItem(2, initialState = DownloadStatus.PAUSED)
    private val finished = createTestDownloadItem(
        3,
        initialState = DownloadStatus.COMPLETED,
        progress = 1f.toProgress(),
    )
    private val failed = createTestDownloadItem(4, initialState = DownloadStatus.FAILED)
    private val episodes = listOf(inProgress, paused, finished, failed)

    private val state = DownloadManagementUiState(
        MediaStats.Unspecified,
        listOf(
            SubjectDownloadGroup(
                subjectId = 1,
                subjectName = "孤独摇滚",
                entries = episodes,
                collectionType = UnifiedCollectionType.DOING,
            ),
        ),
    )

    @Test
    fun `batch actions keep all selected ids until download state catches up`() = runAniComposeUiTest {
        val enterSelectionText = runBlocking { getString(Lang.cache_management_enter_selection_mode) }
        val selectAllText = runBlocking { getString(Lang.cache_management_select_all) }
        val selectedCountText = runBlocking { getString(Lang.cache_management_selected_count, episodes.size) }

        val resumedIds = mutableSetOf<String>()
        val pausedIds = mutableSetOf<String>()
        val deletedIds = mutableSetOf<String>()

        var currentState by mutableStateOf(state)
        setContent {
            ProvideCompositionLocalsForPreview {
                DownloadManagementScreen(
                    state = currentState,
                    selfInfo = null,
                    onPlay = {},
                    onResume = { resumedIds += it.id },
                    onPause = { pausedIds += it.id },
                    onViewDetail = {},
                    onDelete = { item ->
                        deletedIds += item.id
                        currentState = currentState.copy(groups = currentState.groups.map { group ->
                            group.copy(entries = group.entries.filterNot { it.id == item.id })
                        }.filter { it.entries.isNotEmpty() })
                    },
                    onClickLogin = {},
                )
            }
        }

        // 进入多选并全选
        onNodeWithContentDescription(enterSelectionText).performClick()
        onNodeWithContentDescription(selectAllText).performClick()
        onNodeWithText(selectedCountText).assertExists()

        // UI 状态尚未更新时，连续操作都应提交完整选择范围，由 domain 检查执行时的状态。
        onNodeWithTag(DownloadSelectionToolbarTestTags.RESUME).performClick()
        runOnIdle {
            assertEquals(episodes.map { it.id }.toSet(), resumedIds)
        }

        onNodeWithTag(DownloadSelectionToolbarTestTags.PAUSE).performClick()
        runOnIdle {
            assertEquals(episodes.map { it.id }.toSet(), pausedIds)
        }

        // 批量删除: 需要确认, 作用于所有选中项, 然后退出多选
        onNodeWithTag(DownloadSelectionToolbarTestTags.DELETE).performClick()
        onNodeWithTag(DownloadManagementTestTags.DELETE_CONFIRM_BUTTON).performClick()
        runOnIdle {
            assertEquals(episodes.map { it.id }.toSet(), deletedIds)
        }
        onNodeWithText(selectedCountText).assertDoesNotExist()
    }
}
