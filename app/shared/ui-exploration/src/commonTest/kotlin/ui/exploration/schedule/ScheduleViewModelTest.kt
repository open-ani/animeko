/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.exploration.schedule

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.UtcOffset
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import me.him188.ani.app.data.models.danmaku.DanmakuFilterConfig
import me.him188.ani.app.data.models.preference.AnalyticsSettings
import me.him188.ani.app.data.models.preference.AnitorrentConfig
import me.him188.ani.app.data.models.preference.DanmakuSettings
import me.him188.ani.app.data.models.preference.DebugSettings
import me.him188.ani.app.data.models.preference.MediaCacheSettings
import me.him188.ani.app.data.models.preference.MediaPreference
import me.him188.ani.app.data.models.preference.MediaSelectorSettings
import me.him188.ani.app.data.models.preference.OneshotActionConfig
import me.him188.ani.app.data.models.preference.PikPakConfig
import me.him188.ani.app.data.models.preference.PlayerKernelConfig
import me.him188.ani.app.data.models.preference.ProfileSettings
import me.him188.ani.app.data.models.preference.ProxySettings
import me.him188.ani.app.data.models.preference.ThemeSettings
import me.him188.ani.app.data.models.preference.TorrentPeerConfig
import me.him188.ani.app.data.models.preference.UISettings
import me.him188.ani.app.data.models.preference.UpdateSettings
import me.him188.ani.app.data.models.preference.VideoResolverSettings
import me.him188.ani.app.data.models.preference.VideoScaffoldConfig
import me.him188.ani.app.data.models.preference.WatchTogetherSettings
import me.him188.ani.app.data.models.subject.LightEpisodeInfo
import me.him188.ani.app.data.models.subject.LightSubjectInfo
import me.him188.ani.app.data.repository.user.Settings
import me.him188.ani.app.data.repository.user.SettingsRepository
import me.him188.ani.app.domain.episode.AiringScheduleForDate
import me.him188.ani.app.domain.episode.EpisodeWithAiringTime
import me.him188.ani.app.domain.episode.GetAnimeScheduleFlowUseCase
import me.him188.ani.datasources.api.EpisodeSort
import me.him188.ani.datasources.api.PackedDate
import me.him188.ani.datasources.api.UTC9
import me.him188.ani.danmaku.ui.DanmakuConfig
import org.koin.core.Koin
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlin.time.TimeSource

/**
 * 覆盖 [ScheduleViewModel]:
 * - [ScheduleViewModel.delayUntilNextMidnight] / [ScheduleViewModel.delayUntilNextMinute] 的纯计算 (含夏令时);
 * - 跨过本地午夜时 "今天" 变化: 在新的响应到达之前先发出以新的今天为基准的占位状态, 日期列 (表头) 和列内容一起移动,
 *   [ScheduleViewModel.pageState] 的日期列与 presentation 同源, 然后用新的今天重新请求;
 * - [ScheduleViewModel.refresh] 重新调用 use case, 新响应到达前显示占位状态, 出错后可以恢复.
 *
 * ViewModel 的 flow 跑在它自己的 backgroundScope (真实线程) 上, 所以等待都在 [Dispatchers.Default] 上带真实超时进行,
 * 不用 runTest 的虚拟时间 (虚拟时间会让 withTimeout 立刻触发).
 * 跨午夜用 [OffsetClock]: 从一个接近午夜的时刻起随真实时间推进, 只需真实等待约 1 秒.
 */
class ScheduleViewModelTest {
    // region delayUntilNextMidnight / delayUntilNextMinute

    @Test
    fun `delayUntilNextMidnight in a zone without DST`() {
        val zone = TimeZone.of("Asia/Shanghai")
        val now = LocalDateTime(2026, 9, 4, 22, 30).toInstant(zone)
        assertEquals(1.hours + 30.minutes, ScheduleViewModel.delayUntilNextMidnight(now, zone))
    }

    @Test
    fun `delayUntilNextMidnight at exactly midnight is a full day`() {
        val zone = TimeZone.of("Asia/Shanghai")
        val now = LocalDateTime(2026, 9, 5, 0, 0).toInstant(zone)
        assertEquals(24.hours, ScheduleViewModel.delayUntilNextMidnight(now, zone))
    }

    @Test
    fun `delayUntilNextMidnight one millisecond before midnight`() {
        val zone = TimeZone.of("Asia/Shanghai")
        val now = LocalDateTime(2026, 9, 5, 0, 0).toInstant(zone) - 1.milliseconds
        assertEquals(1.milliseconds, ScheduleViewModel.delayUntilNextMidnight(now, zone))
    }

    @Test
    fun `delayUntilNextMidnight targets the next local midnight not UTC midnight`() {
        val zone = TimeZone.of("Asia/Shanghai") // UTC+8
        val now = Instant.parse("2026-09-04T15:00:00Z") // 2026-09-04 23:00 local
        val delay = ScheduleViewModel.delayUntilNextMidnight(now, zone)
        assertEquals(1.hours, delay)
        assertEquals(LocalDate(2026, 9, 5).atStartOfDayIn(zone), now + delay)
    }

    @Test
    fun `delayUntilNextMidnight across DST spring forward`() {
        // 2026-03-08 02:00 EST -> 03:00 EDT (America/New_York): 这一天只有 23 小时
        val zone = TimeZone.of("America/New_York")
        val now = LocalDateTime(2026, 3, 8, 1, 30).toInstant(zone) // 01:30 EST = 06:30Z
        val delay = ScheduleViewModel.delayUntilNextMidnight(now, zone)
        assertEquals(21.hours + 30.minutes, delay) // 不是 22.5h
        assertEquals(LocalDate(2026, 3, 9).atStartOfDayIn(zone), now + delay)
    }

    @Test
    fun `delayUntilNextMidnight across DST fall back`() {
        // 2026-11-01 02:00 EDT -> 01:00 EST (America/New_York): 这一天有 25 小时
        val zone = TimeZone.of("America/New_York")
        val now = Instant.parse("2026-11-01T05:30:00Z") // 01:30 EDT, 回拨之前
        val delay = ScheduleViewModel.delayUntilNextMidnight(now, zone)
        assertEquals(23.hours + 30.minutes, delay) // 不是 22.5h
        assertEquals(LocalDate(2026, 11, 2).atStartOfDayIn(zone), now + delay)
    }

    @Test
    fun `delayUntilNextMidnight when the next midnight does not exist`() {
        // America/Sao_Paulo 2018-11-04: 00:00 -> 01:00, 当天从 01:00 (-02:00) 开始, 没有 00:00
        val zone = TimeZone.of("America/Sao_Paulo")
        val now = LocalDateTime(2018, 11, 3, 12, 0).toInstant(zone) // 15:00Z
        val delay = ScheduleViewModel.delayUntilNextMidnight(now, zone)
        assertTrue(delay.isPositive())
        assertEquals(LocalDate(2018, 11, 4).atStartOfDayIn(zone), now + delay)
        assertEquals(Instant.parse("2018-11-04T03:00:00Z"), now + delay)
    }

    @Test
    fun `delayUntilNextMinute is aligned to the minute and within one minute`() {
        assertEquals(1.minutes, ScheduleViewModel.delayUntilNextMinute(Instant.parse("2026-09-04T12:00:00Z")))
        assertEquals(30.seconds, ScheduleViewModel.delayUntilNextMinute(Instant.parse("2026-09-04T12:00:30Z")))
        assertEquals(1.milliseconds, ScheduleViewModel.delayUntilNextMinute(Instant.parse("2026-09-04T12:00:59.999Z")))
    }

    // endregion

    // region ViewModel

    /**
     * 从 [start] 开始随真实时间推进的时钟.
     */
    private class OffsetClock(private val start: Instant) : Clock {
        private val mark = TimeSource.Monotonic.markNow()
        override fun now(): Instant = start + mark.elapsedNow()
    }

    /**
     * 记录每次调用的 `today` 与 `timeZone`; 返回 [handler] 生成的 flow.
     */
    private class FakeGetAnimeScheduleFlowUseCase(
        private val handler: (today: LocalDate, callIndex: Int) -> Flow<List<AiringScheduleForDate>>,
    ) : GetAnimeScheduleFlowUseCase {
        val calls = MutableStateFlow<List<LocalDate>>(emptyList())
        val timeZones = MutableStateFlow<List<TimeZone>>(emptyList())

        override fun invoke(today: LocalDate, timeZone: TimeZone): Flow<List<AiringScheduleForDate>> {
            val index = calls.value.size
            calls.update { it + today }
            timeZones.update { it + timeZone }
            return handler(today, index)
        }
    }

    /**
     * 只实现 [SettingsRepository.uiSettings] 的自定义偏好; ViewModel 不依赖其他字段,
     * 因此访问它们会立刻失败而不是返回可能掩盖问题的默认值.
     */
    private class FakeSettingsRepository(
        initial: UISettings = UISettings.Default,
    ) : SettingsRepository {
        val state = MutableStateFlow(initial)

        override val uiSettings: Settings<UISettings> = object : Settings<UISettings> {
            override val flow: Flow<UISettings> get() = state
            override suspend fun set(value: UISettings) {
                state.value = value
            }
        }

        override val danmakuEnabled: Settings<Boolean> get() = notNeeded()
        override val danmakuConfig: Settings<DanmakuConfig> get() = notNeeded()
        override val danmakuFilterConfig: Settings<DanmakuFilterConfig> get() = notNeeded()
        override val mediaSelectorSettings: Settings<MediaSelectorSettings> get() = notNeeded()
        override val defaultMediaPreference: Settings<MediaPreference> get() = notNeeded()
        override val profileSettings: Settings<ProfileSettings> get() = notNeeded()
        override val proxySettings: Settings<ProxySettings> get() = notNeeded()
        override val mediaCacheSettings: Settings<MediaCacheSettings> get() = notNeeded()
        override val danmakuSettings: Settings<DanmakuSettings> get() = notNeeded()
        override val themeSettings: Settings<ThemeSettings> get() = notNeeded()
        override val updateSettings: Settings<UpdateSettings> get() = notNeeded()
        override val videoScaffoldConfig: Settings<VideoScaffoldConfig> get() = notNeeded()
        override val playerKernelConfig: Settings<PlayerKernelConfig> get() = notNeeded()
        override val videoResolverSettings: Settings<VideoResolverSettings> get() = notNeeded()
        override val anitorrentConfig: Settings<AnitorrentConfig> get() = notNeeded()
        override val pikpakConfig: Settings<PikPakConfig> get() = notNeeded()
        override val torrentPeerConfig: Settings<TorrentPeerConfig> get() = notNeeded()
        override val oneshotActionConfig: Settings<OneshotActionConfig> get() = notNeeded()
        override val analyticsSettings: Settings<AnalyticsSettings> get() = notNeeded()
        override val debugSettings: Settings<DebugSettings> get() = notNeeded()
        override val watchTogetherSettings: Settings<WatchTogetherSettings> get() = notNeeded()

        private fun <T> notNeeded(): Settings<T> =
            error("ScheduleViewModel 只使用 uiSettings")
    }

    private val timeZone = TimeZone.of("Asia/Shanghai")

    private fun koinWith(
        useCase: GetAnimeScheduleFlowUseCase,
        settings: FakeSettingsRepository = FakeSettingsRepository(),
    ): Koin = koinApplication {
        modules(
            module {
                single<GetAnimeScheduleFlowUseCase> { useCase }
                single<SettingsRepository> { settings }
            },
        )
    }.koin

    private fun episode(
        subjectId: Int,
        airingTime: Instant,
        timeKnown: Boolean,
    ) = EpisodeWithAiringTime(
        subject = LightSubjectInfo(subjectId = subjectId, name = "Subject $subjectId", nameCn = "", imageLarge = ""),
        episode = LightEpisodeInfo(
            episodeId = subjectId * 10,
            name = "Ep",
            nameCn = "",
            airDate = PackedDate(2026, 9, 4),
            timezone = UTC9,
            sort = EpisodeSort(1),
            ep = EpisodeSort(1),
        ),
        airingTime = airingTime,
        timeKnown = timeKnown,
    )

    /**
     * 每个日期一个 timeKnown 和一个时间未定的剧集.
     */
    private fun scheduleFor(today: LocalDate): List<AiringScheduleForDate> =
        SchedulePageDataHelper.OFFSET_DAYS_RANGE.map { offset ->
            val date = today.plus(DatePeriod(days = offset))
            AiringScheduleForDate(
                date = date,
                list = listOf(
                    episode(1, date.atStartOfDayIn(timeZone), timeKnown = false),
                    episode(2, date.atStartOfDayIn(timeZone) + 20.hours, timeKnown = true),
                ),
            )
        }

    private val fixtureScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val viewModels = mutableListOf<ScheduleViewModel>()

    private fun newViewModel(useCase: GetAnimeScheduleFlowUseCase, clock: Clock): ScheduleViewModel =
        ScheduleViewModel(
            koin = koinWith(useCase),
            clock = clock,
            timeZoneOverride = timeZone,
        ).also { viewModels += it }

    /**
     * 不固定时区, 因此时区来自 [FakeSettingsRepository] (即用户偏好), 可以测试切换时区的行为.
     */
    private fun newViewModelReadingTimeZoneSetting(
        useCase: GetAnimeScheduleFlowUseCase,
        clock: Clock,
        settings: FakeSettingsRepository,
    ): ScheduleViewModel =
        ScheduleViewModel(
            koin = koinWith(useCase, settings),
            clock = clock,
        ).also { viewModels += it }

    @AfterTest
    fun tearDown() {
        viewModels.forEach { it.backgroundScope.cancel() }
        viewModels.clear()
        fixtureScope.cancel()
    }

    /**
     * 在真实线程上等待, 带真实超时: runTest 的虚拟时间会让 withTimeout 立刻触发, 所以切到 Default.
     */
    private suspend fun <T> awaitReal(block: suspend () -> T): T = withContext(Dispatchers.Default) {
        withTimeout(20.seconds) { block() }
    }

    private fun List<ScheduleDay>.today(): LocalDate = first { it.kind == ScheduleDay.Kind.TODAY }.date

    /**
     * 日期列 (表头) 和列内容必须来自同一个今天: 每一列的日期序列与日期列的日期序列完全相同.
     */
    private fun assertColumnsMatchDays(presentation: SchedulePagePresentation) {
        assertEquals(
            presentation.days.map { it.date },
            presentation.airingSchedules.map { it.date },
            "columns must cover exactly the days of the header (placeholder=${presentation.isPlaceholder})",
        )
    }

    @Test
    fun `initial state uses today from the injected clock and time zone`() = runTest {
        val today = LocalDate(2026, 9, 4)
        val useCase = FakeGetAnimeScheduleFlowUseCase { day, _ -> flow { emit(scheduleFor(day)) } }
        val clock = OffsetClock(LocalDateTime(2026, 9, 4, 12, 0).toInstant(timeZone))
        val vm = newViewModel(useCase, clock)

        assertEquals(today, vm.pageState.days.today())
        assertEquals(15, vm.pageState.days.size)
        val initial = vm.presentationFlow.value
        assertTrue(initial.isPlaceholder)
        assertNull(initial.error)
        assertEquals(today, initial.days.today())
        assertEquals(today.plus(DatePeriod(days = -7)), initial.airingSchedules.first().date)
        assertEquals(today.plus(DatePeriod(days = 7)), initial.airingSchedules.last().date)
        assertColumnsMatchDays(initial)
        assertEquals(initial.days, vm.pageState.days)
        // 没有订阅就不会请求
        assertEquals(emptyList(), useCase.calls.value)
    }

    @Test
    fun `presentation renders unknown-time items after timed items`() = runTest {
        val today = LocalDate(2026, 9, 4)
        val useCase = FakeGetAnimeScheduleFlowUseCase { day, _ -> flow { emit(scheduleFor(day)) } }
        val clock = OffsetClock(LocalDateTime(2026, 9, 4, 12, 0).toInstant(timeZone))
        val vm = newViewModel(useCase, clock)
        val subscription = fixtureScope.launch { vm.presentationFlow.collect {} }

        val presentation = awaitReal { vm.presentationFlow.first { !it.isPlaceholder } }
        assertNull(presentation.error)
        assertEquals(listOf(today), useCase.calls.value)
        assertEquals(today, presentation.days.today())
        assertColumnsMatchDays(presentation)

        val todayColumn = presentation.airingSchedules.first { it.date == today }.episodes
        // 20:00 的项目, 当前时间指示器 (12:00 在 20:00 之前), 然后是时间未定的项目
        assertEquals(3, todayColumn.size)
        assertIs<AiringScheduleColumnItem.CurrentTimeIndicator>(todayColumn[0])
        val timed = assertIs<AiringScheduleColumnItem.Data>(todayColumn[1])
        assertEquals(2, timed.item.subjectId)
        assertNotNull(timed.item.time)
        assertTrue(timed.showTime)
        val unknown = assertIs<AiringScheduleColumnItem.Data>(todayColumn[2])
        assertEquals(1, unknown.item.subjectId)
        assertNull(unknown.item.time)
        assertTrue(unknown.showTime)

        // 不是今天的列没有指示器
        val otherColumn = presentation.airingSchedules.first { it.date != today }.episodes
        assertEquals(2, otherColumn.size)
        assertTrue(otherColumn.none { it is AiringScheduleColumnItem.CurrentTimeIndicator })

        subscription.cancel()
    }

    @Test
    fun `crossing local midnight moves header and columns together and shows loading before the new response`() =
        runTest {
            val day1 = LocalDate(2026, 9, 4)
            val day2 = LocalDate(2026, 9, 5)
            // 第二次请求 (day2) 的响应由测试控制, 以便观察响应到达之前的状态
            val day2Response = CompletableDeferred<Unit>()
            val useCase = FakeGetAnimeScheduleFlowUseCase { day, callIndex ->
                flow {
                    if (callIndex >= 1) day2Response.await()
                    emit(scheduleFor(day))
                }
            }
            // 距离本地午夜 500ms; ViewModel 至少等 1 秒后重新读取时钟, 那时已经是第二天
            val clock = OffsetClock(LocalDateTime(2026, 9, 5, 0, 0).toInstant(timeZone) - 500.milliseconds)
            val vm = newViewModel(useCase, clock)
            assertEquals(day1, vm.pageState.days.today())
            assertEquals(day1, vm.presentationFlow.value.days.today())

            val observed = MutableStateFlow<List<SchedulePagePresentation>>(emptyList())
            val subscription = fixtureScope.launch {
                vm.presentationFlow.collect { presentation -> observed.update { it + presentation } }
            }

            val loadedDay1 = awaitReal { vm.presentationFlow.first { !it.isPlaceholder } }
            assertEquals(day1, loadedDay1.days.today())
            assertColumnsMatchDays(loadedDay1)
            assertEquals(listOf(day1), useCase.calls.value)

            // 跨过午夜: day2 的响应还没到, 但日期列 (表头) 和占位列已经一起移到了 day2
            val loadingDay2 = awaitReal { vm.presentationFlow.first { it.days.today() == day2 } }
            assertTrue(loadingDay2.isPlaceholder, "the first presentation for the new day must be the loading placeholder")
            assertNull(loadingDay2.error)
            assertEquals(day2.plus(DatePeriod(days = -7)), loadingDay2.days.first().date)
            assertEquals(day2.plus(DatePeriod(days = 7)), loadingDay2.days.last().date)
            assertColumnsMatchDays(loadingDay2)
            assertEquals(loadingDay2.days, vm.pageState.days)

            awaitReal { useCase.calls.first { it.size >= 2 } }
            assertEquals(listOf(day1, day2), useCase.calls.value)
            // 响应没到, 一直是占位状态
            assertTrue(vm.presentationFlow.value.isPlaceholder)
            assertEquals(day2, vm.presentationFlow.value.days.today())
            // observed 由另一个协程收集, 与这里没有同步; StateFlow 会合并状态, 所以在放行响应之前先等它记录到占位状态
            awaitReal { observed.first { list -> list.any { it.isPlaceholder && it.days.today() == day2 } } }

            day2Response.complete(Unit)
            val presentation = awaitReal {
                vm.presentationFlow.first { !it.isPlaceholder && it.days.today() == day2 }
            }
            assertNull(presentation.error)
            assertEquals(day2.plus(DatePeriod(days = -7)), presentation.days.first().date)
            assertEquals(day2.plus(DatePeriod(days = 7)), presentation.days.last().date)
            assertEquals(day2.plus(DatePeriod(days = -7)), presentation.airingSchedules.first().date)
            assertEquals(day2.plus(DatePeriod(days = 7)), presentation.airingSchedules.last().date)
            assertColumnsMatchDays(presentation)
            // 只有今天的列有当前时间指示器
            assertEquals(
                listOf(day2),
                presentation.airingSchedules.filter { schedule ->
                    schedule.episodes.any { it is AiringScheduleColumnItem.CurrentTimeIndicator }
                }.map { it.date },
            )
            // 页面状态的日期列 (ScheduleScreenState.days) 与 presentation 同源, 一起移动了
            assertEquals(day2, vm.pageState.days.today())
            assertEquals(presentation.days, vm.pageState.days)

            // 观察到的每一个状态里, 表头和列内容都一致 (从来没有 "表头是 day2, 内容还是 day1"); 占位状态先于加载完成的状态
            val all = awaitReal { observed.first { list -> list.any { !it.isPlaceholder && it.days.today() == day2 } } }
            all.forEach(::assertColumnsMatchDays)
            val firstDay2Loading = all.indexOfFirst { it.isPlaceholder && it.days.today() == day2 }
            val firstDay2Loaded = all.indexOfFirst { !it.isPlaceholder && it.days.today() == day2 }
            assertTrue(firstDay2Loading >= 0, "a loading presentation for the new day must be observed")
            assertTrue(
                firstDay2Loading < firstDay2Loaded,
                "loading (index $firstDay2Loading) must precede loaded (index $firstDay2Loaded)",
            )
            // 表头一旦移到 day2 就不再回到 day1
            assertTrue(all.drop(firstDay2Loading).none { it.days.today() == day1 })

            subscription.cancel()
        }

    @Test
    fun `refresh re-invokes the use case with the current today`() = runTest {
        val today = LocalDate(2026, 9, 4)
        val useCase = FakeGetAnimeScheduleFlowUseCase { day, _ -> flow { emit(scheduleFor(day)) } }
        val clock = OffsetClock(LocalDateTime(2026, 9, 4, 12, 0).toInstant(timeZone))
        val vm = newViewModel(useCase, clock)
        val subscription = fixtureScope.launch { vm.presentationFlow.collect {} }

        awaitReal { vm.presentationFlow.first { !it.isPlaceholder } }
        assertEquals(listOf(today), useCase.calls.value)

        vm.refresh()
        awaitReal { useCase.calls.first { it.size >= 2 } }
        assertEquals(listOf(today, today), useCase.calls.value)

        vm.refresh()
        awaitReal { useCase.calls.first { it.size >= 3 } }
        assertEquals(listOf(today, today, today), useCase.calls.value)

        subscription.cancel()
    }

    @Test
    fun `refresh shows a loading presentation until the new response arrives`() = runTest {
        val today = LocalDate(2026, 9, 4)
        val secondResponse = CompletableDeferred<Unit>()
        val useCase = FakeGetAnimeScheduleFlowUseCase { day, callIndex ->
            flow {
                if (callIndex >= 1) secondResponse.await()
                emit(scheduleFor(day))
            }
        }
        val clock = OffsetClock(LocalDateTime(2026, 9, 4, 12, 0).toInstant(timeZone))
        val vm = newViewModel(useCase, clock)
        val subscription = fixtureScope.launch { vm.presentationFlow.collect {} }

        val loadedBefore = awaitReal { vm.presentationFlow.first { !it.isPlaceholder } }
        assertEquals(listOf(today), useCase.calls.value)

        vm.refresh()
        val loading = awaitReal { vm.presentationFlow.first { it.isPlaceholder } }
        assertNull(loading.error)
        assertEquals(loadedBefore.days, loading.days)
        assertColumnsMatchDays(loading)
        assertEquals(loading.days, vm.pageState.days)
        awaitReal { useCase.calls.first { it.size >= 2 } }
        assertEquals(listOf(today, today), useCase.calls.value)
        assertTrue(vm.presentationFlow.value.isPlaceholder)

        secondResponse.complete(Unit)
        val loaded = awaitReal { vm.presentationFlow.first { !it.isPlaceholder } }
        assertNull(loaded.error)
        assertEquals(15, loaded.airingSchedules.size)
        assertColumnsMatchDays(loaded)

        subscription.cancel()
    }

    @Test
    fun `refresh recovers from an error`() = runTest {
        val today = LocalDate(2026, 9, 4)
        val useCase = FakeGetAnimeScheduleFlowUseCase { day, callIndex ->
            flow {
                if (callIndex == 0) throw IllegalStateException("network down")
                emit(scheduleFor(day))
            }
        }
        val clock = OffsetClock(LocalDateTime(2026, 9, 4, 12, 0).toInstant(timeZone))
        val vm = newViewModel(useCase, clock)
        val subscription = fixtureScope.launch { vm.presentationFlow.collect {} }

        val failed = awaitReal { vm.presentationFlow.first { !it.isPlaceholder } }
        assertNotNull(failed.error)
        assertEquals(emptyList(), failed.airingSchedules)
        assertEquals(today, failed.days.today())

        vm.refresh()
        val loaded = awaitReal { vm.presentationFlow.first { !it.isPlaceholder && it.error == null } }
        assertEquals(listOf(today, today), useCase.calls.value)
        assertEquals(15, loaded.airingSchedules.size)

        subscription.cancel()
    }

    // endregion

    // region 时区选择

    /**
     * 时区不同, "今天" 就不同: 服务端窗口的基准日期随之改变.
     */
    @Test
    fun `time zone decides which date is today`() = runTest {
        // 2026-09-04T16:00Z: Asia/Shanghai (UTC+8) 已是 9-5, America/Los_Angeles (UTC-7) 还是 9-4
        val now = Instant.parse("2026-09-04T16:00:00Z")
        val shanghai = TimeZone.of("Asia/Shanghai")
        val losAngeles = TimeZone.of("America/Los_Angeles")

        val shanghaiNow = now.toLocalDateTime(shanghai)
        assertEquals(LocalDate(2026, 9, 5), shanghaiNow.date, "前提: 上海此时已是 9-5")
        assertEquals(
            LocalDate(2026, 9, 4),
            now.toLocalDateTime(losAngeles).date,
            "前提: 洛杉矶此时还是 9-4",
        )
    }

    /**
     * 切换时区后: 时区偏好被写入, "今天" 按新时区重算, 并用新时区重新请求服务端.
     */
    @Test
    fun `switching time zone re-reads today and refetches with the new zone`() = runTest {
        // 上海 2026-09-05 00:00, 洛杉矶仍是 2026-09-04 09:00
        val now = Instant.parse("2026-09-04T16:00:00Z")
        val shanghai = TimeZone.of("Asia/Shanghai")
        val losAngeles = TimeZone.of("America/Los_Angeles")
        val shanghaiToday = LocalDate(2026, 9, 5)
        val losAngelesToday = LocalDate(2026, 9, 4)

        val settings = FakeSettingsRepository(UISettings(scheduleTimeZoneId = shanghai.id))
        val useCase = FakeGetAnimeScheduleFlowUseCase { day, _ -> flow { emit(scheduleFor(day)) } }
        val vm = newViewModelReadingTimeZoneSetting(useCase, OffsetClock(now), settings)
        val subscription = fixtureScope.launch { vm.presentationFlow.collect {} }

        val before = awaitReal { vm.presentationFlow.first { !it.isPlaceholder } }
        assertEquals(shanghai, vm.scheduleTimeZone.value, "初始时区来自偏好")
        assertEquals(shanghaiToday, before.days.today())

        vm.setScheduleTimeZone(losAngeles)

        // 偏好被写入
        awaitReal { settings.state.first { it.scheduleTimeZoneId == losAngeles.id } }
        assertEquals(losAngeles, awaitReal { vm.scheduleTimeZone.first { it == losAngeles } })

        // "今天" 按新时区重算, 日期列 (表头) 移动到新的今天
        val after = awaitReal { vm.presentationFlow.first { it.days.today() == losAngelesToday } }
        assertEquals(losAngelesToday, after.days.today())
        assertEquals(after.days, vm.pageState.days, "日期列与 presentation 同源")

        // 用新时区重新请求, 且请求的 today 也按新时区计算
        awaitReal { useCase.calls.first { it.size >= 2 } }
        assertEquals(listOf(shanghaiToday, losAngelesToday), useCase.calls.value)
        assertEquals(
            listOf(shanghai, losAngeles),
            awaitReal { useCase.timeZones.first { it.size >= 2 } },
            "两次请求分别使用各自的时区",
        )

        subscription.cancel()
    }

    /**
     * 切回系统时区时偏好被清空 (`null`), 而不是记录下系统时区的 ID.
     */
    @Test
    fun `selecting the system time zone clears the preference`() = runTest {
        val now = Instant.parse("2026-09-04T16:00:00Z")
        val shanghai = TimeZone.of("Asia/Shanghai")
        val settings = FakeSettingsRepository(UISettings(scheduleTimeZoneId = shanghai.id))
        val useCase = FakeGetAnimeScheduleFlowUseCase { day, _ -> flow { emit(scheduleFor(day)) } }
        val vm = newViewModelReadingTimeZoneSetting(useCase, OffsetClock(now), settings)
        val subscription = fixtureScope.launch { vm.presentationFlow.collect {} }

        awaitReal { vm.presentationFlow.first { !it.isPlaceholder } }
        assertEquals(shanghai, vm.scheduleTimeZone.value)

        vm.setScheduleTimeZone(null)

        assertNull(awaitReal { settings.state.first { it.scheduleTimeZoneId == null } }.scheduleTimeZoneId)
        assertEquals(
            TimeZone.currentSystemDefault(),
            awaitReal { vm.scheduleTimeZone.first { it == TimeZone.currentSystemDefault() } },
            "跟随系统时区",
        )

        subscription.cancel()
    }

    @Test
    fun `formatUtcOffset renders the sign and padded offset`() {
        assertEquals("UTC+08:00", formatUtcOffset(UtcOffset(hours = 8)))
        assertEquals("UTC-05:00", formatUtcOffset(UtcOffset(hours = -5)))
        assertEquals("UTC+05:30", formatUtcOffset(UtcOffset(hours = 5, minutes = 30)))
        assertEquals("UTC+00:00", formatUtcOffset(UtcOffset(hours = 0)))
    }

    @Test
    fun `selectable time zones are valid ids and unique`() {
        assertTrue(ScheduleSelectableTimeZones.isNotEmpty())
        assertEquals(
            ScheduleSelectableTimeZones.size,
            ScheduleSelectableTimeZones.toSet().size,
            "不应有重复项",
        )
        for (id in ScheduleSelectableTimeZones) {
            // 无法解析的 ID 会让选择器少一个选项, 因此这里逐个校验
            assertEquals(id, TimeZone.of(id).id, "时区 ID 应当可解析: $id")
        }
    }

    /**
     * 首次打开 (偏好里没有时区) 时自动使用系统所在时区.
     */
    @Test
    fun `without a stored preference the system time zone is used`() = runTest {
        val settings = FakeSettingsRepository() // scheduleTimeZoneId 默认 null = 未选择过
        val useCase = FakeGetAnimeScheduleFlowUseCase { day, _ -> flow { emit(scheduleFor(day)) } }
        val vm = newViewModelReadingTimeZoneSetting(
            useCase,
            OffsetClock(LocalDateTime(2026, 9, 4, 12, 0).toInstant(timeZone)),
            settings,
        )

        assertEquals(
            TimeZone.currentSystemDefault(),
            awaitReal { vm.scheduleTimeZone.first() },
            "未选择过时应自动选取系统时区",
        )
        assertNull(settings.state.value.scheduleTimeZoneId, "自动选取不应写入偏好")
    }

    /**
     * 用户手动选过时区后, 之后每次打开都使用该时区, 而不是重新跟随系统.
     */
    @Test
    fun `a stored preference wins over the system time zone on later launches`() = runTest {
        val settings = FakeSettingsRepository(UISettings(scheduleTimeZoneId = "Asia/Tokyo"))
        val useCase = FakeGetAnimeScheduleFlowUseCase { day, _ -> flow { emit(scheduleFor(day)) } }
        val vm = newViewModelReadingTimeZoneSetting(
            useCase,
            OffsetClock(LocalDateTime(2026, 9, 4, 12, 0).toInstant(timeZone)),
            settings,
        )

        val restored = awaitReal { vm.scheduleTimeZone.first() }
        assertEquals(TimeZone.of("Asia/Tokyo"), restored, "手动选择过的时区应当被沿用")
        assertTrue(
            restored != TimeZone.currentSystemDefault() || TimeZone.of("Asia/Tokyo") == TimeZone.currentSystemDefault(),
            "前提: 该时区与系统时区不同, 才能真正区分二者",
        )
    }

    /**
     * 切换时区必须让服务端重新请求 —— 即使新旧时区的"今天"是同一天.
     *
     * 这条覆盖一个已被修复的缺陷: `distinctUntilChanged` 曾放在 `todayFlow` 的 `flatMapLatest` 之外,
     * 于是上海 → 柏林 (同属 9-30) 这类切换的去重会把日期变化整个丢掉, 服务端不再收到新时区,
     * 页面停在旧数据上, 只有离开页面再进入才会刷新.
     */
    @Test
    fun `switching to a zone on the same date still refetches with the new zone`() = runTest {
        val shanghai = TimeZone.of("Asia/Shanghai")
        val berlin = TimeZone.of("Europe/Berlin")
        assertEquals(
            Instant.parse("2026-09-30T10:00:00Z").toLocalDateTime(shanghai).date,
            Instant.parse("2026-09-30T10:00:00Z").toLocalDateTime(berlin).date,
            "前提: 两个时区下同属 2026-09-30, 因此不能用日期变化来触发刷新",
        )

        val settings = FakeSettingsRepository(UISettings(scheduleTimeZoneId = shanghai.id))
        val useCase = FakeGetAnimeScheduleFlowUseCase { day, _ -> flow { emit(emptyList()) } }
        val vm = newViewModelReadingTimeZoneSetting(
            useCase,
            OffsetClock(Instant.parse("2026-09-30T10:00:00Z")),
            settings,
        )
        val subscription = fixtureScope.launch { vm.presentationFlow.collect {} }
        awaitReal { useCase.timeZones.first { it.isNotEmpty() } }

        vm.setScheduleTimeZone(berlin)

        assertEquals(
            listOf(shanghai, berlin),
            awaitReal { useCase.timeZones.first { it.size >= 2 } },
            "同一天内切换时区也必须用新时区重新请求",
        )

        subscription.cancel()
    }

    /**
     * 切换到同一天但偏移不同的时区时, 日期列不变, 但每列的放送时刻必须重新计算.
     */
    @Test
    fun `switching to a zone on the same date refreshes the displayed times`() = runTest {
        val shanghai = TimeZone.of("Asia/Shanghai")
        val berlin = TimeZone.of("Europe/Berlin")
        // 2026-09-30T10:00Z: 上海 18:00 (9-30), 柏林 12:00 (9-30) —— 同一天, 时刻不同
        val airingTime = Instant.parse("2026-09-30T10:00:00Z")
        assertEquals(LocalDate(2026, 9, 30), airingTime.toLocalDateTime(shanghai).date)
        assertEquals(
            LocalDate(2026, 9, 30),
            airingTime.toLocalDateTime(berlin).date,
            "前提: 两个时区下同属一天, 因此日期列本身不会变化",
        )

        val settings = FakeSettingsRepository(UISettings(scheduleTimeZoneId = shanghai.id))
        val useCase = FakeGetAnimeScheduleFlowUseCase { day, _ ->
            flow {
                emit(
                    listOf(
                        AiringScheduleForDate(
                            date = day,
                            list = listOf(episode(1, airingTime, timeKnown = true)),
                        ),
                    ),
                )
            }
        }
        val vm = newViewModelReadingTimeZoneSetting(useCase, OffsetClock(Instant.parse("2026-09-30T10:00:00Z")), settings)
        val subscription = fixtureScope.launch { vm.presentationFlow.collect {} }

        fun SchedulePagePresentation.firstTime(): LocalTime? =
            (airingSchedules.first().episodes.first() as AiringScheduleColumnItem.Data).item.time

        val shanghaiPresentation = awaitReal { vm.presentationFlow.first { !it.isPlaceholder } }
        assertEquals(LocalTime(18, 0), shanghaiPresentation.firstTime(), "上海应为 18:00")

        vm.setScheduleTimeZone(berlin)

        val berlinPresentation = awaitReal {
            vm.presentationFlow.first { it.firstTime() == LocalTime(12, 0) }
        }
        assertEquals(LocalTime(12, 0), berlinPresentation.firstTime(), "柏林应为 12:00")
        assertEquals(
            shanghaiPresentation.days,
            berlinPresentation.days,
            "两个时区同属一天, 日期列应当一致",
        )

        subscription.cancel()
    }

    // endregion
}
