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
import androidx.room3.PrimaryKey
import me.him188.ani.app.data.models.subject.PersonType

/**
 * @since 4.0.0-alpha04
 */
@Entity(
    "person",
)
data class PersonEntity(
    @PrimaryKey
    val personId: Int = 0,
    val name: String,
    val nameCn: String,
    val imageLarge: String,
    val imageMedium: String,
    val type: PersonType,
    val summary: String,
)
