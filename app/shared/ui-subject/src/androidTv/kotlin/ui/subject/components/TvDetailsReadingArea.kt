/*
 * Copyright (C) 2026 OpenAni and contributors.
 * Use of this source code is governed by the GNU AGPLv3 license.
 */
package me.him188.ani.leanback.ui.subject.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** A single remote focus target with a shared scroll column, fades and a scrollbar. */
@Composable
internal fun TvDetailsReadingArea(
    modifier: Modifier = Modifier,
    scroll: ScrollState = rememberScrollState(),
    content: @Composable ColumnScope.() -> Unit,
) {
    val scope = rememberCoroutineScope()
    val step = with(LocalDensity.current) { 84.dp.toPx() }
    var focused by remember { mutableStateOf(false) }
    var scrollingKey by remember { mutableStateOf<Key?>(null) }
    var scrolling by remember { mutableStateOf<Job?>(null) }
    Box(modifier.onFocusChanged { focused = it.isFocused }.onPreviewKeyEvent { event ->
        if (event.type == KeyEventType.KeyUp && event.key == scrollingKey) {
            scrollingKey = null
            true
        } else if (!focused) false else {
            val direction = when (event.key) {
                Key.DirectionDown -> if (scroll.canScrollForward) 1 else 0
                Key.DirectionUp -> if (scroll.canScrollBackward) -1 else 0
                else -> 0
            }
            if (direction == 0) false else {
                if (event.type == KeyEventType.KeyDown) {
                    scrollingKey = event.key
                    scrolling?.cancel()
                    scrolling = scope.launch { scroll.animateScrollBy(step * direction) }
                }
                true
            }
        }
    }.focusable()) {
        Column(Modifier.fillMaxSize().padding(end = 24.dp).readerEdges(scroll).verticalScroll(scroll), content = content)
        Canvas(Modifier.align(Alignment.CenterEnd).width(6.dp).fillMaxHeight().testTag("tv-description-scrollbar")) {
            if (scroll.maxValue <= 0 || scroll.maxValue == Int.MAX_VALUE) return@Canvas
            val fraction = size.height / (size.height + scroll.maxValue)
            val height = (size.height * fraction).coerceIn(24.dp.toPx().coerceAtMost(size.height), size.height)
            drawRoundRect(Color.White.copy(alpha = .10f), cornerRadius = CornerRadius(size.width))
            drawRoundRect(Color.White.copy(alpha = if (focused) .9f else .35f),
                topLeft = Offset(0f, (size.height - height) * scroll.value / scroll.maxValue),
                size = Size(size.width, height), cornerRadius = CornerRadius(size.width))
        }
    }
}

private fun Modifier.readerEdges(scroll: ScrollState) = graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        val fade = TvSubjectDetailsDefaults.ReaderEdgeFade.toPx().coerceAtMost(size.height / 2)
        if (scroll.canScrollBackward) drawRect(
            Brush.verticalGradient(0f to Color.Transparent, 1f to Color.Black, endY = fade),
            size = Size(size.width, fade), blendMode = BlendMode.DstIn,
        )
        if (scroll.canScrollForward) drawRect(
            Brush.verticalGradient(0f to Color.Black, 1f to Color.Transparent, startY = size.height - fade, endY = size.height),
            topLeft = Offset(0f, size.height - fade), size = Size(size.width, fade), blendMode = BlendMode.DstIn,
        )
    }
