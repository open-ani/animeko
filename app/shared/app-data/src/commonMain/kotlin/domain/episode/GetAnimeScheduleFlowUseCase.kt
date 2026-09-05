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
import kotlinx.coroutines.flow.flowOn
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import me.him188.ani.app.data.models.subject.LightEpisodeInfo
import me.him188.ani.app.data.models.subject.LightSubjectInfo
import me.him188.ani.app.data.repository.episode.AnimeScheduleRepository
import me.him188.ani.app.domain.usecase.UseCase
import kotlin.coroutines.CoroutineContext
import kotlin.time.Instant

data class AiringScheduleForDate(
    val date: LocalDate,
    val list: List<EpisodeWithAiringTime>,
)

/**
 * @param airingTime 放送时间. [timeKnown] 为 `false` 时, 这是该剧集的 Bangumi 放送日期在客户端时区的 00:00.
 * @param timeKnown 放送时间是否精确已知. `false` 表示服务端只知道 Bangumi 的放送日期, 而没有可靠的放送时刻 (如没有 recurrence, 或与 Bangumi 日期不符).
 */
data class EpisodeWithAiringTime(
    val subject: LightSubjectInfo,
    val episode: LightEpisodeInfo,
    val airingTime: Instant,
    val timeKnown: Boolean,
) {
    val combinedId = packInts(subject.subjectId, episode.episodeId)
}

fun interface GetAnimeScheduleFlowUseCase : UseCase {
    operator fun invoke(today: LocalDate, timeZone: TimeZone): Flow<List<AiringScheduleForDate>>

    companion object {
        val OFFSET_DAYS_RANGE = (-7..7)
    }
}

class GetAnimeScheduleFlowUseCaseImpl(
    private val animeScheduleRepository: AnimeScheduleRepository,
    private val defaultDispatcher: CoroutineContext = Dispatchers.Default,
) : GetAnimeScheduleFlowUseCase {
    override fun invoke(today: LocalDate, timeZone: TimeZone): Flow<List<AiringScheduleForDate>> =
        animeScheduleRepository.recentAiringSchedulesFlow(today, timeZone)
            .flowOn(defaultDispatcher)
}
