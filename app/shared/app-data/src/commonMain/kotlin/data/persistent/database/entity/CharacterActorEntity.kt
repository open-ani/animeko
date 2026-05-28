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
import androidx.room3.PrimaryKey

/**
 * 角色对应演员, 1:N
 *
 * @since 4.0.0-alpha04
 */
@Entity(
    "character_actor",
    indices = [
        Index(value = ["characterId", "actorPersonId"], unique = true),
        Index(value = ["actorPersonId"], orders = [Index.Order.ASC]),
    ],
    foreignKeys = [
        ForeignKey(
            CharacterEntity::class,
            parentColumns = ["characterId"],
            childColumns = ["characterId"],
            onDelete = ForeignKey.CASCADE,
            deferred = true,
        ),
        ForeignKey(
            PersonEntity::class,
            parentColumns = ["personId"],
            childColumns = ["actorPersonId"],
            onDelete = ForeignKey.RESTRICT,
            deferred = true,
        ),
    ],
)
data class CharacterActorEntity(
    @PrimaryKey
    val characterId: Int,
    val actorPersonId: Int,
)