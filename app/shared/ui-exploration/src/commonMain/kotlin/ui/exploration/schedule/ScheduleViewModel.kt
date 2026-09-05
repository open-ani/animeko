/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.exploration.schedule

import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import me.him188.ani.app.domain.episode.AiringScheduleForDate
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
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * 新番时间表页面的 ViewModel.
 *
 * - "今天" 是一个 flow: 构造后立即发出当前本地日期, 之后每到 [timeZone] 的本地 00:00 再发一次 (每次都重新计算到下一个 00:00 的延迟,
 *   所以夏令时切换也正确). 向服务端请求的窗口由它派生 (`flatMapLatest`).
 * - [presentationFlow] 是页面状态的唯一来源: 日期列 ([SchedulePagePresentation.days]) 和每列的内容
 *   ([SchedulePagePresentation.airingSchedules]) 总是由同一个 [ScheduleLoad] 生成, 所以永远一致.
 *   今天变化 (或 [refresh]) 时, 在新的响应到达之前, 先立即发出一个以新的今天为基准的占位 presentation
 *   ([SchedulePagePresentation.isPlaceholder]), 日期列和列内容一起移动, 不会出现表头是新的一天而内容还是旧的一天.
 * - [pageState] 的日期列也来自 [presentationFlow] (经由 [presentationState]).
 * - [refresh] 重启整条链路: 重新读取今天并重新请求服务端.
 * - 当前时间指示器每分钟重新计算一次.
 *
 * 本 ViewModel 由 androidx `viewModel {}` 取得, 不会被 compose remember, 所以不使用 [AbstractViewModel.init].
 *
 * @param clock 时钟. 测试用假时钟驱动跨午夜.
 */
class ScheduleViewModel(
    koin: Koin = GlobalKoin,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
    private val clock: Clock = Clock.System,
) : AbstractViewModel() {
    private val getAnimeScheduleFlowUseCase: GetAnimeScheduleFlowUseCase by koin.inject()

    private fun currentToday(): LocalDate = clock.now().toLocalDateTime(timeZone).date

    /**
     * 当前本地日期. 立即发出一次, 之后每到本地 00:00 再发一次.
     */
    private val todayFlow: Flow<LocalDate> = flow {
        while (true) {
            val now = clock.now()
            emit(now.toLocalDateTime(timeZone).date)
            // 至少等 1 秒, 防止时钟异常时空转
            delay(delayUntilNextMidnight(now, timeZone).coerceAtLeast(1.seconds))
        }
    }.distinctUntilChanged()

    /**
     * 每分钟发出一次 (对齐到整分钟), 用于移动当前时间指示器.
     */
    private val minuteTicker: Flow<Unit> = flow {
        while (true) {
            emit(Unit)
            delay(delayUntilNextMinute(clock.now()))
        }
    }

    /**
     * 一次请求的状态: 以 [today] 为基准的日期窗口, 以及服务端的响应.
     *
     * @param result `null` 表示正在加载 (刚跨过午夜或 [refresh] 之后, 新的响应还没到), 此时页面显示以 [today] 为基准的占位列.
     */
    private class ScheduleLoad(
        val today: LocalDate,
        val result: Result<List<AiringScheduleForDate>>?,
    )

    private val airingSchedulesFlowRestarter = FlowRestarter()
    private val airingSchedulesFlow: Flow<ScheduleLoad> = todayFlow
        .flatMapLatest { today ->
            getAnimeScheduleFlowUseCase(today, timeZone = timeZone)
                .catching()
                .map { ScheduleLoad(today, it) }
                // 今天一变就先发出 "加载中", 让日期列和占位列立即移动到新的今天, 不等服务端响应
                .onStart { emit(ScheduleLoad(today, result = null)) }
        }
        .restartable(airingSchedulesFlowRestarter)
        .shareInBackground(started = SharingStarted.Lazily) // always cached

    /**
     * 重新读取今天并重新请求服务端. 在新的响应到达之前, [presentationFlow] 先发出占位状态.
     */
    fun refresh() {
        airingSchedulesFlowRestarter.restart()
    }

    /**
     * [presentationFlow] 的 snapshot 镜像, 供 [pageState] 读取: 在 [presentationFlow] 的每次发出时同步更新, 二者内容永远相同.
     * 单独镜像一份是因为 [ScheduleScreenState.days] 用 `derivedStateOf` 读取日期列, 只有 Compose snapshot state 才能触发它重新计算,
     * 直接读 [StateFlow.value] 不会.
     */
    private val presentationState = mutableStateOf(ScheduleLoad(currentToday(), result = null).toPresentation(clock.now()))

    val presentationFlow: StateFlow<SchedulePagePresentation> = combine(airingSchedulesFlow, minuteTicker) { load, _ ->
        load.toPresentation(clock.now())
    }
        .onEach { presentationState.value = it }
        .stateIn(
            backgroundScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = presentationState.value,
        )

    /**
     * 页面状态. 日期列来自 [presentationFlow] (经由 [presentationState]), 与列内容同源.
     */
    val pageState = ScheduleScreenState { presentationState.value.days }

    /**
     * 日期列和列内容都由 [ScheduleLoad.today] 派生, 所以二者的日期序列总是相同.
     */
    private fun ScheduleLoad.toPresentation(now: Instant): SchedulePagePresentation {
        val days = ScheduleDay.generateForRecentTwoWeeks(today)
        val loaded = result
            ?: return SchedulePagePresentation(
                days = days,
                airingSchedules = generatePlaceholderAiringScheduleList(today),
                error = null,
                isPlaceholder = true,
            )

        val timeZone = timeZone
        val currentDateTime = now.toLocalDateTime(timeZone)
        return SchedulePagePresentation(
            days = days,
            airingSchedules = loaded.getOrNull()?.map { airingSchedule ->
                AiringSchedule(
                    airingSchedule.date,
                    SchedulePageDataHelper.toColumnItems(
                        airingSchedule.list.map { it.toPresentation(timeZone) },
                        addIndicator = currentDateTime.date == airingSchedule.date,
                        currentDateTime.time,
                    ),
                )
            }.orEmpty(),
            error = loaded.exceptionOrNull()?.let { LoadError.fromException(it) },
        )
    }

    companion object {
        /**
         * 从 [now] 到 [timeZone] 的下一个本地 00:00 的时长, 总是大于零.
         * 用 [LocalDate.atStartOfDayIn] 计算, 夏令时切换 (一天 23 或 25 小时, 或者 00:00 不存在) 也正确.
         */
        internal fun delayUntilNextMidnight(now: Instant, timeZone: TimeZone): Duration {
            val today = now.toLocalDateTime(timeZone).date
            val nextMidnight = today.plus(DatePeriod(days = 1)).atStartOfDayIn(timeZone)
            return nextMidnight - now
        }

        /**
         * 从 [now] 到下一个整分钟的时长, 在 `(0, 1min]` 内.
         */
        internal fun delayUntilNextMinute(now: Instant): Duration {
            val millisInMinute = 60_000L
            return (millisInMinute - now.toEpochMilliseconds().mod(millisInMinute)).milliseconds
        }
    }
}

data class SchedulePagePresentation(
    val days: List<ScheduleDay>,
    val airingSchedules: List<AiringSchedule>,
    val error: LoadError?,
    val isPlaceholder: Boolean = false,
)

private fun generatePlaceholderAiringScheduleList(
    baseDate: LocalDate,
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
