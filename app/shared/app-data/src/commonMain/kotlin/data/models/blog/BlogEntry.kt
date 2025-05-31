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
import me.him188.ani.app.data.models.UserInfo
import me.him188.ani.datasources.bangumi.next.models.BangumiNextBlogEntry

@Immutable
data class BlogEntry(
    val content: String,
    val createdAt: Long,
    val icon: String,
    val id: Int,
    val noreply: Int,
    val public: Boolean,
    val related: Int,
    val replies: Int,
    val tags: List<String>,
    val title: String,
    val type: Int,
    val updatedAt: Long,
    val user: UserInfo,
    val views: Int
)

fun BangumiNextBlogEntry.toBlogEntry(): BlogEntry =
    BlogEntry(
        content = content,
        createdAt = createdAt * 1000L, // 转为毫秒
        icon = icon,
        id = id,
        noreply = noreply,
        public = `public`,
        related = related,
        replies = replies,
        tags = tags,
        title = title,
        type = type,
        updatedAt = updatedAt * 1000L, // 转为毫秒
        user = UserInfo(
            id = user.id,
            nickname = user.nickname,
            username = null,   // 无 username 字段
            avatarUrl = user.avatar.large
        ),
        views = views
    )