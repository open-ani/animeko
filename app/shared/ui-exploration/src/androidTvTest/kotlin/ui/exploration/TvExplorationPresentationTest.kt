/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */
package me.him188.ani.leanback.ui.exploration

import me.him188.ani.app.data.models.episode.EpisodeCollectionInfo
import me.him188.ani.app.data.models.episode.EpisodeInfo
import me.him188.ani.app.data.models.subject.TestSubjectCollections
import me.him188.ani.datasources.api.EpisodeSort
import me.him188.ani.datasources.api.EpisodeType
import me.him188.ani.datasources.api.topic.UnifiedCollectionType
import me.him188.ani.utils.platform.annotations.TestOnly
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(TestOnly::class)
class TvExplorationPresentationTest {
    @Test
    fun carouselWrapsBothWaysAndKeepsIdentityAfterReorder() {
        assertEquals(30, nextFeaturedSubjectId(listOf(10, 20, 30), 10, -1))
        assertEquals(10, nextFeaturedSubjectId(listOf(10, 20, 30), 30, 1))
        assertEquals(10, nextFeaturedSubjectId(listOf(20, 10, 30), 20, 1))
        assertEquals(10, nextFeaturedSubjectId(listOf(10), 10, 1))
        assertNull(nextFeaturedSubjectId(emptyList(), 10, 1))
    }

    @Test
    fun watchedProgressCountsCompletedMainEpisodesWithoutFillingGapsOrSpecials() {
        val collection = TestSubjectCollections.first()
        val episodes = (1..12).map { number ->
            EpisodeCollectionInfo(
                EpisodeInfo(
                    episodeId = number,
                    type = EpisodeType.MainStory,
                    name = "Episode $number",
                    nameCn = "",
                    comment = 0,
                    desc = "",
                    sort = EpisodeSort(number),
                    ep = null,
                ),
                if (number in listOf(1, 4, 8)) UnifiedCollectionType.DONE else UnifiedCollectionType.NOT_COLLECTED,
            )
        }
        val special = episodes.first().copy(episodeInfo = episodes.first().episodeInfo.copy(type = EpisodeType.SP))
        val progress = watchedEpisodeProgress(
            collection.copy(
                episodes = episodes + special,
                subjectInfo = collection.subjectInfo.copy(totalEpisodes = 12),
            ),
        )
        assertEquals(TvWatchedEpisodeProgress(3, 12), progress)
        assertEquals(.25f, progress.fraction)
    }

    @Test
    fun unknownEpisodeTotalDoesNotProduceInvalidProgress() {
        val collection = TestSubjectCollections.first()
        assertEquals(
            TvWatchedEpisodeProgress(0, 0),
            watchedEpisodeProgress(
                collection.copy(
                    episodes = emptyList(),
                    airingInfo = collection.airingInfo.copy(mainEpisodeCount = 0),
                ),
            ),
        )
        assertEquals(0f, TvWatchedEpisodeProgress(0, 0).fraction)
    }
}
