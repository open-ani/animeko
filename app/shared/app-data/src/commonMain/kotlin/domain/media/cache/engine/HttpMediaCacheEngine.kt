/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.media.cache.engine

import androidx.compose.runtime.Composable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import me.him188.ani.app.data.persistent.DataStoreJson
import me.him188.ani.app.domain.media.cache.MediaCache
import me.him188.ani.app.domain.media.cache.MediaCacheState
import me.him188.ani.app.domain.media.cache.engine.HttpMediaCacheEngine.Companion.EXTRA_URI
import me.him188.ani.app.domain.media.resolver.EpisodeMetadata
import me.him188.ani.app.domain.media.resolver.MediaResolver
import me.him188.ani.app.tools.Progress
import me.him188.ani.app.tools.toProgress
import me.him188.ani.app.torrent.api.files.averageRate
import me.him188.ani.datasources.api.CachedMedia
import me.him188.ani.datasources.api.DefaultMedia
import me.him188.ani.datasources.api.Media
import me.him188.ani.datasources.api.MediaCacheMetadata
import me.him188.ani.datasources.api.MediaCacheProperties
import me.him188.ani.datasources.api.MetadataKey
import me.him188.ani.datasources.api.topic.FileSize
import me.him188.ani.datasources.api.topic.FileSize.Companion.bytes
import me.him188.ani.datasources.api.topic.ResourceLocation
import me.him188.ani.utils.coroutines.IO_
import me.him188.ani.utils.httpdownloader.DownloadId
import me.him188.ani.utils.httpdownloader.DownloadOptions
import me.him188.ani.utils.httpdownloader.DownloadStatus
import me.him188.ani.utils.httpdownloader.HttpDownloader
import me.him188.ani.utils.httpdownloader.MediaType
import me.him188.ani.utils.io.absolutePath
import me.him188.ani.utils.io.actualSize
import me.him188.ani.utils.io.deleteRecursively
import me.him188.ani.utils.io.inSystem
import me.him188.ani.utils.io.resolve
import me.him188.ani.utils.logging.info
import me.him188.ani.utils.logging.logger
import me.him188.ani.utils.logging.warn
import me.him188.ani.utils.platform.Uuid
import org.openani.mediamp.source.SeekableInputMediaData
import org.openani.mediamp.source.UriMediaData
import kotlin.coroutines.CoroutineContext

class HttpMediaCacheEngine(
    private val downloader: HttpDownloader,
    override val engineKey: MediaCacheEngineKey,
    private val dataDir: Path,
    private val mediaResolver: MediaResolver,
    private val mediaSourceId: String,
) : MediaCacheEngine {
    private val json get() = DataStoreJson

    override val stats: Flow<MediaStats> = run {
        val downloadSpeedFlow =
            downloader.downloadStatesFlow
                .map { list ->
                    list.sumOf { it.downloadedBytes }
                }
                .averageRate()

        combine(downloader.downloadStatesFlow, downloadSpeedFlow) { list, speed ->
            MediaStats(
                uploaded = FileSize.Zero,
                downloaded = list.sumOf { it.downloadedBytes }.bytes,
                uploadSpeed = FileSize.Zero,
                downloadSpeed = speed.bytes,
            )
        }
    }

    override fun supports(media: Media): Boolean {
        // Check that the media is not already cached
        when (media) {
            is CachedMedia -> return false
            is DefaultMedia -> {} // for smart cast
        }

        return when (media.download) {
            is ResourceLocation.HttpStreamingFile -> false
            is ResourceLocation.HttpTorrentFile,
            is ResourceLocation.LocalFile,
            is ResourceLocation.MagnetLink,
                -> {
                false
            }

            is ResourceLocation.WebVideo -> mediaResolver.supports(media)
        }
    }


    @Composable
    override fun ComposeContent(): Unit = mediaResolver.ComposeContent()

    override suspend fun restore(
        origin: Media,
        metadata: MediaCacheMetadata,
        parentContext: CoroutineContext,
    ): MediaCache? {
        if (!supports(origin)) throw UnsupportedOperationException("Media is not supported by this engine $this: ${origin.download}")

        logger.info { "Restarting cache '${origin.mediaId}'" }

        val downloadId = metadata.extra[EXTRA_DOWNLOAD_ID]?.let { DownloadId(it) } ?: return null
        val uri = metadata.extra[EXTRA_URI] ?: return null
        val outputPath = metadata.extra[EXTRA_OUTPUT_PATH] ?: return null
        val headers = json.decodeFromString<Map<String, String>>(metadata.extra[EXTRA_HEADERS] ?: return null)

        // 注意, getState 一般不会返回 null, 除非 downloader 的 persistent datastore 出问题了 (例如文件损坏).
        if (downloader.getState(downloadId) != null) {
            downloader.resume(downloadId) // ignore result.
            // Task already exists
            logger.info { "Resumed download $downloadId" }
            return HttpMediaCache(mediaSourceId, downloader, downloadId, origin, metadata)
        }

        logger.info { "Download not found, recreating $downloadId" }
        downloader.downloadWithId(
            downloadId = downloadId,
            uri,
            outputPath = Path(outputPath),
            options = DownloadOptions(headers = headers),
        )
        return HttpMediaCache(mediaSourceId, downloader, downloadId, origin, metadata)
    }

    override suspend fun createCache(
        origin: Media,
        metadata: MediaCacheMetadata,
        episodeMetadata: EpisodeMetadata,
        parentContext: CoroutineContext,
    ): MediaCache {
        if (!supports(origin)) throw UnsupportedOperationException("Media is not supported by this engine $this: ${origin.download}")

        val mediaDataProvider = mediaResolver.resolve(origin, episodeMetadata)
        when (val mediaData = mediaDataProvider.open(CoroutineScope(parentContext))) {
            is SeekableInputMediaData -> {
                // This should not happen.
                throw UnsupportedOperationException("SeekableInputMediaData is not supported")
            }

            is UriMediaData -> {
                val downloadId = DownloadId(Uuid.randomString())
                val outputPath = dataDir.resolve(downloadId.value)
                downloader.downloadWithId(
                    downloadId = downloadId,
                    mediaData.uri,
                    outputPath = outputPath,
                    options = DownloadOptions(headers = mediaData.headers),
                )
                return HttpMediaCache(
                    mediaSourceId,
                    downloader,
                    downloadId,
                    origin,
                    metadata.copy(
                        extra = metadata.extra.toMutableMap().apply {
                            put(EXTRA_DOWNLOAD_ID, downloadId.value)
                            put(EXTRA_URI, mediaData.uri)
                            put(EXTRA_OUTPUT_PATH, outputPath.inSystem.absolutePath)
                            put(EXTRA_HEADERS, json.encodeToString(mediaData.headers))
                        },
                    ),
                )
            }
        }
    }

    override suspend fun deleteUnusedCaches(all: List<MediaCache>) {
        if (!(SystemFileSystem.exists(dataDir))) return


        val allowedAbsolute = buildSet {
            for (mediaCache in all.filterIsInstance<HttpMediaCache>()) {
                mediaCache.metadata.extra[EXTRA_OUTPUT_PATH]?.let {
                    add(it) // 上次记录的位置
                }
                downloader.getState(mediaCache.downloadId)?.let {
                    add(it.segmentCacheDir)
                }
            }
        }
        withContext(Dispatchers.IO) {
            val saves = SystemFileSystem.list(dataDir)
            for (save in saves) {
                val myPath = save.inSystem.absolutePath
                if (allowedAbsolute.none {
                        myPath.startsWith(it)
                    }) {
                    logger.warn { "本地 WEB 缓存文件未找到匹配的 MediaCache, 已释放 ${save.inSystem.actualSize().bytes}: ${save.inSystem.absolutePath}" }
                    SystemFileSystem.deleteRecursively(save)
                }
            }
        }

    }

    internal companion object {
        val EXTRA_DOWNLOAD_ID = MetadataKey("downloadId")
        val EXTRA_URI = MetadataKey("uri")
        val EXTRA_OUTPUT_PATH = MetadataKey("outputPath")
        val EXTRA_HEADERS = MetadataKey("headers")

        private val logger = logger<HttpMediaCacheEngine>()
    }
}

class HttpMediaCache(
    private val mediaSourceId: String,
    private val downloader: HttpDownloader,
    internal val downloadId: DownloadId,
    override val origin: Media,
    override val metadata: MediaCacheMetadata,
) : MediaCache {
    override val state: Flow<MediaCacheState> = downloader.getProgressFlow(downloadId).map {
        when (it.status) {
            DownloadStatus.DOWNLOADING,
            DownloadStatus.MERGING,
            DownloadStatus.COMPLETED,
                -> MediaCacheState.IN_PROGRESS

            DownloadStatus.INITIALIZING,
            DownloadStatus.PAUSED,
                -> MediaCacheState.PAUSED

            DownloadStatus.FAILED,
            DownloadStatus.CANCELED,
                -> MediaCacheState.PAUSED
        }
    }

    override val canPlay: Flow<Boolean>
        get() = downloader.getProgressFlow(downloadId).map {
            it.status == DownloadStatus.COMPLETED
        }

    override val fileStats: Flow<MediaCache.FileStats> = downloader.getProgressFlow(downloadId).map {
        val totalSize = it.totalBytes
        val downloadedBytes = it.downloadedBytes
        MediaCache.FileStats(
            totalSize = totalSize.bytes,
            downloadedBytes = downloadedBytes.bytes,
            downloadProgress = if (
                it.status == DownloadStatus.COMPLETED
            ) {
                1f.toProgress()
            } else {
                Progress.Unspecified
            }, // m3u8 has no total size.
        )
    }
    override val sessionStats: Flow<MediaCache.SessionStats> = run {
        val downloadSpeedFlow = fileStats.map { it.downloadedBytes.inBytes }.averageRate()

        combine(downloadSpeedFlow, fileStats) { speed, stats ->
            MediaCache.SessionStats(
                totalSize = stats.totalSize,
                downloadedBytes = stats.downloadedBytes,
                downloadSpeed = speed.bytes,
                uploadedBytes = FileSize.Zero,
                uploadSpeed = FileSize.Zero,
                downloadProgress = stats.downloadProgress,
            )
        }
    }
    override val isDeleted: MutableStateFlow<Boolean> = MutableStateFlow(false)
    private val closeMutex = Mutex()

    override suspend fun getCachedMedia(): CachedMedia {
        val state = downloader.getState(downloadId)
            ?: throw IllegalStateException("Download state not found for $downloadId")

        return when (state.status) {
            DownloadStatus.INITIALIZING,
            DownloadStatus.DOWNLOADING,
            DownloadStatus.MERGING,
            DownloadStatus.PAUSED,
                -> {
                error("Download not completed, cannot get cached media for $downloadId")
            }

            DownloadStatus.COMPLETED -> {
                val actualFileSize = withContext(Dispatchers.IO_) {
                    try {
                        Path(state.outputPath).inSystem.actualSize().bytes
                    } catch (_: Exception) {
                        FileSize.Unspecified
                    }
                }
                CachedMedia(
                    origin,
                    cacheMediaSourceId = mediaSourceId,
                    download = ResourceLocation.LocalFile(
                        state.outputPath,
                        state.mediaType.toFileType(),
                        originalUri = metadata.extra[EXTRA_URI],
                    ),
                    properties = origin.properties.copy(
                        size = if (actualFileSize.isUnspecified) {
                            origin.properties.size
                        } else {
                            actualFileSize
                        },
                    ),
                    cacheProperties = MediaCacheProperties(
                        totalSegments = state.totalSegments,
                        httpDownloaderStatus = state.status.toString(),
                    ),
                )
            }

            DownloadStatus.FAILED,
            DownloadStatus.CANCELED,
                -> {
                error("Download failed or canceled")
            }
        }
    }

    override suspend fun pause() {
        downloader.pause(downloadId)
    }

    override suspend fun close() {
        if (isDeleted.value) return
        closeMutex.withLock {
            if (isDeleted.value) return
            downloader.cancel(downloadId)
        }
    }

    override suspend fun resume() {
        downloader.resume(downloadId)
    }

    override suspend fun closeAndDeleteFiles() {
        if (isDeleted.value) return
        closeMutex.withLock {
            if (isDeleted.value) return
            downloader.cancel(downloadId)
            isDeleted.value = true
        }
    }
}

private fun MediaType.toFileType(): ResourceLocation.LocalFile.FileType? {
    return when (this) {
        MediaType.M3U8 -> ResourceLocation.LocalFile.FileType.MPTS
        MediaType.MP4,
        MediaType.MKV,
            -> ResourceLocation.LocalFile.FileType.CONTAINED
    }
}
