/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.him188.ani.app.data.models.comment.CommentReportReason
import me.him188.ani.app.data.models.comment.CommentReportSource
import me.him188.ani.app.data.models.comment.CommentReportTargetType
import me.him188.ani.app.data.repository.RepositoryException
import me.him188.ani.client.apis.CommentsAniApi
import me.him188.ani.client.models.AniCommentReportReason
import me.him188.ani.client.models.AniCommentReportSource
import me.him188.ani.client.models.AniCommentReportTargetType
import me.him188.ani.client.models.AniCreateCommentReportRequest
import me.him188.ani.utils.coroutines.IO_
import me.him188.ani.utils.ktor.ApiInvoker
import kotlin.coroutines.CoroutineContext

open class AniCommentReportService(
    private val commentsApi: ApiInvoker<CommentsAniApi>,
    private val ioDispatcher: CoroutineContext = Dispatchers.IO_,
) {
    /**
     * 举报一条评论. 任意来源 (Animeko / Bangumi) 的评论都可以举报.
     * 同一用户重复举报同一条评论会覆盖之前的举报内容, 不会报错.
     *
     * @param contentSnapshot 被举报评论的快照 (作者昵称 + 评论原文).
     *        Bangumi 来源的评论不存在于服务端, 审核依赖这个快照.
     * @param commentAuthorId 被举报评论的作者 ID, Animeko 源为服务端用户 UUID,
     *        Bangumi 源为 bangumi 侧用户 id. 作者未知时为 `null`.
     */
    open suspend fun createReport(
        targetType: CommentReportTargetType,
        source: CommentReportSource,
        targetId: String,
        reason: CommentReportReason,
        commentAuthorId: String? = null,
        detail: String? = null,
        contentSnapshot: String? = null,
        subjectId: Long? = null,
        episodeId: Long? = null,
    ) = withContext(ioDispatcher) {
        try {
            commentsApi.invoke {
                createCommentReport(
                    AniCreateCommentReportRequest(
                        targetType = targetType.toAniCommentReportTargetType(),
                        source = source.toAniCommentReportSource(),
                        targetId = targetId,
                        commentAuthorId = commentAuthorId,
                        reason = reason.toAniCommentReportReason(),
                        detail = detail,
                        contentSnapshot = contentSnapshot,
                        subjectId = subjectId,
                        episodeId = episodeId,
                    ),
                ).body()
            }
        } catch (e: Exception) {
            throw RepositoryException.wrapOrThrowCancellation(e)
        }
    }
}

private fun CommentReportTargetType.toAniCommentReportTargetType(): AniCommentReportTargetType = when (this) {
    CommentReportTargetType.EPISODE_COMMENT -> AniCommentReportTargetType.EPISODE_COMMENT
    CommentReportTargetType.SUBJECT_REVIEW -> AniCommentReportTargetType.SUBJECT_REVIEW
    CommentReportTargetType.PERSON_COMMENT -> AniCommentReportTargetType.PERSON_COMMENT
    CommentReportTargetType.CHARACTER_COMMENT -> AniCommentReportTargetType.CHARACTER_COMMENT
}

private fun CommentReportSource.toAniCommentReportSource(): AniCommentReportSource = when (this) {
    CommentReportSource.ANIMEKO -> AniCommentReportSource.ANIMEKO
    CommentReportSource.BANGUMI -> AniCommentReportSource.BANGUMI
}

private fun CommentReportReason.toAniCommentReportReason(): AniCommentReportReason = when (this) {
    CommentReportReason.SPAM -> AniCommentReportReason.SPAM
    CommentReportReason.HARASSMENT -> AniCommentReportReason.HARASSMENT
    CommentReportReason.SPOILER -> AniCommentReportReason.SPOILER
    CommentReportReason.NSFW -> AniCommentReportReason.NSFW
    CommentReportReason.ILLEGAL -> AniCommentReportReason.ILLEGAL
    CommentReportReason.OTHER -> AniCommentReportReason.OTHER
}
