/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.models.subject

import me.him188.ani.app.data.models.subject.SubjectProgressInfo.Episode
import me.him188.ani.datasources.api.EpisodeSort
import me.him188.ani.datasources.api.EpisodeType
import me.him188.ani.datasources.api.PackedDate
import me.him188.ani.datasources.api.PackedDate.Companion.Invalid
import me.him188.ani.datasources.api.topic.UnifiedCollectionType
import me.him188.ani.datasources.api.topic.UnifiedCollectionType.DONE
import me.him188.ani.datasources.api.topic.UnifiedCollectionType.DROPPED
import me.him188.ani.datasources.api.topic.UnifiedCollectionType.WISH
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * @see me.him188.ani.app.data.models.subject.SubjectProgressInfo
 */
class SubjectProgressInfoTest {
    private fun ep(
        type: UnifiedCollectionType,
        sort: Int,
        isKnownCompleted: Boolean,
        airDate: PackedDate = Invalid,
        id: Int = sort,
        episodeType: EpisodeType? = EpisodeType.MainStory, // 默认为主线剧集
    ): Episode = Episode(
        id, type, EpisodeSort(sort, episodeType), EpisodeSort(sort, episodeType),
        airDate,
        isKnownCompleted,
    )

    private fun calculate(
        subjectStarted: Boolean,
        episodes: List<Episode>,
        subjectAirDate: PackedDate = Invalid,
    ): SubjectProgressInfo {
        return SubjectProgressInfo.compute(
            subjectStarted,
            episodes,
            subjectAirDate,
        )
    }

    @Test
    fun `subject not started - no ep`() {
        calculate(
            subjectStarted = false,
            episodes = listOf(),
        ).run {
            assertEquals(ContinueWatchingStatus.NotOnAir(Invalid), continueWatchingStatus)
            assertEquals(null, nextEpisodeIdToPlay)
        }
    }

    @Test
    fun `subject not started - no ep - with time`() {
        calculate(
            subjectStarted = false,
            episodes = listOf(),
            subjectAirDate = PackedDate(2024, 8, 24),
        ).run {
            assertEquals(ContinueWatchingStatus.NotOnAir(PackedDate(2024, 8, 24)), continueWatchingStatus)
            assertEquals(null, nextEpisodeIdToPlay)
        }
    }

    @Test
    fun `subject not started - one ep`() {
        calculate(
            subjectStarted = false,
            episodes = listOf(
                ep(WISH, 1, isKnownCompleted = false),
            ),
        ).run {
            assertEquals(ContinueWatchingStatus.NotOnAir(Invalid), continueWatchingStatus)
            assertEquals(1, nextEpisodeIdToPlay)
        }
    }

    @Test
    fun `subject not started - first episode wish`() {
        calculate(
            subjectStarted = false,
            episodes = listOf(
                ep(WISH, 1, isKnownCompleted = false),
                ep(WISH, 2, isKnownCompleted = false),
            ),
        ).run {
            assertEquals(ContinueWatchingStatus.NotOnAir(Invalid), continueWatchingStatus)
            assertEquals(1, nextEpisodeIdToPlay)
        }
    }

    @Test
    fun `subject not started - first ep done - second ep not completed`() {
        calculate(
            subjectStarted = false,
            episodes = listOf(
                ep(DONE, 1, isKnownCompleted = true),
                ep(WISH, 2, isKnownCompleted = false),
            ),
        ).run {
            assertEquals(
                ContinueWatchingStatus.Watched(EpisodeSort(1), EpisodeSort(1), Invalid),
                continueWatchingStatus,
            )
            assertEquals(1, nextEpisodeIdToPlay)
        }
    }

    @Test
    fun `subject not started - first ep done - first ep not completed`() {
        calculate(
            subjectStarted = false,
            episodes = listOf(
                ep(DONE, 1, isKnownCompleted = false),
                ep(WISH, 2, isKnownCompleted = false),
            ),
        ).run {
            assertEquals(
                ContinueWatchingStatus.Watched(EpisodeSort(1), EpisodeSort(1), Invalid),
                continueWatchingStatus,
            )
            assertEquals(1, nextEpisodeIdToPlay)
        }
    }

    @Test
    fun `subject not started - first ep done - second ep completed`() {
        calculate(
            subjectStarted = false,
            episodes = listOf(
                ep(DONE, 1, isKnownCompleted = true),
                ep(WISH, 2, isKnownCompleted = true),
            ),
        ).run {
            assertEquals(continue2_1(), continueWatchingStatus)
            assertEquals(2, nextEpisodeIdToPlay)
        }
    }

    @Test
    fun `first episode wish`() {
        calculate(
            subjectStarted = true,
            episodes = listOf(
                ep(WISH, 1, isKnownCompleted = false),
                ep(WISH, 2, isKnownCompleted = false),
            ),
        ).run {
            assertEquals(ContinueWatchingStatus.Start, continueWatchingStatus)
            assertEquals(1, nextEpisodeIdToPlay)
        }
    }

    @Test
    fun `first ep done - second ep not completed`() {
        calculate(
            subjectStarted = true,
            episodes = listOf(
                ep(DONE, 1, isKnownCompleted = true),
                ep(WISH, 2, isKnownCompleted = false),
            ),
        ).run {
            assertEquals(
                ContinueWatchingStatus.Watched(EpisodeSort(1), EpisodeSort(1), Invalid),
                continueWatchingStatus,
            )
            assertEquals(1, nextEpisodeIdToPlay)
        }
    }

    @Test
    fun `first ep done - second ep completed`() {
        calculate(
            subjectStarted = true,
            episodes = listOf(
                ep(DONE, 1, isKnownCompleted = true),
                ep(WISH, 2, isKnownCompleted = true),
            ),
        ).run {
            assertEquals(continue2_1(), continueWatchingStatus)
            assertEquals(2, nextEpisodeIdToPlay)
        }
    }

    @Test
    fun `first ep dropped - second ep completed`() {
        calculate(
            subjectStarted = true,
            episodes = listOf(
                ep(DROPPED, 1, isKnownCompleted = true),
                ep(WISH, 2, isKnownCompleted = true),
            ),
        ).run {
            assertEquals(continue2_1(), continueWatchingStatus)
            assertEquals(2, nextEpisodeIdToPlay)
        }
    }

    private fun continue2_1() =
        ContinueWatchingStatus.Continue(EpisodeSort(2), EpisodeSort(2), EpisodeSort(1), EpisodeSort(1))

    @Test
    fun `all ep done`() {
        calculate(
            subjectStarted = true,
            episodes = listOf(
                ep(DONE, 1, isKnownCompleted = true),
                ep(DONE, 2, isKnownCompleted = true),
            ),
        ).run {
            assertEquals(ContinueWatchingStatus.Done, continueWatchingStatus)
            assertEquals(2, nextEpisodeIdToPlay)
        }
    }

    // https://github.com/open-ani/animeko/issues/1871
    // 新添加的 00 排在最后
    // https://bgm.tv/subject/1730
    @Test
    fun `episodes with 00 at end`() {
        calculate(
            subjectStarted = true,
            episodes = listOf(
                ep(DONE, 1, isKnownCompleted = true),  // 主线第1集
                ep(WISH, 2, isKnownCompleted = true),  // 主线第2集，未看
                ep(WISH, 3, isKnownCompleted = true),  // 主线第3集，未看
                ep(DONE, 0, isKnownCompleted = true), // 主线第0集
            ),
        ).run {
            // 最后看的主线剧集应该是第1集，下一集应该是第2集
            assertEquals(
                ContinueWatchingStatus.Continue(EpisodeSort(2), EpisodeSort(2), EpisodeSort(1), EpisodeSort(1)),
                continueWatchingStatus,
            )
            assertEquals(2, nextEpisodeIdToPlay)
        }
    }

    // 有 SP 且在最后并且已完成
    // https://bgm.tv/subject/506677
    @Test
    fun `episodes with sp done`() {
        calculate(
            subjectStarted = true,
            episodes = listOf(
                ep(DONE, 1, isKnownCompleted = true),  // 主线第1集
                ep(WISH, 2, isKnownCompleted = true),  // 主线第2集，未看
                ep(WISH, 3, isKnownCompleted = true),  // 主线第3集，未看
                ep(DONE, 4, isKnownCompleted = true, episodeType = EpisodeType.SP),
            ),
        ).run {
            // 最后看的主线剧集应该是第1集，下一集应该是第2集，应该排除掉 SP 干扰
            assertEquals(
                ContinueWatchingStatus.Continue(EpisodeSort(2), EpisodeSort(2), EpisodeSort(1), EpisodeSort(1)),
                continueWatchingStatus,
            )
            assertEquals(2, nextEpisodeIdToPlay)
        }
    }
}

