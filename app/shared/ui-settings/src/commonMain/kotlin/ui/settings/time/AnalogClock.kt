/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.settings.time

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import kotlinx.datetime.LocalTime

/**
 * 时/分针的补间时长.
 */
private const val HAND_ANIMATION_MILLIS = 700

/**
 * 秒针补间时长. 比一秒略短, 让相邻两次指针跳动之间留出静止的一小段.
 */
private const val SECOND_HAND_ANIMATION_MILLIS = 900

/**
 * 极简模拟时钟: 圆形表盘、12/3/6/9 四个刻度点, 时/分/秒三针.
 *
 * 三针都由补间动画驱动: 秒针匀速转动且只顺时针前进; 时/分针沿最短弧转向目标角度.
 * 因此切换时区导致的时间跳变表现为指针旋转过去, 而不是瞬间换位.
 *
 * [time] 每秒变化一次.
 */
@Composable
fun AnalogClock(
    time: LocalTime,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val hourAngle = rememberAnimatedHandAngle(
        target = hourHandTargetAngle(time.hour, time.minute),
        animationSpec = tween(durationMillis = HAND_ANIMATION_MILLIS, easing = FastOutSlowInEasing),
        advance = ::nearestHandAngle,
    )
    val minuteAngle = rememberAnimatedHandAngle(
        target = minuteHandTargetAngle(time.minute, time.second),
        animationSpec = tween(durationMillis = HAND_ANIMATION_MILLIS, easing = FastOutSlowInEasing),
        advance = ::nearestHandAngle,
    )
    val secondAngle = rememberAnimatedHandAngle(
        target = secondHandTargetAngle(time.second, time.nanosecond),
        animationSpec = tween(durationMillis = SECOND_HAND_ANIMATION_MILLIS, easing = LinearEasing),
        advance = ::forwardHandAngle,
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
        rotate(secondAngle, pivot = dialCenter) {
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

/**
 * 把指针补间到 [target] 换算出的角度. [advance] 决定从当前角度到目标的走法:
 * 秒针只前进, 时/分针走最短弧.
 *
 * 目标每次变化都从当前动画值重新起步, 所以连续变化不会积累误差, 也不会回跳.
 */
@Composable
private fun rememberAnimatedHandAngle(
    target: Float,
    animationSpec: AnimationSpec<Float>,
    advance: (current: Float, target: Float) -> Float,
): Float {
    val angle = remember { Animatable(target) }
    LaunchedEffect(target) {
        angle.animateTo(advance(angle.value, target), animationSpec)
    }
    return angle.value
}
