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
 * - Persistent state for resuming downloads across app restarts
 *
 * Note: This interface manages HTTP clients and other resources
 * that need to be closed when no longer needed.
 */
interface M3u8Downloader : AutoCloseable {
    /**
     * Flow of progress updates for all active downloads.
     * Listen to this flow to get real-time progress information for all downloads.
     */
    val progressFlow: Flow<DownloadProgress>

    /**
     * Gets a flow of progress updates for a specific download.
     *
     * @param downloadId ID of the download to monitor
     * @return Flow emitting progress updates for the specified download
     */
    fun getProgressFlow(downloadId: DownloadId): Flow<DownloadProgress>

    /**
     * Starts a new download and returns its unique ID.
     *
     * @param url The m3u8 URL to download
     * @param outputPath Path where the final file will be saved
     * @param options Configuration options
     * @return A unique identifier for this download
     */
    suspend fun download(
        url: String,
        outputPath: Path,
        options: DownloadOptions = DownloadOptions()
    ): DownloadId

    /**
     * Starts a new download with a specific ID.
     *
     * @param downloadId Custom identifier for this download
     * @param url The m3u8 URL to download
     * @param outputPath Path where the final file will be saved
     * @param options Configuration options
     */
    suspend fun downloadWithId(
        downloadId: DownloadId,
        url: String,
        outputPath: Path,
        options: DownloadOptions = DownloadOptions()
    )

    /**
     * Resumes a previously paused or failed download using its ID.
     * The implementation will retrieve the stored state internally.
     *
     * @param downloadId ID of the download to resume
     * @return True if the download was successfully resumed
     */
    suspend fun resume(downloadId: DownloadId): Boolean

    /**
     * Gets all currently active download IDs.
     *
     * @return List of active download identifiers
     */
    suspend fun getActiveDownloadIds(): List<DownloadId>

    /**
     * Pauses a specific download.
     *
     * @param downloadId ID of the download to pause
     * @return True if the download was successfully paused
     */
    suspend fun pause(downloadId: DownloadId): Boolean

    /**
     * Pauses all active downloads.
     *
     * @return List of IDs of all paused downloads
     */
    suspend fun pauseAll(): List<DownloadId>

    /**
     * Cancels a specific download.
     *
     * @param downloadId ID of the download to cancel
     * @return True if the download was successfully canceled
     */
    suspend fun cancel(downloadId: DownloadId): Boolean

    /**
     * Cancels all active downloads.
     */
    suspend fun cancelAll()

    /**
     * Gets the current state of a specific download.
     *
     * @param downloadId ID of the download
     * @return Current state of the download, or null if not found
     */
    suspend fun getState(downloadId: DownloadId): DownloadState?

    /**
     * Gets states of all active downloads.
     *
     * @return List of states for all active downloads
     */
    suspend fun getAllStates(): List<DownloadState>

    /**
     * Saves state of a specific download to persistent storage.
     *
     * @param downloadId ID of the download to save
     * @return True if save was successful
     */
    suspend fun saveState(downloadId: DownloadId): Boolean

    /**
     * Saves states of all active downloads to persistent storage.
     *
     * @return True if all saves were successful
     */
    suspend fun saveAllStates(): Boolean

    /**
     * Loads all previously saved states from persistent storage.
     *
     * @return List of IDs of all successfully loaded download states
     */
    suspend fun loadSavedStates(): List<DownloadId>

    /**
     * Checks if a saved state exists for the given download ID.
     *
     * @param downloadId ID of the download to check
     * @return True if a saved state exists
     */
    suspend fun hasSavedState(downloadId: DownloadId): Boolean

    /**
     * Closes and releases all resources used by this downloader,
     * including HTTP clients and any other connection pools.
     * All active downloads will be paused before closing.
     */
    override fun close()
}

/**
 * Type-safe wrapper for download identifiers.
 *
 * @property value The underlying string ID value
 */
@JvmInline
@Serializable
value class DownloadId(val value: String) {
    override fun toString(): String = value
}

/**
 * Enumeration of error types for m3u8 downloads.
 * This allows for i18n-friendly error reporting.
 */
@Serializable
enum class DownloadErrorCode {
    NO_MEDIA_LIST,
    UNEXPECTED_ERROR,
}

/**
 * Structured error information to support internationalization.
 *
 * @property code The error code for mapping to localized messages
 * @property params Additional parameters for message interpolation (e.g., "retryCount": "3")
 * @property technicalMessage Optional technical details (for logging, not user-facing)
 */
@Serializable
data class DownloadError(
    val code: DownloadErrorCode,
    val params: Map<String, String> = emptyMap(),
    val technicalMessage: String? = null
)

/**
 * Data class for reporting download progress and speed.
 *
 * @property downloadId Unique identifier for the download
 * @property url The source URL being downloaded
 * @property totalSegments Total number of segments in the m3u8 playlist
 * @property downloadedSegments Number of segments that have been downloaded
 * @property downloadedBytes Number of bytes downloaded so far
 * @property totalBytes Total size in bytes (-1 indicates unknown size)
 * @property speedBytesPerSecond Current download speed in bytes per second
 * @property estimatedTimeRemainingSeconds Estimated time to completion (-1 if unknown)
 * @property status Current status of the download
 * @property error Error information if status is FAILED
 */
@Serializable
data class DownloadProgress(
    val downloadId: DownloadId,
    val url: String,
    val totalSegments: Int,
    val downloadedSegments: Int,
    val downloadedBytes: Long,
    val totalBytes: Long, // -1 for unknown size
    val speedBytesPerSecond: Long,
    val estimatedTimeRemainingSeconds: Long, // -1 if unknown
    val status: DownloadStatus,
    val error: DownloadError? = null
)

/**
 * Possible download states for an m3u8 download.
 */
@Serializable
enum class DownloadStatus {
    /** Parsing m3u8 and preparing for download */
    INITIALIZING,

    /** Actively downloading segments */
    DOWNLOADING,

    /** Merging all downloaded segments into the final file */
    MERGING,

    /** Paused by user */
    PAUSED,

    /** Successfully completed */
    COMPLETED,

    /** Failed with error */
    FAILED,

    /** Canceled by user */
    CANCELED
}

/**
 * Serializable state for saving/resuming downloads.
 * This is used internally and for persistence.
 *
 * @property downloadId Unique identifier for the download
 * @property url The source URL
 * @property outputPath Path where the final file will be saved
 * @property segments List of all segments in the m3u8 playlist
 * @property totalSegments Total number of segments
 * @property downloadedBytes Total bytes downloaded so far
 * @property timestamp When the state was saved
 * @property status Current download status
 * @property error Error information if status is FAILED
 */
@Serializable
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

    // Add this to remember where segment files are stored:
    val segmentCacheDir: String? = null,
)

/**
 * Information about each segment in the m3u8 playlist.
 *
 * @property index Position in the playlist
 * @property url URL of the segment
 * @property isDownloaded Whether segment has been downloaded
 * @property byteSize Size of segment in bytes (-1 for unknown size)
 * @property tempFilePath Temporary location of downloaded segment
 */
@Serializable
data class SegmentInfo(
    val index: Int,
    val url: String,
    val isDownloaded: Boolean,
    val byteSize: Long = -1, // -1 for unknown size
    val tempFilePath: String,
)

/**
 * Configuration options for m3u8 downloads.
 *
 * @property maxConcurrentSegments Maximum number of segments to download in parallel
 * @property segmentRetryCount How many times to retry failed segment downloads
 * @property connectTimeoutMs Connection timeout in milliseconds
 * @property readTimeoutMs Read timeout in milliseconds
 * @property autoSaveIntervalMs How often to auto-save download state
 * @property headers Custom HTTP headers to use for requests
 */
@Serializable
data class DownloadOptions(
    val maxConcurrentSegments: Int = 3,
    val segmentRetryCount: Int = 3,
    val connectTimeoutMs: Long = 30_000,
    val readTimeoutMs: Long = 30_000,
    val autoSaveIntervalMs: Long = 5_000,
    val headers: Map<String, String> = emptyMap()
)


/**
 * Custom exceptions for m3u8 download errors
 */
open class M3u8DownloaderException(message: String, cause: Throwable? = null) : Exception(message, cause)
class M3u8StateException(message: String, cause: Throwable? = null) : M3u8DownloaderException(message, cause)
class M3u8IOException(message: String, cause: Throwable? = null) : M3u8DownloaderException(message, cause)
class M3u8NetworkException(message: String, cause: Throwable? = null) : M3u8DownloaderException(message, cause)
