/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.network

import io.ktor.client.plugins.ClientRequestException
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.him188.ani.app.data.models.UserInfo
import me.him188.ani.app.data.models.comment.CommentVoteValue
import me.him188.ani.app.data.models.episode.EpisodeComment
import me.him188.ani.app.data.models.episode.EpisodeCommentReaction
import me.him188.ani.app.data.models.episode.EpisodeCommentSource
import me.him188.ani.app.data.repository.RepositoryException
import me.him188.ani.client.apis.EpisodesAniApi
import me.him188.ani.client.models.AniCommentVoteValue
import me.him188.ani.client.models.AniCreateEpisodeCommentRequest
import me.him188.ani.client.models.AniCreateEpisodeReplyRequest
import me.him188.ani.client.models.AniEpisodeComment
import me.him188.ani.client.models.AniEpisodeCommentAuthor
import me.him188.ani.client.models.AniEpisodeCommentRepliesResponse
import me.him188.ani.client.models.AniEpisodeCommentReply
import me.him188.ani.client.models.AniEpisodeCommentSource
import me.him188.ani.client.models.AniEpisodeCommentsResponse
import me.him188.ani.utils.coroutines.IO_
import me.him188.ani.utils.ktor.ApiInvoker
import kotlin.coroutines.CoroutineContext

open class AniEpisodeCommentService(
    private val episodesApi: ApiInvoker<EpisodesAniApi>,
    private val ioDispatcher: CoroutineContext = Dispatchers.IO_,
) {
    /**
     * 获取剧集评论, 新评论在前. 服务端已合并 Bangumi 评论, 客户端不再自行拉取 Bangumi.
     *
     * 只支持游标翻页: [after] 传上一页的 [AniEpisodeCommentsResponse.nextCursor], `null` 表示首屏.
     * 用游标而非 offset, 是因为滚动期间新增的评论会让 offset 漂移, 导致某条评论重复出现 —— 而列表按
     * `stableId` 做 key, 重复项会直接崩溃.
     *
     * 上游 Bangumi 故障时本接口仍返回 Ani 评论, 并置 [AniEpisodeCommentsResponse.bangumiUnavailable].
     */
    open suspend fun listEpisodeComments(
        episodeId: Long,
        after: String? = null,
        limit: Int = 30,
    ): AniEpisodeCommentsResponse = withContext(ioDispatcher) {
        try {
            episodesApi.invoke {
                listEpisodeComments(
                    episodeId = episodeId,
                    limit = limit,
                    includeBangumi = true,
                    after = after,
                ).body()
            }
        } catch (e: Exception) {
            throw RepositoryException.wrapOrThrowCancellation(e)
        }
    }

    /**
     * 评论详情 (thread) 的根评论, 不含回复. [commentId] 是列表响应里的稳定 `id` (`ani:<uuid>` / `bangumi:<id>`).
     *
     * @return 评论不存在、已删除、不属于该剧集或者是楼中楼回复时为 `null`.
     */
    open suspend fun getEpisodeComment(
        episodeId: Long,
        commentId: String,
    ): AniEpisodeComment? = withContext(ioDispatcher) {
        try {
            episodesApi.invoke {
                getEpisodeComment(episodeId = episodeId, commentId = commentId).body()
            }
        } catch (e: Exception) {
            if (e.isNotFound()) return@withContext null
            throw RepositoryException.wrapOrThrowCancellation(e)
        }
    }

    /**
     * 一条根评论的楼中楼回复, 发送时间正序, 游标翻页: [after] 传上一页的 [AniEpisodeCommentRepliesResponse.nextCursor].
     * Bangumi 评论的回复一次全部返回.
     */
    open suspend fun listEpisodeCommentReplies(
        episodeId: Long,
        commentId: String,
        after: String? = null,
        limit: Int = 30,
    ): AniEpisodeCommentRepliesResponse = withContext(ioDispatcher) {
        try {
            episodesApi.invoke {
                listEpisodeCommentReplies(
                    episodeId = episodeId,
                    commentId = commentId,
                    after = after,
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

    /**
     * 对评论 (根评论或楼中楼回复) 投票. [vote] 为 `null` 表示取消投票. 只有 Ani 源的评论可投票.
     */
    open suspend fun voteEpisodeComment(
        episodeId: Long,
        commentId: String,
        vote: CommentVoteValue?,
    ) = withContext(ioDispatcher) {
        try {
            episodesApi.invoke {
                if (vote == null) {
                    removeEpisodeCommentVote(
                        episodeId = episodeId,
                        commentId = commentId,
                    ).body()
                } else {
                    voteEpisodeComment(
                        episodeId = episodeId,
                        commentId = commentId,
                        vote = vote.toAniCommentVoteValue(),
                    ).body()
                }
            }
        } catch (e: Exception) {
            throw RepositoryException.wrapOrThrowCancellation(e)
        }
    }
}

/**
 * 协程的异常栈恢复会把异常复制一份并以原异常为 cause, 所以沿 cause 链找 404, 不只看最外层.
 */
internal fun Throwable.isNotFound(): Boolean = generateSequence(this) { it.cause }
    .any { it is ClientRequestException && it.response.status == HttpStatusCode.NotFound }

internal fun AniEpisodeCommentAuthor.toUserInfo(): UserInfo = UserInfo(
    id = id,
    username = null,
    nickname = nickname,
    avatarUrl = avatarUrl,
)

internal fun CommentVoteValue.toAniCommentVoteValue(): AniCommentVoteValue = when (this) {
    CommentVoteValue.LIKE -> AniCommentVoteValue.LIKE
    CommentVoteValue.DISLIKE -> AniCommentVoteValue.DISLIKE
}

internal fun AniCommentVoteValue.toCommentVoteValue(): CommentVoteValue = when (this) {
    AniCommentVoteValue.LIKE -> CommentVoteValue.LIKE
    AniCommentVoteValue.DISLIKE -> CommentVoteValue.DISLIKE
}

fun AniEpisodeComment.toEpisodeComment(): EpisodeComment {
    // 服务端合并后 Bangumi 评论也从这个接口返回, 来源必须以服务端字段为准, 不能假设是 ANI
    val commentSource = when (source) {
        AniEpisodeCommentSource.ANIMEKO -> EpisodeCommentSource.ANI
        AniEpisodeCommentSource.BANGUMI -> EpisodeCommentSource.BANGUMI
    }
    return EpisodeComment(
        stableId = id,
        source = commentSource,
        sourceCommentId = sourceCommentId,
        commentId = sourceCommentId,
        episodeId = episodeId,
        createdAt = createdAtMillis,
        content = contentBbcode,
        author = author?.toUserInfo(),
        reactions = reactions.map { it.toEpisodeCommentReaction() },
        replies = briefReplies.map { it.toEpisodeComment(episodeId, commentSource) },
        canReply = canReply,
        replyCount = replyCount,
        likeCount = likeCount,
        selfVote = selfVote?.toCommentVoteValue(),
    )
}

/**
 * 楼中楼回复. 回复的 DTO 没有来源字段, [source] 沿用所在根评论的来源; 只有 Ani 的回复可以被回复 (回复它的人).
 */
internal fun AniEpisodeCommentReply.toEpisodeComment(
    episodeId: Long,
    source: EpisodeCommentSource,
): EpisodeComment {
    return EpisodeComment(
        stableId = id,
        source = source,
        sourceCommentId = sourceCommentId,
        commentId = sourceCommentId,
        episodeId = episodeId,
        createdAt = createdAtMillis,
        content = contentBbcode,
        author = author?.toUserInfo(),
        reactions = reactions.map { it.toEpisodeCommentReaction() },
        canReply = source == EpisodeCommentSource.ANI,
        likeCount = likeCount,
        selfVote = selfVote?.toCommentVoteValue(),
        parentStableId = parentId,
        replyTo = replyToAuthor?.toUserInfo(),
    )
}

private fun me.him188.ani.client.models.AniEpisodeCommentReaction.toEpisodeCommentReaction(): EpisodeCommentReaction {
    return EpisodeCommentReaction(
        value = value,
        count = count,
        selected = selected,
    )
}
