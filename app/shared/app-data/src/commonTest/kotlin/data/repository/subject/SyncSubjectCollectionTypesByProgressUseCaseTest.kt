/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.repository.subject

import kotlinx.coroutines.test.runTest
import me.him188.ani.app.data.models.episode.EpisodeCollectionInfo
import me.him188.ani.app.data.models.episode.EpisodeInfo
import me.him188.ani.app.data.models.subject.SubjectCollectionInfo
import me.him188.ani.app.data.models.subject.TestSubjectAiringInfos
import me.him188.ani.app.data.models.subject.createTestSubjectCollection
import me.him188.ani.datasources.api.EpisodeSort
import me.him188.ani.datasources.api.EpisodeType
import me.him188.ani.datasources.api.topic.UnifiedCollectionType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class SyncSubjectCollectionTypesByProgressUseCaseTest {
    @Test
    fun `wish becomes doing when any main story episode is done`() {
        val collection = collection(
            type = UnifiedCollectionType.WISH,
            episode(UnifiedCollectionType.NOT_COLLECTED),
            episode(UnifiedCollectionType.DONE, id = 2),
        )

        assertEquals(UnifiedCollectionType.DOING, collection.syncedCollectionTypeByEpisodeProgress())
    }

    @Test
    fun `wish ignores done non-main-story episodes`() {
        val collection = collection(
            type = UnifiedCollectionType.WISH,
            episode(UnifiedCollectionType.DONE, episodeType = EpisodeType.SP),
        )

        assertNull(collection.syncedCollectionTypeByEpisodeProgress())
    }

    @Test
    fun `wish does not count dropped main story episode as watched`() {
        val collection = collection(
            type = UnifiedCollectionType.WISH,
            episode(UnifiedCollectionType.DROPPED),
        )

        assertNull(collection.syncedCollectionTypeByEpisodeProgress())
    }

    @Test
    fun `completed doing becomes done when every main story episode is done`() {
        val collection = collection(
            type = UnifiedCollectionType.DOING,
            episode(UnifiedCollectionType.DONE),
            episode(UnifiedCollectionType.DONE, id = 2),
            completed = true,
        )

        assertEquals(UnifiedCollectionType.DONE, collection.syncedCollectionTypeByEpisodeProgress())
    }

    @Test
    fun `ongoing doing remains doing even when every current main story episode is done`() {
        val collection = collection(
            type = UnifiedCollectionType.DOING,
            episode(UnifiedCollectionType.DONE),
            completed = false,
        )

        assertNull(collection.syncedCollectionTypeByEpisodeProgress())
    }

    @Test
    fun `completed doing remains doing when a main story episode is dropped`() {
        val collection = collection(
            type = UnifiedCollectionType.DOING,
            episode(UnifiedCollectionType.DONE),
            episode(UnifiedCollectionType.DROPPED, id = 2),
            completed = true,
        )

        assertNull(collection.syncedCollectionTypeByEpisodeProgress())
    }

    @Test
    fun `complete snapshot loads every page`() = runTest {
        val source = (1..5).toList()

        val result = fetchCompleteSnapshot(
            pageSize = 2,
            keySelector = { it },
        ) { offset, limit ->
            source.size.toLong() to source.drop(offset).take(limit)
        }

        assertEquals(source, result)
    }

    @Test
    fun `complete snapshot rejects duplicate items across pages`() = runTest {
        assertFailsWith<IllegalStateException> {
            fetchCompleteSnapshot(
                pageSize = 2,
                keySelector = { it },
            ) { offset, _ ->
                4L to if (offset == 0) listOf(1, 2) else listOf(2, 3)
            }
        }
    }

    @Test
    fun `complete snapshot rejects an early empty page`() = runTest {
        assertFailsWith<IllegalStateException> {
            fetchCompleteSnapshot(
                pageSize = 2,
                keySelector = { it },
            ) { offset, _ ->
                3L to if (offset == 0) listOf(1, 2) else emptyList()
            }
        }
    }

    private fun collection(
        type: UnifiedCollectionType,
        vararg episodes: EpisodeCollectionInfo,
        completed: Boolean = false,
    ): SubjectCollectionInfo {
        return createTestSubjectCollection(1, episodes.toList(), type).copy(
            airingInfo = if (completed) {
                TestSubjectAiringInfos.Completed12Eps
            } else {
                TestSubjectAiringInfos.OnAir12Eps
            },
        )
    }

    private fun episode(
        collectionType: UnifiedCollectionType,
        id: Int = 1,
        episodeType: EpisodeType = EpisodeType.MainStory,
    ): EpisodeCollectionInfo {
        return EpisodeCollectionInfo(
            episodeInfo = EpisodeInfo(
                episodeId = id,
                type = episodeType,
                sort = EpisodeSort(id, episodeType),
            ),
            collectionType = collectionType,
        )
    }
}
