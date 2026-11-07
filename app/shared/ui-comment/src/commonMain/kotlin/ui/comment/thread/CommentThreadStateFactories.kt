/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.comment.thread

import androidx.paging.map
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.map
import me.him188.ani.app.data.models.person.PersonCommentTarget
import me.him188.ani.app.data.repository.episode.EpisodeCommentRepository
import me.him188.ani.app.data.repository.person.PersonCommentRepository
import me.him188.ani.app.navigation.CommentThreadTarget
import me.him188.ani.app.ui.comment.CommentMapperContext.parseToUIComment
import me.him188.ani.app.ui.comment.CommentMapperContext.toCommentVoteValue
import me.him188.ani.app.ui.comment.UIComment
import me.him188.ani.app.ui.comment.UICommentSource

/**
 * 人物/角色评论的 thread 对应的评论接口对象; 剧集评论为 `null`.
 */
fun CommentThreadTarget.toPersonCommentTarget(): PersonCommentTarget? = when (this) {
    is CommentThreadTarget.Episode -> null
    is CommentThreadTarget.Person -> PersonCommentTarget.Person(personId)
    is CommentThreadTarget.Character -> PersonCommentTarget.Character(characterId)
}

/**
 * 剧集评论 thread 的数据接线: 根评论与回复来自 [repository], 点赞与贴纸回应的提交方式与评论列表相同 (Bangumi 评论只读).
 */
fun episodeCommentThreadState(
    key: CommentThreadKey,
    target: CommentThreadTarget.Episode,
    snapshot: UIComment?,
    repository: EpisodeCommentRepository,
    backgroundScope: CoroutineScope,
): CommentThreadState {
    val episodeId = target.episodeId.toLong()
    return CommentThreadState(
        key = key,
        snapshot = snapshot,
        loadRoot = { repository.getComment(episodeId, key.rootStableId)?.parseToUIComment() },
        repliesPager = { onTotal ->
            repository.repliesPager(episodeId, key.rootStableId, onTotal)
                .map { page -> page.map { it.parseToUIComment() } }
        },
        onSubmitCommentReaction = { comment, value, selected ->
            if (comment.source == UICommentSource.ANI) {
                repository.submitReaction(comment.episodeId ?: episodeId, comment.sourceCommentId, value, selected)
            }
        },
        onSubmitCommentVote = { comment, vote ->
            if (comment.source == UICommentSource.ANI) {
                repository.submitVote(comment.episodeId ?: episodeId, comment.sourceCommentId, vote?.toCommentVoteValue())
            }
        },
        backgroundScope = backgroundScope,
    )
}

/**
 * 人物/角色评论 thread 的数据接线, 与 [episodeCommentThreadState] 同构.
 */
fun personCommentThreadState(
    key: CommentThreadKey,
    target: PersonCommentTarget,
    snapshot: UIComment?,
    repository: PersonCommentRepository,
    backgroundScope: CoroutineScope,
): CommentThreadState {
    return CommentThreadState(
        key = key,
        snapshot = snapshot,
        loadRoot = { repository.getComment(target, key.rootStableId)?.parseToUIComment() },
        repliesPager = { onTotal ->
            repository.repliesPager(target, key.rootStableId, onTotal)
                .map { page -> page.map { it.parseToUIComment() } }
        },
        onSubmitCommentReaction = { comment, value, selected ->
            if (comment.source == UICommentSource.ANI) {
                repository.submitReaction(target, comment.sourceCommentId, value, selected)
            }
        },
        onSubmitCommentVote = { comment, vote ->
            if (comment.source == UICommentSource.ANI) {
                repository.submitVote(target, comment.sourceCommentId, vote?.toCommentVoteValue())
            }
        },
        backgroundScope = backgroundScope,
    )
}
