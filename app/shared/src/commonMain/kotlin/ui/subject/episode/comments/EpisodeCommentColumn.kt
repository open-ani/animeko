/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

@file:OptIn(TestOnly::class)

package me.him188.ani.app.ui.subject.episode.comments

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddComment
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.paging.compose.collectAsLazyPagingItemsWithLifecycle
import me.him188.ani.app.tools.formatDateTime
import me.him188.ani.app.ui.comment.Comment
import me.him188.ani.app.ui.comment.CommentColumn
import me.him188.ani.app.ui.comment.CommentDefaults
import me.him188.ani.app.ui.comment.CommentState
import me.him188.ani.app.ui.comment.UIComment
import me.him188.ani.app.ui.comment.UICommentSource
import me.him188.ani.app.ui.comment.UIRichText
import me.him188.ani.app.ui.comment.generateUiComment
import me.him188.ani.app.ui.comment.rememberTestCommentState
import me.him188.ani.app.domain.foundation.LoadError
import me.him188.ani.app.ui.foundation.LocalImageViewerHandler
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.foundation.widgets.LocalToaster
import me.him188.ani.app.ui.foundation.widgets.showLoadError
import me.him188.ani.app.ui.foundation.layout.plus
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.comment_send_comment
import me.him188.ani.app.ui.richtext.RichText
import me.him188.ani.app.ui.richtext.UIRichElement
import me.him188.ani.utils.platform.annotations.TestOnly
import org.jetbrains.compose.resources.stringResource

@Composable
fun EpisodeCommentColumn(
    state: CommentState,
    onClickReply: (commentId: String) -> Unit,
    onNewCommentClick: () -> Unit,
    onClickUrl: (url: String) -> Unit,
    modifier: Modifier = Modifier,
    gridState: LazyGridState = rememberLazyGridState(),
) {
    val imageViewer = LocalImageViewerHandler.current
    val writeCommentText = stringResource(Lang.comment_send_comment)
    val toaster = LocalToaster.current
    LaunchedEffect(state) {
        state.reactionSubmitFailures.collect { error ->
            toaster.showLoadError(LoadError.fromException(error))
        }
    }

    Scaffold(
        modifier,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                text = { Text(writeCommentText) },
                icon = {
                    Icon(Icons.Rounded.AddComment, null)
                },
                onClick = onNewCommentClick,
                expanded = !gridState.canScrollBackward,
            )
        },
    ) { _ ->
        CommentColumn(
            state.list.collectAsLazyPagingItemsWithLifecycle(),
            state = gridState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 72.dp)
                .plus(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom).asPaddingValues()), // 允许滚动到 FAB 上面
        ) { _, comment ->
            val commentWithOverlay = state.withReactionOverlay(comment)
            EpisodeComment(
                comment = commentWithOverlay,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp)
                    // 如果没有回复则 ActionBar 就是最后一个元素，减小一下 bottom padding 以看起来舒服
                    .padding(top = 12.dp, bottom = if (commentWithOverlay.replyCount != 0) 12.dp else 4.dp),
                onClickImage = { imageViewer.viewImage(it) },
                onActionReply = { onClickReply(commentWithOverlay.sourceCommentId) },
                onClickUrl = onClickUrl,
                onClickReaction = { state.submitReaction(commentWithOverlay, it) },
            )
        }
    }
}

private const val LOREM_IPSUM =
    "Ipsum dolor sit amet, consectetur adipiscing elit. Integer nec odio. Praesent libero. Sed cursus ante dapibus diam. Sed nisi. Nulla quis sem at nibh elementum imperdiet."

@Composable
fun EpisodeComment(
    comment: UIComment,
    onClickUrl: (String) -> Unit,
    onClickImage: (String) -> Unit,
    onActionReply: () -> Unit,
    onClickReaction: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var showReactionPicker by remember(comment.stableId) { mutableStateOf(false) }
    val canAddReaction = comment.source == UICommentSource.ANI

    Comment(
        avatar = { CommentDefaults.Avatar(comment.author?.avatarUrl) },
        primaryTitle = {
            Text(
                text = comment.author?.nickname ?: comment.author?.id.toString(),
                textAlign = TextAlign.Center,
                overflow = TextOverflow.Ellipsis,
            )
        },
        secondaryTitle = {
            Text(
                formatDateTime(comment.createdAt),
                overflow = TextOverflow.Ellipsis,
            )
        },
        rhsTitle = {
            Text(
                if (comment.source == UICommentSource.ANI) "Animeko" else "Bangumi",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        content = {
            RichText(
                elements = comment.content.elements,
                modifier = Modifier.fillMaxWidth(),
                onClickUrl = onClickUrl,
                onClickImage = onClickImage,
            )
        },
        modifier = modifier,
        reactionRow = {
            CommentDefaults.ReactionRow(
                comment.reactions,
                onClickItem = onClickReaction,
            )
        },
        actionRow = {
            CommentDefaults.ActionRow(
                onClickReply = onActionReply,
                showReply = false,
                showReaction = canAddReaction,
                onClickReaction = { showReactionPicker = !showReactionPicker },
                onClickBlock = {},
                onClickReport = {},
            )
            if (canAddReaction && showReactionPicker) {
                Popup(
                    onDismissRequest = { showReactionPicker = false },
                    properties = PopupProperties(focusable = true),
                ) {
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        tonalElevation = 6.dp,
                        shadowElevation = 6.dp,
                        modifier = Modifier
                            .width(216.dp)
                            .heightIn(max = 280.dp),
                    ) {
                        CommentDefaults.ReactionPicker(
                            onClickItem = {
                                showReactionPicker = false
                                onClickReaction(it)
                            },
                        )
                    }
                }
            }
        },
        reply = if (comment.briefReplies.isNotEmpty()) {
            {
                CommentDefaults.ReplyList(
                    replies = comment.briefReplies,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    hiddenReplyCount = 0, //comment.replyCount - comment.briefReplies.size,
                    onClickUrl = { },
                    onClickExpand = { },
                )
            }
        } else null,
    )
}

@Preview
@Composable
private fun PreviewEpisodeComment() {
    ProvideCompositionLocalsForPreview {
        EpisodeComment(
            comment = remember {
                generateUiComment(
                    size = 1,
                    content = UIRichText(
                        listOf(
                            UIRichElement.AnnotatedText(
                                listOf(
                                    UIRichElement.Annotated.Text(LOREM_IPSUM),
                                    UIRichElement.Annotated.Text("masked text", mask = true),
                                ),
                            ),
                        ),
                    ),
                ).single()
            },
            modifier = Modifier.fillMaxWidth(),
            onActionReply = { },
            onClickImage = { },
            onClickUrl = { },
            onClickReaction = { },
        )
    }
}

@Preview
@Composable
private fun PreviewEpisodeCommentColumn() {
    ProvideCompositionLocalsForPreview {
        EpisodeCommentColumn(
            state = rememberTestCommentState(commentList = generateUiComment(4)),
            onClickReply = { },
            onNewCommentClick = { },
            onClickUrl = { },
        )
    }
}
