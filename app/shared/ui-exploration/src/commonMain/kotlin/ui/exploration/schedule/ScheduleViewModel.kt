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
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import me.him188.ani.app.data.repository.user.SettingsRepository
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
import org.koin.core.component.inject

/**
 * 新番时间表页面的 ViewModel.
 *
 * - [scheduleTimeZone] 是页面使用的时区: 默认读取用户偏好
 *   ([me.him188.ani.app.data.models.preference.UISettings.scheduleTimeZoneId], `null` 表示系统时区),
 *   测试可通过构造参数固定. 时区变化后重新计算"今天"并重新请求服务端, 日期列
 *   ([SchedulePagePresentation.days]) 与每列内容一起切换.
 * - "今天" 是一个 flow: 构造后立即发出当前 [scheduleTimeZone] 下的本地日期, 之后每到该时区的本地 00:00
 *   再发一次 (每次都重新计算到下一个 00:00 的延迟, 所以夏令时切换也正确). 向服务端请求的窗口由它派生
 *   (`flatMapLatest`).
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
 * @param koin 依赖来源. 测试传入自己的 Koin.
 * @param clock 时钟. 测试用假时钟驱动跨午夜.
 * @param timeZoneOverride 非 `null` 时固定使用该时区, 不读取用户偏好. 供测试与预览使用.
 */
open class ScheduleViewModel(
    koin: Koin = GlobalKoin,
    private val clock: Clock = Clock.System,
    timeZoneOverride: TimeZone? = null,
) : AbstractViewModel() {
    private val getAnimeScheduleFlowUseCase: GetAnimeScheduleFlowUseCase by koin.inject()
    private val settingsRepository: SettingsRepository by koin.inject()

    /**
     * 用户选择的时区, 或测试固定的时区. 单例 `flow` 而非 `StateFlow`, 以便测试替换.
     */
    private val selectedTimeZoneFlow: Flow<TimeZone?> =
        if (timeZoneOverride != null) {
            flowOf(timeZoneOverride)
        } else {
            settingsRepository.uiSettings.flow.map { uiSettings ->
                uiSettings.scheduleTimeZoneId?.let(TimeZone::of)
            }
        }

    /**
     * 页面使用的时区. [selectedTimeZoneFlow] 为 `null` 时取系统时区.
     *
     * 每次读取都重新调用 [TimeZone.currentSystemDefault], 因此系统时区改变后无需任何操作即可生效.
     */
    val scheduleTimeZone: StateFlow<TimeZone> = selectedTimeZoneFlow
        .map { it ?: TimeZone.currentSystemDefault() }
        .distinctUntilChanged()
        .stateIn(backgroundScope, SharingStarted.Eagerly, timeZoneOverride ?: TimeZone.currentSystemDefault())

    /**
     * 修改时区. 传 `null` 表示跟随系统时区.
     */
    fun setScheduleTimeZone(timeZone: TimeZone?) {
        backgroundScope.launch {
            settingsRepository.uiSettings.update { copy(scheduleTimeZoneId = timeZone?.id) }
        }
    }

    private fun currentToday(): LocalDate = clock.now().toLocalDateTime(scheduleTimeZone.value).date

    /**
     * 在 [timeZone] 下立即发出当前本地日期, 之后每到该时区的本地 00:00 再发一次.
     *
     * 每次循环都重新读取 [Clock.now], 因此时区变化 (含夏令时) 不会把已经过期的延迟带入下一轮.
     *
     * [distinctUntilChanged] 必须留在内层, 只对本时区内的连续值去重 (跨午夜时不会重复发出同一天).
     * 若把它提到 [todayFlow] 的 `flatMapLatest` 之外, 两个偏移不同但同属一天的时区 (如上海与柏林)
     * 切换时, 内层发出的日期与前一个时区相同, 这次变化会被整个丢掉, 服务端就不会用新时区重新请求.
     */
    private fun todayFlowFor(timeZone: TimeZone): Flow<LocalDate> = flow {
        while (true) {
            val now = clock.now()
            emit(now.toLocalDateTime(timeZone).date)
            // 至少等 1 秒, 防止时钟异常时空转
            delay(delayUntilNextMidnight(now, timeZone).coerceAtLeast(1.seconds))
        }
    }.distinctUntilChanged()

    /**
     * 当前本地日期. 立即发出一次, 之后每到本地 00:00 再发一次.
     *
     * 时区变化时 [flatMapLatest] 会重启内层 flow: **即使新旧时区的当前日期相同, 也会重新发出该日期**,
     * 让 [airingSchedulesFlow] 用新时区重新请求服务端 (各剧集归属的日期与时刻都可能不同).
     */
    private val todayFlow: Flow<LocalDate> = scheduleTimeZone
        .flatMapLatest { timeZone -> todayFlowFor(timeZone) }

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
            getAnimeScheduleFlowUseCase(today, timeZone = scheduleTimeZone.value)
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

        val timeZone = scheduleTimeZone.value
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
