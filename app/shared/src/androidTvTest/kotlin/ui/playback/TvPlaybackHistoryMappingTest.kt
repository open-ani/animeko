/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.tv.ui.playback

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import me.him188.ani.app.data.models.player.EpisodeHistory
import me.him188.ani.app.data.repository.episode.EpisodeCollectionPendingOp
import me.him188.ani.app.data.repository.player.PlaybackHistoryPendingOp
import me.him188.ani.app.ui.playback.PendingSyncEpisode
import me.him188.ani.datasources.api.topic.UnifiedCollectionType

class TvPlaybackHistoryMappingTest {

    private fun history(
        episodeId: Int,
        updatedAtMillis: Long = episodeId.toLong(),
        deletedAtMillis: Long? = null,
    ) = EpisodeHistory(
        episodeId = episodeId,
        positionMillis = 10_000,
        subjectId = 1,
        episodeSort = 3f,
        subjectName = "Subject",
        subjectImageUrl = "https://example.com/cover.jpg",
        episodeName = "Episode",
        durationMillis = 100_000,
        updatedAtMillis = updatedAtMillis,
        deletedAtMillis = deletedAtMillis,
        isDirty = false,
    )

    @Test
    fun `histories exclude deleted records and sort by update time descending`() {
        val items = listOf(
            history(1, updatedAtMillis = 100),
            history(2, updatedAtMillis = 300),
            history(3, updatedAtMillis = 200, deletedAtMillis = 400),
        ).toTvHistoryItems()

        assertEquals(listOf(2, 1), items.map { it.episodeId })
        assertEquals(300L, items.first().updatedAtMillis)
        assertEquals(3f, items.first().episodeSort)
        assertEquals(10_000L, items.first().positionMillis)
        assertEquals("https://example.com/cover.jpg", items.first().subjectImageUrl)
    }

    @Test
    fun `pending sync lists playback operation before watched state`() {
        val item = PendingSyncEpisode(
            episodeId = 10,
            subjectName = "Subject",
            episodeName = "Episode 1",
            playbackOp = PlaybackHistoryPendingOp.Upsert(
                id = 7,
                episodeId = 10,
                subjectId = 1,
                positionMillis = 1_000,
                durationMillis = 2_000,
                updatedAtMillis = 500,
            ),
            collectionOp = EpisodeCollectionPendingOp(
                id = 9,
                subjectId = 1,
                episodeId = 10,
                collectionType = UnifiedCollectionType.DONE,
                updatedAtMillis = 600,
            ),
        ).toTvSyncItem()

        assertEquals(
            listOf(TvPlaybackSyncOperation.ProgressUpsert, TvPlaybackSyncOperation.MarkWatched),
            item.operations,
        )
        assertEquals(7L, item.playbackOpId)
        assertEquals(9L, item.collectionOpId)
        assertEquals(600L, item.versionMillis)
    }

    @Test
    fun `pending sync maps delete and unwatched operation`() {
        val item = PendingSyncEpisode(
            episodeId = 11,
            subjectName = null,
            episodeName = null,
            playbackOp = PlaybackHistoryPendingOp.Delete(id = 3, episodeId = 11, deletedAtMillis = 100),
            collectionOp = EpisodeCollectionPendingOp(
                id = 4,
                subjectId = 1,
                episodeId = 11,
                collectionType = UnifiedCollectionType.WISH,
                updatedAtMillis = 200,
            ),
        ).toTvSyncItem()

        assertEquals(
            listOf(TvPlaybackSyncOperation.ProgressDelete, TvPlaybackSyncOperation.UnmarkWatched),
            item.operations,
        )
        assertEquals(200L, item.versionMillis)
    }

    @Test
    fun `pending sync without operations has empty operation list`() {
        val item = PendingSyncEpisode(
            episodeId = 11,
            subjectName = null,
            episodeName = null,
            playbackOp = null,
            collectionOp = null,
        ).toTvSyncItem()

        assertTrue(item.operations.isEmpty())
        assertEquals(0L, item.versionMillis)
    }
}
