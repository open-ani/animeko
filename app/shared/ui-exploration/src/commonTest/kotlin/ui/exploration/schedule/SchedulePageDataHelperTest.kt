/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.exploration.schedule

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import me.him188.ani.app.data.models.subject.LightEpisodeInfo
import me.him188.ani.app.data.models.subject.LightSubjectInfo
import me.him188.ani.app.domain.episode.EpisodeWithAiringTime
import me.him188.ani.datasources.api.EpisodeSort
import me.him188.ani.datasources.api.PackedDate
import me.him188.ani.datasources.api.UTC9
import me.him188.ani.datasources.api.topic.UnifiedCollectionType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

/**
 * 覆盖 [SchedulePageDataHelper.toColumnItems] 对时间未定项目的处理, [EpisodeWithAiringTime.toPresentation] 对 `timeKnown` 的映射,
 * 以及 [ScheduleItemDefaults.renderTime] 对 `null` 时间 (时间未定文本由调用方传入) 的渲染.
 */
class SchedulePageDataHelperTest {
    private fun item(
        id: Int,
        time: LocalTime?,
        title: String = "Subject $id",
    ) = AiringScheduleItemPresentation(
        subjectId = id,
        subjectTitle = title,
        imageUrl = "",
        episodeId = id,
        episodeSort = EpisodeSort(1),
        episodeEp = EpisodeSort(1),
        episodeName = null,
        subjectCollectionType = UnifiedCollectionType.NOT_COLLECTED,
        dayOfWeek = DayOfWeek.MONDAY,
        time = time,
    )

    private fun List<AiringScheduleColumnItem>.subjectIds(): List<Int?> = map {
        when (it) {
            is AiringScheduleColumnItem.Data -> it.item.subjectId
            is AiringScheduleColumnItem.CurrentTimeIndicator -> null
            is AiringScheduleColumnItem.PlaceholderData -> error("unexpected placeholder")
        }
    }

    private fun List<AiringScheduleColumnItem>.showTimes(): List<Boolean?> = map {
        (it as? AiringScheduleColumnItem.Data)?.showTime
    }

    // region toColumnItems

    @Test
    fun `timed items sorted by time and unknown-time items appended after them`() {
        val items = listOf(
            item(1, null),
            item(2, LocalTime(23, 0)),
            item(3, LocalTime(1, 0)),
            item(4, null),
            item(5, LocalTime(12, 0)),
        )
        val columns = SchedulePageDataHelper.toColumnItems(items, addIndicator = false, currentTime = LocalTime(12, 0))

        // 已知时间按时间升序; 时间未定的保持输入顺序追加在最后
        assertEquals(listOf(3, 5, 2, 1, 4), columns.subjectIds())
    }

    @Test
    fun `unknown-time items come after the current time indicator even when it is late in the day`() {
        val items = listOf(
            item(1, null),
            item(2, LocalTime(8, 0)),
            item(3, LocalTime(9, 0)),
        )
        val columns = SchedulePageDataHelper.toColumnItems(items, addIndicator = true, currentTime = LocalTime(23, 59))

        // 指示器在所有已知时间的项目之后, 但仍在时间未定的项目之前
        assertEquals(listOf(2, 3, null, 1), columns.subjectIds())
    }

    @Test
    fun `indicator inserted after the last item at or before current time`() {
        val items = listOf(
            item(1, null),
            item(2, LocalTime(8, 0)),
            item(3, LocalTime(12, 0)),
            item(4, LocalTime(18, 0)),
        )
        val columns = SchedulePageDataHelper.toColumnItems(items, addIndicator = true, currentTime = LocalTime(12, 0))

        assertEquals(listOf(2, 3, null, 4, 1), columns.subjectIds())
        val indicator = assertIs<AiringScheduleColumnItem.CurrentTimeIndicator>(columns[2])
        assertEquals(LocalTime(12, 0), indicator.currentTime)
    }

    @Test
    fun `only the first unknown-time item shows the time label`() {
        val items = listOf(
            item(1, null),
            item(2, LocalTime(8, 0)),
            item(3, LocalTime(8, 0)),
            item(4, null),
            item(5, null),
        )
        val columns = SchedulePageDataHelper.toColumnItems(items, addIndicator = false, currentTime = LocalTime(0, 0))

        assertEquals(listOf(2, 3, 1, 4, 5), columns.subjectIds())
        // 同一时刻只有第一个显示时间; 时间未定组只有第一个显示 "时间未定"
        assertEquals(listOf(true, false, true, false, false), columns.showTimes())
    }

    @Test
    fun `first unknown-time item shows the time label even when there are no timed items`() {
        val items = listOf(item(1, null), item(2, null))
        val columns = SchedulePageDataHelper.toColumnItems(items, addIndicator = true, currentTime = LocalTime(10, 0))

        assertEquals(listOf(null, 1, 2), columns.subjectIds())
        assertEquals(listOf(null, true, false), columns.showTimes())
    }

    @Test
    fun `timed items keep input order when times are equal`() {
        val items = listOf(
            item(1, LocalTime(8, 0), title = "B"),
            item(2, LocalTime(8, 0), title = "A"),
            item(3, LocalTime(7, 0)),
        )
        val columns = SchedulePageDataHelper.toColumnItems(items, addIndicator = false, currentTime = LocalTime(0, 0))

        // 稳定排序: 服务端给出的顺序 (按 subjectId, sort) 在同一时刻内保留
        assertEquals(listOf(3, 1, 2), columns.subjectIds())
        assertEquals(listOf(true, true, false), columns.showTimes())
    }

    @Test
    fun `empty list yields only the indicator`() {
        val columns = SchedulePageDataHelper.toColumnItems(emptyList(), addIndicator = true, currentTime = LocalTime(10, 0))
        assertEquals(listOf(null), columns.subjectIds())

        assertEquals(emptyList(), SchedulePageDataHelper.toColumnItems(emptyList(), addIndicator = false, LocalTime(10, 0)))
    }

    // endregion

    // region toPresentation

    private fun episodeWithAiringTime(airingTime: LocalDateTime, timeZone: TimeZone, timeKnown: Boolean) =
        EpisodeWithAiringTime(
            subject = LightSubjectInfo(subjectId = 1, name = "Name", nameCn = "中文名", imageLarge = "img"),
            episode = LightEpisodeInfo(
                episodeId = 2,
                name = "Ep",
                nameCn = "",
                airDate = PackedDate(2026, 9, 4),
                timezone = UTC9,
                sort = EpisodeSort(3),
                ep = EpisodeSort(3),
            ),
            airingTime = airingTime.toInstant(timeZone),
            timeKnown = timeKnown,
        )

    @Test
    fun `toPresentation keeps the time when timeKnown`() {
        val timeZone = TimeZone.of("Asia/Shanghai")
        val presentation = episodeWithAiringTime(LocalDateTime(2026, 9, 4, 23, 30), timeZone, timeKnown = true)
            .toPresentation(timeZone)

        assertEquals(LocalTime(23, 30), presentation.time)
        assertEquals(DayOfWeek.FRIDAY, presentation.dayOfWeek)
        assertEquals("中文名", presentation.subjectTitle)
        assertEquals("Ep", presentation.episodeName)
        assertEquals(EpisodeSort(3), presentation.episodeSort)
    }

    @Test
    fun `toPresentation drops the time but keeps the day when timeKnown is false`() {
        val timeZone = TimeZone.of("Asia/Shanghai")
        // 服务端对时间未定的剧集给出该日期在客户端时区的 00:00
        val presentation = episodeWithAiringTime(LocalDateTime(2026, 9, 4, 0, 0), timeZone, timeKnown = false)
            .toPresentation(timeZone)

        assertNull(presentation.time)
        assertEquals(DayOfWeek.FRIDAY, presentation.dayOfWeek)
    }

    @Test
    fun `toPresentation converts to the given time zone`() {
        val presentation = episodeWithAiringTime(LocalDateTime(2026, 9, 4, 23, 30), UTC9, timeKnown = true)
            .toPresentation(TimeZone.UTC)

        assertEquals(LocalTime(14, 30), presentation.time)
        assertEquals(DayOfWeek.FRIDAY, presentation.dayOfWeek)
    }

    // endregion

    // region renderTime

    @Test
    fun `renderTime renders a known time`() {
        assertEquals("09:05", ScheduleItemDefaults.renderTime(null, LocalTime(9, 5)))
        assertEquals("1/2\n23:00", ScheduleItemDefaults.renderTime(LocalDate(2026, 1, 2), LocalTime(23, 0)))
    }

    @Test
    fun `renderTime renders unknown time with the given text`() {
        // 文本来自字符串资源 (Lang.exploration_schedule_time_unknown), 由 composable 传入
        assertEquals("时间未定", ScheduleItemDefaults.renderTime(null, null, timeUnknownText = "时间未定"))
        assertEquals("Time TBA", ScheduleItemDefaults.renderTime(null, null, timeUnknownText = "Time TBA"))
        assertEquals("1/2\n时间未定", ScheduleItemDefaults.renderTime(LocalDate(2026, 1, 2), null, timeUnknownText = "时间未定"))
    }

    @Test
    fun `renderTime ignores the unknown text when the time is known`() {
        assertEquals("09:05", ScheduleItemDefaults.renderTime(null, LocalTime(9, 5), timeUnknownText = "时间未定"))
        assertEquals(
            "1/2\n23:00",
            ScheduleItemDefaults.renderTime(LocalDate(2026, 1, 2), LocalTime(23, 0), timeUnknownText = "时间未定"),
        )
    }

    // endregion
}
