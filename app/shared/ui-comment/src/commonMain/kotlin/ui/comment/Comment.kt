/*
 * Copyright (C) 2024 OpenAni and contributors.
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.paging.PagingData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import me.him188.ani.app.data.models.UserInfo
import me.him188.ani.app.tools.MonoTasker
import me.him188.ani.app.ui.foundation.isInDebugMode
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
                Column {
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
                    .paddingIfNotEmpty(top = 8.dp)
                    .padding(horizontal = horizontalPadding).fillMaxWidth(),
            ) {
                reactionRow()
            }

            if (actionRow != null && isInDebugMode()) {
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
    private val onSubmitCommentReaction: suspend (commentId: Long, reactionId: Int) -> Unit,
    backgroundScope: CoroutineScope,
) {
    val count by countState

    private val reactionSubmitTasker = MonoTasker(backgroundScope)

    fun submitReaction(commentId: Long, reactionId: Int) {
        reactionSubmitTasker.launch {
            onSubmitCommentReaction(commentId, reactionId)
        }
    }
}


@Immutable
class UIRichText(val elements: List<UIRichElement>)

@Immutable
class UIComment(
    val id: Long,
    val author: UserInfo?,
    val content: UIRichText,
    val createdAt: Long, // timestamp millis
    val reactions: List<UICommentReaction>,
    val briefReplies: List<UIComment>,
    val replyCount: Int,
    val rating: Int?,
)

@Immutable
class UICommentReaction(
    val id: Int,
    val count: Int,
    val selected: Boolean
)

