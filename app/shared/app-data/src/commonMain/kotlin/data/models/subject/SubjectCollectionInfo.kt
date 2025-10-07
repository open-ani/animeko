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
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.toLocalDateTime
import me.him188.ani.app.data.models.episode.EpisodeCollectionInfo
import me.him188.ani.app.data.models.episode.EpisodeInfo
import me.him188.ani.app.data.models.preference.NsfwMode
import me.him188.ani.app.data.persistent.database.dao.SubjectRelations
import me.him188.ani.datasources.api.EpisodeSort
import me.him188.ani.datasources.api.EpisodeType
import me.him188.ani.datasources.api.PackedDate
import me.him188.ani.datasources.api.toLocalDateOrNull
import me.him188.ani.datasources.api.topic.UnifiedCollectionType
import me.him188.ani.utils.platform.annotations.TestOnly
import kotlin.math.abs
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
     * 连载周期. 仅对连载动画有效. `null` 表示此动画不是连载中, 或者因为各种原因获取失败.
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

data class SubjectRecurrence(
    /** 首播时间 (UTC) */
    val startTime: Instant,
    /** 两集之间的时间间隔，例如一周 = `7.days` */
    val interval: Duration,
) {
    /**
     * 根据首播时间和两集间隔，**猜测**某个日期对应集数的大致首播时间。
     *
     * * `packedDate` 会被转换为本地日历日 (`LocalDate`)；
     * * 只要候选时间与该日期 *午夜* 的差值不超过 **24 小时** (±1 day) 就视为有效；
     * * 若待估日期早于首播日、间隔为 0/负数，或无法在容差内匹配，则返回 `null`。
     */
    fun calculateEpisodeAirTime(
        packedDate: PackedDate,
        zone: TimeZone = TimeZone.currentSystemDefault(),
    ): Instant? {
        val localDate: LocalDate = packedDate.toLocalDateOrNull() ?: return null

        val startDate: LocalDate = startTime.toLocalDateTime(zone).date
        val intervalDays = interval.inWholeDays
        if (intervalDays <= 0) return null

        val approxIndex = startDate.daysUntil(localDate) / intervalDays
        val candidateIndices = listOf(approxIndex - 1, approxIndex, approxIndex + 1).filter { it >= 0 }

        for (idx in candidateIndices) {
            val candidate = startTime + interval * idx.toInt()
            val candidateDate = candidate.toLocalDateTime(zone).date
            // 接受本地日期差 ≤ 1 的候选
            if (abs(localDate.daysUntil(candidateDate)) <= 1) {
                return candidate
            }
        }
        return null
    }
    /**
     * @see [calculateEpisodeAirTime]
     */
    fun isEpisodeBroadcast(
        packedDate: PackedDate,
        currentTime: Instant,
        zone: TimeZone = TimeZone.currentSystemDefault()
    ): Boolean {
        val time = calculateEpisodeAirTime(packedDate, zone) ?: return false
        return time <= currentTime
    }
}

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
