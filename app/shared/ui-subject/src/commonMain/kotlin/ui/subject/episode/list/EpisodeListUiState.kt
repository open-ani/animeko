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
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import me.him188.ani.app.data.models.subject.SubjectCollectionInfo
import me.him188.ani.datasources.api.EpisodeSort
import me.him188.ani.datasources.api.EpisodeType
import me.him188.ani.utils.platform.annotations.TestOnly
import me.him188.ani.utils.serialization.BigNum

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
            zone: TimeZone = TimeZone.Companion.currentSystemDefault()
        ): EpisodeListUiState {
            val (mainEpisodes, otherEpisodes) = collection.episodes.map { episode ->
                EpisodeListItem.from(
                    episode,
                    isBroadcast = collection.recurrence?.isEpisodeBroadcast(
                        episode.episodeInfo.airDate,
                        currentTime,
                        zone,
                    ) ?: true, // 注意, 没有 recurrence 时需要为 true. 因为完结番没有 recurrence.
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
