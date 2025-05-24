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

package me.him188.ani.app.data.models.blog

import me.him188.ani.app.data.models.UserInfo
import me.him188.ani.datasources.bangumi.next.models.BangumiNextGetBlogComments200ResponseInner

data class BlogComment(
    val commentId: Int,
    val blogId: Int,
    
    val createdAt: Long,
    val content: String,
    val author: UserInfo?,
    val replies: List<BlogComment> = listOf()
)

fun BangumiNextGetBlogComments200ResponseInner.toBlogComment(blogId: Int) = BlogComment(
    commentId = id,
    blogId = mainID,
    createdAt = createdAt * 1000L,
    content = content,
    author = user?.let {u ->
        UserInfo(
            id = u.id,
            nickname = u.nickname,
            username = null,
            avatarUrl = u.avatar.large,
        ) // No username
    },
    replies = replies.map {r ->
        BlogComment(
            commentId = r.id,
            blogId = mainID,
            createdAt = r.createdAt * 1000L,
            content = r.content,
            author = r.user?.let { u -> 
                UserInfo(
                    id = u.id,
                    nickname = u.nickname,
                    username = null,
                    avatarUrl = u.avatar.large,
                )
            }
            
        )
    }
)