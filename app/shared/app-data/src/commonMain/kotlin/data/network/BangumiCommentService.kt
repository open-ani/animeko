/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.network

import androidx.compose.ui.util.packInts
import io.ktor.client.plugins.ClientRequestException
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.him188.ani.app.data.models.UserInfo
import me.him188.ani.app.data.models.episode.EpisodeComment
import me.him188.ani.app.data.models.episode.toEpisodeComment
import me.him188.ani.app.data.models.subject.SubjectReview
import me.him188.ani.datasources.api.paging.Paged
import me.him188.ani.datasources.api.paging.processPagedResponse
import me.him188.ani.datasources.bangumi.BangumiClient
import me.him188.ani.datasources.bangumi.next.models.BangumiNextCreateSubjectEpCommentRequest
import me.him188.ani.datasources.bangumi.next.models.BangumiNextGetSubjectEpisodeComments200ResponseInner
import me.him188.ani.datasources.bangumi.next.models.BangumiNextSubjectComment
import me.him188.ani.utils.coroutines.IO_
import kotlin.coroutines.CoroutineContext

sealed interface BangumiCommentService {
    /**
     * @return `null` if [subjectId] is invalid
     */
    suspend fun getSubjectReviews(subjectId: Int, offset: Int, limit: Int): Paged<SubjectReview>?

    /**
     * @return `null` if [subjectId] is invalid
     */
    suspend fun getSubjectEpisodeComments(subjectId: Int): List<EpisodeComment>?

    // comment.id 会被忽略
    suspend fun postEpisodeComment(
        episodeId: Int,
        content: String,
        cfTurnstileResponse: String,
        replyToCommentId: Int? = null
    )
}

class BangumiBangumiCommentServiceImpl(
    private val client: BangumiClient,
    private val ioDispatcher: CoroutineContext = Dispatchers.IO_,
) : BangumiCommentService {
    override suspend fun getSubjectReviews(subjectId: Int, offset: Int, limit: Int): Paged<SubjectReview>? {
        return withContext(ioDispatcher) {
            val response = try {
                client.getNextApi()
                    .getSubjectComments(subjectId, null, limit, offset)
                    .body()
            } catch (e: ClientRequestException) {
                if (e.response.status == HttpStatusCode.NotFound || e.response.status == HttpStatusCode.BadRequest) {
                    return@withContext null
                }
                throw e
            }
            val list = response.data.map { it.toSubjectReview(subjectId) }
            Paged.processPagedResponse(total = response.total, pageSize = limit, data = list)
        }
    }

    override suspend fun postEpisodeComment(
        episodeId: Int,
        content: String,
        cfTurnstileResponse: String,
        replyToCommentId: Int?
    ) {
        withContext(ioDispatcher) {
            client.getNextApi().createSubjectEpComment(
                episodeId,
                BangumiNextCreateSubjectEpCommentRequest(
                    cfTurnstileResponse,
                    content,
                    replyToCommentId,
                ),
            )
        }
    }

    override suspend fun getSubjectEpisodeComments(subjectId: Int): List<EpisodeComment>? {
        return withContext(ioDispatcher) {
            val response = try {
                client.getNextApi()
                    .getSubjectEpisodeComments(subjectId)
                    .body()
                    .map(BangumiNextGetSubjectEpisodeComments200ResponseInner::toEpisodeComment)
            } catch (e: ClientRequestException) {
                if (e.response.status == HttpStatusCode.NotFound || e.response.status == HttpStatusCode.BadRequest) {
                    return@withContext null
                }
                throw e
            }
            response
        }
    }
}

private fun BangumiNextSubjectComment.toSubjectReview(subjectId: Int) = SubjectReview(
    id = packInts(subjectId, user.id ?: 0),
    content = comment,
    updatedAt = updatedAt * 1000L,
    rating = rate,
    creator = UserInfo(
        id = user.id,
        nickname = user.nickname,
        username = null,
        avatarUrl = user.avatar.medium,
    ), // 没有username,
)