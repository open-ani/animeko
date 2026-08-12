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
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddComment
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.paging.compose.collectAsLazyPagingItemsWithLifecycle
import me.him188.ani.app.domain.foundation.LoadError
import me.him188.ani.app.ui.comment.CommentColumn
import me.him188.ani.app.ui.comment.CommentItem
import me.him188.ani.app.ui.comment.CommentMenuHandlers
import me.him188.ani.app.ui.comment.CommentOverlayCleanupEffect
import me.him188.ani.app.ui.comment.CommentReportState
import me.him188.ani.app.ui.comment.CommentState
import me.him188.ani.app.ui.comment.UICommentSource
import me.him188.ani.app.ui.comment.generateUiComment
import me.him188.ani.app.ui.comment.rememberTestCommentState
import me.him188.ani.app.ui.foundation.LocalImageViewerHandler
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.foundation.layout.plus
import me.him188.ani.app.ui.foundation.widgets.LocalToaster
import me.him188.ani.app.ui.foundation.widgets.showLoadError
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.comment_send_comment
import me.him188.ani.utils.platform.annotations.TestOnly
import org.jetbrains.compose.resources.stringResource

@Composable
fun EpisodeCommentColumn(
    state: CommentState,
    episodeId: Int,
    onClickReply: (commentId: String) -> Unit,
    onNewCommentClick: () -> Unit,
    onClickUrl: (url: String) -> Unit,
    modifier: Modifier = Modifier,
    reportState: CommentReportState? = null,
    gridState: LazyGridState = rememberLazyGridState(),
) {
    val imageViewer = LocalImageViewerHandler.current
    val uriHandler = LocalUriHandler.current
    val writeCommentText = stringResource(Lang.comment_send_comment)
    val toaster = LocalToaster.current
    LaunchedEffect(state) {
        state.actionSubmitFailures.collect { error ->
            toaster.showLoadError(LoadError.fromException(error))
        }
    }
    LaunchedEffect(state) {
        state.commentLoadFailures.collect { error ->
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
        val items = state.list.collectAsLazyPagingItemsWithLifecycle()
        CommentOverlayCleanupEffect(state, items)
        CommentColumn(
            items,
            state = gridState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 72.dp)
                .plus(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom).asPaddingValues()), // 允许滚动到 FAB 上面
        ) { _, comment ->
            val commentWithOverlay = state.withOverlay(comment)
            CommentItem(
                comment = commentWithOverlay,
                onClickUrl = onClickUrl,
                onClickImage = { imageViewer.viewImage(it) },
                onClickReply = { onClickReply(it.sourceCommentId) },
                onToggleVote = { c, vote -> state.toggleVote(c, vote) },
                onToggleReaction = if (commentWithOverlay.source == UICommentSource.ANI) {
                    { c, value -> state.submitReaction(c, value) }
                } else null,
                menu = CommentMenuHandlers(
                    onOpenOriginal = if (commentWithOverlay.source == UICommentSource.BANGUMI) {
                        { uriHandler.openUri("https://bgm.tv/ep/$episodeId") }
                    } else null,
                    // 只支持举报 Animeko 自有评论
                    onReport = if (commentWithOverlay.source == UICommentSource.ANI) {
                        reportState?.let { report -> { report.show(it) } }
                    } else null,
                ),
            )
        }
    }
}

@Preview
@Composable
private fun PreviewEpisodeCommentColumn() {
    ProvideCompositionLocalsForPreview {
        EpisodeCommentColumn(
            state = rememberTestCommentState(commentList = generateUiComment(4)),
            episodeId = 1,
            onClickReply = { },
            onNewCommentClick = { },
            onClickUrl = { },
        )
    }
}
