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
import me.him188.ani.app.data.models.SubjectInfo
import me.him188.ani.app.data.models.episode.EpisodeInfo
import me.him188.ani.app.data.models.episode.isKnownCompleted
import me.him188.ani.app.data.repository.EpisodeCollectionInfo
import me.him188.ani.datasources.api.EpisodeSort
import me.him188.ani.datasources.api.PackedDate
import me.him188.ani.datasources.api.ifInvalid
import me.him188.ani.datasources.api.topic.UnifiedCollectionType

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
        ): SubjectProgressInfo {
            return compute(
                subjectStarted = currentDate > subjectInfo.airDate,
                episodes = episodes.map {
                    Episode(
                        it.episodeId,
                        it.collectionType,
                        it.episodeInfo.sort,
                        it.episodeInfo.airDate,
                        it.episodeInfo.isKnownCompleted,
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
            val lastWatchedEpIndex = episodes.indexOfLast {
                it.type == UnifiedCollectionType.DONE || it.type == UnifiedCollectionType.DROPPED
            }
            val continueWatchingStatus = kotlin.run {
                val latestEp = kotlin.run {
                    episodes.lastOrNull { it.isKnownCompleted }
                }

                // 有剧集 isKnownCompleted == true 时就认为已开播
                val actualSubjectStarted = latestEp != null || subjectStarted

                val latestEpIndex: Int? =
                    episodes.indexOfFirst { it == latestEp }
                        .takeIf { it != -1 }
                        ?: episodes.lastIndex.takeIf { it != -1 }

                when (lastWatchedEpIndex) {
                    // 还没看过
                    -1 -> {
                        if (actualSubjectStarted) {
                            ContinueWatchingStatus.Start
                        } else {
                            ContinueWatchingStatus.NotOnAir(
                                subjectAirDate.ifInvalid { episodes.firstOrNull()?.airDate ?: PackedDate.Invalid },
                            )
                        }
                    }

                    // 看了第 n 集并且还有第 n+1 集
                    in 0..<episodes.size - 1 -> {
                        if (latestEpIndex != null && lastWatchedEpIndex < latestEpIndex && actualSubjectStarted) {
                            // 更新了 n+1 集
                            ContinueWatchingStatus.Continue(
                                lastWatchedEpIndex + 1,
                                episodes.getOrNull(lastWatchedEpIndex + 1)?.sort,
                                episodes[lastWatchedEpIndex].sort,
                            )
                        } else {
                            // 还没更新
                            ContinueWatchingStatus.Watched(
                                lastWatchedEpIndex,
                                episodes.getOrNull(lastWatchedEpIndex)?.sort,
                                episodes.getOrNull(lastWatchedEpIndex + 1)?.airDate ?: PackedDate.Invalid,
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
                    return@run episodes[continueWatchingStatus.episodeIndex]
                } else {
                    if (lastWatchedEpIndex != -1) {
                        episodes.getOrNull(lastWatchedEpIndex + 1)?.let { return@run it }
                        episodes.getOrNull(lastWatchedEpIndex)?.let { return@run it }
                    }

                    episodes.firstOrNull()?.let {
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
    get() = nextEpisodeIdToPlay != null

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
        val episodeIndex: Int,
        val episodeSort: EpisodeSort?, // "12.5"
        val watchedEpisodeSort: EpisodeSort,
    ) : ContinueWatchingStatus()

    /**
     * 看到了, 但是下一集还没更新
     */
    data class Watched(
        val episodeIndex: Int,
        val episodeSort: EpisodeSort?, // "12.5"
        /**
         * Might be [PackedDate.Invalid]
         */
        val nextEpisodeAirDate: PackedDate,
    ) : ContinueWatchingStatus()

    data object Done : ContinueWatchingStatus()
}
