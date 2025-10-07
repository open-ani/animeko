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
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import me.him188.ani.app.data.models.schedule.OnAirAnimeInfo
import me.him188.ani.app.data.models.subject.LightEpisodeInfo
import me.him188.ani.app.data.models.subject.LightSubjectAndEpisodes
import me.him188.ani.datasources.api.toLocalDateOrNull
import kotlin.math.roundToInt
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

object AnimeScheduleHelper {

    data class EpisodeNextAiringTime(
        val subjectId: Int,
        val episode: LightEpisodeInfo,
        val airingTime: Instant,
    )

    /**
     * Builds an airing schedule for [targetDate], in the given [localTimeZone], with an
     * [allowedDeviation] (default = 24h). The algorithm:
     *
     * - For each subject & episode (in ascending sort order):
     *   - Determine an "intended" LocalDate for that episode (either from [episode.airDate]
     *     if valid, or guess from the previous episode).
     *   - Convert that LocalDate into an approximate "actual" Instant by snapping to the nearest
     *     multiple of [recurrence.interval] from [recurrence.startTime]—but only if the difference
     *     is within [allowedDeviation].
     *   - If it’s out of [allowedDeviation], fall back to “previous episode’s actual airtime + interval”
     *     or “startTime + (episodeIndex * interval)”.
     *   - Finally, if that actual airtime’s LocalDate == [targetDate], we include it in the result.
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

        return subjects.mapNotNull { subject ->
            val airInfo = subjectIdToAirInfo[subject.subjectId] ?: return@mapNotNull null

            // If we have no actual begin time or no recurrence, skip
            val startTime = airInfo.begin ?: return@mapNotNull null
            val recurrence = airInfo.recurrence ?: return@mapNotNull null

            // Sort episodes in ascending order of "sort"
            val episodes = subject.episodes.sortedBy { it.sort }

            var lastEpisodeInstant: Instant? = null
            var matchedEpisode: LightEpisodeInfo? = null
            var matchedEpisodeInstant: Instant? = null

            episodes.forEachIndexed { index, ep ->
                val episodeNumber = index + 1

                // 1) Figure out an intended local date/time from ep.airDate (if valid)
                val epLocalDate: LocalDate? = ep.airDate.toLocalDateOrNull()
                // We'll interpret it in ep.timezone at 00:00
                val epLocalMidnight: Instant? = epLocalDate
                    ?.atStartOfDayIn(ep.timezone)

                // 2) Try to snap that epLocalMidnight to the nearest multiple of recurrence.interval
                //    from the recurrence.startTime, as long as the difference is <= allowedDeviation.
                //    This is “the ideal actual airing time.” 
                val snappedAirtime: Instant? = epLocalMidnight?.let { localMidnight ->
                    val diff = localMidnight - recurrence.startTime
                    val n = (diff.inWholeMilliseconds.toDouble() / recurrence.interval.inWholeMilliseconds).roundToInt()
                    val candidateAirtime = recurrence.startTime + (recurrence.interval * n)
                    val offBy = (candidateAirtime - localMidnight).absoluteValue

                    if (offBy <= allowedDeviation) {
                        // The candidate is "close enough" to the epLocalMidnight
                        candidateAirtime
                    } else {
                        // If it's too far from the 'intended' date, we disregard snapping
                        null
                    }
                }

                // 3) If invalid date or snapping is too far, use a guess:
                //    if we have a lastEpisodeInstant, use lastEpisodeInstant + interval
                //    else use startTime + (episodeIndex)*interval
                val actualEpTime: Instant = when {
                    snappedAirtime != null -> {
                        snappedAirtime
                    }

                    lastEpisodeInstant != null -> {
                        lastEpisodeInstant!!.plus(recurrence.interval)
                    }

                    else -> {
                        // For the 1st episode if everything else is invalid
                        startTime + recurrence.interval * (episodeNumber - 1)
                    }
                }

                // Update lastEpisodeInstant
                lastEpisodeInstant = actualEpTime

                // 4) Now see if that actualEpTime falls on the targetDate (in localTimeZone).
                //    For example, if actualEpTime is Jan 1 at any time, and targetDate is Jan 1 => match.
                val actualEpLocalDate = actualEpTime.toLocalDateTime(localTimeZone).date
                if (actualEpLocalDate == targetDate) {
                    matchedEpisode = ep
                    matchedEpisodeInstant = actualEpTime
                    return@forEachIndexed
                }
            }

            // If no matched episode, skip
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
