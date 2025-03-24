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
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
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
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.filter
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
import me.him188.ani.utils.platform.Uuid
import kotlin.concurrent.atomics.AtomicLong
import kotlin.concurrent.atomics.ExperimentalAtomicApi

/**
 * A simple implementation of [M3u8Downloader] that uses Ktor and coroutines.
 *
 * This version:
 * - Uses a dedicated cache directory to store each segment separately.
 * - Merges segments into the final file when all downloads finish.
 * - Cleans up segment files after merging.
 *
 * @param client The Ktor HTTP client used for downloads.
 * @param fileSystem The filesystem to use for reading/writing segments and output files.
 * @param computeDispatcher The dispatcher used for compute-heavy tasks (default: [Dispatchers.Default]).
 * @param ioDispatcher The dispatcher used for IO-bound tasks (default: [IO_]).
 * @param clock For time-based operations such as generating unique IDs or timestamping states.
 * @param m3u8Parser The parser used to interpret m3u8 content.
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
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    override val progressFlow: Flow<DownloadProgress> = _progressFlow.asSharedFlow()

    /**
     * Get a flow for a specific download by filtering the shared flow.
     */
    override fun getProgressFlow(downloadId: DownloadId): Flow<DownloadProgress> {
        return progressFlow.filter { it.downloadId == downloadId }
    }

    /**
     * Single map to track all downloads (their [DownloadState] and associated [Job]).
     * Could be replaced or augmented by a database or other persistent storage for states.
     */
    private val downloads = mutableMapOf<DownloadId, DownloadEntry>()

    /**
     * Mutex to guard access to [downloads].
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
     *
     * Creates a dedicated cache directory to store segments, parses the m3u8
     * playlist, downloads all segments, then merges them into [outputPath].
     */
    override suspend fun downloadWithId(
        downloadId: DownloadId,
        url: String,
        outputPath: Path,
        options: DownloadOptions
    ) {
        stateMutex.withLock {
            // If a download with this ID already exists and is active, do nothing
            val existing = downloads[downloadId]
            if (existing != null && existing.job?.isActive == true) {
                return
            }
        }

        // Create or update the DownloadState to INITIALIZING
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

        // Register or override in the map with a placeholder job
        stateMutex.withLock {
            downloads[downloadId] = DownloadEntry(
                job = null,
                state = initialState,
            )
        }
        emitProgress(downloadId)

        // Launch a job to parse M3U8, download segments, merge, etc.
        val job = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                // 1) Parse M3U8
                val playlist = resolveM3u8MediaPlaylist(url, options)
                val segments = playlist.toSegments(segmentCacheDir)
                val totalSegments = segments.size

                // 2) Update state to DOWNLOADING
                updateState(downloadId) {
                    it.copy(
                        segments = segments,
                        totalSegments = totalSegments,
                        status = DOWNLOADING,
                    )
                }
                emitProgress(downloadId)

                // 3) Download all segments
                downloadSegments(downloadId, options)

                // 4) After all segments are downloaded => set status to MERGING
                updateState(downloadId) {
                    it.copy(
                        status = MERGING,
                        timestamp = clock.now().toEpochMilliseconds(),
                    )
                }
                emitProgress(downloadId)

                // 5) Merge segments into the final file
                mergeSegments(downloadId)

                // 6) Mark as COMPLETED
                updateState(downloadId) {
                    it.copy(
                        status = COMPLETED,
                        timestamp = clock.now().toEpochMilliseconds(),
                    )
                }
                emitProgress(downloadId)

            } catch (e: CancellationException) {
                // Job canceled => do not set status to FAILED or COMPLETED
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

        // Store the job in the single map
        stateMutex.withLock {
            downloads[downloadId]?.job = job
        }
    }

    /**
     * Resumes a paused or failed download. Re-initiates the job with the existing [DownloadState].
     * In this simple version, if segments are partially downloaded, they won't be re-downloaded
     * if [SegmentInfo.isDownloaded] is `true`. After all segments are confirmed, merges them.
     */
    override suspend fun resume(downloadId: DownloadId): Boolean {
        val state = getState(downloadId) ?: return false
        if (state.status != PAUSED && state.status != FAILED) {
            return false
        }

        // Check if there's already an active job for this ID
        stateMutex.withLock {
            val existing = downloads[downloadId]
            if (existing != null && existing.job?.isActive == true) {
                // Already resuming
                return true
            }
        }

        // Mark status as DOWNLOADING, then re-download any missing segments
        updateState(downloadId) { it.copy(status = DOWNLOADING) }

        val job = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                // 1) Download missing segments
                downloadSegments(downloadId, DownloadOptions()) // or reconstruct from state, as needed

                // 2) Merge
                updateState(downloadId) { it.copy(status = MERGING) }
                emitProgress(downloadId)
                mergeSegments(downloadId)

                // 3) Completed
                updateState(downloadId) { it.copy(status = COMPLETED) }
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
            downloads[downloadId]?.job = job
        }
        return true
    }

    /**
     * Gets all currently active download IDs.
     */
    override suspend fun getActiveDownloadIds(): List<DownloadId> {
        return stateMutex.withLock {
            downloads.values
                .map { it.state }
                .filter { it.status == DOWNLOADING || it.status == INITIALIZING }
                .map { it.downloadId }
        }
    }

    /**
     * Pauses a specific download by canceling its job and marking status = PAUSED.
     */
    override suspend fun pause(downloadId: DownloadId): Boolean {
        stateMutex.withLock {
            val entry = downloads[downloadId] ?: return false
            val job = entry.job ?: return false
            if (!job.isActive) return false
            job.cancel()
            entry.job = null
            val oldState = entry.state
            entry.state = oldState.copy(status = PAUSED)
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
            downloads.forEach { (id, entry) ->
                val job = entry.job
                if (job != null && job.isActive) {
                    job.cancel()
                    entry.job = null
                    entry.state = entry.state.copy(status = PAUSED)
                    paused.add(id)
                }
            }
        }
        paused.forEach { emitProgress(it) }
        return paused
    }

    /**
     * Cancels a specific download, removing it from active jobs and marking status = CANCELED.
     */
    override suspend fun cancel(downloadId: DownloadId): Boolean {
        stateMutex.withLock {
            val entry = downloads[downloadId] ?: return false
            val job = entry.job
            if (job != null && job.isActive) {
                job.cancel()
            }
            entry.job = null
            entry.state = entry.state.copy(status = CANCELED)
        }
        emitProgress(downloadId)
        return true
    }

    /**
     * Cancels all active downloads, removing them from jobs and marking them as CANCELED.
     */
    override suspend fun cancelAll() {
        stateMutex.withLock {
            downloads.forEach { (_, entry) ->
                if (entry.job?.isActive == true) {
                    entry.job?.cancel()
                }
                entry.job = null
                val st = entry.state
                if (st.status == DOWNLOADING || st.status == INITIALIZING || st.status == PAUSED) {
                    entry.state = st.copy(status = CANCELED)
                }
            }
        }
        // Emit progress for all
        val allIds = stateMutex.withLock { downloads.keys.toList() }
        allIds.forEach { emitProgress(it) }
    }

    /**
     * Get the current state of a download.
     */
    override suspend fun getState(downloadId: DownloadId): DownloadState? {
        return stateMutex.withLock {
            downloads[downloadId]?.state
        }
    }

    /**
     * Get all active states.
     */
    override suspend fun getAllStates(): List<DownloadState> {
        return stateMutex.withLock {
            downloads.values.map { it.state }.toList()
        }
    }

    /**
     * Saves state for the specified download to persistent storage.
     * This example is just in-memory, so it's a no-op returning true.
     */
    override suspend fun saveState(downloadId: DownloadId): Boolean {
        // TODO: Integrate with real persistence (e.g. Room, DataStore, etc.)
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
     * This example is just in-memory, so we only check our map.
     */
    override suspend fun hasSavedState(downloadId: DownloadId): Boolean {
        return stateMutex.withLock {
            downloads.containsKey(downloadId)
        }
    }

    /**
     * Closes the downloader, cancelling all active jobs and closing the [HttpClient].
     */
    override fun close() {
        scope.launch(NonCancellable + CoroutineName("M3u8Downloader.close")) {
            closeSuspend()
        }
    }

    suspend fun closeSuspend() {
        stateMutex.withLock {
            downloads.forEach { (_, entry) ->
                if (entry.job?.isActive == true) {
                    entry.job?.cancelAndJoin()
                }
            }
            downloads.clear()
        }
        client.close()
        scope.cancel() // Cancel the entire coroutine scope
    }

    // ------------------------------------------------------------------------
    // Internal helpers
    // ------------------------------------------------------------------------

    /**
     * Data structure holding both the [DownloadState] and the [Job] for a download.
     */
    private data class DownloadEntry(
        var job: Job?,
        var state: DownloadState
    )

    /**
     * Creates a dedicated subdirectory for segment caching.
     * For example: if outputPath=/downloads/video.mp4, we store segments
     * in a directory named something like /downloads/video.mp4_segments_<downloadId>.
     */
    private fun createSegmentCacheDir(
        outputPath: Path,
        downloadId: DownloadId
    ): Path {
        // A simple naming approach:
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
        val response = httpGet(url, options).bodyAsText()
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

    /**
     * Downloads segments, respecting concurrency.
     */
    private suspend fun downloadSegments(downloadId: DownloadId, options: DownloadOptions) {
        val stateSnapshot = getState(downloadId) ?: return
        if (stateSnapshot.segments.isEmpty()) return

        val semaphore = Semaphore(options.maxConcurrentSegments)

        coroutineScope {
            stateSnapshot.segments.forEach { segmentInfo ->
                // Skip if already downloaded
                if (segmentInfo.isDownloaded) return@forEach

                launch(ioDispatcher) {
                    semaphore.acquire()
                    try {
                        val newSize = downloadSingleSegment(downloadId, segmentInfo, options)
                        markSegmentDownloaded(downloadId, segmentInfo.index, newSize)
                    } finally {
                        semaphore.release()
                    }
                }
            }
        }
        // Wait for all segments to finish
    }

    /**
     * Download a single segment file to [segmentInfo.tempFilePath].
     *
     * @return the number of bytes actually downloaded
     */
    @OptIn(ExperimentalAtomicApi::class)
    private suspend fun downloadSingleSegment(
        downloadId: DownloadId,
        segmentInfo: SegmentInfo,
        options: DownloadOptions
    ): Long {
        val response = httpGet(segmentInfo.url, options)
        val channel = response.bodyAsChannel()

        val segmentPath = Path(requireNotNull(segmentInfo.tempFilePath))
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
     * Merges all downloaded segments into the final [DownloadState.outputPath].
     * Then deletes the segment cache directory and all .ts files.
     */
    private suspend fun mergeSegments(downloadId: DownloadId) {
        val state = getState(downloadId) ?: return
        val finalOutput = Path(state.outputPath)

        // The path to the cache directory where segments are stored
        val cacheDir = state.segmentCacheDir?.let { Path(it) } ?: return

        // Merge all segments in ascending index order
        fileSystem.sink(finalOutput).buffered().use { out ->
            state.segments.sortedBy { it.index }.forEach { seg ->
                seg.tempFilePath?.let { tsFilePath ->
                    fileSystem.source(Path(tsFilePath)).buffered().use { input ->
                        input.copyTo(out)
                    }
                }
            }
        }

        // Delete each segment file
        state.segments.forEach { seg ->
            seg.tempFilePath?.let { fileSystem.delete(Path(it)) }
        }

        // Finally remove the cache directory itself
        fileSystem.delete(cacheDir)
    }

    /**
     * Reads the latest [DownloadState] and emits a new [DownloadProgress].
     */
    private suspend fun emitProgress(downloadId: DownloadId) {
        val currentState = getState(downloadId) ?: return
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
     * Helper that updates the internal [DownloadState] in [downloads] inside a [stateMutex] lock
     * and returns the new state.
     */
    private suspend fun updateState(downloadId: DownloadId, transform: (DownloadState) -> DownloadState) {
        stateMutex.withLock {
            val entry = downloads[downloadId] ?: return
            val old = entry.state
            val new = transform(old)
            entry.state = new
        }
    }

    /**
     * Simple HTTP GET wrapper to attach custom headers from [DownloadOptions].
     */
    private suspend fun httpGet(url: String, options: DownloadOptions): HttpResponse {
        return client.prepareGet(url) {
            options.headers.forEach { (key, value) ->
                header(key, value)
            }
        }.body()
    }

    /**
     * Generates a simple unique ID based on the URL and current time.
     */
    private fun generateDownloadId(url: String): String {
        return Uuid.randomString()
    }

    /**
     * Wait for a particular download job to finish (either complete or fail).
     */
    suspend fun joinDownload(downloadId: DownloadId) {
        val job = stateMutex.withLock {
            downloads[downloadId]?.job
        }
        job?.join()
    }
}

/**
 * Private internal exception for M3u8 parsing issues.
 */
private class M3u8Exception(val errorCode: DownloadErrorCode) : Exception()

/**
 * Extension function to convert a [M3u8Playlist.MediaPlaylist] into a list of [SegmentInfo],
 * pointing each segment’s `tempFilePath` into the given [cacheDir].
 */
private fun M3u8Playlist.MediaPlaylist.toSegments(cacheDir: Path): List<SegmentInfo> {
    return segments.mapIndexed { i, segment ->
        // The mediaSequence offset is often used for live streams but also relevant
        // for static playlists if the .m3u8 isn't zero-based.
        val segmentIndex = mediaSequence + i
        SegmentInfo(
            index = segmentIndex,
            url = segment.uri,
            isDownloaded = false,
            byteSize = -1,
            tempFilePath = cacheDir.resolve("$segmentIndex.ts").toString(),
        )
    }
}
