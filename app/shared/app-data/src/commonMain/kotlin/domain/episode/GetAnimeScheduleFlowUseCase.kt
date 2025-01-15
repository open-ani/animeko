/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.episode

import androidx.compose.ui.util.packInts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.datetime.Clock
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import me.him188.ani.app.data.models.subject.LightEpisodeInfo
import me.him188.ani.app.data.models.subject.LightSubjectInfo
import me.him188.ani.app.data.repository.episode.AnimeScheduleRepository
import me.him188.ani.app.data.repository.subject.SubjectCollectionRepository
import me.him188.ani.app.domain.usecase.UseCase
import me.him188.ani.utils.platform.collections.mapToIntList
import org.koin.core.Koin
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration.Companion.minutes

data class AiringScheduleForDate(
    val date: LocalDate,
    val list: List<EpisodeWithAiringTime>,
)

data class EpisodeWithAiringTime(
    val subject: LightSubjectInfo,
    val episode: LightEpisodeInfo,
    val airingTime: Instant,
) {
    val combinedId = packInts(subject.subjectId, episode.episodeId)
}

fun interface GetAnimeScheduleFlowUseCase : UseCase {
    operator fun invoke(now: Instant, timeZone: TimeZone): Flow<List<AiringScheduleForDate>>
}

class GetAnimeScheduleFlowUseCaseImpl(
    koin: Koin,
    private val defaultDispatcher: CoroutineContext = Dispatchers.Default,
) : GetAnimeScheduleFlowUseCase {
    private val animeScheduleRepository: AnimeScheduleRepository by koin.inject()
    private val subjectCollectionRepository: SubjectCollectionRepository by koin.inject()

    override fun invoke(now: Instant, timeZone: TimeZone): Flow<List<AiringScheduleForDate>> =
        animeScheduleRepository.recentSchedulesFlow()
            .flatMapLatest { schedule ->
                val onAirAnimeInfos = schedule.flatMap { it.list }
                    .filter {
                        val end = it.end
                        it.begin != null && it.recurrence != null && (end == null || end < Clock.System.now())
                    }

                subjectCollectionRepository.batchLightSubjectAndEpisodesFlow(onAirAnimeInfos.mapToIntList { it.bangumiId })
                    .mapLatest { subjects ->
                        (0..6).map { offsetDays ->
                            val date = now.toLocalDateTime(timeZone).date.plus(DatePeriod(days = offsetDays))
                            val airingSchedule = AnimeScheduleHelper.buildAiringScheduleForDate(
                                subjects,
                                onAirAnimeInfos,
                                date,
                                timeZone,
                                allowedDeviation = 1.minutes,
                            )
                            AiringScheduleForDate(
                                date,
                                airingSchedule.map { episodeSchedule ->
                                    EpisodeWithAiringTime(
                                        subject = subjects.first { it.subjectId == episodeSchedule.subjectId }.subject,
                                        episode = episodeSchedule.episode,
                                        airingTime = episodeSchedule.airingTime,
                                    )
                                }.distinctBy { it.combinedId },
                            )
                        }
                    }
            }.flowOn(defaultDispatcher)
}