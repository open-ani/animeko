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
import me.him188.ani.datasources.bangumi.next.models.BangumiNextGetEpisodeComments200ResponseInner

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
)

data class EpisodeCommentReaction(
    val value: String,
    val count: Int,
    val selected: Boolean,
)

fun BangumiNextGetEpisodeComments200ResponseInner.toEpisodeComment(
    episodeId: Long,
    selfBangumiUsername: String? = null,
) = EpisodeComment(
    stableId = "bangumi:$id",
    source = EpisodeCommentSource.BANGUMI,
    sourceCommentId = id.toString(),
    commentId = id.toString(),
    episodeId = episodeId,
    createdAt = createdAt * 1000L,
    content = content,
    author = user?.let { u ->
        UserInfo(
            id = u.id.toString(),
            nickname = u.nickname,
            username = null,
            avatarUrl = u.avatar.large,
        ) // 没有username
    },
    reactions = reactions.orEmpty().map { reaction ->
        EpisodeCommentReaction(
            value = "bgm${reaction.value}",
            count = reaction.users.size,
            selected = selfBangumiUsername != null && reaction.users.any { it.username == selfBangumiUsername },
        )
    },
    replies = replies.map { r ->
        EpisodeComment(
            stableId = "bangumi:${r.id}",
            source = EpisodeCommentSource.BANGUMI,
            sourceCommentId = r.id.toString(),
            commentId = r.id.toString(),
            episodeId = episodeId,
            createdAt = r.createdAt * 1000L,
            content = r.content,
            author = r.user?.let { u ->
                UserInfo(
                    id = u.id.toString(),
                    nickname = u.nickname,
                    username = null,
                    avatarUrl = u.avatar.large,
                )
            },
            reactions = r.reactions.orEmpty().map { reaction ->
                EpisodeCommentReaction(
                    value = "bgm${reaction.value}",
                    count = reaction.users.size,
                    selected = selfBangumiUsername != null && reaction.users.any { it.username == selfBangumiUsername },
                )
            },
            canReply = false,
        )
    },
    canReply = false,
)
