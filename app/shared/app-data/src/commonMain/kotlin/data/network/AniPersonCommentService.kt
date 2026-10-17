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
import me.him188.ani.app.data.models.person.PersonComment
import me.him188.ani.app.data.models.person.PersonCommentReaction
import me.him188.ani.app.data.models.person.PersonCommentSource
import me.him188.ani.app.data.models.person.PersonCommentTarget
import me.him188.ani.app.data.repository.RepositoryException
import me.him188.ani.client.apis.CharactersAniApi
import me.him188.ani.client.apis.PersonsAniApi
import me.him188.ani.client.models.AniCreateCharacterCommentRequest
import me.him188.ani.client.models.AniCreateCharacterReplyRequest
import me.him188.ani.client.models.AniCreatePersonCommentRequest
import me.him188.ani.client.models.AniCreatePersonReplyRequest
import me.him188.ani.client.models.AniEpisodeCommentReaction
import me.him188.ani.client.models.AniPersonComment
import me.him188.ani.client.models.AniPersonCommentReply
import me.him188.ani.client.models.AniPersonCommentSource
import me.him188.ani.client.models.AniPersonCommentsResponse
import me.him188.ani.utils.coroutines.IO_
import me.him188.ani.utils.ktor.ApiInvoker
import kotlin.coroutines.CoroutineContext

/**
 * 人物/角色评论的 Ani 服务端接口. 人物走 `/persons/{id}/comments/...`, 角色走 `/characters/{id}/comments/...`,
 * 两组接口形状相同, 由 [PersonCommentTarget] 决定用哪组.
 */
open class AniPersonCommentService(
    private val personsApi: ApiInvoker<PersonsAniApi>,
    private val charactersApi: ApiInvoker<CharactersAniApi>,
    private val ioDispatcher: CoroutineContext = Dispatchers.IO_,
) {
    /**
     * 获取评论, 新评论在前. 服务端已合并 Bangumi 评论, 客户端不再自行拉取 Bangumi.
     *
     * 只支持游标翻页: [after] 传上一页的 [AniPersonCommentsResponse.nextCursor], `null` 表示首屏.
     * 用游标而非 offset 是因为滚动期间新增的评论会让 offset 漂移, 导致某条评论重复出现 —— 而列表按 `stableId` 做 key,
     * 重复项会直接崩溃.
     *
     * 上游 Bangumi 故障时本接口仍返回 Ani 评论, 并置 [AniPersonCommentsResponse.bangumiUnavailable].
     */
    open suspend fun listComments(
        target: PersonCommentTarget,
        after: String? = null,
        limit: Int = 30,
    ): AniPersonCommentsResponse = call {
        when (target) {
            is PersonCommentTarget.Person -> personsApi {
                listPersonComments(
                    personId = target.personId.toLong(),
                    limit = limit,
                    includeBangumi = true,
                    after = after,
                ).body()
            }

            is PersonCommentTarget.Character -> charactersApi {
                listCharacterComments(
                    characterId = target.characterId.toLong(),
                    limit = limit,
                    includeBangumi = true,
                    after = after,
                ).body()
            }
        }
    }

    open suspend fun createComment(target: PersonCommentTarget, contentBbcode: String) {
        call {
            when (target) {
                is PersonCommentTarget.Person -> personsApi {
                    createPersonComment(
                        personId = target.personId.toLong(),
                        aniCreatePersonCommentRequest = AniCreatePersonCommentRequest(contentBbcode),
                    ).body()
                }

                is PersonCommentTarget.Character -> charactersApi {
                    createCharacterComment(
                        characterId = target.characterId.toLong(),
                        aniCreateCharacterCommentRequest = AniCreateCharacterCommentRequest(contentBbcode),
                    ).body()
                }
            }
        }
    }

    open suspend fun createReply(target: PersonCommentTarget, commentId: String, contentBbcode: String) {
        call {
            when (target) {
                is PersonCommentTarget.Person -> personsApi {
                    createPersonReply(
                        personId = target.personId.toLong(),
                        commentId = commentId,
                        aniCreatePersonReplyRequest = AniCreatePersonReplyRequest(contentBbcode),
                    ).body()
                }

                is PersonCommentTarget.Character -> charactersApi {
                    createCharacterReply(
                        characterId = target.characterId.toLong(),
                        commentId = commentId,
                        aniCreateCharacterReplyRequest = AniCreateCharacterReplyRequest(contentBbcode),
                    ).body()
                }
            }
        }
    }

    open suspend fun addReaction(target: PersonCommentTarget, commentId: String, value: String) {
        call {
            when (target) {
                is PersonCommentTarget.Person -> personsApi {
                    addPersonCommentReaction(target.personId.toLong(), commentId, value).body()
                }

                is PersonCommentTarget.Character -> charactersApi {
                    addCharacterCommentReaction(target.characterId.toLong(), commentId, value).body()
                }
            }
        }
    }

    open suspend fun removeReaction(target: PersonCommentTarget, commentId: String, value: String) {
        call {
            when (target) {
                is PersonCommentTarget.Person -> personsApi {
                    removePersonCommentReaction(target.personId.toLong(), commentId, value).body()
                }

                is PersonCommentTarget.Character -> charactersApi {
                    removeCharacterCommentReaction(target.characterId.toLong(), commentId, value).body()
                }
            }
        }
    }

    /**
     * 对评论投票. [vote] 为 `null` 表示取消投票. 只有 Ani 源的根评论可投票.
     */
    open suspend fun vote(target: PersonCommentTarget, commentId: String, vote: CommentVoteValue?) {
        call {
            when (target) {
                is PersonCommentTarget.Person -> personsApi {
                    if (vote == null) {
                        removePersonCommentVote(target.personId.toLong(), commentId).body()
                    } else {
                        votePersonComment(target.personId.toLong(), commentId, vote.toAniCommentVoteValue()).body()
                    }
                }

                is PersonCommentTarget.Character -> charactersApi {
                    if (vote == null) {
                        removeCharacterCommentVote(target.characterId.toLong(), commentId).body()
                    } else {
                        voteCharacterComment(target.characterId.toLong(), commentId, vote.toAniCommentVoteValue()).body()
                    }
                }
            }
        }
    }

    private suspend inline fun <R> call(crossinline block: suspend () -> R): R = withContext(ioDispatcher) {
        try {
            block()
        } catch (e: Exception) {
            throw RepositoryException.wrapOrThrowCancellation(e)
        }
    }
}

fun AniPersonComment.toPersonComment(): PersonComment {
    // 服务端合并后 Bangumi 评论也从这个接口返回, 来源必须以服务端字段为准, 不能假设是 ANI
    val commentSource = source.toPersonCommentSource()
    return PersonComment(
        stableId = id,
        source = commentSource,
        sourceCommentId = sourceCommentId,
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
        reactions = reactions.map { it.toPersonCommentReaction() },
        replies = briefReplies.map { it.toPersonComment(commentSource) },
        canReply = canReply,
        replyCount = replyCount,
        likeCount = likeCount,
        selfVote = selfVote?.toCommentVoteValue(),
    )
}

private fun AniPersonCommentReply.toPersonComment(source: PersonCommentSource): PersonComment {
    return PersonComment(
        stableId = id,
        source = source,
        sourceCommentId = sourceCommentId,
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
        reactions = reactions.map { it.toPersonCommentReaction() },
        canReply = false,
    )
}

private fun AniPersonCommentSource.toPersonCommentSource(): PersonCommentSource = when (this) {
    AniPersonCommentSource.ANIMEKO -> PersonCommentSource.ANI
    AniPersonCommentSource.BANGUMI -> PersonCommentSource.BANGUMI
}

private fun AniEpisodeCommentReaction.toPersonCommentReaction(): PersonCommentReaction {
    return PersonCommentReaction(
        value = value,
        count = count,
        selected = selected,
    )
}
