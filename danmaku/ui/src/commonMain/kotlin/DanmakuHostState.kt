/*
 * Copyright (C) 2024 OpenAni and contributors.
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
import me.him188.ani.danmaku.api.Danmaku
import me.him188.ani.danmaku.api.DanmakuLocation
import me.him188.ani.danmaku.api.DanmakuPresentation
import kotlin.math.absoluteValue
import kotlin.math.floor

@Stable
class DanmakuHostState(
    danmakuConfigState: State<DanmakuConfig> = mutableStateOf(DanmakuConfig.Default), // state 
    private val danmakuTrackProperties: DanmakuTrackProperties = DanmakuTrackProperties.Default,
) {
    private val danmakuConfig by danmakuConfigState
    private val uiContext: UIContext = UIContext()
    

    /**
     * DanmakuHost 显示大小, 在显示时修改
     */
    private val hostWidthState = mutableIntStateOf(0)
    internal var hostWidth by hostWidthState
    private val hostHeightState = mutableIntStateOf(0)
    internal var hostHeight by hostHeightState

    private val trackHeightState = mutableIntStateOf(0)
    internal var trackHeight by trackHeightState
        private set

    // currently not configurable
    private val floatingSpeedMultiplierState = mutableFloatStateOf(danmakuTrackProperties.speedMultiplier)
    // currently not configurable
    private val fixedDanmakuPresentDuration = mutableLongStateOf(danmakuTrackProperties.fixedDanmakuPresentDuration)

    internal val canvasAlpha by derivedStateOf { danmakuConfig.style.alpha }
    internal var paused by mutableStateOf(false)
    internal val isDebug by derivedStateOf { danmakuConfig.isDebug }

    private val elapsedFrameTimeNanoState = mutableLongStateOf(0)

    /**
     * 已经过的帧时间, 在 [setPaused] 设置暂停时此帧时间也会暂停
     */
    internal var elapsedFrameTimeNanos by elapsedFrameTimeNanoState
    internal var danmakuUpdateSubscription by mutableLongStateOf(0L)
        private set

    // 弹幕轨道
    internal val floatingTrack = mutableListOf<FloatingDanmakuTrack<StyledDanmaku>>()
    internal val topTrack = mutableListOf<FixedDanmakuTrack<StyledDanmaku>>()
    internal val bottomTrack = mutableListOf<FixedDanmakuTrack<StyledDanmaku>>()

    /**
     * 所有在 [floatingTrack], [topTrack] 和 [bottomTrack] 弹幕.
     * 在这里保留一个引用, 方便在 [repopulatePresentDanmaku] 的时候重新计算所有弹幕位置.
     * 大部分弹幕是按时间排序的, 确保 [removeFirst] 操作能消耗较低的时间.
     */
    internal val presentFloatingDanmaku: MutableList<FloatingDanmaku<StyledDanmaku>> = mutableListOf()
    internal val presentFixedDanmaku: MutableList<FixedDanmaku<StyledDanmaku>> = mutableListOf()

    fun setUIContext(
        baseStyle: TextStyle,
        textMeasurer: TextMeasurer,
        density: Density
    ) {
        uiContext.set(baseStyle, textMeasurer, density)
    }

    /**
     * 监听 [DanmakuConfig] 和 [hostHeight] 配置变化
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
                        dummyText = "哈哈哈哈"
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
     * 更新弹幕轨道数量, 同时也会更新轨道属性
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
     * 更新一些 DanmakuTrack 的一些静态属性, 这些属性不是 State, 需要手动更新
     * - [FloatingDanmakuTrack.baseSpeedPxPerSecond]
     * - [FloatingDanmakuTrack.safeSeparation]
     * - [FloatingDanmakuTrack.baseSpeedTextWidth]
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
     * 重新放置屏幕上弹幕的位置.
     *
     * 此方法的行为与 [repopulate] 相同, 但是具有更高的执行效率.
     */
    private suspend fun repopulatePresentDanmaku(currentElapsedFrameTimeNanos: Long) {
        uiContext.await()
        val presentFloatingDanmakuCopied = presentFloatingDanmaku.toList()
        val presentFixedDanmakuCopied = presentFixedDanmaku.toList()

        val floatingTrackSpeed = with(uiContext.density) { danmakuConfig.speed.dp.toPx() }

        clearPresentDanmaku()

        presentFixedDanmakuCopied.forEach { trySend(it.danmaku.presentation, it.placeFrameTimeNanos) }
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

    internal suspend fun interpolateFrameLoop() {
        uiContext.await()
        coroutineScope {
            var currentFloatingTrackSpeed = with(uiContext.density) { danmakuConfig.speed.dp.toPx() }

            launch {
                snapshotFlow { danmakuConfig.speed }.collect {
                    currentFloatingTrackSpeed = with(uiContext.density) { danmakuConfig.speed.dp.toPx() }
                }
            }

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
     * 计算一次弹幕的位置，若弹幕的静态位置没有被计算过，则会计算一次
     * 
     * @param appendedFrameTime 需要为浮动弹幕附加的帧时间，在[帧 loop][interpolateFrameLoop] 中需要增加帧时间.
     * @param floatingTrackSpeed 浮动弹幕的基础速度.
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
     * 逻辑帧 tick, 主要用于移除超出屏幕外或超过时间的弹幕
     */
    internal fun tick() {
        floatingTrack.forEach { it.tick() }
        topTrack.forEach { it.tick() }
        bottomTrack.forEach { it.tick() }
    }

    /**
     * 尝试发送弹幕到屏幕, 如果当前时间点已没有更多轨道可以使用则会发送失败.
     *
     * 对于一定发送成功的版本, 请查看 [DanmakuHostState.send].
     *
     * @return 如果发送成功则返回 true
     * @see DanmakuHostState.send
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
     * 发送弹幕到屏幕, 此方法一定会保证弹幕发送出去
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

                val track = sendTrack
                checkNotNull(track) { "danmaku track must be found when sending danmaku." }
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

                val track = sendTrack
                checkNotNull(track) { "danmaku track must be found when sending danmaku." }
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

    /**
     * 清空屏幕并填充 [list] 到屏幕.
     *
     * **此方法不会重新排序 [list], 所以弹幕必须已经按发送时间排序**.
     *
     * **在通常情况下, 此方法假设 [list] 中弹幕是在 repopulate 时的[帧时间][elapsedFrameTimeNanos]之前发送的弹幕**.
     * 因此, [list] 最后一条浮动弹幕会被放到屏幕的最右侧.
     * 其他弹幕将以其[发送时间戳][Danmaku.playTimeMillis]为基准依次**向前**排列.
     *
     * 但是如果 [list] 中的第一条浮动弹幕是可以显示在最开始 trackDurationMillis 内的时间
     * (trackDurationMillis 表示一条浮动的那幕从屏幕最右侧滚动到最左侧的时间),
     * 那么 [list] 中的第一条弹幕会被放到屏幕对应位置来模拟最开始的弹幕滚动过程,
     * 其他弹幕将以其[发送时间戳][Danmaku.playTimeMillis]为基准依次**向后**排列.
     *
     * 例如 trackDurationMillis 为 `10000ms`, [list] 中的第一条弹幕的发送时间为 `5000ms`,
     * 那么第一条弹幕就会被放置在轨道的中间位置.
     *
     * 通过设置 [timeOffsetMillis] 来指定整体的弹幕放置时间偏移.
     * 若 `timeOffsetMillis < 0L` 则弹幕放置的位置会向左偏移 [timeOffsetMillis] 的[帧时间][elapsedFrameTimeNanos].
     *
     * @param timeOffsetMillis 弹幕放置偏移
     * @param list 要填充到屏幕的弹幕, 必须按发送时间戳排序.
     */
    suspend fun repopulate(list: List<DanmakuPresentation>, timeOffsetMillis: Long = 0L) {
        withContext(Dispatchers.Main.immediate) { clearPresentDanmaku() }
        if (list.isEmpty()) return
        uiContext.await()

        val currentElapsedFrameTimeNanos = elapsedFrameTimeNanos // take snapshot

        val isFloatingDanmaku = { danmaku: DanmakuPresentation ->
            danmaku.danmaku.location == DanmakuLocation.NORMAL
        }

        val floatingDanmaku = list.filter(isFloatingDanmaku)
        if (floatingDanmaku.isNotEmpty()) {
            // 第一条和最后一条浮动弹幕发送时间戳
            val firstDanmakuTimeMillis = list.first(isFloatingDanmaku).danmaku.playTimeMillis
            val danmakuDurationMillis = list.last(isFloatingDanmaku).danmaku.playTimeMillis - firstDanmakuTimeMillis
            // 弹幕从左滑倒右边需要的时间(毫秒)
            val trackDurationMillis =
                hostWidth / with(uiContext.density) { danmakuConfig.speed.dp.toPx().toLong() } * 1_000

            val firstDanmakuPlaceTimeNanos = if (firstDanmakuTimeMillis <= trackDurationMillis) {
                // repopulate 了最开始的弹幕, 要向后排列.
                // 首条弹幕出现的时间在屏幕对应位置
                currentElapsedFrameTimeNanos - firstDanmakuTimeMillis * 1_000_000L
            } else {
                // 最后一条弹幕在屏幕最右侧, 所以首条弹幕出现的位置在前 danmakuDurationMillis 的帧时间.
                // 如果超过了 elapsedFrameTimeNanos - trackDurationMillis, 那在 trySend 的时候也不会被放置.
                currentElapsedFrameTimeNanos - danmakuDurationMillis * 1_000_000L
            }

            floatingDanmaku.forEach { danmaku ->
                val playFrameTimeNanos = firstDanmakuPlaceTimeNanos +
                        (danmaku.danmaku.playTimeMillis - firstDanmakuTimeMillis - timeOffsetMillis) * 1_000_000L
                if (playFrameTimeNanos >= 0) trySend(danmaku, playFrameTimeNanos)
            }
        }

        val fixedDanmaku = list.filterNot(isFloatingDanmaku)
        if (fixedDanmaku.isNotEmpty()) {
            val lastDanmakuTimeMillis = fixedDanmaku.last().danmaku.playTimeMillis
            // 浮动弹幕倒序 place 进 presentDanmaku 里
            fixedDanmaku.asReversed().forEach { danmaku ->
                val playFrameTimeNanos = currentElapsedFrameTimeNanos -
                        (lastDanmakuTimeMillis - danmaku.danmaku.playTimeMillis - timeOffsetMillis) * 1_000_000L
                if (playFrameTimeNanos >= 0) trySend(danmaku, playFrameTimeNanos)
            }
        }
    }

    private fun clearPresentDanmaku() {
        floatingTrack.forEach { it.clearAll() }
        topTrack.forEach { it.clearAll() }
        bottomTrack.forEach { it.clearAll() }

        check(presentFloatingDanmaku.size == 0) {
            "presentFloatingDanmaku is not totally cleared after releasing track."
        }
        check(presentFixedDanmaku.size == 0) {
            "presentFloatingDanmaku is not totally cleared after releasing track."
        }
    }

    fun setPaused(pause: Boolean) {
        paused = pause
    }

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