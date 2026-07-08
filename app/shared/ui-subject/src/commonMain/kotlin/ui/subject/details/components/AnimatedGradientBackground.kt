/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.subject.details.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.isActive
import kotlin.math.PI

@Composable
internal fun AnimatedGradientBackground(
    colors: ImmutableList<Color>,
    modifier: Modifier = Modifier,
    speed: Double = 0.25,
    blurRadius: Dp = 60.dp,
) {
    var time by remember { mutableFloatStateOf(0f) }
    // Inspection / preview environments don't tick frames; leave `time` at
    // zero so screenshot tests get a deterministic still frame.
    val animate = !LocalInspectionMode.current
    LaunchedEffect(speed, animate) {
        if (!animate) return@LaunchedEffect
        val startNanos = withFrameNanos { it }
        while (isActive) {
            val nowNanos = withFrameNanos { it }
            time = ((nowNanos - startNanos) / NANOS_PER_SECOND * speed).toFloat()
        }
    }

    Canvas(
        modifier = modifier
            .blur(blurRadius)
            .semantics { hideFromAccessibility() },
    ) {
        if (colors.isEmpty()) return@Canvas
        val count = colors.size
        val baseRadius = max(size.width, size.height) * BASE_RADIUS_FACTOR
        for (index in colors.indices) {
            val color = colors[index]
            val phase = index * (TAU / count)
            val a = 1.0 + index * ORBIT_A_STEP
            val b = ORBIT_B_OFFSET + index * ORBIT_B_STEP
            val cx = size.width * 0.5f +
                    (sin(time * a + phase).toFloat() * size.width * ORBIT_AMPLITUDE)
            val cy = size.height * 0.5f +
                    (cos(time * b + phase).toFloat() * size.height * ORBIT_AMPLITUDE)
            val r = baseRadius * (RADIUS_BASE + RADIUS_SWING * sin(time * RADIUS_PHASE + phase).toFloat())
            val safeRadius = r.coerceAtLeast(1f)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(color, color.copy(alpha = 0f)),
                    center = Offset(cx, cy),
                    radius = safeRadius,
                ),
                center = Offset(cx, cy),
                radius = safeRadius,
            )
        }
    }
}

private const val TAU: Double = 2.0 * PI
private const val ORBIT_A_STEP: Double = 0.35
private const val ORBIT_B_OFFSET: Double = 1.3
private const val ORBIT_B_STEP: Double = 0.27
private const val ORBIT_AMPLITUDE: Float = 0.35f
private const val RADIUS_PHASE: Float = 0.7f
private const val RADIUS_BASE: Float = 0.85f
private const val RADIUS_SWING: Float = 0.15f
private const val BASE_RADIUS_FACTOR: Float = 0.55f
private const val NANOS_PER_SECOND: Double = 1_000_000_000.0

// Lower fraction of the glow band that fades into the page background.
private const val FADE_FRACTION: Float = 0.5f