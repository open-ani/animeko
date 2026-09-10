/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.danmaku.ui

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/** User-facing parameter ranges, shared by touch, desktop and TV controls. */
object DanmakuConfigRanges {
    val FontSizeScale = 0.5f..3f
    val Opacity = 0f..1f
    val StrokeWidthScale = 0f..2f
    val FontWeight = 100..900
    val SpeedScale = 0.2f..3f
    val DisplayArea = 0f..1f
    val DensityLevel = 0f..10f

    fun densitySeparation(desktop: Boolean): ClosedRange<Dp> = 36.dp..(if (desktop) 720.dp else 240.dp)

    fun densityLevel(separation: Dp, range: ClosedRange<Dp>): Float =
        ((1 - (separation - range.start) / (range.endInclusive - range.start + 1.dp)) / 0.1f)
            .roundToInt().toFloat().coerceIn(DensityLevel)

    fun separationForDensity(level: Float, range: ClosedRange<Dp>): Dp =
        range.start + (range.endInclusive - range.start + 1.dp) * (1 - level.coerceIn(DensityLevel) * 0.1f)
}
