/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.comment

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddReaction
import androidx.compose.material.icons.outlined.ModeComment
import androidx.compose.material.icons.rounded.Face
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import me.him188.ani.app.ui.foundation.LocalIsPreviewing
import me.him188.ani.app.ui.foundation.avatar.AvatarImage
import me.him188.ani.app.ui.foundation.theme.stronglyWeaken
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.comment_empty_title
import me.him188.ani.app.ui.lang.comment_reply
import me.him188.ani.app.ui.lang.comment_view_more_replies
import me.him188.ani.app.ui.richtext.RichText
import me.him188.ani.app.ui.richtext.RichTextDefaults
import me.him188.ani.app.ui.richtext.UIRichElement
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

object CommentDefaults {
    @Composable
    fun Avatar(url: String?, modifier: Modifier = Modifier) {
        AvatarImage(
            url = url,
            modifier = modifier.size(36.dp),
        )
    }

    @Composable
    fun Reaction(
        reaction: UICommentReaction,
        onClick: () -> Unit,
        modifier: Modifier = Modifier
    ) {
        val backgroundColor by animateColorAsState(
            if (reaction.selected) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                Color.Transparent
            },
        )
        Surface(
            onClick = onClick,
            modifier = Modifier
                .then(modifier),
            enabled = true,
            shape = CircleShape,
            color = backgroundColor,
            border = SuggestionChipDefaults.suggestionChipBorder(true),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val previewing = LocalIsPreviewing.current
                val reactionDrawableRes = reaction.value.bangumiReactionIdOrNull()
                    ?.let { BangumiCommentSticker[it] }

                if (previewing || reactionDrawableRes == null) Icon(
                    imageVector = Icons.Rounded.Face,
                    modifier = Modifier.padding(end = 4.dp).size(24.dp),
                    contentDescription = null,
                ) else Image(
                    painter = painterResource(reactionDrawableRes),
                    modifier = Modifier.padding(end = 4.dp).size(24.dp),
                    contentDescription = null,
                )

                Text(
                    text = reaction.count.toString(),
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(end = 4.dp),
                )
            }
        }
    }

    @Composable
    fun ReactionRow(
        list: List<UICommentReaction>,
        onClickItem: (reactionValue: String) -> Unit,
        modifier: Modifier = Modifier,
    ) {
        FlowRow(
            modifier = modifier,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            list.forEach {
                Reaction(
                    reaction = it,
                    onClick = { onClickItem(it.value) },
                )
            }
        }
    }

    @Composable
    fun ReactionPicker(
        onClickItem: (reactionValue: String) -> Unit,
        modifier: Modifier = Modifier,
    ) {
        val previewing = LocalIsPreviewing.current
        FlowRow(
            modifier = modifier
                .verticalScroll(rememberScrollState())
                .padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            BangumiCommentSticker.map { (id, drawableRes) -> id to drawableRes }.forEach { (id, drawableRes) ->
                Surface(
                    onClick = { onClickItem("bgm$id") },
                    shape = CircleShape,
                    color = Color.Transparent,
                ) {
                    if (previewing) {
                        Icon(
                            imageVector = Icons.Rounded.Face,
                            contentDescription = null,
                            modifier = Modifier.padding(4.dp).size(22.dp),
                        )
                    } else {
                        Image(
                            painter = painterResource(drawableRes),
                            contentDescription = null,
                            modifier = Modifier.padding(4.dp).size(22.dp),
                        )
                    }
                }
            }
        }
    }

    @Composable
    fun ActionRow(
        onClickReply: () -> Unit,
        modifier: Modifier = Modifier,
        showReply: Boolean = true,
        showReaction: Boolean = true,
        onClickReaction: () -> Unit,
        onClickBlock: () -> Unit,
        onClickReport: () -> Unit
    ) {
        val replyText = stringResource(Lang.comment_reply)
        val size = EditCommentDefaults.ActionButtonSize.dp
        val iconSize = 20.dp
        Row(modifier = modifier) {
            CompositionLocalProvider(
                LocalContentColor provides MaterialTheme.colorScheme.onSurface.stronglyWeaken(),
            ) {
                if (showReply) {
                    EditCommentDefaults.ActionButton(
                        imageVector = Icons.Outlined.ModeComment,
                        contentDescription = replyText,
                        onClick = onClickReply,
                        iconSize = iconSize,
                    )
                }

                if (showReaction) {
                    EditCommentDefaults.ActionButton(
                        imageVector = Icons.Outlined.AddReaction,
                        contentDescription = "添加表情",
                        onClick = onClickReaction,
                        iconSize = iconSize,
                        modifier = Modifier.size(size),
                    )
                }
            }
        }
    }

    @Composable
    fun ReplyList(
        replies: List<UIComment>,
        onClickUrl: (String) -> Unit,
        onClickExpand: () -> Unit,
        modifier: Modifier = Modifier,
        hiddenReplyCount: Int = 0
    ) {
        val primaryColor = MaterialTheme.colorScheme.primary

        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            shape = MaterialTheme.shapes.extraSmall,
            modifier = modifier,
        ) {
            Column {
                replies.forEach { reply ->
                    val prepended = remember(reply.content, primaryColor) {
                        reply.content.prependText(
                            prependix = "${reply.author?.nickname ?: reply.author?.id.toString()}：",
                            color = primaryColor,
                        )
                    }
                    RichText(
                        elements = prepended.elements,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        onClickUrl = onClickUrl,
                    )
                }
                if (hiddenReplyCount > 0) {
                    Text(
                        text = stringResource(Lang.comment_view_more_replies, hiddenReplyCount),
                        color = primaryColor,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                            .clickable(onClick = onClickExpand),
                    )
                }
            }
        }
    }

    @Composable
    fun EmptyPlaceholder(modifier: Modifier = Modifier) {
        Text(
            stringResource(Lang.comment_empty_title),
            modifier = modifier.padding(16.dp),
        )
    }

    // prepend text
    private fun UIRichText.prependText(prependix: String, color: Color): UIRichText = run {
        // 如果 elements 是空的则直接返回一个 annotated text
        val first = elements.firstOrNull()
            ?: return@run listOf(
                UIRichElement.AnnotatedText(
                    listOf(
                        UIRichElement.Annotated.Text(
                            prependix,
                            RichTextDefaults.FontSize,
                            color,
                        ),
                    ),
                ),
            )

        // 如果第一个 element 是 annotated text，则把 prepend 添加到其中
        if (first is UIRichElement.AnnotatedText) {
            listOf(
                first.copy(
                    slice = listOf(
                        UIRichElement.Annotated.Text(prependix, RichTextDefaults.FontSize, color),
                        *first.slice.toTypedArray(),
                    ),
                ),
                *elements.drop(1).toTypedArray(),
            )
        } else { // 如果不是就添加一个 annotated text
            listOf(
                UIRichElement.AnnotatedText(
                    listOf(
                        UIRichElement.Annotated.Text(
                            prependix,
                            RichTextDefaults.FontSize,
                            color,
                        ),
                    ),
                ),
                *elements.toTypedArray(),
            )
        }
    }.let {
        UIRichText(it)
    }

    private fun String.bangumiReactionIdOrNull(): Int? {
        if (!startsWith("bgm")) return null
        return removePrefix("bgm").toIntOrNull()
    }
}
