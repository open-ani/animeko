/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.episode

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import me.him188.ani.app.data.models.episode.EpisodeInfo
import me.him188.ani.app.data.models.schedule.AnimeRecurrence
import me.him188.ani.app.data.models.schedule.OnAirAnimeInfo
import me.him188.ani.app.data.models.subject.LightEpisodeInfo
import me.him188.ani.app.data.models.subject.LightSubjectAndEpisodes
import me.him188.ani.app.data.models.subject.LightSubjectInfo
import me.him188.ani.app.data.models.subject.SubjectCollectionInfo
import me.him188.ani.datasources.api.EpisodeSort
import me.him188.ani.datasources.api.PackedDate
import me.him188.ani.datasources.api.UTC9
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

class AnimeScheduleHelperTest {
    // --------------------
// SAMPLE TEST CLASS
// --------------------

    /**
     * We'll assume we have the `convert` method somewhere in your code base:
     *
     *  fun convert(
     *    subjects: List<SubjectCollectionInfo>,
     *    airInfos: List<OnAirAnimeInfo>,
     *    targetDate: LocalDate,
     *  ): List<ScheduleItemPresentation> { ... }
     *
     * Make sure it's imported or in the same file so these tests can call it.
     */

    // --------------
    // Helper Methods
    // --------------

    /**
     * Creates a minimal [SubjectCollectionInfo] with some default or empty property values
     * so that test authors only need to specify what's relevant. Example usage:
     *
     *   createSubjectCollectionInfo(
     *       subjectId = 123,
     *       episodes = listOf(episode1, episode2)
     *   )
     *
     * Then add or override if needed, e.g. `subjectInfo = subjectInfo.copy(name = "NewName")`.
     */
    private fun createSubjectCollectionInfo(
        subjectId: Int,
        episodes: List<LightEpisodeInfo>,
        subjectName: String = "Default Subject"
    ): LightSubjectAndEpisodes {
        val subjectInfo = LightSubjectInfo(
            subjectId = subjectId,
            name = subjectName,
            nameCn = subjectName,
            imageLarge = "https://example.com/default.jpg",
        )
        return LightSubjectAndEpisodes(
            subjectInfo,
            episodes = episodes,
        )
    }

    /**
     * Creates a minimal [OnAirAnimeInfo]. If `begin` is not specified, the algorithm cannot proceed,
     * so provide one if needed. If `recurrence` is null, the algorithm also cannot guess.
     */
    private fun createOnAirAnimeInfo(
        bangumiId: Int,
        begin: String?,            // ISO-8601 string, e.g. "2024-07-06T13:00:00Z"
        intervalDays: Int?,        // e.g. 7
        end: String? = null
    ): OnAirAnimeInfo {
        val beginInstant = begin?.let { Instant.parse(it) } // or null
        val endInstant = end?.let { Instant.parse(it) }

        val recurrence = intervalDays?.let {
            AnimeRecurrence(
                startTime = beginInstant ?: Instant.DISTANT_PAST,
                interval = it.days,
            )
        }

        return OnAirAnimeInfo(
            bangumiId = bangumiId,
            name = "Anime-$bangumiId",
            aliases = listOf(),
            begin = beginInstant,
            recurrence = recurrence,
            end = endInstant,
            mikanId = null,
        )
    }

    /**
     * Utility to create an [EpisodeInfo] with an optional air date in 'yyyy-mm-dd' format (UTC+9).
     * Pass null or "Invalid" to test invalid/unknown air dates.
     */
    private fun createEpisodeInfo(
        episodeId: Int,
        airDateString: String? = null,
        sort: String = "$episodeId" // default sort is just the episode number
    ): LightEpisodeInfo {
        val packedDate = if (airDateString.isNullOrBlank() || airDateString == "Invalid") {
            PackedDate.Invalid
        } else {
            // e.g. "2024-01-05" => year=2024, month=1, day=5
            val parts = airDateString.split("-")
            if (parts.size == 3) {
                PackedDate(parts[0].toInt(), parts[1].toInt(), parts[2].toInt())
            } else {
                PackedDate.Invalid
            }
        }
        return LightEpisodeInfo(
            episodeId = episodeId,
            name = "Ep$episodeId",
            nameCn = "第${episodeId}集",
            airDate = packedDate,
            timezone = UTC9,
            sort = EpisodeSort(sort),
            ep = null,
        )
    }

    /**
     * Converts a LocalDate (KotlinX datetime) into a string in ISO8601 with
     * an arbitrary time (e.g. 09:00:00Z).
     */
    private fun localDateToInstantString(date: LocalDate, hour: Int = 9): String {
        val dt = date.toLocalDateTime(hour = hour, minute = 0, second = 0)
        val instant = dt.toInstant(UTC9)
        // Make sure we produce an ISO-8601 with UTC offset, e.g. "2025-01-14T09:00:00Z"
        return instant.toString()
    }

    private fun convert(
        subjects: List<LightSubjectAndEpisodes>,
        airInfos: List<OnAirAnimeInfo>,
        targetDate: LocalDate,
        localTimeZone: TimeZone = TimeZone.UTC,
    ): List<AnimeScheduleHelper.EpisodeNextAiringTime> = AnimeScheduleHelper.buildAiringScheduleForDate(
        subjects,
        airInfos,
        targetDate,
        localTimeZone,
    )

    // --------------
    // Actual Tests
    // --------------

    @Test
    fun `subject without OnAirAnimeInfo is skipped`() {
        // We'll create a single subject but with no matching OnAirAnimeInfo in the list
        val subjects = listOf(
            createSubjectCollectionInfo(
                subjectId = 123,
                episodes = listOf(createEpisodeInfo(1, "2025-01-14")),
            ),
        )
        val airInfos = listOf<OnAirAnimeInfo>() // empty

        val result = convert(subjects, airInfos, LocalDate(2025, 1, 14))
        assertTrue(result.isEmpty(), "No schedule items should be returned if OnAirAnimeInfo is missing.")
    }

    @Test
    fun `subject with null begin or null recurrence is skipped`() {
        // Create one subject with OnAirAnimeInfo, but missing essential fields
        val subjects = listOf(
            createSubjectCollectionInfo(
                subjectId = 101,
                episodes = listOf(createEpisodeInfo(1, "2025-01-14")),
            ),
        )
        // begin = null => can't compute
        val airInfos = listOf(
            createOnAirAnimeInfo(
                bangumiId = 101,
                begin = null,
                intervalDays = 7,
            ),
        )

        val targetDate = LocalDate(2025, 1, 14)
        val result = convert(subjects, airInfos, targetDate)
        assertTrue(result.isEmpty(), "No match since begin is null => skip")

        // likewise, if begin != null but intervalDays = null => skip
        val airInfos2 = listOf(
            createOnAirAnimeInfo(
                bangumiId = 101,
                begin = localDateToInstantString(LocalDate(2025, 1, 1)),
                intervalDays = null,
            ),
        )
        val result2 = convert(subjects, airInfos2, targetDate)
        assertTrue(result2.isEmpty(), "No match since recurrence is null => skip")
    }

    @Test
    fun `when episode airDate is valid and matches target date - it is returned`() {
        // Suppose we have 2 episodes, ep1 = 2025-01-14, ep2 = 2025-01-21
        val ep1 = createEpisodeInfo(1, "2025-01-14")
        val ep2 = createEpisodeInfo(2, "2025-01-21")

        val subjects = listOf(
            createSubjectCollectionInfo(
                subjectId = 201,
                episodes = listOf(ep1, ep2),
            ),
        )
        val airInfos = listOf(
            createOnAirAnimeInfo(
                bangumiId = 201,
                begin = localDateToInstantString(LocalDate(2025, 1, 7)), // or any valid begin
                intervalDays = 7,
            ),
        )
        val targetDate = LocalDate(2025, 1, 14)

        val result = convert(subjects, airInfos, targetDate)
        assertEquals(1, result.size, "Expected exactly one matching episode")

        val scheduleItem = result.first()
        assertEquals(201, scheduleItem.subjectId)
        assertEquals(1f, scheduleItem.episode.sort.number)
        // And so forth ...
    }

    @Test
    fun `when episode airDate is invalid - guess it by recurrence from previous valid episode`() {
        // ep1 is valid => 2025-02-01
        // ep2 is invalid => we guess ep2 = ep1 + 7 days => 2025-02-08
        val ep1 = createEpisodeInfo(1, "2025-02-01")
        val ep2 = createEpisodeInfo(2, "Invalid")

        val subjects = listOf(
            createSubjectCollectionInfo(
                subjectId = 202,
                episodes = listOf(ep1, ep2),
            ),
        )
        // Start at 2025-02-01T09:00:00Z. Weekly recurrence of 7 days
        val airInfos = listOf(
            createOnAirAnimeInfo(
                bangumiId = 202,
                begin = "2025-02-01T09:00:00Z",
                intervalDays = 7,
            ),
        )
        // Our target date is 2025-02-08 => that means ep2
        val targetDate = LocalDate(2025, 2, 8)

        val result = convert(subjects, airInfos, targetDate)
        assertEquals(1, result.size)
        val scheduleItem = result.first()
        assertEquals(202, scheduleItem.subjectId)
        assertEquals(2f, scheduleItem.episode.sort.number, "We matched episode 2 by guess")
    }

    @Test
    fun `when first episode is invalid - guess from subjectBegin`() {
        // ep1 is invalid => guess from subjectBegin => 2025-03-01
        // ep2 is invalid => guess from ep1 + 7 => 2025-03-08
        // We'll target 2025-03-01 to see if ep1 matches
        val ep1 = createEpisodeInfo(1, "Invalid")
        val ep2 = createEpisodeInfo(2, "Invalid")

        val subjects = listOf(
            createSubjectCollectionInfo(
                subjectId = 303,
                episodes = listOf(ep1, ep2),
            ),
        )
        val airInfos = listOf(
            createOnAirAnimeInfo(
                bangumiId = 303,
                begin = "2025-03-01T13:00:00Z", // This is the time we anchor ep1 to
                intervalDays = 7,
            ),
        )
        val targetDate = LocalDate(2025, 3, 1)

        val result = convert(subjects, airInfos, targetDate)
        assertEquals(1, result.size)
        val scheduleItem = result.first()
        assertEquals(303, scheduleItem.subjectId)
        assertEquals(1f, scheduleItem.episode.sort.number)
    }

    @Test
    fun `if no episodes match target date - skip`() {
        // ep1 => 2025-04-01, ep2 => 2025-04-08 => no one matches 2025-04-14
        val ep1 = createEpisodeInfo(1, "2025-04-01")
        val ep2 = createEpisodeInfo(2, "2025-04-08")
        val subjects = listOf(
            createSubjectCollectionInfo(
                subjectId = 404,
                episodes = listOf(ep1, ep2),
            ),
        )
        val airInfos = listOf(
            createOnAirAnimeInfo(
                bangumiId = 404,
                begin = "2025-04-01T00:00:00Z",
                intervalDays = 7,
            ),
        )
        val targetDate = LocalDate(2025, 4, 14)

        val result = convert(subjects, airInfos, targetDate)
        assertTrue(result.isEmpty(), "No episodes match => skip")
    }

    // ------------------------------------
    // You can add more edge-case tests here
    // ------------------------------------
}

private fun LocalDate.toLocalDateTime(hour: Int, minute: Int, second: Int): LocalDateTime {
    return LocalDateTime(this, LocalTime(hour, minute, second))
}
