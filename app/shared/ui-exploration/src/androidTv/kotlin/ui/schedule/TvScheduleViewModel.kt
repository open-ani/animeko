/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.tv.ui.schedule

import me.him188.ani.app.ui.exploration.schedule.ScheduleViewModel
import me.him188.ani.tv.ui.foundation.TvNavigationEvent
import me.him188.ani.tv.ui.foundation.TvNavigationEvents
import me.him188.ani.utils.analytics.Analytics
import me.him188.ani.utils.analytics.AnalyticsEvent.Companion.SubjectEnter
import me.him188.ani.utils.analytics.recordEvent
import org.koin.core.Koin

sealed interface TvScheduleIntent {
    data object Refresh : TvScheduleIntent
    data class OpenSubject(val subjectId: Int) : TvScheduleIntent
}

class TvScheduleViewModel(koin: Koin) : ScheduleViewModel(koin) {
    private val navigation = TvNavigationEvents()
    val navigationEvents = navigation.events

    fun onIntent(intent: TvScheduleIntent) {
        when (intent) {
            TvScheduleIntent.Refresh -> refresh()
            is TvScheduleIntent.OpenSubject -> {
                Analytics.recordEvent(SubjectEnter) {
                    put("source", "schedule")
                    put("subject_id", intent.subjectId)
                }
                navigation.emit(TvNavigationEvent.Subject(intent.subjectId))
            }
        }
    }
}
