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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasParent
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import me.him188.ani.app.domain.comment.CommentContext
import me.him188.ani.app.domain.comment.CommentImageProcessingStatus
import me.him188.ani.app.domain.comment.CommentImageStatusResult
import me.him188.ani.app.domain.comment.CommentImageUploadResult
import me.him188.ani.app.domain.comment.CommentSendResult
import me.him188.ani.app.domain.comment.PostCommentUseCase
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.foundation.stateOf
import me.him188.ani.app.ui.foundation.widgets.LocalToaster
import me.him188.ani.app.ui.foundation.widgets.Toaster
import me.him188.ani.app.ui.framework.runAniComposeUiTest
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.comment_image_chip
import me.him188.ani.app.ui.lang.comment_image_limit_reached
import me.him188.ani.app.ui.lang.comment_image_processing
import me.him188.ani.app.ui.lang.comment_image_processing_failed
import me.him188.ani.app.ui.lang.comment_image_remove
import me.him188.ani.app.ui.lang.comment_image_retry
import me.him188.ani.app.ui.lang.comment_image_upload_failed_network
import me.him188.ani.app.ui.lang.comment_image_uploading
import me.him188.ani.app.ui.lang.comment_send
import me.him188.ani.app.ui.lang.comment_send_blocked_failed_images
import me.him188.ani.app.ui.lang.comment_upload_image
import me.him188.ani.utils.platform.annotations.TestOnly
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 评论编辑框下方的图片缩略图.
 */
@OptIn(TestOnly::class)
class EditCommentImageStripTest {
    /**
     * 编辑器显示过的 toast, 按先后顺序.
     */
    private val toasts = mutableListOf<String>()

    private fun ComposeUiTest.setEditor(
        onUploadImage: suspend (ByteArray) -> CommentImageUploadResult = { CommentImageUploadResult.Ready(IMAGE_URL) },
        onSend: suspend (CommentContext, String) -> CommentSendResult = { _, _ -> CommentSendResult.Ok },
        onCheckImageStatus: suspend (List<String>) -> CommentImageStatusResult = { CommentImageStatusResult.Unavailable },
    ): CommentEditorState {
        lateinit var state: CommentEditorState
        val toaster = object : Toaster {
            override fun toast(text: String) {
                toasts += text
            }
        }
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
                        onSend = onSend,
                        onUploadImage = onUploadImage,
                        onCheckImageStatus = onCheckImageStatus,
                        backgroundScope = scope,
                    ).apply {
                        startEdit(CommentContext.Episode(subjectId = 1, episodeId = 2))
                    }
                }
                CompositionLocalProvider(LocalToaster provides toaster) {
                    Box(Modifier.size(width = 480.dp, height = 720.dp)) {
                        Column {
                            Spacer(Modifier.height(160.dp))
                            EditComment(state = state)
                        }
                    }
                }
            }
        }
        waitForIdle()
        return state
    }

    /**
     * 等待编辑器显示 [text] 的 toast. 提示的文字在协程中加载, [waitForIdle] 不等待它.
     */
    private fun ComposeUiTest.waitForToast(text: String) {
        waitUntil("toast \"$text\" in $toasts", timeoutMillis = 5_000) { text in toasts }
    }

    /**
     * 评论中是否有上传失败的图片.
     */
    private val CommentEditorState.hasFailedImage: Boolean
        get() = imageThumbnails.any { it.status == CommentImageChipStatus.Failed }

    private fun ComposeUiTest.textField() = onNodeWithTag(EditCommentTestTags.TextField)
    private fun ComposeUiTest.strip() = onNodeWithTag(EditCommentTestTags.ImageStrip)
    private fun ComposeUiTest.toolbar() = onNodeWithTag(EditCommentTestTags.FormattingToolbar)

    /**
     * 第 [number] 张图片的缩略图.
     */
    private fun ComposeUiTest.thumbnail(number: Int) = onNodeWithContentDescription(chipLabel(number))
    private fun ComposeUiTest.removeButton(number: Int) =
        onNodeWithContentDescription(runBlocking { getString(Lang.comment_image_remove, number) })

    /**
     * 第 [number] 张缩略图中的进度指示. 进度指示自成一个语义节点, 不合并进缩略图.
     */
    private fun ComposeUiTest.progressOf(number: Int) = onNode(
        hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate) and hasParent(hasContentDescription(chipLabel(number))),
    )

    private fun chipLabel(number: Int) = runBlocking { getString(Lang.comment_image_chip, number) }
    private fun string(resource: StringResource) = runBlocking { getString(resource) }

    private val CommentEditorState.text get() = textFieldState.text.toString()

    private fun pngImage() = CommentImageFile(PNG.size.toLong()) { PNG }

    private fun hasStateDescription(value: String) =
        SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, value)

    private fun hasRetry() = SemanticsMatcher("has retry") { node ->
        string(Lang.comment_image_retry) in node.config.getOrElse(SemanticsProperties.ContentDescription) { emptyList() }
    }

    @Test
    fun `strip shows the images in order between the text field and the action row`() = runAniComposeUiTest {
        val state = setEditor()
        strip().assertDoesNotExist()

        textField().performTextInput("a [img]https://a.com/1.png[/img] b [img]https://a.com/2.png[/img]")
        waitForIdle()
        strip().assertIsDisplayed()
        val first = thumbnail(1).fetchSemanticsNode().boundsInRoot
        val second = thumbnail(2).fetchSemanticsNode().boundsInRoot
        assertTrue(first.right <= second.left, "thumbnail 1 $first should be left of thumbnail 2 $second")

        val field = textField().fetchSemanticsNode().boundsInRoot
        val stripBounds = strip().fetchSemanticsNode().boundsInRoot
        val send = onNodeWithText(string(Lang.comment_send)).fetchSemanticsNode().boundsInRoot
        assertTrue(stripBounds.top >= field.bottom, "strip $stripBounds should be below the text field $field")
        assertTrue(stripBounds.bottom <= send.top, "strip $stripBounds should be above the send button $send")

        // 预览时不显示, 删除所有图片后不显示
        runOnIdle { state.togglePreview() }
        waitForIdle()
        strip().assertDoesNotExist()
        runOnIdle { state.togglePreview() }
        waitForIdle()
        strip().assertIsDisplayed()
        runOnIdle { state.textFieldState.edit { replace(0, length, "no images") } }
        waitForIdle()
        strip().assertDoesNotExist()
    }

    @Test
    fun `strip stays visible above the action row when the editor is expanded`() = runAniComposeUiTest {
        val state = setEditor()
        textField().performTextInput("[img]https://a.com/1.png[/img]")
        runOnIdle { state.editExpanded = true }
        waitForIdle()

        strip().assertIsDisplayed()
        val stripBounds = strip().fetchSemanticsNode().boundsInRoot
        val field = textField().fetchSemanticsNode().boundsInRoot
        val send = onNodeWithText(string(Lang.comment_send)).fetchSemanticsNode().boundsInRoot
        assertTrue(stripBounds.height > 0f, "strip $stripBounds should have a height")
        assertTrue(stripBounds.top >= field.bottom, "strip $stripBounds should be below the text field $field")
        assertTrue(stripBounds.bottom <= send.top, "strip $stripBounds should be above the send button $send")
    }

    @Test
    fun `numbering follows the images after one is moved`() = runAniComposeUiTest {
        val state = setEditor(onUploadImage = { CommentImageUploadResult.NetworkError })
        textField().performTextInput("[img]$IMAGE_URL[/img] ")
        runOnIdle { state.addImages(listOf(pngImage())) }
        waitUntil(timeoutMillis = 5_000) { state.hasFailedImage }
        waitForIdle()
        thumbnail(1).assert(!hasRetry())
        thumbnail(2).assert(hasRetry())

        // 把上传失败的图片移到开头
        runOnIdle {
            val failed = state.imageChips[1]
            state.textFieldState.edit {
                replace(failed.range.start, failed.range.end, "")
                replace(0, 0, failed.source)
            }
        }
        waitForIdle()
        assertEquals(listOf(CommentImageChipStatus.Failed, CommentImageChipStatus.Ready), state.imageChips.map {
            state.imageChipStatus(it)
        })
        thumbnail(1).assert(hasRetry())
        thumbnail(2).assert(!hasRetry())
        val first = thumbnail(1).fetchSemanticsNode().boundsInRoot
        val second = thumbnail(2).fetchSemanticsNode().boundsInRoot
        assertTrue(first.right <= second.left)
    }

    @Test
    fun `remove button removes that image from the comment`() = runAniComposeUiTest {
        val state = setEditor()
        textField().performTextInput("a [img]https://a.com/1.png[/img] b [img]https://a.com/2.png[/img]")
        waitForIdle()

        removeButton(1).performClick()
        waitForIdle()
        assertEquals("a  b [img]https://a.com/2.png[/img]", state.text)
        thumbnail(1).assertIsDisplayed()
        thumbnail(2).assertDoesNotExist()

        removeButton(1).performClick()
        waitForIdle()
        assertEquals("a  b ", state.text)
        strip().assertDoesNotExist()
    }

    @Test
    fun `clicking a thumbnail selects its image in the text field`() = runAniComposeUiTest {
        val state = setEditor()
        textField().performTextInput("a [img]https://a.com/1.png[/img] b [img]https://a.com/2.png[/img] c")
        waitForIdle()
        toolbar().assertDoesNotExist()

        thumbnail(2).performMouseInput { click() }
        waitForIdle()
        assertEquals(state.imageChips[1].range, state.textFieldState.selection)
        textField().assertIsFocused()
        toolbar().assertIsDisplayed()
    }

    @Test
    fun `thumbnail shows the upload progress and a failed upload is retried when clicked`() = runAniComposeUiTest {
        val firstAttempt = CompletableDeferred<CommentImageUploadResult>()
        var attempts = 0
        val state = setEditor(
            onUploadImage = {
                if (++attempts == 1) firstAttempt.await() else CommentImageUploadResult.Ready(IMAGE_URL)
            },
        )
        textField().performTextInput("hi ")
        runOnIdle { state.addImages(listOf(pngImage())) }
        waitForIdle()
        thumbnail(1).assert(hasStateDescription(string(Lang.comment_image_uploading)))

        firstAttempt.complete(CommentImageUploadResult.NetworkError)
        waitUntil(timeoutMillis = 5_000) { state.hasFailedImage }
        waitForIdle()
        thumbnail(1).assert(hasRetry())
        thumbnail(1).assert(hasStateDescription(string(Lang.comment_image_upload_failed_network)))
        waitForToast(string(Lang.comment_image_upload_failed_network))

        thumbnail(1).performClick()
        waitUntil(timeoutMillis = 5_000) { state.text == "hi [img]$IMAGE_URL[/img]" }
        waitForIdle()
        assertEquals(2, attempts)
        thumbnail(1).assert(!hasRetry())
        thumbnail(1).assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.StateDescription))
        assertEquals(listOf(string(Lang.comment_image_upload_failed_network)), toasts)
    }

    @Test
    fun `processing image shows its own progress and send waits until it is ready`() = runAniComposeUiTest {
        val ready = AtomicBoolean(false)
        val checks = AtomicInteger(0)
        val state = setEditor(
            onUploadImage = { CommentImageUploadResult.Processing(IMAGE_ID, IMAGE_URL) },
            onCheckImageStatus = { ids ->
                checks.incrementAndGet()
                val status = if (ready.get()) {
                    CommentImageProcessingStatus.Ready(IMAGE_URL)
                } else {
                    CommentImageProcessingStatus.Processing
                }
                CommentImageStatusResult.Ok(ids.associateWith { status })
            },
        )
        textField().performTextInput("hi ")
        runOnIdle { state.addImages(listOf(pngImage())) }
        waitUntil(timeoutMillis = 5_000) { state.imageThumbnails.singleOrNull()?.status == CommentImageChipStatus.Processing }
        waitForIdle()
        thumbnail(1).assert(hasStateDescription(string(Lang.comment_image_processing)))
        progressOf(1).assertExists()
        thumbnail(1).assert(!hasRetry())
        onNodeWithText(string(Lang.comment_send)).assertIsNotEnabled()

        // 查询到仍在处理时保持不变
        waitUntil(timeoutMillis = 5_000) { checks.get() >= 1 }
        waitForIdle()
        thumbnail(1).assert(hasStateDescription(string(Lang.comment_image_processing)))
        onNodeWithText(string(Lang.comment_send)).assertIsNotEnabled()

        ready.set(true)
        waitUntil(timeoutMillis = 10_000) { state.text == "hi [img]$IMAGE_URL[/img]" }
        waitForIdle()
        thumbnail(1).assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.StateDescription))
        progressOf(1).assertDoesNotExist()
        onNodeWithText(string(Lang.comment_send)).assertIsEnabled()
    }

    @Test
    fun `failed processing shows its error and clicking the thumbnail uploads the image again`() =
        runAniComposeUiTest {
            val uploads = AtomicInteger(0)
            val state = setEditor(
                onUploadImage = {
                    if (uploads.incrementAndGet() == 1) {
                        CommentImageUploadResult.Processing(IMAGE_ID, IMAGE_URL)
                    } else {
                        CommentImageUploadResult.Ready(IMAGE_URL)
                    }
                },
                onCheckImageStatus = { ids ->
                    CommentImageStatusResult.Ok(
                        ids.associateWith {
                            CommentImageProcessingStatus.Failed(CommentImageUploadResult.ProcessingFailed)
                        },
                    )
                },
            )
            textField().performTextInput("hi ")
            runOnIdle { state.addImages(listOf(pngImage())) }
            waitUntil(timeoutMillis = 10_000) { state.imageThumbnails.any { it.error == CommentImageUploadResult.ProcessingFailed } }
            waitForIdle()
            val message = string(Lang.comment_image_processing_failed)
            thumbnail(1).assert(hasRetry())
            thumbnail(1).assert(hasStateDescription(message))
            waitForToast(message)

            thumbnail(1).performClick()
            waitUntil(timeoutMillis = 5_000) { state.text == "hi [img]$IMAGE_URL[/img]" }
            waitForIdle()
            assertEquals(2, uploads.get())
            assertEquals(listOf(message), toasts)
            onNodeWithText(string(Lang.comment_send)).assertIsEnabled()
        }

    @Test
    fun `send with a failed image toasts instead of sending`() = runAniComposeUiTest {
        var sent: String? = null
        val state = setEditor(
            onUploadImage = { CommentImageUploadResult.NetworkError },
            onSend = { _, content ->
                sent = content
                CommentSendResult.Ok
            },
        )
        textField().performTextInput("hi ")
        runOnIdle { state.addImages(listOf(pngImage())) }
        waitUntil(timeoutMillis = 5_000) { state.hasFailedImage }
        waitForIdle()

        onNodeWithText(string(Lang.comment_send)).performClick()
        waitForToast(string(Lang.comment_send_blocked_failed_images))
        assertEquals(null, sent)

        removeButton(1).performClick()
        waitForIdle()
        assertEquals("hi ", state.text)
        onNodeWithText(string(Lang.comment_send)).performClick()
        waitUntil(timeoutMillis = 5_000) { sent != null }
        assertEquals("hi ", sent)
        assertEquals(
            listOf(string(Lang.comment_image_upload_failed_network), string(Lang.comment_send_blocked_failed_images)),
            toasts,
        )
    }

    @Test
    fun `upload button shows the limit when the comment is full`() = runAniComposeUiTest {
        val state = setEditor()
        textField().performTextInput("[img]https://a.com/x.png[/img]".repeat(PostCommentUseCase.MAX_IMAGES))
        waitForIdle()
        assertEquals(0, state.remainingImageSlots)
        val message = runBlocking { getString(Lang.comment_image_limit_reached, PostCommentUseCase.MAX_IMAGES) }
        assertTrue(toasts.isEmpty())

        onNodeWithContentDescription(string(Lang.comment_upload_image)).performClick()
        waitForToast(message)

        removeButton(1).performClick()
        waitForIdle()
        assertEquals(PostCommentUseCase.MAX_IMAGES - 1, state.imageChips.size)
        assertEquals(listOf(message), toasts)
    }

    private companion object {
        const val IMAGE_URL = "https://img.myani.org/comments/1.webp"
        const val IMAGE_ID = "1111111111111111111111111111111111111111111111111111111111111111"
        val PNG = byteArrayOf(137.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 13, 10, 26, 10)
    }
}
