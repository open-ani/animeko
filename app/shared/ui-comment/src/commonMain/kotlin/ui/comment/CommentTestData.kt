/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.comment

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.CoroutineScope
import me.him188.ani.app.data.models.UserInfo
import me.him188.ani.app.ui.richtext.UIRichElement
import me.him188.ani.utils.platform.annotations.TestOnly
import me.him188.ani.utils.platform.currentTimeMillis
import kotlin.random.Random
import kotlin.time.Duration.Companion.minutes


@Composable
@TestOnly
fun rememberTestCommentState(commentList: List<UIComment>): CommentState {
    val scope = rememberCoroutineScope()
    return remember {
        createTestCommentState(scope, commentList)
    }
}

@TestOnly
fun createTestCommentState(
    scope: CoroutineScope,
    commentList: List<UIComment> = generateUiComment(10),
) = CommentState(
    sourceVersion = mutableStateOf(Any()),
    list = mutableStateOf(commentList),
    hasMore = mutableStateOf(false),
    onReload = { },
    onLoadMore = { },
    onSubmitCommentReaction = { _, _ -> },
    backgroundScope = scope,
)

@TestOnly
fun generateUiComment(
    size: Int,
    content: UIRichText = UIRichText(
        listOf(
            UIRichElement.AnnotatedText(
                listOf(
                    UIRichElement.Annotated.Text(
                        "${(0..1000).random()}Lorem ipsum dolor sit amet, consectetur adipiscing elit. " +
                                "Integer nec odio. Praesent libero. Sed cursus ante dapibus diam. Sed nisi. Nulla " +
                                "quis sem at nibh elementum imperdiet.",
                    ),
                ),
            ),
        ),
    ),
    generateReply: Boolean = false
): List<UIComment> = buildList {
    repeat(size) { i ->
        add(
            UIComment(
                id = i,
                content = content,
                createdAt = run {
                    currentTimeMillis() - (1..10000).random().minutes.inWholeMilliseconds
                },
                creator = UserInfo(
                    id = (1..100).random(),
                    username = "",
                    nickname = "nickname him188 $i",
                    avatarUrl = "https://picsum.photos/200/300",
                ),
                reactions = buildList {
                    repeat((0..8).random()) {
                        add(UICommentReaction((0..100).random(), (0..100).random(), Random.nextBoolean()))
                    }
                },
                briefReplies = if (generateReply) {
                    generateUiComment((0..3).random(), content, false)
                } else emptyList(),
                replyCount = (0..100).random(),
                rating = (0..10).random(),
            ),
        )
    }
}
