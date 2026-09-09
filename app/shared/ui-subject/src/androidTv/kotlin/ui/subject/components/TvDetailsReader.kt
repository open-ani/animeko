/*
 * Copyright (C) 2026 OpenAni and contributors.
 * Use of this source code is governed by the GNU AGPLv3 license.
 */
package me.him188.ani.leanback.ui.subject.components

import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import me.him188.ani.app.ui.richtext.RichText
import me.him188.ani.app.ui.richtext.UIRichElement
import me.him188.ani.leanback.ui.foundation.focus.TvFocusDefaults
import me.him188.ani.leanback.ui.foundation.widgets.TvOptionDefaults

@Composable
internal fun TvDetailsReader(elements: List<UIRichElement>, modifier: Modifier = Modifier) {
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    val step = with(LocalDensity.current) { 96.dp.toPx() }
    var focused by remember { mutableStateOf(false) }
    var scrollingKey by remember { mutableStateOf<Key?>(null) }
    Column(
        modifier.fillMaxWidth().heightIn(max = 360.dp)
            .onFocusChanged { focused = it.isFocused }
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyUp && event.key == scrollingKey) {
                    scrollingKey = null
                    return@onPreviewKeyEvent true
                }
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
            .border(TvFocusDefaults.RingWidth, if (focused) MaterialTheme.colorScheme.primary else Color.Transparent, MaterialTheme.shapes.small)
            .focusable().verticalScroll(scroll).padding(12.dp),
    ) {
        RichText(elements, color = TvOptionDefaults.Content,
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp, lineHeight = 27.sp), interactionEnabled = false)
    }
}

internal fun String.asDetailsText(): List<UIRichElement> = listOf(
    UIRichElement.AnnotatedText(listOf(UIRichElement.Annotated.Text(this, size = 18f))),
)

internal fun List<UIRichElement>.detailsHasMask(): Boolean = any {
    when (it) {
        is UIRichElement.AnnotatedText -> it.slice.any { it is UIRichElement.Annotated.Text && it.mask }
        is UIRichElement.Quote -> it.content.detailsHasMask()
        is UIRichElement.Image -> false
    }
}

internal fun List<UIRichElement>.detailsRevealMasks(): List<UIRichElement> = map {
    when (it) {
        is UIRichElement.AnnotatedText -> it.copy(slice = it.slice.map { if (it is UIRichElement.Annotated.Text) it.copy(mask = false) else it })
        is UIRichElement.Quote -> it.copy(content = it.content.detailsRevealMasks())
        is UIRichElement.Image -> it
    }
}

internal fun List<UIRichElement>.detailsLinks(): List<String> = flatMap {
    when (it) {
        is UIRichElement.AnnotatedText -> it.slice.mapNotNull { span ->
            span.url?.takeUnless { span is UIRichElement.Annotated.Text && span.mask }
        }
        is UIRichElement.Quote -> it.content.detailsLinks()
        is UIRichElement.Image -> listOfNotNull(it.jumpUrl)
    }
}.distinct()

internal fun List<UIRichElement>.detailsImages(): List<String> = flatMap {
    when (it) {
        is UIRichElement.Image -> listOf(it.imageUrl)
        is UIRichElement.Quote -> it.content.detailsImages()
        is UIRichElement.AnnotatedText -> emptyList()
    }
}.distinct()
