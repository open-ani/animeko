/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.torrent.pikpak

import io.github.nihildigit.pikpak.PikPakFileCache
import io.github.nihildigit.pikpak.PikPakStreamReader
import io.github.nihildigit.pikpak.StreamRole
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.io.IOException
import me.him188.ani.app.torrent.api.files.AbstractTorrentFileEntry
import me.him188.ani.app.torrent.api.files.FilePriority
import me.him188.ani.app.torrent.api.files.TorrentFileEntry
import me.him188.ani.app.torrent.api.files.TorrentFileHandle
import me.him188.ani.app.torrent.api.files.TorrentRemoteFile
import me.him188.ani.app.torrent.api.pieces.MutablePieceList
import me.him188.ani.app.torrent.api.pieces.PieceList
import me.him188.ani.app.torrent.api.pieces.PieceState
import me.him188.ani.app.torrent.io.TorrentInput
import me.him188.ani.utils.coroutines.IO_
import me.him188.ani.utils.io.SystemPath
import me.him188.ani.utils.io.resolve
import me.him188.ani.utils.logging.info
import me.him188.ani.utils.logging.warn
import org.openani.mediamp.io.SeekableInput
import kotlin.concurrent.Volatile
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration.Companion.seconds

// One file of a PikPak torrent. Its bytes live in the SDK's file cache, which reads through the
// cloud and keeps what it fetched in a PikPakSparseStore on disk; playback, the seek-bar preview,
// the index prefetch and an explicit cache request all share that one cache, so nothing is fetched
// twice. The piece list mirrors the store's blocks for the cache UI and the resume logic.
internal class PikPakFileEntry(
    index: Int,
    length: Long,
    private val saveDirectory: SystemPath,
    relativePath: String,
    torrentId: String,
    parentCoroutineContext: CoroutineContext,
    val meta: PikPakFileMeta,
    /** Builds the cloud side over the store the entry currently owns; see [CloudSource.release]. */
    cloudFactory: (storeProvider: () -> PikPakSparseStore) -> CloudSource,
    private val onHandleCountChanged: suspend () -> Unit,
    /**
     * Whether a copy of this file was ever asked for: a cache download, or an import. What was only
     * played is removed when the session closes, as it was before playback went through the store.
     */
    kept: Boolean = false,
    /** Records whether the file is kept, so a restart knows; see [kept]. */
    private val persistKept: suspend (kept: Boolean) -> Unit = {},
) : AbstractTorrentFileEntry(
    index = index,
    length = length,
    saveDirectory = saveDirectory,
    relativePath = relativePath,
    torrentId = torrentId,
    isDebug = false,
    parentCoroutineContext = parentCoroutineContext,
), CloudReadiness, TorrentRemoteFile {
    override val supportsStreaming: Boolean get() = true

    private val dataPath: SystemPath get() = saveDirectory.resolve(relativePath)

    private val storeContext: CoroutineContext = parentCoroutineContext + Dispatchers.IO_

    // Guards the stream generation: deleteFiles holds it across the swap, and every cache is opened
    // under it, so no cache is ever built over a store that is being deleted.
    private val resolveMutex = Mutex()

    @Volatile
    private var generation = 0

    // Whether this session put bytes into the current data file. Closing removes only those: a file
    // this session never touched may be another episode's finished download, whatever the kept
    // flag on disk says.
    @Volatile
    private var wroteThisSession = false

    @Volatile
    private var stream: Stream = newStream()

    private val cloud: CloudSource = cloudFactory { stream.store }

    @Volatile
    private var kept = kept

    // Writes run in order and each writes the flag as it is then, so a late write cannot restore a
    // value a delete has since cleared.
    private val keptWrites = Mutex()

    private suspend fun saveKept() {
        try {
            keptWrites.withLock { persistKept(kept) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            logger.warn(e) { "[$torrentId] $fileName could not record whether it is kept" }
        }
    }

    @Volatile
    private var cloudReady = false

    override val pieces: MutablePieceList get() = stream.pieces

    override suspend fun ensureCloudReady() {
        if (cloudReady) return
        resolveMutex.withLock {
            if (cloudReady) return
            // Complete files, imports among them, have what they need on disk; imports have no GCID
            // and must never need the cloud.
            if (stream.store.isComplete) {
                cloudReady = true
                return
            }
            cloud.prepare()
            cloudReady = true
        }
    }

    override val fileStats: Flow<TorrentFileEntry.Stats> = flow {
        while (true) {
            val downloaded = stream.store.heldBytes.value
            emit(
                TorrentFileEntry.Stats(
                    downloadedBytes = downloaded,
                    downloadProgress = if (length <= 0L) 1f
                    else (downloaded.toFloat() / length).coerceIn(0f, 1f),
                ),
            )
            delay(1.seconds)
        }
    }

    // Everything the CDN delivered for this file: playback, preview, index and cache download alike.
    val deliveredBytes: Long get() = cloud.deliveredBytes

    val downloadedBytes: Long get() = stream.store.heldBytes.value

    private val _error = MutableStateFlow<Throwable?>(null)
    override val error: StateFlow<Throwable?> = _error.asStateFlow()

    val isComplete: Boolean get() = stream.store.isComplete

    private val openHandles = MutableStateFlow(0)
    val hasOpenHandles: Boolean get() = openHandles.value > 0

    inner class EntryHandle : AbstractTorrentFileHandle() {
        override val entry get() = this@PikPakFileEntry

        override fun resumeImpl(priority: FilePriority) {
            prepareInBackground()
        }

        override fun setPrefetchRangeImpl(byteRange: LongRange?) {
            prefetchRange.value = byteRange
        }

        override suspend fun closeImpl() {
            openHandles.update { (it - 1).coerceAtLeast(0) }
            onHandleCountChanged()
        }

        override suspend fun closeAndDelete() {
            close()
            deleteFiles()
        }
    }

    override fun createHandle(): TorrentFileHandle {
        openHandles.update { it + 1 }
        return EntryHandle()
    }

    // HIGH belongs to playback; lower active priorities request a persistent copy.
    private val wantsWholeFile: Boolean
        get() = priorityRequests.values.any { it != null && it != FilePriority.IGNORE && it < FilePriority.HIGH }

    // A flag rather than a Job handed between start and stop: a stop landing while the start is
    // still being scheduled would otherwise cancel nothing.
    private val downloadWanted = MutableStateFlow(false)

    // The range to have on disk ahead of a jump, such as skipping the opening; one per entry, a new
    // one replacing the last. See TorrentFileHandle.setPrefetchRange.
    private val prefetchRange = MutableStateFlow<LongRange?>(null)

    init {
        scope.launch { downloadWanted.collectLatest { wanted -> if (wanted) downloadLoop() } }
        scope.launch { prefetchRange.collectLatest { range -> if (range != null) prefetch(range) } }
    }

    override fun updatePriority() {
        val wanted = wantsWholeFile
        if (wanted && !kept) {
            kept = true
            scope.launch { saveKept() }
        }
        downloadWanted.value = wanted
        logger.info { "[$torrentId] $fileName priority -> $requestingPriority" }
    }

    private fun prepareInBackground() {
        if (cloudReady) return
        scope.launch {
            try {
                ensureCloudReady()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                logger.warn(e) { "[$torrentId] $fileName could not settle its stream" }
            }
        }
    }

    /**
     * Downloads what the store is missing until it holds the whole file, container head and index
     * first. The SDK retries each block before failing the download; this is the one outer layer,
     * a pause and another call, which fetches only what is still missing.
     */
    private suspend fun downloadLoop() {
        while (!stream.store.isComplete) {
            var cache: PikPakFileCache? = null
            try {
                ensureCloudReady()
                cache = openCache()
                cache.download(downloadOrder()).await()
            } catch (e: CancellationException) {
                // Our own cancellation ends the loop. Otherwise the cache was closed under the
                // download, by an account switch or a delete, and the next pass opens the new one.
                currentCoroutineContext().ensureActive()
            } catch (e: Throwable) {
                // Closed under the download too, surfacing as the cache's own "closed" failure
                if (cache?.isClosed == true) continue
                _error.value = e
                logger.warn(e) { "[$torrentId] $fileName download stopped, retrying in $RETRY_DELAY" }
                delay(RETRY_DELAY)
            }
        }
        _error.value = null
    }

    /**
     * Fetches [range] into the store at WARM priority: behind everything playing, ahead of all
     * background work. Best effort: a failure, or a cache replaced under it, ends it.
     *
     * A download rather than a prefetch. The range is usually asked for a minute or two before the
     * jump, and a prefetch keeps it in the memory cache, least recently used and capped, where
     * playback's read-ahead may push it out by then; on disk it waits. A download holds only two
     * requests in flight while playback runs, but it fetches the range in order, so the seconds
     * right after the jump arrive first.
     */
    private suspend fun prefetch(range: LongRange) {
        val store = stream.store
        val last = range.last.coerceAtMost(length - 1)
        if (range.first > last) return
        if (((range.first / PIECE_SIZE).toInt()..(last / PIECE_SIZE).toInt()).all { store.isHeld(it) }) return
        var job: Deferred<Unit>? = null
        try {
            ensureCloudReady()
            job = openCache().download(listOf(range), StreamRole.FOREGROUND, PikPakStreamReader.WARM_PRIORITY)
            job.await()
        } catch (e: CancellationException) {
            // Our own cancellation, a new range or a close, propagates; a closed cache just ends it
            currentCoroutineContext().ensureActive()
        } catch (e: Throwable) {
            logger.warn(e) { "[$torrentId] $fileName could not prefetch $range" }
        } finally {
            // Not a child of this coroutine; cancelling it withdraws what it had not fetched
            job?.cancel()
        }
    }

    private suspend fun openCache(): PikPakFileCache = resolveMutex.withLock { cloud.cache() }

    private fun downloadOrder(): List<LongRange> {
        val head = minOf(length, HEADER_SIZE)
        val tailStart = maxOf(head, length - FOOTER_SIZE)
        return listOf(0L until head, tailStart until length, head until tailStart).filterNot { it.isEmpty() }
    }

    override suspend fun createInput(awaitCoroutineContext: CoroutineContext): SeekableInput =
        withContext(Dispatchers.IO_) {
            ensureCloudReady()
            val current = stream
            // 播放已经持有 input 时, 后续 input 用于进度条预览。createInput 不携带调用方信息，因此以现有
            // input 数量区分两者。预览只读关键帧附近的一两个块, 预读窗口压到一个块, 以免和播放抢连接。
            val secondary = current.claimInput() > 0
            val inputName = if (secondary) "$fileName (preview)" else fileName
            logger.info { "[$torrentId] $inputName creating an input" }
            try {
                if (current.store.isComplete) {
                    completeFileInput(current)
                } else {
                    cloudInput(current, inputName, secondary)
                }
            } catch (e: Throwable) {
                current.releaseInput()
                throw e
            }
        }

    // Nothing is missing, so there is nothing to wait for and no cloud to ask: a plain read of the
    // data file, as for any finished torrent. It stops serving once the file is deleted, like the
    // cloud input: an open handle would otherwise keep reading the unlinked file.
    private fun completeFileInput(current: Stream): SeekableInput {
        val input = TorrentInput(file = dataPath, pieces = current.pieces, size = length)
        return object : SeekableInput by input {
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                if (current.isStale()) throw IOException("[$fileName] the file this input was opened on was deleted")
                return input.read(buffer, offset, length)
            }

            override fun close() {
                try {
                    input.close()
                } finally {
                    current.releaseInput()
                }
            }
        }
    }

    private suspend fun cloudInput(current: Stream, inputName: String, secondary: Boolean): SeekableInput {
        // Opened now rather than on the first read, so the index prefetch starts with the input
        if (!secondary) current.prefetchIndex(openCache(), length)
        val stream = StreamSeekableInput(
            name = inputName,
            size = length,
            open = {
                val cache = openCache()
                if (!secondary) current.prefetchIndex(cache, length)
                val reader = cache.openStream(StreamRole.FOREGROUND)
                if (secondary) reader.readAheadLimit = PikPakStreamReader.DEFAULT_BLOCK_SIZE
                StreamSeekableInput.Opened(reader) { cache.isClosed }
            },
            urgentOnSeek = !secondary,
            isStale = current::isStale,
        )
        return CloudSeekableInput(
            name = inputName,
            stream = stream,
            onClosed = { current.releaseInput() },
            isStale = current::isStale,
        )
    }

    // An import has no gcid: it exists only on disk and is never streamed.
    override val isStreamingRemotely: Boolean
        get() = meta.gcid.isNotEmpty() && !stream.store.isComplete

    // What was only played goes with the session, as it did before playback went through the store;
    // a kept file stays for the next session.
    suspend fun close() {
        downloadWanted.value = false
        prefetchRange.value = null
        resolveMutex.withLock {
            cloud.release()
            if (kept || !wroteThisSession) {
                stream.close()
            } else {
                try {
                    stream.delete()
                } catch (e: Throwable) {
                    // Windows refuses to unlink a file an input still has open; startup pruning takes it
                    logger.warn(e) { "[$torrentId] $fileName could not remove what was only played" }
                }
            }
        }
        scope.cancel()
    }

    // The store, its pieces and every cache over them start again: the old ones describe a file
    // that no longer exists. The old files go before the new store loads, or it would read their
    // bitmap back as progress. The copy that was asked for is gone with them, so the file is no
    // longer kept: what is played of it later goes with its session again.
    private suspend fun deleteFiles() {
        prefetchRange.value = null
        withContext(NonCancellable) {
            resolveMutex.withLock {
                val old = stream
                generation++
                cloudReady = false
                cloud.release()
                old.delete()
                wroteThisSession = false
                stream = newStream()
            }
            kept = false
            saveKept()
        }
    }

    private inner class Stream(
        val pieces: MutablePieceList,
        val store: PikPakSparseStore,
        private val generation: Int,
    ) {
        /** Whether the file this stream describes has been deleted since. */
        fun isStale(): Boolean = this@PikPakFileEntry.generation != generation

        private val lock = SynchronizedObject()
        private var liveInputs = 0

        // 预取的是这个文件末尾的一段, 归文件所有而不是归某个 input: 进度条预览会为同一个文件再开一个
        // input, 各预取一份就是和正在播的读抢连接. 缓存换了 (换账号) 就要对新缓存再预取一次.
        private var indexPrefetch: Pair<PikPakFileCache, Deferred<Unit>>? = null

        fun prefetchIndex(cache: PikPakFileCache, length: Long) = synchronized(lock) {
            // A failed or withdrawn prefetch is tried again by the next input
            if (indexPrefetch?.first === cache && indexPrefetch?.second?.isCancelled != true) return@synchronized
            indexPrefetch?.second?.cancel()
            val tail = maxOf(0L, length - INDEX_BYTES) until length
            indexPrefetch = cache to cache.prefetch(listOf(tail), StreamRole.FOREGROUND, PikPakStreamReader.INDEX_PRIORITY)
        }

        fun claimInput(): Int = synchronized(lock) { liveInputs.also { liveInputs = it + 1 } }

        fun releaseInput() = synchronized(lock) {
            if (liveInputs > 0) liveInputs--
        }

        suspend fun close() {
            synchronized(lock) { indexPrefetch?.second?.cancel() }
            store.close()
        }

        suspend fun delete() {
            synchronized(lock) { indexPrefetch?.second?.cancel() }
            store.delete()
        }
    }

    private fun newStream(): Stream {
        val pieces = PieceList.create(totalSize = length, pieceSize = PIECE_SIZE)
        val store = PikPakSparseStore(
            dataFile = dataPath,
            size = length,
            parentCoroutineContext = storeContext,
            onHeld = { block ->
                wroteThisSession = true
                with(pieces) { pieces.createPieceByListIndexUnsafe(block).state = PieceState.FINISHED }
                // Progress means whatever stopped the download has cleared
                _error.value = null
            },
            blockSize = PIECE_SIZE,
        )
        with(pieces) {
            for (block in 0 until store.blockCount) {
                if (store.isHeld(block)) pieces.createPieceByListIndexUnsafe(block).state = PieceState.FINISHED
            }
        }
        return Stream(pieces, store, generation)
    }

    companion object {
        // One piece per block of the SDK's file cache, so the piece list and the store's bitmap agree.
        const val PIECE_SIZE: Long = PikPakStreamReader.DEFAULT_BLOCK_SIZE

        // What a cache download fetches first, so a file cached while it plays has its container
        // header and trailing index early.
        const val HEADER_SIZE: Long = 2L * 1024 * 1024
        const val FOOTER_SIZE: Long = 512L * 1024

        // The tail a player jumps to for a Matroska file's Cues or a trailing MP4 moov before the
        // first frame; fetched with the head so the jump is served from memory.
        const val INDEX_BYTES: Long = 512L * 1024

        val RETRY_DELAY = 30.seconds
    }
}

// Resolvers await this before handing data to the player, while engine fallback is still possible.
interface CloudReadiness {
    suspend fun ensureCloudReady()
}
