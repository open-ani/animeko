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
import me.him188.ani.app.data.models.episode.EpisodeComment
import me.him188.ani.app.data.models.episode.EpisodeCommentReaction
import me.him188.ani.app.data.models.episode.EpisodeCommentSource
import me.him188.ani.app.data.repository.RepositoryException
import me.him188.ani.client.apis.EpisodesAniApi
import me.him188.ani.client.models.AniCommentVoteValue
import me.him188.ani.client.models.AniCreateEpisodeCommentRequest
import me.him188.ani.client.models.AniCreateEpisodeReplyRequest
import me.him188.ani.client.models.AniEpisodeComment
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
     * 对评论投票. [vote] 为 `null` 表示取消投票.
     * 只有 Ani 源的根评论可投票.
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
        author = author?.let {
            UserInfo(
                id = it.id,
                username = null,
                nickname = it.nickname,
                avatarUrl = it.avatarUrl,
            )
        },
        reactions = reactions.map { it.toEpisodeCommentReaction() },
        replies = briefReplies.map { it.toEpisodeComment(episodeId, commentSource) },
        canReply = canReply,
        replyCount = replyCount,
        likeCount = likeCount,
        selfVote = selfVote?.toCommentVoteValue(),
    )
}

private fun AniEpisodeCommentReply.toEpisodeComment(
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
