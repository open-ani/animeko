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
import me.him188.ani.app.data.models.episode.isKnownCompleted
import me.him188.ani.app.data.models.episode.isKnownOnAir
import me.him188.ani.datasources.api.EpisodeSort
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
    val episodeCount: Int,
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
            episodeCount = 0,
            airDate = PackedDate.Invalid,
            firstSort = null,
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
        ): SubjectAiringInfo {
            // See test SubjectAiringInfoTest
            // Change with care!
            val kind = when {
                list.isEmpty() -> SubjectAiringKind.UPCOMING
                list.all { it.isKnownCompleted } -> SubjectAiringKind.COMPLETED
                list.all { it.isKnownOnAir } -> SubjectAiringKind.UPCOMING
                list.any { it.isKnownCompleted } -> SubjectAiringKind.ON_AIR
                airDate.isValid -> when {
                    airDate <= PackedDate.now() -> SubjectAiringKind.COMPLETED
                    PackedDate.now() - airDate > 10 * 30 * 365.days -> SubjectAiringKind.COMPLETED // 播出 10 年后判定为完结, 一些老番缺失信息
                    else -> SubjectAiringKind.UPCOMING
                }

                else -> SubjectAiringKind.UPCOMING
            }
            return SubjectAiringInfo(
                kind = kind,
                episodeCount = list.size,
                airDate = airDate.ifInvalid { list.firstOrNull()?.airDate ?: PackedDate.Invalid },
                firstSort = list.firstOrNull()?.sort,
                latestSort = list.lastOrNull { it.isKnownCompleted }?.sort,
                upcomingSort = if (kind == SubjectAiringKind.COMPLETED) null else list.firstOrNull { it.isKnownOnAir }?.sort
                    ?: list.firstOrNull { it.airDate.isInvalid }?.sort,
            )
        }

        /**
         * 在无剧集信息的时候使用, 估算
         */
        fun computeFromSubjectInfo(
            info: SubjectInfo
        ): SubjectAiringInfo {
            val kind = when {
                info.completeDate.isValid -> SubjectAiringKind.COMPLETED
                info.airDate < PackedDate.now() -> SubjectAiringKind.ON_AIR
                else -> SubjectAiringKind.UPCOMING
            }
            return SubjectAiringInfo(
                kind = kind,
                episodeCount = info.totalEpisodes,
                airDate = info.airDate,
                firstSort = null,
                latestSort = null,
                upcomingSort = null,
            )
        }
    }
}

// Mainly for SubjectAiringLabel
@Stable
fun SubjectAiringInfo.computeTotalEpisodeText(): String? {
    return if (kind == SubjectAiringKind.UPCOMING && episodeCount == 0) {
        // 剧集还未知
        null
    } else {
        when (kind) {
            SubjectAiringKind.COMPLETED -> "全 $episodeCount 话"

            SubjectAiringKind.ON_AIR,
            SubjectAiringKind.UPCOMING,
                -> "预定全 $episodeCount 话"
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
