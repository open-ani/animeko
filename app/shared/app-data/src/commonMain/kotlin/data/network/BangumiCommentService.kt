/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.him188.ani.app.data.models.UserInfo
import me.him188.ani.app.data.models.subject.SubjectReview
import me.him188.ani.client.apis.SubjectsAniApi
import me.him188.ani.client.models.AniSubjectReview
import me.him188.ani.datasources.api.paging.Paged
import me.him188.ani.utils.ktor.ApiInvoker
import me.him188.ani.utils.coroutines.IO_
import kotlin.coroutines.CoroutineContext
import kotlin.time.Instant

interface BangumiCommentService {
    /**
     * @return `null` if [subjectId] is invalid
     */
    suspend fun getSubjectComments(subjectId: Int, offset: Int, limit: Int): Paged<SubjectReview>?
}

class BangumiBangumiCommentServiceImpl(
    private val subjectsApi: ApiInvoker<SubjectsAniApi>,
    private val ioDispatcher: CoroutineContext = Dispatchers.IO_,
) : BangumiCommentService {
    override suspend fun getSubjectComments(subjectId: Int, offset: Int, limit: Int): Paged<SubjectReview>? {
        return withContext(ioDispatcher) {
            val response = subjectsApi {
                getSubjectReviews(subjectId.toLong(), offset, limit).body()
            }
            val list = response.items.map { it.toSubjectReview() }
            Paged(
                total = response.total.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
                hasMore = offset + list.size < response.total,
                page = list,
            )
        }
    }


}

private fun AniSubjectReview.toSubjectReview() = SubjectReview(
    id = id.hashCode().toLong(),
    content = contentBbcode,
    updatedAt = Instant.parse(updatedAt).toEpochMilliseconds(),
    rating = rating,
    creator = author?.let {
        UserInfo(
            id = it.id,
            nickname = it.nickname,
            username = null,
            avatarUrl = it.avatarUrl,
        )
    },
)
