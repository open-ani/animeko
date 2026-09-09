/*
 * Copyright (C) 2026 OpenAni and contributors.
 * Use of this source code is governed by the GNU AGPLv3 license.
 */
package me.him188.ani.leanback.ui.subject.details

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Subject
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusDirection
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
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.subject_details_no_summary
import me.him188.ani.app.ui.lang.subject_details_summary
import me.him188.ani.leanback.ui.foundation.focus.tvFocusAnchor
import me.him188.ani.leanback.ui.foundation.focus.tvFocusExit
import me.him188.ani.leanback.ui.foundation.focus.tvFocusLink
import me.him188.ani.leanback.ui.subject.TvSubjectDetailsContentState
import me.him188.ani.leanback.ui.subject.components.TvDetailsFullscreenOverlay
import me.him188.ani.leanback.ui.subject.components.TvSubjectDetailsDefaults
import me.him188.ani.leanback.ui.subject.presentation.TvDetailsKey
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun TvSubjectDescription(
    details: TvSubjectDetailsContentState,
    backdrop: String,
    onClose: () -> Unit,
    onTag: (String) -> Unit,
) {
    TvDetailsFullscreenOverlay(
        backdrop, onClose,
        initialKey = details.info.tags.firstOrNull()?.let { "tag:${it.name}" } ?: "description-body",
    ) { focus ->
        val scroll = rememberScrollState()
        val scope = rememberCoroutineScope()
        val step = with(LocalDensity.current) { 84.dp.toPx() }
        var focused by remember { mutableStateOf(false) }
        var scrollingKey by remember { mutableStateOf<Key?>(null) }
        Column(Modifier.fillMaxSize().padding(
            start = TvSubjectDetailsDefaults.HorizontalPadding, end = TvSubjectDetailsDefaults.HorizontalPadding,
            top = 50.dp, bottom = 22.dp,
        )) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.AutoMirrored.Rounded.Subject, null, Modifier.size(28.dp), tint = TvSubjectDetailsDefaults.Content)
                Text(stringResource(Lang.subject_details_summary), color = TvSubjectDetailsDefaults.Content,
                    style = MaterialTheme.typography.headlineMedium.copy(fontSize = 26.sp),
                    modifier = Modifier.testTag("tv-description-title"))
            }
            Spacer(Modifier.height(44.dp))
            if (details.info.tags.isNotEmpty()) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth().padding(end = 24.dp)
                        .tvFocusExit(focus, FocusDirection.Down to TvDetailsKey("description-body"))
                        .testTag("tv-description-tags"),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    details.info.tags.distinctBy { it.name }.forEach { tag ->
                        key(tag.name) {
                            TvDetailsAction(
                                label = tag.name,
                                onClick = { onTag(tag.name) },
                                modifier = Modifier.tvFocusAnchor(focus, TvDetailsKey("tag:${tag.name}"))
                                    .testTag("tv-description-tag:${tag.name}"),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
            Box(
                Modifier.weight(1f).fillMaxWidth()
                    .tvFocusAnchor(focus, TvDetailsKey("description-body"))
                    .tvFocusLink(focus, up = details.info.tags.firstOrNull()?.let { TvDetailsKey("tag:${it.name}") })
                    .onFocusChanged { focused = it.isFocused }
                    .onPreviewKeyEvent { event ->
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
                                    scope.launch { scroll.scrollBy(step * direction) }
                                }
                                true
                            }
                        }
                    }.focusable().testTag("tv-details-panel-summary-text"),
            ) {
                Column(Modifier.fillMaxSize().padding(end = 24.dp).readerEdges(scroll).verticalScroll(scroll)) {
                    Column(Modifier.fillMaxWidth(.78f), verticalArrangement = Arrangement.spacedBy(28.dp)) {
                        Text(details.info.summary.ifBlank { stringResource(Lang.subject_details_no_summary) },
                            color = TvSubjectDetailsDefaults.SecondaryContent,
                            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 20.sp, lineHeight = 42.sp),
                            modifier = Modifier.testTag("tv-description-text"))
                        TvSubjectInformation(
                            details.info, totalEpisodes = if (details.episodesLoading) null else details.mainEpisodeIds.size,
                            modifier = Modifier.testTag("tv-description-info"),
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                }
                TvDescriptionScrollbar(scroll, focused, Modifier.align(Alignment.CenterEnd).width(8.dp).fillMaxHeight())
            }
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

@Composable
private fun TvDescriptionScrollbar(scroll: ScrollState, focused: Boolean, modifier: Modifier) {
    Canvas(modifier.testTag("tv-description-scrollbar")) {
        if (scroll.maxValue <= 0 || scroll.maxValue == Int.MAX_VALUE) return@Canvas
        val fraction = size.height / (size.height + scroll.maxValue)
        val height = (size.height * fraction).coerceAtLeast(24.dp.toPx())
        drawRoundRect(Color.White.copy(alpha = .10f), cornerRadius = CornerRadius(size.width))
        drawRoundRect(Color.White.copy(alpha = if (focused) .9f else .35f),
            topLeft = Offset(0f, (size.height - height) * scroll.value / scroll.maxValue),
            size = Size(size.width, height), cornerRadius = CornerRadius(size.width))
    }
}
