/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.foundation.layout

import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.foundation.animation.StandardDecelerateEasing
import me.him188.ani.app.ui.foundation.effects.onPointerEventMultiplatform
import kotlin.math.roundToInt

@Composable
fun rememberNestedScrollableColumnState(
    flingBehavior: FlingBehavior = ScrollableDefaults.flingBehavior(),
): NestedScrollableColumnState {
    return rememberSaveable(flingBehavior, saver = NestedScrollableColumnState.saver(flingBehavior)) {
        NestedScrollableColumnState(flingBehavior)
    }
}

/**
 * [NestedScrollableColumn] 的状态: header 滚出布局的进度.
 */
@Stable
class NestedScrollableColumnState(
    internal val flingBehavior: FlingBehavior,
    initialScrolledOffset: Float = 0f,
) {
    /**
     * header 的总高度 (px). 仅在第一次测量后更新.
     */
    var headerHeight by mutableIntStateOf(0)
        internal set

    /**
     * header 已滚出布局的距离 (px), 范围 `0f..headerHeight`.
     */
    var scrolledOffset by mutableFloatStateOf(initialScrolledOffset)
        internal set

    /**
     * header 是否已完全滚出布局, 即 content 已占满整个布局.
     */
    val isHeaderScrolledOut by derivedStateOf {
        headerHeight > 0 && scrolledOffset.roundToInt() >= headerHeight
    }

    /**
     * 布局是否处于最顶部, 即 header 完全可见 (未滚出任何距离).
     *
     * 可用于决定是否启用 pull-to-refresh 等只应在顶部生效的手势.
     */
    val isHeaderFullyVisible by derivedStateOf {
        scrolledOffset.roundToInt() <= 0
    }

    /**
     * header 滚出的进度, `0f` = 完全可见, `1f` = 完全滚出.
     */
    val collapseProgress by derivedStateOf {
        if (headerHeight == 0) 0f else (scrolledOffset / headerHeight).coerceIn(0f, 1f)
    }

    /**
     * 布局自身的滚动状态. delta 为正 = 手指向下滑动 (header 滚回).
     */
    val scrollableState = ScrollableState { delta ->
        val previous = scrolledOffset
        val new = (previous - delta).coerceIn(0f, headerHeight.toFloat())
        scrolledOffset = new
        previous - new // 与 delta 同号
    }

    /**
     * 协调 content 内部 scrollable 与 header 的滚动:
     * - 手指向上: 先滚出 header, 之后才轮到内部 scrollable;
     * - 手指向下: 内部 scrollable 滚到顶后, 剩余的滚动让 header 滚回.
     */
    val nestedScrollConnection = object : NestedScrollConnection {
        override fun onPreScroll(
            available: Offset,
            source: NestedScrollSource,
        ): Offset {
            return if (available.y < 0) {
                Offset(0f, scrollableState.dispatchRawDelta(available.y))
            } else {
                Offset.Zero
            }
        }

        /**
         * 注意, 因为 Compose 有 bug, [onPreScroll] 和 [onPostScroll] 实际上都不会在用鼠标滚轮滑动时调用,
         * 需搭配 [NestedScrollableScope.nestedScrollWorkaround].
         */
        override fun onPostScroll(
            consumed: Offset,
            available: Offset,
            source: NestedScrollSource,
        ): Offset {
            if (available.y > 0) {
                return Offset(0f, scrollableState.dispatchRawDelta(available.y))
            }
            return Offset.Zero
        }

        override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
            if (available.y > 0) { // 手指往下, 让 header 跟着滚回来
                scrollableState.scroll {
                    with(flingBehavior) {
                        performFling(available.y)
                    }
                }
            }
            return super.onPostFling(consumed, available)
        }
    }

    /**
     * 将 header 完全滚回 (带动画).
     */
    suspend fun animateExpandHeader() {
        scrollableState.animateScrollBy(
            scrolledOffset,
            tween(500, easing = StandardDecelerateEasing),
        )
    }

    companion object {
        fun saver(flingBehavior: FlingBehavior): Saver<NestedScrollableColumnState, Float> = Saver(
            save = { it.scrolledOffset },
            restore = { NestedScrollableColumnState(flingBehavior, initialScrolledOffset = it) },
        )
    }
}

/**
 * [NestedScrollableColumn] 的 slot scope, 提供接线用的辅助工具.
 */
@Stable
abstract class NestedScrollableScope {
    /**
     * header 是否已完全滚出布局.
     */
    abstract val isHeaderScrolledOut: Boolean

    /**
     * header 滚出的进度, `0f` = 完全可见, `1f` = 完全滚出.
     */
    abstract val collapseProgress: Float

    /**
     * 解决鼠标滚轮不触发 nested scroll 的 Compose bug:
     * 当 [scrollableState] 已在顶部且滚轮继续向上滚动时, 将 header 滚回.
     *
     * 应用到 content 内的每个可滚动组件上, [scrollableState] 传其自身的滚动状态
     * (LazyList/LazyGrid state 或 `Modifier.verticalScroll` 的 [androidx.compose.foundation.ScrollState]).
     *
     * 当 Compose 修复 bug 后, 直接删除此 modifier 就行.
     */
    abstract fun Modifier.nestedScrollWorkaround(scrollableState: ScrollableState): Modifier
}

private class NestedScrollableScopeImpl(
    private val state: NestedScrollableColumnState,
) : NestedScrollableScope() {
    override val isHeaderScrolledOut: Boolean
        get() = state.isHeaderScrolledOut

    override val collapseProgress: Float
        get() = state.collapseProgress

    override fun Modifier.nestedScrollWorkaround(scrollableState: ScrollableState): Modifier = composed {
        val scope = rememberCoroutineScope()
        var isInProgress = false
        onPointerEventMultiplatform(PointerEventType.Scroll, pass = PointerEventPass.Final) {
            if (isInProgress) return@onPointerEventMultiplatform

            val event = it.changes.getOrNull(0) ?: return@onPointerEventMultiplatform
            if (event.type != PointerType.Mouse) {
                // 只有鼠标有 bug
                return@onPointerEventMultiplatform
            }

            val scrollDelta = event.scrollDelta
            if (scrollDelta != Offset.Unspecified && scrollDelta != Offset.Zero) {
                if (!scrollableState.canScrollBackward && scrollDelta.y < -0.5f) { // 0.5 为阈值, 防止稍微动一下
                    isInProgress = true
                    scope.launch {
                        try {
                            state.animateExpandHeader()
                        } finally {
                            isInProgress = false
                        }
                    }
                }
            }
        }
    }
}

/**
 * 类似 [Column][androidx.compose.foundation.layout.Column] 的两段式嵌套滚动布局:
 * [header] 在上, [content] 在下, [content] 内可以有嵌套的可垂直滚动组件
 * (LazyColumn / `Modifier.verticalScroll` 等).
 *
 * ### 交互逻辑
 *
 * - 初始时 [header] 占据其固有高度, [content] 占据剩余空间;
 * - 向上滚动 (手指向上) 时 [header] 逐渐滚出布局外, [content] 高度随之增大,
 *   直到 header 完全滚出, content 占满布局;
 * - 此后滚动才由 [content] 内部的 scrollable 消费;
 * - 内部 scrollable 位于其顶部时继续向下拉, header 才会滚回来.
 *
 * ### 布局规则
 *
 * - [header] 的高度不会被压缩 (以无界高度测量), 即使超过布局高度;
 * - [content] 的高度 = 布局高度 - 可见的 header 部分, 若 header 高于布局则为 0.
 *
 * ### 接线
 *
 * [content] 内的嵌套 scrollable 自动通过 nested scroll 参与协调, 无需额外接线.
 * 桌面平台的鼠标滚轮因 Compose bug 不触发 nested scroll, 需要为每个内部 scrollable
 * 使用 [NestedScrollableScope.nestedScrollWorkaround].
 *
 * 本布局需要有界的高度约束.
 */
@Composable
fun NestedScrollableColumn(
    header: @Composable NestedScrollableScope.() -> Unit,
    content: @Composable NestedScrollableScope.() -> Unit,
    modifier: Modifier = Modifier,
    state: NestedScrollableColumnState = rememberNestedScrollableColumnState(),
) {
    val scope = remember(state) { NestedScrollableScopeImpl(state) }
    Layout(
        content = {
            // 用 Box 包装, 保证每个 slot 恰好一个 measurable.
            Box { scope.header() }
            Box { scope.content() }
        },
        modifier = modifier
            .clipToBounds()
            .nestedScroll(state.nestedScrollConnection)
            .scrollable(state.scrollableState, Orientation.Vertical),
    ) { measurables, constraints ->
        require(constraints.hasBoundedHeight) { "NestedScrollableColumn requires bounded height constraints" }
        val viewportHeight = constraints.maxHeight

        // header 高度不可被压缩: 以无界高度测量.
        val headerPlaceable = measurables[0].measure(
            constraints.copy(minWidth = 0, minHeight = 0, maxHeight = Constraints.Infinity),
        )
        if (state.headerHeight != headerPlaceable.height) {
            state.headerHeight = headerPlaceable.height
        }
        // header 变矮后, 已滚出的距离可能超出范围, 需要收敛.
        if (state.scrolledOffset > headerPlaceable.height) {
            state.scrolledOffset = headerPlaceable.height.toFloat()
        }
        val scrolledOffset = state.scrolledOffset.roundToInt().coerceIn(0, headerPlaceable.height)

        // content 高度 = 视口高度 - 可见的 header 部分.
        val contentHeight = (viewportHeight - headerPlaceable.height + scrolledOffset)
            .coerceIn(0, viewportHeight)
        val contentPlaceable = measurables[1].measure(
            constraints.copy(minWidth = 0, minHeight = 0, maxHeight = contentHeight),
        )

        val width = maxOf(headerPlaceable.width, contentPlaceable.width)
            .coerceIn(constraints.minWidth, constraints.maxWidth)
        layout(width, viewportHeight) {
            headerPlaceable.place(0, -scrolledOffset)
            contentPlaceable.place(0, headerPlaceable.height - scrolledOffset)
        }
    }
}

@Composable
@Preview
private fun PreviewNestedScrollableColumn() = ProvideCompositionLocalsForPreview {
    Surface(color = MaterialTheme.colorScheme.surface) {
        val state = rememberNestedScrollableColumnState()
        val pagerState = rememberPagerState(pageCount = { 3 })
        val uiScope = rememberCoroutineScope()

        NestedScrollableColumn(
            header = {
                Column(Modifier.fillMaxWidth()) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(280.dp)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "Header\n" +
                                    "collapseProgress = ${(collapseProgress * 100).roundToInt()}%\n" +
                                    "isHeaderScrolledOut = $isHeaderScrolledOut",
                        )
                    }

                    TabRow(selectedTabIndex = pagerState.currentPage) {
                        repeat(3) { index ->
                            Tab(
                                selected = pagerState.currentPage == index,
                                onClick = { uiScope.launch { pagerState.animateScrollToPage(index) } },
                                text = { Text("Tab $index") },
                            )
                        }
                    }
                }
            },
            content = {
                HorizontalPager(
                    pagerState,
                    Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.Top,
                ) { page ->
                    val listState = rememberLazyListState()
                    LazyColumn(
                        Modifier
                            .fillMaxSize()
                            .nestedScrollWorkaround(listState),
                        state = listState,
                    ) {
                        items(100) { item ->
                            Text(
                                "Page $page - Item $item",
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                            )
                        }
                    }
                }
            },
            Modifier.fillMaxSize(),
            state = state,
        )
    }
}
