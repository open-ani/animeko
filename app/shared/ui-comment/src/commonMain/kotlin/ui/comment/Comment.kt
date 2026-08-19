/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.comment

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.paging.PagingData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import me.him188.ani.app.data.models.UserInfo
import me.him188.ani.app.ui.richtext.UIRichElement

/**
 * A state which is read by comment composables ([CommentItem]).
 */
@Stable
class CommentState(
    val list: Flow<PagingData<UIComment>>,
    countState: State<Int?>,
    private val onSubmitCommentReaction: suspend (comment: UIComment, value: String, selected: Boolean) -> Unit,
    private val backgroundScope: CoroutineScope,
    val commentLoadFailures: Flow<Throwable> = emptyFlow(),
    private val onSubmitCommentVote: suspend (comment: UIComment, vote: UICommentVote?) -> Unit = { _, _ -> },
) {
    val count by countState
    private val actionSubmitFailureChannel = Channel<Throwable>(Channel.BUFFERED)

    /**
     * 提交回应或投票失败的事件流.
     */
    val actionSubmitFailures: Flow<Throwable> = actionSubmitFailureChannel.receiveAsFlow()

    private val reactionOverrides = mutableStateMapOf<String, List<UICommentReaction>>()
    private val reactionJobs = mutableMapOf<ReactionKey, Job>()

    private val voteOverrides = mutableStateMapOf<String, VoteOverride>()
    private val voteJobs = mutableMapOf<String, Job>()

    /**
     * 应用本地乐观更新 (回应与投票) 后的评论.
     */
    fun withOverlay(comment: UIComment): UIComment {
        var result = comment
        reactionOverrides[comment.stableId]?.let { result = result.copyWithReactions(it) }
        voteOverrides[comment.stableId]?.let { result = result.copyWithVote(it.likeCount, it.selfVote) }
        return result
    }

    fun submitReaction(comment: UIComment, value: String) {
        val currentComment = withOverlay(comment)
        val before = currentComment.reactions.firstOrNull { it.value == value }
        val afterReactions = currentComment.reactions.toggle(value)
        val after = afterReactions.firstOrNull { it.value == value }
        val selected = after?.selected == true
        val key = ReactionKey(comment.stableId, value)

        reactionOverrides[comment.stableId] = afterReactions
        reactionJobs[key]?.cancel()
        val job = backgroundScope.launch {
            try {
                onSubmitCommentReaction(comment, value, selected)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                reactionOverrides[comment.stableId] = reactionOverrides[comment.stableId]
                    .orEmpty()
                    .restore(value, before)
                actionSubmitFailureChannel.trySend(e)
            } finally {
                if (reactionJobs[key] === coroutineContext[Job]) {
                    reactionJobs.remove(key)
                }
            }
        }
        reactionJobs[key] = job
    }

    /**
     * 点赞或点踩. 再次点击同一按钮则取消投票, 点另一个按钮则覆盖之前的投票.
     *
     * 乐观更新本地状态, 失败时回滚并发送错误到 [actionSubmitFailures].
     */
    fun toggleVote(comment: UIComment, target: UICommentVote) {
        // 已完成的 job 在这里 (UI 线程) 惰性清理, 使 voteJobs 只被 UI 线程改写
        voteJobs.entries.removeAll { it.value.isCompleted }

        val current = withOverlay(comment)
        val before = VoteOverride(current.likeCount, current.selfVote)
        val newVote = if (current.selfVote == target) null else target
        val likeDelta = (if (newVote == UICommentVote.LIKE) 1 else 0) -
                (if (current.selfVote == UICommentVote.LIKE) 1 else 0)
        val after = VoteOverride((current.likeCount + likeDelta).coerceAtLeast(0), newVote)
        val stableId = comment.stableId

        voteOverrides[stableId] = after
        voteJobs[stableId]?.cancel()
        val job = backgroundScope.launch {
            try {
                onSubmitCommentVote(comment, newVote)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                voteOverrides[stableId] = before
                actionSubmitFailureChannel.trySend(e)
            }
        }
        voteJobs[stableId] = job
    }

    /**
     * 清除已结算 (没有在途提交) 的乐观覆盖, 让列表回到以服务端数据为准.
     *
     * 应在 Paging 刷新完成后调用 (see [CommentOverlayCleanupEffect]); 在途提交的覆盖保留, 避免丢失乐观状态.
     */
    fun clearStaleOverlays() {
        voteOverrides.keys.toList().forEach { stableId ->
            val job = voteJobs[stableId]
            if (job == null || job.isCompleted) {
                voteOverrides.remove(stableId)
            }
        }
        val activeReactionIds = reactionJobs.entries.toList()
            .filter { !it.value.isCompleted }
            .mapTo(HashSet()) { it.key.stableId }
        reactionOverrides.keys.toList().forEach { stableId ->
            if (stableId !in activeReactionIds) {
                reactionOverrides.remove(stableId)
            }
        }
    }

    private data class ReactionKey(val stableId: String, val value: String)
    private data class VoteOverride(val likeCount: Int, val selfVote: UICommentVote?)
}


@Immutable
class UIRichText(val elements: List<UIRichElement>)

@Immutable
class UIComment(
    val id: Long,
    val stableId: String,
    val author: UserInfo?,
    val content: UIRichText,
    val createdAt: Long, // timestamp millis
    val reactions: List<UICommentReaction>,
    val briefReplies: List<UIComment>,
    val replyCount: Int,
    val rating: Int?,
    val source: UICommentSource = UICommentSource.BANGUMI,
    val sourceCommentId: String = stableId,
    val canReply: Boolean = false,
    /**
     * 点赞总数. [UICommentSource.BANGUMI] 来源的评论恒为 `0`.
     */
    val likeCount: Int = 0,
    /**
     * 当前登录用户对这条评论的投票, 未投票或未登录时为 `null`.
     */
    val selfVote: UICommentVote? = null,
    /**
     * 原始 BBCode 内容, 用于复制评论与举报快照.
     */
    val rawContent: String? = null,
    /**
     * 评论所属的剧集 ID. 仅剧集评论有值; 提交投票/举报时应优先用它而不是当前播放的剧集.
     */
    val episodeId: Long? = null,
)

/**
 * 用户对一条评论的投票. 同一用户对同一条评论只能持有一个值.
 */
enum class UICommentVote {
    LIKE,
    DISLIKE,
}

@Immutable
class UICommentReaction(
    val value: String,
    val count: Int,
    val selected: Boolean
)

private fun UIComment.copyWithReactions(reactions: List<UICommentReaction>): UIComment {
    return UIComment(
        id = id,
        stableId = stableId,
        author = author,
        content = content,
        createdAt = createdAt,
        reactions = reactions,
        briefReplies = briefReplies,
        replyCount = replyCount,
        rating = rating,
        source = source,
        sourceCommentId = sourceCommentId,
        canReply = canReply,
        likeCount = likeCount,
        selfVote = selfVote,
        rawContent = rawContent,
        episodeId = episodeId,
    )
}

private fun UIComment.copyWithVote(likeCount: Int, selfVote: UICommentVote?): UIComment {
    return UIComment(
        id = id,
        stableId = stableId,
        author = author,
        content = content,
        createdAt = createdAt,
        reactions = reactions,
        briefReplies = briefReplies,
        replyCount = replyCount,
        rating = rating,
        source = source,
        sourceCommentId = sourceCommentId,
        canReply = canReply,
        likeCount = likeCount,
        selfVote = selfVote,
        rawContent = rawContent,
        episodeId = episodeId,
    )
}

private fun List<UICommentReaction>.toggle(value: String): List<UICommentReaction> {
    val current = firstOrNull { it.value == value }
    val updated = when {
        current == null -> this + UICommentReaction(value, count = 1, selected = true)
        current.selected && current.count <= 1 -> filterNot { it.value == value }
        current.selected -> replace(value, UICommentReaction(value, current.count - 1, selected = false))
        else -> replace(value, UICommentReaction(value, current.count + 1, selected = true))
    }
    return updated.sortedWith(UI_COMMENT_REACTION_COMPARATOR)
}

private fun List<UICommentReaction>.restore(value: String, reaction: UICommentReaction?): List<UICommentReaction> {
    val restored = if (reaction == null) {
        filterNot { it.value == value }
    } else {
        replace(value, reaction)
    }
    return restored.sortedWith(UI_COMMENT_REACTION_COMPARATOR)
}

private fun List<UICommentReaction>.replace(value: String, reaction: UICommentReaction): List<UICommentReaction> {
    var replaced = false
    val updated = map {
        if (it.value == value) {
            replaced = true
            reaction
        } else {
            it
        }
    }
    return if (replaced) updated else updated + reaction
}

private val UI_COMMENT_REACTION_COMPARATOR = compareBy<UICommentReaction> {
    it.value.removePrefix("bgm").toIntOrNull() ?: Int.MAX_VALUE
}.thenBy { it.value }

enum class UICommentSource {
    ANI,
    BANGUMI,
}
