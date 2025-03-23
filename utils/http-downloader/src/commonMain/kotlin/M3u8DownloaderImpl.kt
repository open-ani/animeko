/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.utils.httpdownloader

import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
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
import me.him188.ani.utils.httpdownloader.DownloadStatus.PAUSED
import me.him188.ani.utils.httpdownloader.m3u.DefaultM3u8Parser
import me.him188.ani.utils.httpdownloader.m3u.M3u8Parser
import me.him188.ani.utils.httpdownloader.m3u.M3u8Playlist
import me.him188.ani.utils.io.resolve

/**
 * A simple implementation of [M3u8Downloader] that uses Ktor and coroutines.
 *
 * @param client The Ktor HTTP client used for downloads.
 * @param computeDispatcher The coroutine dispatcher on which downloads will run (default: [Dispatchers.Default]).
 */
class KtorM3u8Downloader(
    private val client: HttpClient,
    private val fileSystem: FileSystem,
    private val computeDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO_,
    val clock: Clock = Clock.System,
    private val m3u8Parser: M3u8Parser = DefaultM3u8Parser,
) : M3u8Downloader {

    private val scope = CoroutineScope(SupervisorJob() + computeDispatcher)

    /**
     * Holds the shared flow of [DownloadProgress] for all active downloads.
     * We use `replay = 1` so that new collectors get the latest progress immediately.
     */
    private val _progressFlow = MutableSharedFlow<DownloadProgress>(
        replay = 1,
        extraBufferCapacity = 64,
    )
    override val progressFlow: Flow<DownloadProgress> = _progressFlow.asSharedFlow()

    /**
     * Get a flow for a specific download by filtering the shared flow.
     */
    override fun getProgressFlow(downloadId: DownloadId): Flow<DownloadProgress> {
        return progressFlow.filter { it.downloadId == downloadId }
    }

    /**
     * Internal map to track [DownloadState]. This could be replaced by database or other persistent storage.
     */
    private val downloadStates = mutableMapOf<DownloadId, DownloadState>()

    /**
     * Internal map to track the active jobs associated with each download.
     */
    private val downloadJobs = mutableMapOf<DownloadId, Job>()

    /**
     * Mutex to guard access to mutable maps ([downloadStates], [downloadJobs]).
     */
    private val stateMutex = Mutex()

    /**
     * Starts a download using an auto-generated [DownloadId].
     */
    override suspend fun download(
        url: String,
        outputPath: Path,
        options: DownloadOptions
    ): DownloadId {
        val downloadId = DownloadId(value = generateDownloadId(url))
        downloadWithId(downloadId, url, outputPath, options)
        return downloadId
    }

    /**
     * Starts a new download with the provided [downloadId].
     */
    override suspend fun downloadWithId(
        downloadId: DownloadId,
        url: String,
        outputPath: Path,
        options: DownloadOptions
    ) {
        stateMutex.withLock {
            // If a download with this ID already exists and is active, do nothing
            if (downloadJobs[downloadId]?.isActive == true) {
                return
            }
        }

        // Create or update the DownloadState to INITIALIZING
        val initialState = DownloadState(
            downloadId = downloadId,
            url = url,
            outputPath = outputPath.toString(),
            segments = emptyList(),  // Will be updated after M3U8 parse
            totalSegments = 0,
            downloadedBytes = 0L,
            timestamp = clock.now().toEpochMilliseconds(),
            status = INITIALIZING,
        )

        stateMutex.withLock {
            downloadStates[downloadId] = initialState
        }

        // Launch a job to parse the M3U8 and download segments
        val job = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                val segments = resolveM3u8MediaPlaylist(url, options)
                    .toSegments()
                val totalSegments = segments.size

                // 2) Update state to DOWNLOADING
                updateState(downloadId) {
                    it.copy(
                        segments = segments,
                        totalSegments = totalSegments,
                        status = DOWNLOADING,
                    )
                }

                // 3) Download all segments with concurrency
                downloadSegments(downloadId, options)

                // 4) If we reach here successfully, mark as COMPLETED
                updateState(downloadId) {
                    it.copy(
                        status = COMPLETED,
                        timestamp = clock.now().toEpochMilliseconds(),
                    )
                }
                emitProgress(downloadId)
            } catch (e: CancellationException) {
                // Job canceled => do not set status to FAILED or COMPLETED
                // We'll handle that in `pause` or `cancel`
                throw e
            } catch (e: Throwable) {
                // Something went wrong => set status to FAILED
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

        stateMutex.withLock {
            downloadJobs[downloadId] = job
        }
    }

    private suspend fun resolveM3u8MediaPlaylist(
        url: String,
        options: DownloadOptions,
        depth: Int = 0,
    ): M3u8Playlist.MediaPlaylist {
        if (depth >= 5) {
            throw M3u8Exception(DownloadErrorCode.NO_MEDIA_LIST)
        }
        when (val playlist = m3u8Parser.parse(httpGet(url, options).bodyAsText(), url)) {
            is M3u8Playlist.MasterPlaylist -> {
                val bestVariant = playlist.variants.maxByOrNull { it.bandwidth }
                    ?: throw M3u8Exception(DownloadErrorCode.NO_MEDIA_LIST)
                return resolveM3u8MediaPlaylist(
                    bestVariant.uri,
                    options, depth + 1,
                )
            }

            is M3u8Playlist.MediaPlaylist -> {
                return playlist
            }
        }
    }

    /**
     * Resumes a paused or failed download. Re-initiates the job with the existing [DownloadState].
     */
    override suspend fun resume(downloadId: DownloadId): Boolean {
        val state = getState(downloadId) ?: return false
        if (state.status != PAUSED && state.status != FAILED) {
            return false
        }

        // Re-launch the same logic as `downloadWithId` but with the same segments
        stateMutex.withLock {
            if (downloadJobs[downloadId]?.isActive == true) {
                // Already resuming
                return true
            }
        }

        // Mark status as DOWNLOADING, then re-download any missing segments
        updateState(downloadId) {
            it.copy(status = DOWNLOADING)
        }

        val job = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                downloadSegments(downloadId, DownloadOptions()) // use default or reconstruct from state
                // Mark as completed if all segments are downloaded
                updateState(downloadId) {
                    it.copy(status = COMPLETED)
                }
                emitProgress(downloadId)
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                updateState(downloadId) {
                    it.copy(
                        status = FAILED,
                        error = DownloadError(
                            code = DownloadErrorCode.UNEXPECTED_ERROR,
                            technicalMessage = t.message,
                        ),
                    )
                }
                emitProgress(downloadId)
            }
        }

        stateMutex.withLock {
            downloadJobs[downloadId] = job
        }
        return true
    }

    override suspend fun getActiveDownloadIds(): List<DownloadId> {
        return stateMutex.withLock {
            downloadStates.values
                .filter { it.status == DOWNLOADING || it.status == INITIALIZING }
                .map { it.downloadId }
        }
    }

    /**
     * Pauses a specific download. In this example, we simply cancel its job and mark status = PAUSED.
     */
    override suspend fun pause(downloadId: DownloadId): Boolean {
        stateMutex.withLock {
            val job = downloadJobs[downloadId] ?: return false
            if (!job.isActive) return false
            job.cancel()
            downloadJobs.remove(downloadId)
            // Mark state as paused
            downloadStates[downloadId]?.let { old ->
                downloadStates[downloadId] = old.copy(status = PAUSED)
            }
        }
        emitProgress(downloadId)
        return true
    }

    /**
     * Pauses all active downloads by cancelling their jobs and marking them as PAUSED.
     */
    override suspend fun pauseAll(): List<DownloadId> {
        val paused = mutableListOf<DownloadId>()
        stateMutex.withLock {
            downloadJobs.forEach { (id, job) ->
                if (job.isActive) {
                    job.cancel()
                    paused.add(id)
                    downloadStates[id]?.let { old ->
                        downloadStates[id] = old.copy(status = PAUSED)
                    }
                }
            }
            paused.forEach { downloadJobs.remove(it) }
        }
        paused.forEach { emitProgress(it) }
        return paused
    }

    /**
     * Cancels a specific download. Mark as CANCELED and remove from active jobs.
     */
    override suspend fun cancel(downloadId: DownloadId): Boolean {
        stateMutex.withLock {
            val job = downloadJobs[downloadId] ?: return false
            if (job.isActive) {
                job.cancel()
            }
            downloadJobs.remove(downloadId)
            downloadStates[downloadId]?.let { old ->
                downloadStates[downloadId] = old.copy(status = CANCELED)
            }
        }
        emitProgress(downloadId)
        return true
    }

    /**
     * Cancels all active downloads.
     */
    override suspend fun cancelAll() {
        stateMutex.withLock {
            downloadJobs.forEach { (_, job) ->
                if (job.isActive) job.cancel()
            }
            downloadJobs.clear()
            downloadStates.keys.forEach { id ->
                downloadStates[id]?.let { old ->
                    if (old.status == DOWNLOADING || old.status == INITIALIZING || old.status == PAUSED) {
                        downloadStates[id] = old.copy(status = CANCELED)
                    }
                }
            }
        }
        // Emit progress for all
        val allIds = downloadStates.keys.toList()
        allIds.forEach { emitProgress(it) }
    }

    /**
     * Get the current state of a download.
     */
    override suspend fun getState(downloadId: DownloadId): DownloadState? {
        return stateMutex.withLock {
            downloadStates[downloadId]
        }
    }

    /**
     * Get all active states.
     */
    override suspend fun getAllStates(): List<DownloadState> {
        return stateMutex.withLock {
            downloadStates.values.toList()
        }
    }

    /**
     * Saves state for the specified download to persistent storage.
     * This example is just in-memory, so it's a no-op returning true.
     */
    override suspend fun saveState(downloadId: DownloadId): Boolean {
        // TODO: Integrate with real persistence (e.g. Room, DataStore, etc.)
        // For now, do nothing and pretend success.
        return true
    }

    /**
     * Saves states for all active downloads to persistent storage.
     * This example is just in-memory, so it's a no-op returning true.
     */
    override suspend fun saveAllStates(): Boolean {
        // TODO: Integrate with real persistence
        return true
    }

    /**
     * Loads all previously saved states from persistent storage.
     * This example is just in-memory, so there's nothing to load.
     */
    override suspend fun loadSavedStates(): List<DownloadId> {
        // TODO: Integrate with real persistence
        return emptyList()
    }

    /**
     * Checks if a saved state exists for the given download ID.
     * This example is just in-memory, so we only check if we have it in our map.
     */
    override suspend fun hasSavedState(downloadId: DownloadId): Boolean {
        return stateMutex.withLock {
            downloadStates.containsKey(downloadId)
        }
    }

    /**
     * Closes the downloader, cancelling all active jobs and closing the [HttpClient].
     */
    override fun close() {
        runBlocking {
            stateMutex.withLock {
                downloadJobs.forEach { (_, job) ->
                    if (job.isActive) {
                        job.cancel()
                    }
                }
                downloadJobs.clear()
            }
            client.close()
            scope.cancel() // Cancel the entire coroutine scope
        }
    }

    // ------------------------------------------------------------------------
    // Internal helpers
    // ------------------------------------------------------------------------
    /**
     * Downloads segments, respecting [DownloadOptions.maxConcurrentSegments].
     * Updates progress as it goes.
     */
    private suspend fun downloadSegments(downloadId: DownloadId, options: DownloadOptions) {
        val stateSnapshot = getState(downloadId) ?: return
        if (stateSnapshot.segments.isEmpty()) return

        val semaphore = Semaphore(options.maxConcurrentSegments)
        val segmentDownloadJobs = mutableListOf<Deferred<Unit>>()

        stateSnapshot.segments.forEach { segmentInfo ->
            // Skip if already downloaded
            if (segmentInfo.isDownloaded) return@forEach

            val deferred = scope.async {
                semaphore.acquire()
                try {
                    val newSize = downloadSegment(segmentInfo.url, options, segmentInfo.index, downloadId)
                    markSegmentDownloaded(downloadId, segmentInfo.index, newSize)
                } finally {
                    semaphore.release()
                }
            }
            segmentDownloadJobs += deferred
        }

        // Wait for all segments to finish
        segmentDownloadJobs.awaitAll()
    }

    /**
     * Download a single segment in a streaming fashion so we do NOT hold the entire segment in memory.
     *
     * @return the number of bytes actually downloaded.
     */
    private suspend fun downloadSegment(
        url: String,
        options: DownloadOptions,
        segmentIndex: Int,
        downloadId: DownloadId
    ): Long {
        val response = httpGet(url, options)
        val channel = response.bodyAsChannel()

        // We’ll store each segment as a separate file, e.g. /path/to/output/0.ts, 1.ts, 2.ts, etc.
        val state = getState(downloadId) ?: error("No state found for downloadId=$downloadId")
        val outputDir = Path(state.outputPath)
        val segmentFile = outputDir.resolve("$segmentIndex.ts")

        var totalBytes = 0L

        fileSystem.sink(segmentFile).buffered().use { sink ->
            val ktorBuffer = ByteArray(8 * 1024)
            while (true) {
                val bytesRead = channel.readAvailable(ktorBuffer, 0, ktorBuffer.size)
                if (bytesRead == -1) break
                sink.write(ktorBuffer, startIndex = 0, endIndex = bytesRead)
                totalBytes += bytesRead
            }
        }

        return totalBytes
    }

    private suspend fun httpGet(
        url: String,
        options: DownloadOptions
    ): HttpResponse = client.prepareGet(url) {
        options.headers.forEach { (key, value) ->
            header(key, value)
        }
    }.body()

    /**
     * Marks a specific segment as downloaded, updates [downloadedBytes], and emits progress.
     */
    private suspend fun markSegmentDownloaded(downloadId: DownloadId, segmentIndex: Int, byteSize: Long) {
        updateState(downloadId) { old ->
            val newSegments = old.segments.map { seg ->
                if (seg.index == segmentIndex) {
                    seg.copy(isDownloaded = true, byteSize = byteSize)
                } else seg
            }
            val newDownloadedBytes = old.downloadedBytes + byteSize
            old.copy(
                segments = newSegments,
                downloadedBytes = newDownloadedBytes,
            )
        }
        emitProgress(downloadId)
    }

    /**
     * Helper that updates the internal [downloadStates] map inside a [stateMutex] lock
     * and returns the new state.
     */
    private suspend fun updateState(downloadId: DownloadId, transform: (DownloadState) -> DownloadState) {
        stateMutex.withLock {
            val old = downloadStates[downloadId] ?: return
            val new = transform(old)
            downloadStates[downloadId] = new
        }
    }

    /**
     * Reads the latest [DownloadState] from [downloadStates] and emits a new [DownloadProgress].
     */
    private suspend fun emitProgress(downloadId: DownloadId) {
        val currentState = getState(downloadId) ?: return
        // Build a DownloadProgress
        val (downloadedSegments, totalSegments) = with(currentState) {
            segments.count { it.isDownloaded } to totalSegments
        }
        val progress = DownloadProgress(
            downloadId = currentState.downloadId,
            url = currentState.url,
            totalSegments = currentState.totalSegments,
            downloadedSegments = downloadedSegments,
            downloadedBytes = currentState.downloadedBytes,
            totalBytes = currentState.segments.sumOf { it.byteSize.coerceAtLeast(0) },
            speedBytesPerSecond = 0L, // not implemented in this example
            estimatedTimeRemainingSeconds = -1L, // not implemented in this example
            status = currentState.status,
            error = currentState.error,
        )
        _progressFlow.emit(progress)
    }

    /**
     * Generates a simple unique ID based on the URL and timestamp.
     */
    private fun generateDownloadId(url: String): String {
        return "download_${url.hashCode()}_${clock.now().toEpochMilliseconds()}"
    }

    /**
     * Wait for a particular download job to finish (either complete or fail).
     */
    suspend fun joinDownload(downloadId: DownloadId) {
        stateMutex.withLock {
            downloadJobs[downloadId]
        }?.join()
    }
}

private class M3u8Exception(
    val errorCode: DownloadErrorCode,
) : Exception()

private fun M3u8Playlist.MediaPlaylist.toSegments(): List<SegmentInfo> {
    return this.segments.mapIndexed { i, segment ->

        SegmentInfo(
            index = this.mediaSequence + i,
            url = segment.uri,
            isDownloaded = false,
            byteSize = -1,
            tempFilePath = null,
        )
    }
}
