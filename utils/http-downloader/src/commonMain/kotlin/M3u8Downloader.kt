/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.utils.httpdownloader

import kotlinx.coroutines.flow.Flow
import kotlinx.io.files.Path
import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

/**
 * Interface for downloading multiple HLS (m3u8) streams concurrently.
 *
 * This interface handles:
 * - Management of multiple concurrent m3u8 downloads
 * - Progress reporting via Flow for all downloads
 * - Pause/resume/cancel functionality for individual downloads
 * - (Now removed manual save/load functions in favor of automatic persistence.)
 */
interface M3u8Downloader : AutoCloseable {
    /**
     * Flow of progress updates for all downloads.
     */
    val progressFlow: Flow<DownloadProgress>

    /**
     * Gets a flow of progress updates for a specific download.
     */
    fun getProgressFlow(downloadId: DownloadId): Flow<DownloadProgress>

    /**
     * Flow that emits the entire list of known download states.
     * These states remain until removed internally (e.g. upon close).
     */
    val downloadStatesFlow: Flow<List<DownloadState>>

    /**
     * Initialize this downloader. Should be called before starting downloads.
     *
     * This may load any persisted download states, but does NOT resume downloads.
     * You may need to call [resume] for each download [getActiveDownloadIds] to resume them.
     */
    suspend fun init()

    /**
     * Starts a new download and returns its unique ID.
     */
    suspend fun download(
        url: String,
        outputPath: Path,
        options: DownloadOptions = DownloadOptions()
    ): DownloadId

    /**
     * Starts a new download with a specific ID.
     */
    suspend fun downloadWithId(
        downloadId: DownloadId,
        url: String,
        outputPath: Path,
        options: DownloadOptions = DownloadOptions()
    )

    /**
     * Resumes a previously paused or failed download by ID.
     */
    suspend fun resume(downloadId: DownloadId): Boolean

    /**
     * Gets all currently active download IDs.
     */
    suspend fun getActiveDownloadIds(): List<DownloadId>

    /**
     * Pauses a specific download.
     */
    suspend fun pause(downloadId: DownloadId): Boolean

    /**
     * Pauses all active downloads.
     */
    suspend fun pauseAll(): List<DownloadId>

    /**
     * Cancels a specific download.
     */
    suspend fun cancel(downloadId: DownloadId): Boolean

    /**
     * Cancels all active downloads.
     */
    suspend fun cancelAll()

    /**
     * Gets the current state of a download by ID.
     */
    suspend fun getState(downloadId: DownloadId): DownloadState?

    /**
     * Gets states of all known downloads.
     */
    suspend fun getAllStates(): List<DownloadState>

    /**
     * Closes and releases all resources used by this downloader.
     */
    override fun close()
}

@JvmInline
@Serializable
value class DownloadId(val value: String) {
    override fun toString(): String = value
}

@Serializable
enum class DownloadErrorCode {
    NO_MEDIA_LIST,
    UNEXPECTED_ERROR,
}

@Serializable
data class DownloadError(
    val code: DownloadErrorCode,
    val params: Map<String, String> = emptyMap(),
    val technicalMessage: String? = null
)

@Serializable
data class DownloadProgress(
    val downloadId: DownloadId,
    val url: String,
    val totalSegments: Int,
    val downloadedSegments: Int,
    val downloadedBytes: Long,
    val totalBytes: Long, // -1 for unknown
    val status: DownloadStatus,
    val error: DownloadError? = null
)

@Serializable
enum class DownloadStatus {
    INITIALIZING,
    DOWNLOADING,
    MERGING,
    PAUSED,
    COMPLETED,
    FAILED,
    CANCELED
}

@Serializable // saved in data store
data class DownloadState(
    val downloadId: DownloadId,
    val url: String,
    val outputPath: String,
    val segments: List<SegmentInfo>,
    val totalSegments: Int,
    val downloadedBytes: Long,
    val timestamp: Long,
    val status: DownloadStatus,
    val error: DownloadError? = null,
    val segmentCacheDir: String,
)

@Serializable
data class SegmentInfo(
    val index: Int,
    val url: String,
    val isDownloaded: Boolean,
    val byteSize: Long = -1,
    val tempFilePath: String,
)

@Serializable
data class DownloadOptions(
    val maxConcurrentSegments: Int = 3,
    val segmentRetryCount: Int = 3,
    val connectTimeoutMs: Long = 30_000,
    val readTimeoutMs: Long = 30_000,
    val autoSaveIntervalMs: Long = 5_000,
    val headers: Map<String, String> = emptyMap()
)
