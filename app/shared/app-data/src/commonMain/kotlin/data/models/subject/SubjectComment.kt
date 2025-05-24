/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.models.subject

import androidx.compose.runtime.Immutable
import me.him188.ani.app.data.models.UserInfo

@Immutable
data class SubjectComment(
    /**
     * This [id] is calculated by [creator], [content] and [updatedAt], not provided by Bangumi API.
     */
    val id: Long,
    /**
     * Timestamp, millis
     */
    val updatedAt: Long,
    val content: String,
    val creator: UserInfo?,
    val rating: Int
)