/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */
package org.burnoutcrew.reorderable

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridItemInfo
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.CoroutineScope


@OptIn(ExperimentalFoundationApi::class)
@Composable
fun rememberReorderableLazyHorizontalStaggeredGridState(
    onMove: (ItemPosition, ItemPosition) -> Unit,
    gridState: LazyStaggeredGridState = rememberLazyStaggeredGridState(),
    canDragOver: ((draggedOver: ItemPosition, dragging: ItemPosition) -> Boolean)? = null,
    onDragEnd: ((startIndex: Int, endIndex: Int) -> (Unit))? = null,
    maxScrollPerFrame: Float = 20f,
    dragCancelledAnimation: DragCancelledAnimation = SpringDragCancelledAnimation(),
) = rememberReorderableLazyStaggeredGridState(
    onMove = onMove,
    gridState = gridState,
    canDragOver = canDragOver,
    onDragEnd = onDragEnd,
    maxScrollPerFrame = maxScrollPerFrame,
    dragCancelledAnimation = dragCancelledAnimation,
    orientation = Orientation.Horizontal,
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun rememberReorderableLazyVerticalStaggeredGridState(
    onMove: (ItemPosition, ItemPosition) -> Unit,
    gridState: LazyStaggeredGridState = rememberLazyStaggeredGridState(),
    canDragOver: ((draggedOver: ItemPosition, dragging: ItemPosition) -> Boolean)? = null,
    onDragEnd: ((startIndex: Int, endIndex: Int) -> (Unit))? = null,
    maxScrollPerFrame: Float = 20f,
    dragCancelledAnimation: DragCancelledAnimation = SpringDragCancelledAnimation(),
) = rememberReorderableLazyStaggeredGridState(
    onMove = onMove,
    gridState = gridState,
    canDragOver = canDragOver,
    onDragEnd = onDragEnd,
    maxScrollPerFrame = maxScrollPerFrame,
    dragCancelledAnimation = dragCancelledAnimation,
    orientation = Orientation.Vertical,
)


@OptIn(ExperimentalFoundationApi::class)
@Composable
fun rememberReorderableLazyStaggeredGridState(
    onMove: (ItemPosition, ItemPosition) -> Unit,
    gridState: LazyStaggeredGridState = rememberLazyStaggeredGridState(),
    canDragOver: ((draggedOver: ItemPosition, dragging: ItemPosition) -> Boolean)? = null,
    onDragStart: ((startIndex: Int, x: Int, y: Int) -> (Unit))? = null,
    onDragEnd: ((startIndex: Int, endIndex: Int) -> (Unit))? = null,
    maxScrollPerFrame: Float = 20F,
    dragCancelledAnimation: DragCancelledAnimation = SpringDragCancelledAnimation(),
    orientation: Orientation
): ReorderableLazyStaggeredGridState {
    val maxScroll = with(LocalDensity.current) { maxScrollPerFrame }
    val scope = rememberCoroutineScope()
    val state = remember(gridState) {
        ReorderableLazyStaggeredGridState(
            gridState = gridState,
            scope = scope,
            maxScrollPerFrame = maxScrollPerFrame,
            onMove = onMove,
            onDragStart = onDragStart,
            canDragOver = canDragOver,
            onDragEnd = onDragEnd,
            dragCancelledAnimation = dragCancelledAnimation,
            orientation = orientation,
        )
    }
    LaunchedEffect(state) {
        state.visibleItemsChanged()
            .collect { state.onDrag(0, 0) }
    }

    LaunchedEffect(state) {
        while (true) {
            val diff = state.scrollChannel.receive()
            gridState.scrollBy(diff)
        }
    }
    return state
}

@OptIn(ExperimentalFoundationApi::class)
class ReorderableLazyStaggeredGridState(
    val gridState: LazyStaggeredGridState,
    scope: CoroutineScope,
    maxScrollPerFrame: Float,
    onMove: (fromIndex: ItemPosition, toIndex: ItemPosition) -> (Unit),
    canDragOver: ((draggedOver: ItemPosition, dragging: ItemPosition) -> Boolean)? = null,
    onDragStart: ((startIndex: Int, x: Int, y: Int) -> (Unit))? = null,
    onDragEnd: ((startIndex: Int, endIndex: Int) -> (Unit))? = null,
    dragCancelledAnimation: DragCancelledAnimation = SpringDragCancelledAnimation(),
    val orientation: Orientation
) : ReorderableState<LazyStaggeredGridItemInfo>(
    scope = scope,
    maxScrollPerFrame = maxScrollPerFrame,
    onMove = onMove,
    onDragStart = onDragStart,
    canDragOver = canDragOver,
    onDragEnd = onDragEnd,
    dragCancelledAnimation = dragCancelledAnimation,
) {
    override val isVerticalScroll: Boolean
        get() = orientation == Orientation.Vertical // XXX gridState.isVertical is not accessible
    override val LazyStaggeredGridItemInfo.left: Int
        get() = offset.x
    override val LazyStaggeredGridItemInfo.right: Int
        get() = offset.x + size.width
    override val LazyStaggeredGridItemInfo.top: Int
        get() = offset.y
    override val LazyStaggeredGridItemInfo.bottom: Int
        get() = offset.y + size.height
    override val LazyStaggeredGridItemInfo.width: Int
        get() = size.width
    override val LazyStaggeredGridItemInfo.height: Int
        get() = size.height
    override val LazyStaggeredGridItemInfo.itemIndex: Int
        get() = index
    override val LazyStaggeredGridItemInfo.itemKey: Any
        get() = key
    override val visibleItemsInfo: List<LazyStaggeredGridItemInfo>
        get() = gridState.layoutInfo.visibleItemsInfo
    override val viewportStartOffset: Int
        get() = gridState.layoutInfo.viewportStartOffset
    override val viewportEndOffset: Int
        get() = gridState.layoutInfo.viewportEndOffset
    override val firstVisibleItemIndex: Int
        get() = gridState.firstVisibleItemIndex
    override val firstVisibleItemScrollOffset: Int
        get() = gridState.firstVisibleItemScrollOffset

    override suspend fun scrollToItem(index: Int, offset: Int) {
        gridState.scrollToItem(index, offset)
    }
}
