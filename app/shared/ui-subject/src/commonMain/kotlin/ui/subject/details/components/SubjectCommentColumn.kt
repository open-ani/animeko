/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import me.him188.ani.app.ui.comment.UIComment
import me.him188.ani.app.ui.comment.UICommentSource
import me.him188.ani.app.ui.comment.generateUiComment
import me.him188.ani.app.ui.comment.rememberTestCommentState
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.foundation.layout.ConnectedScrollState
import me.him188.ani.app.ui.foundation.layout.rememberConnectedScrollState
import me.him188.ani.app.ui.foundation.widgets.LocalToaster
import me.him188.ani.app.ui.foundation.widgets.showLoadError
import me.him188.ani.utils.platform.annotations.TestOnly

/**
 * 条目评价列表. 也被人物/角色评论复用 (人物评论无评分; Ani 源的人物评论可回复/贴纸回应).
 *
 * @param onOpenOriginal 在来源平台打开原始评论, 只对 [UICommentSource.BANGUMI] 来源的评论生效.
 * @param reportState 举报状态. `null` 时隐藏举报入口. 弹层与结果提示由页面级的
 *   [me.him188.ani.app.ui.comment.CommentReportHost] 渲染, 调用方需在 sheet 外层挂一个.
 * @param onClickReply 点击评论回复, 只对可回复 ([UIComment.canReply]) 的 [UICommentSource.ANI] 评论生效. `null` 表示不支持回复 (条目评价).
 * @param onToggleReaction 贴纸回应, 只对 [UICommentSource.ANI] 评论生效. `null` 时隐藏贴纸入口.
 */
@Composable
fun SubjectDetailsDefaults.SubjectCommentColumn(
    state: CommentState,
    onClickUrl: (url: String) -> Unit,
    onClickImage: (String) -> Unit,
    modifier: Modifier = Modifier,
    reportState: CommentReportState? = null,
    onOpenOriginal: ((UIComment) -> Unit)? = null,
    onClickReply: ((UIComment) -> Unit)? = null,
    onToggleReaction: ((UIComment, String) -> Unit)? = null,
    showRating: Boolean = true,
    connectedScrollState: ConnectedScrollState? = null,
    gridState: LazyGridState = rememberLazyGridState(),
    contentPadding: PaddingValues = PaddingValues(0.dp),
    pullToRefreshEnabled: Boolean = true,
) {
    val toaster = LocalToaster.current
    LaunchedEffect(state) {
        state.actionSubmitFailures.collect { error ->
            toaster.showLoadError(LoadError.fromException(error))
        }
    }
    Box(modifier, contentAlignment = Alignment.TopCenter) {
        val items = state.list.collectAsLazyPagingItemsWithLifecycle()
        CommentOverlayCleanupEffect(state, items)
        CommentColumn(
            items,
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentWidth(align = Alignment.CenterHorizontally)
                .widthIn(max = SubjectDetailsDefaults.MaximumContentWidth)
                .fillMaxHeight(),
            contentPadding = contentPadding,
            state = gridState,
            connectedScrollState = connectedScrollState,
            pullToRefreshEnabled = pullToRefreshEnabled,
        ) { _, comment ->
            val commentWithOverlay = state.withOverlay(comment)
            CommentItem(
                comment = commentWithOverlay,
                onClickUrl = onClickUrl,
                onClickImage = onClickImage,
                showRating = showRating,
                onClickReply = onClickReply,
                onToggleVote = { c, vote -> state.toggleVote(c, vote) },
                onToggleReaction = if (commentWithOverlay.source == UICommentSource.ANI) onToggleReaction else null,
                menu = CommentMenuHandlers(
                    onOpenOriginal = if (commentWithOverlay.source == UICommentSource.BANGUMI) {
                        onOpenOriginal
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

@OptIn(TestOnly::class)
@Preview
@Composable
private fun PreviewSubjectCommentColumn() {
    ProvideCompositionLocalsForPreview {
        Surface {
            SubjectDetailsDefaults.SubjectCommentColumn(
                state = rememberTestCommentState(generateUiComment(4)),
                onClickUrl = { },
                onClickImage = {},
                connectedScrollState = rememberConnectedScrollState(),
            )
        }
    }
}
