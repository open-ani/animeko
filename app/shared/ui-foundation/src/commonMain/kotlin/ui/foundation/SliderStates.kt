/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.foundation

import androidx.compose.material3.RangeSliderState
import androidx.compose.material3.SliderState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/**
 * 创建并记住一个 [SliderState], 它的值在每次重组时与 [value] 同步.
 *
 * Material 3 中可以自定义 thumb 和 track 的 `Slider` 只接受 [SliderState].
 * 滑块的值由外部状态持有时 (`value` 加 `onValueChange`), 用这个函数把外部的值接到 [SliderState] 上:
 * `onValueChange` 照常更新外部状态, 不需要写 [SliderState.value].
 *
 * 做法与 Material 3 接受 `value` 的 `Slider` 内部相同. [steps] 或 [valueRange] 变化时会创建新的 [SliderState].
 */
@Composable
fun rememberUpdatedSliderState(
    value: Float,
    steps: Int = 0,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
): SliderState {
    val state = remember(steps, valueRange) { SliderState(value, steps, valueRange) }
    state.value = value
    return state
}

/**
 * [rememberUpdatedSliderState] 的 `RangeSlider` 版本: 创建并记住一个 [RangeSliderState],
 * 它的起止值在每次重组时与 [value] 同步.
 */
@Composable
fun rememberUpdatedRangeSliderState(
    value: ClosedFloatingPointRange<Float>,
    steps: Int = 0,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
): RangeSliderState {
    val state = remember(steps, valueRange) {
        RangeSliderState(value.start, value.endInclusive, steps, valueRange)
    }
    state.startValue = value.start
    state.endValue = value.endInclusive
    return state
}
