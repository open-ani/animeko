/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.torrent.anitorrent.session

import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import me.him188.ani.app.torrent.anitorrent.DisposableTaskQueue
import me.him188.ani.app.torrent.api.TorrentSession
import me.him188.ani.app.torrent.api.files.AbstractTorrentFileEntry
import me.him188.ani.app.torrent.api.files.FilePriority
import me.him188.ani.app.torrent.api.files.TorrentFileEntry
import me.him188.ani.app.torrent.api.files.TorrentFileHandle
import me.him188.ani.app.torrent.api.files.TorrentFilePieceMatcher
import me.him188.ani.app.torrent.api.pieces.MutablePieceList
import me.him188.ani.app.torrent.api.pieces.Piece
import me.him188.ani.app.torrent.api.pieces.PieceList
import me.him188.ani.app.torrent.api.pieces.PiecePriorities
import me.him188.ani.app.torrent.api.pieces.PieceState
import me.him188.ani.app.torrent.api.pieces.TorrentDownloadController
import me.him188.ani.app.torrent.api.pieces.count
import me.him188.ani.app.torrent.api.pieces.first
import me.him188.ani.app.torrent.api.pieces.isEmpty
import me.him188.ani.app.torrent.api.pieces.last
import me.him188.ani.app.torrent.api.pieces.maxBy
import me.him188.ani.app.torrent.api.pieces.minBy
import me.him188.ani.app.torrent.api.pieces.sumOf
import me.him188.ani.app.torrent.io.DEFAULT_BUFFER_PER_DIRECTION
import me.him188.ani.app.torrent.io.RawTorrentInputConstructorParameter
import me.him188.ani.app.torrent.io.TorrentInput
import me.him188.ani.app.torrent.io.TorrentInputParameters
import me.him188.ani.utils.coroutines.IO_
import me.him188.ani.utils.io.SystemPath
import me.him188.ani.utils.io.absolutePath
import me.him188.ani.utils.io.deleteRecursively
import me.him188.ani.utils.logging.debug
import me.him188.ani.utils.logging.error
import me.him188.ani.utils.logging.info
import me.him188.ani.utils.logging.thisLogger
import me.him188.ani.utils.logging.warn
import me.him188.ani.utils.platform.annotations.TestOnly
import me.him188.ani.utils.platform.currentTimeMillis
import org.openani.mediamp.io.SeekableInput
import kotlin.concurrent.Volatile
import kotlin.coroutines.CoroutineContext
import kotlin.jvm.JvmField
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

private val PROGRESS_RANGE = 0f..1f

class AnitorrentDownloadSession(
    private val handle: TorrentHandle,
    private val saveDirectory: SystemPath,
    private val fastResumeFile: SystemPath,
    private val onClose: (AnitorrentDownloadSession) -> Unit,
    private val onPostClose: (AnitorrentDownloadSession) -> Unit,
    private val onDelete: (AnitorrentDownloadSession) -> Unit,
    parentCoroutineContext: CoroutineContext,
    dispatcher: CoroutineContext = Dispatchers.IO_,
) : TorrentSession, SynchronizedObject() {
    internal val logger = thisLogger()
    val handleId get() = handle.id // 内存地址, 不可持久

    private val scope = CoroutineScope(
        parentCoroutineContext + dispatcher + SupervisorJob(parentCoroutineContext[Job]),
    )

    // 要放在 init 前面, 因为 init 会用到它
    override val sessionStats: MutableSharedFlow<TorrentSession.Stats?> =
        MutableSharedFlow<TorrentSession.Stats?>(
            replay = 1,
            extraBufferCapacity = 1,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        ).apply {
            tryEmit(null)
        }

    /**
     * complete 此属性为 true 后, 将会开始循环获取文件信息. 用于处理在收到事件时没有文件信息导致需要延后一段时间再获取的情况.
     */
    private val shouldTryLoadFiles = CompletableDeferred<Boolean>()

    init {
        scope.launch {
            while (isActive) {
                if (!handle.isValid) {
                    return@launch
                }
                handle.postStatusUpdates()
                delay(1000)
            }
        }

        scope.launch {
            var lastUploaded = 0L
            while (isActive) {
                if (!handle.isValid) {
                    return@launch
                }
                val uploaded = sessionStats.first()
                if (uploaded != null && uploaded.uploadedBytes != lastUploaded) {
                    lastUploaded = uploaded.uploadedBytes
                    try {
                        handle.postSaveResume()
                    } catch (e: Throwable) {
                        logger.error(e) { "Failed to save resume data" }
                    }
                }
                delay(60.seconds)
            }
        }

        scope.launch {
            if (shouldTryLoadFiles.await()) {
                while (true) {
                    if (!handle.isValid) {
                        return@launch
                    }
                    reloadFilesAndInitializeIfNotYet(force = false)
                    delay(1000)
                }
            }
        }
    }

    private val openFiles = mutableListOf<AnitorrentEntry.EntryHandle>()
    private val prioritizer = createPiecePriorities()

    inner class AnitorrentEntry(
        override val pieces: PieceList,
        index: Int,
        val offset: Long,
        length: Long, saveDirectory: SystemPath, relativePath: String,
        isDebug: Boolean, parentCoroutineContext: CoroutineContext,
        initialDownloadedBytes: Long,
    ) : AbstractTorrentFileEntry(
        index, length, saveDirectory, relativePath, handleId.toString(), isDebug,
        parentCoroutineContext,
    ) {
        override val supportsStreaming: Boolean get() = true

        /**
         * 是 [Piece.pieceIndex], 不是 file offset
         */
        val pieceIndexRange = with(pieces) {
            if (pieces.isEmpty()) IntRange.EMPTY else pieces.first().pieceIndex..pieces.last().pieceIndex
        }

        val controller: TorrentDownloadController = with(pieces) {
            val pieceSize = if (pieces.isEmpty()) 1024 else pieces.first().size
            TorrentDownloadController(
                pieces,
                prioritizer,
                // libtorrent 可能会平均地请求整个 window, 所以不能太大
                windowSize = (8 * 1024 * 1024 / pieceSize).toInt().coerceIn(2, 64),
                headerSize = 2 * 1024 * 1024,
                footerSize = (0.5 * 1024 * 1024).toLong(),
                possibleFooterSize = 8 * 1024 * 1024,
            )
        }

        inner class EntryHandle : AbstractTorrentFileHandle() {
            override val entry get() = this@AnitorrentEntry

            override suspend fun closeImpl() {
                openFiles.remove(this)
                closeIfNotInUse()
            }

            override fun resumeImpl(priority: FilePriority) {
                controller.resume()
                handle.resume()
            }

            override suspend fun closeAndDelete() {
                close()
                withContext(Dispatchers.IO_) { deleteEntireTorrentIfNotInUse() }
            }
        }

        override fun updatePriority() {
            logger.info { "[$handleId] Set file priority to $requestingPriority: $relativePath" }
            handle.setFilePriority(index, requestingPriority)
        }

        val downloadedBytes: MutableStateFlow<Long> = MutableStateFlow(initialDownloadedBytes)
        override val fileStats: Flow<TorrentFileEntry.Stats> = downloadedBytes.map { downloaded ->
            TorrentFileEntry.Stats(
                downloadedBytes = downloaded.coerceAtMost(length),
                downloadProgress = if (length == 0L) {
                    0f
                } else {
                    (downloaded.toFloat() / length).coerceIn(PROGRESS_RANGE)
                },
            )
        }

        override fun createHandle(): TorrentFileHandle {
            return EntryHandle().also {
                openFiles.add(it)
            }
        }

        override suspend fun createInput(awaitCoroutineContext: CoroutineContext): SeekableInput {
            val input = resolveDownloadingFile()
            return withContext(Dispatchers.IO_) {
                TorrentInput(
                    input,
                    pieces,
                    logicalStartOffset = offset,
                    onWait = { piece ->
                        updatePieceDeadlinesForSeek(piece)
                    },
                    size = length,
                    awaitCoroutineContext = awaitCoroutineContext,
                )
            }
        }

        /**
         * 在 Remote TorrentFileEntry 中创建 TorrentInput 实例, 需要获取 TorrentInput 构建参数
         */
        @OptIn(RawTorrentInputConstructorParameter::class)
        suspend fun createTorrentInputParameters(): TorrentInputParameters {
            return TorrentInputParameters(
                resolveDownloadingFile(),
                logicalStartOffset = offset,
                bufferSize = DEFAULT_BUFFER_PER_DIRECTION,
                size = length,
            )
        }

        fun updatePieceDeadlinesForSeek(requested: Piece) {
            with(pieces) {
                if (!controller.isDownloading(requested.pieceIndex)) {
                    logger.info { "[TorrentDownloadControl] $torrentId: Resetting deadlines to download ${requested.pieceIndex}" }
                    handle.clearPieceDeadlines()
                    controller.seekTo(requested.pieceIndex) // will request further pieces
                } else {
                    logger.info { "[TorrentDownloadControl] $torrentId: Requested piece ${requested.pieceIndex} is already downloading" }
                    return
                }
            }
        }
    }

    /**
     * 延迟获取的具体文件信息. 因为如果是添加磁力链的话, 文件信息需要经过耗时的网络查询后才能得到.
     * @see useTorrentInfoOrLaunch
     */
    inner class TorrentInfo(
        val name: String,
        val allPiecesInTorrent: MutablePieceList,
        val entries: List<AnitorrentEntry>,
    ) {
        override fun toString(): String {
            return "TorrentInfo(name=$name, numPieces=${allPiecesInTorrent.count}, entries.size=${entries.size})"
        }
    }

    private val actualTorrentInfo = CompletableDeferred<TorrentInfo>()

    @TestOnly
    val isActualTorrentInfoCompleted get() = actualTorrentInfo.isCompleted

    /**
     * 在某些时候 [close] 需要等待此 session 完全关闭，通过 [onTorrentRemoved] 来监听此事件
     */
    private val closingDeferred: CompletableDeferred<Unit> by lazy { CompletableDeferred() }

    /**
     * 当 [actualTorrentInfo] 还未完成时的任务队列, 用于延迟执行需要 [TorrentInfo] 的任务.
     *
     * 这是因为 [onTorrentFinished] 和 [onPieceDownloading] 可能会早于 [onTorrentChecked] 调用.
     * 而且 [onPieceDownloading] 会非常频繁调用, 不能为它启动过多协程
     */
    private val taskQueue: DisposableTaskQueue<AnitorrentDownloadSession> = DisposableTaskQueue(this).apply {
        scope.launch {
            actualTorrentInfo.await()
            runAndDispose()
        }
    }

    // 回调可能会早于 [actualTorrentInfo] 计算完成之前调用, 所以需要考虑延迟的情况
    private inline fun useTorrentInfoOrLaunch(
        // receiver 是为了让 lambda 无需捕获 this 对象. 因为 [onPieceDownloading] 可能会调用数万次
        // will be inlined multiple times!
        crossinline block: AnitorrentDownloadSession.(TorrentInfo) -> Unit
    ) {
        if (actualTorrentInfo.isCompleted) {
            block(actualTorrentInfo.getCompleted())
        } else {
            val added = taskQueue.add {
                block(actualTorrentInfo.getCompleted())
            }
            if (!added) {
                // taskQueue disposed, then actualTorrentInfo must have completed now
                check(actualTorrentInfo.isCompleted) {
                    "taskQueue disposed however actualTorrentInfo is not completed yet"
                }
                block(actualTorrentInfo.getCompleted())
            }
        }
    }

    private fun initializeTorrentInfo(info: TorrentDescriptor) {
        check(this.actualTorrentInfo.isActive) {
            "actualTorrentInfo has already been completed or closed"
        }
        logger.info { "initializeTorrentInfo" }
        val allPiecesInTorrent =
            PieceList.create(info.numPieces) {
                if (it == info.numPieces - 1) {
                    info.lastPieceSize
                } else info.pieceLength
            }

        val entries: List<AnitorrentEntry> = kotlin.run {
            val numFiles = info.fileSequence.toList()

            var currentOffset = 0L
            val list = numFiles.mapIndexed { index, file ->
                val size = file.size
                val path = file.path.takeIf { it.isNotBlank() } ?: file.name
                val list = TorrentFilePieceMatcher.matchPiecesForFile(
                    allPiecesInTorrent,
                    currentOffset,
                    size,
                ).also { pieces ->
                    logPieces(pieces, path)
                }
                AnitorrentEntry(
                    pieces = list,
                    index = index,
                    offset = currentOffset,
                    length = size,
                    saveDirectory = saveDirectory,
                    relativePath = path,
                    isDebug = false,
                    parentCoroutineContext = Dispatchers.IO_,
                    initialDownloadedBytes = calculateTotalFinishedSize(list).coerceAtMost(size),
                ).also {
                    currentOffset += size
                }
            }
            list
        }
        val value = TorrentInfo(
            name = info.name,
            allPiecesInTorrent,
            entries,
        )
        logger.info { "[$handleId] Got torrent info: $value" }
//        handle.ignore_all_files() // no need because we set libtorrent::torrent_flags::default_dont_download in native
        this.actualTorrentInfo.complete(value)
    }

    fun onTorrentChecked() {
        logger.info { "[$handleId] onTorrentChecked" }
        reloadFilesAndInitializeIfNotYet(force = true)
    }

    // 这个不一定会触发. 有可能是如果种子内已经有信息的就不会触发
    fun onMetadataReceived() {
        logger.info { "[$handleId] onMetadataReceived" }
        reloadFilesAndInitializeIfNotYet(force = true)
    }

    private val reloadFilesLock = SynchronizedObject()

    private fun reloadFilesAndInitializeIfNotYet(
        force: Boolean,
    ): Boolean = synchronized(reloadFilesLock) {
        val handleIsValid = handle.isValid
        val state = handle.getState()
        val metadataReady = state?.isMetadataReady() == true
        if (actualTorrentInfo.isActive && handleIsValid && metadataReady) {
            logger.info { "[$handleId] reloadFiles" }
            val info = handle.reloadFile() // split to multiple lines for debugging
            if (info.fileCount == 0 && !force) {
                logger.debug { "[$handleId] reloadFiles fileCount is 0, actualTorrentInfo=$actualTorrentInfo handleIsValid=$handleIsValid, state=$state, metadataReady=$metadataReady" }
                return false
            }
            initializeTorrentInfo(info)
            shouldTryLoadFiles.complete(false) // cancel coroutine
            return true
        } else {
            logger.debug { "[$handleId] reloadFiles condition not met, actualTorrentInfo=$actualTorrentInfo handleIsValid=$handleIsValid, state=$state, metadataReady=$metadataReady" }
            return false
        }
    }

    fun onPieceDownloading(pieceIndex: Int) {
        useTorrentInfoOrLaunch { info ->
            with(info.allPiecesInTorrent) {
                getByPieceIndex(pieceIndex).compareAndSetState(
                    PieceState.READY,
                    PieceState.DOWNLOADING,
                )
            }
        }
    }

    fun onPieceFinished(pieceIndex: Int) {
        // 注意, 在恢复时, libtorrent 不一定会为所有 piece 发送这个事件
        useTorrentInfoOrLaunch { info ->
            onPieceFinishedImpl(info, pieceIndex) // avoid being inlined multiple times
        }
    }

    private fun onPieceFinishedImpl(
        info: TorrentInfo,
        pieceIndex: Int
    ) {
        with(info.allPiecesInTorrent) {
            info.allPiecesInTorrent.getByPieceIndex(pieceIndex).state = PieceState.FINISHED
        }
        for (file in openFiles) {
            if (pieceIndex in file.entry.pieceIndexRange) {
                file.entry.controller.onPieceDownloaded(pieceIndex)
            }
        }
        // TODO: Anitorrent 计算 file 完成度的算法需要优化性能, 这有 n^2 复杂度
        for (entry in info.entries) {
            if (pieceIndex !in entry.pieceIndexRange) continue

            val downloadedBytes = entry.downloadedBytes.value
            if (downloadedBytes == entry.length) {
                // entry already finished
                continue
            }

            entry.downloadedBytes.compareAndSet(
                downloadedBytes,
                calculateTotalFinishedSize(entry.pieces).coerceAtMost(entry.length),
            ) // lazy set, if already finished, don't update
        }
    }

    // 这可能会被调用多次
    fun onTorrentFinished() {
        // 注意, 这个事件不一定是所有文件下载完成了. 
        // 在刚刚创建任务的时候所有文件都是完全不下载的状态, libtorrent 会立即广播这个事件.
        logger.info { "[$handleId] onTorrentFinished" }
        handle.postSaveResume()

        if (!reloadFilesAndInitializeIfNotYet(force = false)) {
            shouldTryLoadFiles.complete(true)
        }
    }

    fun onStatsUpdate(stats: TorrentStats) {
        this.sessionStats.tryEmit(
            TorrentSession.Stats(
                totalSizeRequested = stats.total,
                downloadedBytes = stats.totalDone,
                downloadSpeed = stats.downloadPayloadRate,
                uploadedBytes = stats.allTimeUpload,
                uploadSpeed = stats.uploadPayloadRate,
                downloadProgress = stats.progress,
            ),
        )
    }

    fun onFileCompleted(index: Int) {
        useTorrentInfoOrLaunch { info ->
            val entry = info.entries.getOrNull(index) ?: return@useTorrentInfoOrLaunch
            // 没有设置 pieces 状态, 因为假如首尾 pieces 不是精确匹配, 首尾 pieces 可能实际上没有完成
            entry.downloadedBytes.value = entry.length
        }
    }

    fun onSaveResumeData(data: TorrentResumeData) {
        logger.info { "[$handleId] saving resume data to: ${fastResumeFile.absolutePath}" }
        data.saveToPath(fastResumeFile.path)
    }

    override suspend fun getName(): String = this.actualTorrentInfo.await().name

    override suspend fun getFiles(): List<TorrentFileEntry> = this.actualTorrentInfo.await().entries

    override fun getPeers() = handle.getPeers()

    @Volatile
    private var closed = false

    override suspend fun close() {
        if (closed) { // 多次调用此 close 会等待同一个 deferred, 完成时一起返回
            closingDeferred.await()
            return
        }

        kotlin.run {
            synchronized(this) {
                if (closed) return@synchronized // 多次调用此 close 会等待同一个 deferred, 完成时一起返回
                closed = true
                return@run // set close = true, 跳出 run lambda 并真正执行 onClose() 并等待
            }
            closingDeferred.await() // 只有在 synchronized 里检查 closed == true 时会在此 await
            return
        }

        logger.info { "[$handleId] closing" }
        onClose(this)
        try {
            withTimeout(7500L) {
                closingDeferred.await() // 收到 on_torrent_removed 事件时返回
            }
        } catch (timeout: TimeoutCancellationException) {
            logger.warn { "[$handleId] timeout on closing this session, force to mark as closed." }
            closingDeferred.complete(Unit)
        }
        scope.cancel()
        onPostClose(this)
    }

    fun onTorrentRemoved() {
        logger.info { "[$handleId] onTorrentRemoved" }
        closingDeferred.complete(Unit)
    }

    override suspend fun closeIfNotInUse() {
        if (openFiles.isEmpty()) {
            close()
        }
    }

    fun deleteEntireTorrentIfNotInUse() {
        if (openFiles.isEmpty() && closed) {
            saveDirectory.deleteRecursively()
            onDelete(this)
        }
    }

    private fun createPiecePriorities(): PiecePriorities {
        return object : PiecePriorities {
            private val baseDeadline = -5000
            private val highestDeadline = -10000

            override fun downloadOnly(highPriorityPieces: List<Int>, normalPriorityPieces: List<Int>) {
                if (highPriorityPieces.isEmpty() && normalPriorityPieces.isEmpty()) {
                    return
                }

                if (enablePieceLogs || (currentTimeMillis() - startupTime).milliseconds > 5.seconds) {
                    enablePieceLogs = true
                    logger.debug { "[$handleId][TorrentDownloadControl] Prioritizing pieces: ${highPriorityPieces + normalPriorityPieces}" }
                }

                if (highPriorityPieces.isNotEmpty()) {
                    // 以 deadline = 5000 为分界点,  
                    // highPriorityPieces 的 deadline < 5000, normalPriorityPieces 的 deadline > 5000

                    // 让 high priority 的 piece 均匀分布在 highestDeadline 到 baseDeadline 之间, 
                    // 第一个 piece 的 deadline 一定是 highestDeadline
                    val highPriorityStep = (baseDeadline - highestDeadline) / highPriorityPieces.size
                    highPriorityPieces.asReversed().forEachIndexed { index, pieceIndex ->
                        handle.setPieceDeadline(pieceIndex, baseDeadline - (index + 1) * highPriorityStep)
                    }

                    // 让 normal priority 的 piece 根据等差数列分布到 baseDeadline 到 IntMax
                    normalPriorityPieces.forEachIndexed { index, pieceIndex ->
                        handle.setPieceDeadline(pieceIndex, baseDeadline + (index + 1) * 700)
                    }
                } else {
                    // 既然 highPriorityPieces 已经全部下载完了, 那我们的 normal priority 也可以成为 high priority
                    val highPriorityStep = (baseDeadline - highestDeadline) / normalPriorityPieces.size
                    normalPriorityPieces.asReversed().forEachIndexed { index, pieceIndex ->
                        handle.setPieceDeadline(pieceIndex, baseDeadline - (index + 1) * highPriorityStep)
                    }
                }
            }
        }
    }

    private companion object {
        @JvmField
        val startupTime = currentTimeMillis()

        @JvmField
        var enablePieceLogs = false // faster
    }
}


private fun AnitorrentDownloadSession.logPieces(pieces: PieceList, pathInTorrent: String): Unit = with(pieces) {
    if (pieces.isEmpty()) {
        logger.warn { "[$handleId] File '$pathInTorrent' piece initialized, empty pieces" }
        return@with
    }
    logger.info {
        val start = pieces.minBy { it.dataStartOffset }
        val end = pieces.maxBy { it.dataLastOffset }
        "[$handleId] File '$pathInTorrent' piece initialized, ${pieces.count} pieces, " +
                "index range: ${start.pieceIndex..end.pieceIndex}, " +
                "offset range: $start..$end"
    }
}

val TorrentDescriptor.fileSequence
    get() = sequence {
        val fileCount = fileCount
        repeat(fileCount) {
            val file = fileAtOrNull(it)
            checkNotNull(file) { "fileAtOrNull($it) returned null, however fileCount was $fileCount" }
            yield(file)
        }
    }

private fun calculateTotalFinishedSize(pieces: PieceList) = with(pieces) {
    pieces.sumOf { if (it.state == PieceState.FINISHED) it.size else 0 }
}

private fun TorrentHandleState.isMetadataReady(): Boolean {
    return when (this) {
        TorrentHandleState.QUEUED_FOR_CHECKING -> false
        TorrentHandleState.CHECKING_FILES -> false
        TorrentHandleState.DOWNLOADING_METADATA -> false
        TorrentHandleState.DOWNLOADING -> true
        TorrentHandleState.FINISHED -> true
        TorrentHandleState.SEEDING -> true
        TorrentHandleState.ALLOCATING -> false
        TorrentHandleState.CHECKING_RESUME_DATA -> false
    }
}
