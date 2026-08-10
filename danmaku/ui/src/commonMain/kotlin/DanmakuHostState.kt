/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.danmaku.ui

import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.him188.ani.danmaku.api.DanmakuLocation
import me.him188.ani.danmaku.ui.layout.CompiledDanmakuLayout
import me.him188.ani.danmaku.ui.layout.DanmakuLayoutCompiler
import me.him188.ani.danmaku.ui.layout.DanmakuLayoutParams
import me.him188.ani.danmaku.ui.layout.PlacedFixedDanmaku
import me.him188.ani.danmaku.ui.layout.PlacedFloatingDanmaku
import me.him188.ani.danmaku.ui.layout.PlaybackClock
import me.him188.ani.danmaku.ui.layout.danmakuLayoutKey
import me.him188.ani.danmaku.ui.layout.fixedDurationVideoMillis
import me.him188.ani.danmaku.ui.layout.floatingDanmakuExitTime
import me.him188.ani.danmaku.ui.layout.floatingDanmakuFits
import me.him188.ani.danmaku.ui.layout.floatingDanmakuSpeed
import kotlin.math.floor

/**
 * [DanmakuHostState] 是弹幕渲染的核心状态.
 *
 * ## 架构: 编译式布局 + 单一视频时间轴
 *
 * - 弹幕列表通过 [setDanmakuList] 整体提供, 由 [DanmakuLayoutCompiler] 编译为确定性的
 *   [CompiledDanmakuLayout]: 每条弹幕的 (轨道, 进入时刻, 速度) 完全确定;
 * - 播放器进度通过 [onPositionReport] 提供, [PlaybackClock] 将粗粒度报告平滑为每帧连续的视频时间;
 * - 每帧只做一件事: 用当前视频时间维护[可见窗口][visibleFloating] (双指针推进, seek 时二分重建),
 *   位置是布局与时间的纯函数, 没有任何逐帧积分状态;
 * - 布局参数 (字号/速度/密度/倍速/窗口尺寸) 或列表变化时[重新编译][recompile],
 *   已进入屏幕的弹幕通过前缀冻结保持原布局, 屏幕上永不跳变;
 * - 帧回调时间戳经 [FrameTimeSmoother] 锁相平滑, 消除桌面端时间戳抖动造成的顿挫.
 *
 * 典型接线:
 * 1. [setUIContext] 提供测量与密度;
 * 2. [observeConfig] 在协程中观察配置变化并触发重编译;
 * 3. [interpolateFrameLoop] 驱动帧循环;
 * 4. [setDanmakuList] / [onPositionReport] / [setPlaybackSpeed] / [setPaused] 与播放器连接;
 * 5. [send] 发送用户自己的弹幕.
 */
@Stable
class DanmakuHostState(
    danmakuConfigState: State<DanmakuConfig> = mutableStateOf(DanmakuConfig.Default),
    private val danmakuTrackProperties: DanmakuTrackProperties = DanmakuTrackProperties.Default,
) {
    private val danmakuConfig by danmakuConfigState
    private val uiContext: UIContext = UIContext()

    /**
     * The width of this DanmakuHost. Measured in pixels. Updated when the Composable hosting it changes size.
     */
    private val hostWidthState = mutableIntStateOf(0)
    internal var hostWidth by hostWidthState

    /**
     * The height of this DanmakuHost. Measured in pixels.
     */
    private val hostHeightState = mutableIntStateOf(0)
    internal var hostHeight by hostHeightState

    /**
     * The height of each track. Updated on font size / display area / host height changes.
     */
    private val trackHeightState = mutableIntStateOf(0)
    internal var trackHeight by trackHeightState
        private set

    internal val canvasAlpha by derivedStateOf { danmakuConfig.style.alpha }

    private val pausedState = mutableStateOf(false)
    internal val paused: Boolean get() = pausedState.value

    internal val isDebug by derivedStateOf { danmakuConfig.isDebug }

    /**
     * 平滑后的帧时钟累计值, 作为 [PlaybackClock] 的墙钟. 单位 ns.
     */
    internal var elapsedFrameTimeNanos by mutableLongStateOf(0L)
        private set

    /**
     * 当前的帧生成时间 (经 [FrameTimeSmoother] 平滑后)
     */
    internal var currentFrameTimeDeltaNanos by mutableLongStateOf(0L)
        private set

    /**
     * 当前的帧生成时间 (平滑前的原始值, 仅用于调试)
     */
    internal var currentFrameRawDeltaNanos by mutableLongStateOf(0L)
        private set

    /**
     * A timestamp-like value used to prompt canvas redraws.
     */
    internal var danmakuUpdateSubscription by mutableLongStateOf(0L)
        private set

    private val playbackClock = PlaybackClock()

    /**
     * 当前帧的视频时间估计. `-1` 表示还未收到任何进度报告.
     */
    internal var currentVideoTimeMillis by mutableLongStateOf(-1L)
        private set

    private val danmakuListState = mutableStateOf<List<DanmakuPresentation>>(emptyList())
    private val playbackSpeedState = mutableFloatStateOf(1f)

    private val layoutState = mutableStateOf<CompiledDanmakuLayout?>(null)
    internal val compiledLayout: CompiledDanmakuLayout? get() = layoutState.value

    // region 可见窗口 (仅主线程访问)
    internal val visibleFloating = ArrayList<VisibleFloatingDanmaku>()
    internal val visibleTop = ArrayList<VisibleFixedDanmaku>()
    internal val visibleBottom = ArrayList<VisibleFixedDanmaku>()

    private var floatingCursor = 0
    private var topCursor = 0
    private var bottomCursor = 0

    /** 可见窗口对应的布局与时刻 */
    private var windowLayout: CompiledDanmakuLayout? = null
    private var windowTimeMillis = Long.MIN_VALUE
    // endregion

    // region 用户自己发送的弹幕 (不在编译布局中, 覆盖显示)
    private val extraFloating = ArrayList<PlacedFloatingDanmaku>()
    private val extraTop = ArrayList<PlacedFixedDanmaku>()
    private val extraBottom = ArrayList<PlacedFixedDanmaku>()
    // endregion

    // 宽度测量缓存, 样式变化时整体失效
    private var widthCache = HashMap<String, Int>()
    private var widthCacheKey: Any? = null

    fun setUIContext(
        baseStyle: TextStyle,
        textMeasurer: TextMeasurer,
        density: Density
    ) {
        uiContext.set(baseStyle, textMeasurer, density)
    }

    // region 播放器接线

    /**
     * 设置当前的完整弹幕列表. 列表变化会触发重新编译;
     * 已在屏幕上的弹幕通过前缀冻结保持原布局不变.
     */
    fun setDanmakuList(list: List<DanmakuPresentation>) {
        danmakuListState.value = list
    }

    /**
     * 播放器报告当前进度. 小偏差被平滑消化, 大偏差 (seek) 立即跳变.
     */
    fun onPositionReport(positionMillis: Long) {
        playbackClock.onPositionReport(positionMillis, elapsedFrameTimeNanos)
    }

    fun setPlaybackSpeed(speed: Float) {
        if (speed <= 0f) return
        playbackClock.setPlaybackSpeed(speed, elapsedFrameTimeNanos)
        playbackSpeedState.floatValue = speed
    }

    fun setPaused(pause: Boolean) {
        if (pausedState.value == pause) return
        playbackClock.setPaused(pause, elapsedFrameTimeNanos)
        pausedState.value = pause
    }

    // endregion

    /**
     * 观察配置与输入变化:
     * - 布局相关变化 (尺寸/字号/密度/速度/倍速/列表/debug) -> [recompile]
     * - 仅样式变化 (颜色/透明度/描边/字重) -> 重建可见弹幕的样式, 不重新布局
     */
    internal suspend fun observeConfig(measurer: TextMeasurer) {
        uiContext.await()
        coroutineScope {
            launch {
                combine(
                    snapshotFlow { hostWidth },
                    snapshotFlow { hostHeight },
                    snapshotFlow { danmakuListState.value },
                    snapshotFlow { playbackSpeedState.floatValue },
                    snapshotFlow { danmakuConfig },
                ) { width, height, list, playbackSpeed, config ->
                    RecompileInputs(
                        trackWidth = width,
                        hostHeight = height,
                        list = list,
                        playbackSpeed = playbackSpeed,
                        fontSize = config.style.fontSize,
                        fontWeight = config.style.fontWeight,
                        displayArea = config.displayArea,
                        enableFloating = config.enableFloating,
                        enableTop = config.enableTop,
                        enableBottom = config.enableBottom,
                        speed = config.speed,
                        safeSeparation = config.safeSeparation,
                        isDebug = config.isDebug,
                    )
                }
                    .distinctUntilChanged()
                    // collectLatest: 新输入到来时取消进行中的编译, 始终以最新输入为准
                    .collectLatest { inputs ->
                        recompile(inputs, measurer)
                    }
            }
            launch {
                snapshotFlow { danmakuConfig }
                    .distinctUntilChanged { old, new ->
                        old.style.alpha == new.style.alpha &&
                                old.style.fontWeight == new.style.fontWeight &&
                                old.style.shadow == new.style.shadow &&
                                old.style.strokeColor == new.style.strokeColor &&
                                old.style.strokeWidth == new.style.strokeWidth &&
                                old.enableColor == new.enableColor
                    }
                    .collect {
                        withContext(Dispatchers.Main.immediate) {
                            restyleVisible()
                            danmakuUpdateSubscription++
                        }
                    }
            }
        }
    }

    private data class RecompileInputs(
        val trackWidth: Int,
        val hostHeight: Int,
        val list: List<DanmakuPresentation>,
        val playbackSpeed: Float,
        val fontSize: TextUnit,
        val fontWeight: FontWeight,
        val displayArea: Float,
        val enableFloating: Boolean,
        val enableTop: Boolean,
        val enableBottom: Boolean,
        val speed: Float,
        val safeSeparation: Dp,
        val isDebug: Boolean,
    )

    private suspend fun recompile(inputs: RecompileInputs, measurer: TextMeasurer) {
        if (inputs.trackWidth <= 0 || inputs.hostHeight <= 0) return

        val style = danmakuConfig.style
        val dummy = dummyDanmaku(measurer, uiContext.baseStyle, style, "哈哈哈哈")
        val verticalPadding = with(uiContext.density) {
            (danmakuTrackProperties.verticalPadding * 2).dp.toPx()
        }
        val newTrackHeight = (dummy.danmakuHeight + verticalPadding).toInt().coerceAtLeast(1)
        val trackCount = floor(inputs.hostHeight.toFloat() / newTrackHeight * inputs.displayArea)
            .coerceAtLeast(1f)
            .toInt()

        val params = DanmakuLayoutParams(
            trackWidthPx = inputs.trackWidth,
            floatingTrackCount = if (inputs.enableFloating) trackCount else 0,
            topTrackCount = if (inputs.enableTop) trackCount else 0,
            bottomTrackCount = if (inputs.enableBottom) trackCount else 0,
            baseSpeedPxPerSecond = with(uiContext.density) { inputs.speed.dp.toPx() },
            playbackSpeed = inputs.playbackSpeed,
            safeSeparationPx = with(uiContext.density) { inputs.safeSeparation.toPx() },
            baseSpeedTextWidthPx = dummy.danmakuWidth,
            speedMultiplier = danmakuTrackProperties.speedMultiplier,
            fixedDanmakuDurationMillis = danmakuTrackProperties.fixedDanmakuPresentDuration,
        )

        // 宽度缓存按影响文本宽度的样式输入失效
        val cacheKey = Triple(inputs.fontSize, inputs.fontWeight, inputs.isDebug)
        if (widthCacheKey != cacheKey) {
            widthCache = HashMap()
            widthCacheKey = cacheKey
        }
        val cache = widthCache
        val baseStyle = uiContext.baseStyle
        val isDebug = inputs.isDebug

        val previous = layoutState.value
        val freezeBefore = if (previous != null && currentVideoTimeMillis >= 0) {
            currentVideoTimeMillis
        } else Long.MIN_VALUE

        val compiled = withContext(Dispatchers.Default) {
            DanmakuLayoutCompiler.compile(
                list = inputs.list,
                params = params,
                measureWidth = { presentation ->
                    cache.getOrPut(danmakuLayoutKey(presentation)) {
                        measureDanmakuTextWidth(measurer, presentation, baseStyle, style, isDebug)
                    }
                },
                previous = previous,
                freezeBeforeMillis = freezeBefore,
            )
        }

        withContext(Dispatchers.Main.immediate) {
            trackHeight = newTrackHeight
            // 如果列表中已包含用户自己发送的弹幕, 移除覆盖显示的副本
            if (extraFloating.isNotEmpty() || extraTop.isNotEmpty() || extraBottom.isNotEmpty()) {
                val compiledKeys = HashSet<String>(compiled.floating.size + compiled.top.size + compiled.bottom.size)
                compiled.floating.mapTo(compiledKeys) { danmakuLayoutKey(it.presentation) }
                compiled.top.mapTo(compiledKeys) { danmakuLayoutKey(it.presentation) }
                compiled.bottom.mapTo(compiledKeys) { danmakuLayoutKey(it.presentation) }
                extraFloating.removeAll { danmakuLayoutKey(it.presentation) in compiledKeys }
                extraTop.removeAll { danmakuLayoutKey(it.presentation) in compiledKeys }
                extraBottom.removeAll { danmakuLayoutKey(it.presentation) in compiledKeys }
            }
            layoutState.value = compiled
            rebuildWindow(compiled, currentVideoTimeMillis)
            danmakuUpdateSubscription++
        }
    }

    /**
     * 帧循环: 平滑帧时钟, 推进视频时间, 维护可见窗口.
     */
    internal suspend fun interpolateFrameLoop() {
        uiContext.await()
        val frameTimeSmoother = FrameTimeSmoother()
        var currentFrameTimeNanos = withFrameNanos { it }

        while (true) {
            withFrameNanos { nanos ->
                val rawDelta = nanos - currentFrameTimeNanos
                val delta = frameTimeSmoother.smooth(rawDelta)

                elapsedFrameTimeNanos += delta
                currentFrameTimeDeltaNanos = delta
                currentFrameRawDeltaNanos = rawDelta
                currentFrameTimeNanos = nanos

                val position = playbackClock.positionAt(elapsedFrameTimeNanos)
                if (position != null) {
                    currentVideoTimeMillis = position
                    updateVisibleWindow(position)
                    pruneExtras(position)
                }
                danmakuUpdateSubscription++
            }
        }
    }

    // region 可见窗口维护

    private fun updateVisibleWindow(videoTimeMillis: Long) {
        val layout = layoutState.value
        if (layout !== windowLayout) {
            rebuildWindow(layout, videoTimeMillis)
            return
        }
        if (layout == null) return
        if (videoTimeMillis < windowTimeMillis ||
            videoTimeMillis - windowTimeMillis > WINDOW_REBUILD_THRESHOLD_MILLIS
        ) {
            rebuildWindow(layout, videoTimeMillis)
            return
        }
        advanceWindow(layout, videoTimeMillis)
    }

    private fun advanceWindow(layout: CompiledDanmakuLayout, t: Long) {
        val floating = layout.floating
        while (floatingCursor < floating.size && floating[floatingCursor].enterTimeMillis <= t) {
            val placed = floating[floatingCursor]
            if (placed.exitTimeMillis > t) visibleFloating.add(materialize(placed))
            floatingCursor++
        }
        val top = layout.top
        while (topCursor < top.size && top[topCursor].enterTimeMillis <= t) {
            val placed = top[topCursor]
            if (placed.endTimeMillis > t) visibleTop.add(materialize(placed))
            topCursor++
        }
        val bottom = layout.bottom
        while (bottomCursor < bottom.size && bottom[bottomCursor].enterTimeMillis <= t) {
            val placed = bottom[bottomCursor]
            if (placed.endTimeMillis > t) visibleBottom.add(materialize(placed))
            bottomCursor++
        }

        // 自己发送的弹幕可能被指定了未来的进入时刻
        addEnteringExtras(windowTimeMillis, t)

        visibleFloating.removeAll { it.placed.exitTimeMillis <= t }
        visibleTop.removeAll { it.placed.endTimeMillis <= t }
        visibleBottom.removeAll { it.placed.endTimeMillis <= t }

        windowTimeMillis = t
    }

    private fun rebuildWindow(layout: CompiledDanmakuLayout?, t: Long) {
        visibleFloating.clear()
        visibleTop.clear()
        visibleBottom.clear()
        windowLayout = layout
        windowTimeMillis = t
        floatingCursor = 0
        topCursor = 0
        bottomCursor = 0
        if (layout == null || t < 0) return

        floatingCursor = upperBoundFloating(layout.floating, t)
        run {
            val minEnter = t - layout.maxFloatingLifetimeMillis
            var i = floatingCursor - 1
            while (i >= 0) {
                val placed = layout.floating[i]
                if (placed.enterTimeMillis < minEnter) break
                if (placed.exitTimeMillis > t) visibleFloating.add(materialize(placed))
                i--
            }
        }

        topCursor = upperBoundFixed(layout.top, t)
        bottomCursor = upperBoundFixed(layout.bottom, t)
        val minFixedEnter = t - layout.maxFixedLifetimeMillis
        run {
            var i = topCursor - 1
            while (i >= 0) {
                val placed = layout.top[i]
                if (placed.enterTimeMillis < minFixedEnter) break
                if (placed.endTimeMillis > t) visibleTop.add(materialize(placed))
                i--
            }
        }
        run {
            var i = bottomCursor - 1
            while (i >= 0) {
                val placed = layout.bottom[i]
                if (placed.enterTimeMillis < minFixedEnter) break
                if (placed.endTimeMillis > t) visibleBottom.add(materialize(placed))
                i--
            }
        }

        for (placed in extraFloating) {
            if (placed.enterTimeMillis <= t && placed.exitTimeMillis > t) visibleFloating.add(materialize(placed))
        }
        for (placed in extraTop) {
            if (placed.enterTimeMillis <= t && placed.endTimeMillis > t) visibleTop.add(materialize(placed))
        }
        for (placed in extraBottom) {
            if (placed.enterTimeMillis <= t && placed.endTimeMillis > t) visibleBottom.add(materialize(placed))
        }
    }

    private fun addEnteringExtras(previousTimeMillis: Long, t: Long) {
        if (extraFloating.isNotEmpty()) {
            for (placed in extraFloating) {
                if (placed.enterTimeMillis > previousTimeMillis && placed.enterTimeMillis <= t &&
                    placed.exitTimeMillis > t
                ) {
                    visibleFloating.add(materialize(placed))
                }
            }
        }
        if (extraTop.isNotEmpty()) {
            for (placed in extraTop) {
                if (placed.enterTimeMillis > previousTimeMillis && placed.enterTimeMillis <= t &&
                    placed.endTimeMillis > t
                ) {
                    visibleTop.add(materialize(placed))
                }
            }
        }
        if (extraBottom.isNotEmpty()) {
            for (placed in extraBottom) {
                if (placed.enterTimeMillis > previousTimeMillis && placed.enterTimeMillis <= t &&
                    placed.endTimeMillis > t
                ) {
                    visibleBottom.add(materialize(placed))
                }
            }
        }
    }

    private fun pruneExtras(t: Long) {
        if (extraFloating.isNotEmpty()) extraFloating.removeAll { it.exitTimeMillis <= t }
        if (extraTop.isNotEmpty()) extraTop.removeAll { it.endTimeMillis <= t }
        if (extraBottom.isNotEmpty()) extraBottom.removeAll { it.endTimeMillis <= t }
    }

    private fun materialize(placed: PlacedFloatingDanmaku): VisibleFloatingDanmaku =
        VisibleFloatingDanmaku(placed, createStyled(placed.presentation))

    private fun materialize(placed: PlacedFixedDanmaku): VisibleFixedDanmaku =
        VisibleFixedDanmaku(placed, createStyled(placed.presentation))

    private fun createStyled(presentation: DanmakuPresentation): StyledDanmaku = StyledDanmaku(
        presentation = presentation,
        measurer = uiContext.textMeasurer,
        baseStyle = uiContext.baseStyle,
        style = danmakuConfig.style,
        enableColor = danmakuConfig.enableColor,
        isDebug = danmakuConfig.isDebug,
    )

    private fun restyleVisible() {
        for (i in visibleFloating.indices) {
            visibleFloating[i].styled = createStyled(visibleFloating[i].placed.presentation)
        }
        for (i in visibleTop.indices) {
            visibleTop[i].styled = createStyled(visibleTop[i].placed.presentation)
        }
        for (i in visibleBottom.indices) {
            visibleBottom[i].styled = createStyled(visibleBottom[i].placed.presentation)
        }
    }

    // endregion

    /**
     * 发送用户自己的弹幕, 保证显示 (必要时延迟到轨道可容纳的时刻).
     *
     * 这类弹幕不在编译布局中, 以覆盖方式显示; 当弹幕列表后续包含它时 (重新编译时按 key 去重),
     * 覆盖副本会被自动移除.
     */
    suspend fun send(danmaku: DanmakuPresentation) {
        uiContext.await()
        withContext(Dispatchers.Main.immediate) {
            val layout = layoutState.value ?: return@withContext
            val t = currentVideoTimeMillis
            if (t < 0) return@withContext

            when (danmaku.danmaku.location) {
                DanmakuLocation.NORMAL -> sendFloating(layout, danmaku, t)
                DanmakuLocation.TOP -> sendFixed(layout, danmaku, t, visibleTop, extraTop, layout.params.topTrackCount)
                DanmakuLocation.BOTTOM ->
                    sendFixed(layout, danmaku, t, visibleBottom, extraBottom, layout.params.bottomTrackCount)
            }
            danmakuUpdateSubscription++
        }
    }

    private fun sendFloating(layout: CompiledDanmakuLayout, danmaku: DanmakuPresentation, t: Long) {
        val params = layout.params
        if (params.floatingTrackCount == 0) return
        val styled = createStyled(danmaku)
        val width = styled.danmakuWidth
        val speed = floatingDanmakuSpeed(params, width, fluctuation01 = 0.5f)

        // 每条轨道上最后一条可见弹幕
        val lastPerTrack = arrayOfNulls<PlacedFloatingDanmaku>(params.floatingTrackCount)
        for (visible in visibleFloating) {
            val trackIndex = visible.placed.trackIndex
            if (trackIndex >= params.floatingTrackCount) continue
            val last = lastPerTrack[trackIndex]
            if (last == null || visible.placed.enterTimeMillis > last.enterTimeMillis) {
                lastPerTrack[trackIndex] = visible.placed
            }
        }

        // 首选: 立即出现且不与轨道上最后一条冲突
        var trackIndex = -1
        var enter = t
        for (i in 0 until params.floatingTrackCount) {
            val last = lastPerTrack[i]
            val candidate = PlacedFloatingDanmaku(
                danmaku, i, t, width, speed,
                floatingDanmakuExitTime(params, t, width, speed),
            )
            if (last == null || floatingDanmakuFits(last, candidate, params.trackWidthPx, params.safeSeparationPx)) {
                trackIndex = i
                break
            }
        }
        if (trackIndex == -1) {
            // 所有轨道都放不下: 选择能最早容纳的轨道, 延迟出现, 保证一定显示
            var bestEnter = Long.MAX_VALUE
            for (i in 0 until params.floatingTrackCount) {
                val last = lastPerTrack[i] ?: continue
                // 前一条完全进入轨道的时刻 (碰撞规则 1)
                val fullyEntered = last.enterTimeMillis +
                        ((last.widthPx + params.safeSeparationPx) / last.speedPxPerVideoSecond * 1000.0).toLong() + 1
                // 不追尾 (碰撞规则 2)
                val noClash = last.exitTimeMillis -
                        (params.trackWidthPx / speed * 1000.0).toLong()
                val earliest = maxOf(t, fullyEntered, noClash)
                if (earliest < bestEnter) {
                    bestEnter = earliest
                    trackIndex = i
                }
            }
            enter = bestEnter
        }

        val placed = PlacedFloatingDanmaku(
            danmaku, trackIndex, enter, width, speed,
            floatingDanmakuExitTime(params, enter, width, speed),
        )
        extraFloating.add(placed)
        if (placed.enterTimeMillis <= t && placed.exitTimeMillis > t) {
            visibleFloating.add(VisibleFloatingDanmaku(placed, styled))
        }
    }

    private fun sendFixed(
        layout: CompiledDanmakuLayout,
        danmaku: DanmakuPresentation,
        t: Long,
        visible: ArrayList<VisibleFixedDanmaku>,
        extras: ArrayList<PlacedFixedDanmaku>,
        trackCount: Int,
    ) {
        if (trackCount == 0) return
        val duration = layout.params.fixedDurationVideoMillis

        var trackIndex = (0 until trackCount).firstOrNull { index ->
            visible.none { it.placed.trackIndex == index }
        } ?: -1
        if (trackIndex == -1) {
            // 没有空轨道: 覆盖最先消失的那条, 保证自己的弹幕一定显示
            val victim = visible.minByOrNull { it.placed.endTimeMillis } ?: return
            trackIndex = victim.placed.trackIndex
            visible.remove(victim)
        }

        val placed = PlacedFixedDanmaku(danmaku, trackIndex, t, t + duration)
        extras.add(placed)
        visible.add(VisibleFixedDanmaku(placed, createStyled(danmaku)))
    }

    /**
     * Internal UI context class storing text measurement and density information.
     */
    private class UIContext {
        lateinit var baseStyle: TextStyle
        lateinit var textMeasurer: TextMeasurer
        lateinit var density: Density

        private val setDeferred: CompletableDeferred<Unit> = CompletableDeferred()

        fun set(baseStyle: TextStyle, textMeasurer: TextMeasurer, density: Density) {
            this.baseStyle = baseStyle
            this.textMeasurer = textMeasurer
            this.density = density
            setDeferred.complete(Unit)
        }

        suspend fun await() = setDeferred.await()
    }

    private companion object {
        /**
         * 单帧内视频时间前进超过此值时重建窗口而不是逐条推进.
         */
        const val WINDOW_REBUILD_THRESHOLD_MILLIS = 3000L
    }
}

private fun upperBoundFloating(list: List<PlacedFloatingDanmaku>, t: Long): Int {
    var lo = 0
    var hi = list.size
    while (lo < hi) {
        val mid = (lo + hi) ushr 1
        if (list[mid].enterTimeMillis <= t) lo = mid + 1 else hi = mid
    }
    return lo
}

private fun upperBoundFixed(list: List<PlacedFixedDanmaku>, t: Long): Int {
    var lo = 0
    var hi = list.size
    while (lo < hi) {
        val mid = (lo + hi) ushr 1
        if (list[mid].enterTimeMillis <= t) lo = mid + 1 else hi = mid
    }
    return lo
}

/**
 * 一条已进入可见窗口的浮动弹幕.
 */
@Stable
internal class VisibleFloatingDanmaku(
    val placed: PlacedFloatingDanmaku,
    var styled: StyledDanmaku,
)

/**
 * 一条已进入可见窗口的固定弹幕.
 */
@Stable
internal class VisibleFixedDanmaku(
    val placed: PlacedFixedDanmaku,
    var styled: StyledDanmaku,
)
