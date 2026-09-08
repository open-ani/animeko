/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.leanback.ui.episode

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Person
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import kotlinx.coroutines.launch
import me.him188.ani.app.data.models.episode.EpisodeComment
import me.him188.ani.app.data.models.episode.EpisodeCommentSource
import me.him188.ani.app.ui.foundation.avatar.AvatarImage
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.comment_hide_hidden
import me.him188.ani.app.ui.lang.comment_preview_image
import me.him188.ani.app.ui.lang.comment_preview_quote
import me.him188.ani.app.ui.lang.comment_read_full
import me.him188.ani.app.ui.lang.comment_show_hidden
import me.him188.ani.app.ui.lang.foundation_anonymous
import me.him188.ani.app.ui.lang.tv_player_scroll_hide_hint
import me.him188.ani.app.ui.lang.tv_player_scroll_hint
import me.him188.ani.app.ui.lang.tv_player_scroll_reveal_hint
import me.him188.ani.app.ui.richtext.RichText
import me.him188.ani.app.ui.richtext.UIRichElement
import me.him188.ani.app.ui.richtext.rememberBBCodeRichTextState
import me.him188.ani.leanback.ui.foundation.focus.TvFocusDefaults
import me.him188.ani.leanback.ui.foundation.widgets.LocalTvOptionColors
import me.him188.ani.leanback.ui.foundation.widgets.TvOptionDefaults
import org.jetbrains.compose.resources.stringResource
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** One card is one D-pad target; rich text never competes with the card's primary action. */
@Composable
internal fun TvCommentCard(comment: EpisodeComment, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val colors = LocalTvOptionColors.current
    val elements = rememberCommentElements(comment.content)
    val quote = stringResource(Lang.comment_preview_quote)
    val image = stringResource(Lang.comment_preview_image)
    val preview = remember(elements, quote, image) { elements.toTvCommentPreview(quote, image) }
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().testTag("tv-comment-${comment.stableId}"),
        shape = ClickableSurfaceDefaults.shape(TvOptionDefaults.ItemShape),
        // A dark focused surface keeps BBCode links, quotes and masks readable.
        colors = ClickableSurfaceDefaults.colors(
            containerColor = colors.raised,
            contentColor = colors.content,
            focusedContainerColor = colors.selectedContainer ?: colors.raised,
            focusedContentColor = colors.content,
        ),
        border = TvFocusDefaults.clickableCardBorder(TvOptionDefaults.ItemShape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = TvFocusDefaults.FocusedScale),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            CommentAuthor(comment)
            RichText(
                elements = listOf(preview),
                modifier = Modifier.fillMaxWidth().heightIn(min = 78.dp).testTag("tv-comment-preview-${comment.stableId}"),
                color = colors.content,
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp, lineHeight = 26.sp),
                interactionEnabled = false,
            )
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(Lang.comment_read_full), Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, color = colors.muted)
                Icon(Icons.Rounded.ChevronRight, null, Modifier.size(20.dp), tint = colors.muted)
            }
        }
    }
}

/** Keep the reading area as one focus target, including BBCode images and hidden text. */
@Composable
internal fun TvCommentDetail(comment: EpisodeComment, modifier: Modifier = Modifier) {
    val colors = LocalTvOptionColors.current
    val elements = rememberCommentElements(comment.content)
    val hasMaskedText = remember(elements) { elements.hasMaskedText() }
    var revealMaskedText by remember(comment.stableId, comment.content) { mutableStateOf(false) }
    val visibleElements = remember(elements, revealMaskedText) {
        if (revealMaskedText) elements.revealMaskedText() else elements
    }
    val maskAction = if (revealMaskedText) stringResource(Lang.comment_hide_hidden) else stringResource(Lang.comment_show_hidden)
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    val step = with(LocalDensity.current) { 96.dp.toPx() }
    var focused by remember { mutableStateOf(false) }

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        CommentAuthor(comment, Modifier.padding(horizontal = 16.dp))
        Text(
            stringResource(
                if (!hasMaskedText) Lang.tv_player_scroll_hint
                else if (revealMaskedText) Lang.tv_player_scroll_hide_hint else Lang.tv_player_scroll_reveal_hint,
            ),
            Modifier.padding(horizontal = 16.dp),
            style = MaterialTheme.typography.labelMedium,
            color = colors.muted,
        )
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .onFocusChanged { focused = it.isFocused }
                .onPreviewKeyEvent { event ->
                    if (hasMaskedText && event.key in listOf(Key.DirectionCenter, Key.Enter, Key.NumPadEnter)) {
                        if (event.type == KeyEventType.KeyDown && event.nativeKeyEvent.repeatCount == 0) {
                            revealMaskedText = !revealMaskedText
                        }
                        return@onPreviewKeyEvent true
                    }
                    val direction = when (event.key) {
                        Key.DirectionDown -> if (scroll.canScrollForward) 1 else 0
                        Key.DirectionUp -> if (scroll.canScrollBackward) -1 else 0
                        else -> 0
                    }
                    if (direction == 0) false else {
                        if (event.type == KeyEventType.KeyDown) scope.launch { scroll.scrollBy(step * direction) }
                        true
                    }
                }
                .semantics {
                    if (hasMaskedText) onClick(maskAction) {
                        revealMaskedText = !revealMaskedText
                        true
                    }
                }
                .border(
                    2.dp,
                    if (focused) MaterialTheme.colorScheme.primary else Color.Transparent,
                    TvOptionDefaults.ItemShape,
                )
                .clip(TvOptionDefaults.ItemShape)
                .focusable()
                .verticalScroll(scroll)
                .padding(16.dp)
                .testTag("tv-comment-full-text"),
        ) {
            RichText(
                elements = visibleElements,
                modifier = Modifier.fillMaxWidth(),
                color = colors.content,
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp, lineHeight = 26.sp),
                interactionEnabled = false,
            )
        }
    }
}

@Composable
private fun CommentAuthor(comment: EpisodeComment, modifier: Modifier = Modifier) {
    val colors = LocalTvOptionColors.current
    Row(
        modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier.size(36.dp).clip(CircleShape).background(colors.content.copy(alpha = .08f)),
            contentAlignment = Alignment.Center,
        ) {
            val avatar = comment.author?.avatarUrl?.takeIf { it.isNotBlank() }
            if (avatar == null) Icon(Icons.Rounded.Person, null, Modifier.size(24.dp), tint = colors.muted)
            else AvatarImage(avatar, Modifier.fillMaxSize())
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                comment.author?.nickname?.takeIf { it.isNotBlank() } ?: stringResource(Lang.foundation_anonymous),
                style = MaterialTheme.typography.titleSmall,
                color = colors.content,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val source = when (comment.source) {
                EpisodeCommentSource.ANI -> "Animeko"
                EpisodeCommentSource.BANGUMI -> "Bangumi"
            }
            val date = remember(comment.createdAt) {
                SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(comment.createdAt))
            }
            Text(
                "$source · $date",
                style = MaterialTheme.typography.labelMedium,
                color = colors.muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun rememberCommentElements(content: String): List<UIRichElement> {
    val state = rememberBBCodeRichTextState(content, defaultTextSize = 18.sp)
    SideEffect { state.setText(content) }
    return state.elements
}

/** Bound the whole preview, preserving inline formatting, stickers and spoiler masks. */
internal fun List<UIRichElement>.toTvCommentPreview(quote: String, image: String): UIRichElement.AnnotatedText = UIRichElement.AnnotatedText(
    slice = flatMap { element ->
        when (element) {
            is UIRichElement.AnnotatedText -> element.slice.map {
                if (it is UIRichElement.Annotated.Text) it.copy(content = it.content.replace('\n', ' ')) else it
            }
            is UIRichElement.Quote -> listOf(UIRichElement.Annotated.Text(" $quote ", size = 18f))
            is UIRichElement.Image -> listOf(UIRichElement.Annotated.Text(" $image ", size = 18f))
        }
    },
    maxLine = 3,
)

private fun List<UIRichElement>.hasMaskedText(): Boolean = any { element ->
    when (element) {
        is UIRichElement.AnnotatedText -> element.slice.any { it is UIRichElement.Annotated.Text && it.mask }
        is UIRichElement.Quote -> element.content.hasMaskedText()
        is UIRichElement.Image -> false
    }
}

private fun List<UIRichElement>.revealMaskedText(): List<UIRichElement> = map { element ->
    when (element) {
        is UIRichElement.AnnotatedText -> element.copy(slice = element.slice.map {
            if (it is UIRichElement.Annotated.Text) it.copy(mask = false) else it
        })
        is UIRichElement.Quote -> element.copy(content = element.content.revealMaskedText())
        is UIRichElement.Image -> element
    }
}
