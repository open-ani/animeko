/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.persistent.database.entity

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Relation
import me.him188.ani.app.data.persistent.database.dao.EpisodeCollectionEntity

/**
 * 剧集的评论. 每个用户可以为剧集创建多个评论
 *
 * @since 4.1.0-alpha02
 */
@Entity(
    "episode_comment",
    indices = [
        Index(value = ["episodeId"]),
        Index(value = ["parentCommentId"]),
    ],
    primaryKeys = ["commentId"],
    foreignKeys = [
        ForeignKey(
            EpisodeCollectionEntity::class,
            parentColumns = ["episodeId"],
            childColumns = ["episodeId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            EpisodeCommentEntity::class,
            parentColumns = ["commentId"],
            childColumns = ["parentCommentId"],
            onDelete = ForeignKey.CASCADE,
            deferred = true,
        ),
    ],
)
data class EpisodeCommentEntity(
    val episodeId: Int,
    val commentId: Int,
    val authorId: Int,

    val parentCommentId: Int?,

    val authorNickname: String, // can be empty
    val authorAvatarUrl: String?,

    val createdAt: Long,
    val content: String,
)

data class EpisodeCommentEntityWithReplies(
    @Embedded
    val entity: EpisodeCommentEntity,
    @Relation(
        parentColumn = "commentId",
        entityColumn = "parentCommentId",
    )
    val replies: List<EpisodeCommentEntity>,
)
