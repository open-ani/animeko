/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.comment

import me.him188.ani.app.data.models.episode.EpisodeComment
import me.him188.ani.app.data.models.subject.SubjectReview
import me.him188.ani.app.ui.richtext.toUIBriefText
import me.him188.ani.app.ui.richtext.toUIRichElements
import me.him188.ani.utils.bbcode.BBCode


// TODO: remove this and use BBCodeRichTextState
object CommentMapperContext {
    fun parseBBCode(code: String): UIRichText = UIRichText(BBCode.parse(code).toUIRichElements())

    fun parseBBCodeAsReply(code: String): UIRichText =
        UIRichText(listOf(BBCode.parse(code).toUIBriefText().copy(maxLine = 2)))

    fun SubjectReview.parseToUIComment() =
        UIComment(
            id = id,
            author = creator,
            content = parseBBCode(content),
            createdAt = updatedAt,
            reactions = emptyList(),
            briefReplies = emptyList(),
            replyCount = 0,
            rating = rating,
        )

    fun EpisodeComment.parseToUIComment(): UIComment {
        val comment = this
        return UIComment(
            id = comment.commentId.toLong(),
            author = comment.author,
            content = parseBBCode(comment.content),
            createdAt = comment.createdAt,
            reactions = emptyList(),
            briefReplies = comment.replies.map { reply ->
                UIComment(
                    id = reply.commentId.toLong(),
                    author = reply.author,
                    content = parseBBCode(reply.content),
                    createdAt = reply.createdAt,
                    reactions = emptyList(),
                    briefReplies = emptyList(),
                    replyCount = 0,
                    rating = null,
                )
            },
            replyCount = comment.replies.size,
            rating = null,
        )
    }

}