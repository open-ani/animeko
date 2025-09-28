/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.subject.episode.comments

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.paging.compose.collectAsLazyPagingItemsWithLifecycle
import me.him188.ani.app.tools.formatDateTime
import me.him188.ani.app.ui.comment.Comment
import me.him188.ani.app.ui.comment.CommentColumn
import me.him188.ani.app.ui.comment.CommentDefaults
import me.him188.ani.app.ui.comment.CommentState
import me.him188.ani.app.ui.comment.UIComment
import me.him188.ani.app.ui.foundation.LocalImageViewerHandler
import me.him188.ani.app.ui.richtext.RichText

@Composable
fun EpisodeCommentColumn(
    state: CommentState,
    onClickReply: (commentId: Long) -> Unit,
    onNewCommentClick: () -> Unit,
    onClickUrl: (url: String) -> Unit,
    modifier: Modifier = Modifier,
    gridState: LazyGridState = rememberLazyGridState(),
) {
    val imageViewer = LocalImageViewerHandler.current

    Scaffold(
        modifier,
        // TODO: 2025.08.15 暂时去掉发评论功能, 等 ani server 做好转发再上
        /*floatingActionButton = {
            ExtendedFloatingActionButton(
                text = { Text("写评论") },
                icon = {
                    Icon(Icons.Rounded.AddComment, null)
                },
                onNewCommentClick,
                expanded = !gridState.canScrollBackward,
            )
        },*/
    ) { _ ->
        CommentColumn(
            state.list.collectAsLazyPagingItemsWithLifecycle(),
            state = gridState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 72.dp), // 允许滚动到 FAB 上面
        ) { _, comment ->
            EpisodeComment(
                comment = comment,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp)
                    // 如果没有回复则 ActionBar 就是最后一个元素，减小一下 bottom padding 以看起来舒服
                    .padding(top = 12.dp, bottom = if (comment.replyCount != 0) 12.dp else 4.dp),
                onClickImage = { imageViewer.viewImage(it) },
                onActionReply = { onClickReply(comment.id) },
                onClickUrl = onClickUrl,
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
    modifier: Modifier = Modifier
) {
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
                onClickItem = { },
            )
        },
        actionRow = {
            CommentDefaults.ActionRow(
                onClickReply = onActionReply,
                onClickReaction = {},
                onClickBlock = {},
                onClickReport = {},
            )
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
