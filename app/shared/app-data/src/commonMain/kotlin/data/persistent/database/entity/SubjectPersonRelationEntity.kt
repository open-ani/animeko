/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.persistent.database.entity

import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import me.him188.ani.app.data.models.subject.PersonPosition
import me.him188.ani.app.data.persistent.database.dao.SubjectCollectionEntity

/**
 * @since 4.0.0-alpha04
 */
@Entity(
    "subject_person",
    indices = [
        Index(value = ["personId"], orders = [Index.Order.ASC]),
    ],
    primaryKeys = ["subjectId", "personId"],
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