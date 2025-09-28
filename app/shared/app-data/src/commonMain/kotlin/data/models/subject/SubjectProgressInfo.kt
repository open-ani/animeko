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
import me.him188.ani.app.data.models.episode.EpisodeCollectionInfo
import me.him188.ani.app.data.models.episode.EpisodeInfo
import me.him188.ani.app.data.models.subject.SubjectProgressInfo.Companion.compute
import me.him188.ani.app.domain.episode.EpisodeCompletionContext.isKnownCompleted
import me.him188.ani.datasources.api.EpisodeSort
import me.him188.ani.datasources.api.PackedDate
import me.him188.ani.datasources.api.ifInvalid
import me.him188.ani.datasources.api.topic.UnifiedCollectionType
import me.him188.ani.utils.platform.annotations.TestOnly

/**
 * 用户对一个条目的观看进度
 *
 * @see SubjectCollection
 */
@Immutable
data class SubjectProgressInfo(
    val continueWatchingStatus: ContinueWatchingStatus,
    /**
     * 供 UI 点击按钮时跳转用
     */
    val nextEpisodeIdToPlay: Int?,
) {
    /**
     * 仅供 [compute]
     */
    class Episode(
        val id: Int,
        val type: UnifiedCollectionType,
        val ep: EpisodeSort?,
        val sort: EpisodeSort,
        /**
         * Might be [PackedDate.Invalid]
         */
        val airDate: PackedDate,
        /**
         * 是否一定已经播出了
         * @see EpisodeInfo.isKnownCompleted
         */
        val isKnownCompleted: Boolean,
    )

    companion object {
        @Stable
        val Done = SubjectProgressInfo(
            ContinueWatchingStatus.Done,
            null,
        )

        fun compute(
            subjectInfo: SubjectInfo,
            episodes: List<EpisodeCollectionInfo>,
            currentDate: PackedDate,
            recurrence: SubjectRecurrence?,
        ): SubjectProgressInfo {
            return compute(
                subjectStarted = currentDate > subjectInfo.airDate,
                episodes = episodes.map {
                    Episode(
                        it.episodeId,
                        it.collectionType,
                        it.episodeInfo.ep,
                        it.episodeInfo.sort,
                        it.episodeInfo.airDate,
                        it.episodeInfo.isKnownCompleted(recurrence),
                    )
                },
                subjectAirDate = subjectInfo.airDate,
            )
        }

        fun compute(
            subjectStarted: Boolean,
            episodes: List<Episode>,
            subjectAirDate: PackedDate,
        ): SubjectProgressInfo {
            // 进度应该仅考虑普通剧集，并且按 sort 排序
            val sortedNormalEpisodes = episodes
                .filter { it.sort is EpisodeSort.Normal }
                .sortedBy { it.sort }

            val lastWatchedEpIndex = sortedNormalEpisodes.indexOfLast {
                it.type == UnifiedCollectionType.DONE || it.type == UnifiedCollectionType.DROPPED
            }

            val continueWatchingStatus = kotlin.run {
                val latestEp = kotlin.run {
                    sortedNormalEpisodes.lastOrNull { it.isKnownCompleted }
                }

                // 有剧集 isKnownCompleted == true 时就认为已开播
                val actualSubjectStarted = latestEp != null || subjectStarted

                val latestEpIndex: Int? =
                    sortedNormalEpisodes.indexOfFirst { it == latestEp }
                        .takeIf { it != -1 }
                        ?: sortedNormalEpisodes.lastIndex.takeIf { it != -1 }

                when (lastWatchedEpIndex) {
                    // 还没看过
                    -1 -> {
                        if (actualSubjectStarted) {
                            ContinueWatchingStatus.Start
                        } else {
                            ContinueWatchingStatus.NotOnAir(
                                subjectAirDate.ifInvalid {
                                    sortedNormalEpisodes.firstOrNull()?.airDate ?: PackedDate.Invalid
                                },
                            )
                        }
                    }

                    // 看了第 n 集并且还有第 n+1 集
                    in 0..<sortedNormalEpisodes.size - 1 -> {
                        if (latestEpIndex != null && lastWatchedEpIndex < latestEpIndex && actualSubjectStarted) {
                            // 更新了 n+1 集
                            ContinueWatchingStatus.Continue(
                                episodeEp = sortedNormalEpisodes.getOrNull(lastWatchedEpIndex + 1)?.ep,
                                episodeSort = sortedNormalEpisodes.getOrNull(lastWatchedEpIndex + 1)?.sort,
                                watchedEpisodeEp = sortedNormalEpisodes[lastWatchedEpIndex].ep,
                                watchedEpisodeSort = sortedNormalEpisodes[lastWatchedEpIndex].sort,
                            )
                        } else {
                            // 还没更新
                            ContinueWatchingStatus.Watched(
                                sortedNormalEpisodes.getOrNull(lastWatchedEpIndex)?.ep,
                                sortedNormalEpisodes.getOrNull(lastWatchedEpIndex)?.sort,
                                sortedNormalEpisodes.getOrNull(lastWatchedEpIndex + 1)?.airDate
                                    ?: PackedDate.Invalid,
                            )
                        }
                    }

                    else -> {
                        ContinueWatchingStatus.Done
                    }
                }
            }

            val episodeToPlay = kotlin.run {
                if (continueWatchingStatus is ContinueWatchingStatus.Watched) {
                    return@run sortedNormalEpisodes[lastWatchedEpIndex]
                } else {
                    if (lastWatchedEpIndex != -1) {
                        sortedNormalEpisodes.getOrNull(lastWatchedEpIndex + 1)?.let { return@run it }
                        sortedNormalEpisodes.getOrNull(lastWatchedEpIndex)?.let { return@run it }
                    }

                    sortedNormalEpisodes.firstOrNull()?.let {
                        return@run it
                    }
                }

                null
            }

            return SubjectProgressInfo(
                continueWatchingStatus,
                episodeToPlay?.id,
            )
        }
    }
}

@Stable
inline val SubjectProgressInfo.hasNewEpisodeToPlay: Boolean
    get() = continueWatchingStatus is ContinueWatchingStatus.Start || continueWatchingStatus is ContinueWatchingStatus.Continue

sealed class ContinueWatchingStatus {
    data object Start : ContinueWatchingStatus()

    /**
     * 还未开播
     */
    data class NotOnAir(
        val airDate: PackedDate,
    ) : ContinueWatchingStatus()

    /**
     * 继续看
     */
    data class Continue(
        val episodeEp: EpisodeSort?,
        val episodeSort: EpisodeSort?, // "12.5"
        val watchedEpisodeEp: EpisodeSort?,
        val watchedEpisodeSort: EpisodeSort,
    ) : ContinueWatchingStatus()

    /**
     * 看到了, 但是下一集还没更新
     */
    data class Watched(
        val episodeEp: EpisodeSort?, // "12.5"
        val episodeSort: EpisodeSort?, // "24.5"
        /**
         * Might be [PackedDate.Invalid]
         */
        val nextEpisodeAirDate: PackedDate,
    ) : ContinueWatchingStatus()

    data object Done : ContinueWatchingStatus()
}


@Stable
@TestOnly
object TestSubjectProgressInfos {
    @Stable
    val NotOnAir = SubjectProgressInfo(
        continueWatchingStatus = ContinueWatchingStatus.NotOnAir(PackedDate.Invalid),
        nextEpisodeIdToPlay = null,
    )

    @Stable
    val ContinueWatching2 = SubjectProgressInfo(
        continueWatchingStatus = ContinueWatchingStatus.Continue(
            episodeEp = EpisodeSort(2),
            episodeSort = EpisodeSort(2),
            watchedEpisodeEp = EpisodeSort(1),
            watchedEpisodeSort = EpisodeSort(1),
        ),
        nextEpisodeIdToPlay = null,
    )

    @Stable
    val Watched2 = SubjectProgressInfo(
        continueWatchingStatus = ContinueWatchingStatus.Watched(EpisodeSort(2), EpisodeSort(2), PackedDate.Invalid),
        nextEpisodeIdToPlay = null,
    )

    @Stable
    val Done = SubjectProgressInfo(
        continueWatchingStatus = ContinueWatchingStatus.Done,
        nextEpisodeIdToPlay = null,
    )
}
