/*
 * Copyright (C) 2026 OpenAni and contributors.
 * Use of this source code is governed by the GNU AGPLv3 license.
 */
package me.him188.ani.leanback.ui.foundation.layout

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.toSize
import me.him188.ani.leanback.ui.foundation.widgets.TvOptionPanelDefaults
import kotlin.math.roundToInt

/** Window coordinates allow a button and its overlay to live under different layout parents. */
@Stable
class TvOptionAnchors {
    private val bounds = mutableStateMapOf<Any, Rect>()
    fun boundsOf(key: Any): Rect? = bounds[key]
    internal fun update(key: Any, rect: Rect) { bounds[key] = rect }
}

@Composable
fun rememberTvOptionAnchors(): TvOptionAnchors = remember { TvOptionAnchors() }

fun Modifier.tvOptionAnchor(anchors: TvOptionAnchors, key: Any): Modifier = onGloballyPositioned {
    anchors.update(key, Rect(it.positionInWindow(), it.size.toSize()))
}

/**
 * Draw the trigger at its original window bounds and constrain the panel to the space above it.
 * [panelAnchorFraction] aligns the trigger's left edge with that fraction of the panel's width.
 */
@Composable
fun TvAnchoredOptionLayout(
    anchorBounds: Rect,
    modifier: Modifier = Modifier,
    panelWidth: Dp = TvOptionPanelDefaults.Width,
    panelMaxHeight: Dp = TvOptionPanelDefaults.MaxHeight,
    screenPadding: Dp = TvOptionPanelDefaults.ScreenPadding,
    gap: Dp = TvOptionPanelDefaults.Gap,
    panelAnchorFraction: Float = 0f,
    anchor: @Composable () -> Unit,
    panel: @Composable () -> Unit,
) {
    require(panelAnchorFraction in 0f..1f)
    var origin by remember { mutableStateOf<Offset?>(null) }
    Layout(
        modifier = modifier.fillMaxSize().onGloballyPositioned { origin = it.positionInWindow() },
        content = {
            if (origin != null) {
                Box(propagateMinConstraints = true) { anchor() }
                Box(propagateMinConstraints = true) { panel() }
            }
        },
    ) { measurables, constraints ->
        if (measurables.isEmpty()) return@Layout layout(constraints.maxWidth, constraints.maxHeight) { }
        val local = anchorBounds.translate(-checkNotNull(origin))
        val inset = screenPadding.roundToPx()
        val gapPx = gap.roundToPx()
        val width = panelWidth.roundToPx().coerceAtMost((constraints.maxWidth - inset * 2).coerceAtLeast(0))
        val availableHeight = (local.top.roundToInt() - gapPx - inset).coerceAtLeast(0)
        val panelPlaceable = measurables[1].measure(Constraints(
            minWidth = width, maxWidth = width,
            maxHeight = minOf(panelMaxHeight.roundToPx(), availableHeight),
        ))
        val anchorPlaceable = measurables[0].measure(Constraints.fixed(
            local.width.roundToInt().coerceAtLeast(0), local.height.roundToInt().coerceAtLeast(0),
        ))
        val panelX = (local.left - panelPlaceable.width * panelAnchorFraction).roundToInt()
            .coerceIn(inset, (constraints.maxWidth - inset - width).coerceAtLeast(inset))
        layout(constraints.maxWidth, constraints.maxHeight) {
            panelPlaceable.place(panelX, local.top.roundToInt() - gapPx - panelPlaceable.height)
            anchorPlaceable.place(local.left.roundToInt(), local.top.roundToInt())
        }
    }
}
