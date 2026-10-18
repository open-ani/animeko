/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.comment

import androidx.annotation.UiThread
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.text.TextRange
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import me.him188.ani.app.domain.comment.CommentContext
import me.him188.ani.app.domain.comment.CommentImageFormat
import me.him188.ani.app.domain.comment.CommentImageProcessingStatus
import me.him188.ani.app.domain.comment.CommentImageStatusResult
import me.him188.ani.app.domain.comment.CommentImageUploadResult
import me.him188.ani.app.domain.comment.CommentSendResult
import me.him188.ani.app.domain.comment.GetCommentImageStatusUseCase
import me.him188.ani.app.domain.comment.PostCommentUseCase
import me.him188.ani.app.domain.comment.UploadCommentImageUseCase
import me.him188.ani.app.tools.MonoTasker
import me.him188.ani.app.ui.foundation.getClipEntryText
import me.him188.ani.app.ui.foundation.setClipEntryText
import me.him188.ani.app.ui.foundation.transcodeImageToJpeg
import org.jetbrains.compose.resources.DrawableResource
import kotlin.coroutines.CoroutineContext
import kotlin.random.Random
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/**
 * @param onUploadImage 上传评论图片, 返回结果. 调用前已检查过文件大小不超过 [UploadCommentImageUseCase.MAX_IMAGE_SIZE_BYTES],
 * 且图片已是 [CommentImageFormat] 中的格式.
 * @param onCheckImageStatus 查询上传后正在服务器上处理的图片 ([CommentImageUploadResult.Processing.id]) 的状态,
 * 见 [GetCommentImageStatusUseCase]. 在 [imageUploadContext] 中调用.
 * @param backgroundScope 发送评论和处理图片的后台任务所在的作用域. 图片在其中上传, 编辑框关闭后仍继续, 直到这个作用域结束.
 * @param transcodeImage 把不是 [CommentImageFormat] 中格式的图片 (例如 HEIC 照片) 转成服务器接受的格式, 无法转换时返回 `null`.
 * 在 [imageUploadContext] 中调用.
 * @param uiContext 在 [backgroundScope] 中处理图片的协程所用的上下文. 上传完成后要修改编辑框的文本, 只能在主线程上进行.
 * @param imageUploadContext 读取、转码和上传图片, 以及查询图片处理状态所用的上下文.
 * @param timeSource 计算图片处理了多久所用的时间源.
 */
@Stable
class CommentEditorState(
    showExpandEditCommentButton: Boolean,
    initialEditExpanded: Boolean,
    panelTitle: State<String?>,
    stickers: State<List<EditCommentSticker>>,
    private val richTextRenderer: suspend (String) -> UIRichText,
    private val onSend: suspend (target: CommentContext, content: String) -> CommentSendResult,
    private val onUploadImage: suspend (image: ByteArray) -> CommentImageUploadResult,
    private val onCheckImageStatus: suspend (ids: List<String>) -> CommentImageStatusResult,
    private val backgroundScope: CoroutineScope,
    private val transcodeImage: (image: ByteArray) -> ByteArray? = { image ->
        transcodeImageToJpeg(
            image,
            maxShortSide = UploadCommentImageUseCase.MAX_SHORT_SIDE,
            maxLongSide = UploadCommentImageUseCase.MAX_LONG_SIDE,
        )
    },
    private val uiContext: CoroutineContext = Dispatchers.Main,
    private val imageUploadContext: CoroutineContext = Dispatchers.Default,
    private val timeSource: TimeSource = TimeSource.Monotonic,
) {
    /**
     * 编辑框的文本与选区. 工具栏、插入表情和图片等程序化修改都在一次 [TextFieldState.edit] 里完成, 每次是一个撤销步骤.
     *
     * 文本是 BBCode, 其中的图片标签在编辑框中显示为 "图片 N" 标签, 见 [CommentImageChip].
     */
    val textFieldState = TextFieldState()

    /**
     * 编辑框中显示的图片标签与原文的对应, 见 [ImageChipRegistry].
     */
    internal val imageChipRegistry = ImageChipRegistry()

    /**
     * 评论中的图片, 按出现顺序编号.
     */
    internal val imageChips: List<CommentImageChip> by derivedStateOf { parseImageChips(textFieldState.text) }

    /**
     * 待上传图片的上传状态, 键为 [CommentImageChip.uploadId]. 选择图片时登记, 切换评论对象或评论发送成功时清空.
     *
     * 文本中的占位标签不在其中时 (例如粘贴了上一次编辑留下的占位标签), 视为上传失败且不能重试.
     */
    private val imageUploads: SnapshotStateMap<String, CommentImageUpload> = mutableStateMapOf()

    /**
     * 状态为 [CommentImageUpload.Queued] 的图片, 按选择的顺序上传. 见 [uploadQueuedImages].
     */
    private val imageUploadQueue: SnapshotStateList<String> = mutableStateListOf()

    /**
     * 有图片开始在服务器上处理 ([CommentImageUpload.Processing]) 时发送, 让 [pollProcessingImages] 尽快查询它.
     */
    private val imageProcessingStarted = Channel<Unit>(Channel.CONFLATED)

    /**
     * 处理图片的后台任务, 见 [runImageWorker]. 第一次添加图片时启动.
     */
    private var imageWorker: Job? = null

    /**
     * 上传状态的代数. 切换评论对象或评论发送成功时清空上传状态并加一, 此前开始的上传的结果随之作废.
     */
    private var imageUploadGeneration = 0

    private val _notices = MutableSharedFlow<CommentEditorNotice>(
        extraBufferCapacity = 16,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    /**
     * 一次性的提示, 界面以 toast 显示. 没有人收集时直接丢弃: 编辑器关闭期间上传失败的图片, 重新打开时由缩略图显示失败.
     */
    val notices: Flow<CommentEditorNotice> get() = _notices

    private fun notify(notice: CommentEditorNotice) {
        _notices.tryEmit(notice)
    }

    /**
     * 已可用、还没有改到文本中的图片, 等待输入法结束组合. 见 [completeImageUpload].
     */
    private val pendingImageEdits = mutableListOf<PendingImageEdit>()

    /**
     * 每个 [CommentEditorState] 的随机前缀, 让其他编辑中留下的占位标签 (例如复制到别处后又粘贴回来) 不会与本次的待上传图片混淆.
     */
    private val uploadIdPrefix = Random.nextInt(0, Int.MAX_VALUE).toString(36) + "-"
    private var nextUploadId = 1

    /**
     * 编辑框的 [CommentImageChipInputTransformation].
     */
    internal val imageChipInputTransformation = CommentImageChipInputTransformation(::decodeImageChipLabels)

    private val sendTasker = MonoTasker(backgroundScope)

    val panelTitle by panelTitle

    var currentSendTarget: CommentContext? by mutableStateOf(null)
        private set
    val sending get() = sendTasker.isRunning

    /**
     * 评论中是否有正在上传、等待上传或正在服务器上处理的图片. 这期间不能发送评论, 图片都可用或被删除后才能发送.
     */
    val uploadingImages: Boolean by derivedStateOf {
        imageChips.any {
            val status = imageChipStatus(it)
            status == CommentImageChipStatus.Uploading || status == CommentImageChipStatus.Processing
        }
    }

    /**
     * 评论中还能添加的图片数. 评论最多包含 [PostCommentUseCase.MAX_IMAGES] 张图片, 包括正在上传、上传失败的图片和其他网站的图片.
     */
    internal val remainingImageSlots: Int by derivedStateOf {
        (PostCommentUseCase.MAX_IMAGES - imageChips.size).coerceAtLeast(0)
    }

    /**
     * 编辑框下方的缩略图, 与 [imageChips] 一一对应.
     */
    internal val imageThumbnails: List<CommentImageThumbnail> by derivedStateOf {
        imageChips.map { chip ->
            val upload = chip.uploadId?.let { imageUploads[it] }
            CommentImageThumbnail(
                number = chip.number,
                status = imageChipStatus(chip),
                url = if (chip.uploadId == null) chip.url else (upload as? CommentImageUpload.Uploaded)?.url,
                uploadId = chip.uploadId,
                error = (upload as? CommentImageUpload.Failed)?.error,
                canRetry = (upload as? CommentImageUpload.Failed)?.image != null,
            )
        }
    }

    var previewing by mutableStateOf(false)
        private set
    var previewContent: UIRichText? by mutableStateOf(null)
        private set

    /**
     * 已开始的预览渲染次数, 见 [renderPreview].
     */
    private var previewGeneration = 0

    var editExpanded: Boolean by mutableStateOf(initialEditExpanded)
    val expandButtonState by derivedStateOf { if (!showExpandEditCommentButton) null else editExpanded }

    var showStickerPanel: Boolean by mutableStateOf(false)
        private set
    val stickers by stickers

    /**
     * 当前选区已应用的格式, 用于高亮选区工具栏的按钮.
     */
    val activeFormats: Set<CommentFormat> by derivedStateOf {
        val current = currentText()
        CommentFormat.entries.filterTo(mutableSetOf()) { current.isFormatActive(it) }
    }

    /**
     * 当前选区是否已经是链接.
     */
    val linkActive: Boolean by derivedStateOf { currentText().isLinkActive() }

    /**
     * 连续开关为同一个评论的编辑框将保存编辑内容和编辑框状态
     */
    fun startEdit(newTarget: CommentContext) {
        if (newTarget != currentSendTarget) {
            textFieldState.clearText()
            textFieldState.undoState.clearHistory()
            clearImageUploads()
        }
        currentSendTarget = newTarget
        previewing = false
        previewContent = null
        editExpanded = false
    }

    fun toggleStickerPanelState(desired: Boolean? = null) {
        showStickerPanel = desired ?: !showStickerPanel
    }

    /**
     * 切换选区的 [format].
     *
     * @see CommentText.toggleFormat
     */
    fun toggleFormat(format: CommentFormat) {
        edit { it.toggleFormat(format) }
    }

    /**
     * 把选区变成链接, 或去掉选区上的链接.
     *
     * @see CommentText.toggleLink
     */
    fun toggleLink() {
        edit { it.toggleLink() }
    }

    /**
     * 在光标处插入文本. 有选区时插入到选区末尾.
     */
    fun insertTextAtCursor(value: String) {
        edit { it.insertAtCursor(value) }
    }

    /**
     * 图片标签的状态. 读取快照状态, 可以在 [derivedStateOf] 或组合中使用.
     */
    internal fun imageChipStatus(chip: CommentImageChip): CommentImageChipStatus {
        val uploadId = chip.uploadId ?: return CommentImageChipStatus.Ready
        return when (imageUploads[uploadId]) {
            is CommentImageUpload.Queued -> CommentImageChipStatus.Uploading
            is CommentImageUpload.Processing -> CommentImageChipStatus.Processing
            is CommentImageUpload.Uploaded -> CommentImageChipStatus.Ready
            is CommentImageUpload.Failed, null -> CommentImageChipStatus.Failed
        }
    }

    /**
     * 选中第 [number] 张图片的整个标签. 调用方负责让编辑框获得焦点, 选区工具栏随之出现, 可以剪切或复制这张图片.
     */
    internal fun selectImageChip(number: Int) {
        edit { it.selectImageChip(number) }
    }

    /**
     * 从评论中删除第 [number] 张图片.
     */
    internal fun removeImageChip(number: Int) {
        edit { it.removeImageChip(number) }
    }

    /**
     * 添加用户选择的图片: 在光标处依次插入每张图片的占位标签 (见 [pendingImageTag]), 有选区时插入到选区末尾, 光标移到插入的标签之后.
     * 图片在 [backgroundScope] 中按顺序上传 (见 [runImageWorker]), 服务器处理完成后占位标签替换为图片地址.
     *
     * - 超过 [remainingImageSlots] 的图片不添加, 并提示已达上限 ([CommentEditorNotice.ImageLimitReached]).
     * - 超过 [UploadCommentImageUseCase.MAX_IMAGE_SIZE_BYTES] 的图片不读取, 直接标记为上传失败 ([CommentImageUploadResult.TooLarge]);
     *   不论几张, 只提示一次.
     */
    internal fun addImages(images: List<CommentImageFile>) {
        ensureImageWorker()
        val accepted = images.take(remainingImageSlots)
        if (accepted.size < images.size) notify(CommentEditorNotice.ImageLimitReached)
        if (accepted.isEmpty()) return

        var tooLarge = false
        val uploadIds = accepted.map { image ->
            val uploadId = uploadIdPrefix + nextUploadId++
            val size = image.size
            if (size != null && size > UploadCommentImageUseCase.MAX_IMAGE_SIZE_BYTES) {
                imageUploads[uploadId] = CommentImageUpload.Failed(CommentImageUploadResult.TooLarge, image = null, prepared = null)
                tooLarge = true
            } else {
                imageUploads[uploadId] = CommentImageUpload.Queued(image)
                imageUploadQueue.add(uploadId)
            }
            uploadId
        }
        edit { current -> current.insertAtCursor(uploadIds.joinToString("") { pendingImageTag(it) }) }
        if (tooLarge) notify(CommentEditorNotice.ImageUploadFailed(CommentImageUploadResult.TooLarge))
    }

    /**
     * 提示评论中的图片已达上限 ([CommentEditorNotice.ImageLimitReached]). 用于在达到上限时点击上传按钮; 还能添加图片时不提示.
     */
    fun notifyImageLimitReached() {
        if (remainingImageSlots == 0) notify(CommentEditorNotice.ImageLimitReached)
    }

    /**
     * 重新上传上传失败的图片 [uploadId]. 图片不能重试时 (见 [CommentImageThumbnail.canRetry]) 不做任何事.
     */
    internal fun retryImageUpload(uploadId: String) {
        val failed = imageUploads[uploadId] as? CommentImageUpload.Failed ?: return
        val image = failed.image ?: return
        ensureImageWorker()
        imageUploads[uploadId] = CommentImageUpload.Queued(image, failed.prepared)
        imageUploadQueue.add(uploadId)
    }

    /**
     * 启动处理图片的后台任务 [runImageWorker], 已经在运行时不做任何事.
     */
    private fun ensureImageWorker() {
        if (imageWorker?.isActive == true) return
        imageWorker = backgroundScope.launch(uiContext) { runImageWorker() }
    }

    /**
     * 处理图片的后台任务. 在 [backgroundScope] 中以 [uiContext] 运行, 不依赖编辑框是否显示:
     * - 依次上传排队的图片, 见 [uploadQueuedImages];
     * - 查询正在服务器上处理的图片的状态, 见 [pollProcessingImages];
     * - 释放占位标签已不在文本中的图片在内存中保存的内容, 见 [CommentImageUpload.Failed.prepared];
     * - 输入法结束组合时把已可用的图片改到文本中, 见 [completeImageUpload].
     */
    private suspend fun runImageWorker(): Nothing = coroutineScope {
        launch {
            snapshotFlow { imageChips.mapNotNullTo(HashSet()) { it.uploadId } }.collect { releaseRemovedImages(it) }
        }
        launch {
            snapshotFlow { textFieldState.composition == null }
                .collect { notComposing -> if (notComposing) applyPendingImageEdits() }
        }
        launch { pollProcessingImages() }
        uploadQueuedImages()
    }

    /**
     * 按选择的顺序上传占位标签在文本中的图片, 一次一张. 占位标签被删除或剪切的图片留在队列中, 标签因撤销或粘贴重新出现时再上传.
     *
     * 每张图片的读取、转码和上传在 [imageUploadContext] 中进行. 不是服务器接受的格式时先用 `transcodeImage` 转码,
     * 无法转码时报告 [CommentImageUploadResult.InvalidFormat]. 服务器已处理好时见 [completeImageUpload];
     * 还在处理时变为 [CommentImageUpload.Processing], 由 [pollProcessingImages] 查询.
     * 上传失败时保留占位标签, 可以重试的失败在内存中保存已读取和转码的图片, 用于 [retryImageUpload].
     */
    private suspend fun uploadQueuedImages(): Nothing {
        while (true) {
            val uploadId = snapshotFlow { nextQueuedImageUpload() }.filterNotNull().first()
            uploadQueuedImage(uploadId)
        }
    }

    /**
     * 队列中第一张占位标签在文本中的图片.
     */
    private fun nextQueuedImageUpload(): String? {
        if (imageUploadQueue.isEmpty()) return null
        val present = imageChips.mapNotNullTo(HashSet()) { it.uploadId }
        return imageUploadQueue.firstOrNull { it in present }
    }

    private suspend fun uploadQueuedImage(uploadId: String) {
        val queued = imageUploads[uploadId] as? CommentImageUpload.Queued
        if (queued == null) {
            imageUploadQueue.remove(uploadId)
            return
        }
        val generation = imageUploadGeneration
        val (result, prepared) = withContext(imageUploadContext) { readAndUploadImage(queued) }
        // 上传期间切换了评论对象或发送了评论, 这张图片已不在本次编辑中
        if (generation != imageUploadGeneration) return
        imageUploadQueue.remove(uploadId)
        when (result) {
            is CommentImageUploadResult.Ready -> completeImageUpload(uploadId, result.url)
            is CommentImageUploadResult.Processing -> {
                imageUploads[uploadId] = CommentImageUpload.Processing(
                    queued.image,
                    id = result.id,
                    url = result.url,
                    since = timeSource.markNow(),
                )
                imageProcessingStarted.trySend(Unit)
            }

            is CommentImageUploadResult.Error -> {
                val retryable = result.isRetryable
                val present = imageChips.any { it.uploadId == uploadId }
                imageUploads[uploadId] = CommentImageUpload.Failed(
                    result,
                    image = queued.image.takeIf { retryable },
                    prepared = prepared.takeIf { retryable && present },
                )
                // 占位标签已被删除的图片不提示: 用户已经不要它了
                if (present) notify(CommentEditorNotice.ImageUploadFailed(result))
            }
        }
    }

    /**
     * 上传 [queued] 中的图片. 有已读取和转码的内容时直接上传, 否则读取图片并在需要时转码.
     *
     * @return 上传结果, 以及上传的图片内容 (已读取和转码). 没有得到可以上传的内容时为 `null`.
     */
    private suspend fun readAndUploadImage(
        queued: CommentImageUpload.Queued,
    ): Pair<CommentImageUploadResult, ByteArray?> {
        var prepared = queued.prepared
        val result = try {
            if (prepared == null) {
                val bytes = queued.image.readBytes()
                prepared = when {
                    // 文件大小未知时在读取后检查
                    bytes.size > UploadCommentImageUseCase.MAX_IMAGE_SIZE_BYTES ->
                        return CommentImageUploadResult.TooLarge to null

                    CommentImageFormat.isSupported(bytes) -> bytes
                    else -> transcodeImage(bytes) ?: return CommentImageUploadResult.InvalidFormat to null
                }
            }
            onUploadImage(prepared)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            CommentImageUploadResult.UnknownError(e.toString())
        }
        return result to prepared
    }

    /**
     * 查询占位标签在文本中、正在服务器上处理 ([CommentImageUpload.Processing]) 的图片的状态, 直到它们可用或失败.
     * 标签被删除或剪切的图片不查询, 标签因撤销或粘贴重新出现后继续.
     *
     * 每次用一次 `onCheckImageStatus` 查询所有这些图片. 查询间隔从 [ImageStatusPollMinInterval] 开始, 每次变为
     * [ImageStatusPollBackoff] 倍, 最长 [ImageStatusPollMaxInterval]. 有图片开始处理时间隔恢复为最短,
     * 从那时起等待 [ImageStatusPollMinInterval] 后查询. 查询失败 ([CommentImageStatusResult.Unavailable]) 时图片保持不变,
     * 下次再查. 结果见 [applyImageStatus].
     */
    private suspend fun pollProcessingImages(): Nothing {
        var interval = ImageStatusPollMinInterval
        while (true) {
            snapshotFlow { processingImagesInText().isNotEmpty() }.first { it }
            if (withTimeoutOrNull(interval) { imageProcessingStarted.receive() } != null) {
                interval = ImageStatusPollMinInterval
                continue
            }
            val processing = processingImagesInText()
            if (processing.isEmpty()) continue
            val generation = imageUploadGeneration
            val ids = processing.values.mapTo(LinkedHashSet()) { it.id }.toList()
            val result = withContext(imageUploadContext) { checkImageStatus(ids) }
            // 查询期间切换了评论对象或发送了评论, 这些图片已不在本次编辑中
            if (generation != imageUploadGeneration) continue
            applyImageStatus(processing, result)
            interval = (interval * ImageStatusPollBackoff).coerceAtMost(ImageStatusPollMaxInterval)
        }
    }

    /**
     * 占位标签在文本中、正在服务器上处理的图片, 键为 [CommentImageChip.uploadId].
     */
    private fun processingImagesInText(): Map<String, CommentImageUpload.Processing> {
        val result = LinkedHashMap<String, CommentImageUpload.Processing>()
        for (chip in imageChips) {
            val uploadId = chip.uploadId ?: continue
            val upload = imageUploads[uploadId] as? CommentImageUpload.Processing ?: continue
            result[uploadId] = upload
        }
        return result
    }

    private suspend fun checkImageStatus(ids: List<String>): CommentImageStatusResult = try {
        onCheckImageStatus(ids)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        CommentImageStatusResult.Unavailable
    }

    /**
     * 把一次查询的结果 [result] 应用到查询的图片 [polled] 上. 查询期间状态已经变化的图片不修改.
     *
     * - 处理完成的图片见 [completeImageUpload].
     * - 处理失败的图片保留占位标签; 可以重试时 (见 [CommentImageUploadResult.Error.isRetryable]) 重试会重新读取并上传图片.
     * - 开始处理超过 [ImageProcessingTimeout] 仍未完成的图片视为处理失败 ([CommentImageUploadResult.ProcessingFailed]).
     *   重试时重新上传同一张图片, 服务器据此返回已处理好的图片, 继续处理, 或重新开始处理.
     */
    private suspend fun applyImageStatus(
        polled: Map<String, CommentImageUpload.Processing>,
        result: CommentImageStatusResult,
    ) {
        val statuses = (result as? CommentImageStatusResult.Ok)?.statuses.orEmpty()
        for ((uploadId, processing) in polled) {
            if (imageUploads[uploadId] !== processing) continue
            when (val status = statuses[processing.id]) {
                is CommentImageProcessingStatus.Ready -> completeImageUpload(uploadId, status.url)
                is CommentImageProcessingStatus.Failed -> failProcessing(uploadId, processing, status.error)
                CommentImageProcessingStatus.Processing, null -> {
                    if (processing.since.elapsedNow() >= ImageProcessingTimeout) {
                        failProcessing(uploadId, processing, CommentImageUploadResult.ProcessingFailed)
                    }
                }
            }
        }
    }

    /**
     * 正在处理的图片 [uploadId] 处理失败. 查询期间占位标签被删除的图片不提示.
     */
    private fun failProcessing(
        uploadId: String,
        processing: CommentImageUpload.Processing,
        error: CommentImageUploadResult.Error,
    ) {
        imageUploads[uploadId] = CommentImageUpload.Failed(
            error,
            image = processing.image.takeIf { error.isRetryable },
            prepared = null,
        )
        if (imageChips.any { it.uploadId == uploadId }) notify(CommentEditorNotice.ImageUploadFailed(error))
    }

    /**
     * 占位标签已不在文本中 ([present] 之外) 的图片不在内存中保存图片内容. 标签因撤销或粘贴重新出现后, 重试时重新读取图片.
     */
    private fun releaseRemovedImages(present: Set<String>) {
        for ((uploadId, upload) in imageUploads.toList()) {
            if (uploadId in present) continue
            when (upload) {
                is CommentImageUpload.Queued -> if (upload.prepared != null) {
                    imageUploads[uploadId] = CommentImageUpload.Queued(upload.image)
                }

                is CommentImageUpload.Failed -> if (upload.prepared != null) {
                    imageUploads[uploadId] = CommentImageUpload.Failed(upload.error, upload.image, null)
                }

                is CommentImageUpload.Processing, is CommentImageUpload.Uploaded -> {}
            }
        }
    }

    /**
     * 图片 [uploadId] 已上传并处理完成, 地址为 [url].
     *
     * 状态立即变为 [CommentImageUpload.Uploaded]: 图片标签显示为可用, 发送、预览和复制时占位标签替换为图片 (见 [resolvedText]).
     * 把文本中的占位标签替换为图片地址 (评论中已有这张图片时删除占位标签并提示 [CommentEditorNotice.ImageAlreadyInComment])
     * 则等到输入法没有在组合文字时进行: 程序化修改文本会结束输入法的组合, 用户正在输入的拼音或假名会被当作文字提交.
     */
    private suspend fun completeImageUpload(uploadId: String, url: String) {
        // 在标记为上传成功之前检查, 以免把这张图片自己的占位标签当作已有的图片
        val duplicate = CommentText(resolvedText(), TextRange.Zero).containsImage(url)
        imageUploads[uploadId] = CommentImageUpload.Uploaded(url)
        // 重新上传评论中已失效的图片得到相同的地址, 评论中原有的图片随之恢复可用, 不重复插入
        if (duplicate) notify(CommentEditorNotice.ImageAlreadyInComment)
        pendingImageEdits.add(PendingImageEdit(uploadId, url.takeUnless { duplicate }))
        if (textFieldState.composition == null) applyPendingImageEdits()
        // 预览只在进入时渲染一次, 预览期间上传完成的图片要重新渲染才能看到
        if (previewing) renderPreview(clear = false)
    }

    /**
     * 把 [pendingImageEdits] 改到文本中, 作为一个撤销步骤. 占位标签已不在文本中的图片不修改,
     * 撤销或粘贴让标签重新出现时, 它代表上传后的图片.
     */
    private fun applyPendingImageEdits() {
        if (pendingImageEdits.isEmpty()) return
        edit { it.withPendingImageEdits() }
        pendingImageEdits.clear()
    }

    private fun CommentText.withPendingImageEdits(): CommentText = pendingImageEdits.fold(this) { current, pending ->
        val url = pending.url
        val edited = if (url == null) {
            current.removePendingImage(pending.uploadId)
        } else {
            current.replacePendingImage(pending.uploadId, url)
        }
        edited ?: current
    }

    /**
     * 发送和预览所用的文本: 已可用的图片替换为图片地址, 包括还没有改到文本中的 (见 [completeImageUpload]).
     */
    private fun resolvedText(): String =
        resolvePendingImages(currentText().withPendingImageEdits().text, ::uploadedImageUrl)

    /**
     * 待上传图片 [uploadId] 可用时的地址, 还没有上传或处理完成时为 `null`.
     */
    private fun uploadedImageUrl(uploadId: String): String? =
        (imageUploads[uploadId] as? CommentImageUpload.Uploaded)?.url

    private fun clearImageUploads() {
        imageUploadGeneration++
        imageUploads.clear()
        imageUploadQueue.clear()
        pendingImageEdits.clear()
    }

    /**
     * 把编辑框显示的图片标签文字还原为图片标签原文 (见 [decodeImageChipLabels]), 其中已可用的占位标签替换为图片.
     * 用于写入剪贴板的文字, 以及粘贴或输入法提交的文字.
     */
    internal fun decodeImageChipLabels(text: String): String =
        resolvePendingImages(decodeImageChipLabels(text, imageChipRegistry::sourceOf), ::uploadedImageUrl)

    /**
     * 把选中的文字 (BBCode) 复制到 [clipboard]. 选区保持不变, 与桌面端的复制相同.
     *
     * Android 的评论面板中没有系统的文字操作菜单, 选区工具栏用它和 [cutSelection]、[paste] 提供剪贴板操作.
     * 这些是程序化修改, 直接操作文本, 不经过编辑框的 `InputTransformation`.
     *
     * 不取消选区是因为文本框只在用户的手势和文字变化时更新它自己的工具栏状态: 在光标处的工具栏中全选后, 它仍认为工具栏属于光标,
     * 此时取消选区会让它重新请求在光标处显示工具栏 (见 [CommentTextToolbarRequests]), 粘贴和全选的工具栏就会一直停在那里.
     */
    internal suspend fun copySelection(clipboard: Clipboard) {
        val selected = currentText().selectedText()
        if (selected.isEmpty()) return
        clipboard.setClipEntryText(selected)
    }

    /**
     * 把选中的文字 (BBCode) 剪切到 [clipboard].
     *
     * @see copySelection
     */
    internal suspend fun cutSelection(clipboard: Clipboard) {
        val selected = currentText().selectedText()
        if (selected.isEmpty()) return
        edit { it.deleteSelection() }
        clipboard.setClipEntryText(selected)
    }

    /**
     * 用 [clipboard] 中的文字替换选区, 效果与在编辑框中粘贴相同 (还原图片标签、识别网址), 见 [CommentText.paste].
     *
     * @see copySelection
     */
    internal suspend fun paste(clipboard: Clipboard) {
        val pasted = clipboard.getClipEntryText()
        if (pasted.isNullOrEmpty()) return
        edit { it.paste(pasted, ::decodeImageChipLabels) }
    }

    /**
     * 选中全部文字.
     */
    internal fun selectAll() {
        textFieldState.edit { selection = TextRange(0, length) }
    }

    fun togglePreview() {
        previewing = !previewing
    }

    /**
     * 渲染预览. 还不可用的图片不显示.
     *
     * 同时有多次渲染时 (例如进入预览后图片上传完成), 只采用最后开始的那次的结果.
     *
     * @param clear 渲染期间是否清除之前的预览, 显示正在渲染. 为 `false` 时之前的预览保留到新的结果出来.
     */
    @UiThread
    suspend fun renderPreview(clear: Boolean = true) {
        val generation = ++previewGeneration
        if (clear) previewContent = null
        val content = removePendingImages(resolvedText())
        val rendered = richTextRenderer(removeEmptyLinks(content))
        if (generation == previewGeneration) previewContent = rendered
    }

    /**
     * 发送评论. 评论中有正在上传、正在处理或上传失败的图片时不发送, 有上传失败的图片时提示 ([CommentEditorNotice.SendBlockedByFailedImages]);
     * 发送失败时提示原因 ([CommentEditorNotice.SendFailed]).
     *
     * @return `true` if comment was sent successfully
     */
    suspend fun send(
        context: CoroutineContext = Dispatchers.Default
    ): Boolean {
        val target = currentSendTarget
        val resolved = resolvedText()
        // 占位标签只存在于编辑框中, 不能发送给服务器
        val blocking = parseImageChips(resolved).filter { it.uploadId != null }
        if (blocking.isNotEmpty()) {
            if (blocking.any { imageChipStatus(it) == CommentImageChipStatus.Failed }) {
                notify(CommentEditorNotice.SendBlockedByFailedImages)
            }
            return false
        }
        val content = removeEmptyLinks(resolved)

        editExpanded = false

        val result = sendTasker.async(context) {
            checkNotNull(target)
            onSend(target, content)
        }.await()

        return when (result) {
            CommentSendResult.Ok -> {
                textFieldState.clearText()
                clearImageUploads()
                true
            }

            is CommentSendResult.Error -> {
                notify(CommentEditorNotice.SendFailed(result))
                false
            }
        }
    }

    fun cancelSend() {
        sendTasker.cancel()
    }

    private fun currentText(): CommentText =
        CommentText(textFieldState.text.toString(), textFieldState.selection)

    /**
     * 在一次 [TextFieldState.edit] 中应用 [transform], 只替换变化的部分, 记录的修改只包含变化的文字.
     *
     * 文字有变化时, 输入法正在进行的组合会结束, 组合中的文字被直接提交. 不是由用户发起的修改要避开组合, 见 [completeImageUpload].
     */
    private inline fun edit(transform: (CommentText) -> CommentText) {
        textFieldState.edit {
            val old = asCharSequence().toString()
            val new = transform(CommentText(old, selection))
            if (new.text != old) {
                var prefix = 0
                val maxPrefix = minOf(old.length, new.text.length)
                while (prefix < maxPrefix && old[prefix] == new.text[prefix]) prefix++
                var suffix = 0
                val maxSuffix = maxPrefix - prefix
                while (suffix < maxSuffix && old[old.length - 1 - suffix] == new.text[new.text.length - 1 - suffix]) {
                    suffix++
                }
                replace(prefix, old.length - suffix, new.text.substring(prefix, new.text.length - suffix))
            }
            selection = new.selection
        }
    }
}

/**
 * 用户选择的一张图片.
 *
 * @property size 文件的字节数, 无法获取时为 `null`. 超过 [UploadCommentImageUseCase.MAX_IMAGE_SIZE_BYTES] 的图片不读取.
 * @property readBytes 读取文件的内容. 轮到这张图片上传时才在上传所用的线程上调用.
 */
internal class CommentImageFile(
    val size: Long?,
    val readBytes: suspend () -> ByteArray,
)

/**
 * 一张待上传图片 (见 [pendingImageTag]) 的上传状态.
 */
internal sealed interface CommentImageUpload {
    /**
     * 等待上传或正在上传.
     *
     * @property image 用户选择的图片.
     * @property prepared 上一次上传失败时保存的已读取和转码的内容, 有它时直接上传, 跳过读取 [image].
     */
    class Queued(
        val image: CommentImageFile,
        val prepared: ByteArray? = null,
    ) : CommentImageUpload

    /**
     * 已上传, 服务器正在处理. 处理完成后才能在评论中引用, 见 [CommentEditorState.pollProcessingImages].
     * 不在内存中保存图片内容, 处理失败后重试时重新读取 [image].
     *
     * @property image 用户选择的图片.
     * @property id 服务器上图片的标识, 用于查询处理状态.
     * @property url 处理完成后图片的地址.
     * @property since 开始处理的时间.
     */
    class Processing(
        val image: CommentImageFile,
        val id: String,
        val url: String,
        val since: TimeMark,
    ) : CommentImageUpload

    /**
     * 已上传并处理完成. 占位标签随即被替换为图片地址, 撤销或粘贴让占位标签重新出现时, 它代表这个地址的图片.
     */
    class Uploaded(val url: String) : CommentImageUpload

    /**
     * 上传或处理失败.
     *
     * @property image 重试时上传的图片. 不能重试 (见 [CommentImageUploadResult.Error.isRetryable]) 时为 `null`.
     * @property prepared 已读取和转码的图片内容, 重试时直接上传. 只在占位标签还在文本中时保存在内存中,
     * 标签被删除或剪切后释放, 之后重试时重新读取 [image].
     */
    class Failed(
        val error: CommentImageUploadResult.Error,
        val image: CommentImageFile?,
        val prepared: ByteArray?,
    ) : CommentImageUpload
}

/**
 * 图片可用后要对文本做的修改: 把待上传图片 [uploadId] 的占位标签替换为 [url] 的图片, [url] 为 `null` 时删除占位标签.
 */
private class PendingImageEdit(
    val uploadId: String,
    val url: String?,
)

@Immutable
data class EditCommentSticker(
    val id: Int,
    val drawableRes: DrawableResource?,
)

/**
 * 查询图片处理状态的最短间隔, 也是图片开始处理后第一次查询前等待的时间.
 */
private val ImageStatusPollMinInterval = 1.seconds

/**
 * 查询图片处理状态的最长间隔.
 */
private val ImageStatusPollMaxInterval = 5.seconds

/**
 * 每次查询后间隔变为原来的倍数.
 */
private const val ImageStatusPollBackoff = 1.5

/**
 * 图片开始处理后最多等待的时间. 超过后视为处理失败, 可以重试.
 */
private val ImageProcessingTimeout = 5.minutes
