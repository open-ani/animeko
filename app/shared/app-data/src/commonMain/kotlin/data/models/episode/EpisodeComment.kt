/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.models.episode

import me.him188.ani.app.data.models.UserInfo
import me.him188.ani.app.data.models.comment.CommentVoteValue

enum class EpisodeCommentSource {
    ANI,
    BANGUMI,
}

data class EpisodeComment(
    val stableId: String,
    val source: EpisodeCommentSource,
    val sourceCommentId: String,
    val commentId: String,
    val episodeId: Long,

    /**
     * Timestamp, millis
     */
    val createdAt: Long,
    val content: String,
    val author: UserInfo?,
    val reactions: List<EpisodeCommentReaction> = emptyList(),
    val replies: List<EpisodeComment> = listOf(),
    val canReply: Boolean = false,
    /**
     * 总回复数. [replies] 只是简要回复, 可能少于此数.
     */
    val replyCount: Int = replies.size,
    /**
     * 点赞总数. [EpisodeCommentSource.BANGUMI] 来源的评论恒为 `0`.
     */
    val likeCount: Int = 0,
    /**
     * 当前登录用户对这条评论的投票, 未投票或未登录时为 `null`.
     */
    val selfVote: CommentVoteValue? = null,
)

data class EpisodeCommentReaction(
    val value: String,
    val count: Int,
    val selected: Boolean,
)
