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
import me.him188.ani.app.data.models.comment.CommentVoteValue
import me.him188.ani.app.data.models.subject.SubjectReview
import me.him188.ani.app.data.models.subject.SubjectReviewSource
import me.him188.ani.app.data.repository.RepositoryException
import me.him188.ani.client.apis.SubjectsAniApi
import me.him188.ani.client.models.AniSubjectReview
import me.him188.ani.client.models.AniSubjectReviewSource
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

    /**
     * 对条目评价投票. [vote] 为 `null` 表示取消投票.
     * 只有 [SubjectReviewSource.ANI] 来源的评价可投票.
     */
    suspend fun voteSubjectReview(subjectId: Int, reviewId: String, vote: CommentVoteValue?)
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

    override suspend fun voteSubjectReview(subjectId: Int, reviewId: String, vote: CommentVoteValue?) {
        withContext(ioDispatcher) {
            try {
                subjectsApi {
                    if (vote == null) {
                        removeSubjectReviewVote(subjectId.toLong(), reviewId).body()
                    } else {
                        voteSubjectReview(subjectId.toLong(), reviewId, vote.toAniCommentVoteValue()).body()
                    }
                }
            } catch (e: Exception) {
                throw RepositoryException.wrapOrThrowCancellation(e)
            }
        }
    }
}

private fun AniSubjectReview.toSubjectReview() = SubjectReview(
    id = id.hashCode().toLong(),
    reviewId = id,
    source = when (source) {
        AniSubjectReviewSource.ANIMEKO -> SubjectReviewSource.ANI
        AniSubjectReviewSource.BANGUMI -> SubjectReviewSource.BANGUMI
    },
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
    likeCount = likeCount,
    selfVote = selfVote?.toCommentVoteValue(),
)
