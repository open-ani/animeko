/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.settings.time

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import kotlinx.datetime.LocalTime

/**
 * 秒针补间时长. 比一秒略短, 让相邻两次指针跳动之间留出静止的一小段.
 */
private const val SECOND_HAND_ANIMATION_MILLIS = 900

private const val NANOS_PER_SECOND = 1_000_000_000f

private const val DEGREES_PER_SECOND = 6f

/**
 * 极简模拟时钟: 圆形表盘、12/3/6/9 四个刻度点, 时/分/秒三针.
 *
 * [time] 每秒变化一次, 秒针在其基础上用匀速补间转过这一秒的角度, 因此视觉上是连续走动的.
 */
@Composable
fun AnalogClock(
    time: LocalTime,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val secondHandTarget = time.second * DEGREES_PER_SECOND +
            time.nanosecond / NANOS_PER_SECOND * DEGREES_PER_SECOND
    val secondHandAngle by animateFloatAsState(
        targetValue = secondHandTarget,
        animationSpec = tween(durationMillis = SECOND_HAND_ANIMATION_MILLIS, easing = LinearEasing),
        label = "analogClockSecondHand",
    )

    Canvas(modifier.size(size)) {
        val radius = this.size.minDimension / 2f
        val dialCenter = this.center
        val rimWidth = radius * 0.04f

        drawCircle(color = colors.surface, radius = radius - rimWidth, center = dialCenter)
        drawCircle(
            color = colors.outline,
            radius = radius - rimWidth,
            center = dialCenter,
            style = Stroke(width = rimWidth),
        )

        val tickRadius = radius * 0.03f
        val tickDistance = radius * 0.76f
        repeat(4) { index ->
            rotate(index * 90f, pivot = dialCenter) {
                drawCircle(
                    color = colors.outline,
                    radius = tickRadius,
                    center = dialCenter - Offset(0f, tickDistance),
                )
            }
        }

        val hourAngle = (time.hour % 12) * 30f + time.minute * 0.5f
        val minuteAngle = time.minute * 6f + time.second * 0.1f

        rotate(hourAngle, pivot = dialCenter) {
            drawLine(
                color = colors.onSurface,
                start = dialCenter,
                end = dialCenter - Offset(0f, radius * 0.46f),
                strokeWidth = radius * 0.06f,
                cap = StrokeCap.Round,
            )
        }
        rotate(minuteAngle, pivot = dialCenter) {
            drawLine(
                color = colors.onSurface,
                start = dialCenter,
                end = dialCenter - Offset(0f, radius * 0.68f),
                strokeWidth = radius * 0.045f,
                cap = StrokeCap.Round,
            )
        }
        rotate(secondHandAngle, pivot = dialCenter) {
            drawLine(
                color = colors.primary,
                start = dialCenter + Offset(0f, radius * 0.12f),
                end = dialCenter - Offset(0f, radius * 0.78f),
                strokeWidth = radius * 0.02f,
                cap = StrokeCap.Round,
            )
        }
        drawCircle(color = colors.primary, radius = radius * 0.035f, center = dialCenter)
    }
}
