/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.models.subject

import androidx.compose.runtime.Immutable
import me.him188.ani.app.data.models.episode.EpisodeCollectionInfo
import me.him188.ani.app.data.models.episode.EpisodeInfo
import me.him188.ani.app.data.models.preference.NsfwMode
import me.him188.ani.app.data.persistent.database.dao.SubjectRelations
import me.him188.ani.datasources.api.EpisodeSort
import me.him188.ani.datasources.api.EpisodeType
import me.him188.ani.datasources.api.PackedDate
import me.him188.ani.datasources.api.topic.UnifiedCollectionType
import me.him188.ani.utils.platform.annotations.TestOnly
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Instant

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
     * 连载周期, 来自 bangumi-data 的 `broadcast`.
     * `null` 表示 bangumi-data 中没有此条目的 `broadcast` 信息 (与是否完结无关: 完结番同样保留 `broadcast`),
     * 或者因为各种原因获取失败.
     */
    val recurrence: SubjectRecurrence?,
    val cachedStaffUpdated: Long,
    val cachedCharactersUpdated: Long,

    /**
     * 最后更新时间
     */
    val lastUpdated: Long,
    val nsfwMode: NsfwMode,

    /**
     * @since 5.0.0
     */
    val relations: SubjectRelations,
) {
    val subjectId: Int get() = subjectInfo.subjectId
}

/**
 * 条目的连载周期, 来自 bangumi-data 的 `broadcast` (`R/<startTime>/<interval>`).
 *
 * 由上映日期计算一集的播出时刻见 [me.him188.ani.app.domain.episode.EpisodeCompletionContext.resolveEpisodeAirTime].
 */
data class SubjectRecurrence(
    /** 首播时间 (UTC) */
    val startTime: Instant,
    /** 两集之间的时间间隔，例如一周 = `7.days`. 可能为 0 (bangumi-data 的 `P0D`, 一次性放送). */
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
            createTestSubjectCollection(++id, eps, UnifiedCollectionType.DOING),
        )
        add(
            createTestSubjectCollection(++id, eps, UnifiedCollectionType.DOING),
        )
        add(
            createTestSubjectCollection(++id, eps, UnifiedCollectionType.DOING),
        )
        add(
            createTestSubjectCollection(++id, eps, collectionType = UnifiedCollectionType.WISH),
        )
        repeat(10) {
            add(
                createTestSubjectCollection(
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
fun createTestSubjectCollection(
    id: Int,
    episodes: List<EpisodeCollectionInfo>,
    collectionType: UnifiedCollectionType,
    nsfwMode: NsfwMode = NsfwMode.DISPLAY,
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
        nsfwMode = nsfwMode,
        relations = SubjectRelations.Empty,
    )
}
