/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.foundation.layout

import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.Velocity
import me.him188.ani.app.ui.foundation.interaction.nestedScrollWorkaround
import kotlin.math.absoluteValue
import kotlin.math.roundToInt

/**
 * 提供 [ConnectedScrollState.nestedScrollConnection], 将其添加到 [Modifier.nestedScroll] 中,
 * 即可让 [Modifier.connectedScrollContainer] 优先处理滚动事件.
 *
 * Compose nested scroll 对鼠标有 bug, 要同时使用 [Modifier.nestedScrollWorkaround].
 */
@Composable
fun rememberConnectedScrollState(
    flingBehavior: FlingBehavior = ScrollableDefaults.flingBehavior(),
): ConnectedScrollState {
    return rememberSaveable(flingBehavior, saver = ConnectedScrollState.saver(flingBehavior)) {
        ConnectedScrollState(flingBehavior)
    }
}

@Stable
class ConnectedScrollState(
    val flingBehavior: FlingBehavior,
    initialContainerHeight: Int = 0,
    initialScrolledOffset: Float = 0f,
) {
    val scrollableState = ScrollableState { available ->
        val previous = scrolledOffset
        val new = (scrolledOffset + available).coerceIn(-containerHeight.toFloat(), 0f)
        scrolledOffset = new
        new - previous
//        if (available < 0) {
//            // 手指往上, 首先让 header 隐藏
//            //
//            //                   y
//            // |---------------| 0
//            // |    TopAppBar  |
//            // |  图片    标题  |  -containerHeight
//            // |               |
//            // |    收藏数据    |  scrolledOffset
//            // |     TAB       |
//            // |  LazyColumn   |
//            // |---------------|
//
//
//            return@ScrollableState scrollScope.scrollBy(available)
//        }
//        0f
    }

    /**
     * 最大能滑动的高度
     * 仅在第一个 measurement pass 后更新
     */
    var containerHeight by mutableIntStateOf(initialContainerHeight)
        internal set

    /**
     * 当前已经滚动了的距离
     */
    // 范围为 -scrollableHeight ~ 0
    var scrolledOffset by mutableFloatStateOf(initialScrolledOffset)
        internal set

    /**
     * 是否已经滚动到最顶部了 (不能再动了)
     */
    // is stuck
    val isScrolledTop by derivedStateOf {
        if (containerHeight == 0) { // not yet measured
            return@derivedStateOf false
        }
        scrolledOffset.toInt() == -containerHeight
    }

    val nestedScrollConnection = object : NestedScrollConnection {
        override fun onPreScroll(
            available: Offset,
            source: NestedScrollSource
        ): Offset {
            return if (available.y < 0) {
                Offset(0f, scrollableState.dispatchRawDelta(available.y))
            } else {
                Offset.Zero
            }
        }

        override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
            if (available.y > 0) { // 手指往下
                scrollableState.scroll {
                    with(flingBehavior) {
                        performFling(available.y) // 让 headers 也跟着往下
                    }
                }
            }
            return super.onPostFling(consumed, available)
        }

        /**
         * 注意, 因为 Compose 有 bug, [onPreScroll] 和 [onPostScroll] 实际上都不会在用鼠标滚轮滑动时调用
         */
        override fun onPostScroll(
            consumed: Offset,
            available: Offset,
            source: NestedScrollSource
        ): Offset {
            if (available.y > 0) {
                // 手指往下, 让 header 显示
                // scrollableOffset 是负的
                return Offset(0f, scrollableState.dispatchRawDelta(available.y))
            }
            return super.onPostScroll(consumed, available, source)
        }
    }

    companion object {
        fun saver(flingBehavior: FlingBehavior) = Saver<ConnectedScrollState, Long>(
            save = {
                it.containerHeight.toLong() or (it.scrolledOffset.toRawBits().toUInt().toLong() shl 32)
            },
            restore = {
                ConnectedScrollState(
                    flingBehavior,
                    initialContainerHeight = (it and 0xFFFFFFFF).toInt(),
                    initialScrolledOffset = Float.fromBits((it shr 32).toInt()),
                )
            },
        )
    }
}

/**
 * 当 [ConnectedScrollState.nestedScrollConnection] 滚动时, 调整此 composable 的位置.
 */
fun Modifier.connectedScrollContainer(state: ConnectedScrollState): Modifier {
    return layout { measurable, constraints ->
        val placeable = measurable.measure(constraints)
        layout(
            placeable.width,
            (placeable.height - state.scrolledOffset.roundToInt().absoluteValue).coerceAtLeast(0),
        ) {
            placeable.place(0, y = state.scrolledOffset.roundToInt())
        }
    }
}

/**
 * 将该 composable 的高度作为可滚动的高度.
 */
fun Modifier.connectedScrollTarget(state: ConnectedScrollState): Modifier {
    return onSizeChanged { state.containerHeight = it.height }
}


/**
 * 同时应用 [connectedScrollContainer] 和 [connectedScrollTarget]
 */
fun Modifier.connectedScroll(state: ConnectedScrollState): Modifier {
    return connectedScrollContainer(state).connectedScrollTarget(state)
}