/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
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
import me.him188.ani.datasources.bangumi.next.models.BangumiNextCreateEpisodeCommentRequest
import me.him188.ani.datasources.bangumi.next.models.BangumiNextGetEpisodeComments200ResponseInner
import me.him188.ani.datasources.bangumi.next.models.BangumiNextSubjectInterestComment
import me.him188.ani.utils.coroutines.IO_
import kotlin.coroutines.CoroutineContext

sealed interface BangumiCommentService {
    /**
     * @return `null` if [subjectId] is invalid
     */
    suspend fun getSubjectComments(subjectId: Int, offset: Int, limit: Int): Paged<SubjectReview>?

    /**
     * @return `null` if [episodeId] is invalid
     */
    suspend fun getSubjectEpisodeComments(episodeId: Int): List<EpisodeComment>?

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
    override suspend fun getSubjectComments(subjectId: Int, offset: Int, limit: Int): Paged<SubjectReview>? {
        return withContext(ioDispatcher) {
            val response = try {
                client.nextSubjectApi {
                    getSubjectComments(subjectId, null, limit, offset)
                        .body()
                }
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
            client.nextEpisodeApi {
                createEpisodeComment(
                    episodeId,
                    BangumiNextCreateEpisodeCommentRequest(
                        cfTurnstileResponse,
                        content,
                        replyToCommentId,
                    ),
                )
                Unit // suppress inspection
            }
        }
    }

    override suspend fun getSubjectEpisodeComments(episodeId: Int): List<EpisodeComment>? {
        return withContext(ioDispatcher) {
            val response = try {
                client.nextEpisodeApi {
                    getEpisodeComments(episodeId)
                        .body()
                        .map { it.toEpisodeComment(episodeId) }
                }
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

private fun BangumiNextSubjectInterestComment.toSubjectReview(subjectId: Int) = SubjectReview(
    id = packInts(subjectId, user.id),
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