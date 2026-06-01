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
     * 被回复的那一条同层回复的 [sourceCommentId]. 直接回复主楼, 或认不出来时为 `null`.
     *
     * 电视上的完整评论弹窗用它显示"回复 @某人".
     *
     * 服务端合并后的 `listEpisodeComments` 不带回复关系 (Bangumi 的 `relatedID` 原先只有客户端
     * 直连 next.bgm.tv 时拿得到), 所以现在是从正文开头那条引用反推的, 见
     * `AniEpisodeCommentService` 里的 `withReplyTargets`. 服务端哪天给了真字段, 换成直接读它.
     */
    val replyToCommentId: String? = null,
)

data class EpisodeCommentReaction(
    val value: String,
    val count: Int,
    val selected: Boolean,
)
