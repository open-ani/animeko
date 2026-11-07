/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.comment.thread

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import me.him188.ani.app.data.models.UserInfo
import me.him188.ani.app.tools.LocalTimeFormatter
import me.him188.ani.app.tools.TimeFormatter
import me.him188.ani.app.ui.comment.CommentItemTestFixtures
import me.him188.ani.app.ui.comment.CommentItemTestTags
import me.him188.ani.app.ui.comment.UIComment
import me.him188.ani.app.ui.comment.UICommentSource
import me.him188.ani.app.ui.comment.UIRichText
import me.him188.ani.app.ui.comment.createTestCommentThreadState
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.framework.runAniComposeUiTest
import me.him188.ani.app.ui.richtext.UIRichElement
import me.him188.ani.utils.platform.annotations.TestOnly
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.hours

/**
 * thread 页的交互: 回复对象的切换与清除, Bangumi 根评论只读, 根评论不存在与没有回复的占位.
 */
@OptIn(TestOnly::class)
class CommentThreadPaneTest {
    private fun reply(
        stableId: String,
        nickname: String,
        source: UICommentSource = UICommentSource.ANI,
        parentStableId: String? = null,
        replyTo: UserInfo? = null,
    ) = UIComment(
        id = stableId.hashCode().toLong(),
        stableId = stableId,
        author = UserInfo(id = nickname, username = nickname, nickname = nickname, avatarUrl = null),
        content = UIRichText(listOf(UIRichElement.AnnotatedText(listOf(UIRichElement.Annotated.Text("reply $stableId"))))),
        createdAt = (CommentItemTestFixtures.fixedNow - 1.hours).toEpochMilliseconds(),
        reactions = emptyList(),
        briefReplies = emptyList(),
        replyCount = 0,
        rating = null,
        source = source,
        sourceCommentId = stableId.substringAfter(':'),
        canReply = source == UICommentSource.ANI,
        parentStableId = parentStableId,
        replyTo = replyTo,
    )

    private class Harness(val state: CommentThreadState) {
        val replies = mutableListOf<UIComment>()
        var backs = 0
    }

    private fun ComposeUiTest.setPane(
        root: UIComment?,
        replies: List<UIComment> = emptyList(),
        withBack: Boolean = false,
    ): Harness {
        lateinit var harness: Harness
        setContent {
            ProvideCompositionLocalsForPreview {
                CompositionLocalProvider(
                    LocalTimeFormatter provides TimeFormatter(getTimeNow = { CommentItemTestFixtures.fixedNow }),
                ) {
                    val scope = rememberCoroutineScope()
                    harness = remember {
                        Harness(createTestCommentThreadState(scope, root = root, replies = replies, snapshot = root))
                    }
                    CommentThreadPane(
                        state = harness.state,
                        onClickUrl = {},
                        onClickImage = {},
                        onReply = { harness.replies += it },
                        modifier = Modifier.fillMaxSize(),
                        onBack = if (withBack) {
                            { harness.backs++ }
                        } else null,
                    )
                }
            }
        }
        return harness
    }

    private fun ComposeUiTest.awaitTag(tag: String) {
        waitUntil(timeoutMillis = 10_000) { onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun ComposeUiTest.awaitNoTag(tag: String) {
        waitUntil(timeoutMillis = 10_000) { onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isEmpty() }
    }

    @Test
    fun `reply bar targets the root by default`() = runAniComposeUiTest {
        val root = CommentItemTestFixtures.aniComment()
        val harness = setPane(root, replies = listOf(reply("ani:2", "虹夏")))
        awaitTag(CommentThreadTestTags.reply("ani:2"))

        onAllNodesWithTag(CommentThreadTestTags.ReplyTargetChip).assertCountEquals(0)
        onNodeWithTag(CommentThreadTestTags.ReplyBar).performClick()
        runOnIdle { assertEquals(listOf(root.sourceCommentId), harness.replies.map { it.sourceCommentId }) }
    }

    @Test
    fun `tapping a reply targets it and clearing the chip returns to the root`() = runAniComposeUiTest {
        val root = CommentItemTestFixtures.aniComment()
        val target = reply("ani:2", "虹夏")
        val harness = setPane(root, replies = listOf(target))
        awaitTag(CommentThreadTestTags.reply("ani:2"))

        onNodeWithTag(CommentThreadTestTags.reply("ani:2")).performClick()
        awaitTag(CommentThreadTestTags.ReplyTargetChip)
        runOnIdle { assertEquals("2", harness.state.replyTarget?.sourceCommentId) }
        onNodeWithTag(CommentThreadTestTags.ReplyBar).performClick()
        runOnIdle { assertEquals(listOf("2"), harness.replies.map { it.sourceCommentId }) }

        onNodeWithTag(CommentThreadTestTags.ReplyTargetChip).performClick()
        awaitNoTag(CommentThreadTestTags.ReplyTargetChip)
        runOnIdle { assertNull(harness.state.replyTarget) }
        onNodeWithTag(CommentThreadTestTags.ReplyBar).performClick()
        runOnIdle { assertEquals(listOf("2", root.sourceCommentId), harness.replies.map { it.sourceCommentId }) }
    }

    @Test
    fun `tapping the root clears the reply target`() = runAniComposeUiTest {
        val root = CommentItemTestFixtures.aniComment()
        val harness = setPane(root, replies = listOf(reply("ani:2", "虹夏")))
        awaitTag(CommentThreadTestTags.reply("ani:2"))

        onNodeWithTag(CommentThreadTestTags.reply("ani:2")).performClick()
        awaitTag(CommentThreadTestTags.ReplyTargetChip)
        onNodeWithTag(CommentThreadTestTags.Root).performClick()
        awaitNoTag(CommentThreadTestTags.ReplyTargetChip)
        runOnIdle { assertNull(harness.state.replyTarget) }
    }

    @Test
    fun `reply to another reply shows who it replies to`() = runAniComposeUiTest {
        val root = CommentItemTestFixtures.aniComment()
        val first = reply("ani:2", "虹夏")
        val second = reply("ani:3", "波奇", parentStableId = "ani:2", replyTo = first.author)
        setPane(root, replies = listOf(first, second))
        awaitTag(CommentThreadTestTags.reply("ani:3"))

        // 只有回复了另一条回复的那条才标 "回复 @某人"
        onAllNodesWithTag(CommentItemTestTags.ReplyTarget, useUnmergedTree = true).assertCountEquals(1)
    }

    @Test
    fun `bangumi root is read only`() = runAniComposeUiTest {
        val root = CommentItemTestFixtures.bangumiComment()
        val harness = setPane(root, replies = listOf(reply("bangumi:x:2", "波奇酱", source = UICommentSource.BANGUMI)))
        awaitTag(CommentThreadTestTags.reply("bangumi:x:2"))

        onAllNodesWithTag(CommentThreadTestTags.ReplyBar).assertCountEquals(0)
        onNodeWithTag(CommentThreadTestTags.reply("bangumi:x:2")).performClick()
        runOnIdle {
            assertNull(harness.state.replyTarget)
            assertEquals(emptyList(), harness.replies)
        }
    }

    @Test
    fun `missing root shows not found without a reply bar`() = runAniComposeUiTest {
        setPane(root = null)
        awaitTag(CommentThreadTestTags.NotFound)
        onAllNodesWithTag(CommentThreadTestTags.ReplyBar).assertCountEquals(0)
        onAllNodesWithTag(CommentThreadTestTags.Root).assertCountEquals(0)
    }

    @Test
    fun `root without replies shows the empty placeholder`() = runAniComposeUiTest {
        setPane(CommentItemTestFixtures.aniComment())
        awaitTag(CommentThreadTestTags.NoReplies)
        onNodeWithTag(CommentThreadTestTags.Root).assertExists()
        onNodeWithTag(CommentThreadTestTags.RepliesTitle).assertExists()
    }

    @Test
    fun `header back button is shown only when hosted in place`() = runAniComposeUiTest {
        val harness = setPane(CommentItemTestFixtures.aniComment(), withBack = true)
        awaitTag(CommentThreadTestTags.BackButton)
        onNodeWithTag(CommentThreadTestTags.BackButton).performClick()
        runOnIdle { assertEquals(1, harness.backs) }
    }

    @Test
    fun `standalone pane has no header back button`() = runAniComposeUiTest {
        setPane(CommentItemTestFixtures.aniComment(), withBack = false)
        awaitTag(CommentThreadTestTags.Root)
        onAllNodesWithTag(CommentThreadTestTags.BackButton).assertCountEquals(0)
    }
}
