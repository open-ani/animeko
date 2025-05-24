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

package me.him188.ani.app.data.models.subject

import me.him188.ani.app.data.models.Blog.BlogEntry
import me.him188.ani.app.data.models.Blog.toBlogEntry
import me.him188.ani.app.data.models.UserInfo
import me.him188.ani.datasources.bangumi.next.models.BangumiNextSubjectReview
import me.him188.ani.datasources.bangumi.next.models.BangumiNextGetSubjectReviews200Response

data class SubjectReview(
    val reviewId: Int,
    val entry: BlogEntry,
    val author: UserInfo
)

data class SubjectReviewResponse(
    val data: List<SubjectReview>,
    val total: Int
)

fun BangumiNextSubjectReview.toSubjectReview(): SubjectReview =
    SubjectReview(
        reviewId = id,
        entry = entry.toBlogEntry(),
        author = UserInfo(
            id = user.id,
            nickname = user.nickname,
            username = null, // 没有 username 字段
            avatarUrl = user.avatar.large
        )
    )

fun BangumiNextGetSubjectReviews200Response.toSubjectReviewResponse(): SubjectReviewResponse =
    SubjectReviewResponse(
        data = data.map { it.toSubjectReview() },
        total = total
    )