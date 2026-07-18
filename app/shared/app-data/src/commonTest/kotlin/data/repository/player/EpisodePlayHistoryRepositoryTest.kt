/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.repository.player

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import me.him188.ani.app.data.models.player.EpisodeHistory
import me.him188.ani.app.data.persistent.MemoryDataStore
import me.him188.ani.app.data.persistent.database.dao.createMemoryPlaybackHistoryDao
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EpisodePlayHistoryRepositoryTest {
    private var now = 100L

    private fun createRepository(
        initialHistories: EpisodeHistories = EpisodeHistories.Empty,
    ): EpisodePlayHistoryRepository {
        return EpisodePlayHistoryRepositoryImpl(
            dataStore = MemoryDataStore(initialHistories),
            playbackHistoryDao = createMemoryPlaybackHistoryDao(),
            nowMillis = { now },
        )
    }

    @Test
    fun `save stores metadata and enqueues pending upsert op`() = runTest {
        val repository = createRepository()

        repository.saveOrUpdate(
            episodeId = 1,
            positionMillis = 20_000,
            subjectId = 10,
            episodeSort = 2f,
            subjectName = "Subject",
            subjectImageUrl = "https://example.com/cover.jpg",
            episodeName = "Episode",
            durationMillis = 100_000,
        )

        assertEquals(
            EpisodeHistory(
                episodeId = 1,
                positionMillis = 20_000,
                subjectId = 10,
                episodeSort = 2f,
                subjectName = "Subject",
                subjectImageUrl = "https://example.com/cover.jpg",
                episodeName = "Episode",
                durationMillis = 100_000,
                updatedAtMillis = 100,
                isDirty = false,
            ),
            repository.flow.first().single(),
        )
        assertFalse(repository.allHistoriesFlow.first().single().isDirty)

        val pendingOp = repository.pendingOpsFlow.first().single()
        assertTrue(pendingOp is PlaybackHistoryPendingOp.Upsert)
        assertEquals(1, pendingOp.episodeId)
        assertEquals(10, pendingOp.subjectId)
        assertEquals(20_000, pendingOp.positionMillis)
        assertEquals(100_000, pendingOp.durationMillis)
    }

    @Test
    fun `successive saves keep only the latest pending op for each episode`() = runTest {
        val repository = createRepository()

        repository.saveOrUpdate(
            episodeId = 1,
            positionMillis = 20_000,
            subjectId = 10,
            durationMillis = 100_000,
        )
        now = 200
        repository.saveOrUpdate(
            episodeId = 1,
            positionMillis = 40_000,
            subjectId = 10,
            durationMillis = 100_000,
        )
        repository.saveOrUpdate(
            episodeId = 2,
            positionMillis = 30_000,
            subjectId = 20,
            durationMillis = 100_000,
        )

        val pendingOps = repository.pendingOpsFlow.first()
        assertEquals(2, pendingOps.size)
        assertEquals(
            mapOf(1 to 40_000L, 2 to 30_000L),
            pendingOps.associate { op ->
                val upsert = op as PlaybackHistoryPendingOp.Upsert
                upsert.episodeId to upsert.positionMillis
            },
        )
    }

    @Test
    fun `legacy migration stores records and enqueues pending upsert ops`() = runTest {
        val repository = createRepository(
            EpisodeHistories(
                histories = listOf(
                    EpisodeHistory(
                        episodeId = 1,
                        positionMillis = 20_000,
                        subjectId = 10,
                        episodeSort = 2f,
                        subjectName = "Subject",
                        subjectImageUrl = "https://example.com/cover.jpg",
                        episodeName = "Episode",
                        durationMillis = 100_000,
                        updatedAtMillis = 50,
                        isDirty = true,
                    ),
                ),
            ),
        )

        assertEquals(20_000, repository.getPositionMillisByEpisodeId(1))

        val history = repository.flow.first().single()
        assertEquals(1, history.episodeId)
        assertEquals("Subject", history.subjectName)
        assertFalse(history.isDirty)

        val pendingOp = repository.pendingOpsFlow.first().single()
        assertTrue(pendingOp is PlaybackHistoryPendingOp.Upsert)
        assertEquals(1, pendingOp.episodeId)
        assertEquals(10, pendingOp.subjectId)
        assertEquals(20_000, pendingOp.positionMillis)
        assertEquals(100_000, pendingOp.durationMillis)
        assertEquals(50, pendingOp.updatedAtMillis)
    }

    @Test
    fun `remove creates tombstone and enqueues pending delete op`() = runTest {
        val repository = createRepository()
        repository.saveOrUpdate(
            episodeId = 1,
            positionMillis = 20_000,
            subjectId = 10,
            durationMillis = 100_000,
        )

        now = 200
        repository.removeAll(listOf(1))

        assertEquals(emptyList(), repository.flow.first())
        val tombstone = repository.allHistoriesFlow.first().single()
        assertEquals(1, tombstone.episodeId)
        assertEquals(200, tombstone.deletedAtMillis)
        assertFalse(tombstone.isDirty)
        assertNull(repository.getPositionMillisByEpisodeId(1))

        val pendingOps = repository.pendingOpsFlow.first()
        assertEquals(1, pendingOps.size)
        assertTrue(pendingOps.single() is PlaybackHistoryPendingOp.Delete)
        assertEquals(1, pendingOps.single().episodeId)
    }

    @Test
    fun `sync result removes sent pending ops and overwrites local record`() = runTest {
        val repository = createRepository()
        repository.saveOrUpdate(
            episodeId = 1,
            positionMillis = 20_000,
            subjectId = 10,
            durationMillis = 100_000,
        )
        val sentOpIds = repository.pendingOpsFlow.first().map { it.id }

        repository.applySyncResult(
            sentPendingOpIds = sentOpIds,
            records = listOf(
                EpisodeHistory(
                    episodeId = 1,
                    positionMillis = 40_000,
                    subjectId = 10,
                    subjectName = "Remote",
                    updatedAtMillis = 300,
                    isDirty = false,
                ),
            ),
            nextSyncAtMillis = 500,
        )

        val history = repository.flow.first().single()
        assertEquals(40_000, history.positionMillis)
        assertEquals("Remote", history.subjectName)
        assertFalse(history.isDirty)
        assertEquals(500, repository.lastSyncAtMillisFlow.first())
        assertEquals(emptyList(), repository.pendingOpsFlow.first())
    }

    @Test
    fun `sync result does not overwrite records with remaining pending ops`() = runTest {
        val repository = createRepository()
        repository.saveOrUpdate(
            episodeId = 1,
            positionMillis = 20_000,
            subjectId = 10,
            durationMillis = 100_000,
        )
        now = 400
        repository.remove(1)

        repository.applySyncResult(
            sentPendingOpIds = emptyList(),
            records = listOf(
                EpisodeHistory(
                    episodeId = 1,
                    positionMillis = 30_000,
                    updatedAtMillis = 300,
                    isDirty = false,
                ),
            ),
            nextSyncAtMillis = 600,
        )

        assertEquals(emptyList(), repository.flow.first())
        val history = repository.allHistoriesFlow.first().single()
        assertEquals(400, history.deletedAtMillis)
        assertTrue(history.isDeleted)
    }

    @Test
    fun `delete pending ops removes selected ops`() = runTest {
        val repository = createRepository()
        repository.saveOrUpdate(
            episodeId = 1,
            positionMillis = 20_000,
            subjectId = 10,
            durationMillis = 100_000,
        )
        repository.saveOrUpdate(
            episodeId = 2,
            positionMillis = 30_000,
            subjectId = 20,
            durationMillis = 100_000,
        )
        val pendingOps = repository.pendingOpsFlow.first()

        repository.deletePendingOps(listOf(pendingOps.first().id))

        assertEquals(
            pendingOps.drop(1).map { it.id },
            repository.pendingOpsFlow.first().map { it.id },
        )
    }
}
