/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.leanback.ui.foundation.layout

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Indicates offscreen list content without adding another focus target. */
fun Modifier.tvPanelScrollEdges(
    state: LazyListState,
    backgroundColor: Color,
): Modifier = drawWithContent {
    drawContent()
    val edge = 12.dp.toPx().coerceAtMost(size.height / 2)
    if (state.canScrollBackward) drawRect(
        Brush.verticalGradient(listOf(backgroundColor, Color.Transparent), endY = edge),
        size = Size(size.width, edge),
    )
    if (state.canScrollForward) drawRect(
        Brush.verticalGradient(
            listOf(Color.Transparent, backgroundColor),
            startY = size.height - edge,
            endY = size.height,
        ),
        topLeft = Offset(0f, size.height - edge),
        size = Size(size.width, edge),
    )
}
