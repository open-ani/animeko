/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.exploration.schedule

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import me.him188.ani.app.domain.episode.GetAnimeScheduleFlowUseCase
import me.him188.ani.app.domain.foundation.LoadError
import me.him188.ani.app.domain.usecase.GlobalKoin
import me.him188.ani.app.ui.foundation.AbstractViewModel
import me.him188.ani.utils.coroutines.flows.FlowRestarter
import me.him188.ani.utils.coroutines.flows.catching
import me.him188.ani.utils.coroutines.flows.restartable
import me.him188.ani.utils.platform.annotations.TestOnly
import org.koin.core.Koin
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes

class ScheduleViewModel(
    koin: Koin = GlobalKoin,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) : AbstractViewModel() {
    private val getAnimeScheduleFlowUseCase: GetAnimeScheduleFlowUseCase by koin.inject()

    /**
     * 当前时刻, 每分钟一跳 (纯本地, 不发任何请求).
     *
     * 上游数据一小时才拉一次 ([AnimeScheduleRepository][me.him188.ani.app.data.repository.episode.AnimeScheduleRepository]
     * 的 ticker), 若只在数据到达时读一次时刻, "现在几点"与"已播出/待播"的分界就一直停在上次拉取
     * 那一刻 —— 坐在页面上等到某部的播出时刻, 界面不会翻.
     */
    private val nowFlow = flow {
        while (true) {
            emit(Clock.System.now())
            delay(TICK_PERIOD)
        }
    }

    /** "今天" (跨零点滚动). 变了才 emit —— 15 天窗口与上游请求都要跟着重建. */
    private val todayFlow = nowFlow
        .map { it.toLocalDateTime(timeZone).date }
        .distinctUntilChanged()

    private val airingSchedulesFlowRestarter = FlowRestarter()
    private val airingSchedulesFlow = todayFlow
        .flatMapLatest { today -> getAnimeScheduleFlowUseCase(today, timeZone = timeZone) }
        .catching()
        // restart 会让上游整条重新执行 (即重新请求), [refresh] 靠它生效. 缺了这一步 refresh()
        // 只是把计数 +1 而没人监听, 表现为"重试/强制刷新按了没反应"
        .restartable(airingSchedulesFlowRestarter)
        .shareInBackground(started = SharingStarted.Lazily) // always cached

    // 15 天窗口. 用 snapshot state: PC 页的 [pageState] 通过 derivedStateOf 读它, 跨零点要跟着重算.
    // 只在"今天"变化时换新实例 —— 每分钟造一个内容相同的新 List 会让下游以它为 key 的 remember 白重算
    private var daysDate = Clock.System.now().toLocalDateTime(timeZone).date
    private var days by mutableStateOf(ScheduleDay.generateForRecentTwoWeeks(daysDate))
    val pageState = ScheduleScreenState { days }

    /** 强制重拉 (出错时的重试按钮 / TV 端长按播放键). */
    fun refresh() {
        airingSchedulesFlowRestarter.restart()
    }

    val presentationFlow = combine(airingSchedulesFlow, todayFlow, nowFlow) { result, today, now ->
        val timeZone = timeZone
        val currentDateTime = now.toLocalDateTime(timeZone)
        SchedulePagePresentation(
            daysFor(today),
            airingSchedules = result.getOrNull()?.map { airingSchedule ->
                AiringSchedule(
                    airingSchedule.date,
                    SchedulePageDataHelper.toColumnItems(
                        airingSchedule.list.map { it.toPresentation(timeZone) },
                        addIndicator = currentDateTime.date == airingSchedule.date,
                        currentDateTime.time,
                    ),
                )
            }.orEmpty(),
            error = result.exceptionOrNull()?.let { LoadError.fromException(it) },
        )
    }.stateIn(
        backgroundScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SchedulePagePresentation(
            days,
            generatePlaceholderAiringScheduleList(),
            error = null,
            isPlaceholder = true,
        ),
    )

    // 只在上面那条 combine 里调用 (单协程, 无并发)
    private fun daysFor(today: LocalDate): List<ScheduleDay> {
        if (today != daysDate) {
            daysDate = today
            days = ScheduleDay.generateForRecentTwoWeeks(today)
        }
        return days
    }

    private companion object {
        /** 当前时刻的重算周期: 界面上"现在几点"精确到分钟, 再密没有意义. */
        private val TICK_PERIOD = 1.minutes
    }
}

data class SchedulePagePresentation(
    val days: List<ScheduleDay>,
    val airingSchedules: List<AiringSchedule>,
    val error: LoadError?,
    val isPlaceholder: Boolean = false,
)

private fun generatePlaceholderAiringScheduleList(
    baseDate: LocalDate = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
): List<AiringSchedule> {
    val episodes = (1..10).map {
        AiringScheduleColumnItem.PlaceholderData(id = it, showTime = true)
    }
    return SchedulePageDataHelper.OFFSET_DAYS_RANGE.map { offset ->
        AiringSchedule(
            baseDate.plus(DatePeriod(days = offset)),
            episodes = episodes,
        )
    }
}

@TestOnly
fun createTestSchedulePagePresentation() = SchedulePagePresentation(
    days = ScheduleDay.generateForRecentTwoWeeks(LocalDate(2025, 12, 10)),
    airingSchedules = TestSchedulePageData,
    null,
)
