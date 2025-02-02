/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.foundation.animation

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.runtime.Stable

// https://m3.material.io/styles/motion/easing-and-duration/tokens-specs#433b1153-2ea3-4fe2-9748-803a47bc97ee

// md.sys.motion.easing.standard
@Stable
val StandardEasing: Easing = CubicBezierEasing(0.2f, 0.0f, 0f, 1.0f)

// md.sys.motion.easing.standard.decelerate
@Stable
val StandardDecelerateEasing: Easing = CubicBezierEasing(0.0f, 0.0f, 0.0f, 1f)

// md.sys.motion.easing.standard.accelerate
@Stable
val StandardAccelerateEasing: Easing = CubicBezierEasing(0.3f, 0.0f, 1f, 1f)

// md.sys.motion.easing.emphasized.decelerate
@Stable
val EmphasizedDecelerateEasing: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f)

// md.sys.motion.easing.emphasized.accelerate
@Stable
val EmphasizedAccelerateEasing: Easing = CubicBezierEasing(0.3f, 0.0f, 0.8f, 0.15f)


/**
 * Emphasized easing from the Material3 spec using the two cubic segments
 * from pathInterpolator(M 0,0
 *   C 0.05, 0, 0.133333, 0.06, 0.166666, 0.4
 *   C 0.208333, 0.82, 0.25, 1, 1, 1).
 *
 * This path is defined as two continuous Bézier segments:
 *
 *  Segment 1: (x in [0..0.166666], y in [0..0.4])
 *    Control points:
 *      P0 = (0, 0)
 *      P1 = (0.05, 0)
 *      P2 = (0.133333, 0.06)
 *      P3 = (0.166666, 0.4)
 *
 *  Segment 2: (x in [0.166666..1], y in [0.4..1])
 *    Control points:
 *      P0 = (0.166666, 0.4)
 *      P1 = (0.208333, 0.82)
 *      P2 = (0.25, 1)
 *      P3 = (1, 1)
 *
 * Each segment is normalized to a local domain [0..1] and a local range [0..1],
 * then we scale/shift the results back into the global domain/range. This ensures
 * continuity at x=0.166666, y=0.4.
 */
// md.sys.motion.easing.emphasized
val EmphasizedEasing: Easing = Easing { fraction ->
    // Quick bounds check (optional, but nice to handle out-of-range gracefully)
    when {
        fraction <= 0f -> 0f
        fraction >= 1f -> 1f

        fraction < 0.166666f -> {
            // ----- Segment 1 -----
            // Normalize fraction into [0..1]
            val localFraction = fraction / 0.166666f

            // The first segment's control points were scaled so it maps to (0,0)..(1,1).
            // We’ll just multiply the resulting y by 0.4 to get the final value in [0..0.4].
            val yLocal = segment1Easing.transform(localFraction)
            yLocal * 0.4f
        }

        else -> {
            // ----- Segment 2 -----
            // Normalize fraction into [0..1]
            val localFraction = (fraction - 0.166666f) / (1f - 0.166666f) // ~0.833334f

            // The second segment's control points were scaled so it maps to (0,0)..(1,1).
            // We then shift+scale the resulting y from [0..1] into [0.4..1].
            val yLocal = segment2Easing.transform(localFraction)
            0.4f + (0.6f * yLocal)
        }
    }
}

/**
 * First segment is from (0,0) → (0.166666,0.4) with control points (0.05,0) and (0.133333,0.06).
 * After normalizing:
 *   - X is scaled by 1 / 0.166666 = ~6.
 *   - Y is scaled by 1 / 0.4 = 2.5.
 *
 * So control points become:
 *   (0.05 / 0.166666, 0 / 0.4)   = (0.3, 0.0)
 *   (0.133333 / 0.166666, 0.06 / 0.4) = (0.8, 0.15)
 */
private val segment1Easing = CubicBezierEasing(
    a = 0.3f,
    b = 0f,
    c = 0.8f,
    d = 0.15f,
)

/**
 * Second segment is from (0.166666,0.4) → (1,1) with control points (0.208333,0.82) and (0.25,1).
 * After shifting + normalizing to domain [0..1] and range [0..1]:
 *   - Δx = 1 - 0.166666 = 0.833334
 *   - Δy = 1 - 0.4 = 0.6
 *
 * Thus control points become:
 *   ( (0.208333 - 0.166666)/0.833334, (0.82 - 0.4)/0.6 ) = (0.05, 0.7)
 *   ( (0.25 - 0.166666)/0.833334, (1   - 0.4)/0.6 )      = (0.1, 1.0)
 */
private val segment2Easing = CubicBezierEasing(
    a = 0.05f,
    b = 0.7f,
    c = 0.1f,
    d = 1f,
)