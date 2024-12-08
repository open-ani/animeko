/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.models.subject

import androidx.compose.runtime.Immutable
import kotlinx.datetime.Instant
import me.him188.ani.app.data.models.episode.EpisodeCollectionInfo
import me.him188.ani.app.data.models.episode.EpisodeInfo
import me.him188.ani.datasources.api.EpisodeSort
import me.him188.ani.datasources.api.EpisodeType
import me.him188.ani.datasources.api.PackedDate
import me.him188.ani.datasources.api.topic.UnifiedCollectionType
import me.him188.ani.utils.platform.annotations.TestOnly
import kotlin.random.Random
import kotlin.time.Duration

/**
 * 用户对一个条目的收藏情况
 */
@Immutable
data class SubjectCollectionInfo(
    val collectionType: UnifiedCollectionType,
    val subjectInfo: SubjectInfo,
    val selfRatingInfo: SelfRatingInfo,
    val episodes: List<EpisodeCollectionInfo>, // sorted by episode sort ascending
    val airingInfo: SubjectAiringInfo,
    val progressInfo: SubjectProgressInfo,
//    /**
//     * 是否正在连载中. 此信息从 ani 服务器获取, 独立于 bangumi, 可能获取失败. `null` 表示未知.
//     */
//    val isOnAir: Boolean?,
    /**
     * 连载周期. 仅对连载动画有效. `null` 表示此动画不是连载中, 或者因为各种原因获取失败.
     */
    val recurrence: SubjectRecurrence?,
    val cachedStaffUpdated: Long,
    val cachedCharactersUpdated: Long,

    /**
     * 最后更新时间
     */
    val lastUpdated: Long,
) {
    val subjectId: Int get() = subjectInfo.subjectId
}

data class SubjectRecurrence(
    val startTime: Instant,
    val interval: Duration,
)

@TestOnly
val TestSubjectCollections
    get() = buildList {
        var id = 0
        val eps = listOf(
            EpisodeCollectionInfo(
                episodeInfo = EpisodeInfo(
                    episodeId = 6385,
                    type = EpisodeType.MainStory,
                    name = "Diana Houston",
                    nameCn = "Nita O'Donnell",
                    comment = 5931,
                    desc = "gubergren",
                    sort = EpisodeSort(1),
                    ep = EpisodeSort(1),
                ),
                collectionType = UnifiedCollectionType.DONE,
            ),
            EpisodeCollectionInfo(
                episodeInfo = EpisodeInfo(
                    episodeId = 6386,
                    type = EpisodeType.MainStory,
                    name = "Diana Houston",
                    nameCn = "Nita O'Donnell",
                    sort = EpisodeSort(2),
                    comment = 5931,
                    desc = "gubergren",
                    ep = EpisodeSort(2),
                ),
                collectionType = UnifiedCollectionType.DONE,
            ),

            )
        add(
            testSubjectCollection(++id, eps, UnifiedCollectionType.DOING),
        )
        add(
            testSubjectCollection(++id, eps, UnifiedCollectionType.DOING),
        )
        add(
            testSubjectCollection(++id, eps, UnifiedCollectionType.DOING),
        )
        add(
            testSubjectCollection(++id, eps, collectionType = UnifiedCollectionType.WISH),
        )
        repeat(10) {
            add(
                testSubjectCollection(
                    ++id,
                    episodes = eps + EpisodeCollectionInfo(
                        episodeInfo = EpisodeInfo(
                            episodeId = 6386,
                            type = EpisodeType.MainStory,
                            name = "Diana Houston",
                            nameCn = "Nita O'Donnell",
                            sort = EpisodeSort(2),
                            comment = 5931,
                            desc = "gubergren",
                            ep = EpisodeSort(2),
                        ),
                        collectionType = UnifiedCollectionType.DONE,
                    ),
                    collectionType = UnifiedCollectionType.WISH,
                ),
            )
        }
    }


@TestOnly
private fun testSubjectCollection(
    id: Int,
    episodes: List<EpisodeCollectionInfo>,
    collectionType: UnifiedCollectionType,
): SubjectCollectionInfo {
    val subjectInfo = SubjectInfo.Empty.copy(
        subjectId = id,
        nameCn = "中文条目名称",
        name = "Subject Name",
        nsfw = Random.nextBoolean(),
    )
    return SubjectCollectionInfo(
        collectionType = collectionType,
        subjectInfo = subjectInfo,
        selfRatingInfo = TestSelfRatingInfo,
        episodes = episodes,
        airingInfo = SubjectAiringInfo.computeFromEpisodeList(
            episodes.map { it.episodeInfo },
            airDate = subjectInfo.airDate,
            recurrence = null,
        ),
        progressInfo = SubjectProgressInfo.compute(
            subjectInfo,
            episodes,
            PackedDate.now(),
            recurrence = null,
        ),
        recurrence = null,
        cachedStaffUpdated = 0,
        cachedCharactersUpdated = 0,
        lastUpdated = 0,
    )
}
