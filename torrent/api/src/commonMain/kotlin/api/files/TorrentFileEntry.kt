/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.torrent.api.files

import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.withContext
import kotlinx.io.IOException
import me.him188.ani.app.torrent.api.TorrentSession
import me.him188.ani.app.torrent.api.pieces.Piece
import me.him188.ani.app.torrent.api.pieces.PieceList
import me.him188.ani.utils.io.SystemPath
import me.him188.ani.utils.io.absolutePath
import me.him188.ani.utils.io.isRegularFile
import me.him188.ani.utils.io.length
import me.him188.ani.utils.io.resolve
import me.him188.ani.utils.logging.info
import me.him188.ani.utils.logging.thisLogger
import org.openani.mediamp.io.SeekableInput
import kotlin.concurrent.Volatile
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.time.Duration.Companion.seconds

/**
 * 表示 [BT 资源][TorrentSession] 中的一个文件.
 *
 * 所有文件默认都没有开始下载, 需调用 [createHandle] 创建一个句柄, 并使用 [TorrentFileHandle.resume] 才会开始下载.
 * 当句柄被关闭后, 该文件的下载也会被停止.
 */
interface TorrentFileEntry { // 实现提示, 无 test mock
    /**
     * @see fileStats
     */
    data class Stats(
        /**
         * 已经下载成功的字节数.
         *
         * @return `0L`..[TorrentFileEntry.length]
         */
        val downloadedBytes: Long,
        /**
         * 已完成比例.
         *
         * @return `0f`..`1f`, 在未开始下载时, 该值为 `0f`.
         */
        val downloadProgress: Float, // 0f..1f
        // 没有上传信息
    ) {
        init {
            require(downloadProgress in 0f..1f) { "Progress must be in range 0f..1f, but was $downloadProgress" }
        }

        val isDownloadFinished: Boolean get() = downloadProgress >= 1f
    }

    /**
     * 该文件的下载数据.
     *
     * 有关返回的 flow 的性质, 参考 [TorrentSession.sessionStats].
     */
    val fileStats: Flow<Stats> // shared

    /**
     * 文件数据长度. 注意, 这不是文件在硬盘上的大小. 在硬盘上可能会略有差别.
     */
    val length: Long // get must be fast

    /**
     * 在种子资源的文件. 例如 `01.mp4`, 不包含目录.
     */
    val fileName: String

    /**
     * 在种子资源中的相对目录. 例如 `01.mp4`, `TV/01.mp4`. 相比于 [fileName], 包含了其目录.
     */
    val pathInTorrent: String

    /**
     * 获取与这个文件有关的所有 [Piece].
     *
     * 注意, 文件与 piece 的大小并不一定完全匹配. 文件的第一个字节可能不会是返回的第一个 piece 的第一个字节, 尾部同理.
     * 但不会返回一个完全不包含该文件数据的 piece.
     *
     * @throws IllegalStateException 当未匹配到正确大小的 pieces 时抛出
     * @return 一定是 [RandomAccess] List
     */
    val pieces: PieceList

    /**
     * 是否支持边下边播
     */
    val supportsStreaming: Boolean

    /**
     * 创建一个句柄, 以用于下载文件.
     */
    fun createHandle(): TorrentFileHandle

    /**
     * 绝对路径. 挂起直到文件路径可用 (即有任意一个 piece 下载完成时)
     */
    suspend fun resolveFile(): SystemPath

    @Throws(IOException::class)
    fun resolveFileMaybeEmptyOrNull(): SystemPath?

    /**
     * Opens the downloaded file as a [SeekableInput].
     *
     * @param awaitCoroutineContext 当 [SeekableInput] 需要等待 piece 完成时, 会[切换][withContext]到的 [CoroutineContext].
     */
    suspend fun createInput(awaitCoroutineContext: CoroutineContext = EmptyCoroutineContext): SeekableInput
}

/**
 * 挂起协程, 直到 [TorrentFileEntry] 下载完成, 即 [TorrentFileEntry.Stats.isDownloadFinished] 为 `true`.
 *
 * 注意, 如果 [TorrentFileEntry] 未开始下载, 或所属 [TorrentSession] 已经被关闭, 则此函数会一直挂起.
 *
 * 支持 cancellation.
 */
suspend inline fun TorrentFileEntry.awaitFinished() {
    fileStats.takeWhile { it.isDownloadFinished }.collect()
}

/**
 * 判断此时 [TorrentFileEntry] 是否已经下载完成.
 *
 * 注意, 本函数会挂起, 直到能够判断该状态. 挂起通常只会在 [TorrentFileEntry] 刚刚创建时发生.
 */
suspend inline fun TorrentFileEntry.isFinished(): Boolean = fileStats.first().isDownloadFinished


abstract class AbstractTorrentFileEntry(
    val index: Int,
    final override val length: Long,
    private val saveDirectory: SystemPath,
    val relativePath: String,
    val torrentId: String, // TODO: make this Int 
    val isDebug: Boolean,
    parentCoroutineContext: CoroutineContext,
) : TorrentFileEntry {
    protected val scope = CoroutineScope(parentCoroutineContext + SupervisorJob(parentCoroutineContext[Job]))
    protected val logger = thisLogger()

    abstract inner class AbstractTorrentFileHandle : TorrentFileHandle, SynchronizedObject() {
        @Volatile
        private var closed = false
        private var closeException: Throwable? = null
        private val closingDeferred by lazy { CompletableDeferred<Unit>() }

        final override suspend fun close() {
            if (closed) return

            synchronized(this) {
                if (closed) return
                closed = true

                logger.info { "[$torrentId] Close handle $fileName, remove priority request" }
                removePriority()

                if (isDebug) {
                    closeException = Exception("Stacktrace for close()")
                }
            }

            closeImpl()
        }

        protected abstract suspend fun closeImpl()

        final override fun pause() {
            checkClosed()
            requestPriority(null)
        }

        protected fun checkClosed() {
            if (closed) throw IllegalStateException(
                "Attempting to pause but TorrentFile has already been closed: $fileName",
                closeException,
            )
        }

        override val entry get() = this@AbstractTorrentFileEntry

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is AbstractTorrentFileHandle) return false
            return entry === other.entry
        }

        override fun hashCode(): Int {
            return entry.hashCode()
        }

        final override fun resume(priority: FilePriority) {
            checkClosed()
            requestPriority(priority)
            resumeImpl(priority)
        }

        protected abstract fun resumeImpl(priority: FilePriority)

        override fun toString(): String = "TorrentFileHandle(index=$index, filePath='$fileName')"
    }

    /**
     * 与这个文件有关的 pieces, sorted naturally by offset
     *
     * must support [RandomAccess]
     */
    abstract override val pieces: PieceList

    final override val fileName: String
        get() = relativePath
            .substringAfterLast("\\")
            .substringAfterLast("/")

    final override val pathInTorrent: String = relativePath

    protected val priorityRequests: MutableMap<TorrentFileHandle, FilePriority?> = mutableMapOf()

    /**
     * `null` to ignore
     */
    private fun TorrentFileHandle.requestPriority(priority: FilePriority?) {
        priorityRequests[this] = priority
        updatePriority()
    }

    private fun TorrentFileHandle.removePriority() {
        priorityRequests.remove(this)
        updatePriority()
    }

    val requestingPriority
        get() = priorityRequests.values.maxWithOrNull(nullsFirst(naturalOrder()))
            ?: FilePriority.IGNORE

    protected abstract fun updatePriority()

    override suspend fun resolveFile(): SystemPath = resolveDownloadingFile()

    protected suspend fun resolveDownloadingFile(): SystemPath {
        while (true) {
            val file = withContext(Dispatchers.IO) { resolveFileMaybeEmptyOrNull() }
            if (file != null) {
                if (withContext(Dispatchers.IO) { file.length() == 0L }) {
                    logger.info { "[$torrentId][resolveDownloadingFile]: Got file, but it's length is zero. Waiting..." }
                    delay(1.seconds)
                    continue
                }
                logger.info { "[$torrentId][resolveDownloadingFile]: Get file: ${file.absolutePath}" }
                return file
            }
            logger.info { "[$torrentId][resolveDownloadingFile]: Still waiting to get file... saveDirectory: $saveDirectory" }
            delay(1.seconds)
        }
    }

    @Throws(IOException::class)
    override fun resolveFileMaybeEmptyOrNull(): SystemPath? =
        saveDirectory.resolve(relativePath).takeIf { it.isRegularFile() }

    override fun toString(): String {
        return "TorrentFileEntryImpl(index=$index, length=$length, relativePath='$relativePath')"
    }
}

