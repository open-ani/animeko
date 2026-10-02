/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.utils.selectorworkflow

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import kotlin.time.Duration
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Duration.Companion.seconds

/**
 * 时间线播放器. 只管一件事: 把外面喂进来的帧时刻变成播放位置.
 *
 * 它不自己开协程 —— 帧由 UI (`withFrameNanos`) 或测试代码驱动, 于是同一份逻辑在有没有 Compose 的场合都能跑.
 */
@Stable
class TimelinePlayer(
    timeline: SelectorWorkflowTimeline,
) {
    var timeline: SelectorWorkflowTimeline = timeline
        set(value) {
            field = value
            // 换了时间线就按比例保留进度, 免得切开关时画面跳回开头
            val ratio = if (state.duration > Duration.ZERO) {
                playhead.inWholeMicroseconds.toDouble() / state.duration.inWholeMicroseconds
            } else {
                0.0
            }
            playhead = value.duration * ratio.coerceIn(0.0, 1.0)
            state = value.sampleAt(playhead)
        }

    /** 当前播放位置. */
    var playhead: Duration = Duration.ZERO
        private set

    /** 供 Compose 直接读的快照. */
    var state: SelectorWorkflowState by mutableStateOf(timeline.sampleAt(Duration.ZERO))
        private set

    var isPlaying: Boolean by mutableStateOf(true)

    /** 播放速度. 1 = 原速. */
    var speed: Float by mutableStateOf(1f)

    /** 播到头之后回到开头. */
    var loop: Boolean by mutableStateOf(true)

    private var lastFrameNanos: Long = UNSET

    /**
     * 喂一帧. [frameTimeNanos] 用 `withFrameNanos` 的值即可, 单调递增就行.
     */
    fun onFrame(frameTimeNanos: Long) {
        val previous = lastFrameNanos
        lastFrameNanos = frameTimeNanos
        if (previous == UNSET) return
        if (!isPlaying) return
        val delta = (frameTimeNanos - previous).coerceIn(0L, MAX_FRAME_NANOS)
        advance(delta.nanoseconds * speed.toDouble())
    }

    fun advance(by: Duration) {
        if (by <= Duration.ZERO) return
        val total = timeline.duration
        var next = playhead + by
        if (next > total) {
            next = if (loop && total > Duration.ZERO) {
                (next.inWholeMicroseconds % total.inWholeMicroseconds).microsecondsDuration()
            } else {
                total
            }
        }
        seekTo(next)
    }

    fun seekTo(time: Duration) {
        playhead = time.coerceIn(Duration.ZERO, timeline.duration)
        state = timeline.sampleAt(playhead)
    }

    /** 按 0..1 的比例定位, 用于拖进度条. */
    fun seekToFraction(fraction: Float) {
        seekTo(timeline.duration * fraction.coerceIn(0f, 1f).toDouble())
    }

    fun restart() {
        lastFrameNanos = UNSET
        seekTo(Duration.ZERO)
    }

    private companion object {
        const val UNSET = Long.MIN_VALUE

        /** 单帧最多推进这么多, 免得后台回来之后一次跳过整段动画. */
        const val MAX_FRAME_NANOS = 100_000_000L
    }
}

private fun Long.microsecondsDuration(): Duration = (this * 1000).nanoseconds

/**
 * 数据源选择流程示意动画的 ViewModel.
 *
 * 它只负责三件事:
 * 1. 拿着 [SelectorWorkflowConfig], 配置一变就重新编译时间线;
 * 2. 把帧交给 [TimelinePlayer];
 * 3. 暴露 [state] 给 Compose Canvas 读.
 *
 * 界面上那三个开关、两个输入框直接对应下面的 `setXxx`.
 */
@Stable
class SelectorWorkflowViewModel(
    initialConfig: SelectorWorkflowConfig = SelectorWorkflowPresets.threeSources(),
) : ViewModel() {

    var config: SelectorWorkflowConfig by mutableStateOf(initialConfig)
        private set

    val player: TimelinePlayer = TimelinePlayer(initialConfig.buildTimeline())

    /** Canvas 要画的全部东西. */
    val state: SelectorWorkflowState get() = player.state

    /** 每帧调用一次. */
    fun onFrame(frameTimeNanos: Long) = player.onFrame(frameTimeNanos)

    /**
     * 换配置. 编译失败 (例如预算短到根本来不及拦截) 时保持原样并返回 `false`.
     */
    fun updateConfig(transform: (SelectorWorkflowConfig) -> SelectorWorkflowConfig): Boolean {
        val next = runCatching { transform(config) }.getOrNull() ?: return false
        if (next == config) return true
        val timeline = runCatching { next.buildTimeline() }.getOrNull() ?: return false
        config = next
        player.timeline = timeline
        return true
    }

    // ---------------------------------------------------------------- 界面上的开关

    /**
     * 一次把几个开关都定下来, 只重编一次时间线.
     *
     * 接进设置页时很有用: 那里的规则是"动一个设置项就只演它对应的那段", 所以每次都要同时
     * 打开一个、关掉另一个.
     *
     * @param eager 抢先选源.
     * @param priorityWaitSeconds 高优先级等待要演多少秒; `null` 表示不演这一段.
     * @param resolveBudgetSeconds 拦截超时要演多少秒; `null` 表示第三步只演成功、不出计时器.
     * @param cacheQuery 第一步走缓存, 瞬间出结果.
     * @param highlight 给哪一步罩上高亮框, 即"刚动的那个设置项管的是这一步"; `null` 表示不罩.
     * 这里要显式给, 不按开着哪些特性去推 —— 抢先选源是常驻状态, 跟着它一直亮就成了背景板.
     * @param restart 是否从头播放.
     */
    fun configure(
        eager: Boolean,
        priorityWaitSeconds: Int? = null,
        resolveBudgetSeconds: Int? = null,
        cacheQuery: Boolean = false,
        highlight: HighlightRegion? = null,
        restart: Boolean = true,
    ): Boolean {
        val ok = updateConfig { current ->
            current.copy(
                cachedQuery = cacheQuery,
                highlights = setOfNotNull(highlight),
                selection = current.selection.copy(
                    mode = if (eager) SelectMode.Eager else SelectMode.WaitAll,
                    priorityWait = priorityWaitSeconds?.seconds,
                    demoBothPriorityPaths = priorityWaitSeconds != null,
                    lateLatency = null,
                ),
                resolve = current.resolve.copy(
                    budget = (resolveBudgetSeconds ?: current.resolve.budget.inWholeSeconds.toInt())
                        .coerceAtLeast(1).seconds,
                    outcomes = if (resolveBudgetSeconds != null) {
                        listOf(ResolveOutcome.Hit, ResolveOutcome.Timeout, ResolveOutcome.HitAfterFallback)
                    } else {
                        listOf(ResolveOutcome.Hit)
                    },
                ),
            )
        }
        if (ok && restart) player.restart()
        return ok
    }

    // 下面这几个是 playground 用的单项开关: 开几个亮几个, 高亮框跟着当前开着的特性走.

    /** 抢先选源. */
    fun setEagerSelect(enabled: Boolean) = updateConfig {
        it.copy(
            selection = it.selection.copy(
                mode = if (enabled) SelectMode.Eager else SelectMode.WaitAll,
            ),
        ).withFeatureHighlights()
    }

    /**
     * 最大等待高优先级源: 开关 + 秒数.
     *
     * 秒数只决定计时器旁边那个读数数到几, 不改变动画长度 —— 指针转一圈的时长是
     * [Pacing.clockSweep].
     */
    fun setPriorityWait(enabled: Boolean, seconds: Int = DEFAULT_PRIORITY_WAIT_SECONDS) = updateConfig {
        it.copy(
            selection = it.selection.copy(
                priorityWait = if (enabled) seconds.seconds else null,
                demoBothPriorityPaths = enabled,
                lateLatency = null,
            ),
        ).withFeatureHighlights()
    }

    fun setPriorityWaitSeconds(seconds: Int) = updateConfig {
        if (it.selection.priorityWait == null) it
        else it.copy(selection = it.selection.copy(priorityWait = seconds.seconds))
    }

    /** 第一步走缓存: 数据源查询结果已缓存, 三条线瞬间画满并泛起涟漪. */
    fun setCacheQuery(enabled: Boolean) = updateConfig {
        it.copy(cachedQuery = enabled).withFeatureHighlights()
    }

    /** 第三步: 连演成功 / 超时 / 换下一个候选再成功. */
    fun setResolveDemo(enabled: Boolean) = updateConfig {
        it.copy(
            resolve = it.resolve.copy(
                outcomes = if (enabled) {
                    listOf(ResolveOutcome.Hit, ResolveOutcome.Timeout, ResolveOutcome.HitAfterFallback)
                } else {
                    listOf(ResolveOutcome.Hit)
                },
            ),
        ).withFeatureHighlights()
    }

    /**
     * 拦截播放链接的最大等待时长 (秒).
     *
     * 同样只决定读数, 不改变动画长度.
     */
    fun setInterceptBudgetSeconds(seconds: Int) = updateConfig {
        it.copy(resolve = it.resolve.copy(budget = seconds.seconds))
    }

    private companion object {
        const val DEFAULT_PRIORITY_WAIT_SECONDS = 5
    }
}
