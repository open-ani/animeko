/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.subject.episode.list

import androidx.compose.runtime.Immutable
import me.him188.ani.app.data.models.subject.SubjectCollectionInfo
import me.him188.ani.app.data.models.subject.SubjectRecurrence
import me.him188.ani.app.domain.episode.EpisodeCompletionContext
import me.him188.ani.app.domain.episode.EpisodeCompletionContext.mapAirDate
import me.him188.ani.datasources.api.EpisodeSort
import me.him188.ani.datasources.api.EpisodeType
import me.him188.ani.datasources.api.PackedDate
import me.him188.ani.utils.platform.annotations.TestOnly
import me.him188.ani.utils.serialization.BigNum
import kotlin.time.Instant

@Immutable
data class EpisodeListUiState(
    val subjectTitle: String,
    val mainEpisodes: List<EpisodeListItem>,
    val otherEpisodes: List<EpisodeListItem>,
    val isPlaceholder: Boolean = false,
) {
    companion object {
        fun from(
            collection: SubjectCollectionInfo,
            currentTime: Instant,
        ): EpisodeListUiState {
            val (mainEpisodes, otherEpisodes) = collection.episodes.map { episode ->
                EpisodeListItem.from(
                    episode,
                    isBroadcast = isEpisodeBroadcast(collection.recurrence, episode.episodeInfo.airDate, currentTime),
                )
            }.partition {
                it.sort is EpisodeSort.Normal
            }

            return EpisodeListUiState(
                subjectTitle = collection.subjectInfo.displayName,
                mainEpisodes = mainEpisodes.sortedBy { it.sort },
                otherEpisodes = otherEpisodes.sortedBy { it.sort },
            )
        }

        /**
         * 剧集列表的 "未开播" 着色规则.
         *
         * - 能算出播出时刻 (见 [EpisodeCompletionContext.mapAirDate]) 时, 以 [currentTime] 是否已到达该时刻为准;
         * - 算不出时 (剧集没有上映日期): 有 [recurrence] 说明条目仍在连载, 没有日期的剧集视为未开播;
         *   没有 [recurrence] 时保持显示为已开播.
         *
         * 注意 [recurrence] 为 `null` 只表示 bangumi-data 没有该条目的 `broadcast` 信息, 与是否完结无关 (完结番同样保留 `broadcast`).
         */
        fun isEpisodeBroadcast(
            recurrence: SubjectRecurrence?,
            airDate: PackedDate,
            currentTime: Instant,
        ): Boolean = recurrence.mapAirDate(airDate)?.let { it <= currentTime } ?: (recurrence == null)

        val Placeholder = EpisodeListUiState(
            subjectTitle = "",
            mainEpisodes = emptyList(),
            otherEpisodes = emptyList(),
            isPlaceholder = true,
        )
    }
}

@TestOnly
val TestEpisodeListUiState
    get() = EpisodeListUiState(
        subjectTitle = "测试标题",
        mainEpisodes = TestEpisodeListItems,
        otherEpisodes = TestEpisodeListItems.take(2)
            .map { it.copy(sort = EpisodeSort(BigNum(it.sort.number!!), EpisodeType.SP)) },
    )

@TestOnly
val TestEpisodeListUiStateVeryLong
    get() = EpisodeListUiState(
        subjectTitle = "测试标题",
        mainEpisodes = buildList {
            repeat(100) {
                add(createTestEpisodeListItem(EpisodeSort(it + 1)))
            }
        },
        otherEpisodes = TestEpisodeListItems.take(2)
            .map { it.copy(sort = EpisodeSort(BigNum(it.sort.number!!), EpisodeType.SP)) },
    )

@TestOnly
val TestEpisodeListItems
    get() = buildList {
        repeat(12) {
            add(createTestEpisodeListItem(EpisodeSort(it + 1)))
        }
    }
