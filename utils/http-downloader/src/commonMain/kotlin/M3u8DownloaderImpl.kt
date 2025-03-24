/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.utils.httpdownloader

import io.ktor.client.request.header
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.HttpStatement
import io.ktor.client.statement.bodyAsChannel
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.io.buffered
import kotlinx.io.files.FileSystem
import kotlinx.io.files.Path
import me.him188.ani.utils.coroutines.IO_
import me.him188.ani.utils.httpdownloader.DownloadStatus.CANCELED
import me.him188.ani.utils.httpdownloader.DownloadStatus.COMPLETED
import me.him188.ani.utils.httpdownloader.DownloadStatus.DOWNLOADING
import me.him188.ani.utils.httpdownloader.DownloadStatus.FAILED
import me.him188.ani.utils.httpdownloader.DownloadStatus.INITIALIZING
import me.him188.ani.utils.httpdownloader.DownloadStatus.MERGING
import me.him188.ani.utils.httpdownloader.DownloadStatus.PAUSED
import me.him188.ani.utils.httpdownloader.m3u.DefaultM3u8Parser
import me.him188.ani.utils.httpdownloader.m3u.M3u8Parser
import me.him188.ani.utils.httpdownloader.m3u.M3u8Playlist
import me.him188.ani.utils.io.copyTo
import me.him188.ani.utils.io.resolve
import me.him188.ani.utils.ktor.ScopedHttpClient
import me.him188.ani.utils.platform.Uuid
import kotlin.concurrent.atomics.AtomicLong
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.coroutines.CoroutineContext

/**
 * A simple implementation of [M3u8Downloader] that uses Ktor and coroutines.
 */
open class KtorM3u8Downloader(
    protected val client: ScopedHttpClient,
    private val fileSystem: FileSystem,
    computeDispatcher: CoroutineContext = Dispatchers.Default,
    private val ioDispatcher: CoroutineContext = Dispatchers.IO_,
    val clock: Clock = Clock.System,
    private val m3u8Parser: M3u8Parser = DefaultM3u8Parser,
) : M3u8Downloader {

    protected val scope = CoroutineScope(SupervisorJob() + computeDispatcher)

    private val _progressFlow = MutableSharedFlow<DownloadProgress>(
        replay = 1,
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    override val progressFlow: Flow<DownloadProgress> = _progressFlow.asSharedFlow()

    override fun getProgressFlow(downloadId: DownloadId): Flow<DownloadProgress> {
        return progressFlow.filter { it.downloadId == downloadId }.onStart {
            emit(
                createProgress(getState(downloadId) ?: return@onStart),
            )
        }
    }

    /**
     * Our map of download states.
     */
    protected val _downloadStatesFlow = MutableStateFlow(emptyMap<DownloadId, DownloadEntry>())

    override val downloadStatesFlow: Flow<List<DownloadState>> =
        _downloadStatesFlow.map { it.values.map { entry -> entry.state } }

    override suspend fun init() {
    }

    protected val stateMutex = Mutex()

    override suspend fun download(
        url: String,
        outputPath: Path,
        options: DownloadOptions
    ): DownloadId {
        val downloadId = DownloadId(value = generateDownloadId(url))
        downloadWithId(downloadId, url, outputPath, options)
        return downloadId
    }

    override suspend fun downloadWithId(
        downloadId: DownloadId,
        url: String,
        outputPath: Path,
        options: DownloadOptions
    ) {
        stateMutex.withLock {
            val currentMap = _downloadStatesFlow.value.toMutableMap()
            val existing = currentMap[downloadId]
            if (existing != null) {
                return
            }
            val segmentCacheDir = createSegmentCacheDir(outputPath, downloadId)
            val initialState = DownloadState(
                downloadId = downloadId,
                url = url,
                outputPath = outputPath.toString(),
                segments = emptyList(),
                totalSegments = 0,
                downloadedBytes = 0L,
                timestamp = clock.now().toEpochMilliseconds(),
                status = INITIALIZING,
                segmentCacheDir = segmentCacheDir.toString(),
            )
            currentMap[downloadId] = DownloadEntry(job = null, state = initialState)
            _downloadStatesFlow.value = currentMap
        }

        emitProgress(downloadId)

        val job = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                val playlist = resolveM3u8MediaPlaylist(url, options)
                val segments = playlist.toSegments(Path(getState(downloadId)?.segmentCacheDir ?: return@launch))
                val totalSegments = segments.size

                updateState(downloadId) {
                    it.copy(segments = segments, totalSegments = totalSegments, status = DOWNLOADING)
                }
                emitProgress(downloadId)

                downloadSegments(downloadId, options)

                updateState(downloadId) {
                    it.copy(status = MERGING, timestamp = clock.now().toEpochMilliseconds())
                }
                emitProgress(downloadId)

                mergeSegments(downloadId)

                updateState(downloadId) {
                    it.copy(status = COMPLETED, timestamp = clock.now().toEpochMilliseconds())
                }
                emitProgress(downloadId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                updateState(downloadId) {
                    it.copy(
                        status = FAILED,
                        error = DownloadError(
                            code = if (e is M3u8Exception) e.errorCode else DownloadErrorCode.UNEXPECTED_ERROR,
                            technicalMessage = e.message,
                        ),
                        timestamp = clock.now().toEpochMilliseconds(),
                    )
                }
                emitProgress(downloadId)
            }
        }

        // store the job
        stateMutex.withLock {
            val currentMap = _downloadStatesFlow.value.toMutableMap()
            val entry = currentMap[downloadId]
            if (entry != null) {
                currentMap[downloadId] = entry.copy(job = job)
                _downloadStatesFlow.value = currentMap
            }
        }
    }

    override suspend fun resume(downloadId: DownloadId): Boolean {
        val st = getState(downloadId) ?: return false
        if (st.status != PAUSED && st.status != FAILED) {
            return false
        }

        // Check if there's already an active job
        val hasExistingJob = stateMutex.withLock {
            val existing = _downloadStatesFlow.value[downloadId]
            existing != null && existing.job?.isActive == true
        }
        if (hasExistingJob) { // must be done outside the lock
            emitProgress(downloadId)
            return true
        }

        updateState(downloadId) { it.copy(status = DOWNLOADING) }
        emitProgress(downloadId)
        val job = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                downloadSegments(downloadId, DownloadOptions())
                updateState(downloadId) { it.copy(status = MERGING) }
                emitProgress(downloadId)
                mergeSegments(downloadId)
                updateState(downloadId) { it.copy(status = COMPLETED) }
                emitProgress(downloadId)
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                updateState(downloadId) {
                    it.copy(
                        status = FAILED,
                        error = DownloadError(DownloadErrorCode.UNEXPECTED_ERROR, technicalMessage = t.message),
                    )
                }
                emitProgress(downloadId)
            }
        }
        stateMutex.withLock {
            val currentMap = _downloadStatesFlow.value.toMutableMap()
            val entry = currentMap[downloadId]
            if (entry != null) {
                currentMap[downloadId] = entry.copy(job = job)
                _downloadStatesFlow.value = currentMap
            }
        }
        return true
    }

    override suspend fun getActiveDownloadIds(): List<DownloadId> {
        return stateMutex.withLock {
            _downloadStatesFlow.value.values
                .filter { it.state.status == DOWNLOADING || it.state.status == INITIALIZING }
                .map { it.state.downloadId }
        }
    }

    override suspend fun pause(downloadId: DownloadId): Boolean {
        stateMutex.withLock {
            val currentMap = _downloadStatesFlow.value.toMutableMap()
            val entry = currentMap[downloadId] ?: return false
            val job = entry.job
            if (job != null) {
                if (!job.isActive) return false
                job.cancel()
            }

            val oldState = entry.state
            currentMap[downloadId] = entry.copy(
                job = null,
                state = oldState.copy(status = PAUSED),
            )
            _downloadStatesFlow.value = currentMap
        }
        emitProgress(downloadId)
        return true
    }

    override suspend fun pauseAll(): List<DownloadId> {
        val paused = mutableListOf<DownloadId>()
        stateMutex.withLock {
            val currentMap = _downloadStatesFlow.value.toMutableMap()
            currentMap.forEach { (id, entry) ->
                val job = entry.job
                if (job != null && job.isActive) {
                    job.cancel()
                    currentMap[id] = entry.copy(
                        job = null,
                        state = entry.state.copy(status = PAUSED),
                    )
                    paused.add(id)
                }
            }
            _downloadStatesFlow.value = currentMap
        }
        paused.forEach { emitProgress(it) }
        return paused
    }

    override suspend fun cancel(downloadId: DownloadId): Boolean {
        stateMutex.withLock {
            val currentMap = _downloadStatesFlow.value.toMutableMap()
            val entry = currentMap[downloadId] ?: return false
            val job = entry.job
            if (job != null && job.isActive) {
                job.cancel()
            }
            currentMap[downloadId] = entry.copy(
                job = null,
                state = entry.state.copy(status = CANCELED),
            )
            _downloadStatesFlow.value = currentMap
        }
        emitProgress(downloadId)
        return true
    }

    override suspend fun cancelAll() {
        stateMutex.withLock {
            val currentMap = _downloadStatesFlow.value.toMutableMap()
            currentMap.forEach { (id, entry) ->
                if (entry.job?.isActive == true) {
                    entry.job.cancel()
                }
                val st = entry.state
                if (st.status in listOf(INITIALIZING, DOWNLOADING, PAUSED, MERGING)) {
                    currentMap[id] = entry.copy(
                        job = null,
                        state = st.copy(status = CANCELED),
                    )
                }
            }
            _downloadStatesFlow.value = currentMap
        }
        val allIds = stateMutex.withLock { _downloadStatesFlow.value.keys.toList() }
        allIds.forEach { emitProgress(it) }
    }

    override suspend fun getState(downloadId: DownloadId): DownloadState? {
        return stateMutex.withLock {
            _downloadStatesFlow.value[downloadId]?.state
        }
    }

    override suspend fun getAllStates(): List<DownloadState> {
        return stateMutex.withLock {
            _downloadStatesFlow.value.values.map { it.state }
        }
    }

    override fun close() {
        scope.launch(NonCancellable + CoroutineName("M3u8Downloader.close")) {
            closeSuspend()
        }
    }

    suspend fun closeSuspend() {
        stateMutex.withLock {
            val currentMap = _downloadStatesFlow.value
            currentMap.forEach { (_, entry) ->
                if (entry.job?.isActive == true) {
                    entry.job.cancelAndJoin()
                }
            }
            // If we want to remove them entirely:
            _downloadStatesFlow.value = emptyMap()
        }
        scope.cancel()
    }

    // -------------------------------------------------------
    // Internal details
    // -------------------------------------------------------

    protected data class DownloadEntry(val job: Job?, val state: DownloadState)

    protected fun createSegmentCacheDir(outputPath: Path, downloadId: DownloadId): Path {
        val cacheDirName = outputPath.name + "_segments_" + downloadId.value
        val parentDir = outputPath.parent ?: Path(".")
        val cacheDir = parentDir.resolve(cacheDirName)
        fileSystem.createDirectories(cacheDir)
        return cacheDir
    }

    /**
     * Recursively resolves a MasterPlaylist to a MediaPlaylist (if needed).
     */
    private suspend fun resolveM3u8MediaPlaylist(
        url: String,
        options: DownloadOptions,
        depth: Int = 0
    ): M3u8Playlist.MediaPlaylist {
        if (depth >= 5) {
            throw M3u8Exception(DownloadErrorCode.NO_MEDIA_LIST)
        }
        val response = httpGet(url, options) { it.body<String>() }
        return when (val playlist = m3u8Parser.parse(response, url)) {
            is M3u8Playlist.MasterPlaylist -> {
                val bestVariant = playlist.variants.maxByOrNull { it.bandwidth }
                    ?: throw M3u8Exception(DownloadErrorCode.NO_MEDIA_LIST)
                resolveM3u8MediaPlaylist(bestVariant.uri, options, depth + 1)
            }

            is M3u8Playlist.MediaPlaylist -> {
                playlist
            }
        }
    }

    protected suspend fun updateState(downloadId: DownloadId, transform: (DownloadState) -> DownloadState) {
        stateMutex.withLock {
            val currentMap = _downloadStatesFlow.value.toMutableMap()
            val entry = currentMap[downloadId] ?: return
            val oldState = entry.state
            val newState = transform(oldState)
            currentMap[downloadId] = entry.copy(state = newState)
            _downloadStatesFlow.value = currentMap
        }
    }

    @OptIn(ExperimentalAtomicApi::class)
    protected suspend fun downloadSingleSegment(
        downloadId: DownloadId,
        segmentInfo: SegmentInfo,
        options: DownloadOptions
    ): Long {
        httpGet(segmentInfo.url, options) {
            val channel = it.body<HttpResponse>().bodyAsChannel()

            val segmentPath = Path(segmentInfo.tempFilePath)
            fileSystem.createDirectories(segmentPath.parent ?: Path("."))

            val totalBytes = AtomicLong(0L)
            fileSystem.sink(segmentPath).buffered().use { sink ->
                val ktorBuffer = ByteArray(8 * 1024)
                withContext(ioDispatcher) {
                    while (true) {
                        val bytesRead = channel.readAvailable(ktorBuffer, 0, ktorBuffer.size)
                        if (bytesRead == -1) break
                        sink.write(ktorBuffer, startIndex = 0, endIndex = bytesRead)
                        totalBytes.fetchAndAdd(bytesRead.toLong())
                    }
                }
            }
            return totalBytes.load()
        }
    }

    protected suspend fun downloadSegments(downloadId: DownloadId, options: DownloadOptions) {
        val snapshot = getState(downloadId) ?: return
        if (snapshot.segments.isEmpty()) return
        val semaphore = Semaphore(options.maxConcurrentSegments)

        coroutineScope {
            snapshot.segments.forEach { seg ->
                if (seg.isDownloaded) return@forEach
                launch(ioDispatcher) {
                    semaphore.acquire()
                    try {
                        val newSize = downloadSingleSegment(downloadId, seg, options)
                        markSegmentDownloaded(downloadId, seg.index, newSize)
                    } finally {
                        semaphore.release()
                    }
                }
            }
        }
    }

    protected suspend fun markSegmentDownloaded(downloadId: DownloadId, segmentIndex: Int, byteSize: Long) {
        updateState(downloadId) { old ->
            val updatedSegments = old.segments.map {
                if (it.index == segmentIndex) it.copy(isDownloaded = true, byteSize = byteSize) else it
            }
            old.copy(downloadedBytes = old.downloadedBytes + byteSize, segments = updatedSegments)
        }
        emitProgress(downloadId)
    }

    protected suspend fun mergeSegments(downloadId: DownloadId) {
        val st = getState(downloadId) ?: return
        val cacheDir = Path(st.segmentCacheDir)
        val finalOutput = Path(st.outputPath)
        fileSystem.sink(finalOutput).buffered().use { out ->
            st.segments.sortedBy { it.index }.forEach { seg ->
                fileSystem.source(Path(seg.tempFilePath)).buffered().use { input ->
                    input.copyTo(out)
                }
            }
        }
        // remove segment files
        st.segments.forEach { seg ->
            fileSystem.delete(Path(seg.tempFilePath))
        }
        // remove the cache dir
        fileSystem.delete(cacheDir)
    }

    protected suspend fun emitProgress(downloadId: DownloadId) {
        val st = getState(downloadId) ?: return
        val progress = createProgress(st)
        _progressFlow.emit(progress)
    }

    private fun createProgress(st: DownloadState): DownloadProgress {
        val downloadedSegments = st.segments.count { it.isDownloaded }
        val progress = DownloadProgress(
            downloadId = st.downloadId,
            url = st.url,
            totalSegments = st.totalSegments,
            downloadedSegments = downloadedSegments,
            downloadedBytes = st.downloadedBytes,
            totalBytes = st.segments.sumOf { it.byteSize.coerceAtLeast(0) }
                .coerceAtLeast(st.downloadedBytes),
            status = st.status,
            error = st.error,
        )
        return progress
    }

    protected suspend inline fun <R> httpGet(url: String, options: DownloadOptions, block: (HttpStatement) -> R): R {
        return client.use {
            prepareGet(url) {
                options.headers.forEach { (k, v) -> header(k, v) }
            }.let { statement -> block(statement) }
        }
    }

    protected fun generateDownloadId(url: String): String {
        return Uuid.randomString()
    }

    suspend fun joinDownload(downloadId: DownloadId) {
        val job = stateMutex.withLock {
            _downloadStatesFlow.value[downloadId]?.job
        }
        job?.join()
    }
}

private class M3u8Exception(val errorCode: DownloadErrorCode) : Exception()

private fun M3u8Playlist.MediaPlaylist.toSegments(cacheDir: Path): List<SegmentInfo> {
    return segments.mapIndexed { i, seg ->
        val idx = mediaSequence + i
        SegmentInfo(
            index = idx,
            url = seg.uri,
            isDownloaded = false,
            byteSize = seg.byteRange?.length ?: -1,
            tempFilePath = cacheDir.resolve("$idx.ts").toString(),
        )
    }
}
