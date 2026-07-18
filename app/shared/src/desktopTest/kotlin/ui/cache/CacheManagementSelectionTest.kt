/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.cache

import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import kotlinx.coroutines.runBlocking
import me.him188.ani.app.domain.media.cache.engine.MediaStats
import me.him188.ani.app.tools.toProgress
import me.him188.ani.app.ui.cache.components.CacheEpisodePaused
import me.him188.ani.app.ui.cache.components.CacheEpisodeState
import me.him188.ani.app.ui.cache.components.CacheGroupState
import me.him188.ani.app.ui.cache.components.CacheSelectionToolbarTestTags
import me.him188.ani.app.ui.cache.components.createTestCacheEpisode
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.framework.runAniComposeUiTest
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.cache_management_enter_selection_mode
import me.him188.ani.app.ui.lang.cache_management_select_all
import me.him188.ani.app.ui.lang.cache_management_selected_count
import me.him188.ani.datasources.api.topic.UnifiedCollectionType
import me.him188.ani.utils.platform.annotations.TestOnly
import org.jetbrains.compose.resources.getString
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(TestOnly::class)
class CacheManagementSelectionTest {
    private val inProgress = createTestCacheEpisode(1, initialState = CacheEpisodePaused.IN_PROGRESS)
    private val paused = createTestCacheEpisode(2, initialState = CacheEpisodePaused.PAUSED)
    private val finished = createTestCacheEpisode(
        3,
        initialState = CacheEpisodePaused.COMPLETED,
        progress = 1f.toProgress(),
    )
    private val failed = createTestCacheEpisode(4, initialState = CacheEpisodePaused.FAILED)
    private val episodes = listOf(inProgress, paused, finished, failed)

    private val state = CacheManagementState(
        MediaStats.Unspecified,
        listOf(
            CacheGroupState(
                subjectId = 1,
                subjectName = "孤独摇滚",
                entries = episodes,
                collectionType = UnifiedCollectionType.DOING,
            ),
        ),
    )

    @Test
    fun `batch resume, pause and delete selected caches`() = runAniComposeUiTest {
        val enterSelectionText = runBlocking { getString(Lang.cache_management_enter_selection_mode) }
        val selectAllText = runBlocking { getString(Lang.cache_management_select_all) }
        val selectedCountText = runBlocking { getString(Lang.cache_management_selected_count, episodes.size) }

        val resumedIds = mutableSetOf<String>()
        val pausedIds = mutableSetOf<String>()
        val deletedIds = mutableSetOf<String>()

        setContent {
            ProvideCompositionLocalsForPreview {
                CacheManagementScreen(
                    state = state,
                    selfInfo = null,
                    onPlay = {},
                    onResume = { resumedIds += it.cacheId },
                    onPause = { pausedIds += it.cacheId },
                    onViewDetail = {},
                    onDelete = { deletedIds += it.cacheId },
                    onClickLogin = {},
                )
            }
        }

        // 进入多选并全选
        onNodeWithContentDescription(enterSelectionText).performClick()
        onNodeWithContentDescription(selectAllText).performClick()
        onNodeWithText(selectedCountText).assertExists()

        // 批量继续: 只作用于已暂停的
        onNodeWithTag(CacheSelectionToolbarTestTags.RESUME).performClick()
        runOnIdle {
            assertEquals(setOf(paused.cacheId), resumedIds)
        }

        // 批量暂停: 只作用于下载中的 (不含已完成/已失败)
        onNodeWithTag(CacheSelectionToolbarTestTags.PAUSE).performClick()
        runOnIdle {
            assertEquals(setOf(inProgress.cacheId), pausedIds)
        }

        // 批量删除: 需要确认, 作用于所有选中项, 然后退出多选
        onNodeWithTag(CacheSelectionToolbarTestTags.DELETE).performClick()
        onNodeWithTag(CacheManagementTestTags.DELETE_CONFIRM_BUTTON).performClick()
        runOnIdle {
            assertEquals(episodes.map { it.cacheId }.toSet(), deletedIds)
        }
        onNodeWithText(selectedCountText).assertDoesNotExist()
    }
}
