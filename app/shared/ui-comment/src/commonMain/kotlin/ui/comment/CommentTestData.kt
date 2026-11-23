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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.paging.LoadState
import androidx.paging.LoadStates
import androidx.paging.PagingData
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.flow
import me.him188.ani.app.data.models.UserInfo
import me.him188.ani.app.navigation.CommentThreadTarget
import me.him188.ani.app.ui.comment.thread.CommentThreadKey
import me.him188.ani.app.ui.comment.thread.CommentThreadState
import me.him188.ani.app.ui.foundation.stateOf
import me.him188.ani.app.ui.richtext.UIRichElement
import me.him188.ani.app.ui.search.createTestPager
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
    list = createTestPager(commentList),
    countState = stateOf(commentList.size),
    onSubmitCommentReaction = { _, _, _ -> },
    backgroundScope = scope,
)

/**
 * 用固定数据构造的 thread 状态: 根评论与回复都不走网络.
 *
 * @param root 加载到的根评论, `null` 表示不存在
 * @param loadRoot 自定义根评论的加载过程 (例如让它比回复晚到), 默认立刻返回 [root]
 * @param loadReplies 自定义回复首页的加载过程, 每次 (重新) 加载都调用一次, 抛出的异常成为分页的加载错误; 默认立刻返回 [replies]
 */
@TestOnly
fun createTestCommentThreadState(
    scope: CoroutineScope,
    key: CommentThreadKey = CommentThreadKey(CommentThreadTarget.Episode(1, 1), "test:root"),
    root: UIComment?,
    replies: List<UIComment> = emptyList(),
    snapshot: UIComment? = null,
    loadRoot: suspend () -> UIComment? = { root },
    loadReplies: suspend () -> List<UIComment> = { replies },
): CommentThreadState = CommentThreadState(
    key = key,
    snapshot = snapshot,
    loadRoot = loadRoot,
    repliesPager = { onTotal ->
        flow {
            val loaded = try {
                loadReplies()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                emit(PagingData.from(emptyList<UIComment>(), sourceLoadStates = singlePageLoadStates(refresh = LoadState.Error(e))))
                return@flow
            }
            onTotal(loaded.size)
            // 带 "已到末页" 的加载状态, 让页面像真实分页加载完一样显示空占位
            emit(
                PagingData.from(
                    loaded,
                    sourceLoadStates = singlePageLoadStates(refresh = LoadState.NotLoading(endOfPaginationReached = true)),
                ),
            )
        }
    },
    onSubmitCommentReaction = { _, _, _ -> },
    onSubmitCommentVote = { _, _ -> },
    backgroundScope = scope,
)

/**
 * 只有一页的分页状态: 前后都已到末页, 首页的状态是 [refresh].
 */
private fun singlePageLoadStates(refresh: LoadState) = LoadStates(
    refresh = refresh,
    prepend = LoadState.NotLoading(endOfPaginationReached = true),
    append = LoadState.NotLoading(endOfPaginationReached = true),
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
                id = i.toLong(),
                stableId = "test:$i",
                content = content,
                createdAt = run {
                    currentTimeMillis() - (1..10000).random().minutes.inWholeMilliseconds
                },
                author = UserInfo(
                    id = (1..100).random().toString(),
                    username = "",
                    nickname = "nickname him188 $i",
                    avatarUrl = "https://picsum.photos/200/300",
                ),
                reactions = buildList {
                    repeat((0..8).random()) {
                        add(UICommentReaction("bgm${(1..125).random()}", (0..100).random(), Random.nextBoolean()))
                    }
                },
                briefReplies = if (generateReply) {
                    generateUiComment((0..3).random(), content, false)
                } else emptyList(),
                replyCount = (0..100).random(),
                rating = (0..10).random(),
                canReply = true,
            ),
        )
    }
}
