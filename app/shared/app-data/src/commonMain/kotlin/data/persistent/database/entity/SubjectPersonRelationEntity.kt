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
import me.him188.ani.app.data.models.subject.PersonPosition
import me.him188.ani.app.data.persistent.database.dao.SubjectCollectionEntity

/**
 * 条目与制作人员的关系, 一行是一个人在一个条目里的一个职位.
 *
 * 主键必须包含 [position]: 同一个人在同一条目里常兼多个职位 (导演往往同时是分镜、演出、原画),
 * 只按 (subjectId, personId) 去重会把其余职位丢掉.
 *
 * @since 4.0.0-alpha04
 */
@Entity(
    "subject_person",
    indices = [
        Index(value = ["personId"], orders = [Index.Order.ASC]),
    ],
    primaryKeys = ["subjectId", "personId", "position"],
    foreignKeys = [
        ForeignKey(
            SubjectCollectionEntity::class,
            parentColumns = ["subjectId"],
            childColumns = ["subjectId"],
            onDelete = ForeignKey.CASCADE,
            deferred = true,
        ),
        ForeignKey(
            PersonEntity::class,
            parentColumns = ["personId"],
            childColumns = ["personId"],
            onDelete = ForeignKey.RESTRICT,
            deferred = true,
        ),
    ],
)
data class SubjectPersonRelationEntity(
    val subjectId: Int,
    val index: Int,
    val personId: Int,
    val position: PersonPosition,
)