/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.comment.thread

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import me.him188.ani.app.data.models.UserInfo
import me.him188.ani.app.navigation.CommentThreadTarget
import me.him188.ani.app.ui.comment.UIComment
import me.him188.ani.app.ui.comment.UICommentSource
import me.him188.ani.app.ui.comment.UIRichText
import me.him188.ani.app.ui.search.createTestPager
import me.him188.ani.utils.platform.annotations.TestOnly
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame

/**
 * 根评论的加载状态机: 快照先显示、加载结果替换、不存在与失败的区分, 以及发送回复后的收尾.
 */
@OptIn(TestOnly::class)
class CommentThreadStateTest {
    private val key = CommentThreadKey(CommentThreadTarget.Episode(subjectId = 1, episodeId = 2), "ani:root")

    private fun comment(stableId: String, nickname: String = "author") = UIComment(
        id = stableId.hashCode().toLong(),
        stableId = stableId,
        author = UserInfo(id = nickname, username = nickname, nickname = nickname, avatarUrl = null),
        content = UIRichText(emptyList()),
        createdAt = 0,
        reactions = emptyList(),
        briefReplies = emptyList(),
        replyCount = 0,
        rating = null,
        source = UICommentSource.ANI,
        sourceCommentId = stableId.removePrefix("ani:"),
        canReply = true,
    )

    private fun TestScope.createState(
        snapshot: UIComment? = null,
        loadRoot: suspend () -> UIComment?,
    ) = CommentThreadState(
        key = key,
        snapshot = snapshot,
        loadRoot = loadRoot,
        repliesPager = { onTotal ->
            onTotal(0)
            createTestPager(emptyList())
        },
        onSubmitCommentReaction = { _, _, _ -> },
        onSubmitCommentVote = { _, _ -> },
        backgroundScope = backgroundScope,
    )

    @Test
    fun `snapshot shows while loading and loaded root replaces it`() = runTest {
        val snapshot = comment("ani:root")
        val loaded = comment("ani:root")
        val gate = CompletableDeferred<Unit>()
        val state = createState(snapshot) {
            gate.await()
            loaded
        }

        runCurrent()
        val loading = assertIs<CommentThreadRootState.Loading>(state.root.value)
        assertSame(snapshot, loading.snapshot)
        assertSame(snapshot, state.root.value.commentOrNull)

        gate.complete(Unit)
        runCurrent()
        assertSame(loaded, assertIs<CommentThreadRootState.Loaded>(state.root.value).comment)
    }

    @Test
    fun `missing root is not found and shows nothing`() = runTest {
        val state = createState(snapshot = comment("ani:root")) { null }

        runCurrent()
        assertEquals(CommentThreadRootState.NotFound, state.root.value)
        assertNull(state.root.value.commentOrNull)
    }

    @Test
    fun `failure with a snapshot keeps the snapshot and reports once`() = runTest {
        val snapshot = comment("ani:root")
        val failure = IllegalStateException("network")
        val state = createState(snapshot) { throw failure }
        val reported = mutableListOf<Throwable>()
        backgroundScope.launch { state.rootLoadFailures.toList(reported) }

        runCurrent()
        val failed = assertIs<CommentThreadRootState.Failed>(state.root.value)
        assertSame(failure, failed.error)
        assertSame(snapshot, failed.snapshot)
        assertSame(snapshot, state.root.value.commentOrNull)
        assertEquals(listOf<Throwable>(failure), reported)
    }

    @Test
    fun `failure without a snapshot is shown in place and not reported`() = runTest {
        val state = createState(snapshot = null) { throw IllegalStateException("network") }
        val reported = mutableListOf<Throwable>()
        backgroundScope.launch { state.rootLoadFailures.toList(reported) }

        runCurrent()
        val failed = assertIs<CommentThreadRootState.Failed>(state.root.value)
        assertNull(failed.snapshot)
        assertEquals(emptyList(), reported)
    }

    @Test
    fun `refresh reloads the root and a failed refresh keeps the last loaded root`() = runTest {
        val loaded = comment("ani:root")
        var loads = 0
        val state = createState(snapshot = null) {
            loads++
            if (loads == 2) throw IllegalStateException("network") else loaded
        }

        runCurrent()
        assertEquals(1, loads)
        assertIs<CommentThreadRootState.Loaded>(state.root.value)

        state.refresh()
        runCurrent()
        assertEquals(2, loads)
        // 已经加载过的根评论在刷新失败时继续显示, 不退回到错误卡
        assertSame(loaded, assertIs<CommentThreadRootState.Failed>(state.root.value).snapshot)
    }

    @Test
    fun `reply sent resets the target and asks to scroll to the end`() = runTest {
        val state = createState(snapshot = null) { comment("ani:root") }
        val reply = comment("ani:reply", nickname = "replier")
        runCurrent()

        state.selectReplyTarget(reply)
        assertSame(reply, state.replyTarget)
        assertEquals(0, state.scrollToEndRequests)

        state.onReplySent()
        assertNull(state.replyTarget)
        assertEquals(1, state.scrollToEndRequests)
    }
}
