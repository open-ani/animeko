/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.comment

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.paging.PagingData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import me.him188.ani.app.data.models.UserInfo
import me.him188.ani.app.ui.foundation.layout.paddingIfNotEmpty
import me.him188.ani.app.ui.foundation.theme.slightlyWeaken
import me.him188.ani.app.ui.richtext.UIRichElement

/**
 * 评论项目
 *
 * @param avatar 用户头像
 * @param primaryTitle 主标题，一般是评论者用户名
 * @param secondaryTitle 副标题，一般是评论发送时间
 * @param rhsTitle 靠右的标题，一般是番剧打分
 * @param content 评论内容
 * @param reactionRow 评论回应的各种表情
 * @param actionRow 评论操作，例如包含回复，添加回应，绝交，举报等按钮
 * @param reply 评论回复
 */
@Composable
fun Comment(
    avatar: @Composable BoxScope.() -> Unit,
    primaryTitle: @Composable ColumnScope.() -> Unit,
    secondaryTitle: @Composable ColumnScope.() -> Unit,
    content: @Composable ColumnScope.() -> Unit,
    modifier: Modifier = Modifier,
    rhsTitle: @Composable RowScope.() -> Unit = { },
    reactionRow: @Composable ColumnScope.() -> Unit = {},
    actionRow: (@Composable ColumnScope.() -> Unit)? = null,
    reply: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.Top,
    ) {
        Box(modifier = Modifier.padding(top = 2.dp).clip(CircleShape)) {
            avatar()
        }
        val horizontalPadding = 12.dp
        Column {
            Row(
                modifier = Modifier.padding(horizontal = horizontalPadding).fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(Modifier.weight(1f)) {
                    CompositionLocalProvider(LocalTextStyle provides MaterialTheme.typography.bodyMedium) {
                        primaryTitle()
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    CompositionLocalProvider(
                        LocalTextStyle provides MaterialTheme.typography.labelMedium.copy(
                            color = LocalContentColor.current.slightlyWeaken(),
                        ),
                    ) {
                        secondaryTitle()
                    }
                }
                rhsTitle()
            }
            Spacer(modifier = Modifier.height(12.dp))
            SelectionContainer(
                modifier = Modifier.padding(horizontal = horizontalPadding).fillMaxWidth(),
            ) {
                content()
            }

            SelectionContainer(
                modifier = Modifier
                    .paddingIfNotEmpty(top = 4.dp)
                    .padding(horizontal = horizontalPadding).fillMaxWidth(),
            ) {
                reactionRow()
            }

            if (actionRow != null) {
                SelectionContainer(
                    modifier = Modifier.padding(horizontal = horizontalPadding - 8.dp).fillMaxWidth(),
                ) {
                    actionRow()
                }
            } else {
                Spacer(Modifier.height(8.dp))
            }
            if (reply != null) {
                Surface(
                    modifier = Modifier.padding(horizontal = horizontalPadding)
                        .padding(top = if (actionRow == null) 12.dp else 0.dp),
                    color = MaterialTheme.colorScheme.surface,
                    shape = MaterialTheme.shapes.small,
                ) {
                    reply()
                }
            }
        }
    }
}

/**
 * A state which is read by Comment composable
 */
@Stable
class CommentState(
    val list: Flow<PagingData<UIComment>>,
    countState: State<Int?>,
    private val onSubmitCommentReaction: suspend (comment: UIComment, value: String, selected: Boolean) -> Unit,
    private val backgroundScope: CoroutineScope,
) {
    val count by countState
    private val reactionSubmitFailureChannel = Channel<Throwable>(Channel.BUFFERED)
    val reactionSubmitFailures: Flow<Throwable> = reactionSubmitFailureChannel.receiveAsFlow()

    private val reactionOverrides = mutableStateMapOf<String, List<UICommentReaction>>()
    private val reactionJobs = mutableMapOf<ReactionKey, Job>()

    fun withReactionOverlay(comment: UIComment): UIComment {
        val overrideReactions = reactionOverrides[comment.stableId] ?: return comment
        return comment.copyWithReactions(overrideReactions)
    }

    fun submitReaction(comment: UIComment, value: String) {
        val currentComment = withReactionOverlay(comment)
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
                reactionSubmitFailureChannel.trySend(e)
            } finally {
                if (reactionJobs[key] === coroutineContext[Job]) {
                    reactionJobs.remove(key)
                }
            }
        }
        reactionJobs[key] = job
    }

    private data class ReactionKey(val stableId: String, val value: String)
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
)

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
