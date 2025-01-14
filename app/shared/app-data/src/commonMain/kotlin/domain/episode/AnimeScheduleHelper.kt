/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.episode

import androidx.collection.MutableIntObjectMap
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import me.him188.ani.app.data.models.episode.EpisodeInfo
import me.him188.ani.app.data.models.schedule.OnAirAnimeInfo
import me.him188.ani.app.data.models.subject.SubjectCollectionInfo
import me.him188.ani.datasources.api.toLocalDateOrNull

data class EpisodeWithAiringTime(
    val subject: SubjectCollectionInfo,
    val episode: EpisodeInfo,
    val airingTime: Instant,
)

object AnimeScheduleHelper {
    private val Utc9 = TimeZone.of("UTC+9")

    fun buildAiringScheduleForDate(
        subjects: List<SubjectCollectionInfo>,
        airInfos: List<OnAirAnimeInfo>,
        targetDate: LocalDate,
        localTimeZone: TimeZone,
    ): List<EpisodeWithAiringTime> {
        // Pre-map OnAirAnimeInfo by bangumiId (subjectId)
        val subjectIdToAirInfo = MutableIntObjectMap<OnAirAnimeInfo>(subjects.size).apply {
            airInfos.forEach { put(it.bangumiId, it) }
        }

        return subjects.mapNotNull { subject ->
            val airInfo = subjectIdToAirInfo[subject.subjectId] ?: return@mapNotNull null

            // If we have no actual begin time or no recurrence, we can’t do further computations
            val subjectBegin = airInfo.begin ?: return@mapNotNull null
            val recurrence = airInfo.recurrence ?: return@mapNotNull null

            // Sort episodes in ascending order of "sort"
            val episodes = subject.episodes
                .map { it.episodeInfo }
                .sortedBy { it.sort }

            // Keep track of the last known valid date/time (as Instant in UTC+9).
            // So if we have an episode with an invalid airDate, we can guess it
            // from the previous known date + recurrence.interval.
            //
            // We also keep track of how many episodes we’ve processed, so we can
            // approximate using “(n-1) * recurrence.interval” from subjectBegin
            // if needed.

            var lastKnownInstant: Instant? = null

            // The actual “match” we find for targetDate
            var matchedEpisode: EpisodeInfo? = null
            var matchedEpisodeInstant: Instant? = null

            episodes.forEachIndexed { index, ep ->
                // 1-based index for the “(episode n) => subjectBegin + (n - 1)*interval”
                val episodeNumber = index + 1

                // Try to see if the episode’s airDate is valid.
                val localDateFromAirDate = ep.airDate.toLocalDateOrNull()  // This is a LocalDate in UTC+9
                    ?.atStartOfDayIn(Utc9)
                    ?.toLocalDateTime(localTimeZone) // convert to our local timezone

                // Option A: If we have a valid date in EpisodeInfo itself, trust it.
                if (localDateFromAirDate != null) {
                    // Convert that local date into an Instant at (subject’s known hour) if we have it,
                    // or default to the subjectBegin’s local time-of-day. 
                    // Usually subjectBegin is the time for Ep1, so if we’re matching Episode #N,
                    // we might do subjectBegin + (N-1)*recurrence.

                    // We'll guess the day’s time from:
                    //   subjectBegin’s LocalTime in UTC+9
                    val baseDateTime = subjectBegin.toLocalDateTime(localTimeZone)
                    val localTime = baseDateTime.time

                    // Now combine localDateFromAirDate + localTime to get an Instant
                    val epLocalDateTime = LocalDateTime(
                        localDateFromAirDate.year,
                        localDateFromAirDate.monthNumber,
                        localDateFromAirDate.dayOfMonth,
                        localTime.hour,
                        localTime.minute,
                        localTime.second,
                        localTime.nanosecond,
                    )
                    val epInstant = epLocalDateTime.toInstant(localTimeZone)

                    // Because we have a real date from the episode metadata, let's reset lastKnownInstant
                    lastKnownInstant = epInstant

                    // Check if it matches the targetDate
                    if (localDateFromAirDate == targetDate) {
                        matchedEpisode = ep
                        matchedEpisodeInstant = epInstant
                        return@forEachIndexed  // Done searching this subject
                    }
                } else {
                    // Option B: Guess the date/time from recurrence if no date is specified
                    val guessedInstant = if (lastKnownInstant != null) {
                        // Add one recurrence interval from the last known
                        // but if the user wants “episodeNumber-based”, do that:
                        lastKnownInstant!!.plus(recurrence.interval)
                    } else {
                        // if no last known instant, guess from subjectBegin + (episodeNumber - 1) * recurrence
                        subjectBegin.plus(recurrence.interval * (episodeNumber - 1))
                    }

                    val localDateTime = guessedInstant.toLocalDateTime(localTimeZone)
                    val localDate = localDateTime.date

                    // Update lastKnownInstant
                    lastKnownInstant = guessedInstant

                    // Compare with targetDate
                    if (localDate == targetDate) {
                        matchedEpisode = ep
                        matchedEpisodeInstant = guessedInstant
                        return@forEachIndexed
                    }
                }
            }

            // If we found no matched episode for that subject, skip it
            val nextEpisode = matchedEpisode ?: return@mapNotNull null
            val nextEpisodeInstant = matchedEpisodeInstant ?: return@mapNotNull null

            EpisodeWithAiringTime(
                subject = subject,
                episode = nextEpisode,
                airingTime = nextEpisodeInstant,
            )
        }
    }
}
