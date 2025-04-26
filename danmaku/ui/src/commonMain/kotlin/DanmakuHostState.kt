/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.danmaku.ui

import androidx.compose.runtime.LongState
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
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.him188.ani.danmaku.api.DanmakuInfo
import me.him188.ani.danmaku.api.DanmakuLocation
import me.him188.ani.utils.logging.logger
import me.him188.ani.utils.logging.warn
import kotlin.math.absoluteValue
import kotlin.math.floor

/**
 * [DanmakuHostState] is the core state holder for managing and rendering danmakus (bullet-screen comments)
 * within a Compose UI. It encapsulates the configuration, display properties, and lifecycle management
 * needed to render danmakus on the screen with different styles (floating, top, bottom).
 *
 * This state class maintains:
 * - Screen dimension updates for danmakus.
 * - A [UIContext] containing measurement utilities and density for text measurement.
 * - Track-based rendering for floating (NORMAL), top (TOP), and bottom (BOTTOM) danmakus.
 * - Observers that react to changes in [DanmakuConfig] (font size, style, speed, etc.).
 * - Methods to send or repopulate danmakus according to time offsets or track positions.
 *
 * Typical usage flow:
 * 1. Call [setUIContext] to initialize text measurement, styling, and density.
 * 2. Observe changes via [observeConfig] in a coroutine scope (usually during composition).
 * 3. Drive the rendering loop with [interpolateFrameLoop], which automatically updates danmaku positions.
 * 4. Use [send] or [trySend] to add new danmakus.
 * 5. Optionally, use [repopulate] to clear and re-initialize the screen with a pre-sorted list of danmakus.
 *
 * @param danmakuConfigState A [State] of [DanmakuConfig], containing all user-configurable aspects of danmakus.
 * @param danmakuTrackProperties Static properties for the danmaku tracks, such as speed multipliers and durations.
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
     * The height of this DanmakuHost. Measured in pixels. Updated when the Composable hosting it changes size.
     */
    private val hostHeightState = mutableIntStateOf(0)
    internal var hostHeight by hostHeightState

    /**
     * The height of each track in this DanmakuHost. Updated whenever the font size, display area,
     * or host height changes.
     */
    private val trackHeightState = mutableIntStateOf(0)
    internal var trackHeight by trackHeightState
        private set

    /**
     * Multiplier for floating danmaku speed. Currently not configurable outside of initialization.
     */
    private val floatingSpeedMultiplierState = mutableFloatStateOf(danmakuTrackProperties.speedMultiplier)

    /**
     * Duration (in milliseconds) to present fixed (TOP/BOTTOM) danmakus on the screen.
     * Currently not configurable outside of initialization.
     */
    private val fixedDanmakuPresentDuration = mutableLongStateOf(danmakuTrackProperties.fixedDanmakuPresentDuration)

    /**
     * The alpha value of danmakus on the canvas, derived from [DanmakuConfig.style.alpha].
     */
    internal val canvasAlpha by derivedStateOf { danmakuConfig.style.alpha }

    /**
     * Whether the danmaku system is paused, halting all position updates.
     */
    internal var paused by mutableStateOf(false)

    /**
     * Whether debugging visuals are enabled (e.g., drawing bounding boxes).
     */
    internal val isDebug by derivedStateOf { danmakuConfig.isDebug }

    /**
     * The total elapsed frame time in nanoseconds. This value accumulates over time
     * unless [setPaused] is called to pause.
     */
    private val elapsedFrameTimeNanoState =
        mutableLongStateOf(danmakuTrackProperties.initialFrameTimeNanos) // workaround for emulating frame time.

    /**
     * 已经过的帧时间, 在 [setPaused] 设置暂停时此帧时间也会暂停
     */
    internal var elapsedFrameTimeNanos by elapsedFrameTimeNanoState

    /**
     * A timestamp-like value used to manually prompt updates (e.g., after config changes).
     */
    internal var danmakuUpdateSubscription by mutableLongStateOf(0L)
        private set

    /**
     * Tracks for NORMAL (floating) danmakus.
     */
    internal val floatingTrack = mutableListOf<FloatingDanmakuTrack<StyledDanmaku>>()

    /**
     * Tracks for TOP (fixed) danmakus.
     */
    internal val topTrack = mutableListOf<FixedDanmakuTrack<StyledDanmaku>>()

    /**
     * Tracks for BOTTOM (fixed) danmakus.
     */
    internal val bottomTrack = mutableListOf<FixedDanmakuTrack<StyledDanmaku>>()

    /**
     * All currently displayed floating danmakus. This list is updated as danmakus are placed or removed.
     */
    internal val presentFloatingDanmaku: MutableList<FloatingDanmaku<StyledDanmaku>> = mutableListOf()

    /**
     * All currently displayed fixed danmakus. This list is updated as danmakus are placed or removed.
     */
    internal val presentFixedDanmaku: MutableList<FixedDanmaku<StyledDanmaku>> = mutableListOf()

    private val danmakuRepopulator = DanmakuRepopulator(
        currentFrameTimeNanosState = elapsedFrameTimeNanoState,
        onSend = { danmaku, placeFrameTimeNanos -> trySend(danmaku, placeFrameTimeNanos) },
    )

    /**
     * Sets the [UIContext] required for text layout, styling, and density.
     * Must be called before any layout or rendering logic.
     *
     * @param baseStyle A [TextStyle] used as the base for all danmakus.
     * @param textMeasurer A [TextMeasurer] used to measure danmaku text.
     * @param density A [Density] providing pixel-density-related conversions.
     */
    fun setUIContext(
        baseStyle: TextStyle,
        textMeasurer: TextMeasurer,
        density: Density
    ) {
        uiContext.set(baseStyle, textMeasurer, density)
    }

    /**
     * Observes relevant [DanmakuConfig] changes (e.g., font size, display area, speed) and
     * responds by updating track counts, track static properties, or repopulating existing
     * danmakus. This method typically runs in a coroutine scope, often from a LaunchedEffect block.
     *
     * @param measurer A [TextMeasurer] to measure any newly required text layout adjustments.
     */
    internal suspend fun observeConfig(measurer: TextMeasurer) {
        uiContext.await()
        coroutineScope {
            /**
             * 以下配置变更将导致 DanmakuTrack 数量和属性的变化
             * - [hostHeight] 屏幕高度
             * - [DanmakuStyle.fontSize] in [DanmakuConfig.style] 弹幕字体大小
             * - [DanmakuConfig.displayArea] 弹幕显示区域
             * - [DanmakuConfig.enableTop], [DanmakuConfig.enableBottom] 和 [DanmakuConfig.enableFloating]
             */
            launch {
                var lastFontSize = danmakuConfig.style.fontSize
                // var lastSafeSeparation = danmakuConfig.safeSeparation
                // var lastIsDebug = danmakuConfig.isDebug

                combine(
                    snapshotFlow { hostHeight },
                    snapshotFlow { danmakuConfig }.distinctUntilChanged { old, new ->
                        old.style.fontSize == new.style.fontSize
                                && old.displayArea == new.displayArea
                                && old.enableTop == new.enableTop
                                && old.enableFloating == new.enableFloating
                                && old.enableBottom == new.enableBottom
                    },
                ) { newHeight, newConfig ->
                    val dummyTextLayout = dummyDanmaku(
                        measurer = measurer,
                        baseStyle = uiContext.baseStyle,
                        style = newConfig.style,
                        dummyText = "哈哈哈哈",
                    )
                    val verticalPadding = with(uiContext.density) {
                        (danmakuTrackProperties.verticalPadding * 2).dp.toPx()
                    }

                    val trackHeight = (dummyTextLayout.danmakuHeight + verticalPadding).toInt()
                    val trackCount = floor(newHeight / trackHeight * newConfig.displayArea)
                        .coerceAtLeast(1f)
                        .toInt()

                    Triple(trackCount, trackHeight to dummyTextLayout.danmakuWidth, newConfig)
                }
                    .distinctUntilChanged()
                    .collect { (trackCount, trackSize, newConfig) ->
                        val (trackHeight, baseTrackSpeedWidth) = trackSize
                        if (trackHeight != this@DanmakuHostState.trackHeight) {
                            this@DanmakuHostState.trackHeight = trackHeight
                        }

                        updateTrackCount(trackCount, newConfig, baseTrackSpeedWidth)
                        // 如果弹幕字体大小变化了也会导致弹幕重置和浮动弹幕的一个静态属性更新
                        if (lastFontSize != newConfig.style.fontSize) {
                            updateTrackStaticProperties(newBaseSpeedTextWidth = baseTrackSpeedWidth)
                            repopulatePresentDanmaku(elapsedFrameTimeNanos)
                            lastFontSize = newConfig.style.fontSize
                        }
                        danmakuUpdateSubscription++ // update subscription manually if paused
                    }
            }
            /**
             * 以下变更将导致弹幕重新放置
             * - [DanmakuConfig.safeSeparation] 弹幕最小间隔
             * - [DanmakuConfig.isDebug] 是否开启调试模式, 将改变弹幕内容
             * - 弹幕字体大小已在上面的 flow 中监听, 并且也会导致弹幕重新放置.
             * - 弹幕的基础速度宽度已在上面的 flow 中监听, 并且也会导致弹幕重新放置.
             */
            launch {
                snapshotFlow { danmakuConfig }.distinctUntilChanged { old, new ->
                    old.safeSeparation == new.safeSeparation && old.isDebug == new.isDebug
                }.collect { newConfig ->
                    updateTrackStaticProperties(newConfigSafeSeparation = newConfig.safeSeparation)
                    repopulatePresentDanmaku(elapsedFrameTimeNanos)
                    danmakuUpdateSubscription++ // update subscription manually if paused
                }
            }
            /**
             * 以下变会导致更新 DanmakuTrack 的静态属性发生变化
             * - [DanmakuConfig.speed] 浮动弹幕的速度
             * - 弹幕最小间隔已在上面的 flow 中监听, 并且也会更新 DanmakuTrack 属性.
             * - 弹幕的基础速度宽度已在上面的 flow 中监听, 并且也会更新 DanmakuTrack 属性.
             */
            launch {
                snapshotFlow { danmakuConfig }.distinctUntilChanged { old, new ->
                    old.speed == new.speed
                }.collect { newConfig ->
                    updateTrackStaticProperties(newConfigSpeed = newConfig.speed)
                    danmakuUpdateSubscription++ // update subscription manually if paused
                }
            }
            /**
             * 以下变化将仅导致弹幕重新变更样式, 而不用重新计算静态位置
             *
             * - [DanmakuStyle.alpha] in [DanmakuConfig.style]
             * - [DanmakuStyle.fontWeight] in [DanmakuConfig.style]
             * - [DanmakuStyle.shadow] in [DanmakuConfig.style]
             * - [DanmakuStyle.strokeColor] in [DanmakuConfig.style]
             * - [DanmakuStyle.strokeWidth] in [DanmakuConfig.style]
             */
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
                    .collect { newConfig ->
                        fun StyledDanmaku.transform(): StyledDanmaku {
                            return copy(
                                baseStyle = uiContext.baseStyle,
                                style = newConfig.style,
                                enableColor = newConfig.enableColor,
                            )
                        }

                        for (danmaku in presentFloatingDanmaku) danmaku.danmaku = danmaku.danmaku.transform()
                        for (danmaku in presentFixedDanmaku) danmaku.danmaku = danmaku.danmaku.transform()
                        danmakuUpdateSubscription++ // update subscription manually
                    }
            }
            /**
             * 如果在暂停的时候屏幕高度和宽度有变化, 需要更新一次固定弹幕的位置
             */
            launch {
                snapshotFlow { hostHeight }.collect {
                    if (!paused) return@collect
                    for (danmaku in presentFixedDanmaku) {
                        if (danmaku.fromBottom) danmaku.y = danmaku.calculatePosY()
                    }
                    danmakuUpdateSubscription++ // update subscription manually
                }
            }
        }
    }

    /**
     * Updates the number of tracks for floating, top, and bottom danmakus. Called whenever
     * relevant configuration or dimension changes occur (e.g., screen height changes, enabling/disabling of track types).
     *
     * @param count The new track count.
     * @param config The current [DanmakuConfig].
     * @param baseTrackSpeedWidth A reference width used to calculate base speed for floating danmakus.
     */
    private suspend fun updateTrackCount(count: Int, config: DanmakuConfig, baseTrackSpeedWidth: Int) {
        uiContext.await()
        // updateTrack 时 speed 和 safeSeparation 也可能变化, 也需要更新
        val newFloatingTrackSpeed = with(uiContext.density) { danmakuConfig.speed.dp.toPx() }
        val newFloatingTrackSafeSeparation = with(uiContext.density) { danmakuConfig.safeSeparation.toPx() }

        floatingTrack.setTrackCountImpl(if (config.enableFloating) count else 0) { index ->
            FloatingDanmakuTrack(
                trackIndex = index,
                frameTimeNanosState = elapsedFrameTimeNanoState,
                trackHeight = trackHeightState,
                trackWidth = hostWidthState,
                baseSpeedPxPerSecond = newFloatingTrackSpeed,
                safeSeparation = newFloatingTrackSafeSeparation,
                baseSpeedTextWidth = baseTrackSpeedWidth,
                speedMultiplier = floatingSpeedMultiplierState,
                onRemoveDanmaku = { removed -> presentFloatingDanmaku.removeFirst { it.danmaku == removed.danmaku } },
            )
        }

        topTrack.setTrackCountImpl(if (config.enableTop) count else 0) { index ->
            FixedDanmakuTrack(
                trackIndex = index,
                frameTimeNanosState = elapsedFrameTimeNanoState,
                trackHeight = trackHeightState,
                hostHeight = hostHeightState,
                fromBottom = false,
                durationMillis = fixedDanmakuPresentDuration,
                onTickReplacePending = { new -> presentFixedDanmaku.add(new) },
                onRemoveDanmaku = { removed -> presentFixedDanmaku.removeFirst { it.danmaku == removed.danmaku } },
            )
        }
        bottomTrack.setTrackCountImpl(if (config.enableBottom) count else 0) { index ->
            FixedDanmakuTrack(
                trackIndex = index,
                frameTimeNanosState = elapsedFrameTimeNanoState,
                trackHeight = trackHeightState,
                hostHeight = hostHeightState,
                fromBottom = true,
                durationMillis = fixedDanmakuPresentDuration,
                onTickReplacePending = { new -> presentFixedDanmaku.add(new) },
                onRemoveDanmaku = { removed -> presentFixedDanmaku.removeFirst { it.danmaku == removed.danmaku } },
            )
        }
    }

    /**
     * 更新一些 DanmakuTrack 的一些静态属性, 这些属性不是 State, 需要手动更新.
     * - [FloatingDanmakuTrack.baseSpeedPxPerSecond]
     * - [FloatingDanmakuTrack.safeSeparation]
     * - [FloatingDanmakuTrack.baseSpeedTextWidth]
     *
     * @param newConfigSpeed The new speed (dp/s) to apply to floating tracks.
     * @param newConfigSafeSeparation The new minimum separation (Dp) to keep between danmakus.
     * @param newBaseSpeedTextWidth The new reference width for base speed calculations.
     */
    private suspend fun updateTrackStaticProperties(
        newConfigSpeed: Float? = null,
        newConfigSafeSeparation: Dp? = null,
        newBaseSpeedTextWidth: Int? = null
    ) {
        uiContext.await()
        val newFloatingTrackSpeed = with(uiContext.density) { newConfigSpeed?.dp?.toPx() }
        val newFloatingTrackSafeSeparation = with(uiContext.density) { newConfigSafeSeparation?.toPx() }

        floatingTrack.forEach {
            if (newFloatingTrackSafeSeparation != null) it.safeSeparation = newFloatingTrackSafeSeparation
            if (newFloatingTrackSpeed != null) it.baseSpeedPxPerSecond = newFloatingTrackSpeed
            if (newBaseSpeedTextWidth != null) it.baseSpeedTextWidth = newBaseSpeedTextWidth
        }
    }

    /**
     * Recomputes the position of all currently displayed danmakus (both floating and fixed) without
     * fully clearing them. 此方法的行为与 [repopulate] 相同, 但是具有更高的执行效率.
     *
     * @param currentElapsedFrameTimeNanos The current frame time in nanos, used to recalculate positions.
     */
    private suspend fun repopulatePresentDanmaku(currentElapsedFrameTimeNanos: Long) {
        uiContext.await()
        val presentFloatingDanmakuCopied = presentFloatingDanmaku.toList()
        val presentFixedDanmakuCopied = presentFixedDanmaku.toList()

        val floatingTrackSpeed = with(uiContext.density) { danmakuConfig.speed.dp.toPx() }

        clearPresentDanmaku()

        // Restore fixed danmakus in correct track positions.
        presentFixedDanmakuCopied.forEach {
            trySend(it.danmaku.presentation, it.placeFrameTimeNanos)
        }
        // Restore floating danmakus, adjusting for how far they've moved so far.
        presentFloatingDanmakuCopied.forEach {
            val placeFrameTimeNanos = currentElapsedFrameTimeNanos -
                    ((it.distanceX / (floatingTrackSpeed * it.speedMultiplier)) * 1_000_000_000f).toLong()
            if (placeFrameTimeNanos >= 0) {
                trySend(it.danmaku.presentation, placeFrameTimeNanos)
            }
        }

        // 暂停时重新放置后需要计算一次位置, 否则重新填充弹幕后,
        // 暂停的这一帧中所有填充的弹幕的静态属性都没有被计算而导致屏幕上没有弹幕
        if (paused) calculateDanmakuInFrame(0L, 0f)
    }

    /**
     * Drives the danmaku animation loop by incrementally updating the frame time and
     * recalculating the positions of active danmakus. Should be launched in a coroutine that
     * remains active (e.g., from a LaunchedEffect).
     *
     * This method will:
     * - Update [elapsedFrameTimeNanos] every frame.
     * - Call [calculateDanmakuInFrame] to move floating danmakus and finalize fixed danmaku positions.
     *
     * If [paused] is true, danmakus remain in place, but the loop keeps running.
     */
    internal suspend fun interpolateFrameLoop() {
        uiContext.await()
        coroutineScope {
            var currentFloatingTrackSpeed = with(uiContext.density) { danmakuConfig.speed.dp.toPx() }

            // Observe dynamic changes in speed.
            launch {
                snapshotFlow { danmakuConfig.speed }.collect {
                    currentFloatingTrackSpeed = with(uiContext.density) { it.dp.toPx() }
                }
            }

            // Frame loop: increment time, compute positions.
            launch {
                var currentFrameTimeNanos = withFrameNanos {
                    // 使用了这一帧来获取时间, 需要补偿平均帧时间
                    // elapsedFrameTimeNanos += avgFrameTimeNanos.avg()
                    it
                }

                while (true) {
                    withFrameNanos { nanos ->
                        val delta = nanos - currentFrameTimeNanos

                        elapsedFrameTimeNanos += delta
                        // avgFrameTimeNanos += delta
                        currentFrameTimeNanos = nanos

                        calculateDanmakuInFrame(delta, currentFloatingTrackSpeed)
                        danmakuUpdateSubscription++ // update subscription manually if paused
                    }
                }
            }
        }
    }

    /**
     * Calculates the position of all currently displayed danmakus.
     * - For floating danmakus, updates [FloatingDanmaku.distanceX] based on [appendedFrameTime] and the provided [floatingTrackSpeed].
     * - For fixed danmakus, ensures placement time and [FixedDanmaku.y] coordinate are set.
     *
     * @param appendedFrameTime The time in nanoseconds to move forward since the last frame.
     * @param floatingTrackSpeed The base speed (px/s) of floating danmakus.
     */
    private fun calculateDanmakuInFrame(
        appendedFrameTime: Long,
        floatingTrackSpeed: Float
    ) {
        for (danmaku in presentFloatingDanmaku) {
            // calculate y once
            if (danmaku.y.isNaN()) danmaku.y = danmaku.calculatePosY()
            // always calculate distance x
            danmaku.distanceX += appendedFrameTime / 1_000_000_000f * (floatingTrackSpeed * danmaku.speedMultiplier)
        }
        for (danmaku in presentFixedDanmaku) {
            if (danmaku.placeFrameTimeNanos == DanmakuTrack.NOT_PLACED) {
                danmaku.placeFrameTimeNanos = elapsedFrameTimeNanos
            }
            // calculate y once
            if (danmaku.y.isNaN()) danmaku.y = danmaku.calculatePosY()
        }
    }

    /**
     * Executes a "tick" to remove danmakus that have scrolled out of view (floating) or
     * exceeded their presentation time (fixed).
     */
    internal fun tick() {
        floatingTrack.forEach { it.tick() }
        topTrack.forEach { it.tick() }
        bottomTrack.forEach { it.tick() }
    }

    /**
     * Attempts to place a danmaku on the screen at the current frame time.
     *
     * If the tracks are all busy at the exact moment, this method may fail and return `false`.
     * Use [send] for a guaranteed placement (possibly with a slight delay).
     *
     * @param danmaku The [DanmakuPresentation] data describing the text and style.
     * @param placeFrameTimeNanos Optional. The frame time (in nanos) to place this danmaku.
     *   Defaults to [DanmakuTrack.NOT_PLACED], meaning it will be placed immediately.
     * @return True if placed successfully, otherwise false.
     *
     * @see send
     */
    // 若是浮动弹幕则加入到 [presentFloatingDanmaku], 固定弹幕加到 [presentFixedDanmaku].
    suspend fun trySend(
        danmaku: DanmakuPresentation,
        placeFrameTimeNanos: Long = DanmakuTrack.NOT_PLACED
    ): Boolean {
        uiContext.await()
        val styledDanmaku = StyledDanmaku(
            presentation = danmaku,
            measurer = uiContext.textMeasurer,
            baseStyle = uiContext.baseStyle,
            style = danmakuConfig.style,
            enableColor = danmakuConfig.enableColor,
            isDebug = danmakuConfig.isDebug,
        )

        return withContext(Dispatchers.Main.immediate) {
            when (danmaku.danmaku.location) {
                DanmakuLocation.NORMAL -> {
                    val floatingDanmaku = floatingTrack.firstNotNullOfOrNull {
                        it.tryPlace(styledDanmaku, placeFrameTimeNanos)
                    }
                    floatingDanmaku?.also(presentFloatingDanmaku::add) != null
                }

                DanmakuLocation.TOP -> {
                    val floatingDanmaku = topTrack.firstNotNullOfOrNull {
                        it.tryPlace(styledDanmaku, placeFrameTimeNanos)
                    }
                    floatingDanmaku?.also(presentFixedDanmaku::add) != null
                }

                DanmakuLocation.BOTTOM -> {
                    val floatingDanmaku = bottomTrack.firstNotNullOfOrNull {
                        it.tryPlace(styledDanmaku, placeFrameTimeNanos)
                    }
                    floatingDanmaku?.also(presentFixedDanmaku::add) != null
                }
            }
        }
    }

    /**
     * Guaranteed method to place a danmaku on the screen. If all tracks are busy at the moment,
     * [send] will schedule the danmaku to appear as soon as possible, potentially replacing a
     * pending danmaku if no free track is found.
     *
     * @param danmaku The [DanmakuPresentation] data to send.
     */
    suspend fun send(danmaku: DanmakuPresentation) {
        uiContext.await()

        withContext(Dispatchers.Main.immediate) {
            val currentElapsedFrameTimeNanos = elapsedFrameTimeNanos
            val styledDanmaku = StyledDanmaku(
                presentation = danmaku,
                measurer = uiContext.textMeasurer,
                baseStyle = uiContext.baseStyle,
                style = danmakuConfig.style,
                enableColor = danmakuConfig.enableColor,
                isDebug = danmakuConfig.isDebug,
            )

            if (danmaku.danmaku.location == DanmakuLocation.NORMAL) {
                // 没开启就没办法发送, 因为轨道数量为 0
                if (!danmakuConfig.enableFloating) return@withContext

                val safeSeparation = with(uiContext.density) { danmakuConfig.safeSeparation.toPx() }
                val floatingTrackSpeed = with(uiContext.density) { danmakuConfig.speed.dp.toPx() }

                var sendTrack: FloatingDanmakuTrack<StyledDanmaku>? = null
                var maxDanmakuRight = Float.NEGATIVE_INFINITY

                floatingTrack.forEach { track ->
                    val lastDanmaku = track.getLastDanmaku() ?: kotlin.run {
                        // 表示当前轨道没有弹幕, 可以直接发送到这条轨道
                        track.place(styledDanmaku).also(presentFloatingDanmaku::add)
                        return@withContext
                    }
                    val right = lastDanmaku.distanceX - (lastDanmaku.danmaku.danmakuWidth + safeSeparation)
                    if (right >= 0) {
                        // 最后一条弹幕完全可见, 表示当前轨道没有在屏幕右边的弹幕, 可以直接发送到这条轨道
                        track.place(styledDanmaku).also(presentFloatingDanmaku::add)
                        return@withContext
                    }
                    if (right > maxDanmakuRight) {
                        // 如果没办法放, 那就获取最先可以放的轨道
                        maxDanmakuRight = right
                        sendTrack = track
                    }
                }

                val track = ensureTrackNotNull(sendTrack, floatingTrack) ?: return@withContext
                val placeTimeNanos = (maxDanmakuRight.absoluteValue / floatingTrackSpeed * 1_000_000_000f).toLong()
                track.place(styledDanmaku, currentElapsedFrameTimeNanos + placeTimeNanos)
                    .also(presentFloatingDanmaku::add)
            } else {
                val tracks = if (danmaku.danmaku.location == DanmakuLocation.TOP) {
                    // 没开启就没办法发送, 因为轨道数量为 0
                    if (!danmakuConfig.enableTop) return@withContext
                    topTrack
                } else {
                    // 没开启就没办法发送, 因为轨道数量为 0
                    if (!danmakuConfig.enableBottom) return@withContext
                    bottomTrack
                }
                var sendTrack: FixedDanmakuTrack<StyledDanmaku>? = null
                var minDanmakuTime = Long.MAX_VALUE

                for (track in tracks) {
                    val lastDanmaku = track.currentDanmaku
                    if (lastDanmaku == null) {
                        // 表示当前轨道没有弹幕, 可以直接发送到这条轨道
                        track.place(styledDanmaku).also(presentFixedDanmaku::add)
                        return@withContext
                    }
                    if (lastDanmaku.placeFrameTimeNanos < minDanmakuTime) {
                        // 获取弹幕最先消失的轨道
                        minDanmakuTime = lastDanmaku.placeFrameTimeNanos
                        sendTrack = track
                    }
                }

                val track = ensureTrackNotNull(sendTrack, tracks) ?: return@withContext
                if (track.pendingDanmaku == null) {
                    // 如果这个轨道没有等待发送的弹幕, 那就把这条弹幕放进去等待发送
                    track.setPending(styledDanmaku)?.also(presentFixedDanmaku::add)
                    return@withContext
                }
                // 待发送的轨道有正在等待发送的弹幕了
                val trackWithoutPending = tracks.firstOrNull { it.pendingDanmaku == null }
                if (trackWithoutPending != null) {
                    // 找第一个没有待发送弹幕的轨道
                    track.setPending(styledDanmaku)?.also(presentFixedDanmaku::add)
                    return@withContext
                }
                // 所有的轨道都有弹幕和待发送弹幕, 那就随机找一个轨道覆盖掉待发送的, 保证我们的弹幕必须发送出去
                tracks.random().setPending(styledDanmaku)?.also(presentFixedDanmaku::add)
            }
        }
    }

    private fun <T : DanmakuTrack<*, *>> ensureTrackNotNull(sendTrack: T?, tracks: List<T>): T? {
        if (sendTrack == null) {
            val firstOrNull = tracks.firstOrNull()
            if (firstOrNull == null) {
                logger.warn { "Did not find track to send danmaku. floatingTrack was empty." }
            }
            return firstOrNull
        }

        return sendTrack
    }

    /**
     * 清空屏幕并填充[新弹幕列表][list]到屏幕.
     *
     * @see DanmakuRepopulator.repopulate
     */
    suspend fun repopulate(list: List<DanmakuPresentation>, currentPlayMillis: Long) {
        withContext(Dispatchers.Main.immediate) { clearPresentDanmaku() }

        if (list.isEmpty()) return
        uiContext.await()

        danmakuRepopulator.repopulate(list, currentPlayMillis)
    }

    /**
     * Clears all currently displayed danmakus from the screen, resetting the internal track structures.
     */
    private fun clearPresentDanmaku() {
        floatingTrack.forEach { it.clearAll() }
        topTrack.forEach { it.clearAll() }
        bottomTrack.forEach { it.clearAll() }

        // We expect presentFloatingDanmaku.size and presentFixedDanmaku.size to be zero, but we don't crash UI here if they are not the case.
        // It's possible some thread

//        check(presentFloatingDanmaku.size == 0) {
//            "presentFloatingDanmaku is not totally cleared after releasing track."
//        }
//        check(presentFixedDanmaku.size == 0) {
//            "presentFloatingDanmaku is not totally cleared after releasing track."
//        }
    }

    /**
     * Toggles the paused state of the danmaku system. While paused:
     * - [elapsedFrameTimeNanos] stops accumulating.
     * - Danmakus remain in their current positions (floating stops moving).
     * - Layout changes can still occur, but no movement or timing-based updates happen.
     *
     * @param pause True to pause, false to resume.
     */
    fun setPaused(pause: Boolean) {
        paused = pause
    }

    /**
     * Internal UI context class storing text measurement and density information.
     * Ensures these are set before any layout or population logic executes.
     */
    private class UIContext {
        lateinit var baseStyle: TextStyle
        lateinit var textMeasurer: TextMeasurer
        lateinit var density: Density

        private val setDeferred: CompletableDeferred<Unit> = CompletableDeferred()

        /**
         * Sets the fields of this [UIContext] and completes the internal deferred,
         * unblocking any pending UI measurement or rendering operations.
         */
        fun set(baseStyle: TextStyle, textMeasurer: TextMeasurer, density: Density) {
            this.baseStyle = baseStyle
            this.textMeasurer = textMeasurer
            this.density = density
            setDeferred.complete(Unit)
        }

        /**
         * Suspends until [set] is called, ensuring all measurement tools and densities
         * are initialized before proceeding.
         */
        suspend fun await() = setDeferred.await()
    }

    private companion object {
        val logger = logger<DanmakuHostState>()
    }
}

/**
 * A class that manages the repopulation of danmakus on the screen.
 *
 * @param onSend A callback to send a danmaku to the screen.
 */
internal class DanmakuRepopulator(
    private val currentFrameTimeNanosState: LongState,
    private val onSend: suspend (DanmakuPresentation, placeFrameTimeNanos: Long) -> Unit
) {
    /**
     * 清空屏幕并填充[新弹幕列表][list]到屏幕.
     *
     * 以 [currentPlayMillis] 为放置的基准, 换句话说:
     * 如果 [list] 中最后一条弹幕的[放置时间][DanmakuInfo.playTimeMillis]等于 [currentPlayMillis] 那它就会被放到屏幕最右侧, 然后立刻出现.
     *
     * @param currentPlayMillis 放置到最右侧的弹幕的基准时间戳.
     * 如果不许需要关联实际播放的时间, 可以传入 [list.last().danmaku.placeTimeMillis], 这将会把最后一条弹幕放到最右侧.
     * @param list 要填充到屏幕的弹幕, 必须按发送时间戳排序.
     */
    suspend fun repopulate(list: List<DanmakuPresentation>, currentPlayMillis: Long) {
        val currentElapsedFrameTimeNanos = currentFrameTimeNanosState.value // take snapshot
        val sortedList = list.sortedBy { it.danmaku.playTimeMillis }

        val isFloatingDanmaku = { danmaku: DanmakuPresentation ->
            danmaku.danmaku.location == DanmakuLocation.NORMAL
        }

        val floatingDanmaku = sortedList.filter(isFloatingDanmaku)
        if (floatingDanmaku.isNotEmpty()) {
            // 第一条和最后一条浮动弹幕发送时间戳
            val firstDanmakuTimeMillis = floatingDanmaku.first().danmaku.playTimeMillis
            val firstDanmakuPlaceFrameTimeNanos = currentElapsedFrameTimeNanos -
                    (currentPlayMillis - firstDanmakuTimeMillis) * 1_000_000L

            floatingDanmaku.forEach { danmaku ->
                val placeFrameTimeNanos = firstDanmakuPlaceFrameTimeNanos +
                        (danmaku.danmaku.playTimeMillis - firstDanmakuTimeMillis) * 1_000_000L
                if (placeFrameTimeNanos >= 0) onSend(danmaku, placeFrameTimeNanos)
            }
        }

        val fixedDanmaku = sortedList.filterNot(isFloatingDanmaku)
        if (fixedDanmaku.isNotEmpty()) {
            val lastDanmakuTimeMillis = fixedDanmaku.last().danmaku.playTimeMillis

            val lastDanmakuPlaceFrameTimeNanos = currentElapsedFrameTimeNanos -
                    (currentPlayMillis - lastDanmakuTimeMillis) * 1_000_000L

            // 浮动弹幕倒序 place 进 presentDanmaku 里
            fixedDanmaku.asReversed().forEach { danmaku ->
                val placeFrameTimeNanos = lastDanmakuPlaceFrameTimeNanos -
                        (lastDanmakuTimeMillis - danmaku.danmaku.playTimeMillis) * 1_000_000L
                if (placeFrameTimeNanos >= 0) onSend(danmaku, placeFrameTimeNanos)
            }
        }
    }
}

private fun <D : SizeSpecifiedDanmaku, DT, T : DanmakuTrack<D, DT>>
        MutableList<T>.setTrackCountImpl(count: Int, newInstance: (index: Int) -> T) {
    when {
        size == count -> return
        // 清除 track 的同时要把 track 里的 danmaku 也要清除
        count < size -> repeat(size - count) { removeLastOrNull()?.clearAll() }
        else -> addAll(List(count - size) { newInstance(size + it) })
    }
}

private inline fun <T> MutableList<T>.removeFirst(predicate: (T) -> Boolean): T? {
    val index = indexOfFirst(predicate)
    if (index == -1) return null
    return removeAt(index)
}