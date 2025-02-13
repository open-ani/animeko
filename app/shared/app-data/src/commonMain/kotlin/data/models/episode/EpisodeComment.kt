/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.models.episode

import me.him188.ani.app.data.models.UserInfo
import me.him188.ani.datasources.bangumi.next.models.BangumiNextGetEpisodeComments200ResponseInner

data class EpisodeComment(
    val commentId: Int,
    val episodeId: Int,

    /**
     * Timestamp, millis
     */
    val createdAt: Long,
    val content: String,
    val author: UserInfo?,
    val replies: List<EpisodeComment> = listOf()
)

fun BangumiNextGetEpisodeComments200ResponseInner.toEpisodeComment(episodeId: Int) = EpisodeComment(
    commentId = id,
    episodeId = episodeId,
    createdAt = createdAt * 1000L,
    content = content,
    author = user?.let { u ->
        UserInfo(
            id = u.id,
            nickname = u.nickname,
            username = null,
            avatarUrl = u.avatar.medium,
        ) // 没有username
    },
    replies = replies.map { r ->
        EpisodeComment(
            commentId = r.id,
            episodeId = episodeId,
            createdAt = r.createdAt * 1000L,
            content = r.content,
            author = r.user?.let { u ->
                UserInfo(
                    id = u.id,
                    nickname = u.nickname,
                    username = null,
                    avatarUrl = u.avatar.medium,
                )
            },
        )
    },
)