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

        state.withReactionOverlay(comment).reaction("bgm1").let {
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

        assertNull(state.withReactionOverlay(comment).reactions.firstOrNull { it.value == "bgm1" })
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
        assertNull(state.withReactionOverlay(comment).reactions.firstOrNull { it.value == "bgm1" })

        runCurrent()

        state.withReactionOverlay(comment).reaction("bgm1").let {
            assertEquals(1, it.count)
            assertTrue(it.selected)
        }
        state.withReactionOverlay(comment).reaction("bgm2").let {
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
        state.withReactionOverlay(comment).reaction("bgm1").let {
            assertEquals(2, it.count)
            assertTrue(it.selected)
        }

        state.submitReaction(comment, "bgm1")
        state.withReactionOverlay(comment).reaction("bgm1").let {
            assertEquals(1, it.count)
            assertFalse(it.selected)
        }
        runCurrent()

        assertEquals(listOf("bgm1" to false), calls)
        state.withReactionOverlay(comment).reaction("bgm1").let {
            assertEquals(1, it.count)
            assertFalse(it.selected)
        }
    }

    private fun TestScope.createState(
        onSubmitCommentReaction: suspend (comment: UIComment, value: String, selected: Boolean) -> Unit,
    ): CommentState {
        return CommentState(
            list = emptyFlow<PagingData<UIComment>>(),
            countState = mutableStateOf(null),
            onSubmitCommentReaction = onSubmitCommentReaction,
            backgroundScope = this,
        )
    }

    private fun comment(
        reactions: List<UICommentReaction> = emptyList(),
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
        )
    }

    private fun UIComment.reaction(value: String): UICommentReaction {
        return reactions.first { it.value == value }
    }
}
