/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.videoplayer.ui

import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import me.him188.ani.app.ui.foundation.SLIDER_VALUE_STEP
import me.him188.ani.app.ui.foundation.quantizeSliderValue
import org.openani.mediamp.InternalForInheritanceMediampApi
import org.openani.mediamp.features.PlaybackSpeed

private const val KEYBOARD_SPEED_STEP: Float = SLIDER_VALUE_STEP

/** 将键盘输入转换为提交给调用方的最终倍速。 */
fun nextPlaybackSpeed(
    currentSpeed: Float,
    range: ClosedFloatingPointRange<Float>,
    direction: Int,
): Float = quantizeSliderValue(currentSpeed + direction * KEYBOARD_SPEED_STEP, range)

/**
 * 倍速控制 (SpeedSwitcher, 键盘快捷键, 长按快进) 的 state object.
 *
 * 本类**不拥有倍速**, 也不直接写播放器——它只是把 UI 事件转发给倍速的 owner
 * (domain 层的 `PlaybackSpeedController`), 并把 owner 的基础倍速映射成 Compose state.
 * `video-player` 模块不依赖 `app-data`, 因此这里只能拿到 flow 和 lambda,
 * 从类型上就不可能绕过 owner 去写播放器.
 *
 * @param baseSpeed owner 持有的基础倍速. 注意长按快进期间它**不会**变成快进倍速,
 *   所以倍速 UI 不会在长按时跳动.
 * @param effectiveSpeed owner 上实际生效的倍速 (长按快进期间即快进倍速).
 *   按真实播放速率计算的显示 (例如剩余时间) 读它.
 * @param rangeProvider 用户配置的倍速范围, 每次读取 [speedRange] 时都会重新求值.
 * @param onSetSpeed 设置基础倍速. `persist` 表示是否写回配置, 拖动过程中为 `false`.
 * @param onBeginTemporarySpeed 开始临时倍速 (长按快进).
 * @param onEndTemporarySpeed 结束临时倍速, 回到基础倍速.
 * @param scope coroutine scope for the base speed collector, usually `rememberCoroutineScope`.
 */
@Stable
class PlaybackSpeedControllerState(
    baseSpeed: StateFlow<Float> = MutableStateFlow(1f),
    effectiveSpeed: StateFlow<Float> = baseSpeed,
    rangeProvider: () -> ClosedFloatingPointRange<Float> = { DEFAULT_SPEED_RANGE },
    private val onSetSpeed: (speed: Float, persist: Boolean) -> Unit = { _, _ -> },
    private val onBeginTemporarySpeed: (speed: Float) -> Unit = {},
    private val onEndTemporarySpeed: () -> Unit = {},
    scope: CoroutineScope,
) {
    val speedRange: ClosedFloatingPointRange<Float> by derivedStateOf(rangeProvider)

    /**
     * 倍速控件显示的基础倍速, 长按快进期间不变.
     *
     * 它跟随 [baseSpeed], 但在 [previewSpeed] / [commitSpeed] 时会先乐观更新一次:
     * 写入要绕经 owner 再流回来, 隔着两跳协程调度, 不先更新的话 Slider 会在拖动中回弹.
     * 这只是显示值, source of truth 始终是 owner.
     */
    var currentSpeed: Float by mutableStateOf(baseSpeed.value)
        private set

    /**
     * 播放器上实际生效的倍速, 长按快进期间即快进倍速. 供剩余时间这类按真实播放速率计算的显示使用.
     */
    var currentEffectiveSpeed: Float by mutableStateOf(effectiveSpeed.value)
        private set

    init {
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            baseSpeed.collect { value -> currentSpeed = value }
        }
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            effectiveSpeed.collect { value -> currentEffectiveSpeed = value }
        }
    }

    /**
     * 拖动 Slider 期间实时预览倍速, 不写回配置.
     */
    fun previewSpeed(value: Float) {
        currentSpeed = value
        onSetSpeed(value, false)
    }

    /**
     * 提交最终倍速: 应用并写回配置.
     */
    fun commitSpeed(value: Float) {
        currentSpeed = value
        onSetSpeed(value, true)
    }

    /**
     * 开始长按快进的临时倍速. 不影响基础倍速, 因此不会被持久化.
     */
    fun beginTemporarySpeed(value: Float) {
        onBeginTemporarySpeed(value)
    }

    /**
     * 结束长按快进, 回到当前的基础倍速.
     */
    fun endTemporarySpeed() {
        onEndTemporarySpeed()
    }

    companion object {
        val DEFAULT_SPEED_RANGE: ClosedFloatingPointRange<Float> = 0.5f..2.5f
    }
}

@OptIn(InternalForInheritanceMediampApi::class)
object NoOpPlaybackSpeedController : PlaybackSpeed {
    override val value: Float = 1f
    override val valueFlow: Flow<Float> = flowOf(1f)

    override fun set(speed: Float) {

    }
}
