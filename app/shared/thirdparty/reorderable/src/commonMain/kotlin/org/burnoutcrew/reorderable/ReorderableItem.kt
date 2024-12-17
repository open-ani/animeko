/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */
package org.burnoutcrew.reorderable

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.grid.LazyGridItemScope
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridItemScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.zIndex

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LazyItemScope.ReorderableItem(
    reorderableState: ReorderableState<*>,
    key: Any?,
    modifier: Modifier = Modifier,
    index: Int? = null,
    orientationLocked: Boolean = true,
    content: @Composable BoxScope.(isDragging: Boolean) -> Unit
) = ReorderableItem(
    reorderableState,
    key,
    modifier,
    Modifier.animateItem(fadeInSpec = null, fadeOutSpec = null),
    orientationLocked,
    index,
    content,
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LazyGridItemScope.ReorderableItem(
    reorderableState: ReorderableState<*>,
    key: Any?,
    modifier: Modifier = Modifier,
    index: Int? = null,
    content: @Composable BoxScope.(isDragging: Boolean) -> Unit
) = ReorderableItem(
    reorderableState,
    key,
    modifier,
    Modifier.animateItem(fadeInSpec = null, fadeOutSpec = null),
    false,
    index,
    content,
)


@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LazyStaggeredGridItemScope.ReorderableItem(
    reorderableState: ReorderableState<*>,
    key: Any?,
    modifier: Modifier = Modifier,
    index: Int? = null,
    content: @Composable BoxScope.(isDragging: Boolean) -> Unit
) = ReorderableItem(
    reorderableState,
    key,
    modifier,
    Modifier.animateDraggeableItemPlacement(),
    false,
    index,
    content,
)

/**
 * XXX LazyGridItemScope.Modifier.animateItemPlacement is missing from LazyStaggeredGridItemScope
 * XXX Replace this when added to the compose library.
 *
 * @param animationSpec a finite animation that will be used to animate the item placement.
 */
@ExperimentalFoundationApi
fun Modifier.animateDraggeableItemPlacement(
    animationSpec: FiniteAnimationSpec<IntOffset> = spring(
        stiffness = Spring.StiffnessMediumLow,
        visibilityThreshold = IntOffset.VisibilityThreshold,
    )
): Modifier = this

@Composable
fun ReorderableItem(
    state: ReorderableState<*>,
    key: Any?,
    modifier: Modifier = Modifier,
    defaultDraggingModifier: Modifier = Modifier,
    orientationLocked: Boolean = true,
    index: Int? = null,
    content: @Composable BoxScope.(isDragging: Boolean) -> Unit
) {
    val isDragging = if (index != null) {
        index == state.draggingItemIndex
    } else {
        key == state.draggingItemKey
    }
    val draggingModifier =
        if (isDragging) {
            Modifier
                .zIndex(1f)
                .graphicsLayer {
                    translationX =
                        if (!orientationLocked || !state.isVerticalScroll) state.draggingItemLeft else 0f
                    translationY =
                        if (!orientationLocked || state.isVerticalScroll) state.draggingItemTop else 0f
                }
        } else {
            val cancel = if (index != null) {
                index == state.dragCancelledAnimation.position?.index
            } else {
                key == state.dragCancelledAnimation.position?.key
            }
            if (cancel) {
                Modifier.zIndex(1f)
                    .graphicsLayer {
                        translationX =
                            if (!orientationLocked || !state.isVerticalScroll) state.dragCancelledAnimation.offset.x else 0f
                        translationY =
                            if (!orientationLocked || state.isVerticalScroll) state.dragCancelledAnimation.offset.y else 0f
                    }
            } else {
                defaultDraggingModifier
            }
        }
    Box(modifier = modifier.then(draggingModifier)) {
        content(isDragging)
    }
}
