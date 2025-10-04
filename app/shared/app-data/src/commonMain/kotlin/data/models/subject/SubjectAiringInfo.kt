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
import androidx.compose.runtime.Stable
import me.him188.ani.app.data.models.episode.EpisodeInfo
import me.him188.ani.app.domain.episode.EpisodeCompletionContext.isKnownCompleted
import me.him188.ani.app.domain.episode.EpisodeCompletionContext.isKnownOnAir
import me.him188.ani.datasources.api.EpisodeSort
import me.him188.ani.datasources.api.EpisodeType
import me.him188.ani.datasources.api.PackedDate
import me.him188.ani.datasources.api.ifInvalid
import me.him188.ani.datasources.api.minus
import kotlin.time.Duration.Companion.days
import kotlin.time.times

/**
 * 一个条目自身连载进度, 与用户是否观看无关
 *
 * @see SubjectProgressInfo
 */
@Immutable
data class SubjectAiringInfo(
    val kind: SubjectAiringKind,
    /**
     * 总集数
     */
    val mainEpisodeCount: Int,
    /**
     * 首播日期
     */
    val airDate: PackedDate,
    /**
     * 不考虑连载情况的第一集序号, 当这个条目没有任何剧集时为 `null`.
     * 注意, 如果是来自 [SubjectAiringInfo.computeFromSubjectInfo], 则属性为 `null`.
     */
    val firstSort: EpisodeSort?,
    /**
     * 连载至的最新一集序号. 当还未开播时为 `null`, 当已经完结时为最后一集序号.
     * 注意, 如果是来自 [SubjectAiringInfo.computeFromSubjectInfo], 则属性为 `null`.
     */
    val latestEp: EpisodeSort?,
    val latestSort: EpisodeSort?,
    /**
     * 即将要播出的序号. 当还未开播时为第一集的序号, 当已经完结时为 `null`.
     * 注意, 如果是来自 [SubjectAiringInfo.computeFromSubjectInfo], 则属性为 `null`.
     */
    val upcomingSort: EpisodeSort?,
) {
    companion object {
        @Stable
        val EmptyCompleted = SubjectAiringInfo(
            SubjectAiringKind.COMPLETED,
            mainEpisodeCount = 0,
            airDate = PackedDate.Invalid,
            firstSort = null,
            latestEp = null,
            latestSort = null,
            upcomingSort = null,
        )

//        /**
//         * 在有剧集信息的时候使用, 计算最准确的结果
//         */
//        fun computeFromFirstAndLastEpisode(
//            firstEpisode: EpisodeInfo?,
//            lastEpisode: EpisodeInfo?,
//            episodeCount: Int,
//            airDate: PackedDate,
//        ): SubjectAiringInfo {
//            // See test SubjectAiringInfoTest
//            // Change with care!
//            val kind = if (firstEpisode == null && lastEpisode == null) {
//                SubjectAiringKind.UPCOMING
//            } else {
//                check(firstEpisode != null && lastEpisode != null) {
//                    "firstEpisode and lastEpisode must be both null or both not null"
//                }
//                when {
//                    firstEpisode.isKnownCompleted && lastEpisode.isKnownCompleted -> SubjectAiringKind.COMPLETED
//                    firstEpisode.isKnownOnAir && lastEpisode.isKnownOnAir -> SubjectAiringKind.UPCOMING
//                    firstEpisode.isKnownCompleted || lastEpisode.isKnownCompleted -> SubjectAiringKind.ON_AIR
//                    airDate.isValid -> when {
//                        airDate <= PackedDate.now() -> SubjectAiringKind.COMPLETED
//                        PackedDate.now() - airDate > 10 * 30 * 365.days -> SubjectAiringKind.COMPLETED // 播出 10 年后判定为完结, 一些老番缺失信息
//                        else -> SubjectAiringKind.UPCOMING
//                    }
//
//                    else -> SubjectAiringKind.UPCOMING
//                }
//            }
//            return SubjectAiringInfo(
//                kind = kind,
//                episodeCount = episodeCount,
//                airDate = airDate.ifInvalid { list.firstOrNull()?.airDate ?: PackedDate.Invalid },
//                firstSort = list.firstOrNull()?.sort,
//                latestSort = list.lastOrNull { it.isKnownCompleted }?.sort,
//                upcomingSort = if (kind == SubjectAiringKind.COMPLETED) null else list.firstOrNull { it.isKnownOnAir }?.sort
//                    ?: list.firstOrNull { it.airDate.isInvalid }?.sort,
//            )
//        }

        /**
         * 在有剧集信息的时候使用, 计算最准确的结果
         */
        fun computeFromEpisodeList(
            list: List<EpisodeInfo>,
            airDate: PackedDate,
            recurrence: SubjectRecurrence?,
        ): SubjectAiringInfo {
            // See test SubjectAiringInfoTest
            // Change with care!

            // 仅保留正片
            val mainStoryEpisodes = list.filter { it.type == EpisodeType.MainStory }

            val kind = when {
                mainStoryEpisodes.isEmpty() -> SubjectAiringKind.UPCOMING
                mainStoryEpisodes.all { it.isKnownCompleted(recurrence) } -> SubjectAiringKind.COMPLETED
                mainStoryEpisodes.all { it.isKnownOnAir(recurrence) } -> SubjectAiringKind.UPCOMING
                mainStoryEpisodes.any { it.isKnownCompleted(recurrence) } -> SubjectAiringKind.ON_AIR
                airDate.isValid -> when {
                    airDate <= PackedDate.now() -> SubjectAiringKind.COMPLETED
                    PackedDate.now() - airDate > 10 * 30 * 365.days -> SubjectAiringKind.COMPLETED // 播出 10 年后判定为完结, 一些老番缺失信息
                    else -> SubjectAiringKind.UPCOMING
                }

                else -> SubjectAiringKind.UPCOMING
            }

            return SubjectAiringInfo(
                kind = kind,
                mainEpisodeCount = mainStoryEpisodes.size,
                airDate = airDate.ifInvalid { mainStoryEpisodes.firstOrNull()?.airDate ?: PackedDate.Invalid },
                firstSort = mainStoryEpisodes.firstOrNull()?.sort,
                latestEp = mainStoryEpisodes.lastOrNull { it.isKnownCompleted(recurrence) }?.sort,
                latestSort = mainStoryEpisodes.lastOrNull { it.isKnownCompleted(recurrence) }?.sort,
                upcomingSort = if (kind == SubjectAiringKind.COMPLETED) {
                    null
                } else {
                    mainStoryEpisodes.firstOrNull { it.isKnownOnAir(recurrence) }?.sort
                        ?: mainStoryEpisodes.firstOrNull { it.airDate.isInvalid }?.sort
                },
            )
        }

        /**
         * 在无剧集信息的时候使用, 估算
         */
        fun computeFromSubjectInfo(
            info: SubjectInfo,
            mainEpisodeCount: Int,
        ): SubjectAiringInfo {
            val kind = when {
                info.completeDate.isValid -> SubjectAiringKind.COMPLETED
                info.airDate < PackedDate.now() -> SubjectAiringKind.ON_AIR
                else -> SubjectAiringKind.UPCOMING
            }
            return SubjectAiringInfo(
                kind = kind,
                mainEpisodeCount = mainEpisodeCount,
                airDate = info.airDate,
                firstSort = null,
                latestEp = null,
                latestSort = null,
                upcomingSort = null,
            )
        }
    }
}

object TestSubjectAiringInfos {
    val OnAir12Eps = SubjectAiringInfo(
        SubjectAiringKind.ON_AIR,
        mainEpisodeCount = 12,
        airDate = PackedDate(2023, 10, 1),
        firstSort = EpisodeSort(1),
        latestEp = EpisodeSort(2),
        latestSort = EpisodeSort(2),
        upcomingSort = EpisodeSort(3),
    )

    val Upcoming24Eps = SubjectAiringInfo(
        SubjectAiringKind.UPCOMING,
        mainEpisodeCount = 24,
        airDate = PackedDate(2023, 10, 1),
        firstSort = EpisodeSort(1),
        latestEp = null,
        latestSort = null,
        upcomingSort = EpisodeSort(1),
    )

    val Completed12Eps = SubjectAiringInfo(
        SubjectAiringKind.COMPLETED,
        mainEpisodeCount = 12,
        airDate = PackedDate(2023, 10, 1),
        firstSort = EpisodeSort(1),
        latestEp = EpisodeSort(12),
        latestSort = EpisodeSort(12),
        upcomingSort = null,
    )
}

// Mainly for SubjectAiringLabel
@Stable
fun SubjectAiringInfo.computeTotalEpisodeText(): String? {
    return if (kind == SubjectAiringKind.UPCOMING && mainEpisodeCount == 0) {
        // 剧集还未知
        null
    } else {
        when (kind) {
            SubjectAiringKind.COMPLETED -> "全 $mainEpisodeCount 话"

            SubjectAiringKind.ON_AIR,
            SubjectAiringKind.UPCOMING,
                -> "预定全 $mainEpisodeCount 话"
        }
    }
}

/**
 * 正在播出 (第一集还未开播, 将在未来开播)
 */
@Stable
val SubjectAiringInfo.isOnAir: Boolean
    get() = kind == SubjectAiringKind.ON_AIR

@Stable
val SubjectAiringInfo.hasStarted: Boolean
    get() = isOnAir || isCompleted

/**
 * 即将开播 (已经播出了第一集, 但还未播出最后一集)
 */
@Stable
val SubjectAiringInfo.isUpcoming: Boolean
    get() = kind == SubjectAiringKind.UPCOMING

/**
 * 已完结 (最后一集已经播出)
 */
@Stable
val SubjectAiringInfo.isCompleted: Boolean
    get() = kind == SubjectAiringKind.COMPLETED

@Immutable
enum class SubjectAiringKind {
    /**
     * 即将开播 (已经播出了第一集, 但还未播出最后一集)
     */
    UPCOMING,

    /**
     * 正在播出 (第一集还未开播, 将在未来开播)
     */
    ON_AIR,

    /**
     * 已完结 (最后一集已经播出)
     */
    COMPLETED,
}
