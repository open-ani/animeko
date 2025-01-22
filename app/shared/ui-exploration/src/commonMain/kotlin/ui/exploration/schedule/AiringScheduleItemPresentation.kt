/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.exploration.schedule

import androidx.compose.runtime.Immutable
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import me.him188.ani.app.data.models.subject.displayName
import me.him188.ani.app.domain.episode.EpisodeWithAiringTime
import me.him188.ani.app.domain.episode.GetAnimeScheduleFlowUseCase
import me.him188.ani.datasources.api.EpisodeSort
import me.him188.ani.datasources.api.topic.UnifiedCollectionType
import me.him188.ani.utils.platform.annotations.TestOnly
import me.him188.ani.utils.platform.collections.ImmutableEnumMap

@Immutable
data class AiringScheduleItemPresentation(
    val subjectId: Int,
    val subjectTitle: String,
    val imageUrl: String,
    val episodeId: Int,
    val episodeSort: EpisodeSort,
    val episodeEp: EpisodeSort?,
    val episodeName: String?,

    val subjectCollectionType: UnifiedCollectionType,
    val dayOfWeek: DayOfWeek,
    val time: LocalTime,
)

@Immutable
data class AiringSchedule(
    val date: LocalDate,
    val episodes: List<AiringScheduleColumnItem>,
)

@Immutable
data class ScheduleDay(
    val date: LocalDate,
    val kind: Kind,
) {
    val dayOfWeek: DayOfWeek get() = date.dayOfWeek

    enum class Kind {
        LAST_WEEK,
        TODAY,
        THIS_WEEK,
        NEXT_WEEK,
    }

    companion object {
        fun generateForRecentTwoWeeks(
            today: LocalDate,
        ): List<ScheduleDay> {
            // 假设今天是本周三, 返回的是上周三到下周三
            return SchedulePageDataHelper.OFFSET_DAYS_RANGE.map { offsetDays ->
                val date = today.plus(DatePeriod(days = offsetDays))
                val thisWeekRange: ClosedRange<LocalDate> = getWeekRange(today)
                ScheduleDay(
                    date = date,
                    kind = when {
                        date == today -> Kind.TODAY
                        date in thisWeekRange -> Kind.THIS_WEEK
                        date > thisWeekRange.endInclusive -> Kind.NEXT_WEEK
                        date < thisWeekRange.start -> Kind.LAST_WEEK
                        else -> error("unreachable")
                    },
                )
            }
        }

        private fun getWeekRange(date: LocalDate): ClosedRange<LocalDate> {
            val dayOfWeek = date.dayOfWeek
            return date.minus(DatePeriod(days = dayOfWeek.ordinal))..date.plus(DatePeriod(days = 6 - dayOfWeek.ordinal))
        }
    }
}

@TestOnly
val TestAiringScheduleItemPresentations
    get() = buildList {
        var id = 0
        repeat(50) { i ->
            repeat(if (i % 8 == 0) 2 else 1) {
                add(
                    AiringScheduleItemPresentation(
                        subjectId = ++id,
                        subjectTitle = "Subject $id",
                        imageUrl = "https://example.com/image.jpg",
                        episodeId = id,
                        episodeSort = EpisodeSort(if (i % 3 == 0) 13 else 1),
                        episodeEp = EpisodeSort(1),
                        episodeName = "Episode 1",
                        subjectCollectionType = UnifiedCollectionType.entries[i % UnifiedCollectionType.entries.size],
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
val TestAiringScheduleItemPresentationData: ImmutableEnumMap<DayOfWeek, List<AiringScheduleItemPresentation>>
    get() = ImmutableEnumMap<DayOfWeek, List<AiringScheduleItemPresentation>> { day ->
        TestAiringScheduleItemPresentations.filter { it.dayOfWeek == day }
            .sortedWith(
                compareBy<AiringScheduleItemPresentation> { it.time }
                    .thenBy { it.subjectTitle },
            )
    }


@TestOnly
val TestSchedulePageData: List<AiringSchedule>
    get() {
        val currentTime = LocalTime(12, 0)
        val list = TestAiringScheduleItemPresentations.filter { it.dayOfWeek == DayOfWeek.MONDAY }
            .sortedWith(
                compareBy<AiringScheduleItemPresentation> { it.time }
                    .thenBy { it.subjectTitle },
            )


        return ScheduleDay.generateForRecentTwoWeeks(LocalDate(2025, 12, 10)).map {
            AiringSchedule(
                date = it.date,
                SchedulePageDataHelper.toColumnItems(list, addIndicator = true, currentTime),
            )
        }
    }

fun EpisodeWithAiringTime.toPresentation(timeZone: TimeZone): AiringScheduleItemPresentation {
    val dateTime = airingTime.toLocalDateTime(timeZone)
    // Return the item
    return AiringScheduleItemPresentation(
        subjectId = subject.subjectId,
        subjectTitle = subject.displayName,
        imageUrl = subject.imageLarge,
        episodeId = episode.episodeId,
        episodeSort = episode.sort,
        episodeEp = episode.ep,
        episodeName = episode.displayName,
        subjectCollectionType = UnifiedCollectionType.NOT_COLLECTED,
        dayOfWeek = dateTime.dayOfWeek,
        time = dateTime.time,
    )
}

object SchedulePageDataHelper {
    val OFFSET_DAYS_RANGE = GetAnimeScheduleFlowUseCase.OFFSET_DAYS_RANGE

    fun toColumnItems(
        list: List<AiringScheduleItemPresentation>,
        addIndicator: Boolean,
        currentTime: LocalTime,
    ): List<AiringScheduleColumnItem> {
        @Suppress("NAME_SHADOWING")
        val list = list.sortedBy { it.time }
        val insertionIndex = list.indexOfLast { it.time <= currentTime }
        return buildList(capacity = list.size + 1) {
            var previousTime: LocalTime? = null
            val handleItem = { itemPresentation: AiringScheduleItemPresentation ->
                val showtime = previousTime != itemPresentation.time
                previousTime = itemPresentation.time
                add(
                    AiringScheduleColumnItem.Data(
                        item = itemPresentation,
                        showtime,
                    ),
                )
            }

            for (itemPresentation in list.subList(0, insertionIndex + 1)) {
                handleItem(itemPresentation)
            }
            if (addIndicator) {
                add(
                    AiringScheduleColumnItem.CurrentTimeIndicator(
                        currentTime = currentTime,
                        isPlaceholder = false,
                    ),
                )
            }
            for (itemPresentation in list.subList(insertionIndex + 1, list.size)) {
                handleItem(itemPresentation)
            }
        }
    }
}