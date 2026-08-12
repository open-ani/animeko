/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.comment

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import me.him188.ani.app.data.models.comment.CommentReportSource
import me.him188.ani.app.domain.foundation.LoadError
import me.him188.ani.app.ui.foundation.widgets.LocalToaster
import me.him188.ani.app.ui.foundation.widgets.showLoadError
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.comment_report_submitted
import me.him188.ani.app.data.models.comment.CommentReportReason as DataCommentReportReason
import org.jetbrains.compose.resources.stringResource

/**
 * 举报评论的弹层状态: 记录当前要举报的评论, 并把提交结果透出给 UI 提示.
 *
 * 由 [CommentReportHost] 消费; 各评论列表把 [show] 挂到菜单的举报入口上.
 */
@Stable
class CommentReportState(
    private val onSubmitReport: suspend (comment: UIComment, reason: CommentReportReason, detail: String) -> Unit,
    private val backgroundScope: CoroutineScope,
) {
    /**
     * 当前正在举报的评论, `null` 表示弹层关闭.
     */
    var target: UIComment? by mutableStateOf(null)
        private set

    private val submitResultChannel = Channel<Result<Unit>>(Channel.BUFFERED)

    /**
     * 每次提交完成后发出一个结果.
     */
    val submitResults: Flow<Result<Unit>> = submitResultChannel.receiveAsFlow()

    fun show(comment: UIComment) {
        target = comment
    }

    fun dismiss() {
        target = null
    }

    fun submit(comment: UIComment, reason: CommentReportReason, detail: String) {
        backgroundScope.launch {
            try {
                onSubmitReport(comment, reason, detail)
                submitResultChannel.trySend(Result.success(Unit))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                submitResultChannel.trySend(Result.failure(e))
            }
        }
    }
}

/**
 * 展示 [CommentReportState] 对应的举报弹层, 并在提交后 toast 结果.
 * 放在评论列表所在的 composable 中即可.
 */
@Composable
fun CommentReportHost(state: CommentReportState) {
    val toaster = LocalToaster.current
    val submittedText = stringResource(Lang.comment_report_submitted)
    LaunchedEffect(state) {
        state.submitResults.collect { result ->
            result.fold(
                onSuccess = { toaster.toast(submittedText) },
                onFailure = { toaster.showLoadError(LoadError.fromException(it)) },
            )
        }
    }
    state.target?.let { target ->
        CommentReportSheet(
            snapshotText = remember(target) { target.reportSnapshotText() },
            onSubmit = { reason, detail ->
                state.submit(target, reason, detail)
                state.dismiss()
            },
            onDismissRequest = { state.dismiss() },
        )
    }
}

/**
 * 举报快照: 作者昵称 + 评论原文. Bangumi 来源的评论不存在于服务端, 审核依赖这个快照.
 */
fun UIComment.reportSnapshotText(): String {
    val authorName = author?.nickname ?: author?.id ?: ""
    val text = rawContent ?: content.toPlainText()
    return if (authorName.isEmpty()) text else "$authorName：$text"
}

fun CommentReportReason.toDataReason(): DataCommentReportReason = when (this) {
    CommentReportReason.SPAM -> DataCommentReportReason.SPAM
    CommentReportReason.HARASSMENT -> DataCommentReportReason.HARASSMENT
    CommentReportReason.SPOILER -> DataCommentReportReason.SPOILER
    CommentReportReason.NSFW -> DataCommentReportReason.NSFW
    CommentReportReason.ILLEGAL -> DataCommentReportReason.ILLEGAL
    CommentReportReason.OTHER -> DataCommentReportReason.OTHER
}

fun UICommentSource.toCommentReportSource(): CommentReportSource = when (this) {
    UICommentSource.ANI -> CommentReportSource.ANIMEKO
    UICommentSource.BANGUMI -> CommentReportSource.BANGUMI
}
