/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.foundation.lists

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/**
 * Cross-platform vertical scrollbar for [LazyListState].
 *
 * - Desktop: uses Compose Desktop built-in `VerticalScrollbar`.
 * - Mobile/Native: uses a lightweight draggable implementation.
 */
@Composable
expect fun LazyListVerticalScrollbar(
    state: LazyListState,
    modifier: Modifier = Modifier,
)

/**
 * A lightweight vertical scroll indicator for [LazyListState] (mobile-friendly).
 *
 * - No dragging / click-to-jump.
 * - Only visible while scrolling (with fade in/out).
 * - Designed for lists with roughly uniform item height.
 */
@Composable
fun LazyListVerticalScrollIndicator(
    state: LazyListState,
    modifier: Modifier = Modifier,
    thickness: Dp = 4.dp,
    padding: Dp = 4.dp,
    minThumbHeight: Dp = 24.dp,
    hideDelayMillis: Long = 700,
) {
    val density = LocalDensity.current

    // 记录容器的高度像素
    var containerHeightPx by remember { mutableFloatStateOf(0f) }

    // 判断是否应该渲染滚动条
    val shouldRender by remember {
        derivedStateOf { state.canScrollBackward || state.canScrollForward }
    }
    if (!shouldRender) return

    // 根据 LazyListState 计算滚动条相关指标（如滑块位置、大小等）
    val metrics by remember(state) {
        derivedStateOf { calculateScrollbarMetrics(state) }
    }
    val resolvedMetrics = metrics ?: return

    // 将 padding 和 minThumbHeight 转换为像素
    val paddingPx = with(density) { padding.toPx() }
    val minThumbHeightPx = with(density) { minThumbHeight.toPx() }

    // 计算可用轨道高度（总高度减去上下 padding）
    val trackHeightPx = (containerHeightPx - paddingPx * 2).coerceAtLeast(0f)

    // 计算滑块的实际高度和顶部偏移量
    val thumbHeightPx = resolvedMetrics.thumbHeightPx(
        trackHeightPx = trackHeightPx,
        minThumbHeightPx = minThumbHeightPx,
    )
    val thumbTopPx = resolvedMetrics.thumbTopPx(
        trackHeightPx = trackHeightPx,
        thumbHeightPx = thumbHeightPx,
    )

    // 定义轨道颜色和滑块颜色
    val trackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.08f)
    val thumbColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)

    // 将滑块高度从 px 转换回 dp
    val thumbHeightDp = with(density) { thumbHeightPx.toDp() }

    // 控制滚动条可见性
    var visible by remember { mutableStateOf(false) }

    // 监听滚动状态变化以控制滚动条的显示/隐藏逻辑
    val isScrollInProgress by remember { derivedStateOf { state.isScrollInProgress } }

    LaunchedEffect(isScrollInProgress) {
        if (isScrollInProgress) {
            visible = true
        } else {
            delay(hideDelayMillis)
            if (!state.isScrollInProgress) {
                visible = false
            }
        }
    }

    // 使用 AnimatedVisibility 实现淡入淡出动画效果
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
    ) {
        Box(
            modifier = modifier
                .width(thickness + padding * 2)
                .fillMaxHeight()
                .onSizeChanged { containerHeightPx = it.height.toFloat() },
        ) {
            // 绘制滚动条轨道背景
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxHeight()
                    .padding(vertical = padding)
                    .width(thickness)
                    .background(trackColor, CircleShape),
            )
            // 绘制可移动的滑块部分
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
}

private data class LazyListScrollbarMetrics(
    val totalItemsCount: Int,
    val averageItemSizePx: Float,
    val viewportHeightPx: Float,
    val scrollPx: Float,
    val maxScrollPx: Float,
) {
    /**
     * 计算滚动条滑块的高度
     * 根据视口比例计算滑块在轨道中的高度，并确保不低于最小高度限制
     */
    fun thumbHeightPx(trackHeightPx: Float, minThumbHeightPx: Float): Float {
        // 边界检查
        if (trackHeightPx <= 0f) return 0f
        if (viewportHeightPx <= 0f) return 0f

        val totalContentPx = viewportHeightPx + maxScrollPx
        if (totalContentPx <= 0f) return 0f

        // 根据视口占总内容的比例计算滑块原始高度
        val raw = trackHeightPx * (viewportHeightPx / totalContentPx)

        // 限制滑块高度在最小值和轨道高度之间
        return raw.coerceIn(minThumbHeightPx, trackHeightPx)
    }

    /**
     * 计算滚动条滑块的顶部位置
     * 根据当前滚动位置和最大滚动距离计算滑块在轨道中的垂直位置
     */
    fun thumbTopPx(trackHeightPx: Float, thumbHeightPx: Float): Float {
        // 计算滑块可用的移动空间
        val available = (trackHeightPx - thumbHeightPx).coerceAtLeast(0f)
        // 计算滚动位置的百分比
        val fraction = if (maxScrollPx > 0f) (scrollPx / maxScrollPx).coerceIn(0f, 1f) else 0f

        return available * fraction
    }
}

/**
 * 计算LazyList滚动条的度量信息
 */
private fun calculateScrollbarMetrics(state: LazyListState): LazyListScrollbarMetrics? {
    val layoutInfo = state.layoutInfo
    val visibleItems = layoutInfo.visibleItemsInfo
    val totalItemsCount = layoutInfo.totalItemsCount
    if (totalItemsCount <= 0 || visibleItems.isEmpty()) return null

    val viewportHeightPx = layoutInfo.viewportSize.height.toFloat()
    if (viewportHeightPx <= 0f) return null

    // 计算可见项的平均大小
    val averageItemSizePx = visibleItems.map { it.size }.average().toFloat()
    if (averageItemSizePx <= 0f) return null

    // 计算总内容高度和最大可滚动距离
    val totalContentPx = averageItemSizePx * totalItemsCount
    val maxScrollPx = (totalContentPx - viewportHeightPx).coerceAtLeast(0f)
    if (maxScrollPx <= 0f) return null

    // 计算当前滚动位置
    val scrollPx = (state.firstVisibleItemIndex * averageItemSizePx + state.firstVisibleItemScrollOffset)
        .coerceIn(0f, maxScrollPx)

    return LazyListScrollbarMetrics(
        totalItemsCount = totalItemsCount,
        averageItemSizePx = averageItemSizePx,
        viewportHeightPx = viewportHeightPx,
        scrollPx = scrollPx,
        maxScrollPx = maxScrollPx,
    )
}


