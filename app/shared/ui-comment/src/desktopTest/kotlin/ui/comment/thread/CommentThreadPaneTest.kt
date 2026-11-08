/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.comment.thread

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CompletableDeferred
import me.him188.ani.app.data.models.UserInfo
import me.him188.ani.app.navigation.CommentThreadTarget
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
import kotlin.test.assertTrue
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

    /**
     * @param focusReplyId 通知落地要定位的回复
     * @param paneHeight 限制页面高度, 让回复列表需要滚动; `null` 时占满窗口
     * @param loadRoot 自定义根评论的加载 (例如比回复晚到), 默认立刻返回 [root]
     */
    private fun ComposeUiTest.setPane(
        root: UIComment?,
        replies: List<UIComment> = emptyList(),
        withBack: Boolean = false,
        focusReplyId: String? = null,
        paneHeight: Dp? = null,
        snapshot: UIComment? = root,
        loadRoot: suspend () -> UIComment? = { root },
    ): Harness {
        lateinit var harness: Harness
        setContent {
            ProvideCompositionLocalsForPreview {
                CompositionLocalProvider(
                    LocalTimeFormatter provides TimeFormatter(getTimeNow = { CommentItemTestFixtures.fixedNow }),
                ) {
                    val scope = rememberCoroutineScope()
                    harness = remember {
                        Harness(
                            createTestCommentThreadState(
                                scope,
                                key = CommentThreadKey(CommentThreadTarget.Episode(1, 1), "test:root", focusReplyId),
                                root = root,
                                replies = replies,
                                snapshot = snapshot,
                                loadRoot = loadRoot,
                            ),
                        )
                    }
                    Box(
                        if (paneHeight != null) Modifier.size(width = 480.dp, height = paneHeight) else Modifier.fillMaxSize(),
                    ) {
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
        }
        return harness
    }

    private fun ComposeUiTest.awaitTag(tag: String) {
        waitUntil(timeoutMillis = 10_000) { onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun ComposeUiTest.awaitNoTag(tag: String) {
        waitUntil(timeoutMillis = 10_000) { onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isEmpty() }
    }

    /**
     * [tag] 整个在页面可视区域内, 且没有任何部分被底部回复栏盖住. 两者有一个还没出现时为 `false`.
     */
    private fun ComposeUiTest.isFullyVisibleAboveReplyBar(tag: String): Boolean {
        if (onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isEmpty()) return false
        if (onAllNodes(hasTestTag(CommentThreadTestTags.ReplyBar)).fetchSemanticsNodes().isEmpty()) return false
        val item = onNodeWithTag(tag).getUnclippedBoundsInRoot()
        val bar = onNodeWithTag(CommentThreadTestTags.ReplyBar).getUnclippedBoundsInRoot()
        return item.top >= 0.dp && item.bottom <= bar.top
    }

    private fun manyReplies(count: Int) = (1..count).map { reply("ani:$it", "user$it") }

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

    @Test
    fun `focus scrolls the target reply fully into view`() = runAniComposeUiTest {
        val replies = manyReplies(12)
        setPane(CommentItemTestFixtures.aniComment(), replies, focusReplyId = "ani:12", paneHeight = 400.dp)
        awaitTag(CommentThreadTestTags.reply("ani:12"))

        waitUntil(timeoutMillis = 10_000) { isFullyVisibleAboveReplyBar(CommentThreadTestTags.reply("ani:12")) }
        // 列表确实滚到了末尾: 开头的回复已经不在可视区域里
        onNodeWithTag(CommentThreadTestTags.reply("ani:1")).assertDoesNotExist()
    }

    @Test
    fun `focused last reply stays above the reply bar when the root arrives after the replies`() = runAniComposeUiTest {
        val rootArrival = CompletableDeferred<UIComment?>()
        val replies = manyReplies(12)
        setPane(
            root = null,
            replies = replies,
            focusReplyId = "ani:12",
            paneHeight = 400.dp,
            snapshot = null,
            loadRoot = { rootArrival.await() },
        )
        // 回复先到, 根评论还在加载: 表头只有分组标题, 没有回复栏
        awaitTag(CommentThreadTestTags.RepliesTitle)
        onAllNodesWithTag(CommentThreadTestTags.ReplyBar).assertCountEquals(0)

        rootArrival.complete(CommentItemTestFixtures.aniComment())
        awaitTag(CommentThreadTestTags.ReplyBar)

        waitUntil(timeoutMillis = 10_000) { isFullyVisibleAboveReplyBar(CommentThreadTestTags.reply("ani:12")) }
        assertTrue(isFullyVisibleAboveReplyBar(CommentThreadTestTags.reply("ani:12")))
    }
}
