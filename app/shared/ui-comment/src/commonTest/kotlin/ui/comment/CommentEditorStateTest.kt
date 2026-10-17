/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.comment

import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.text.TextRange
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import me.him188.ani.app.domain.comment.CommentContext
import me.him188.ani.app.domain.comment.CommentImageProcessingStatus
import me.him188.ani.app.domain.comment.CommentImageStatusResult
import me.him188.ani.app.domain.comment.CommentImageUploadResult
import me.him188.ani.app.domain.comment.CommentSendResult
import me.him188.ani.app.domain.comment.PostCommentUseCase
import me.him188.ani.app.domain.comment.UploadCommentImageUseCase
import me.him188.ani.app.ui.foundation.getClipEntryText
import me.him188.ani.app.ui.foundation.setClipEntryText
import me.him188.ani.app.ui.foundation.stateOf
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class CommentEditorStateTest {
    private fun TestScope.createState(
        richTextRenderer: suspend (String) -> UIRichText = { UIRichText(emptyList()) },
        onSend: suspend (CommentContext, String) -> CommentSendResult = { _, _ -> CommentSendResult.Ok },
        transcodeImage: (ByteArray) -> ByteArray? = { null },
        onCheckImageStatus: suspend (List<String>) -> CommentImageStatusResult = { CommentImageStatusResult.Unavailable },
        onUploadImage: suspend (ByteArray) -> CommentImageUploadResult = { CommentImageUploadResult.Ready(IMAGE_URL) },
    ) = CommentEditorState(
        showExpandEditCommentButton = false,
        initialEditExpanded = false,
        panelTitle = stateOf(null),
        stickers = stateOf(emptyList()),
        richTextRenderer = richTextRenderer,
        onSend = onSend,
        onUploadImage = onUploadImage,
        onCheckImageStatus = onCheckImageStatus,
        backgroundScope = backgroundScope,
        transcodeImage = transcodeImage,
        // 处理图片的后台任务在测试的调度器上运行, 读取和上传图片不切换线程
        uiContext = EmptyCoroutineContext,
        imageUploadContext = EmptyCoroutineContext,
        // 图片处理了多久按虚拟时间计算
        timeSource = testScheduler.timeSource,
    )

    /**
     * 把全局快照中的修改通知给处理图片的后台任务 (应用中由 Compose 完成), 然后运行所有可以运行的协程.
     * [advanceUntilIdle] 不运行 backgroundScope 中的协程, 所以用 [runCurrent].
     */
    private fun TestScope.settle() {
        Snapshot.sendApplyNotifications()
        runCurrent()
    }

    /**
     * 先 [settle], 再让虚拟时间前进 [duration], 运行期间到时的协程 (包括 backgroundScope 中的).
     */
    private fun TestScope.advance(duration: Duration) {
        settle()
        advanceTimeBy(duration)
        settle()
    }

    private fun image(
        bytes: ByteArray = PNG,
        size: Long? = bytes.size.toLong(),
        read: () -> ByteArray = { bytes },
    ) = CommentImageFile(size) { read() }

    private val CommentEditorState.text get() = textFieldState.text.toString()
    private val CommentEditorState.uploadIds get() = imageChips.map { checkNotNull(it.uploadId) }
    private val CommentEditorState.statuses get() = imageChips.map { imageChipStatus(it) }

    /**
     * 每次上传都等待测试给出结果的上传函数.
     */
    private class ControlledUploads {
        val requests = mutableListOf<ByteArray>()
        private val responses = mutableListOf<CompletableDeferred<CommentImageUploadResult>>()

        suspend fun upload(image: ByteArray): CommentImageUploadResult {
            val response = CompletableDeferred<CommentImageUploadResult>()
            requests += image
            responses += response
            return response.await()
        }

        fun complete(index: Int, result: CommentImageUploadResult) {
            responses[index].complete(result)
        }
    }

    /**
     * 查询图片处理状态的函数. 记录每次查询的图片和虚拟时间, 按 [answer] 回答.
     */
    private class StatusChecks(private val now: () -> Long) {
        val requests = mutableListOf<List<String>>()
        val times = mutableListOf<Long>()

        /**
         * 每张图片的状态. 默认一直在处理.
         */
        var status: (id: String) -> CommentImageProcessingStatus = { CommentImageProcessingStatus.Processing }

        /**
         * 整次查询的结果, 默认由 [status] 得出.
         */
        var answer: (ids: List<String>) -> CommentImageStatusResult = { ids ->
            CommentImageStatusResult.Ok(ids.associateWith { status(it) })
        }

        suspend fun check(ids: List<String>): CommentImageStatusResult {
            requests += ids
            times += now()
            return answer(ids)
        }
    }

    private fun TestScope.statusChecks() = StatusChecks { testScheduler.currentTime }

    @Test
    fun `added images are inserted at the cursor and uploaded one at a time in order`() = runTest {
        val uploads = ControlledUploads()
        val state = createState(onUploadImage = uploads::upload)
        state.textFieldState.setTextAndPlaceCursorAtEnd("ab")
        state.textFieldState.edit { selection = TextRange(1) }

        state.addImages(listOf(image(PNG), image(PNG_2)))
        settle()
        val ids = state.uploadIds
        assertEquals(2, ids.toSet().size)
        assertEquals("a${pendingImageTag(ids[0])}${pendingImageTag(ids[1])}b", state.text)
        assertEquals(TextRange(state.text.length - 1), state.textFieldState.selection)
        assertEquals(listOf(CommentImageChipStatus.Uploading, CommentImageChipStatus.Uploading), state.statuses)
        assertTrue(state.uploadingImages)
        // 第一张上传完成前不开始第二张
        assertEquals(1, uploads.requests.size)
        assertContentEquals(PNG, uploads.requests[0])

        uploads.complete(0, CommentImageUploadResult.Ready(IMAGE_URL))
        settle()
        assertEquals("a[img]$IMAGE_URL[/img]${pendingImageTag(ids[1])}b", state.text)
        assertEquals(listOf(CommentImageChipStatus.Ready, CommentImageChipStatus.Uploading), state.statuses)
        assertEquals(2, uploads.requests.size)
        assertContentEquals(PNG_2, uploads.requests[1])

        uploads.complete(1, CommentImageUploadResult.Ready(IMAGE_URL_2))
        settle()
        assertEquals("a[img]$IMAGE_URL[/img][img]$IMAGE_URL_2[/img]b", state.text)
        assertFalse(state.uploadingImages)
        assertNull(state.imageUploadError)
    }

    @Test
    fun `images added during an upload are queued after it`() = runTest {
        val uploads = ControlledUploads()
        val state = createState(onUploadImage = uploads::upload)
        state.addImages(listOf(image(PNG)))
        settle()
        state.addImages(listOf(image(PNG_2)))
        settle()
        assertEquals(1, uploads.requests.size)

        uploads.complete(0, CommentImageUploadResult.Ready(IMAGE_URL))
        settle()
        assertEquals(2, uploads.requests.size)
        assertContentEquals(PNG_2, uploads.requests[1])
    }

    @Test
    fun `upload with a selection inserts after the selection without deleting it`() = runTest {
        val state = createState()
        state.textFieldState.setTextAndPlaceCursorAtEnd("abc")
        state.textFieldState.edit { selection = TextRange(0, 2) }
        state.addImages(listOf(image()))
        settle()
        assertEquals("ab[img]$IMAGE_URL[/img]c", state.text)
    }

    @Test
    fun `uploaded image replaces every copy of its pending tag wherever it was moved`() = runTest {
        val uploads = ControlledUploads()
        val state = createState(onUploadImage = uploads::upload)
        state.textFieldState.setTextAndPlaceCursorAtEnd("x")
        state.addImages(listOf(image()))
        settle()
        val tag = pendingImageTag(state.uploadIds.single())

        // 把标签移到开头, 再粘贴一份到末尾
        state.textFieldState.edit { replace(0, length, "$tag x $tag") }
        settle()
        assertEquals(listOf(CommentImageChipStatus.Uploading, CommentImageChipStatus.Uploading), state.statuses)

        uploads.complete(0, CommentImageUploadResult.Ready(IMAGE_URL))
        settle()
        assertEquals("[img]$IMAGE_URL[/img] x [img]$IMAGE_URL[/img]", state.text)
        assertEquals(1, uploads.requests.size)
    }

    @Test
    fun `result of an upload whose pending tag was deleted is dropped and the tag stands for it when restored`() =
        runTest {
            val uploads = ControlledUploads()
            var sent: String? = null
            val state = createState(
                onSend = { _, content ->
                    sent = content
                    CommentSendResult.Ok
                },
                onUploadImage = uploads::upload,
            )
            state.startEdit(CommentContext.Episode(subjectId = 1, episodeId = 2))
            state.addImages(listOf(image()))
            settle()
            val tag = pendingImageTag(state.uploadIds.single())

            state.removeImageChip(1)
            settle()
            uploads.complete(0, CommentImageUploadResult.Ready(IMAGE_URL))
            settle()
            assertEquals("", state.text)

            // 撤销删除, 占位标签重新出现, 它代表已上传的图片, 不会重复上传
            state.textFieldState.undoState.undo()
            settle()
            assertEquals(tag, state.text)
            assertEquals(listOf(CommentImageChipStatus.Ready), state.statuses)
            assertEquals(IMAGE_URL, state.imageThumbnails.single().url)
            assertFalse(state.uploadingImages)
            assertEquals(1, uploads.requests.size)

            assertTrue(state.send(EmptyCoroutineContext))
            assertEquals("[img]$IMAGE_URL[/img]", sent)
        }

    @Test
    fun `queued image whose tag was cut is uploaded after the tag is pasted back`() = runTest {
        val uploads = ControlledUploads()
        val clipboard = FakeClipboard()
        val state = createState(onUploadImage = uploads::upload)
        state.addImages(listOf(image(PNG), image(PNG_2)))
        settle()
        val ids = state.uploadIds

        state.selectImageChip(2)
        state.cutSelection(clipboard)
        settle()
        uploads.complete(0, CommentImageUploadResult.Ready(IMAGE_URL))
        settle()
        assertEquals("[img]$IMAGE_URL[/img]", state.text)
        // 标签不在文本中的图片不上传
        assertEquals(1, uploads.requests.size)
        assertFalse(state.uploadingImages)

        state.textFieldState.edit { selection = TextRange(0) }
        state.paste(clipboard)
        settle()
        assertEquals("${pendingImageTag(ids[1])}[img]$IMAGE_URL[/img]", state.text)
        assertEquals(2, uploads.requests.size)
        assertContentEquals(PNG_2, uploads.requests[1])

        uploads.complete(1, CommentImageUploadResult.Ready(IMAGE_URL_2))
        settle()
        assertEquals("[img]$IMAGE_URL_2[/img][img]$IMAGE_URL[/img]", state.text)
    }

    @Test
    fun `failed upload keeps the chip and is retried without reading the file again`() = runTest {
        var reads = 0
        val uploaded = mutableListOf<ByteArray>()
        var result: CommentImageUploadResult = CommentImageUploadResult.NetworkError
        val state = createState {
            uploaded += it
            result
        }
        state.textFieldState.setTextAndPlaceCursorAtEnd("hi ")
        state.addImages(listOf(image(read = { reads++; PNG })))
        settle()
        val uploadId = state.uploadIds.single()
        assertEquals("hi ${pendingImageTag(uploadId)}", state.text)
        assertEquals(listOf(CommentImageChipStatus.Failed), state.statuses)
        assertEquals(CommentImageUploadResult.NetworkError, state.imageUploadError)
        assertTrue(state.imageThumbnails.single().canRetry)
        assertEquals(CommentImageUploadResult.NetworkError, state.imageThumbnails.single().error)
        assertFalse(state.uploadingImages)

        result = CommentImageUploadResult.Ready(IMAGE_URL)
        state.retryImageUpload(uploadId)
        settle()
        assertEquals(1, reads)
        assertEquals(2, uploaded.size)
        assertContentEquals(PNG, uploaded[1])
        assertEquals("hi [img]$IMAGE_URL[/img]", state.text)
        assertNull(state.imageUploadError)
    }

    @Test
    fun `too large image fails without reading it and cannot be retried`() = runTest {
        var read = false
        var uploaded = false
        val state = createState { uploaded = true; CommentImageUploadResult.Ready(IMAGE_URL) }
        state.addImages(
            listOf(image(size = UploadCommentImageUseCase.MAX_IMAGE_SIZE_BYTES + 1, read = { read = true; PNG })),
        )
        settle()
        assertFalse(read)
        assertFalse(uploaded)
        assertFalse(state.uploadingImages)
        assertEquals(listOf(CommentImageChipStatus.Failed), state.statuses)
        assertEquals(CommentImageUploadResult.TooLarge, state.imageUploadError)
        assertFalse(state.imageThumbnails.single().canRetry)

        state.retryImageUpload(state.uploadIds.single())
        settle()
        assertFalse(read)
        assertEquals(listOf(CommentImageChipStatus.Failed), state.statuses)
    }

    @Test
    fun `image of unknown size is checked after reading`() = runTest {
        var uploaded = false
        val state = createState { uploaded = true; CommentImageUploadResult.Ready(IMAGE_URL) }
        val bytes = PNG.copyOf(UploadCommentImageUseCase.MAX_IMAGE_SIZE_BYTES.toInt() + 1)
        state.addImages(listOf(image(bytes, size = null)))
        settle()
        assertFalse(uploaded)
        assertEquals(CommentImageUploadResult.TooLarge, state.imageUploadError)
        assertFalse(state.imageThumbnails.single().canRetry)
    }

    @Test
    fun `image of exactly the limit is uploaded`() = runTest {
        val state = createState()
        state.addImages(listOf(image(size = UploadCommentImageUseCase.MAX_IMAGE_SIZE_BYTES)))
        settle()
        assertNull(state.imageUploadError)
        assertEquals("[img]$IMAGE_URL[/img]", state.text)
    }

    @Test
    fun `supported image is uploaded without transcoding`() = runTest {
        var uploaded: ByteArray? = null
        val state = createState(transcodeImage = { error("must not transcode a supported image") }) {
            uploaded = it
            CommentImageUploadResult.Ready(IMAGE_URL)
        }
        state.addImages(listOf(image()))
        settle()
        assertContentEquals(PNG, uploaded)
        assertNull(state.imageUploadError)
    }

    @Test
    fun `unsupported image is transcoded before upload`() = runTest {
        var transcoded: ByteArray? = null
        var uploaded: ByteArray? = null
        val state = createState(
            transcodeImage = {
                transcoded = it
                JPEG
            },
        ) {
            uploaded = it
            CommentImageUploadResult.Ready(IMAGE_URL)
        }
        state.addImages(listOf(image(HEIC)))
        settle()
        assertContentEquals(HEIC, transcoded)
        assertContentEquals(JPEG, uploaded)
        assertNull(state.imageUploadError)
        assertEquals("[img]$IMAGE_URL[/img]", state.text)
    }

    @Test
    fun `retrying a failed transcoded image uploads the transcoded image`() = runTest {
        var transcodes = 0
        val uploaded = mutableListOf<ByteArray>()
        val state = createState(transcodeImage = { transcodes++; JPEG }) {
            uploaded += it
            if (uploaded.size == 1) CommentImageUploadResult.RateLimited else CommentImageUploadResult.Ready(IMAGE_URL)
        }
        state.addImages(listOf(image(HEIC)))
        settle()
        state.retryImageUpload(state.uploadIds.single())
        settle()
        assertEquals(1, transcodes)
        assertContentEquals(JPEG, uploaded[1])
        assertEquals("[img]$IMAGE_URL[/img]", state.text)
    }

    @Test
    fun `image that cannot be transcoded is an invalid format`() = runTest {
        var uploaded = false
        val state = createState(transcodeImage = { null }) {
            uploaded = true
            CommentImageUploadResult.Ready(IMAGE_URL)
        }
        state.addImages(listOf(image(HEIC)))
        settle()
        assertFalse(uploaded)
        assertFalse(state.uploadingImages)
        assertEquals(CommentImageUploadResult.InvalidFormat, state.imageUploadError)
        assertFalse(state.imageThumbnails.single().canRetry)
    }

    @Test
    fun `exception while uploading becomes an unknown error that can be retried`() = runTest {
        var reads = 0
        val state = createState()
        state.addImages(listOf(image(read = { if (++reads == 1) throw IllegalStateException("boom") else PNG })))
        settle()
        assertTrue(state.imageUploadError is CommentImageUploadResult.UnknownError)
        assertTrue(state.imageThumbnails.single().canRetry)

        // 第一次没有读到内容, 重试时重新读取文件
        state.retryImageUpload(state.uploadIds.single())
        settle()
        assertEquals(2, reads)
        assertEquals("[img]$IMAGE_URL[/img]", state.text)
    }

    @Test
    fun `upload error shows the latest failure among the images still in the comment`() = runTest {
        val results = mutableListOf<CommentImageUploadResult>(
            CommentImageUploadResult.RateLimited,
            CommentImageUploadResult.NetworkError,
        )
        val state = createState { results.removeFirst() }
        state.addImages(listOf(image(PNG), image(PNG_2)))
        settle()
        assertEquals(CommentImageUploadResult.NetworkError, state.imageUploadError)

        state.removeImageChip(2)
        assertEquals(CommentImageUploadResult.RateLimited, state.imageUploadError)
        state.removeImageChip(1)
        assertNull(state.imageUploadError)
    }

    @Test
    fun `images beyond the limit are not added and the limit is shown`() = runTest {
        val state = createState()
        val existing = (1..8).joinToString(" ") { "[img]https://a.com/$it.png[/img]" }
        state.textFieldState.setTextAndPlaceCursorAtEnd(existing)
        assertEquals(2, state.remainingImageSlots)
        assertFalse(state.showImageLimitMessage)

        state.addImages(listOf(image(), image(), image()))
        assertEquals(PostCommentUseCase.MAX_IMAGES, state.imageChips.size)
        assertEquals(0, state.remainingImageSlots)
        assertTrue(state.showImageLimitMessage)

        // 删除一张后图片数少于上限, 不提示
        state.removeImageChip(1)
        assertFalse(state.showImageLimitMessage)
        assertEquals(1, state.remainingImageSlots)
    }

    @Test
    fun `limit message is shown only when the comment is full`() = runTest {
        val state = createState()
        state.requestImageLimitMessage()
        assertFalse(state.showImageLimitMessage)

        val full = "[img]https://a.com/x.png[/img]".repeat(PostCommentUseCase.MAX_IMAGES)
        state.textFieldState.setTextAndPlaceCursorAtEnd(full)
        state.requestImageLimitMessage()
        assertTrue(state.showImageLimitMessage)
        state.startEdit(CommentContext.Episode(subjectId = 1, episodeId = 3))
        assertFalse(state.showImageLimitMessage)
    }

    @Test
    fun `send is disabled while images are uploading`() = runTest {
        val uploads = ControlledUploads()
        var sent: String? = null
        val state = createState(
            onSend = { _, content ->
                sent = content
                CommentSendResult.Ok
            },
            onUploadImage = uploads::upload,
        )
        state.startEdit(CommentContext.Episode(subjectId = 1, episodeId = 2))
        state.textFieldState.setTextAndPlaceCursorAtEnd("hi ")
        state.addImages(listOf(image()))
        settle()

        assertTrue(state.uploadingImages)
        assertFalse(state.send(EmptyCoroutineContext))
        assertNull(sent)
        assertFalse(state.failedImagesBlockSend)

        uploads.complete(0, CommentImageUploadResult.Ready(IMAGE_URL))
        settle()
        assertTrue(state.send(EmptyCoroutineContext))
        assertEquals("hi [img]$IMAGE_URL[/img]", sent)
    }

    @Test
    fun `send is blocked while failed images remain and never sends pending tags`() = runTest {
        var sent: String? = null
        val state = createState(
            onSend = { _, content ->
                sent = content
                CommentSendResult.Ok
            },
            onUploadImage = { CommentImageUploadResult.NetworkError },
        )
        state.startEdit(CommentContext.Episode(subjectId = 1, episodeId = 2))
        state.textFieldState.setTextAndPlaceCursorAtEnd("hi ")
        state.addImages(listOf(image()))
        settle()
        assertFalse(state.failedImagesBlockSend)

        assertFalse(state.send(EmptyCoroutineContext))
        assertNull(sent)
        assertTrue(state.failedImagesBlockSend)
        assertNull(state.sendResult)

        state.removeImageChip(1)
        assertFalse(state.failedImagesBlockSend)
        assertTrue(state.send(EmptyCoroutineContext))
        assertEquals("hi ", sent)
    }

    @Test
    fun `pending tag from another editing session is a failed image`() = runTest {
        var sent = false
        val state = createState(onSend = { _, _ -> sent = true; CommentSendResult.Ok })
        state.startEdit(CommentContext.Episode(subjectId = 1, episodeId = 2))
        state.textFieldState.setTextAndPlaceCursorAtEnd("a ${pendingImageTag("elsewhere-1")}")
        settle()
        assertEquals(listOf(CommentImageChipStatus.Failed), state.statuses)
        assertFalse(state.imageThumbnails.single().canRetry)
        assertNull(state.imageUploadError)

        assertFalse(state.send(EmptyCoroutineContext))
        assertFalse(sent)
        assertTrue(state.failedImagesBlockSend)
    }

    @Test
    fun `preview shows uploaded images and hides pending ones`() = runTest {
        val rendered = mutableListOf<String>()
        val state = createState(
            richTextRenderer = {
                rendered += it
                UIRichText(emptyList())
            },
        )
        state.textFieldState.setTextAndPlaceCursorAtEnd("a ${pendingImageTag("elsewhere-1")} b")
        state.renderPreview()
        assertEquals(listOf("a  b"), rendered)
    }

    @Test
    fun `upload finishing during preview renders the preview again`() = runTest {
        val rendered = mutableListOf<String>()
        val uploads = ControlledUploads()
        val state = createState(
            richTextRenderer = {
                rendered += it
                UIRichText(emptyList())
            },
            onUploadImage = uploads::upload,
        )
        state.textFieldState.setTextAndPlaceCursorAtEnd("hi ")
        state.addImages(listOf(image()))
        settle()

        state.togglePreview()
        state.renderPreview()
        assertEquals(listOf("hi "), rendered)

        uploads.complete(0, CommentImageUploadResult.Ready(IMAGE_URL))
        settle()
        assertEquals(listOf("hi ", "hi [img]$IMAGE_URL[/img]"), rendered)
        assertNotNull(state.previewContent)
    }

    @Test
    fun `upload finishing while editing does not render the preview`() = runTest {
        val rendered = mutableListOf<String>()
        val state = createState(
            richTextRenderer = {
                rendered += it
                UIRichText(emptyList())
            },
        )
        state.addImages(listOf(image()))
        settle()
        assertEquals("[img]$IMAGE_URL[/img]", state.text)
        assertTrue(rendered.isEmpty())
    }

    @Test
    fun `uploading an image already in the comment removes its pending tag`() = runTest {
        val state = createState()
        state.textFieldState.setTextAndPlaceCursorAtEnd("a [img]$IMAGE_URL[/img] b ")
        state.addImages(listOf(image()))
        settle()
        assertEquals("a [img]$IMAGE_URL[/img] b ", state.text)
        assertTrue(state.uploadedImageAlreadyInComment)
        assertNull(state.imageUploadError)

        // 选择新的图片时清除提示
        state.textFieldState.setTextAndPlaceCursorAtEnd("")
        state.addImages(listOf(image()))
        assertFalse(state.uploadedImageAlreadyInComment)
        settle()
        assertEquals("[img]$IMAGE_URL[/img]", state.text)
        assertFalse(state.uploadedImageAlreadyInComment)
    }

    @Test
    fun `image whose address is only in a link is inserted`() = runTest {
        val state = createState()
        state.textFieldState.setTextAndPlaceCursorAtEnd("[url]$IMAGE_URL[/url] ")
        state.addImages(listOf(image()))
        settle()
        assertEquals("[url]$IMAGE_URL[/url] [img]$IMAGE_URL[/img]", state.text)
        assertFalse(state.uploadedImageAlreadyInComment)
    }

    @Test
    fun `uploading the expired image again clears the invalid image error`() = runTest {
        val state = createState(onSend = { _, _ -> CommentSendResult.InvalidImage })
        state.startEdit(CommentContext.Episode(subjectId = 1, episodeId = 2))
        state.textFieldState.setTextAndPlaceCursorAtEnd("look [img]$IMAGE_URL[/img]")
        assertFalse(state.send(EmptyCoroutineContext))
        assertEquals(CommentSendResult.InvalidImage, state.sendResult)

        state.addImages(listOf(image()))
        settle()
        assertEquals("look [img]$IMAGE_URL[/img]", state.text)
        assertEquals(1, state.imageChips.size)
        assertNull(state.sendResult)
        assertTrue(state.uploadedImageAlreadyInComment)
    }

    @Test
    fun `uploading an image already in the comment keeps other send errors`() = runTest {
        val state = createState(onSend = { _, _ -> CommentSendResult.TooManyImages })
        state.startEdit(CommentContext.Episode(subjectId = 1, episodeId = 2))
        state.textFieldState.setTextAndPlaceCursorAtEnd("[img]$IMAGE_URL[/img]")
        assertFalse(state.send(EmptyCoroutineContext))

        state.addImages(listOf(image()))
        settle()
        assertEquals(CommentSendResult.TooManyImages, state.sendResult)
        assertTrue(state.uploadedImageAlreadyInComment)
    }

    @Test
    fun `image already in the comment notice is cleared by a successful send and a new target`() = runTest {
        val state = createState()
        state.startEdit(CommentContext.Episode(subjectId = 1, episodeId = 2))
        state.textFieldState.setTextAndPlaceCursorAtEnd("[img]$IMAGE_URL[/img]")
        state.addImages(listOf(image()))
        settle()
        assertTrue(state.uploadedImageAlreadyInComment)
        assertTrue(state.send(EmptyCoroutineContext))
        assertFalse(state.uploadedImageAlreadyInComment)

        state.textFieldState.setTextAndPlaceCursorAtEnd("[img]$IMAGE_URL[/img]")
        state.addImages(listOf(image()))
        settle()
        assertTrue(state.uploadedImageAlreadyInComment)
        state.startEdit(CommentContext.Episode(subjectId = 1, episodeId = 3))
        assertFalse(state.uploadedImageAlreadyInComment)
    }

    @Test
    fun `bytes of a failed image are released when its chip is removed and the file is read again on retry`() =
        runTest {
            var reads = 0
            var result: CommentImageUploadResult = CommentImageUploadResult.NetworkError
            val state = createState { result }
            state.addImages(listOf(image(read = { reads++; PNG })))
            settle()
            assertEquals(listOf(CommentImageChipStatus.Failed), state.statuses)

            state.removeImageChip(1)
            settle()
            state.textFieldState.undoState.undo()
            settle()
            assertTrue(state.imageThumbnails.single().canRetry)

            result = CommentImageUploadResult.Ready(IMAGE_URL)
            state.retryImageUpload(state.uploadIds.single())
            settle()
            assertEquals(2, reads)
            assertEquals("[img]$IMAGE_URL[/img]", state.text)
        }

    @Test
    fun `queued retry whose chip is removed reads the file again when the chip comes back`() = runTest {
        var reads = 0
        val uploads = ControlledUploads()
        val state = createState(onUploadImage = uploads::upload)
        state.addImages(listOf(image(read = { reads++; PNG }), image(PNG_2)))
        settle()
        val ids = state.uploadIds
        uploads.complete(0, CommentImageUploadResult.NetworkError)
        settle()
        // 第二张正在上传, 重试的第一张排在它后面
        state.retryImageUpload(ids[0])
        settle()
        state.removeImageChip(1)
        settle()
        uploads.complete(1, CommentImageUploadResult.Ready(IMAGE_URL_2))
        settle()
        assertEquals(2, uploads.requests.size)
        assertEquals(1, reads)

        // 粘贴回占位标签
        state.textFieldState.edit { replace(0, 0, pendingImageTag(ids[0])) }
        settle()
        assertEquals(3, uploads.requests.size)
        assertEquals(2, reads)
        assertContentEquals(PNG, uploads.requests[2])
        uploads.complete(2, CommentImageUploadResult.Ready(IMAGE_URL))
        settle()
        assertEquals("[img]$IMAGE_URL[/img][img]$IMAGE_URL_2[/img]", state.text)
    }

    @Test
    fun `upload failing after its chip was removed keeps no bytes`() = runTest {
        var reads = 0
        val uploads = ControlledUploads()
        val state = createState(onUploadImage = uploads::upload)
        state.addImages(listOf(image(read = { reads++; PNG })))
        settle()
        val tag = pendingImageTag(state.uploadIds.single())
        state.removeImageChip(1)
        settle()
        uploads.complete(0, CommentImageUploadResult.NetworkError)
        settle()

        state.textFieldState.edit { replace(0, length, tag) }
        settle()
        assertEquals(listOf(CommentImageChipStatus.Failed), state.statuses)
        assertTrue(state.imageThumbnails.single().canRetry)
        state.retryImageUpload(state.uploadIds.single())
        settle()
        assertEquals(2, reads)
        uploads.complete(1, CommentImageUploadResult.Ready(IMAGE_URL))
        settle()
        assertEquals("[img]$IMAGE_URL[/img]", state.text)
    }

    @Test
    fun `failed images message is only about the images that blocked the send`() = runTest {
        val state = createState { CommentImageUploadResult.NetworkError }
        state.startEdit(CommentContext.Episode(subjectId = 1, episodeId = 2))
        state.addImages(listOf(image()))
        settle()
        assertFalse(state.send(EmptyCoroutineContext))
        assertTrue(state.failedImagesBlockSend)

        state.removeImageChip(1)
        assertFalse(state.failedImagesBlockSend)
        // 之后添加的图片上传失败时不显示, 再次发送时才显示
        state.addImages(listOf(image()))
        settle()
        assertEquals(listOf(CommentImageChipStatus.Failed), state.statuses)
        assertFalse(state.failedImagesBlockSend)
        assertFalse(state.send(EmptyCoroutineContext))
        assertTrue(state.failedImagesBlockSend)
    }

    @Test
    fun `limit message does not come back when the comment is full again`() = runTest {
        val state = createState()
        val full = "[img]https://a.com/x.png[/img]".repeat(PostCommentUseCase.MAX_IMAGES)
        state.textFieldState.setTextAndPlaceCursorAtEnd(full)
        state.requestImageLimitMessage()
        settle()
        assertTrue(state.showImageLimitMessage)

        state.removeImageChip(1)
        settle()
        assertFalse(state.showImageLimitMessage)
        state.textFieldState.setTextAndPlaceCursorAtEnd(full)
        settle()
        assertFalse(state.showImageLimitMessage)

        state.requestImageLimitMessage()
        settle()
        assertTrue(state.showImageLimitMessage)
    }

    @Test
    fun `preview keeps the previous content while it is rendered again after an upload`() = runTest {
        val renders = mutableListOf<CompletableDeferred<UIRichText>>()
        val uploads = ControlledUploads()
        val state = createState(
            richTextRenderer = { CompletableDeferred<UIRichText>().also { renders += it }.await() },
            onUploadImage = uploads::upload,
        )
        state.textFieldState.setTextAndPlaceCursorAtEnd("hi ")
        state.addImages(listOf(image()))
        settle()
        state.togglePreview()
        launch { state.renderPreview() }
        runCurrent()
        assertNull(state.previewContent)
        val first = UIRichText(emptyList())
        renders[0].complete(first)
        runCurrent()
        assertSame(first, state.previewContent)

        uploads.complete(0, CommentImageUploadResult.Ready(IMAGE_URL))
        settle()
        assertEquals(2, renders.size)
        assertSame(first, state.previewContent)
        val second = UIRichText(emptyList())
        renders[1].complete(second)
        settle()
        assertSame(second, state.previewContent)
    }

    @Test
    fun `an earlier preview render finishing last does not replace a later one`() = runTest {
        val renders = mutableListOf<CompletableDeferred<UIRichText>>()
        val rendered = mutableListOf<String>()
        val uploads = ControlledUploads()
        val state = createState(
            richTextRenderer = {
                rendered += it
                CompletableDeferred<UIRichText>().also { renders += it }.await()
            },
            onUploadImage = uploads::upload,
        )
        state.textFieldState.setTextAndPlaceCursorAtEnd("hi ")
        state.addImages(listOf(image()))
        settle()
        state.togglePreview()
        launch { state.renderPreview() }
        runCurrent()
        uploads.complete(0, CommentImageUploadResult.Ready(IMAGE_URL))
        settle()
        assertEquals(listOf("hi ", "hi [img]$IMAGE_URL[/img]"), rendered)

        val withImage = UIRichText(emptyList())
        renders[1].complete(withImage)
        settle()
        renders[0].complete(UIRichText(emptyList()))
        settle()
        assertSame(withImage, state.previewContent)
    }

    @Test
    fun `successful send clears the text and the uploads`() = runTest {
        val state = createState { CommentImageUploadResult.InvalidFormat }
        state.startEdit(CommentContext.Episode(subjectId = 1, episodeId = 2))
        state.textFieldState.setTextAndPlaceCursorAtEnd("hello")
        state.addImages(listOf(image()))
        settle()
        assertEquals(CommentImageUploadResult.InvalidFormat, state.imageUploadError)
        val tag = pendingImageTag(state.uploadIds.single())
        state.removeImageChip(1)

        assertTrue(state.send(EmptyCoroutineContext))
        assertEquals("", state.text)
        // 上传状态已清空, 这个占位标签不属于本次编辑
        state.textFieldState.setTextAndPlaceCursorAtEnd(tag)
        assertNull(state.imageUploadError)
        assertEquals(listOf(CommentImageChipStatus.Failed), state.statuses)
        assertFalse(state.imageThumbnails.single().canRetry)
    }

    @Test
    fun `upload finishing after switching to another target is dropped`() = runTest {
        val uploads = ControlledUploads()
        val state = createState(onUploadImage = uploads::upload)
        state.startEdit(CommentContext.Episode(subjectId = 1, episodeId = 2))
        state.addImages(listOf(image()))
        settle()

        state.startEdit(CommentContext.Episode(subjectId = 1, episodeId = 3))
        uploads.complete(0, CommentImageUploadResult.Ready(IMAGE_URL))
        settle()
        assertEquals("", state.text)

        // 新的评论对象中的图片正常上传
        state.addImages(listOf(image(PNG_2)))
        settle()
        assertEquals(2, uploads.requests.size)
        uploads.complete(1, CommentImageUploadResult.Ready(IMAGE_URL_2))
        settle()
        assertEquals("[img]$IMAGE_URL_2[/img]", state.text)
    }

    @Test
    fun `processing image keeps its placeholder and blocks send until it is ready`() = runTest {
        val checks = statusChecks()
        var sent: String? = null
        val state = createState(
            onSend = { _, content ->
                sent = content
                CommentSendResult.Ok
            },
            onCheckImageStatus = checks::check,
        ) { CommentImageUploadResult.Processing(IMAGE_ID, IMAGE_URL) }
        state.startEdit(CommentContext.Episode(subjectId = 1, episodeId = 2))
        state.textFieldState.setTextAndPlaceCursorAtEnd("hi ")
        state.addImages(listOf(image()))
        settle()
        val uploadId = state.uploadIds.single()
        assertEquals("hi ${pendingImageTag(uploadId)}", state.text)
        assertEquals(listOf(CommentImageChipStatus.Processing), state.statuses)
        assertTrue(state.uploadingImages)
        val thumbnail = state.imageThumbnails.single()
        assertEquals(CommentImageChipStatus.Processing, thumbnail.status)
        assertNull(thumbnail.url)
        assertFalse(thumbnail.canRetry)
        assertNull(state.imageUploadError)

        assertFalse(state.send(EmptyCoroutineContext))
        assertNull(sent)
        assertFalse(state.failedImagesBlockSend)

        advance(1.seconds)
        assertEquals(listOf(listOf(IMAGE_ID)), checks.requests)
        assertEquals(listOf(CommentImageChipStatus.Processing), state.statuses)

        checks.status = { CommentImageProcessingStatus.Ready(IMAGE_URL) }
        advance(1.5.seconds)
        assertEquals(2, checks.requests.size)
        assertEquals("hi [img]$IMAGE_URL[/img]", state.text)
        assertEquals(listOf(CommentImageChipStatus.Ready), state.statuses)
        assertEquals(IMAGE_URL, state.imageThumbnails.single().url)
        assertFalse(state.uploadingImages)
        assertTrue(state.send(EmptyCoroutineContext))
        assertEquals("hi [img]$IMAGE_URL[/img]", sent)

        // 没有正在处理的图片后不再查询
        advance(1.minutes)
        assertEquals(2, checks.requests.size)
    }

    @Test
    fun `status is checked one second after the upload and then with growing intervals up to five seconds`() =
        runTest {
            val checks = statusChecks()
            val state = createState(onCheckImageStatus = checks::check) {
                CommentImageUploadResult.Processing(IMAGE_ID, IMAGE_URL)
            }
            state.addImages(listOf(image()))
            settle()
            val start = testScheduler.currentTime

            advance(40.seconds)
            val delays = (listOf(start) + checks.times).zipWithNext { a, b -> (b - a).milliseconds }
            assertEquals(
                listOf(1.seconds, 1.5.seconds, 2.25.seconds, 3.375.seconds, 5.seconds, 5.seconds, 5.seconds),
                delays.take(7),
            )
            assertTrue(delays.all { it <= 5.seconds })
            assertEquals(listOf(CommentImageChipStatus.Processing), state.statuses)
        }

    @Test
    fun `an image starting to process checks again one second later`() = runTest {
        val checks = statusChecks()
        val ids = mutableListOf(IMAGE_ID, IMAGE_ID_2)
        val state = createState(onCheckImageStatus = checks::check) {
            CommentImageUploadResult.Processing(ids.removeFirst(), IMAGE_URL)
        }
        state.addImages(listOf(image()))
        // 查询间隔已增长到最长
        advance(13.125.seconds)
        assertEquals(5, checks.requests.size)

        advance(2.seconds)
        state.addImages(listOf(image(PNG_2)))
        settle()
        val added = testScheduler.currentTime
        advance(1.seconds)
        assertEquals(6, checks.requests.size)
        assertEquals(added + 1000, checks.times.last())
        assertEquals(listOf(IMAGE_ID, IMAGE_ID_2), checks.requests.last())
    }

    @Test
    fun `several processing images are checked in one call`() = runTest {
        val checks = statusChecks()
        val uploads = mutableListOf(
            CommentImageUploadResult.Processing(IMAGE_ID, IMAGE_URL),
            CommentImageUploadResult.Processing(IMAGE_ID_2, IMAGE_URL_2),
        )
        val state = createState(onCheckImageStatus = checks::check) { uploads.removeFirst() }
        state.addImages(listOf(image(PNG), image(PNG_2)))
        settle()
        val ids = state.uploadIds
        assertEquals(listOf(CommentImageChipStatus.Processing, CommentImageChipStatus.Processing), state.statuses)

        checks.status = { id ->
            if (id == IMAGE_ID) CommentImageProcessingStatus.Ready(IMAGE_URL) else CommentImageProcessingStatus.Processing
        }
        advance(1.seconds)
        assertEquals(listOf(listOf(IMAGE_ID, IMAGE_ID_2)), checks.requests)
        assertEquals("[img]$IMAGE_URL[/img]${pendingImageTag(ids[1])}", state.text)
        assertTrue(state.uploadingImages)

        advance(1.5.seconds)
        assertEquals(listOf(IMAGE_ID_2), checks.requests.last())
    }

    @Test
    fun `internal failure while processing is retried by uploading the image again`() = runTest {
        var reads = 0
        val checks = statusChecks()
        checks.status = { CommentImageProcessingStatus.Failed(CommentImageUploadResult.ProcessingFailed) }
        val uploaded = mutableListOf<ByteArray>()
        val state = createState(onCheckImageStatus = checks::check) {
            uploaded += it
            if (uploaded.size == 1) {
                CommentImageUploadResult.Processing(IMAGE_ID, IMAGE_URL)
            } else {
                CommentImageUploadResult.Ready(IMAGE_URL)
            }
        }
        state.addImages(listOf(image(read = { reads++; PNG })))
        advance(1.seconds)
        assertEquals(listOf(CommentImageChipStatus.Failed), state.statuses)
        assertEquals(CommentImageUploadResult.ProcessingFailed, state.imageUploadError)
        assertTrue(state.imageThumbnails.single().canRetry)
        assertFalse(state.uploadingImages)

        state.retryImageUpload(state.uploadIds.single())
        settle()
        // 处理期间不保存图片内容, 重试时重新读取
        assertEquals(2, reads)
        assertEquals(2, uploaded.size)
        assertContentEquals(PNG, uploaded[1])
        assertEquals("[img]$IMAGE_URL[/img]", state.text)
        assertNull(state.imageUploadError)
    }

    @Test
    fun `unsupported image found while processing cannot be retried`() = runTest {
        val checks = statusChecks()
        checks.status = { CommentImageProcessingStatus.Failed(CommentImageUploadResult.InvalidFormat) }
        var uploads = 0
        val state = createState(onCheckImageStatus = checks::check) {
            uploads++
            CommentImageUploadResult.Processing(IMAGE_ID, IMAGE_URL)
        }
        state.addImages(listOf(image()))
        advance(1.seconds)
        assertEquals(listOf(CommentImageChipStatus.Failed), state.statuses)
        assertEquals(CommentImageUploadResult.InvalidFormat, state.imageUploadError)
        assertFalse(state.imageThumbnails.single().canRetry)

        state.retryImageUpload(state.uploadIds.single())
        advance(10.seconds)
        assertEquals(1, uploads)
        assertEquals(1, checks.requests.size)
    }

    @Test
    fun `image still processing after five minutes fails and can be retried`() = runTest {
        val checks = statusChecks()
        val uploads = mutableListOf<CommentImageUploadResult>(
            CommentImageUploadResult.Processing(IMAGE_ID, IMAGE_URL),
            CommentImageUploadResult.Ready(IMAGE_URL),
        )
        val state = createState(onCheckImageStatus = checks::check) { uploads.removeFirst() }
        state.addImages(listOf(image()))
        settle()

        advance(4.minutes + 59.seconds)
        assertEquals(listOf(CommentImageChipStatus.Processing), state.statuses)
        advance(5.seconds)
        assertEquals(listOf(CommentImageChipStatus.Failed), state.statuses)
        assertEquals(CommentImageUploadResult.ProcessingFailed, state.imageUploadError)
        assertTrue(state.imageThumbnails.single().canRetry)
        val checked = checks.requests.size
        advance(1.minutes)
        assertEquals(checked, checks.requests.size)

        state.retryImageUpload(state.uploadIds.single())
        settle()
        assertEquals("[img]$IMAGE_URL[/img]", state.text)
    }

    @Test
    fun `unavailable status keeps the image processing and polling goes on`() = runTest {
        val checks = statusChecks()
        checks.answer = { CommentImageStatusResult.Unavailable }
        val state = createState(onCheckImageStatus = checks::check) {
            CommentImageUploadResult.Processing(IMAGE_ID, IMAGE_URL)
        }
        state.addImages(listOf(image()))
        advance(20.seconds)
        assertTrue(checks.requests.size >= 3)
        assertEquals(listOf(CommentImageChipStatus.Processing), state.statuses)
        assertNull(state.imageUploadError)

        // 查询函数抛出异常时同样继续查询
        checks.answer = { throw IllegalStateException("boom") }
        val checked = checks.requests.size
        advance(10.seconds)
        assertTrue(checks.requests.size > checked)
        assertEquals(listOf(CommentImageChipStatus.Processing), state.statuses)

        checks.answer = { ids -> CommentImageStatusResult.Ok(ids.associateWith { CommentImageProcessingStatus.Ready(IMAGE_URL) }) }
        advance(5.seconds)
        assertEquals("[img]$IMAGE_URL[/img]", state.text)
    }

    @Test
    fun `processing image whose placeholder was removed is not checked until undo restores it`() = runTest {
        val checks = statusChecks()
        val state = createState(onCheckImageStatus = checks::check) {
            CommentImageUploadResult.Processing(IMAGE_ID, IMAGE_URL)
        }
        state.addImages(listOf(image()))
        advance(1.seconds)
        assertEquals(1, checks.requests.size)

        state.removeImageChip(1)
        advance(1.minutes)
        assertEquals(1, checks.requests.size)
        assertFalse(state.uploadingImages)

        state.textFieldState.undoState.undo()
        settle()
        assertEquals(listOf(CommentImageChipStatus.Processing), state.statuses)
        checks.status = { CommentImageProcessingStatus.Ready(IMAGE_URL) }
        advance(1.5.seconds)
        assertEquals(2, checks.requests.size)
        assertEquals("[img]$IMAGE_URL[/img]", state.text)
    }

    @Test
    fun `only processing images whose placeholder is in the text are checked`() = runTest {
        val checks = statusChecks()
        val uploads = mutableListOf(
            CommentImageUploadResult.Processing(IMAGE_ID, IMAGE_URL),
            CommentImageUploadResult.Processing(IMAGE_ID_2, IMAGE_URL_2),
        )
        val state = createState(onCheckImageStatus = checks::check) { uploads.removeFirst() }
        state.addImages(listOf(image(PNG), image(PNG_2)))
        settle()
        state.removeImageChip(1)
        advance(1.seconds)
        assertEquals(listOf(listOf(IMAGE_ID_2)), checks.requests)

        state.textFieldState.undoState.undo()
        advance(1.5.seconds)
        assertEquals(listOf(IMAGE_ID, IMAGE_ID_2), checks.requests.last())
    }

    @Test
    fun `status check finishing after switching to another target is dropped`() = runTest {
        val response = CompletableDeferred<CommentImageStatusResult>()
        var checks = 0
        val state = createState(
            onCheckImageStatus = {
                checks++
                response.await()
            },
        ) { CommentImageUploadResult.Processing(IMAGE_ID, IMAGE_URL) }
        state.startEdit(CommentContext.Episode(subjectId = 1, episodeId = 2))
        state.addImages(listOf(image()))
        advance(1.seconds)
        assertEquals(1, checks)
        val tag = pendingImageTag(state.uploadIds.single())

        state.startEdit(CommentContext.Episode(subjectId = 1, episodeId = 3))
        response.complete(
            CommentImageStatusResult.Ok(mapOf(IMAGE_ID to CommentImageProcessingStatus.Ready(IMAGE_URL))),
        )
        settle()
        assertEquals("", state.text)
        // 结果没有用到上一个评论对象的图片上: 粘贴回它的占位标签, 标签不属于本次编辑
        state.textFieldState.setTextAndPlaceCursorAtEnd(tag)
        settle()
        assertEquals(listOf(CommentImageChipStatus.Failed), state.statuses)
        assertFalse(state.imageThumbnails.single().canRetry)
        advance(1.minutes)
        assertEquals(1, checks)
    }

    @Test
    fun `links without an address are previewed and sent as plain text`() = runTest {
        var rendered: String? = null
        var sent: String? = null
        val state = createState(
            richTextRenderer = {
                rendered = it
                UIRichText(emptyList())
            },
            onSend = { _, content ->
                sent = content
                CommentSendResult.Ok
            },
        )
        state.startEdit(CommentContext.Episode(subjectId = 1, episodeId = 2))
        state.textFieldState.setTextAndPlaceCursorAtEnd("a [url=]docs[/url] b")

        state.renderPreview()
        assertEquals("a docs b", rendered)
        assertTrue(state.send(EmptyCoroutineContext))
        assertEquals("a docs b", sent)
    }

    @Test
    fun `starting a new target clears the text`() = runTest {
        val state = createState()
        state.startEdit(CommentContext.Episode(subjectId = 1, episodeId = 2))
        state.textFieldState.setTextAndPlaceCursorAtEnd("hello")
        state.startEdit(CommentContext.Episode(subjectId = 1, episodeId = 2))
        assertEquals("hello", state.text)
        state.startEdit(CommentContext.Episode(subjectId = 1, episodeId = 3))
        assertEquals("", state.text)
    }

    @Test
    fun `toolbar edit is a single undo step and keeps the selection`() = runTest {
        val state = createState()
        state.textFieldState.setTextAndPlaceCursorAtEnd("a bold c")
        state.textFieldState.edit { selection = TextRange(2, 6) }

        state.toggleFormat(CommentFormat.Bold)
        assertEquals("a [b]bold[/b] c", state.text)
        assertEquals(TextRange(5, 9), state.textFieldState.selection)
        assertTrue(CommentFormat.Bold in state.activeFormats)

        state.textFieldState.undoState.undo()
        assertEquals("a bold c", state.text)
    }

    @Test
    fun `link state reflects the selection`() = runTest {
        val state = createState()
        state.textFieldState.setTextAndPlaceCursorAtEnd("https://a.com")
        state.textFieldState.edit { selection = TextRange(0, length) }
        assertFalse(state.linkActive)

        state.toggleLink()
        assertEquals("[url]https://a.com[/url]", state.text)
        assertTrue(state.linkActive)
    }

    @Test
    fun `image chips are numbered in order`() = runTest {
        val state = createState()
        state.textFieldState.setTextAndPlaceCursorAtEnd("a [img]$IMAGE_URL[/img] b [IMG=1,2]https://a.com/x[/IMG]")
        assertEquals(listOf(1, 2), state.imageChips.map { it.number })
        assertEquals(listOf(IMAGE_URL, "https://a.com/x"), state.imageChips.map { it.url })
    }

    @Test
    fun `new images get new upload ids`() = runTest {
        val state = createState()
        state.addImages(listOf(image()))
        val first = state.uploadIds.single()
        state.textFieldState.setTextAndPlaceCursorAtEnd("")
        state.addImages(listOf(image()))
        val second = state.uploadIds.single()
        assertTrue(first != second)

        // 另一个编辑器的标识也不同
        val other = createState()
        other.addImages(listOf(image()))
        assertTrue(other.uploadIds.single() != first)
    }

    @Test
    fun `selecting an image chip selects its tag`() = runTest {
        val state = createState()
        state.textFieldState.setTextAndPlaceCursorAtEnd("a [img]x[/img] [img]y[/img]")
        state.selectImageChip(2)
        assertEquals(TextRange(15, 27), state.textFieldState.selection)
    }

    @Test
    fun `starting a new target clears failed uploads`() = runTest {
        val state = createState { CommentImageUploadResult.NetworkError }
        state.startEdit(CommentContext.Episode(subjectId = 1, episodeId = 2))
        state.addImages(listOf(image()))
        settle()
        assertEquals(CommentImageUploadResult.NetworkError, state.imageUploadError)
        state.startEdit(CommentContext.Episode(subjectId = 1, episodeId = 3))
        assertEquals("", state.text)
        assertNull(state.imageUploadError)
    }

    @Test
    fun `copy puts the selected bbcode on the clipboard and keeps the selection`() = runTest {
        val state = createState()
        val clipboard = FakeClipboard()
        state.textFieldState.setTextAndPlaceCursorAtEnd("a [img]x[/img] b")
        state.textFieldState.edit { selection = TextRange(2, 16) }
        state.copySelection(clipboard)
        assertEquals("[img]x[/img] b", clipboard.getClipEntryText())
        assertEquals("a [img]x[/img] b", state.text)
        assertEquals(TextRange(2, 16), state.textFieldState.selection)
    }

    @Test
    fun `cut and paste move an image as one undo step each`() = runTest {
        val state = createState()
        val clipboard = FakeClipboard()
        state.textFieldState.setTextAndPlaceCursorAtEnd("a [img]x[/img] b")
        state.textFieldState.edit { selection = TextRange(2, 14) }
        state.cutSelection(clipboard)
        assertEquals("a  b", state.text)
        assertEquals("[img]x[/img]", clipboard.getClipEntryText())

        state.textFieldState.edit { selection = TextRange(length) }
        state.paste(clipboard)
        assertEquals("a  b[img]x[/img]", state.text)
        assertEquals(TextRange(state.text.length), state.textFieldState.selection)

        state.textFieldState.undoState.undo()
        assertEquals("a  b", state.text)
    }

    @Test
    fun `paste links urls and restores image labels`() = runTest {
        val state = createState()
        val clipboard = FakeClipboard()
        val label = encodeImageChipLabel("图片 1", state.imageChipRegistry.idOf("[img]q[/img]"))
        clipboard.setClipEntryText("see https://a.com $label")
        state.paste(clipboard)
        assertEquals("see [url]https://a.com[/url] [img]q[/img]", state.text)
    }

    @Test
    fun `copying displayed labels restores the bbcode`() = runTest {
        val state = createState()
        val label = encodeImageChipLabel("图片 1", state.imageChipRegistry.idOf("[img]q[/img]"))
        assertEquals("a [img]q[/img] b", state.decodeImageChipLabels("a $label b"))
    }

    @Test
    fun `copying the pending tag of an uploaded image puts its url on the clipboard`() = runTest {
        val uploads = ControlledUploads()
        val state = createState(onUploadImage = uploads::upload)
        state.addImages(listOf(image()))
        settle()
        val tag = pendingImageTag(state.uploadIds.single())
        val label = encodeImageChipLabel("图片 1", state.imageChipRegistry.idOf(tag))
        assertEquals(tag, state.decodeImageChipLabels(label))

        uploads.complete(0, CommentImageUploadResult.Ready(IMAGE_URL))
        settle()
        assertEquals("[img]$IMAGE_URL[/img]", state.decodeImageChipLabels(label))
        assertEquals("[img]$IMAGE_URL[/img]", state.decodeImageChipLabels(tag))
    }

    /**
     * 只在内存中保存条目的剪贴板.
     */
    private class FakeClipboard : Clipboard {
        private var entry: ClipEntry? = null
        override suspend fun getClipEntry(): ClipEntry? = entry
        override suspend fun setClipEntry(clipEntry: ClipEntry?) {
            entry = clipEntry
        }
    }

    private companion object {
        const val IMAGE_URL = "https://img.myani.org/comments/1.webp"
        const val IMAGE_URL_2 = "https://img.myani.org/comments/2.webp"
        const val IMAGE_ID = "1111111111111111111111111111111111111111111111111111111111111111"
        const val IMAGE_ID_2 = "2222222222222222222222222222222222222222222222222222222222222222"
        val PNG = byteArrayOf(137.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 13, 10, 26, 10)
        val PNG_2 = PNG + byteArrayOf(2)
        val JPEG = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte())
        val HEIC = "\u0000\u0000\u0000\u0018ftypheic\u0000\u0000\u0000\u0000".encodeToByteArray()
    }
}
