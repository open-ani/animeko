/*
 * Copyright (C) 2025 OpenAni and Contributors
 * Copyright (C) 2025 Kasumi's IT Infrastructure
 *
 * This software is Free Software:
 * You can use, modify, distribute and/or redistribute this software under the term below:
 * Use of this source code is governed by the GNU AFFERO GENERAL PUBLIC LICENSE version 3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/animeko/blob/main/LICENSE
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; 
 *  without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  
 *  See the License file for more details.
 */

package me.him188.ani.app.data.models.Blog

import androidx.compose.runtime.Immutable
import me.him188.ani.datasources.bangumi.next.models.BangumiNextSlimUser


@Immutable
data class BlogEntry (
    val content: String,
    val createdAt: Int,
    val icon: String,
    val id: Int,
    val noreply: Int,
    val public: Boolean,
    val related: Int,
    val replies: Int,
    val tags: List<String>,
    val title: String,
    val type: Int,
    val updatedAt: Int,
    val user: BangumiNextSlimUser,
    val views: Int
)