/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.subject.person

import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import kotlinx.coroutines.CoroutineScope
import me.him188.ani.app.data.models.UserInfo
import me.him188.ani.app.data.models.person.PersonCommentTarget
import me.him188.ani.app.domain.comment.CommentContext
import me.him188.ani.app.domain.comment.CommentSendResult
import me.him188.ani.app.ui.comment.CommentEditorState
import me.him188.ani.app.ui.comment.CommentItemTestTags
import me.him188.ani.app.ui.comment.CommentReportState
import me.him188.ani.app.ui.comment.UIComment
import me.him188.ani.app.ui.comment.UICommentSource
import me.him188.ani.app.ui.comment.UIRichText
import me.him188.ani.app.ui.comment.createTestCommentState
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.foundation.stateOf
import me.him188.ani.app.ui.framework.runAniComposeUiTest
import me.him188.ani.app.ui.richtext.UIRichElement
import me.him188.ani.utils.platform.annotations.TestOnly
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * 人物评论 sheet 的交互: "写评论" 与点击 Ani 评论回复都把编辑器指向正确的目标; Bangumi 评论只读.
 */
@OptIn(TestOnly::class)
class PersonCommentsSheetTest {
    private val target = PersonCommentTarget.Person(42)

    private fun createState(scope: CoroutineScope, comments: List<UIComment>): PeopleCommentsState {
        return PeopleCommentsState(
            target = target,
            commentState = createTestCommentState(scope, comments),
            reportState = CommentReportState(onSubmitReport = { _, _, _ -> }, backgroundScope = scope),
            editorState = CommentEditorState(
                showExpandEditCommentButton = false,
                initialEditExpanded = false,
                panelTitle = stateOf("Person 42"),
                stickers = stateOf(emptyList()),
                richTextRenderer = { UIRichText(emptyList()) },
                onSend = { _, _ -> CommentSendResult.Ok },
                backgroundScope = scope,
            ),
            originalCommentsUrl = "https://bgm.tv/person/42",
            onRefresh = {},
        )
    }

    private fun androidx.compose.ui.test.ComposeUiTest.setSheet(comments: List<UIComment>): () -> PeopleCommentsState {
        lateinit var state: PeopleCommentsState
        setContent {
            ProvideCompositionLocalsForPreview {
                val scope = rememberCoroutineScope()
                state = remember { createState(scope, comments) }
                PersonCommentsSheetContent(state, onClickImage = {})
            }
        }
        return { state }
    }

    @Test
    fun `write comment button starts a new comment for the target`() = runAniComposeUiTest {
        val state = setSheet(listOf(aniComment(), bangumiComment()))

        onNodeWithTag(PersonCommentsSheetTestTags.WriteComment).performClick()
        waitForIdle()

        assertEquals(CommentContext.PersonComment(target), state().editorState.currentSendTarget)
    }

    @Test
    fun `clicking an ani comment starts a reply to it`() = runAniComposeUiTest {
        val state = setSheet(listOf(aniComment(), bangumiComment()))
        assertNull(state().editorState.currentSendTarget)

        onNodeWithText(ANI_CONTENT, substring = true).performClick()
        waitForIdle()

        assertEquals(CommentContext.PersonCommentReply(target, "ani-uuid"), state().editorState.currentSendTarget)
    }

    @Test
    fun `bangumi comments are read-only`() = runAniComposeUiTest {
        val state = setSheet(listOf(bangumiComment()))

        // 只读: 没有点赞/贴纸等操作行, 点击正文也不会进入回复
        onAllNodesWithTag(CommentItemTestTags.Actions).assertCountEquals(0)
        onNodeWithText(BANGUMI_CONTENT, substring = true).performClick()
        waitForIdle()

        assertNull(state().editorState.currentSendTarget)
    }

    private fun aniComment() = UIComment(
        id = 1,
        stableId = "ani:ani-uuid",
        author = UserInfo(id = "user-1", username = null, nickname = "Ani 用户"),
        content = text(ANI_CONTENT),
        createdAt = 1_700_000_000_000L,
        reactions = emptyList(),
        briefReplies = emptyList(),
        replyCount = 0,
        rating = null,
        source = UICommentSource.ANI,
        sourceCommentId = "ani-uuid",
        canReply = true,
        rawContent = ANI_CONTENT,
    )

    private fun bangumiComment() = UIComment(
        id = 2,
        stableId = "bangumi:2",
        author = UserInfo(id = "20", username = null, nickname = "Bangumi 用户"),
        content = text(BANGUMI_CONTENT),
        createdAt = 1_700_000_000_000L,
        reactions = emptyList(),
        briefReplies = emptyList(),
        replyCount = 0,
        rating = null,
        source = UICommentSource.BANGUMI,
        sourceCommentId = "2",
        canReply = false,
        rawContent = BANGUMI_CONTENT,
    )

    private fun text(content: String) = UIRichText(
        listOf(UIRichElement.AnnotatedText(listOf(UIRichElement.Annotated.Text(content)))),
    )

    private companion object {
        const val ANI_CONTENT = "这是一条 Ani 评论"
        const val BANGUMI_CONTENT = "这是一条 Bangumi 评论"
    }
}
