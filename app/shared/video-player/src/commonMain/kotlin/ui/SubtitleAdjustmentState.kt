/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.videoplayer.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.openani.mediamp.MediampPlayer
import org.openani.mediamp.features.SubtitleAdjustment
import org.openani.mediamp.features.subtitleAdjustment
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * Presents the player's [SubtitleAdjustment] feature as Compose state for the player settings UI.
 *
 * Setters quantize their argument to the granularity the UI offers, so that dragging a continuous
 * slider still lands on the values shown in the labels.
 */
@Stable
class SubtitleAdjustmentState(
    private val subtitleAdjustment: SubtitleAdjustment,
    private val scope: CoroutineScope,
) {
    val supportsDelay: Boolean get() = subtitleAdjustment.supportsDelay
    val supportsFontScale: Boolean get() = subtitleAdjustment.supportsFontScale
    val supportsVerticalPosition: Boolean get() = subtitleAdjustment.supportsVerticalPosition

    // Backing fields are private because a public `var delayMillis` would generate a
    // `setDelayMillis(Long)` that clashes on the JVM with the setter of the same name below.
    private var _delayMillis by mutableLongStateOf(subtitleAdjustment.delayMillis.value)
    private var _fontScale by mutableFloatStateOf(subtitleAdjustment.fontScale.value)
    private var _verticalPosition by mutableFloatStateOf(subtitleAdjustment.verticalPosition.value)

    val delayMillis: Long get() = _delayMillis
    val fontScale: Float get() = _fontScale
    val verticalPosition: Float get() = _verticalPosition

    // 位置的实际应用值经此平滑过渡到目标值, 避免字幕在画面上瞬移.
    // UI 状态 (_verticalPosition) 则立即更新, 保证滑条跟手.
    private val appliedPosition = Animatable(subtitleAdjustment.verticalPosition.value)

    init {
        scope.launch {
            subtitleAdjustment.delayMillis.collect { _delayMillis = it }
        }
        scope.launch {
            subtitleAdjustment.fontScale.collect { _fontScale = it }
        }
    }

    fun setDelayMillis(millis: Long) {
        val snapped = (millis.toDouble() / DelayStepMillis).roundToLong() * DelayStepMillis
        subtitleAdjustment.setDelayMillis(snapped.coerceIn(DelayRange.first, DelayRange.last))
    }

    fun increaseDelay() = setDelayMillis(delayMillis + DelayStepMillis)

    fun decreaseDelay() = setDelayMillis(delayMillis - DelayStepMillis)

    fun setFontScale(scale: Float) {
        subtitleAdjustment.setFontScale(
            ((scale / FontScaleStep).roundToInt() * FontScaleStep)
                .coerceIn(FontScaleRange.start, FontScaleRange.endInclusive),
        )
    }

    /**
     * 拖动滑条时调用: 立即应用, 字幕直接跟手. 拖动本身是连续的, 无需再插值.
     */
    fun setVerticalPosition(position: Float) {
        val quantized = (position * 100).roundToInt() / 100f
        _verticalPosition = quantized
        scope.launch {
            // snapTo 会取消可能仍在进行的重置动画, 避免与拖动互相争抢.
            appliedPosition.snapTo(quantized)
            subtitleAdjustment.setVerticalPosition(quantized)
        }
    }

    /**
     * 非交互式的跳变 (如重置) 用平滑动画过渡, 避免字幕在画面上瞬移.
     */
    fun animateVerticalPositionTo(position: Float) {
        val quantized = (position * 100).roundToInt() / 100f
        _verticalPosition = quantized
        scope.launch {
            appliedPosition.animateTo(quantized, PositionAnimationSpec) {
                subtitleAdjustment.setVerticalPosition(value)
            }
        }
    }

    fun reset() {
        if (supportsDelay) subtitleAdjustment.setDelayMillis(DefaultDelayMillis)
        if (supportsFontScale) subtitleAdjustment.setFontScale(DefaultFontScale)
        if (supportsVerticalPosition) animateVerticalPositionTo(DefaultVerticalPosition)
    }

    companion object {
        const val DefaultDelayMillis: Long = 0L
        const val DefaultFontScale: Float = 1f
        const val DefaultVerticalPosition: Float = 1f

        const val DelayStepMillis: Long = 100L
        val DelayRange: LongRange = -10_000L..10_000L

        const val FontScaleStep: Float = 0.1f
        val FontScaleRange: ClosedFloatingPointRange<Float> = 0.5f..3f

        private val PositionAnimationSpec = tween<Float>(durationMillis = 250, easing = FastOutSlowInEasing)
    }
}

/**
 * Creates a [SubtitleAdjustmentState] for [this] player, or `null` if the backend supports no
 * subtitle adjustment at all — in which case the UI shows no subtitle section.
 */
fun MediampPlayer.createSubtitleAdjustmentState(scope: CoroutineScope): SubtitleAdjustmentState? {
    val feature = subtitleAdjustment ?: return null
    if (!feature.supportsDelay && !feature.supportsFontScale && !feature.supportsVerticalPosition) {
        return null
    }
    return SubtitleAdjustmentState(feature, scope)
}
