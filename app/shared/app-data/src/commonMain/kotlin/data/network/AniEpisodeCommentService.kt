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
import me.him188.ani.app.data.models.episode.EpisodeComment
import me.him188.ani.app.data.models.episode.EpisodeCommentReaction
import me.him188.ani.app.data.models.episode.EpisodeCommentSource
import me.him188.ani.app.data.repository.RepositoryException
import me.him188.ani.client.apis.EpisodesAniApi
import me.him188.ani.client.models.AniCreateEpisodeCommentRequest
import me.him188.ani.client.models.AniCreateEpisodeReplyRequest
import me.him188.ani.client.models.AniEpisodeComment
import me.him188.ani.client.models.AniEpisodeCommentReply
import me.him188.ani.client.models.AniEpisodeCommentsResponse
import me.him188.ani.utils.coroutines.IO_
import me.him188.ani.utils.ktor.ApiInvoker
import kotlin.coroutines.CoroutineContext

open class AniEpisodeCommentService(
    private val episodesApi: ApiInvoker<EpisodesAniApi>,
    private val ioDispatcher: CoroutineContext = Dispatchers.IO_,
) {
    open suspend fun listEpisodeComments(
        episodeId: Long,
        offset: Int = 0,
        limit: Int = 100,
    ): AniEpisodeCommentsResponse = withContext(ioDispatcher) {
        try {
            episodesApi.invoke {
                listEpisodeComments(
                    episodeId = episodeId,
                    offset = offset,
                    limit = limit,
                ).body()
            }
        } catch (e: Exception) {
            throw RepositoryException.wrapOrThrowCancellation(e)
        }
    }

    open suspend fun createEpisodeComment(
        episodeId: Long,
        contentBbcode: String,
    ) = withContext(ioDispatcher) {
        try {
            episodesApi.invoke {
                createEpisodeComment(
                    episodeId = episodeId,
                    aniCreateEpisodeCommentRequest = AniCreateEpisodeCommentRequest(contentBbcode),
                ).body()
            }
        } catch (e: Exception) {
            throw RepositoryException.wrapOrThrowCancellation(e)
        }
    }

    open suspend fun createEpisodeReply(
        episodeId: Long,
        commentId: String,
        contentBbcode: String,
    ) = withContext(ioDispatcher) {
        try {
            episodesApi.invoke {
                createEpisodeReply(
                    episodeId = episodeId,
                    commentId = commentId,
                    aniCreateEpisodeReplyRequest = AniCreateEpisodeReplyRequest(contentBbcode),
                ).body()
            }
        } catch (e: Exception) {
            throw RepositoryException.wrapOrThrowCancellation(e)
        }
    }

    open suspend fun addEpisodeCommentReaction(
        episodeId: Long,
        commentId: String,
        value: String,
    ) = withContext(ioDispatcher) {
        try {
            episodesApi.invoke {
                addEpisodeCommentReaction(
                    episodeId = episodeId,
                    commentId = commentId,
                    value = value,
                ).body()
            }
        } catch (e: Exception) {
            throw RepositoryException.wrapOrThrowCancellation(e)
        }
    }

    open suspend fun removeEpisodeCommentReaction(
        episodeId: Long,
        commentId: String,
        value: String,
    ) = withContext(ioDispatcher) {
        try {
            episodesApi.invoke {
                removeEpisodeCommentReaction(
                    episodeId = episodeId,
                    commentId = commentId,
                    value = value,
                ).body()
            }
        } catch (e: Exception) {
            throw RepositoryException.wrapOrThrowCancellation(e)
        }
    }
}

fun AniEpisodeComment.toEpisodeComment(): EpisodeComment {
    return EpisodeComment(
        stableId = id,
        source = EpisodeCommentSource.ANI,
        sourceCommentId = sourceCommentId,
        commentId = sourceCommentId,
        episodeId = episodeId,
        createdAt = createdAtMillis,
        content = contentBbcode,
        author = author?.let {
            UserInfo(
                id = it.id,
                username = null,
                nickname = it.nickname,
                avatarUrl = it.avatarUrl,
            )
        },
        reactions = reactions.map { it.toEpisodeCommentReaction() },
        replies = briefReplies.map { it.toEpisodeComment(episodeId) },
        canReply = canReply,
    )
}

private fun AniEpisodeCommentReply.toEpisodeComment(episodeId: Long): EpisodeComment {
    return EpisodeComment(
        stableId = id,
        source = EpisodeCommentSource.ANI,
        sourceCommentId = sourceCommentId,
        commentId = sourceCommentId,
        episodeId = episodeId,
        createdAt = createdAtMillis,
        content = contentBbcode,
        author = author?.let {
            UserInfo(
                id = it.id,
                username = null,
                nickname = it.nickname,
                avatarUrl = it.avatarUrl,
            )
        },
        reactions = reactions.map { it.toEpisodeCommentReaction() },
        canReply = false,
    )
}

private fun me.him188.ani.client.models.AniEpisodeCommentReaction.toEpisodeCommentReaction(): EpisodeCommentReaction {
    return EpisodeCommentReaction(
        value = value,
        count = count,
        selected = selected,
    )
}
