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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.io.IOException
import kotlinx.io.files.FileNotFoundException
import kotlinx.io.files.Path
import me.him188.ani.app.domain.media.cache.LocalFileMediaCache
import me.him188.ani.app.domain.media.cache.MediaCache
import me.him188.ani.app.domain.media.cache.MediaCacheState
import me.him188.ani.app.domain.media.cache.engine.TorrentMediaCacheEngine.Companion.EXTRA_TORRENT_CACHE_DIR
import me.him188.ani.app.domain.media.cache.engine.TorrentMediaCacheEngine.Companion.EXTRA_TORRENT_CACHE_FILE_SIZE
import me.him188.ani.app.domain.media.cache.engine.TorrentMediaCacheEngine.Companion.EXTRA_TORRENT_CACHE_UPLOADED_SIZE
import me.him188.ani.app.domain.media.cache.engine.TorrentMediaCacheEngine.Companion.EXTRA_TORRENT_DATA
import me.him188.ani.app.domain.media.cache.storage.MediaCacheSave
import me.him188.ani.app.domain.media.cache.storage.MediaSaveDirProvider
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
import me.him188.ani.utils.io.resolveSibling
import me.him188.ani.utils.logging.debug
import me.him188.ani.utils.logging.error
import me.him188.ani.utils.logging.info
import me.him188.ani.utils.logging.logger
import me.him188.ani.utils.logging.warn
import me.him188.ani.utils.platform.currentTimeMillis
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration.Companion.minutes


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
    private val engineAccess: TorrentEngineAccess,
    private val mediaCacheMetadataStore: DataStore<List<MediaCacheSave>>,
    private val shareRatioLimitFlow: Flow<Float>,
    val flowDispatcher: CoroutineContext = Dispatchers.Default,
    private val baseSaveDirProvider: MediaSaveDirProvider,
    private val onDownloadStarted: suspend (session: TorrentSession) -> Unit = {},
) : MediaCacheEngine, AutoCloseable {
    companion object {
        private val EXTRA_TORRENT_DATA = MetadataKey("torrentData")

        /**
         * 种子的缓存目录, 相对于 [MediaSaveDirProvider.saveDir] 的相对路径.
         *
         * 注意, 一个 MediaCache 可能只对应该种子资源的其中一个文件
         */
        val EXTRA_TORRENT_CACHE_DIR = MetadataKey("torrentCacheDir")
        val EXTRA_TORRENT_COMPLETED = MetadataKey("torrentCompleted") // torrent 是否已经完成, 意味着已经下载完并达到分享率
        val EXTRA_TORRENT_CACHE_FILE =
            MetadataKey("torrentCacheFile") // MediaCache 所对应的视频文件相对路径. 该文件一定是 [EXTRA_TORRENT_CACHE_DIR] 目录中的文件 (的其中一个)
        val EXTRA_TORRENT_CACHE_FILE_SIZE = MetadataKey("torrentFileSize") // 种子缓存目录中的文件大小
        val EXTRA_TORRENT_CACHE_UPLOADED_SIZE = MetadataKey("torrentFileUploadedSize") // 上传过的流量大小

        private val logger = logger<TorrentMediaCacheEngine>()
        private val unspecifiedFileStatsFlow = flowOf(MediaCache.FileStats.Unspecified)
        private val unspecifiedSessionStatsFlow = flowOf(MediaCache.SessionStats.Unspecified)
        private val unspecifiedFileSizeFlow = flowOf(FileSize.Unspecified)

        const val LEGACY_MEDIA_CACHE_DIR = "torrent-caches"
    }

    val isServiceConnected = engineAccess.isServiceConnected

    class FileHandle(val state: Flow<State?>) {
        val handle = state.map { it?.handle } // single emit
        val entry = state.map { it?.entry } // single emit
        val session = state.map { it?.session }

        suspend fun close() {
            handle.first()?.close()
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
            // 获取 cached media 不需要让 torrent engine 一直可用
            @OptIn(EnsureTorrentEngineIsAccessible::class)
            engineAccess.withServiceRequest("TorrentMediaCache#$this-getCachedMedia:${origin.mediaId}") {
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

        override val fileStats: Flow<MediaCache.FileStats> = fileHandle.entry.flatMapLatest { entry ->
            if (entry == null) return@flatMapLatest unspecifiedFileStatsFlow

            entry.fileStats.map { stats ->
                MediaCache.FileStats(
                    totalSize = entry.length.bytes,
                    downloadedBytes = stats.downloadedBytes.bytes,
                    downloadProgress = stats.downloadProgress.toProgress(),
                )
            }
        }.flowOn(flowDispatcher)

        override val sessionStats: Flow<MediaCache.SessionStats> = fileHandle.session.flatMapLatest { handle ->
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
            fileHandle.handle.first()?.pause()
            state.value = MediaCacheState.PAUSED
        }

        override suspend fun close() {
            if (isDeleted.value) return
            fileHandle.close()
        }

        override suspend fun resume() {
            if (isDeleted.value) return
            val file = fileHandle.handle.first()
            state.value = MediaCacheState.IN_PROGRESS
            logger.info { "Resuming file: $file" }
            file?.resume(FilePriority.NORMAL)
        }

        override val isDeleted: MutableStateFlow<Boolean> = MutableStateFlow(false)

        override suspend fun closeAndDeleteFiles() {
            logger.info { "closeAndDeleteFiles is called" }
            if (isDeleted.value) return
            synchronized(this) {
                if (isDeleted.value) return
                isDeleted.value = true
            }

            // 只需要在删除缓存的时候 torrent engine 可用, 不需要保证一直可用
            @OptIn(EnsureTorrentEngineIsAccessible::class)
            val handle =
                engineAccess.withServiceRequest("TorrentMediaCache#$this-closeAndDeleteFiles:${origin.mediaId}") {
                    logger.info { "Getting handle" }
                    val handle = fileHandle.handle.first() ?: kotlin.run {
                        // did not even selected a file
                        logger.info { "Deleting torrent cache: No file selected" }
                        close()
                        return
                    }

                    logger.info { "Closing TorrentCache" }
                    close()

                    logger.info { "Closing torrent file handle" }
                    handle.closeAndDelete()

                    handle
                }

            withContext(Dispatchers.IO_) {
                val file = handle.entry.resolveFileMaybeEmptyOrNull() ?: kotlin.run {
                    logger.warn { "No file resolved for torrent entry '${handle.entry.fileName}'" }
                    return@withContext
                }
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

        /**
         * 订阅当前 TorrentMediaCache 的统计信息以更新它的 metadata.
         */
        suspend fun subscribeStats(onUpdateMetadata: suspend (Map<MetadataKey, String>) -> Unit) {
            isServiceConnected.collectLatest { serviceStarted ->
                if (!serviceStarted) return@collectLatest

                coroutineScope {
                    val fileEntryFlow = fileHandle.entry.filterNotNull()
                        .shareIn(this, SharingStarted.Lazily, replay = 1)
                    val sessionStatsFlow = fileHandle.session.filterNotNull()
                        .flatMapLatest { it.sessionStats }.filterNotNull()
                        .shareIn(this, SharingStarted.Lazily, replay = 1)

                    val fileEntry = fileEntryFlow.first()
                    val entryFileStats = fileEntry.fileStats.filterNotNull().first()
                    val sessionStats = sessionStatsFlow.first()

                    val currentShareRatioLimit = shareRatioLimitFlow.first()
                    val currentShareRatio = sessionStats.uploadedBytes /
                            entryFileStats.downloadedBytes.coerceAtLeast(1).toFloat()

                    val finished = metadata.extra[EXTRA_TORRENT_COMPLETED] == "true" || // metadata 已记录 true 表示已完成
                            (entryFileStats.isDownloadFinished && currentShareRatio >= currentShareRatioLimit) // 统计判断达到条件也是完成

                    // 无论如何都先更新一次数据
                    onUpdateMetadata(
                        buildMap {
                            if (finished) {
                                put(EXTRA_TORRENT_COMPLETED, "true")
                            }
                            put(EXTRA_TORRENT_CACHE_FILE, fileEntry.pathInTorrent)
                            put(EXTRA_TORRENT_CACHE_FILE_SIZE, entryFileStats.downloadedBytes.toString())
                            put(EXTRA_TORRENT_CACHE_UPLOADED_SIZE, sessionStats.uploadedBytes.toString())
                        },
                    )

                    // 如果种子任务已经完成了就不启动了
                    if (finished) {
                        logger.debug { "Cache task ${origin.mediaId} is already finished, ignore stats subscription." }
                        return@coroutineScope
                    }

                    // 最后一次有上传活动的时间
                    var lastUploadActivity = currentTimeMillis()
                    // 当更新完 metadata 后需要停止 stats collector
                    // 因为 TorrentMediaCache 没有订阅 metadata 的能力, 使用一个 flow 来辅助停止
                    val finishedFlow = MutableStateFlow(false) // always false initially

                    finishedFlow.collectLatest finished@{ f ->
                        if (f) {
                            logger.debug { "Cache task ${origin.mediaId} is finished, stop stats subscription." }
                            return@finished
                        }

                        logger.debug { "Subscribed stats of cache task ${origin.mediaId}." }

                        combine(
                            sessionStatsFlow,
                            fileEntryFlow.flatMapLatest { it.fileStats.filterNotNull() },
                            shareRatioLimitFlow,
                        ) task@{ sessionStats, fileStats, shareRatioLimit ->
                            if (!fileStats.isDownloadFinished) return@task

                            val shareRatio = sessionStats.uploadedBytes /
                                    fileStats.downloadedBytes.coerceAtLeast(1).toFloat()

                            // 没达到分享率才进入这里的逻辑, 达到分享率直接更新 metadata
                            if (shareRatio < shareRatioLimit) {
                                val currentTimeMillis = currentTimeMillis()

                                // 如果距离上次上传活动小于 10 分钟, 不能更新 metadata, 因为 10 分钟内还可能有上传
                                if (currentTimeMillis - lastUploadActivity < 10.minutes.inWholeMilliseconds) {
                                    // 如果有上传活动, 更新最后的活动时间
                                    if (sessionStats.uploadSpeed > 0L) {
                                        lastUploadActivity = currentTimeMillis
                                    }
                                    return@task
                                }
                                // 如果距离上次上传活动大于 10 分钟, 直接更新 metadata
                            }

                            onUpdateMetadata(
                                mapOf(
                                    EXTRA_TORRENT_COMPLETED to "true",
                                    EXTRA_TORRENT_CACHE_FILE_SIZE to fileStats.downloadedBytes.toString(),
                                    EXTRA_TORRENT_CACHE_UPLOADED_SIZE to sessionStats.uploadedBytes.toString(),
                                ),
                            )

                            finishedFlow.value = true // side effect.
                        }.run {
                            try {
                                collect()
                            } catch (ex: CancellationException) {
                                logger.debug { "Stat subscription of cache task ${origin.mediaId} is cancelled." }
                                throw ex // re-throw it. 
                            }
                        }
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

    override val stats: Flow<MediaStats> = engineAccess.isServiceConnected
        .flatMapLatest { useEngine ->
            val finishedMediaStats = mediaCacheMetadataStore.data.map { saveList ->
                var totalFinishedDownloaded = 0L.bytes
                var totalFinishedUploaded = 0L.bytes

                saveList.forEach { save ->
                    if (save.engine != engineKey) return@forEach

                    val downloaded = save.metadata.torrentDownloaded
                    val uploaded = save.metadata.torrentUploaded

                    if (downloaded != FileSize.Unspecified) totalFinishedDownloaded += downloaded
                    if (uploaded != FileSize.Unspecified) totalFinishedUploaded += uploaded
                }

                MediaStats(
                    uploaded = totalFinishedUploaded,
                    downloaded = totalFinishedDownloaded,
                    uploadSpeed = 0L.bytes,
                    downloadSpeed = 0L.bytes,
                )
            }

            if (!useEngine) {
                return@flatMapLatest finishedMediaStats
            }

            flow { emit(torrentEngine.getDownloader()) }
                .flatMapLatest {
                    combine(finishedMediaStats, it.totalStats) { finished, engineStats ->
                        MediaStats(
                            uploaded = engineStats.uploadedBytes.bytes + finished.uploaded,
                            downloaded = engineStats.downloadedBytes.bytes + finished.downloaded,
                            uploadSpeed = engineStats.uploadSpeed.bytes + finished.uploadSpeed,
                            downloadSpeed = engineStats.downloadSpeed.bytes + finished.downloadSpeed,
                        )
                    }
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

        val localFile = metadata.resolveCompletedFromDataStore()
        if (localFile != null) {
            return LocalFileMediaCache(origin, metadata, localFile) {
                @OptIn(DelicateCoroutinesApi::class)
                GlobalScope.launch {
                    // 如果想删除 LocalFileMediaCache 类型的缓存, 需要启动 torrent engine 删除.
                    // 启动后马上恢复这个缓存并删除, 这个操作需要保证 torrent engine 可用, 删除完成后释放 torrent engine 可用性.
                    @OptIn(EnsureTorrentEngineIsAccessible::class)
                    engineAccess
                        .withServiceRequest("LocalFileMediaCache#$this-closeAndDeleteFiles:${origin.mediaId}") {
                            TorrentMediaCache(
                                origin = origin,
                                metadata = metadata,
                                fileHandle = getFileHandle(
                                    EncodedTorrentInfo.createRaw(data),
                                    metadata,
                                    coroutineContext,
                                ),
                            ).apply {
                                resume()
                                closeAndDeleteFiles()
                            }
                        }
                }
            }
        }

        @OptIn(EnsureTorrentEngineIsAccessible::class)
        return engineAccess.withServiceRequest("TorrentMediaCacheEngine#$this-restore:${origin.mediaId}") {
            TorrentMediaCache(
                origin = origin,
                metadata = metadata,
                fileHandle = getFileHandle(EncodedTorrentInfo.createRaw(data), metadata, parentContext),
            )
        }
    }

    private suspend fun getFileHandle(
        encoded: EncodedTorrentInfo,
        metadata: MediaCacheMetadata,
        parentContext: CoroutineContext,
    ): FileHandle {
        val downloader = torrentEngine.getDownloader()
        val res = kotlinx.coroutines.withTimeoutOrNull(30_000) {
            val session = downloader.startDownload(encoded, parentContext)
            logger.info { "$mediaSourceId: waiting for files" }
            onDownloadStarted(session)

            val files = session.getFiles()
            val selectedFile = TorrentMediaResolver.selectVideoFileEntry(
                files,
                { fileName },
                listOf(metadata.episodeName),
                episodeSort = metadata.episodeSort,
                episodeEp = metadata.episodeEp,
            )

            if (selectedFile == null) {
                logger.error {
                    """
                            $mediaSourceId: Selected null file to download. Diagnosis:
                            - Files: ${files.map { it.fileName }}
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
        }

        return FileHandle(flowOf(res))
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
        engineAccess.withServiceRequest("TorrentMediaCacheEngine#$this-createCache:${origin.mediaId}") {
            val downloader = torrentEngine.getDownloader()
            val data = downloader.fetchTorrent(origin.download.uri)
            val newMetadata = metadata.withExtra(
                mapOf(
                    EXTRA_TORRENT_DATA to data.data.toHexString(),
                    EXTRA_TORRENT_CACHE_DIR to downloader.getSaveDirForTorrent(data).absolutePath.let { path ->
                        val stripped = path.substringAfter(baseSaveDirProvider.saveDir)
                        if (path == stripped) {
                            logger.warn {
                                "Failed to strip torrent save path of media ${origin.mediaId}, " +
                                        "path: $path, base: ${baseSaveDirProvider.saveDir}"
                            }
                        }
                        stripped
                    },
                ),
            )

            return TorrentMediaCache(
                origin = origin,
                metadata = newMetadata,
                fileHandle = getFileHandle(data, newMetadata, parentContext),
            )
        }
    }

    @OptIn(ExperimentalStdlibApi::class)
    override suspend fun deleteUnusedCaches(all: List<MediaCache>) {
        // 只需要在删除缓存的时候 torrent engine 可用, 不需要保证一直可用
        @OptIn(EnsureTorrentEngineIsAccessible::class)
        engineAccess.withServiceRequest("TorrentMediaCacheEngine#$this-deleteUnusedCaches") {
            val downloader = torrentEngine.getDownloader()
            val allowedAbsolute = buildSet(capacity = all.size) {
                for (mediaCache in all) {
                    mediaCache.metadata.extra[EXTRA_TORRENT_CACHE_DIR] // 上次记录的位置
                        ?.let {
                            add(Path(baseSaveDirProvider.saveDir).resolve(it).inSystem.absolutePath)
                        }

                    mediaCache.metadata.extra[EXTRA_TORRENT_DATA]
                        ?.runCatching { hexToByteArray() }
                        ?.getOrNull()
                        ?.let {
                            // 如果新版本 ani 的缓存目录有变, 对于旧版本的 metadata, 存的缓存目录会是旧版本的, 
                            // 就需要用 `getSaveDirForTorrent` 重新计算新目录
                            add(downloader.getSaveDirForTorrent(EncodedTorrentInfo.createRaw(it)).absolutePath)
                        }
                }
            }

            withContext(Dispatchers.IO_) {
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

    override fun close() {
        torrentEngine.close()
    }

    private fun MediaCacheMetadata.resolveCompletedFromDataStore(): SystemPath? {
        if (extra[EXTRA_TORRENT_COMPLETED] != "true") return null
        val cacheDir = extra[EXTRA_TORRENT_CACHE_DIR] ?: return null
        val cacheRelativeFilePath = extra[EXTRA_TORRENT_CACHE_FILE] ?: return null

        val file = Path(baseSaveDirProvider.saveDir, cacheDir).resolve(cacheRelativeFilePath).inSystem
        if (!file.exists() || file.isDirectory()) {
            return null
        }

        return file
    }
}

private val MediaCacheMetadata.torrentDownloaded: FileSize
    get() = extra[EXTRA_TORRENT_CACHE_FILE_SIZE]?.toLongOrNull()?.bytes ?: FileSize.Unspecified

private val MediaCacheMetadata.torrentUploaded: FileSize
    get() = extra[EXTRA_TORRENT_CACHE_UPLOADED_SIZE]?.toLongOrNull()?.bytes ?: FileSize.Unspecified