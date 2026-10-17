/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.comment

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextInputSelection
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import me.him188.ani.app.domain.comment.CommentContext
import me.him188.ani.app.domain.comment.CommentImageStatusResult
import me.him188.ani.app.domain.comment.CommentImageUploadResult
import me.him188.ani.app.domain.comment.CommentSendResult
import me.him188.ani.app.domain.comment.UploadCommentImageUseCase
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.foundation.stateOf
import me.him188.ani.app.ui.framework.runAniComposeUiTest
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.comment_add_emoji
import me.him188.ani.app.ui.lang.comment_bold
import me.him188.ani.app.ui.lang.comment_image_processing_failed
import me.him188.ani.app.ui.lang.comment_image_upload_already_in_comment
import me.him188.ani.app.ui.lang.comment_image_upload_forbidden
import me.him188.ani.app.ui.lang.comment_image_upload_invalid_format
import me.him188.ani.app.ui.lang.comment_image_upload_not_logged_in
import me.him188.ani.app.ui.lang.comment_image_upload_pending_limit
import me.him188.ani.app.ui.lang.comment_image_upload_too_large
import me.him188.ani.app.ui.lang.comment_italic
import me.him188.ani.app.ui.lang.comment_link
import me.him188.ani.app.ui.lang.comment_mask
import me.him188.ani.app.ui.lang.comment_send
import me.him188.ani.app.ui.lang.comment_send_blocked_failed_images
import me.him188.ani.app.ui.lang.comment_send_failed_image_processing
import me.him188.ani.app.ui.lang.comment_send_failed_invalid_image
import me.him188.ani.app.ui.lang.comment_strikethrough
import me.him188.ani.app.ui.lang.comment_underline
import me.him188.ani.app.ui.lang.comment_upload_image
import me.him188.ani.utils.platform.annotations.TestOnly
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 评论编辑器: 操作行、选区格式工具栏、粘贴自动识别链接与图片上传.
 */
@OptIn(TestOnly::class)
class EditCommentTest {
    private class Editor(
        val state: CommentEditorState,
        val scope: CoroutineScope,
    )

    private fun ComposeUiTest.setEditor(
        topSpacing: Int = 160,
        onUploadImage: suspend (ByteArray) -> CommentImageUploadResult = { CommentImageUploadResult.Ready(IMAGE_URL) },
        onSend: suspend (CommentContext, String) -> CommentSendResult = { _, _ -> CommentSendResult.Ok },
    ): Editor {
        lateinit var editor: Editor
        setContent {
            ProvideCompositionLocalsForPreview {
                val scope = rememberCoroutineScope()
                editor = remember {
                    Editor(
                        CommentEditorState(
                            showExpandEditCommentButton = false,
                            initialEditExpanded = false,
                            panelTitle = stateOf(null),
                            stickers = stateOf(emptyList()),
                            richTextRenderer = { UIRichText(emptyList()) },
                            onSend = onSend,
                            onUploadImage = onUploadImage,
                            onCheckImageStatus = { CommentImageStatusResult.Unavailable },
                            backgroundScope = scope,
                        ).apply {
                            startEdit(CommentContext.Episode(subjectId = 1, episodeId = 2))
                        },
                        scope,
                    )
                }
                Box(Modifier.size(width = 480.dp, height = 720.dp)) {
                    Column {
                        // 留出选区上方的空间, 桌面端工具栏放在选区上方
                        Spacer(Modifier.height(topSpacing.dp))
                        EditComment(state = editor.state)
                    }
                }
            }
        }
        waitForIdle()
        return editor
    }

    private fun string(resource: StringResource) = runBlocking { getString(resource) }

    private fun ComposeUiTest.textField() = onNodeWithTag(EditCommentTestTags.TextField)
    private fun ComposeUiTest.toolbar() = onNodeWithTag(EditCommentTestTags.FormattingToolbar)

    private val CommentEditorState.text get() = textFieldState.text.toString()

    private fun ComposeUiTest.textLayoutResult(): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        val action = textField().fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action
        assertTrue(action?.invoke(results) == true, "text layout is not available")
        return results.single()
    }

    @Test
    fun `action row has no formatting or more buttons`() = runAniComposeUiTest {
        setEditor()

        onNodeWithContentDescription(string(Lang.comment_add_emoji)).assertExists()
        onNodeWithContentDescription(string(Lang.comment_upload_image)).assertIsEnabled()
        onNodeWithText(string(Lang.comment_send)).assertIsEnabled()

        for (resource in listOf(
            Lang.comment_bold, Lang.comment_italic, Lang.comment_underline,
            Lang.comment_strikethrough, Lang.comment_mask, Lang.comment_link,
        )) {
            onAllNodesWithContentDescription(string(resource)).assertCountEquals(0)
        }
        onAllNodesWithContentDescription("More comment editor actions").assertCountEquals(0)
        onAllNodesWithContentDescription("更多评论编辑功能").assertCountEquals(0)
        toolbar().assertDoesNotExist()
    }

    @Test
    fun `toolbar shows for a selection and hides when the selection collapses`() = runAniComposeUiTest {
        setEditor()
        textField().performTextInput("hello world")
        textField().assertIsFocused()
        toolbar().assertDoesNotExist()

        textField().performTextInputSelection(TextRange(0, 5))
        waitForIdle()
        toolbar().assertIsDisplayed()

        textField().performTextInputSelection(TextRange(3))
        waitForIdle()
        toolbar().assertDoesNotExist()
    }

    @Test
    fun `toolbar is placed above the selected line on desktop`() = runAniComposeUiTest {
        setEditor(topSpacing = 160)
        textField().performTextInput("hello world")
        textField().performTextInputSelection(TextRange(0, 5))
        waitForIdle()

        val field = textField().fetchSemanticsNode().boundsInWindow
        val toolbar = toolbar().fetchSemanticsNode().boundsInWindow
        // 第一行文字从编辑框顶部的内边距开始
        val firstLineTop = field.top + with(density) { ContentPadding.calculateTopPadding().toPx() }
        assertEquals(
            firstLineTop - with(density) { ToolbarSpacing.toPx() },
            toolbar.bottom,
            absoluteTolerance = PositionTolerancePx,
            "toolbar $toolbar should be right above the first line of $field",
        )
    }

    @Test
    fun `toolbar follows the selected line when the text is scrolled`() = runAniComposeUiTest {
        val editor = setEditor(topSpacing = 160)
        // 收起时最多显示 3 行, 输入后光标在末尾, 编辑框滚动到底
        textField().performTextInput("l1\nl2\nl3\nl4\nl5\nl6")
        val lastLineStart = editor.state.text.lastIndexOf('\n') + 1
        textField().performTextInputSelection(TextRange(lastLineStart, editor.state.text.length))
        waitForIdle()

        val field = textField().fetchSemanticsNode().boundsInWindow
        val toolbar = toolbar().fetchSemanticsNode().boundsInWindow
        val layout = textLayoutResult()
        val (paddingTop, viewportHeight) = with(density) {
            val top = ContentPadding.calculateTopPadding().toPx()
            top to field.height - top - ContentPadding.calculateBottomPadding().toPx()
        }
        val scrollOffset = layout.size.height - viewportHeight
        assertTrue(scrollOffset > 0, "text should be scrolled, layout ${layout.size}, viewport $viewportHeight")
        val lastLineVisibleTop = field.top + paddingTop + layout.getLineTop(layout.lineCount - 1) - scrollOffset

        assertEquals(
            lastLineVisibleTop - with(density) { ToolbarSpacing.toPx() },
            toolbar.bottom,
            absoluteTolerance = PositionTolerancePx,
            "toolbar $toolbar should be right above the last line of $field",
        )
        assertTrue(toolbar.bottom in field.top..field.bottom, "toolbar $toolbar should be inside $field")
    }

    @Test
    fun `toolbar hides while the selection is scrolled out of view`() = runAniComposeUiTest {
        val editor = setEditor(topSpacing = 160)
        // 收起时最多显示 3 行. 选中第一行后编辑框滚动到顶部
        textField().performTextInput("l1\nl2\nl3\nl4\nl5\nl6")
        textField().performTextInputSelection(TextRange(0, 2))
        waitForIdle()
        toolbar().assertIsDisplayed()

        // 用滚轮滚动不改变选区
        textField().performMouseInput {
            moveTo(center)
            scroll(ScrollDelta)
        }
        waitForIdle()
        assertEquals(TextRange(0, 2), editor.state.textFieldState.selection)
        toolbar().assertDoesNotExist()

        textField().performMouseInput {
            moveTo(center)
            scroll(-ScrollDelta)
        }
        waitForIdle()
        toolbar().assertIsDisplayed()
    }

    @Test
    fun `toolbar hides in preview`() = runAniComposeUiTest {
        val editor = setEditor()
        textField().performTextInput("hello world")
        textField().performTextInputSelection(TextRange(0, 5))
        waitForIdle()
        toolbar().assertIsDisplayed()

        runOnIdle { editor.state.togglePreview() }
        waitForIdle()
        toolbar().assertDoesNotExist()
    }

    @Test
    fun `toolbar flips below the selection when there is no room above`() = runAniComposeUiTest {
        setEditor(topSpacing = 0)
        textField().performTextInput("hello world")
        textField().performTextInputSelection(TextRange(0, 5))
        waitForIdle()

        val field = textField().fetchSemanticsNode().boundsInWindow
        val toolbar = toolbar().fetchSemanticsNode().boundsInWindow
        assertTrue(
            toolbar.top >= field.top + with(density) { ContentPadding.calculateTopPadding().toPx() },
            "toolbar $toolbar should be below the first line of $field",
        )
    }

    @Test
    fun `clicking bold wraps the selection and keeps it selected`() = runAniComposeUiTest {
        val editor = setEditor()
        textField().performTextInput("hello world")
        textField().performTextInputSelection(TextRange(0, 5))
        waitForIdle()

        val bold = onNodeWithContentDescription(string(Lang.comment_bold))
        bold.assertIsOff()
        bold.performMouseInput { click() }
        waitForIdle()

        assertEquals("[b]hello[/b] world", editor.state.text)
        assertEquals(TextRange(3, 8), editor.state.textFieldState.selection)
        textField().assertIsFocused()
        toolbar().assertIsDisplayed()
        onNodeWithContentDescription(string(Lang.comment_bold)).assertIsOn()

        // 连续叠加另一种格式, 再次点击加粗则去掉加粗
        onNodeWithContentDescription(string(Lang.comment_italic)).performMouseInput { click() }
        waitForIdle()
        assertEquals("[b][i]hello[/i][/b] world", editor.state.text)
        onNodeWithContentDescription(string(Lang.comment_italic)).performMouseInput { click() }
        onNodeWithContentDescription(string(Lang.comment_bold)).performMouseInput { click() }
        waitForIdle()
        assertEquals("hello world", editor.state.text)
        assertEquals(TextRange(0, 5), editor.state.textFieldState.selection)
    }

    @Test
    fun `clicking link on plain text leaves the cursor for the address`() = runAniComposeUiTest {
        val editor = setEditor()
        textField().performTextInput("docs")
        textField().performTextInputSelection(TextRange(0, 4))
        waitForIdle()

        onNodeWithContentDescription(string(Lang.comment_link)).performMouseInput { click() }
        waitForIdle()
        assertEquals("[url=]docs[/url]", editor.state.text)
        assertEquals(TextRange(5), editor.state.textFieldState.selection)
        toolbar().assertDoesNotExist()

        // 在 `=` 后粘贴的地址填入链接地址, 不包 [url], 括号按属性值编码
        textField().performTextInput("https://en.wikipedia.org/wiki/A_(B)")
        waitForIdle()
        assertEquals("[url=https://en.wikipedia.org/wiki/A_%28B%29]docs[/url]", editor.state.text)
    }

    @Test
    fun `inserted url is wrapped`() = runAniComposeUiTest {
        val editor = setEditor()
        textField().performTextInput("see https://a.com/x 吧")
        waitForIdle()
        assertEquals("see [url]https://a.com/x[/url] 吧", editor.state.text)
        assertEquals(TextRange(editor.state.text.length), editor.state.textFieldState.selection)
    }

    @Test
    fun `url inserted over a selection makes it a link`() = runAniComposeUiTest {
        val editor = setEditor()
        textField().performTextInput("read docs")
        textField().performTextInputSelection(TextRange(5, 9))
        textField().performTextInput("https://a.com")
        waitForIdle()
        assertEquals("read [url=https://a.com]docs[/url]", editor.state.text)
    }

    @Test
    fun `typing is not affected by link detection`() = runAniComposeUiTest {
        val editor = setEditor()
        for (char in "https://a.com") {
            textField().performTextInput(char.toString())
        }
        waitForIdle()
        assertEquals("https://a.com", editor.state.text)
    }

    @Test
    fun `uploading image keeps the upload button enabled and disables send`() = runAniComposeUiTest {
        val response = CompletableDeferred<CommentImageUploadResult>()
        val editor = setEditor(onUploadImage = { response.await() })
        textField().performTextInput("hi ")

        runOnIdle { editor.state.addImages(listOf(pngImage())) }
        waitUntil(timeoutMillis = 5_000) { editor.state.uploadingImages }
        waitForIdle()
        onNodeWithText(string(Lang.comment_send)).assertIsNotEnabled()
        // 上传期间可以继续添加图片
        onNodeWithContentDescription(string(Lang.comment_upload_image)).assertIsEnabled()

        response.complete(CommentImageUploadResult.Ready(IMAGE_URL))
        waitUntil(timeoutMillis = 5_000) { editor.state.text == "hi [img]$IMAGE_URL[/img]" }
        waitForIdle()
        onNodeWithText(string(Lang.comment_send)).assertIsEnabled()
    }

    @Test
    fun `upload errors are shown below the text field`() = runAniComposeUiTest {
        var result: CommentImageUploadResult = CommentImageUploadResult.InvalidFormat
        val editor = setEditor(onUploadImage = { result })

        for ((error, message) in listOf(
            CommentImageUploadResult.InvalidFormat to Lang.comment_image_upload_invalid_format,
            CommentImageUploadResult.NotLoggedIn to Lang.comment_image_upload_not_logged_in,
            CommentImageUploadResult.Forbidden to Lang.comment_image_upload_forbidden,
            CommentImageUploadResult.PendingLimit to Lang.comment_image_upload_pending_limit,
            CommentImageUploadResult.ProcessingFailed to Lang.comment_image_processing_failed,
        )) {
            result = error
            runOnIdle { editor.state.addImages(listOf(pngImage())) }
            waitUntil(timeoutMillis = 5_000) { editor.state.imageUploadError == error }
            waitForIdle()
            onNodeWithText(string(message)).assertIsDisplayed()
        }

        runOnIdle {
            editor.state.addImages(
                listOf(
                    CommentImageFile(UploadCommentImageUseCase.MAX_IMAGE_SIZE_BYTES + 1) {
                        error("must not read a file that is too large")
                    },
                ),
            )
        }
        waitForIdle()
        onNodeWithText(string(Lang.comment_image_upload_too_large)).assertIsDisplayed()
    }

    @Test
    fun `uploading the expired image again replaces the send error with a notice`() = runAniComposeUiTest {
        val editor = setEditor(onSend = { _, _ -> CommentSendResult.InvalidImage })
        textField().performTextInput("look [img]$IMAGE_URL[/img]")
        editor.scope.launch { editor.state.send(EmptyCoroutineContext) }
        waitForIdle()
        onNodeWithText(string(Lang.comment_send_failed_invalid_image)).assertIsDisplayed()

        runOnIdle { editor.state.addImages(listOf(pngImage())) }
        waitUntil(timeoutMillis = 5_000) { editor.state.uploadedImageAlreadyInComment }
        waitForIdle()
        onNodeWithText(string(Lang.comment_image_upload_already_in_comment)).assertIsDisplayed()
        onNodeWithText(string(Lang.comment_send_failed_invalid_image)).assertDoesNotExist()
        assertEquals("look [img]$IMAGE_URL[/img]", editor.state.text)
    }

    @Test
    fun `comment rejected for an image still processing shows the send error`() = runAniComposeUiTest {
        val editor = setEditor(onSend = { _, _ -> CommentSendResult.ImageProcessing })
        textField().performTextInput("look [img]$IMAGE_URL[/img]")
        editor.scope.launch { editor.state.send(EmptyCoroutineContext) }
        waitForIdle()
        onNodeWithText(string(Lang.comment_send_failed_image_processing)).assertIsDisplayed()
        assertEquals("look [img]$IMAGE_URL[/img]", editor.state.text)
    }

    @Test
    fun `send blocked by a failed image shows the message in the expanded preview`() = runAniComposeUiTest {
        var sent = false
        val editor = setEditor(onSend = { _, _ -> sent = true; CommentSendResult.Ok })
        // 不属于本次编辑的占位标签, 视为上传失败
        textField().performTextInput("hi ${pendingImageTag("elsewhere-1")}")
        runOnIdle {
            editor.state.editExpanded = true
            editor.state.togglePreview()
        }
        waitForIdle()

        onNodeWithText(string(Lang.comment_send)).performClick()
        waitForIdle()
        assertTrue(editor.state.failedImagesBlockSend)
        onNodeWithText(string(Lang.comment_send_blocked_failed_images)).assertIsDisplayed()
        assertFalse(sent)
    }

    @Test
    fun `images keep uploading after the editor is closed`() = runAniComposeUiTest {
        var attempts = 0
        val response = CompletableDeferred<CommentImageUploadResult>()
        var showEditor by mutableStateOf(true)
        lateinit var state: CommentEditorState
        setContent {
            ProvideCompositionLocalsForPreview {
                val scope = rememberCoroutineScope()
                state = remember {
                    CommentEditorState(
                        showExpandEditCommentButton = false,
                        initialEditExpanded = false,
                        panelTitle = stateOf(null),
                        stickers = stateOf(emptyList()),
                        richTextRenderer = { UIRichText(emptyList()) },
                        onSend = { _, _ -> CommentSendResult.Ok },
                        onUploadImage = {
                            attempts++
                            response.await()
                        },
                        onCheckImageStatus = { CommentImageStatusResult.Unavailable },
                        backgroundScope = scope,
                    ).apply {
                        startEdit(CommentContext.Episode(subjectId = 1, episodeId = 2))
                    }
                }
                Box(Modifier.size(width = 480.dp, height = 720.dp)) {
                    if (showEditor) EditComment(state = state)
                }
            }
        }
        waitForIdle()
        runOnIdle { state.addImages(listOf(pngImage())) }
        waitUntil(timeoutMillis = 5_000) { attempts == 1 }

        // 关闭编辑框, 正在进行的上传不中断
        showEditor = false
        waitForIdle()
        textField().assertDoesNotExist()
        response.complete(CommentImageUploadResult.Ready(IMAGE_URL))
        waitUntil(timeoutMillis = 5_000) { state.textFieldState.text.toString() == "[img]$IMAGE_URL[/img]" }
        assertEquals(1, attempts)
    }

    private fun pngImage() = CommentImageFile(PNG.size.toLong()) { PNG }

    private companion object {
        const val IMAGE_URL = "https://img.myani.org/comments/1.webp"
        val PNG = byteArrayOf(137.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 13, 10, 26, 10)

        /**
         * 编辑框的内边距. [EditCommentDefaults.CommentTextField] 没有标签, 使用无标签时的默认值.
         */
        val ContentPadding = OutlinedTextFieldDefaults.contentPaddingWithoutLabel()

        /**
         * 工具栏与选区之间的距离.
         */
        val ToolbarSpacing = 8.dp

        /**
         * 坐标取整造成的误差.
         */
        const val PositionTolerancePx = 2f

        /**
         * 足以把编辑框滚动到一端的滚轮距离.
         */
        const val ScrollDelta = 100f
    }
}
