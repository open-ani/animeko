/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.media.cache.engine

import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext
import kotlinx.io.IOException
import kotlinx.io.files.FileNotFoundException
import kotlinx.io.files.Path
import me.him188.ani.app.domain.media.cache.MediaCache
import me.him188.ani.app.domain.media.cache.MediaCacheState
import me.him188.ani.app.domain.media.cache.engine.TorrentMediaCacheEngine.Companion.EXTRA_TORRENT_CACHE_DIR
import me.him188.ani.app.domain.media.cache.engine.TorrentMediaCacheEngine.Companion.EXTRA_TORRENT_DATA
import me.him188.ani.app.domain.media.resolver.EpisodeMetadata
import me.him188.ani.app.domain.media.resolver.TorrentMediaResolver
import me.him188.ani.app.domain.torrent.TorrentEngine
import me.him188.ani.app.tools.toProgress
import me.him188.ani.app.torrent.api.TorrentSession
import me.him188.ani.app.torrent.api.files.EncodedTorrentInfo
import me.him188.ani.app.torrent.api.files.FilePriority
import me.him188.ani.app.torrent.api.files.TorrentFileEntry
import me.him188.ani.app.torrent.api.files.TorrentFileHandle
import me.him188.ani.app.torrent.api.files.isFinished
import me.him188.ani.datasources.api.CachedMedia
import me.him188.ani.datasources.api.Media
import me.him188.ani.datasources.api.MediaCacheMetadata
import me.him188.ani.datasources.api.MetadataKey
import me.him188.ani.datasources.api.topic.FileSize
import me.him188.ani.datasources.api.topic.FileSize.Companion.bytes
import me.him188.ani.datasources.api.topic.ResourceLocation
import me.him188.ani.utils.io.absolutePath
import me.him188.ani.utils.io.actualSize
import me.him188.ani.utils.io.delete
import me.him188.ani.utils.io.deleteRecursively
import me.him188.ani.utils.io.exists
import me.him188.ani.utils.io.inSystem
import me.him188.ani.utils.io.resolve
import me.him188.ani.utils.logging.error
import me.him188.ani.utils.logging.info
import me.him188.ani.utils.logging.logger
import me.him188.ani.utils.logging.warn
import kotlin.coroutines.CoroutineContext


//private const val EXTRA_TORRENT_CACHE_FILE =
//    "torrentCacheFile" // MediaCache 所对应的视频文件. 该文件一定是 [EXTRA_TORRENT_CACHE_DIR] 目录中的文件 (的其中一个)

/**
 * 以 [TorrentEngine] 实现的 [MediaCacheEngine], 意味着通过 BT 缓存 media.
 * 为每个 [MediaCache] 创建一个 [TorrentSession].
 */
class TorrentMediaCacheEngine(
    /**
     * 创建的 [CachedMedia] 将会使用此 [mediaSourceId]
     */
    private val mediaSourceId: String,
    override val engineKey: MediaCacheEngineKey,
    val torrentEngine: TorrentEngine,
    val flowDispatcher: CoroutineContext = Dispatchers.Default,
    private val onDownloadStarted: suspend (session: TorrentSession) -> Unit = {},
) : MediaCacheEngine, AutoCloseable {
    companion object {
        private val EXTRA_TORRENT_DATA = MetadataKey("torrentData")
        val EXTRA_TORRENT_CACHE_DIR = MetadataKey("torrentCacheDir") // 种子的缓存目录, 注意, 一个 MediaCache 可能只对应该种子资源的其中一个文件

        private val logger = logger<TorrentMediaCacheEngine>()
        private val unspecifiedFileStatsFlow = flowOf(MediaCache.FileStats.Unspecified)
        private val unspecifiedSessionStatsFlow = flowOf(MediaCache.SessionStats.Unspecified)
        private val unspecifiedFileSizeFlow = flowOf(FileSize.Unspecified)
    }

    /**
     * 仅当 [MediaCache.getCachedMedia] 等操作时才会创建 [TorrentSession]
     */
    class LazyFileHandle(
        val scope: CoroutineScope,
        val state: SharedFlow<State?>, // suspend lazy
    ) {
        val handle = state.map { it?.handle } // single emit
        val entry = state.map { it?.entry } // single emit
        val session = state.map { it?.session }

        suspend fun close() {
            handle.first()?.close()
            scope.coroutineContext.job.cancelAndJoin()
        }

        class State(
            val session: TorrentSession,
            val entry: TorrentFileEntry?,
            val handle: TorrentFileHandle?,
        )
    }

    inner class TorrentMediaCache(
        override val origin: Media,
        /**
         * Required:
         * @see EXTRA_TORRENT_CACHE_DIR
         * @see EXTRA_TORRENT_DATA
         */
        override val metadata: MediaCacheMetadata, // 注意, 我们不能写 check 检查这些属性, 因为可能会有旧版本的数据
        val lazyFileHandle: LazyFileHandle
    ) : MediaCache, SynchronizedObject() {
        override val state: MutableStateFlow<MediaCacheState> = MutableStateFlow(
            MediaCacheState.IN_PROGRESS,
        )

        override suspend fun getCachedMedia(): CachedMedia {
            logger.info { "getCachedMedia: start" }
            val file = lazyFileHandle.handle.first()
            if (file != null && file.entry.isFinished()) {
                val filePath = file.entry.resolveFile()
                if (!filePath.exists()) {
                    error("TorrentFileHandle has finished but file does not exist: $filePath")
                }
                logger.info { "getCachedMedia: Torrent has already finished, returning file $filePath" }
                return CachedMedia(
                    origin,
                    mediaSourceId,
                    download = ResourceLocation.LocalFile(filePath.toString()),
                )
            } else {
                logger.info { "getCachedMedia: Torrent has not yet finished, returning torrent" }
                return CachedMedia(
                    origin,
                    mediaSourceId,
                    download = origin.download,
                )
            }
        }

        override val fileStats: Flow<MediaCache.FileStats> = lazyFileHandle.entry.flatMapLatest { entry ->
            if (entry == null) return@flatMapLatest unspecifiedFileStatsFlow

            entry.fileStats.map { stats ->
                MediaCache.FileStats(
                    totalSize = entry.length.bytes,
                    downloadedBytes = stats.downloadedBytes.bytes,
                    downloadProgress = stats.downloadProgress.toProgress(),
                )
            }
        }.flowOn(flowDispatcher)

        override val sessionStats: Flow<MediaCache.SessionStats> = lazyFileHandle.session.flatMapLatest { handle ->
            if (handle == null) return@flatMapLatest unspecifiedSessionStatsFlow
            handle.sessionStats
                .map { stats ->
                    if (stats == null) return@map MediaCache.SessionStats.Unspecified
                    MediaCache.SessionStats(
                        totalSize = stats.totalSizeRequested.bytes,
                        downloadedBytes = stats.downloadedBytes.bytes,
                        downloadSpeed = stats.downloadSpeed.bytes,
                        uploadedBytes = stats.uploadedBytes.bytes,
                        uploadSpeed = stats.uploadSpeed.bytes,
                        downloadProgress = stats.downloadProgress.toProgress(),
                    )
                }
        }.flowOn(flowDispatcher)

        override suspend fun pause() {
            if (isDeleted.value) return
            lazyFileHandle.handle.first()?.pause()
            state.value = MediaCacheState.PAUSED
        }

        override suspend fun close() {
            if (isDeleted.value) return
            lazyFileHandle.close()
        }

        override suspend fun resume() {
            if (isDeleted.value) return
            val file = lazyFileHandle.handle.first()
            state.value = MediaCacheState.IN_PROGRESS
            logger.info { "Resuming file: $file" }
            file?.resume(FilePriority.NORMAL)
        }

        override val isDeleted: MutableStateFlow<Boolean> = MutableStateFlow(false)

        override suspend fun closeAndDeleteFiles() {
            if (isDeleted.value) return
            synchronized(this) {
                if (isDeleted.value) return
                isDeleted.value = true
            }
            val handle = lazyFileHandle.handle.first() ?: kotlin.run {
                // did not even selected a file
                logger.info { "Deleting torrent cache: No file selected" }
                close()
                return
            }

            close()
            handle.closeAndDelete()

            withContext(Dispatchers.IO) {
                val file = handle.entry.resolveFileMaybeEmptyOrNull() ?: return@withContext
                if (file.exists()) {
                    logger.info { "Deleting torrent cache: $file" }
                    try {
                        file.delete()
                    } catch (_: FileNotFoundException) {
                    } catch (e: IOException) {
                        logger.warn("Failed to delete cache file $file", e)
                    }
                } else {
                    logger.info { "Torrent cache does not exist, ignoring: $file" }
                }
            }
        }

        override fun toString(): String {
            return "TorrentMediaCache(subjectName='${metadata.subjectNames.firstOrNull()}', " +
                    "episodeSort=${metadata.episodeSort}, " +
                    "episodeName='${metadata.episodeName}', " +
                    "origin.mediaSourceId='${origin.mediaSourceId}')"
        }
    }

    override val stats: Flow<MediaStats> = flow { emit(torrentEngine.getDownloader()) }
        .flatMapLatest {
            it.totalStats
        }.map {
            MediaStats(
                uploaded = it.uploadedBytes.bytes,
                downloaded = it.downloadedBytes.bytes,
                uploadSpeed = it.uploadSpeed.bytes,
                downloadSpeed = it.downloadSpeed.bytes,
            )
        }
        .flowOn(flowDispatcher)

    override fun supports(media: Media): Boolean {
        return media.download is ResourceLocation.HttpTorrentFile
                || media.download is ResourceLocation.MagnetLink
    }

    @OptIn(ExperimentalStdlibApi::class)
    override suspend fun restore(
        origin: Media,
        metadata: MediaCacheMetadata,
        parentContext: CoroutineContext
    ): MediaCache? {
        if (!supports(origin)) throw UnsupportedOperationException("Media is not supported by this engine $this: ${origin.download}")
        val data = metadata.extra[EXTRA_TORRENT_DATA]?.hexToByteArray() ?: return null
        return TorrentMediaCache(
            origin = origin,
            metadata = metadata,
            lazyFileHandle = getLazyFileHandle(EncodedTorrentInfo.createRaw(data), metadata, parentContext),
        )
    }

    private fun getLazyFileHandle(
        encoded: EncodedTorrentInfo,
        metadata: MediaCacheMetadata,
        parentContext: CoroutineContext
    ): LazyFileHandle {
        val scope = CoroutineScope(parentContext + Job(parentContext[Job]))

        // lazy
        val state = flow {
            val downloader = torrentEngine.getDownloader()
            val res = kotlinx.coroutines.withTimeoutOrNull(30_000) {
                val session = downloader.startDownload(encoded, parentContext)
                logger.info { "$mediaSourceId: waiting for files" }
                onDownloadStarted(session)

                val files = session.getFiles()
                val selectedFile = TorrentMediaResolver.selectVideoFileEntry(
                    files,
                    { pathInTorrent },
                    listOf(metadata.episodeName),
                    episodeSort = metadata.episodeSort,
                    episodeEp = metadata.episodeEp,
                )

                if (selectedFile == null) {
                    logger.error {
                        """
                            $mediaSourceId: Selected null file to download. Diagnosis:
                            - Files: ${files.map { it.pathInTorrent }}
                            - Metadata: $metadata
                        """.trimIndent()
                    }
                } else {
                    logger.info { "$mediaSourceId: Selected file to download: $selectedFile" }
                }

                val handle = selectedFile?.createHandle()
                if (handle == null) {
                    session.closeIfNotInUse()
                }
                LazyFileHandle.State(session, selectedFile, handle)
            }
            if (res == null) {
                logger.error { "$mediaSourceId: Timed out while starting download or selecting file. Returning null handle. episode name: ${metadata.episodeName}" }
                emit(null)
            } else {
                emit(res)
            }
        }
        return LazyFileHandle(
            scope,
            state
                .shareIn(
                    scope,
                    SharingStarted.Lazily,
                    replay = 1,
                ), // Must be Lazily here since TorrentMediaCache is not closed
        )
    }

    @OptIn(ExperimentalStdlibApi::class)
    override suspend fun createCache(
        origin: Media,
        metadata: MediaCacheMetadata,
        episodeMetadata: EpisodeMetadata,
        parentContext: CoroutineContext
    ): MediaCache {
        if (!supports(origin)) throw UnsupportedOperationException("Media is not supported by this engine $this: ${origin.download}")
        val downloader = torrentEngine.getDownloader()
        val data = downloader.fetchTorrent(origin.download.uri)
        val newMetadata = metadata.withExtra(
            mapOf(
                EXTRA_TORRENT_DATA to data.data.toHexString(),
                EXTRA_TORRENT_CACHE_DIR to downloader.getSaveDirForTorrent(data).absolutePath,
            ),
        )

        return TorrentMediaCache(
            origin = origin,
            metadata = newMetadata,
            lazyFileHandle = getLazyFileHandle(data, newMetadata, parentContext),
        )
    }

    override suspend fun modifyMetadataForMigration(
        original: MediaCacheMetadata,
        newSaveDir: Path
    ): MediaCacheMetadata {
        val currentTorrentData = original.extra[EXTRA_TORRENT_DATA]?.hexToByteArray() ?: return original
        return original.copy(
            extra = original.extra.toMutableMap().apply {
                put(
                    EXTRA_TORRENT_CACHE_DIR,
                    // TODO: The hardcoded path is for anitorrent only. 
                    // see AnitorrentTorrentDownloader
                    newSaveDir
                        .resolve(torrentEngine.type.id)
                        .resolve("pieces")
                        .resolve(currentTorrentData.contentHashCode().toString())
                        .inSystem.absolutePath,
                )
            },
        )
    }

    @OptIn(ExperimentalStdlibApi::class)
    override suspend fun deleteUnusedCaches(all: List<MediaCache>) {
        val downloader = torrentEngine.getDownloader()
        val allowedAbsolute = buildSet(capacity = all.size) {
            for (mediaCache in all) {
                add(mediaCache.metadata.extra[EXTRA_TORRENT_CACHE_DIR]) // 上次记录的位置

                val data = mediaCache.metadata.extra[EXTRA_TORRENT_DATA]?.runCatching { hexToByteArray() }?.getOrNull()
                if (data != null) {
                    // 如果新版本 ani 的缓存目录有变, 对于旧版本的 metadata, 存的缓存目录会是旧版本的, 
                    // 就需要用 `getSaveDirForTorrent` 重新计算新目录
                    add(downloader.getSaveDirForTorrent(EncodedTorrentInfo.createRaw(data)).absolutePath)
                }
            }
        }

        withContext(Dispatchers.IO) {
            val saves = downloader.listSaves()
            for (save in saves) {
                if (save.absolutePath !in allowedAbsolute) {
                    logger.warn { "本地种子缓存文件未找到匹配的 MediaCache, 已释放 ${save.actualSize()}: ${save.absolutePath}" }
                    save.deleteRecursively()
                }
            }
        }
    }

    override fun close() {
        torrentEngine.close()
    }
}