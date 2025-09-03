/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.subject.details.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
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
import me.him188.ani.app.ui.foundation.layout.ConnectedScrollState
import me.him188.ani.app.ui.rating.FiveRatingStars
import me.him188.ani.app.ui.richtext.RichText

@Composable
fun SubjectDetailsDefaults.SubjectCommentColumn(
    state: CommentState,
    onClickUrl: (url: String) -> Unit,
    onClickImage: (String) -> Unit,
    connectedScrollState: ConnectedScrollState,
    modifier: Modifier = Modifier,
    gridState: LazyGridState = rememberLazyGridState(),
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    Box(modifier, contentAlignment = Alignment.TopCenter) {
        CommentColumn(
            state.list.collectAsLazyPagingItemsWithLifecycle(),
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding,
            state = gridState,
            connectedScrollState = connectedScrollState,
            maxColumnWidth = MaximumContentWidth,
        ) { _, comment ->
            SubjectComment(
                comment = comment,
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                onClickImage = onClickImage,
                onClickUrl = onClickUrl,
                onClickReaction = { commentId, reactionId ->
                    state.submitReaction(commentId, reactionId)
                },
            )
        }
    }
}

@Composable
fun SubjectComment(
    comment: UIComment,
    onClickUrl: (String) -> Unit,
    onClickImage: (String) -> Unit,
    onClickReaction: (commentId: Long, reactionId: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val uriHandler = LocalUriHandler.current
    val interactionSource = remember { MutableInteractionSource() }
    val authorModifier = Modifier.clickable(
        enabled = comment.author != null,
        indication = ripple(),
        interactionSource = interactionSource,
        onClick = {
            comment.author?.id?.let {
                val url = "https://bgm.tv/user/" + comment.author?.id.toString()
                uriHandler.openUri(url)
            }
        },
    )

    Comment(
        avatar = { CommentDefaults.Avatar(comment.author?.avatarUrl, authorModifier) },
        primaryTitle = {
            Text(
                text = comment.author?.nickname ?: comment.author?.id.toString(),
                textAlign = TextAlign.Center,
                overflow = TextOverflow.Ellipsis,
                modifier = authorModifier,
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
        rhsTitle = {
            val rating = comment.rating
            if (rating != null && rating > 0) {
                FiveRatingStars(rating)
            }
        },
        reactionRow = {
            CommentDefaults.ReactionRow(
                comment.reactions,
                onClickItem = { onClickReaction(comment.id, it) },
            )
        },
    )
}