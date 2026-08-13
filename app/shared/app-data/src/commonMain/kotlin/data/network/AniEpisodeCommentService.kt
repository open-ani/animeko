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
        replies = briefReplies.map { it.toEpisodeComment(episodeId, commentSource) }.withReplyTargets(),
        canReply = canReply,
    )
}

/**
 * 给楼内回复补上"这条在回复谁" ([EpisodeComment.replyToCommentId]).
 *
 * 接口本身不带回复关系 (Bangumi 的 `relatedID` 只有客户端直连时拿得到), 所以从正文里认:
 * 在 Bangumi 上回复某条楼内回复时, 站点会把被回复的内容以
 * `[quote][b]昵称[/b] 说: ……[/quote]` 的形式塞在正文开头, 见 [quotedAuthorNicknameOrNull].
 *
 * 认出昵称后还要在**同一楼**里找到那条回复本身 —— 界面上要显示的是名字, 而名字只能由那条回复
 * 提供 (见 `CommentMapperContext.parseToUIComment`). 找不到就留 `null` (只缩进不写名字), 与
 * 原先"被回复的那条不在本楼 brief 列表里"时的表现一致.
 *
 * 同名的人在同一楼里各回一条时, 取时间上不晚于本条的那个最近的 —— 被回复的一定先发出来.
 */
private fun List<EpisodeComment>.withReplyTargets(): List<EpisodeComment> {
    if (size < 2) return this // 一条回复不可能在回复本楼里的另一条
    return map { reply ->
        val nickname = quotedAuthorNicknameOrNull(reply.content) ?: return@map reply
        val candidates = filter { it.sourceCommentId != reply.sourceCommentId && it.author?.nickname == nickname }
        val target = candidates.filter { it.createdAt <= reply.createdAt }.maxByOrNull { it.createdAt }
            ?: candidates.firstOrNull()
            ?: return@map reply
        reply.copy(replyToCommentId = target.sourceCommentId)
    }
}

/**
 * 认出正文开头那条引用是谁说的, 认不出返回 `null`.
 *
 * 只认**开头**的引用: 正文中间的引用是用户自己贴的, 不代表回复关系. 引用里的昵称可能带 `[b]`
 * 也可能不带 (取决于发布时的客户端), 冒号半角全角都见过.
 */
internal fun quotedAuthorNicknameOrNull(contentBbcode: String): String? {
    val match = QUOTED_AUTHOR_REGEX.find(contentBbcode) ?: return null
    val nickname = match.groupValues[1].takeIf { it.isNotEmpty() } ?: match.groupValues[2]
    return nickname.trim().takeIf { it.isNotBlank() }
}

private val QUOTED_AUTHOR_REGEX = Regex(
    """^\s*\[quote]\s*(?:\[b]\s*([^\[\]]{1,64}?)\s*\[/b]|([^\[\]\n]{1,64}?))\s*说\s*[:：]""",
    RegexOption.IGNORE_CASE,
)

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
