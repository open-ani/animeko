/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.repository.subject

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
import kotlin.test.assertNull

class SyncSubjectCollectionTypesByProgressUseCaseTest {
    @Test
    fun `uncollected becomes doing when a main story episode is done`() {
        val collection = collection(
            UnifiedCollectionType.NOT_COLLECTED,
            episode(UnifiedCollectionType.DONE),
            episode(UnifiedCollectionType.NOT_COLLECTED, id = 2),
        )

        assertEquals(UnifiedCollectionType.DOING, collection.syncedCollectionTypeByEpisodeProgress())
    }

    @Test
    fun `uncollected multi episode subject becomes doing even when the last episode is done`() {
        val collection = collection(
            UnifiedCollectionType.NOT_COLLECTED,
            episode(UnifiedCollectionType.NOT_COLLECTED),
            episode(UnifiedCollectionType.DONE, id = 2),
            completed = true,
        )

        assertEquals(UnifiedCollectionType.DOING, collection.syncedCollectionTypeByEpisodeProgress())
    }

    @Test
    fun `uncollected completed single main story episode becomes done`() {
        val collection = collection(
            UnifiedCollectionType.NOT_COLLECTED,
            episode(UnifiedCollectionType.DONE),
            episode(UnifiedCollectionType.NOT_COLLECTED, id = 2, episodeType = EpisodeType.SP),
            completed = true,
        )

        assertEquals(UnifiedCollectionType.DONE, collection.syncedCollectionTypeByEpisodeProgress())
    }

    @Test
    fun `uncollected ongoing single main story episode becomes doing`() {
        val collection = collection(
            UnifiedCollectionType.NOT_COLLECTED,
            episode(UnifiedCollectionType.DONE),
        )

        assertEquals(UnifiedCollectionType.DOING, collection.syncedCollectionTypeByEpisodeProgress())
    }

    @Test
    fun `uncollected ignores unwatched and non-main-story episodes`() {
        val collection = collection(
            UnifiedCollectionType.NOT_COLLECTED,
            episode(UnifiedCollectionType.NOT_COLLECTED),
            episode(UnifiedCollectionType.DROPPED, id = 2),
            episode(UnifiedCollectionType.DONE, id = 3, episodeType = EpisodeType.SP),
        )

        assertNull(collection.syncedCollectionTypeByEpisodeProgress())
        assertNull(collection.copy(episodes = emptyList()).syncedCollectionTypeByEpisodeProgress())
    }

    @Test
    fun `done on hold and dropped subjects keep their collection type`() {
        for (type in listOf(UnifiedCollectionType.DONE, UnifiedCollectionType.ON_HOLD, UnifiedCollectionType.DROPPED)) {
            val collection = collection(type, episode(UnifiedCollectionType.DONE), completed = true)
            assertNull(collection.syncedCollectionTypeByEpisodeProgress(), type.name)
        }
    }

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
    fun `completed wish becomes done when the last main story episode is done`() {
        val collection = collection(
            type = UnifiedCollectionType.WISH,
            episode(UnifiedCollectionType.NOT_COLLECTED),
            episode(UnifiedCollectionType.DONE, id = 2),
            completed = true,
        )

        assertEquals(UnifiedCollectionType.DONE, collection.syncedCollectionTypeByEpisodeProgress())
    }

    @Test
    fun `completed wish becomes doing when only some main story episodes are done`() {
        val collection = collection(
            type = UnifiedCollectionType.WISH,
            episode(UnifiedCollectionType.DONE),
            episode(UnifiedCollectionType.NOT_COLLECTED, id = 2),
            completed = true,
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
    fun `completed doing becomes done when the last main story episode is done`() {
        val collection = collection(
            type = UnifiedCollectionType.DOING,
            episode(UnifiedCollectionType.NOT_COLLECTED),
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

    private fun collection(
        type: UnifiedCollectionType,
        vararg episodes: EpisodeCollectionInfo,
        completed: Boolean = false,
    ): SubjectCollectionInfo {
        return createTestSubjectCollection(1, episodes.toList(), type).copy(
            airingInfo = if (completed) {
                TestSubjectAiringInfos.Completed12Eps.copy(
                    mainEpisodeCount = episodes.count { it.episodeInfo.type == EpisodeType.MainStory },
                )
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
