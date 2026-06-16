/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.playback

import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import kotlinx.coroutines.runBlocking
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.framework.runAniComposeUiTest
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.cache_subject_delete
import me.him188.ani.app.ui.lang.playback_history_cover
import me.him188.ani.app.ui.lang.playback_history_delete_selected
import me.him188.ani.app.ui.lang.playback_history_enter_selection_mode
import me.him188.ani.app.ui.lang.playback_history_episode_label
import me.him188.ani.app.ui.lang.playback_history_selected_count
import me.him188.ani.app.ui.lang.playback_history_sync_delete_pending
import me.him188.ani.app.ui.lang.playback_history_sync_status_pending
import me.him188.ani.app.ui.lang.playback_history_sync_status_synced
import org.jetbrains.compose.resources.getString
import kotlin.test.Test
import kotlin.test.assertEquals

class PlaybackHistoryScreenTest {
    @Test
    fun `shows playback history fields with subject cover`() = runAniComposeUiTest {
        val item = testHistory()
        val coverText = runBlocking { getString(Lang.playback_history_cover, item.subjectName!!) }
        val episodeLabel = runBlocking { getString(Lang.playback_history_episode_label, "2") }

        setContent {
            ProvideCompositionLocalsForPreview {
                PlaybackHistoryScreen(
                    histories = listOf(item),
                    onNavigateBack = {},
                    onOpenHistory = {},
                    onDelete = {},
                )
            }
        }

        onNodeWithContentDescription(coverText).assertExists()
        onNodeWithText("葬送的芙莉莲", substring = true).assertExists()
        onNodeWithText(episodeLabel, substring = true).assertExists()
        onNodeWithText("别离", substring = true).assertExists()
        onNodeWithText("1:30 / 25:00", substring = true).assertExists()
        onAllNodesWithTag(PlaybackHistoryTestTags.DATE, useUnmergedTree = true)[0].assertExists()
    }

    @Test
    fun `batch delete selected playback histories`() = runAniComposeUiTest {
        val histories = listOf(
            testHistory(episodeId = 1),
            testHistory(episodeId = 2, episodeName = "魔法"),
        )
        val enterSelectionText = runBlocking { getString(Lang.playback_history_enter_selection_mode) }
        val selectedCountText = runBlocking { getString(Lang.playback_history_selected_count, 2) }
        val deleteSelectedText = runBlocking { getString(Lang.playback_history_delete_selected) }
        val deleteText = runBlocking { getString(Lang.cache_subject_delete) }
        var deletedIds = emptyList<Int>()

        setContent {
            ProvideCompositionLocalsForPreview {
                PlaybackHistoryScreen(
                    histories = histories,
                    onNavigateBack = {},
                    onOpenHistory = {},
                    onDelete = { deletedIds = it.sorted() },
                )
            }
        }

        onNodeWithContentDescription(enterSelectionText).performClick()
        onNodeWithTag("${PlaybackHistoryTestTags.ITEM_PREFIX}1").performClick()
        onNodeWithTag("${PlaybackHistoryTestTags.ITEM_PREFIX}2").performClick()
        onNodeWithText(selectedCountText).assertExists()

        onNodeWithContentDescription(deleteSelectedText).performClick()
        onNodeWithText(deleteText).performClick()

        runOnIdle {
            assertEquals(listOf(1, 2), deletedIds)
        }
    }

    @Test
    fun `shows synced and pending sync status icons`() = runAniComposeUiTest {
        val syncedText = runBlocking { getString(Lang.playback_history_sync_status_synced) }
        val pendingText = runBlocking { getString(Lang.playback_history_sync_status_pending, 2) }
        var opened = 0

        setContent {
            ProvideCompositionLocalsForPreview {
                PlaybackHistoryScreen(
                    histories = listOf(testHistory()),
                    pendingOpCount = 0,
                    onNavigateBack = {},
                    onOpenHistory = {},
                    onOpenSyncStatus = { opened++ },
                    onDelete = {},
                )
            }
        }

        onNodeWithContentDescription(syncedText).performClick()
        runOnIdle {
            assertEquals(1, opened)
        }

        setContent {
            ProvideCompositionLocalsForPreview {
                PlaybackHistoryScreen(
                    histories = listOf(testHistory()),
                    pendingOpCount = 2,
                    onNavigateBack = {},
                    onOpenHistory = {},
                    onOpenSyncStatus = { opened++ },
                    onDelete = {},
                )
            }
        }

        onNodeWithContentDescription(pendingText).performClick()
        runOnIdle {
            assertEquals(2, opened)
        }
    }

    @Test
    fun `sync status screen lists and deletes pending ops`() = runAniComposeUiTest {
        val deletePendingText = runBlocking { getString(Lang.playback_history_sync_delete_pending) }
        var deletedIds = emptyList<Long>()

        setContent {
            ProvideCompositionLocalsForPreview {
                PlaybackHistorySyncStatusScreen(
                    pendingOps = listOf(
                        PlaybackHistorySyncStatusUiItem(
                            id = 1,
                            episodeId = 11,
                            operationName = "更新",
                            subjectName = "葬送的芙莉莲",
                            episodeName = "别离",
                            versionMillis = 1_700_000_000_000,
                        ),
                        PlaybackHistorySyncStatusUiItem(
                            id = 2,
                            episodeId = 12,
                            operationName = "删除",
                            subjectName = null,
                            episodeName = null,
                            versionMillis = 1_700_000_100_000,
                        ),
                    ),
                    onNavigateBack = {},
                    onDeletePendingOps = { deletedIds = it.sorted() },
                )
            }
        }

        onNodeWithTag(PlaybackHistoryTestTags.SYNC_PENDING_LIST).assertExists()
        onNodeWithTag("${PlaybackHistoryTestTags.SYNC_PENDING_ITEM_PREFIX}1").assertExists()
        onNodeWithText("葬送的芙莉莲", substring = true).assertExists()
        onAllNodesWithContentDescription(deletePendingText)[0].performClick()

        runOnIdle {
            assertEquals(listOf(1L), deletedIds)
        }
    }

    private fun testHistory(
        episodeId: Int = 1,
        episodeName: String = "别离",
    ): PlaybackHistoryUiItem {
        return PlaybackHistoryUiItem(
            episodeId = episodeId,
            subjectId = 100,
            episodeSort = 2f,
            subjectName = "葬送的芙莉莲",
            subjectImageUrl = "https://example.com/cover.jpg",
            episodeName = episodeName,
            positionMillis = 90_000,
            durationMillis = 1_500_000,
            updatedAtMillis = 1_700_000_000_000,
        )
    }
}
