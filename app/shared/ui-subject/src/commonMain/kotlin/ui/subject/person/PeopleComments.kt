/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.subject.person

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import me.him188.ani.app.data.models.person.PersonCommentTarget
import me.him188.ani.app.domain.comment.CommentContext
import me.him188.ani.app.domain.foundation.LoadError
import me.him188.ani.app.ui.comment.CommentEditorState
import me.him188.ani.app.ui.comment.CommentReportHost
import me.him188.ani.app.ui.comment.CommentReportState
import me.him188.ani.app.ui.comment.CommentState
import me.him188.ani.app.ui.foundation.widgets.LocalToaster
import me.him188.ani.app.ui.foundation.widgets.showLoadError

/**
 * 人物/角色页评论区的全部状态: 列表 ([commentState]), 举报 ([reportState]), 编辑器 ([editorState]).
 * 由 [PeopleDetailsViewModel] 创建, 页面、侧边预览与全量评论 sheet 共用同一份.
 *
 * 与剧集评论一个路数: Ani 自有评论可回复/贴纸回应/点赞/举报, Bangumi 评论只读, 可在 Bangumi 打开.
 */
@Stable
class PeopleCommentsState(
    val target: PersonCommentTarget,
    val commentState: CommentState,
    val reportState: CommentReportState,
    val editorState: CommentEditorState,
    /** 评论来源页 (Bangumi 吐槽箱), 用于菜单的 "在 Bangumi 打开". */
    val originalCommentsUrl: String,
    private val onRefresh: () -> Unit,
) {
    /**
     * 重新拉取评论列表与评论数. 发送成功后调用, 让新评论立刻出现在列表顶部.
     */
    fun refresh() = onRefresh()

    /** 开始写一条新评论. */
    fun startNewComment() = editorState.startEdit(CommentContext.PersonComment(target))

    /** 开始回复 [commentId] (Ani 评论的 `sourceCommentId`). */
    fun startReply(commentId: String) = editorState.startEdit(CommentContext.PersonCommentReply(target, commentId))
}

/**
 * 评论区的页面级宿主, 与评论预览区块一起挂在页面上 (不随 sheet 关闭而消失):
 * - 举报弹层与提交结果提示 ([CommentReportHost]);
 * - Bangumi 评论拉取失败的一次性提示 (列表照常显示 Ani 评论, 免得看起来像"没有评论");
 * - 全量评论 sheet ([PersonCommentsSheet], 内含写评论/回复的编辑器).
 *
 * 每个页面 (或侧边预览) 只应挂一个, 否则举报结果会被多个宿主各收一半.
 */
@Composable
internal fun PeopleCommentsHost(
    comments: PeopleCommentsState,
    showAllComments: Boolean,
    onDismissAllComments: () -> Unit,
) {
    val toaster = LocalToaster.current
    LaunchedEffect(comments) {
        comments.commentState.commentLoadFailures.collect { error ->
            toaster.showLoadError(LoadError.fromException(error))
        }
    }
    CommentReportHost(comments.reportState)
    if (showAllComments) {
        PersonCommentsSheet(comments, onDismissRequest = onDismissAllComments)
    }
}
