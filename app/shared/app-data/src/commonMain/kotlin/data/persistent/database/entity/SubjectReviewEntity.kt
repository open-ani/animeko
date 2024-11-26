/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.persistent.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import me.him188.ani.app.data.persistent.database.dao.SubjectCollectionEntity

/**
 * 条目的评论. 每个用户只能为每个条目创建一个评论
 *
 * @since 4.1.0-alpha02
 */
@Entity(
    "subject_review",
    indices = [
        Index(value = ["subjectId"], orders = [Index.Order.ASC]),
        Index(value = ["updatedAt"], orders = [Index.Order.DESC], name = "index_updatedAt_desc"),
        Index(value = ["updatedAt"], orders = [Index.Order.ASC], name = "index_updatedAt_asc"),
        Index(value = ["rating"], orders = [Index.Order.DESC], name = "index_rating_desc"),
        Index(value = ["rating"], orders = [Index.Order.ASC], name = "index_rating_asc"),
    ],
    primaryKeys = ["subjectId", "authorId"],
    foreignKeys = [
        ForeignKey(
            SubjectCollectionEntity::class,
            parentColumns = ["subjectId"],
            childColumns = ["subjectId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class SubjectReviewEntity(
    val subjectId: Int,
    val authorId: Int,

    val authorNickname: String, // can be empty
    val authorAvatarUrl: String?,

    val updatedAt: Long,
    val rating: Int,
    val content: String,
)
