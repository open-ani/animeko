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
    /**
     * 放送时刻. `null` 表示时间未定 (只知道放送日期), 界面显示 "时间未定", 并排在当天所有已知时间的项目之后.
     */
    val time: LocalTime?,
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
                        // 每隔几个放一个时间未定的项目, 预览里能看到 "时间未定" 的样式
                        time = if (i % 11 == 10) null else LocalTime(i % 24, 0),
                    ),
                )

            }
        }
    }

/**
 * 时间未定 (`time == null`) 的项目排在已知时间的项目之后.
 */
private val testPresentationComparator =
    compareBy<AiringScheduleItemPresentation, LocalTime?>(nullsLast()) { it.time }
        .thenBy { it.subjectTitle }

/**
 * @see TestSchedulePageData
 */
@TestOnly
val TestAiringScheduleItemPresentationData: ImmutableEnumMap<DayOfWeek, List<AiringScheduleItemPresentation>>
    get() = ImmutableEnumMap<DayOfWeek, List<AiringScheduleItemPresentation>> { day ->
        TestAiringScheduleItemPresentations.filter { it.dayOfWeek == day }
            .sortedWith(testPresentationComparator)
    }


@TestOnly
val TestSchedulePageData: List<AiringSchedule>
    get() {
        val currentTime = LocalTime(12, 0)
        val list = TestAiringScheduleItemPresentations.filter { it.dayOfWeek == DayOfWeek.MONDAY }
            .sortedWith(testPresentationComparator)


        return ScheduleDay.generateForRecentTwoWeeks(LocalDate(2025, 12, 10)).map {
            AiringSchedule(
                date = it.date,
                SchedulePageDataHelper.toColumnItems(list, addIndicator = true, currentTime),
            )
        }
    }

fun EpisodeWithAiringTime.toPresentation(timeZone: TimeZone): AiringScheduleItemPresentation {
    // 时间未定时 airingTime 是放送日期在 timeZone 的 00:00, 日期仍然可用, 只是不显示时刻.
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
        time = if (timeKnown) dateTime.time else null,
    )
}

object SchedulePageDataHelper {
    val OFFSET_DAYS_RANGE = GetAnimeScheduleFlowUseCase.OFFSET_DAYS_RANGE

    /**
     * 把一天的项目转换为列表项:
     * - 已知时间的项目按时间升序 (稳定排序, 同一时间保持输入顺序), 只有与上一项时间不同的项目显示时间;
     * - [addIndicator] 时, 在最后一个 `time <= currentTime` 的项目之后插入当前时间指示器;
     * - 时间未定 (`time == null`) 的项目保持输入顺序, 追加在所有已知时间的项目和指示器之后, 只有第一个显示 "时间未定".
     */
    fun toColumnItems(
        list: List<AiringScheduleItemPresentation>,
        addIndicator: Boolean,
        currentTime: LocalTime,
    ): List<AiringScheduleColumnItem> {
        val (timed, timeUnknown) = list.partition { it.time != null }
        val sortedTimed = timed.sortedBy { it.time }
        val insertionIndex = sortedTimed.indexOfLast { checkNotNull(it.time) <= currentTime }
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

            for (itemPresentation in sortedTimed.subList(0, insertionIndex + 1)) {
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
            for (itemPresentation in sortedTimed.subList(insertionIndex + 1, sortedTimed.size)) {
                handleItem(itemPresentation)
            }
            timeUnknown.forEachIndexed { index, itemPresentation ->
                add(
                    AiringScheduleColumnItem.Data(
                        item = itemPresentation,
                        showTime = index == 0,
                    ),
                )
            }
        }
    }
}