/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */
package me.him188.ani.tv.ui.exploration

import me.him188.ani.app.data.models.episode.EpisodeCollectionInfo
import me.him188.ani.app.data.models.episode.EpisodeInfo
import me.him188.ani.app.data.models.subject.SubjectAiringKind
import me.him188.ani.app.data.models.subject.TestSubjectCollections
import me.him188.ani.datasources.api.EpisodeSort
import me.him188.ani.datasources.api.EpisodeType
import me.him188.ani.datasources.api.topic.UnifiedCollectionType
import me.him188.ani.utils.platform.annotations.TestOnly
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(TestOnly::class)
class TvWatchingProgressTest {
    private val collection = TestSubjectCollections.first().let { base ->
        base.copy(
            episodes = (25..36).map { number ->
                EpisodeCollectionInfo(
                    EpisodeInfo(
                        episodeId = number, type = EpisodeType.MainStory, name = "Episode $number", nameCn = "",
                        comment = 0, desc = "", sort = EpisodeSort(number), ep = EpisodeSort(number - 24),
                    ),
                    when (number) {
                        25, 28, 32 -> UnifiedCollectionType.DONE
                        26 -> UnifiedCollectionType.DROPPED
                        else -> UnifiedCollectionType.NOT_COLLECTED
                    },
                )
            },
            airingInfo = base.airingInfo.copy(
                kind = SubjectAiringKind.ON_AIR, mainEpisodeCount = 12,
                firstSort = EpisodeSort(25), latestEp = EpisodeSort(9), latestSort = EpisodeSort(33),
            ),
        )
    }

    @Test
    fun countsWatchedEpisodesAcrossGapsAndSeasonOffsetsAndExcludesSpecials() {
        val special = collection.episodes.first().let {
            it.copy(episodeInfo = it.episodeInfo.copy(episodeId = 100, type = EpisodeType.SP))
        }
        val progress = collection.copy(episodes = collection.episodes + special).watchingProgress()
        assertEquals(TvWatchingProgress(watched = 3, aired = 9, total = 12, onAir = true), progress)
        assertEquals(.25f, progress.watchedFraction)
        assertEquals(.75f, progress.airedFraction)
    }

    @Test
    fun completedShowsActualWatchedCountAndReachesFullOnlyWhenEveryEpisodeIsWatched() {
        val completed = collection.copy(airingInfo = collection.airingInfo.copy(kind = SubjectAiringKind.COMPLETED))
        assertEquals(TvWatchingProgress(3, 12, 12, false), completed.watchingProgress())
        val watched = completed.copy(episodes = completed.episodes.map { it.copy(collectionType = UnifiedCollectionType.DONE) })
        assertEquals(1f, watched.watchingProgress().watchedFraction)
    }

    @Test
    fun upcomingAndMissingEpisodeDataDoNotImplyWatchedOrCompleted() {
        val upcoming = collection.copy(
            airingInfo = collection.airingInfo.copy(kind = SubjectAiringKind.UPCOMING, latestSort = null),
            episodes = collection.episodes.map { it.copy(collectionType = UnifiedCollectionType.NOT_COLLECTED) },
        ).watchingProgress()
        assertEquals(TvWatchingProgress(0, 0, 12, false), upcoming)
        val unknown = collection.copy(
            episodes = emptyList(), airingInfo = collection.airingInfo.copy(mainEpisodeCount = 0, latestSort = null),
        ).watchingProgress()
        assertNull(unknown.total)
        assertNull(unknown.aired)
        assertEquals(0f, unknown.watchedFraction)
        assertEquals(0f, unknown.airedFraction)
    }
}
