/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.comment

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ModeComment
import androidx.compose.material.icons.rounded.Face
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
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
import me.him188.ani.app.ui.richtext.RichText
import me.him188.ani.app.ui.richtext.RichTextDefaults
import me.him188.ani.app.ui.richtext.UIRichElement
import org.jetbrains.compose.resources.painterResource

object CommentDefaults {
    @Composable
    fun Avatar(url: String?) {
        AvatarImage(
            url = url,
            modifier = Modifier.size(36.dp),
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
                .minimumInteractiveComponentSize()
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
                val reactionDrawableRes = BangumiCommentSticker[reaction.id]

                if (previewing || reactionDrawableRes == null) Icon(
                    imageVector = Icons.Rounded.Face,
                    modifier = Modifier.padding(end = 4.dp).size(24.dp),
                    contentDescription = null,
                ) else Icon(
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
        onClickItem: (reactionId: Int) -> Unit,
        modifier: Modifier = Modifier,
    ) {
        FlowRow(
            modifier = modifier,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            list.forEach {
                Reaction(
                    reaction = it,
                    modifier = Modifier.padding(vertical = 6.dp),
                    onClick = { onClickItem(it.id) },
                )
            }
        }
    }

    @Composable
    fun ActionRow(
        onClickReply: () -> Unit,
        modifier: Modifier = Modifier,
        onClickReaction: () -> Unit,
        onClickBlock: () -> Unit,
        onClickReport: () -> Unit
    ) {
        val size = EditCommentDefaults.ActionButtonSize.dp
        val iconSize = 20.dp
        var actionRowExpanded by rememberSaveable { mutableStateOf(false) }
        val expandableActionWidth by animateDpAsState(if (actionRowExpanded) size else 0.dp)

        Row(modifier = modifier) {
            CompositionLocalProvider(
                LocalContentColor provides MaterialTheme.colorScheme.onSurface.stronglyWeaken(),
            ) {
                EditCommentDefaults.ActionButton(
                    imageVector = Icons.Outlined.ModeComment,
                    contentDescription = "回复评论",
                    onClick = onClickReply,
                    iconSize = iconSize,
                )

                /*if (actionRowExpanded) {
                    EditCommentDefaults.ActionButton(
                        imageVector = Icons.Outlined.AddReaction,
                        contentDescription = "添加表情",
                        onClick = onClickReaction,
                        iconSize = iconSize,
                        modifier = Modifier.size(height = size, width = expandableActionWidth),
                    )
                    EditCommentDefaults.ActionButton(
                        imageVector = Icons.Outlined.HeartBroken,
                        contentDescription = "拉黑用户",
                        onClick = onClickBlock,
                        iconSize = iconSize,
                        modifier = Modifier.size(height = size, width = expandableActionWidth),
                    )
                    EditCommentDefaults.ActionButton(
                        imageVector = Icons.Outlined.Report,
                        contentDescription = "举报用户",
                        onClick = onClickReport,
                        iconSize = iconSize,
                        modifier = Modifier.size(height = size, width = expandableActionWidth),
                    )
                }

                // 最后一个按钮不要有 ripple effect，因为有动画，看起来比较奇怪
                EditCommentDefaults.ActionButton(
                    imageVector = Icons.Outlined.MoreHoriz,
                    contentDescription = "展开更多评论功能",
                    iconSize = iconSize,
                    onClick = { actionRowExpanded = true },
                    modifier = Modifier.size(height = size, width = size - expandableActionWidth),
                    hasIndication = false,
                )*/
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
                        text = "查看更多 $hiddenReplyCount 条回复>",
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
}