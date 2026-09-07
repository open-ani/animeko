/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.leanback.ui.episode

import androidx.compose.ui.text.font.FontWeight
import me.him188.ani.danmaku.ui.DanmakuConfig
import me.him188.ani.danmaku.ui.DanmakuConfigRanges
import me.him188.ani.danmaku.ui.DanmakuStyle

internal fun DanmakuConfig.adjustForTv(property: TvDanmakuProperty, direction: Int): DanmakuConfig {
    val step = direction.coerceIn(-1, 1)
    return when (property) {
        TvDanmakuProperty.FontSize -> copy(
            style = style.copy(
                fontSize = DanmakuStyle.Default.fontSize *
                        (style.fontSize.value / DanmakuStyle.Default.fontSize.value + step * .05f)
                            .coerceIn(DanmakuConfigRanges.FontSizeScale),
            ),
        )

        TvDanmakuProperty.Opacity -> copy(
            style = style.copy(alpha = (style.alpha + step * .05f).coerceIn(DanmakuConfigRanges.Opacity)),
        )

        TvDanmakuProperty.Speed -> copy(
            speed = DanmakuConfig.Default.speed *
                    (speed / DanmakuConfig.Default.speed + step * .1f).coerceIn(DanmakuConfigRanges.SpeedScale),
        )

        TvDanmakuProperty.Density -> {
            val range = DanmakuConfigRanges.densitySeparation(desktop = false)
            copy(
                safeSeparation = DanmakuConfigRanges.separationForDensity(
                    DanmakuConfigRanges.densityLevel(safeSeparation, range) + step,
                    range,
                ),
            )
        }

        TvDanmakuProperty.Area -> copy(displayArea = (displayArea + step * .05f).coerceIn(DanmakuConfigRanges.DisplayArea))
        TvDanmakuProperty.Stroke -> copy(
            style = style.copy(
                strokeWidth = DanmakuStyle.Default.strokeWidth *
                        (style.strokeWidth / DanmakuStyle.Default.strokeWidth + step * .1f)
                            .coerceIn(DanmakuConfigRanges.StrokeWidthScale),
            ),
        )

        TvDanmakuProperty.Weight -> copy(
            style = style.copy(
                fontWeight = FontWeight((style.fontWeight.weight + step * 100).coerceIn(DanmakuConfigRanges.FontWeight)),
            ),
        )

        TvDanmakuProperty.Top -> copy(enableTop = !enableTop)
        TvDanmakuProperty.Bottom -> copy(enableBottom = !enableBottom)
        TvDanmakuProperty.Floating -> copy(enableFloating = !enableFloating)
        TvDanmakuProperty.Color -> copy(enableColor = !enableColor)
    }
}
