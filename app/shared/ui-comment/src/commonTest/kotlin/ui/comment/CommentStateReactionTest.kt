/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.comment

import androidx.compose.runtime.mutableStateOf
import androidx.paging.PagingData
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CommentStateReactionTest {
    @Test
    fun `submit reaction optimistically adds selected reaction`() = runTest {
        val calls = mutableListOf<Pair<String, Boolean>>()
        val state = createState { _, value, selected ->
            calls += value to selected
        }
        val comment = comment()

        state.submitReaction(comment, "bgm1")

        state.withOverlay(comment).reaction("bgm1").let {
            assertEquals(1, it.count)
            assertTrue(it.selected)
        }
        runCurrent()
        assertEquals(listOf("bgm1" to true), calls)
    }

    @Test
    fun `submit selected reaction optimistically removes it when count reaches zero`() = runTest {
        val state = createState { _, _, _ -> }
        val comment = comment(reactions = listOf(UICommentReaction("bgm1", count = 1, selected = true)))

        state.submitReaction(comment, "bgm1")

        assertNull(state.withOverlay(comment).reactions.firstOrNull { it.value == "bgm1" })
    }

    @Test
    fun `failed reaction request rolls back only this value`() = runTest {
        val state = createState { _, _, _ ->
            error("network failed")
        }
        val comment = comment(
            reactions = listOf(
                UICommentReaction("bgm1", count = 1, selected = true),
                UICommentReaction("bgm2", count = 3, selected = false),
            ),
        )

        state.submitReaction(comment, "bgm1")
        assertNull(state.withOverlay(comment).reactions.firstOrNull { it.value == "bgm1" })

        runCurrent()

        state.withOverlay(comment).reaction("bgm1").let {
            assertEquals(1, it.count)
            assertTrue(it.selected)
        }
        state.withOverlay(comment).reaction("bgm2").let {
            assertEquals(3, it.count)
            assertFalse(it.selected)
        }
    }

    @Test
    fun `later click is not rolled back by cancelled older request`() = runTest {
        val firstStarted = CompletableDeferred<Unit>()
        val calls = mutableListOf<Pair<String, Boolean>>()
        var callCount = 0
        val state = createState { _, value, selected ->
            callCount += 1
            if (callCount == 1) {
                firstStarted.complete(Unit)
                awaitCancellation()
            } else {
                calls += value to selected
            }
        }
        val comment = comment(reactions = listOf(UICommentReaction("bgm1", count = 1, selected = false)))

        state.submitReaction(comment, "bgm1")
        runCurrent()
        firstStarted.await()
        state.withOverlay(comment).reaction("bgm1").let {
            assertEquals(2, it.count)
            assertTrue(it.selected)
        }

        state.submitReaction(comment, "bgm1")
        state.withOverlay(comment).reaction("bgm1").let {
            assertEquals(1, it.count)
            assertFalse(it.selected)
        }
        runCurrent()

        assertEquals(listOf("bgm1" to false), calls)
        state.withOverlay(comment).reaction("bgm1").let {
            assertEquals(1, it.count)
            assertFalse(it.selected)
        }
    }

    @Test
    fun `toggle like optimistically increments like count`() = runTest {
        val calls = mutableListOf<UICommentVote?>()
        val state = createState(onSubmitCommentVote = { _, vote -> calls += vote })
        val comment = comment(likeCount = 12, selfVote = null)

        state.toggleVote(comment, UICommentVote.LIKE)

        state.withOverlay(comment).let {
            assertEquals(13, it.likeCount)
            assertEquals(UICommentVote.LIKE, it.selfVote)
        }
        runCurrent()
        assertEquals(listOf<UICommentVote?>(UICommentVote.LIKE), calls)
    }

    @Test
    fun `toggle like again removes vote`() = runTest {
        val calls = mutableListOf<UICommentVote?>()
        val state = createState(onSubmitCommentVote = { _, vote -> calls += vote })
        val comment = comment(likeCount = 12, selfVote = UICommentVote.LIKE)

        state.toggleVote(comment, UICommentVote.LIKE)

        state.withOverlay(comment).let {
            assertEquals(11, it.likeCount)
            assertNull(it.selfVote)
        }
        runCurrent()
        assertEquals(listOf<UICommentVote?>(null), calls)
    }

    @Test
    fun `dislike overrides like and decrements like count`() = runTest {
        val calls = mutableListOf<UICommentVote?>()
        val state = createState(onSubmitCommentVote = { _, vote -> calls += vote })
        val comment = comment(likeCount = 12, selfVote = UICommentVote.LIKE)

        state.toggleVote(comment, UICommentVote.DISLIKE)

        state.withOverlay(comment).let {
            assertEquals(11, it.likeCount)
            assertEquals(UICommentVote.DISLIKE, it.selfVote)
        }
        runCurrent()
        assertEquals(listOf<UICommentVote?>(UICommentVote.DISLIKE), calls)
    }

    @Test
    fun `failed vote request rolls back`() = runTest {
        val state = createState(onSubmitCommentVote = { _, _ -> error("network failed") })
        val comment = comment(likeCount = 12, selfVote = null)

        state.toggleVote(comment, UICommentVote.LIKE)
        assertEquals(13, state.withOverlay(comment).likeCount)

        runCurrent()

        state.withOverlay(comment).let {
            assertEquals(12, it.likeCount)
            assertNull(it.selfVote)
        }
    }

    @Test
    fun `vote does not affect reactions overlay`() = runTest {
        val state = createState()
        val comment = comment(
            reactions = listOf(UICommentReaction("bgm1", count = 1, selected = false)),
            likeCount = 0,
            selfVote = null,
        )

        state.toggleVote(comment, UICommentVote.LIKE)
        state.submitReaction(comment, "bgm1")

        state.withOverlay(comment).let {
            assertEquals(1, it.likeCount)
            assertEquals(UICommentVote.LIKE, it.selfVote)
            assertEquals(2, it.reaction("bgm1").count)
            assertTrue(it.reaction("bgm1").selected)
        }
    }

    @Test
    fun `clearStaleOverlays removes settled vote overlay`() = runTest {
        val state = createState()
        val comment = comment(likeCount = 12, selfVote = null)

        state.toggleVote(comment, UICommentVote.LIKE)
        runCurrent()
        assertEquals(13, state.withOverlay(comment).likeCount)

        state.clearStaleOverlays()

        // 覆盖已清除, 列表回到以 (刷新后的) Paging 数据为准
        state.withOverlay(comment).let {
            assertEquals(12, it.likeCount)
            assertNull(it.selfVote)
        }
    }

    @Test
    fun `clearStaleOverlays keeps in-flight vote overlay`() = runTest {
        val started = CompletableDeferred<Unit>()
        val gate = CompletableDeferred<Unit>()
        val state = createState(
            onSubmitCommentVote = { _, _ ->
                started.complete(Unit)
                gate.await()
            },
        )
        val comment = comment(likeCount = 12, selfVote = null)

        state.toggleVote(comment, UICommentVote.LIKE)
        runCurrent()
        started.await()

        state.clearStaleOverlays()

        state.withOverlay(comment).let {
            assertEquals(13, it.likeCount)
            assertEquals(UICommentVote.LIKE, it.selfVote)
        }
        gate.complete(Unit)
        runCurrent()
    }

    @Test
    fun `clearStaleOverlays removes settled reaction overlay`() = runTest {
        val state = createState()
        val comment = comment()

        state.submitReaction(comment, "bgm1")
        runCurrent()
        assertTrue(state.withOverlay(comment).reaction("bgm1").selected)

        state.clearStaleOverlays()

        assertNull(state.withOverlay(comment).reactions.firstOrNull { it.value == "bgm1" })
    }

    @Test
    fun `clearStaleOverlays keeps in-flight reaction overlay`() = runTest {
        val started = CompletableDeferred<Unit>()
        val gate = CompletableDeferred<Unit>()
        val state = createState(
            onSubmitCommentReaction = { _, _, _ ->
                started.complete(Unit)
                gate.await()
            },
        )
        val comment = comment()

        state.submitReaction(comment, "bgm1")
        runCurrent()
        started.await()

        state.clearStaleOverlays()

        state.withOverlay(comment).reaction("bgm1").let {
            assertEquals(1, it.count)
            assertTrue(it.selected)
        }
        gate.complete(Unit)
        runCurrent()
    }

    private fun TestScope.createState(
        onSubmitCommentVote: suspend (comment: UIComment, vote: UICommentVote?) -> Unit = { _, _ -> },
        onSubmitCommentReaction: suspend (comment: UIComment, value: String, selected: Boolean) -> Unit = { _, _, _ -> },
    ): CommentState {
        return CommentState(
            list = emptyFlow<PagingData<UIComment>>(),
            countState = mutableStateOf(null),
            onSubmitCommentReaction = onSubmitCommentReaction,
            backgroundScope = this,
            onSubmitCommentVote = onSubmitCommentVote,
        )
    }

    private fun comment(
        reactions: List<UICommentReaction> = emptyList(),
        likeCount: Int = 0,
        selfVote: UICommentVote? = null,
    ): UIComment {
        return UIComment(
            id = 1,
            stableId = "ani:1",
            author = null,
            content = UIRichText(emptyList()),
            createdAt = 0,
            reactions = reactions,
            briefReplies = emptyList(),
            replyCount = 0,
            rating = null,
            source = UICommentSource.ANI,
            sourceCommentId = "1",
            canReply = true,
            likeCount = likeCount,
            selfVote = selfVote,
        )
    }

    private fun UIComment.reaction(value: String): UICommentReaction {
        return reactions.first { it.value == value }
    }
}
