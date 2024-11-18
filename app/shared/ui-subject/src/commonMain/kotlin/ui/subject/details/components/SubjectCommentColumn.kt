/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.subject.details.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import me.him188.ani.app.tools.formatDateTime
import me.him188.ani.app.ui.comment.Comment
import me.him188.ani.app.ui.comment.CommentColumn
import me.him188.ani.app.ui.comment.CommentDefaults
import me.him188.ani.app.ui.comment.CommentState
import me.him188.ani.app.ui.comment.UIComment
import me.him188.ani.app.ui.foundation.layout.ConnectedScrollState
import me.him188.ani.app.ui.richtext.RichText
import me.him188.ani.app.ui.subject.rating.FiveRatingStars

@Composable
fun SubjectDetailsDefaults.SubjectCommentColumn(
    state: CommentState,
    onClickUrl: (url: String) -> Unit,
    onClickImage: (String) -> Unit,
    connectedScrollState: ConnectedScrollState,
    modifier: Modifier = Modifier,
    lazyListState: LazyListState = rememberLazyListState(),
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    Box(modifier, contentAlignment = Alignment.TopCenter) {
        CommentColumn(
            state = state,
            listState = lazyListState,
            modifier = Modifier
                .widthIn(max = BottomSheetDefaults.SheetMaxWidth)
                .fillMaxHeight(),
            contentPadding = contentPadding,
            connectedScrollState = connectedScrollState,
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
    onClickReaction: (commentId: Int, reactionId: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Comment(
        avatar = { CommentDefaults.Avatar(comment.creator?.avatarUrl) },
        primaryTitle = {
            Text(
                text = comment.creator?.nickname ?: comment.creator?.id.toString(),
                textAlign = TextAlign.Center,
            )
        },
        secondaryTitle = { Text(formatDateTime(comment.createdAt)) },
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