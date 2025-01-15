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
import me.him188.ani.app.data.models.schedule.OnAirAnimeInfo
import me.him188.ani.app.data.models.subject.LightEpisodeInfo
import me.him188.ani.app.data.models.subject.LightSubjectAndEpisodes
import me.him188.ani.datasources.api.toLocalDateOrNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours

object AnimeScheduleHelper {
    data class EpisodeNextAiringTime(
        val subjectId: Int,
        val episode: LightEpisodeInfo,
        val airingTime: Instant,
    )


    /**
     * Builds an airing schedule for [targetDate], where [targetDate] is assumed
     * to be in the [localTimeZone]. Allows for an [allowedDeviation] to treat an
     * episode’s airing time as “on the same date” if it’s within that margin of
     * midnight (or any reference point you wish).
     *
     * By default, [allowedDeviation] is 24 hours.
     */
    fun buildAiringScheduleForDate(
        subjects: List<LightSubjectAndEpisodes>,
        airInfos: List<OnAirAnimeInfo>,
        targetDate: LocalDate,
        localTimeZone: TimeZone,
        allowedDeviation: Duration = 24.hours,
    ): List<EpisodeNextAiringTime> {
        // Pre-map OnAirAnimeInfo by bangumiId (subjectId)
        val subjectIdToAirInfo = MutableIntObjectMap<OnAirAnimeInfo>(subjects.size).apply {
            airInfos.forEach { put(it.bangumiId, it) }
        }

        // We'll pick midnight of targetDate in localTimeZone as our reference.
        // E.g., 2025-02-08T00:00:00 local => some Instant
        val targetMidnight: Instant = targetDate.atStartOfDayIn(localTimeZone)

        return subjects.mapNotNull { subject ->
            val airInfo = subjectIdToAirInfo[subject.subjectId] ?: return@mapNotNull null

            // If we have no actual begin time or no recurrence, skip
            val subjectBegin = airInfo.begin ?: return@mapNotNull null
            val recurrence = airInfo.recurrence ?: return@mapNotNull null

            // Sort episodes in ascending order of "sort"
            val episodes = subject.episodes.sortedBy { it.sort }

            var lastKnownInstant: Instant? = null

            // We'll store the first matched episode we find for this subject
            var matchedEpisode: LightEpisodeInfo? = null
            var matchedEpisodeInstant: Instant? = null

            episodes.forEachIndexed { index, ep ->
                // 1-based index => (episode n) => subjectBegin + (n-1)*interval
                val episodeNumber = index + 1

                // 1) If the episode has a valid airDate, parse it in ep.timezone.
                val localDateFromAirDate = ep.airDate.toLocalDateOrNull()
                    ?.atStartOfDayIn(ep.timezone)  // interpret “date” in ep’s own timezone
                val epInstant = if (localDateFromAirDate != null) {
                    // We have a valid date from metadata. We'll combine it with
                    // subjectBegin's local "time" portion or just keep it at midnight.
                    // Example approach: keep the day from the metadata but the "time"
                    // from subjectBegin in localTimeZone.
                    val baseTime = subjectBegin.toLocalDateTime(localTimeZone).time
                    val localDateTime = localDateFromAirDate.toLocalDateTime(localTimeZone)
                    val finalDateTime = LocalDateTime(
                        localDateTime.year,
                        localDateTime.monthNumber,
                        localDateTime.dayOfMonth,
                        baseTime.hour,
                        baseTime.minute,
                        baseTime.second,
                        baseTime.nanosecond,
                    )
                    finalDateTime.toInstant(localTimeZone).also {
                        lastKnownInstant = it
                    }
                } else {
                    // 2) If invalid date, guess from last known + recurrence
                    if (lastKnownInstant != null) {
                        lastKnownInstant!!.plus(recurrence.interval).also {
                            lastKnownInstant = it
                        }
                    } else {
                        // If none known, anchor to subjectBegin + (episodeNumber-1)*interval
                        subjectBegin.plus(recurrence.interval * (episodeNumber - 1)).also {
                            lastKnownInstant = it
                        }
                    }
                }

                // Now epInstant is the computed or actual airing Instant in localTimeZone reference
                // Compare epInstant to targetMidnight by absolute difference
                val diff = (epInstant - targetMidnight).absoluteValue
                if (diff <= allowedDeviation) {
                    // This means epInstant is “close enough” to that date (± 24h by default)
                    matchedEpisode = ep
                    matchedEpisodeInstant = epInstant
                    return@forEachIndexed
                }
            }

            // If not found, skip
            val nextEpisode = matchedEpisode ?: return@mapNotNull null
            val nextEpisodeInstant = matchedEpisodeInstant ?: return@mapNotNull null

            EpisodeNextAiringTime(
                subjectId = subject.subjectId,
                episode = nextEpisode,
                airingTime = nextEpisodeInstant,
            )
        }
    }
}
