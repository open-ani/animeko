/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.playback

import me.him188.ani.app.data.models.player.EpisodeHistory
import me.him188.ani.app.data.repository.episode.EpisodeCollectionPendingOp
import me.him188.ani.app.data.repository.episode.EpisodeCollectionPendingOpNames
import me.him188.ani.app.data.repository.player.PlaybackHistoryPendingOp
import me.him188.ani.datasources.api.topic.UnifiedCollectionType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PlaybackHistorySyncStatusUiItemsTest {
    private val deletedHistory = EpisodeHistory(
        episodeId = 11,
        positionMillis = 0,
        subjectName = "葬送的芙莉莲",
        episodeName = "别离",
        deletedAtMillis = 200,
    )

    private fun List<PendingSyncEpisode>.toUiItems() = toSyncStatusUiItems(
        upsertName = "更新进度",
        deleteName = "删除记录",
        markWatchedName = "标记看过",
        unmarkWatchedName = "取消看过",
    )

    @Test
    fun `delete op takes names from deleted local record`() {
        val item = buildPendingSyncEpisodes(
            playbackOps = listOf(PlaybackHistoryPendingOp.Delete(id = 1, episodeId = 11, deletedAtMillis = 200)),
            playbackHistories = mapOf(11 to deletedHistory),
            collectionOps = emptyList(),
            collectionNames = emptyMap(),
        ).toUiItems().single()

        assertEquals(listOf("删除记录"), item.operationNames)
        assertEquals("葬送的芙莉莲", item.subjectName)
        assertEquals("别离", item.episodeName)
        assertEquals(200, item.versionMillis)
        assertEquals(1L, item.playbackOpId)
        assertNull(item.collectionOpId)
    }

    @Test
    fun `playback and collection ops for one episode merge into one item`() {
        val item = buildPendingSyncEpisodes(
            playbackOps = listOf(
                PlaybackHistoryPendingOp.Upsert(
                    id = 1, episodeId = 11, subjectId = 1,
                    subjectName = "葬送的芙莉莲", episodeName = "别离",
                    positionMillis = 10, durationMillis = 100, updatedAtMillis = 300,
                ),
            ),
            playbackHistories = emptyMap(),
            collectionOps = listOf(
                EpisodeCollectionPendingOp(id = 7, subjectId = 1, episodeId = 11, collectionType = UnifiedCollectionType.DONE, updatedAtMillis = 400),
            ),
            collectionNames = emptyMap(),
        ).toUiItems().single()

        assertEquals(listOf("更新进度", "标记看过"), item.operationNames)
        assertEquals(400, item.versionMillis)
        assertEquals(1L, item.playbackOpId)
        assertEquals(7L, item.collectionOpId)
    }

    @Test
    fun `collection-only op takes names from episode cache and shows unmark for non-DONE`() {
        val item = buildPendingSyncEpisodes(
            playbackOps = emptyList(),
            playbackHistories = emptyMap(),
            collectionOps = listOf(
                EpisodeCollectionPendingOp(id = 7, subjectId = 1, episodeId = 12, collectionType = UnifiedCollectionType.WISH, updatedAtMillis = 400),
            ),
            collectionNames = mapOf(12 to EpisodeCollectionPendingOpNames(subjectName = "条目", episodeName = "第 12 集")),
        ).toUiItems().single()

        assertEquals(listOf("取消看过"), item.operationNames)
        assertEquals("条目", item.subjectName)
        assertEquals("第 12 集", item.episodeName)
        assertNull(item.playbackOpId)
    }

    @Test
    fun `items are ordered by latest op time then episode id`() {
        val episodes = buildPendingSyncEpisodes(
            playbackOps = listOf(
                PlaybackHistoryPendingOp.Delete(id = 1, episodeId = 20, deletedAtMillis = 500),
                PlaybackHistoryPendingOp.Delete(id = 2, episodeId = 10, deletedAtMillis = 100),
            ),
            playbackHistories = emptyMap(),
            collectionOps = listOf(
                EpisodeCollectionPendingOp(id = 3, subjectId = 1, episodeId = 10, collectionType = UnifiedCollectionType.DONE, updatedAtMillis = 300),
                EpisodeCollectionPendingOp(id = 4, subjectId = 1, episodeId = 30, collectionType = UnifiedCollectionType.DONE, updatedAtMillis = 300),
            ),
            collectionNames = emptyMap(),
        )

        assertEquals(listOf(10, 30, 20), episodes.map { it.episodeId })
    }
}
