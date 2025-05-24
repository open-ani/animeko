/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.comment

import androidx.compose.runtime.Immutable
import me.him188.ani.app.torrent.anitorrent.HandleId

/**
 * 评论发送的对象
 */
@Immutable
sealed interface CommentContext {
    /**
     * 剧集评论
     */
    data class Episode(val subjectId: Int, val episodeId: Int) : CommentContext

    /**
     * 条目吐槽箱
     */
    data class SubjectComment(val subjectId: Int) : CommentContext

    /**
     * 剧集回复某个人的评论
     */
    data class EpisodeReply(val subjectId: Int, val episodeId: Int, val commentId: Int) : CommentContext

    /**
     * 长评论回复某个人的评论
     */
    data class BlogReply(val blogId: Int, val commentId: Int) : CommentContext
}