/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.exploration.schedule

import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import me.him188.ani.app.domain.episode.GetAnimeScheduleFlowUseCase
import me.him188.ani.app.domain.usecase.GlobalKoin
import me.him188.ani.app.ui.foundation.AbstractViewModel
import org.koin.core.Koin

class SchedulePageViewModel(
    koin: Koin = GlobalKoin,
) : AbstractViewModel() {
    private val getAnimeScheduleFlowUseCase: GetAnimeScheduleFlowUseCase by koin.inject()

    private val airingSchedulesFlow =
        getAnimeScheduleFlowUseCase(currentTime(), timeZone = TimeZone.currentSystemDefault())
            .shareInBackground()

    val pageState = SchedulePageState(

    )

    val presentationFlow = airingSchedulesFlow.map { list ->
        val timeZone = TimeZone.currentSystemDefault()
        SchedulePagePresentation(
            airingSchedules = list.map { airingSchedule ->
                val currentDateTime = currentTime().toLocalDateTime(timeZone)
                AiringSchedule(
                    airingSchedule.date,
                    SchedulePageDataHelper.toColumnItems(
                        airingSchedule.list.map { it.toPresentation(timeZone) },
                        addIndicator = currentDateTime.date == airingSchedule.date,
                        currentDateTime.time,
                    ),
                )
            },
        )
    }.stateIn(
        backgroundScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SchedulePagePresentation(emptyList(), isPlaceholder = true),
    )

    private fun currentTime() = Clock.System.now()
}

class SchedulePagePresentation(
    val airingSchedules: List<AiringSchedule>,
    val isPlaceholder: Boolean = false,
)
