/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.exploration.schedule

import kotlinx.coroutines.flow.map
import me.him188.ani.app.data.repository.episode.AnimeScheduleRepository
import me.him188.ani.app.domain.usecase.GlobalKoin
import me.him188.ani.app.ui.foundation.AbstractViewModel
import org.koin.core.Koin

class SchedulePageViewModel(
    koin: Koin = GlobalKoin,
) : AbstractViewModel() {
    private val scheduleRepository: AnimeScheduleRepository by koin.inject()

    val pageStateFlow =
        scheduleRepository.recentScheduleSubjectsFlow()
            .map { list ->
                SchedulePageDataHelper.withCurrentTimeIndicator()
            }
}
