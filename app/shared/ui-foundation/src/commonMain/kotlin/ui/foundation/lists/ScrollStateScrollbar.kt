/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.foundation.lists

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * Cross-platform vertical scrollbar for [ScrollState].
 *
 * - Desktop: uses Compose Desktop built-in `VerticalScrollbar`, which supports dragging and track clicks.
 * - Mobile/Native: uses a lightweight scroll indicator.
 */
@Composable
expect fun ScrollStateVerticalScrollbar(
    state: ScrollState,
    modifier: Modifier = Modifier,
)

/**
 * Returns whether [ScrollState] has content outside its viewport.
 */
fun ScrollState.hasScrollableContent(): Boolean {
    return maxValue > 0 && maxValue != Int.MAX_VALUE
}

/**
 * A lightweight vertical scroll indicator for [ScrollState] (mobile-friendly).
 *
 * - No dragging / click-to-jump.
 * - Always visible when the content is scrollable.
 */
@Composable
fun ScrollStateVerticalScrollIndicator(
    state: ScrollState,
    modifier: Modifier = Modifier,
    thickness: Dp = 4.dp,
    padding: Dp = 4.dp,
    minThumbHeight: Dp = 24.dp,
) {
    val density = LocalDensity.current
    var containerHeightPx by remember { mutableFloatStateOf(0f) }

    val shouldRender by remember(state) {
        derivedStateOf { state.hasScrollableContent() }
    }
    if (!shouldRender) return

    val paddingPx = with(density) { padding.toPx() }
    val minThumbHeightPx = with(density) { minThumbHeight.toPx() }
    val trackHeightPx = (containerHeightPx - paddingPx * 2).coerceAtLeast(0f)

    val viewportHeightPx = state.viewportSize.toFloat()
    val totalContentHeightPx = viewportHeightPx + state.maxValue
    val thumbHeightPx = if (trackHeightPx > 0f && totalContentHeightPx > 0f) {
        val effectiveMinThumbHeightPx = minThumbHeightPx.coerceAtMost(trackHeightPx)
        (trackHeightPx * viewportHeightPx / totalContentHeightPx)
            .coerceIn(effectiveMinThumbHeightPx, trackHeightPx)
    } else {
        0f
    }
    val thumbTopPx = if (state.maxValue > 0) {
        (trackHeightPx - thumbHeightPx).coerceAtLeast(0f) *
            (state.value.toFloat() / state.maxValue).coerceIn(0f, 1f)
    } else {
        0f
    }
    val thumbHeightDp = with(density) { thumbHeightPx.toDp() }

    val trackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.08f)
    val thumbColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
    Box(
        modifier = modifier
            .width(thickness + padding * 2)
            .fillMaxHeight()
            .onSizeChanged { containerHeightPx = it.height.toFloat() },
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxHeight()
                .padding(vertical = padding)
                .width(thickness)
                .background(trackColor, CircleShape),
        )
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(vertical = padding)
                .offset { IntOffset(0, thumbTopPx.roundToInt()) }
                .width(thickness)
                .background(thumbColor, CircleShape)
                .height(thumbHeightDp),
        )
    }
}
