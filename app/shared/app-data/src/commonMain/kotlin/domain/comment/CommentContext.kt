/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.comment

import androidx.compose.runtime.Immutable
import me.him188.ani.app.data.models.person.PersonCommentTarget

/**
 * 评论发送的对象
 */
@Immutable
sealed interface CommentContext {
    /**
     * 人物 (声优/制作人员) 或角色的评论 (吐槽箱)
     */
    data class PersonComment(val target: PersonCommentTarget) : CommentContext

    /**
     * 回复人物/角色评论下的某条评论
     */
    data class PersonCommentReply(val target: PersonCommentTarget, val commentId: String) : CommentContext

    /**
     * 剧集评论
     */
    data class Episode(val subjectId: Int, val episodeId: Long) : CommentContext

    /**
     * 条目吐槽箱
     */
    data class SubjectReview(val subjectId: Int) : CommentContext

    /**
     * 剧集回复某个人的评论
     */
    data class EpisodeReply(val subjectId: Int, val episodeId: Long, val commentId: String) : CommentContext
}
