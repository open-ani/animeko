/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.media.cache.engine

import androidx.datastore.core.DataStore
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext
import kotlinx.io.IOException
import kotlinx.io.files.FileNotFoundException
import kotlinx.io.files.Path
import me.him188.ani.app.domain.media.cache.MediaCache
import me.him188.ani.app.domain.media.cache.MediaCacheState
import me.him188.ani.app.domain.media.cache.engine.TorrentMediaCacheEngine.Companion.EXTRA_TORRENT_CACHE_DIR
import me.him188.ani.app.domain.media.cache.engine.TorrentMediaCacheEngine.Companion.EXTRA_TORRENT_CACHE_FILE_SIZE
import me.him188.ani.app.domain.media.cache.engine.TorrentMediaCacheEngine.Companion.EXTRA_TORRENT_CACHE_UPLOADED_SIZE
import me.him188.ani.app.domain.media.cache.engine.TorrentMediaCacheEngine.Companion.EXTRA_TORRENT_DATA
import me.him188.ani.app.domain.media.cache.storage.MediaCacheSave
import me.him188.ani.app.domain.media.resolver.EpisodeMetadata
import me.him188.ani.app.domain.media.resolver.TorrentMediaResolver
import me.him188.ani.app.domain.torrent.TorrentEngine
import me.him188.ani.app.tools.Progress
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
import me.him188.ani.utils.coroutines.IO_
import me.him188.ani.utils.io.SystemPath
import me.him188.ani.utils.io.absolutePath
import me.him188.ani.utils.io.actualSize
import me.him188.ani.utils.io.delete
import me.him188.ani.utils.io.deleteRecursively
import me.him188.ani.utils.io.exists
import me.him188.ani.utils.io.inSystem
import me.him188.ani.utils.io.isDirectory
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
 *
 * ## [TorrentEngine]
 */
class TorrentMediaCacheEngine(
    /**
     * 创建的 [CachedMedia] 将会使用此 [mediaSourceId]
     */
    private val mediaSourceId: String,
    override val engineKey: MediaCacheEngineKey,
    val torrentEngine: TorrentEngine,
    private val engineAccess: TorrentEngineAccess,
    private val mediaCacheMetadataStore: DataStore<List<MediaCacheSave>>,
    private val shareRatioLimitFlow: Flow<Float>,
    val flowDispatcher: CoroutineContext = Dispatchers.Default,
    private val onDownloadStarted: suspend (session: TorrentSession) -> Unit = {},
) : MediaCacheEngine, AutoCloseable {
    companion object {
        private val EXTRA_TORRENT_DATA = MetadataKey("torrentData")
        val EXTRA_TORRENT_CACHE_DIR = MetadataKey("torrentCacheDir") // 种子的缓存目录, 注意, 一个 MediaCache 可能只对应该种子资源的其中一个文件
        val EXTRA_TORRENT_COMPLETED = MetadataKey("torrentCompleted") // torrent 是否已经完成, 意味着已经下载完并达到分享率
        val EXTRA_TORRENT_CACHE_FILE =
            MetadataKey("torrentCacheFile") // MediaCache 所对应的视频文件相对路径. 该文件一定是 [EXTRA_TORRENT_CACHE_DIR] 目录中的文件 (的其中一个)
        val EXTRA_TORRENT_CACHE_FILE_SIZE = MetadataKey("torrentFileSize") // 种子缓存目录中的文件大小
        val EXTRA_TORRENT_CACHE_UPLOADED_SIZE = MetadataKey("torrentFileUploadedSize") // 上传过的流量大小

        private val logger = logger<TorrentMediaCacheEngine>()
        private val unspecifiedFileStatsFlow = flowOf(MediaCache.FileStats.Unspecified)
        private val unspecifiedSessionStatsFlow = flowOf(MediaCache.SessionStats.Unspecified)
        private val unspecifiedFileSizeFlow = flowOf(FileSize.Unspecified)
    }

    class FileHandle(
        val scope: CoroutineScope,
        val state: SharedFlow<State?>, // suspend lazy or not
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
        val fileHandle: FileHandle
    ) : MediaCache, SynchronizedObject() {
        override val state: MutableStateFlow<MediaCacheState> = MutableStateFlow(
            MediaCacheState.IN_PROGRESS,
        )

        override suspend fun getCachedMedia(): CachedMedia {
            val useEngineAccess = engineAccess.useEngine.value
            logger.info { "getCachedMedia: start, useEngine: $useEngineAccess" }

            // 先判断是否使用 data store 的数据, 如果用就不 access file handle
            if (!useEngineAccess) {
                val localFile = resolveFromDataStore()
                if (localFile != null) {
                    return CachedMedia(
                        origin,
                        mediaSourceId,
                        download = ResourceLocation.LocalFile(localFile.absolutePath),
                    )
                }

                logger.warn {
                    "Local torrent cache ${origin.mediaId} cannot be resumed with datastore save, " +
                            "request to launch torrent engine."
                }
            }

            // 获取 cached media 不需要让 torrent engine 一直可用
            @OptIn(EnsureTorrentEngineIsAccessible::class)
            engineAccess.withEngineAccessible {
                val file = fileHandle.handle.first()
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
        }

        override val fileStats: Flow<MediaCache.FileStats> = engineAccess.useEngine
            .flatMapLatest { useEngine ->
                // 先判断是否使用 data store 的数据, 如果用就不 access file handle
                if (!useEngine) {
                    val fileSize = metadata.torrentDownloaded
                    return@flatMapLatest flowOf(MediaCache.FileStats(fileSize, fileSize))
                }
                fileHandle.entry.flatMapLatest lfh@{ entry ->
                    if (entry == null) return@lfh unspecifiedFileStatsFlow

                    entry.fileStats.map { stats ->
                        MediaCache.FileStats(
                            totalSize = entry.length.bytes,
                            downloadedBytes = stats.downloadedBytes.bytes,
                            downloadProgress = stats.downloadProgress.toProgress(),
                        )
                    }
                }
            }
            .flowOn(flowDispatcher)

        override val sessionStats: Flow<MediaCache.SessionStats> = engineAccess.useEngine
            .flatMapLatest { useEngine ->
                // 先判断是否使用 data store 的数据, 如果用就不 access file handle
                if (!useEngine) {
                    val downloaded = metadata.torrentDownloaded
                    val uploaded = metadata.torrentUploaded
                    return@flatMapLatest flowOf(
                        MediaCache.SessionStats(
                            totalSize = downloaded,
                            downloadedBytes = downloaded,
                            downloadSpeed = 0L.bytes,
                            uploadedBytes = uploaded,
                            uploadSpeed = 0L.bytes,
                            downloadProgress = Progress.fromZeroToOne(1f),
                        ),
                    )
                }

                fileHandle.session.flatMapLatest lfh@{ handle ->
                    if (handle == null) return@lfh unspecifiedSessionStatsFlow
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
                }
            }
            .flowOn(flowDispatcher)

        override suspend fun pause() {
            if (!engineAccess.useEngine.value) return
            if (isDeleted.value) return
            fileHandle.handle.first()?.pause()
            state.value = MediaCacheState.PAUSED
        }

        override suspend fun close() {
            if (!engineAccess.useEngine.value) return
            if (isDeleted.value) return
            fileHandle.close()
        }

        override suspend fun resume() {
            if (!engineAccess.useEngine.value) {
                // todo: 目前不支持已经完成的缓存继续手动开启做种
                state.value = MediaCacheState.IN_PROGRESS
                return
            }
            if (isDeleted.value) return
            val file = fileHandle.handle.first()
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

            // 只需要在删除缓存的时候 torrent engine 可用, 不需要保证一直可用
            @OptIn(EnsureTorrentEngineIsAccessible::class)
            val handle = engineAccess.withEngineAccessible {
                val handle = fileHandle.handle.first() ?: kotlin.run {
                    // did not even selected a file
                    logger.info { "Deleting torrent cache: No file selected" }
                    close()
                    return
                }

                close()
                handle.closeAndDelete()

                handle
            }

            withContext(Dispatchers.IO_) {
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

        private fun resolveFromDataStore(): SystemPath? {
            val cacheDir = metadata.extra[EXTRA_TORRENT_CACHE_DIR] ?: return null
            val cacheRelativeFilePath = metadata.extra[EXTRA_TORRENT_CACHE_FILE] ?: return null

            val file = Path(cacheDir, cacheRelativeFilePath).inSystem
            if (!file.exists() || file.isDirectory()) {
                return null
            }

            return file
        }

        /**
         * 订阅当前 TorrentMediaCache 的统计信息以更新它的 metadata
         */
        suspend fun subscribeStats(onUpdateMetadata: suspend (Map<MetadataKey, String>) -> Unit) {
            // TorrentMediaCache 的构造函数中的 metadata 没有检测更新的能力, 用一个变量来表示已完成
            var completed = false

            engineAccess.useEngine
                .filter { it }
                .collectLatest {
                    coroutineScope {
                        val fileEntryFlow = fileHandle.entry.filterNotNull().shareIn(this, SharingStarted.Lazily)
                        val sessionStatsFlow = fileHandle.session.filterNotNull()
                            .flatMapLatest { it.sessionStats }.filterNotNull()
                            .shareIn(this, SharingStarted.Lazily)

                        val fileEntry = fileEntryFlow.first()
                        val entryFileStats = fileEntry.fileStats.filterNotNull().first()
                        val sessionStats = sessionStatsFlow.first()
                        // 无论如何都先更新一次数据
                        onUpdateMetadata(
                            buildMap {
                                // todo: 没检测分享率
                                if (entryFileStats.isDownloadFinished) {
                                    put(EXTRA_TORRENT_COMPLETED, "true")
                                    completed = true
                                }
                                put(EXTRA_TORRENT_CACHE_FILE, fileEntry.pathInTorrent)
                                put(EXTRA_TORRENT_CACHE_FILE_SIZE, entryFileStats.downloadedBytes.toString())
                                put(EXTRA_TORRENT_CACHE_UPLOADED_SIZE, sessionStats.uploadedBytes.toString())
                            },
                        )
                        fileEntryFlow.collectLatest { entry ->
                            combine(
                                sessionStatsFlow,
                                entry.fileStats.filterNotNull(),
                                shareRatioLimitFlow,
                            ) { sessionStats, fileStats, shareRatioLimit ->
                                if (!fileStats.isDownloadFinished) return@combine

                                // todo: 没检测分享率
                                // val shareRatio = sessionStats.uploadedBytes / fileStats.downloadedBytes.coerceAtLeast(1).toFloat()
                                // if (shareRatio < shareRatioLimit) return@combine

                                if (completed || metadata.extra[EXTRA_TORRENT_COMPLETED] == "true") return@combine

                                completed = true
                                onUpdateMetadata(
                                    mapOf(
                                        EXTRA_TORRENT_COMPLETED to "true",
                                        EXTRA_TORRENT_CACHE_FILE_SIZE to fileStats.downloadedBytes.toString(),
                                        EXTRA_TORRENT_CACHE_UPLOADED_SIZE to sessionStats.uploadedBytes.toString(),
                                    ),
                                )
                            }.collect()
                        }
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

    override val stats: Flow<MediaStats> = engineAccess.useEngine
        .flatMapLatest { useEngine ->
            // 先判断是否使用 data store 的数据, 如果用就不 downloader
            if (!useEngine) {
                var totalDownloaded = 0L.bytes
                var totalUploaded = 0L.bytes

                mediaCacheMetadataStore.data.first().map { save ->
                    if (save.engine != engineKey) return@map

                    val downloaded = save.metadata.torrentDownloaded
                    val uploaded = save.metadata.torrentUploaded

                    if (downloaded != FileSize.Unspecified) totalDownloaded += downloaded
                    if (uploaded != FileSize.Unspecified) totalUploaded += uploaded
                }

                return@flatMapLatest flowOf(
                    MediaStats(
                        uploaded = totalUploaded,
                        downloaded = totalDownloaded,
                        uploadSpeed = 0L.bytes,
                        downloadSpeed = 0L.bytes,
                    ),
                )
            }
            flow { emit(torrentEngine.getDownloader()) }
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
            // lazily handle, 在 TorrentEngineAccess.useEngine 为 false 时不启动
            fileHandle = getFileHandle(EncodedTorrentInfo.createRaw(data), metadata, parentContext),
        )
    }

    private suspend fun getFileHandle(
        encoded: EncodedTorrentInfo,
        metadata: MediaCacheMetadata,
        parentContext: CoroutineContext,
        lazilyStart: Boolean = true,
    ): FileHandle {
        val scope = CoroutineScope(parentContext + Job(parentContext[Job]))

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
                FileHandle.State(session, selectedFile, handle)
            }
            if (res == null) {
                logger.error { "$mediaSourceId: Timed out while starting download or selecting file. Returning null handle. episode name: ${metadata.episodeName}" }
                emit(null)
            } else {
                emit(res)
            }
        }
        return FileHandle(
            scope,
            state.run {
                if (lazilyStart) {
                    shareIn(scope, SharingStarted.Lazily, replay = 1)
                } else {
                    stateIn(scope)
                }
            },
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
        // 创建缓存需要保证 torrent engine 一直可用, 所以 getFileHandle 直接启动协程创建好缓存.
        @OptIn(EnsureTorrentEngineIsAccessible::class)
        engineAccess.withEngineAccessible {
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
                // 必须马上启动 torrent engine 来保证上面 withEngineAccessible 返回时 torrent engine 是可用的.
                fileHandle = getFileHandle(data, newMetadata, parentContext, false),
            )
        }
    }

    override suspend fun modifyMetadataForMigration(
        original: MediaCacheMetadata,
        newSaveDir: Path
    ): MediaCacheMetadata {
        val currentTorrentData = original.extra[EXTRA_TORRENT_DATA]?.hexToByteArray() ?: return original

        // TODO: The hardcoded path is for anitorrent only. 
        // see AnitorrentTorrentDownloader
        val newTorrentCacheDir = newSaveDir
            .resolve(torrentEngine.type.id)
            .resolve("pieces")
            .resolve(currentTorrentData.contentHashCode().toString())
            .inSystem.absolutePath

        logger.info {
            "Migrate metadata, EXTRA_TORRENT_CACHE_DIR prev: ${original.extra[EXTRA_TORRENT_CACHE_DIR]}, " +
                    "new: $newTorrentCacheDir"
        }

        return original.copy(
            extra = original.extra.toMutableMap()
                .apply { put(EXTRA_TORRENT_CACHE_DIR, newTorrentCacheDir) },
        )
    }

    @OptIn(ExperimentalStdlibApi::class)
    override suspend fun deleteUnusedCaches(all: List<MediaCache>) {
        // 只需要在删除缓存的时候 torrent engine 可用, 不需要保证一直可用
        @OptIn(EnsureTorrentEngineIsAccessible::class)
        engineAccess.withEngineAccessible {
            val downloader = torrentEngine.getDownloader()
            val allowedAbsolute = buildSet(capacity = all.size) {
                for (mediaCache in all) {
                    add(mediaCache.metadata.extra[EXTRA_TORRENT_CACHE_DIR]) // 上次记录的位置

                    val data =
                        mediaCache.metadata.extra[EXTRA_TORRENT_DATA]?.runCatching { hexToByteArray() }?.getOrNull()
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
                        logger.warn { "本地种子缓存文件未找到匹配的 MediaCache, 已释放 ${save.actualSize().bytes}: ${save.absolutePath}" }
                        save.deleteRecursively()
                    }
                }
            }
        }
    }

    /**
     * 订阅 torrent engine 状态, torrent engine 状态改变后其 MediaCacheStorage 可能要重新 restore 缓存
     */
    suspend fun subscribeTorrentAccess(block: suspend (Boolean) -> Unit) {
        engineAccess.useEngine
            .filterNotNull()
            .collectLatest(block)
    }

    override fun close() {
        torrentEngine.close()
    }
}

private val MediaCacheMetadata.torrentDownloaded: FileSize
    get() = extra[EXTRA_TORRENT_CACHE_FILE_SIZE]?.toLongOrNull()?.bytes ?: FileSize.Unspecified

private val MediaCacheMetadata.torrentUploaded: FileSize
    get() = extra[EXTRA_TORRENT_CACHE_UPLOADED_SIZE]?.toLongOrNull()?.bytes ?: FileSize.Unspecified