/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.watchtogether

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize

@Immutable
internal data class SettledBubblePlacement(
    val offset: Offset,
    val containerSize: IntSize,
    val bubbleSize: IntSize,
)

/**
 * Keeps the bubble's last settled position outside its visibility-controlled subtree.
 *
 * Drag updates are intentionally not stored here. A placement is persisted only after the drag settles or after a
 * container-size change has produced its new target.
 */
@Stable
internal class DraggableBubblePositionState(
    initialPlacement: SettledBubblePlacement? = null,
) {
    var settledPlacement by mutableStateOf(initialPlacement)
        private set

    fun settle(offset: Offset, containerSize: IntSize, bubbleSize: IntSize) {
        settledPlacement = SettledBubblePlacement(offset, containerSize, bubbleSize)
    }

    fun targetFor(containerSize: IntSize, bubbleSize: IntSize, marginPx: Float): Offset? {
        if (containerSize.width <= 0 || containerSize.height <= 0 || bubbleSize == IntSize.Zero) return null

        return calculateBubbleTarget(
            previous = settledPlacement,
            containerSize = containerSize,
            bubbleSize = bubbleSize,
            marginPx = marginPx,
        )
    }

    companion object {
        val Saver: Saver<DraggableBubblePositionState, List<Float>> = Saver(
            save = { state ->
                state.settledPlacement?.let { placement ->
                    listOf(
                        placement.offset.x,
                        placement.offset.y,
                        placement.containerSize.width.toFloat(),
                        placement.containerSize.height.toFloat(),
                        placement.bubbleSize.width.toFloat(),
                        placement.bubbleSize.height.toFloat(),
                    )
                } ?: emptyList()
            },
            restore = { saved ->
                val placement = saved.takeIf { it.size == 6 }?.let {
                    SettledBubblePlacement(
                        offset = Offset(it[0], it[1]),
                        containerSize = IntSize(it[2].toInt(), it[3].toInt()),
                        bubbleSize = IntSize(it[4].toInt(), it[5].toInt()),
                    )
                }
                DraggableBubblePositionState(placement)
            },
        )
    }
}

@Composable
internal fun rememberDraggableBubblePositionState(): DraggableBubblePositionState =
    rememberSaveable(saver = DraggableBubblePositionState.Saver) {
        DraggableBubblePositionState()
    }

internal fun calculateBubbleTarget(
    previous: SettledBubblePlacement?,
    containerSize: IntSize,
    bubbleSize: IntSize,
    marginPx: Float,
): Offset {
    val maxX = (containerSize.width - bubbleSize.width - marginPx).coerceAtLeast(marginPx)
    val maxY = (containerSize.height - bubbleSize.height - marginPx).coerceAtLeast(marginPx)

    if (previous == null || previous.containerSize == IntSize.Zero || previous.bubbleSize == IntSize.Zero) {
        return Offset(
            x = maxX,
            y = (containerSize.height * 0.68f).coerceIn(marginPx, maxY),
        )
    }

    val wasRight = previous.offset.x + previous.bubbleSize.width / 2f >= previous.containerSize.width / 2f
    val wasBottom = previous.offset.y + previous.bubbleSize.height / 2f >= previous.containerSize.height / 2f
    val bottomGap = previous.containerSize.height - previous.bubbleSize.height - previous.offset.y

    val x = if (wasRight) maxX else previous.offset.x
    val y = if (wasBottom) {
        containerSize.height - bubbleSize.height - bottomGap
    } else {
        previous.offset.y
    }

    return Offset(
        x = x.coerceIn(marginPx, maxX),
        y = y.coerceIn(marginPx, maxY),
    )
}
