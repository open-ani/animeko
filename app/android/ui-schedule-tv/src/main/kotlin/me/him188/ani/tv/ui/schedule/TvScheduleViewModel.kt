/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.tv.ui.schedule

import androidx.compose.runtime.Stable
import kotlin.time.Clock
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.retry
import kotlinx.coroutines.flow.stateIn
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import me.him188.ani.app.domain.episode.AiringScheduleForDate
import me.him188.ani.app.domain.episode.GetAnimeScheduleFlowUseCase
import me.him188.ani.app.ui.foundation.AbstractViewModel
import me.him188.ani.utils.logging.logger
import me.him188.ani.utils.logging.warn
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * TV 新番时间表薄 VM (atv-architecture.md §7.2): 15 天窗口 (前后各 7 天).
 */
@Stable
class TvScheduleViewModel : AbstractViewModel(), KoinComponent {
    private val getAnimeScheduleFlowUseCase: GetAnimeScheduleFlowUseCase by inject()

    private val timeZone = TimeZone.currentSystemDefault()
    val today = Clock.System.todayIn(timeZone)

    val schedule: StateFlow<List<AiringScheduleForDate>?> =
        getAnimeScheduleFlowUseCase(today, timeZone)
            .retry(3)
            .catch { logger.warn(it) { "Failed to load schedule" } }
            .stateIn(backgroundScope, SharingStarted.WhileSubscribed(5_000), null)

    private companion object {
        private val logger = logger<TvScheduleViewModel>()
    }
}
