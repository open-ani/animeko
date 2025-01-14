/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.exploration.schedule

import androidx.collection.MutableIntObjectMap
import androidx.compose.runtime.Immutable
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import me.him188.ani.app.data.models.episode.EpisodeInfo
import me.him188.ani.app.data.models.episode.displayName
import me.him188.ani.app.data.models.schedule.OnAirAnimeInfo
import me.him188.ani.app.data.models.subject.SubjectCollectionInfo
import me.him188.ani.datasources.api.EpisodeSort
import me.him188.ani.datasources.api.topic.UnifiedCollectionType
import me.him188.ani.utils.platform.annotations.TestOnly
import me.him188.ani.utils.platform.collections.ImmutableEnumMap

@Immutable
data class ScheduleItemPresentation(
    val subjectId: Int,
    val subjectTitle: String,
    val imageUrl: String,
    val episodeSort: EpisodeSort,
    val episodeEp: EpisodeSort?,
    val episodeName: String?,

    val subjectCollectionType: UnifiedCollectionType,

    /**
     * 未来的开始日期. 如果此条目已经开播了, 则为 `null`.
     */
    val futureStartDate: LocalDate?,
    val dayOfWeek: DayOfWeek,
    val time: LocalTime,
)

@TestOnly
val TestScheduleItemPresentations
    get() = buildList {
        var id = 0
        repeat(50) { i ->
            repeat(if (i % 8 == 0) 2 else 1) {
                add(
                    ScheduleItemPresentation(
                        subjectId = ++id,
                        subjectTitle = "Subject $id",
                        imageUrl = "https://example.com/image.jpg",
                        episodeSort = EpisodeSort(if (i % 3 == 0) 13 else 1),
                        episodeEp = EpisodeSort(1),
                        episodeName = "Episode 1",
                        subjectCollectionType = UnifiedCollectionType.entries[i % UnifiedCollectionType.entries.size],
                        futureStartDate = if (i % 4 == 0) {
                            LocalDate(2024, 5, 9)
                        } else {
                            null
                        },
                        dayOfWeek = DayOfWeek.entries[i % DayOfWeek.entries.size],
                        time = LocalTime(i % 24, 0),
                    ),
                )

            }
        }
    }

/**
 * @see TestSchedulePageData
 */
@TestOnly
val TestScheduleItemPresentationData: ImmutableEnumMap<DayOfWeek, List<ScheduleItemPresentation>>
    get() = ImmutableEnumMap<DayOfWeek, List<ScheduleItemPresentation>> { day ->
        TestScheduleItemPresentations.filter { it.dayOfWeek == day }
            .sortedWith(
                compareBy<ScheduleItemPresentation> { it.time }
                    .thenBy { it.subjectTitle },
            )
    }


@TestOnly
val TestSchedulePageData: ImmutableEnumMap<DayOfWeek, List<ScheduleDayColumnItem>>
    get() = ImmutableEnumMap<DayOfWeek, _> { day ->
        val currentTime = LocalTime(12, 0)
        val list = TestScheduleItemPresentations.filter { it.dayOfWeek == day }
            .sortedWith(
                compareBy<ScheduleItemPresentation> { it.time }
                    .thenBy { it.subjectTitle },
            )

        SchedulePageDataHelper.withCurrentTimeIndicator(list, currentTime)
    }

object SchedulePageDataHelper {

    /**
     * Returns the subjects which will have a new episode on air on [targetDate].
     */
    fun convert(
        subjects: List<SubjectCollectionInfo>,
        airInfos: List<OnAirAnimeInfo>,
        targetDate: LocalDate,
    ): List<ScheduleItemPresentation> {
        val subjectIdToAirInfo = MutableIntObjectMap<OnAirAnimeInfo>(subjects.size).apply {
            airInfos.forEach { put(it.bangumiId, it) }
        }
        return subjects.mapNotNull { info ->
            val airInfo = subjectIdToAirInfo[info.subjectId] ?: return@mapNotNull null
            val subjectBegin = airInfo.begin ?: return@mapNotNull null
            val recurrence = airInfo.recurrence ?: return@mapNotNull null

            val nextEpisode: EpisodeInfo = TODO()
            val nextEpisodeTime: LocalTime = TODO()

            ScheduleItemPresentation(
                subjectId = info.subjectId,
                subjectTitle = info.subjectInfo.name,
                imageUrl = info.subjectInfo.imageLarge,
                episodeSort = nextEpisode.sort,
                episodeEp = nextEpisode.ep,
                episodeName = nextEpisode.displayName,
                subjectCollectionType = info.collectionType,
                futureStartDate = null,
                dayOfWeek = DayOfWeek.MONDAY,
                time = nextEpisodeTime,
            )
        }
    }

    fun withCurrentTimeIndicator(
        list: List<ScheduleItemPresentation>,
        currentTime: LocalTime,
    ): List<ScheduleDayColumnItem> {
        val insertionIndex = list.indexOfLast { it.time <= currentTime }
        return buildList(capacity = list.size + 1) {
            var previousTime: LocalTime? = null
            val handleItem = { itemPresentation: ScheduleItemPresentation ->
                val showtime = previousTime != itemPresentation.time
                previousTime = itemPresentation.time
                add(
                    ScheduleDayColumnItem.Item(
                        item = itemPresentation,
                        showtime,
                    ),
                )
            }

            for (itemPresentation in list.subList(0, insertionIndex + 1)) {
                handleItem(itemPresentation)
            }
            add(
                ScheduleDayColumnItem.CurrentTimeIndicator(
                    currentTime = currentTime,
                ),
            )
            for (itemPresentation in list.subList(insertionIndex + 1, list.size)) {
                handleItem(itemPresentation)
            }
        }
    }
}