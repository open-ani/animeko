/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.comment

import me.him188.ani.app.data.models.comment.CommentVoteValue
import me.him188.ani.app.data.models.episode.EpisodeComment
import me.him188.ani.app.data.models.episode.EpisodeCommentSource
import me.him188.ani.app.data.models.person.PersonComment
import me.him188.ani.app.data.models.person.PersonCommentSource
import me.him188.ani.app.data.models.subject.SubjectReview
import me.him188.ani.app.data.models.subject.SubjectReviewSource
import me.him188.ani.app.ui.richtext.toUIBriefText
import me.him188.ani.app.ui.richtext.toUIRichElements
import me.him188.ani.utils.bbcode.BBCode


// TODO: remove this and use BBCodeRichTextState
object CommentMapperContext {
    private fun String.toUiCommentId(): Long = hashCode().toLong()

    fun parseBBCode(code: String): UIRichText = UIRichText(BBCode.parse(code).toUIRichElements())

    fun parseBBCodeAsReply(code: String): UIRichText =
        UIRichText(listOf(BBCode.parse(code).toUIBriefText().copy(maxLine = 2)))

    private fun CommentVoteValue.toUICommentVote(): UICommentVote = when (this) {
        CommentVoteValue.LIKE -> UICommentVote.LIKE
        CommentVoteValue.DISLIKE -> UICommentVote.DISLIKE
    }

    fun UICommentVote.toCommentVoteValue(): CommentVoteValue = when (this) {
        UICommentVote.LIKE -> CommentVoteValue.LIKE
        UICommentVote.DISLIKE -> CommentVoteValue.DISLIKE
    }

    fun SubjectReview.parseToUIComment() =
        UIComment(
            id = id,
            stableId = id.toString(),
            author = creator,
            content = parseBBCode(content),
            createdAt = updatedAt,
            reactions = emptyList(),
            briefReplies = emptyList(),
            replyCount = 0,
            rating = rating,
            source = when (source) {
                SubjectReviewSource.ANI -> UICommentSource.ANI
                SubjectReviewSource.BANGUMI -> UICommentSource.BANGUMI
            },
            sourceCommentId = reviewId,
            canReply = false,
            likeCount = likeCount,
            selfVote = selfVote?.toUICommentVote(),
            rawContent = content,
        )

    fun EpisodeComment.parseToUIComment(): UIComment {
        val comment = this
        return UIComment(
            id = comment.stableId.toUiCommentId(),
            stableId = comment.stableId,
            author = comment.author,
            content = parseBBCode(comment.content),
            createdAt = comment.createdAt,
            reactions = comment.reactions.map { UICommentReaction(it.value, it.count, it.selected) },
            briefReplies = comment.replies.map { reply ->
                UIComment(
                    id = reply.stableId.toUiCommentId(),
                    stableId = reply.stableId,
                    author = reply.author,
                    content = parseBBCode(reply.content),
                    createdAt = reply.createdAt,
                    reactions = reply.reactions.map { UICommentReaction(it.value, it.count, it.selected) },
                    briefReplies = emptyList(),
                    replyCount = 0,
                    rating = null,
                    source = when (reply.source) {
                        EpisodeCommentSource.ANI -> UICommentSource.ANI
                        EpisodeCommentSource.BANGUMI -> UICommentSource.BANGUMI
                    },
                    sourceCommentId = reply.sourceCommentId,
                    canReply = reply.canReply,
                    rawContent = reply.content,
                    episodeId = reply.episodeId,
                )
            },
            replyCount = comment.replyCount,
            rating = null,
            source = when (comment.source) {
                EpisodeCommentSource.ANI -> UICommentSource.ANI
                EpisodeCommentSource.BANGUMI -> UICommentSource.BANGUMI
            },
            sourceCommentId = comment.sourceCommentId,
            canReply = comment.canReply,
            likeCount = comment.likeCount,
            selfVote = comment.selfVote?.toUICommentVote(),
            rawContent = comment.content,
            episodeId = comment.episodeId,
        )
    }

    /**
     * 人物/角色评论 (无评分). 与 [EpisodeComment.parseToUIComment] 的区别只是没有 `episodeId`.
     */
    fun PersonComment.parseToUIComment(): UIComment {
        val comment = this
        return UIComment(
            id = comment.stableId.toUiCommentId(),
            stableId = comment.stableId,
            author = comment.author,
            content = parseBBCode(comment.content),
            createdAt = comment.createdAt,
            reactions = comment.reactions.map { UICommentReaction(it.value, it.count, it.selected) },
            briefReplies = comment.replies.map { reply ->
                UIComment(
                    id = reply.stableId.toUiCommentId(),
                    stableId = reply.stableId,
                    author = reply.author,
                    content = parseBBCode(reply.content),
                    createdAt = reply.createdAt,
                    reactions = reply.reactions.map { UICommentReaction(it.value, it.count, it.selected) },
                    briefReplies = emptyList(),
                    replyCount = 0,
                    rating = null,
                    source = reply.source.toUICommentSource(),
                    sourceCommentId = reply.sourceCommentId,
                    canReply = reply.canReply,
                    rawContent = reply.content,
                )
            },
            replyCount = comment.replyCount,
            rating = null,
            source = comment.source.toUICommentSource(),
            sourceCommentId = comment.sourceCommentId,
            canReply = comment.canReply,
            likeCount = comment.likeCount,
            selfVote = comment.selfVote?.toUICommentVote(),
            rawContent = comment.content,
        )
    }

    private fun PersonCommentSource.toUICommentSource(): UICommentSource = when (this) {
        PersonCommentSource.ANI -> UICommentSource.ANI
        PersonCommentSource.BANGUMI -> UICommentSource.BANGUMI
    }
}
