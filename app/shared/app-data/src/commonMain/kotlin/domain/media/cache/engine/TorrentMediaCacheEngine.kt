/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
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
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.io.IOException
import kotlinx.io.files.FileNotFoundException
import kotlinx.io.files.Path
import me.him188.ani.app.data.persistent.database.dao.TorrentCacheInfoDao
import me.him188.ani.app.data.persistent.database.dao.TorrentCacheInfoEntity
import me.him188.ani.app.domain.media.cache.DownloaderStatus
import me.him188.ani.app.domain.media.cache.LocalFileMediaCache
import me.him188.ani.app.domain.media.cache.MediaCache
import me.him188.ani.app.domain.media.cache.MediaCacheState
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
import me.him188.ani.datasources.api.MediaCacheProperties
import me.him188.ani.datasources.api.topic.FileSize
import me.him188.ani.datasources.api.topic.FileSize.Companion.bytes
import me.him188.ani.datasources.api.topic.ResourceLocation
import me.him188.ani.torrent.pikpak.PartialListing
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
import me.him188.ani.utils.logging.debug
import me.him188.ani.utils.logging.error
import me.him188.ani.utils.logging.info
import me.him188.ani.utils.logging.logger
import me.him188.ani.utils.logging.warn
import me.him188.ani.utils.platform.currentTimeMillis
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds


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
    private val dao: TorrentCacheInfoDao,
    val flowDispatcher: CoroutineContext = Dispatchers.Default,
    private val baseSaveDirProvider: MediaSaveDirProvider,
    /**
     * 持久化的缓存记录, 与使用本引擎的 [me.him188.ani.app.domain.media.cache.storage.MediaCacheStorage] 是同一个.
     *
     * 季度合集的每一集是一条独立记录, 共用同一个 torrent 行与可能同一个文件. 删除一条记录时必须知道
     * 同一 media 下还剩哪些记录, 否则会连带删掉其他集的 torrent_cache 行和文件.
     */
    internal val metadataStore: DataStore<List<MediaCacheSave>>,
    private val onDownloadStarted: suspend (session: TorrentSession) -> Unit = {},
) : MediaCacheEngine, AutoCloseable {
    companion object {
        private val logger = logger<TorrentMediaCacheEngine>()
        private val unspecifiedFileStatsFlow = flowOf(MediaCache.FileStats.Unspecified)
        private val unspecifiedSessionStatsFlow = flowOf(MediaCache.SessionStats.Unspecified)
        private val unspecifiedFileSizeFlow = flowOf(FileSize.Unspecified)

        const val LEGACY_MEDIA_CACHE_DIR = "torrent-caches"
        private val DOWNLOADER_STATUS_INTERVAL = 1.seconds
    }

    val isServiceConnected = engineAccess.isServiceConnected

    /**
     * 同一 media 下本引擎还持有的记录. 删除流程先从 store 移除被删记录再关闭缓存, 所以这里读到的是删除后仍存活的记录.
     */
    private suspend fun remainingRecordsOfMedia(mediaId: String): List<MediaCacheMetadata> =
        metadataStore.data.first()
            .filter { it.engine == engineKey && it.origin.mediaId == mediaId }
            .map { it.metadata }

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
        initialMetadata: MediaCacheMetadata,
        val fileHandle: FileHandle
    ) : MediaCache, SynchronizedObject() {
        private val metadataFlow = MutableStateFlow(initialMetadata)
        override val metadata: MediaCacheMetadata get() = metadataFlow.value

        /**
         * 实例由本引擎创建, 创建方拿不到持久化入口, 因此只能由持有本实例的
         * [me.him188.ani.app.domain.media.cache.storage.TorrentMediaCacheStorage] 在拿到实例后赋值.
         *
         * 不赋值时 [metadata] 的变化只存在于内存: 下载完成、[resumeByUser] 的转正都不会写回 store,
         * 重启后记录回到旧状态.
         */
        var onMetadataUpdated: (suspend (MediaCacheMetadata) -> Unit)? = null

        private val metadataUpdateLock = Mutex()

        private suspend fun updateMetadata(transform: (MediaCacheMetadata) -> MediaCacheMetadata) {
            metadataUpdateLock.withLock {
                val current = metadataFlow.value
                val updated = transform(current)
                if (updated != current) {
                    metadataFlow.value = updated
                    onMetadataUpdated?.invoke(updated)
                }
            }
        }

        private val desiredState = MutableStateFlow(
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
                        cacheProperties = MediaCacheProperties(
                            pathInTorrent = metadata.pathInTorrent
                                ?: fileHandle.entry.first()?.pathInTorrent,
                        ),
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

        override val downloaderStatus: Flow<DownloaderStatus?> = if (engineKey.isCloud) {
            fileHandle.entry.flatMapLatest { entry ->
                entry?.error ?: flowOf(null)
            }.map { error ->
                DownloaderStatus.Cloud(errorMessage = error?.let { it.message ?: it::class.simpleName })
            }.flowOn(flowDispatcher)
        } else {
            combine(isServiceConnected, fileHandle.state) { connected, handleState -> connected to handleState }
                .flatMapLatest { (connected, handleState) ->
                    val startup = when {
                        handleState == null -> DownloaderStatus.TorrentStartup.TIMED_OUT
                        handleState.handle == null -> DownloaderStatus.TorrentStartup.NO_MATCHING_FILE
                        else -> DownloaderStatus.TorrentStartup.STARTED
                    }
                    if (!connected || handleState?.handle == null) {
                        return@flatMapLatest flowOf(DownloaderStatus.Torrent(connected, startup, null, 0, 0))
                    }
                    // 节点与任务状态只能主动查询, 有订阅者时每秒刷新.
                    val session = handleState.session
                    flow {
                        while (true) {
                            val peers = session.getPeers()
                            emit(
                                DownloaderStatus.Torrent(
                                    serviceConnected = true,
                                    startup = startup,
                                    state = session.getState(),
                                    connectedPeers = peers.size,
                                    seeds = peers.count { it.progress >= 1f },
                                ),
                            )
                            delay(DOWNLOADER_STATUS_INTERVAL)
                        }
                    }.catch { e ->
                        // 任务在查询期间被关闭时停止刷新, 保留最后一次结果.
                        if (e is CancellationException) throw e
                        logger.warn(e) { "Failed to query downloader status of ${origin.mediaId}" }
                    }
                }.flowOn(flowDispatcher)
        }

        override val state: Flow<MediaCacheState> =
            combine(desiredState, fileHandle.state, fileStats) { currentState, handleState, stats ->
                when {
                    handleState == null -> MediaCacheState.FAILED
                    stats.isDownloadFinished -> MediaCacheState.COMPLETED
                    currentState == MediaCacheState.PAUSED -> MediaCacheState.PAUSED
                    else -> MediaCacheState.IN_PROGRESS
                }
            }.flowOn(flowDispatcher)

        override suspend fun pause() {
            if (isDeleted.value) return
            desiredState.value = MediaCacheState.PAUSED
            fileHandle.handle.first()?.pause()
        }

        override suspend fun close() {
            if (isDeleted.value) return
            fileHandle.close()
        }

        override suspend fun resume() {
            if (isDeleted.value) return
            desiredState.value = MediaCacheState.IN_PROGRESS
            val file = fileHandle.handle.first()
            logger.info { "Resuming file: $file" }
            file?.resume(FilePriority.NORMAL)
        }

        // User intent promotes an automatic record to a full download; lifecycle resume must not do this.
        override suspend fun resumeByUser() {
            if (isDeleted.value) return
            updateMetadata { it.copy(autoCached = false) }
            resume()
        }

        override val isDeleted: MutableStateFlow<Boolean> = MutableStateFlow(false)

        override suspend fun closeAndDeleteFiles() {
            logger.info { "closeAndDeleteFiles is called" }
            if (isDeleted.value) return
            synchronized(this) {
                if (isDeleted.value) return
                isDeleted.value = true
            }

            val remaining = remainingRecordsOfMedia(origin.mediaId)

            val fileStillInUse = remaining.any { it.pathInTorrent != null && it.pathInTorrent == metadata.pathInTorrent }

            // 只需要在删除缓存的时候 torrent engine 可用, 不需要保证一直可用
            @OptIn(EnsureTorrentEngineIsAccessible::class)
            val handle =
                engineAccess.withServiceRequest("TorrentMediaCache#$this-closeAndDeleteFiles:${origin.mediaId}") {
                    logger.info { "Getting handle" }
                    val handle = fileHandle.handle.first() ?: kotlin.run {
                        // did not even selected a file
                        logger.info { "Deleting torrent cache: No file selected" }
                        close()
                        deleteRowIfLastRecord(remaining)
                        return
                    }

                    logger.info { "Closing TorrentCache" }
                    close()

                    if (fileStillInUse) {
                        logger.info { "Another record still plays ${metadata.pathInTorrent}, keeping the file" }
                        handle.close()
                        deleteRowIfLastRecord(remaining)
                        return
                    }

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
            deleteRowIfLastRecord(remaining)
        }

        private suspend fun deleteRowIfLastRecord(remaining: List<MediaCacheMetadata>) {
            if (remaining.isNotEmpty()) {
                logger.info {
                    "${remaining.size} record(s) of ${origin.mediaId} remain, keeping torrent_cache row"
                }
                return
            }
            dao.deleteByMediaId(origin.mediaId)
        }

        /**
         * 订阅当前 TorrentMediaCache 的统计信息以更新它的 metadata.
         */
        suspend fun subscribeStats(shareRatioLimitFlow: Flow<Float>) {
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

                    val entity = dao.get(origin.mediaId)
                        ?: error("No entity with id ${origin.mediaId} exists while subscribing cache.")

                    val finished = metadata.completed ||
                            (entryFileStats.isDownloadFinished && currentShareRatio >= currentShareRatioLimit) // 统计判断达到条件也是完成

                    // 无论如何都先更新一次数据
                    updateMetadata {
                        it.copy(
                            completed = finished,
                            pathInTorrent = if (finished) fileEntry.pathInTorrent else it.pathInTorrent,
                        )
                    }

                    dao.upsert(
                        entity.copy(
                            completed = entity.completed || finished,
                            pathInTorrent = fileEntry.pathInTorrent,
                            downloadSize = entryFileStats.downloadedBytes,
                            uploadSize = sessionStats.uploadedBytes,
                        ),
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

                            updateMetadata {
                                it.copy(completed = true, pathInTorrent = fileEntry.pathInTorrent)
                            }
                            dao.upsert(
                                entity.copy(
                                    completed = true,
                                    pathInTorrent = fileEntry.pathInTorrent,
                                    downloadSize = fileStats.downloadedBytes,
                                    uploadSize = sessionStats.uploadedBytes,
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
            val finishedMediaStats = dao.getAll().map { saveList ->
                var totalFinishedDownloaded = 0L.bytes
                var totalFinishedUploaded = 0L.bytes

                saveList.filter { it.completed }.forEach { save ->
                    val downloaded = save.downloadSize.bytes
                    val uploaded = save.uploadSize.bytes

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
        if (!torrentEngine.isSupported) return false
        return media.download is ResourceLocation.HttpTorrentFile
                || media.download is ResourceLocation.MagnetLink
    }

    @OptIn(ExperimentalStdlibApi::class)
    override suspend fun restore(
        origin: Media,
        metadata: MediaCacheMetadata,
        parentContext: CoroutineContext
    ): MediaCache? {
        val entity = dao.get(origin.mediaId) ?: kotlin.run {
            logger.warn { "No torrent_cache row for ${origin.mediaId}, cannot restore cache: $metadata" }
            return null
        }
        val data = entity.torrentData

        // Completed files remain usable after the engine is disabled or credentials are removed.
        val localFile = origin.resolveCompletedFromDataStore(metadata, entity)
        if (localFile != null) {
            return LocalFileMediaCache(origin, metadata, localFile) { file ->
                @OptIn(DelicateCoroutinesApi::class)
                GlobalScope.launch {
                    try {
                        if (!torrentEngine.isSupported) {
                            deleteCompletedLocalFile(origin.mediaId, metadata, file)
                            return@launch
                        }

                        // 如果想删除 LocalFileMediaCache 类型的缓存, 需要启动 torrent engine 删除.
                        // 启动后马上恢复这个缓存并删除, 这个操作需要保证 torrent engine 可用, 删除完成后释放 torrent engine 可用性.
                        @OptIn(EnsureTorrentEngineIsAccessible::class)
                        engineAccess
                            .withServiceRequest("LocalFileMediaCache#$this-closeAndDeleteFiles:${origin.mediaId}") {
                                TorrentMediaCache(
                                    origin = origin,
                                    initialMetadata = metadata,
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
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Throwable) {
                        logger.error(e) { "Failed to delete local cache of ${origin.mediaId}" }
                    }
                }
            }
        }

        if (!supports(origin)) throw UnsupportedOperationException("Media is not supported by this engine $this: ${origin.download}")

        @OptIn(EnsureTorrentEngineIsAccessible::class)
        return engineAccess.withServiceRequest("TorrentMediaCacheEngine#$this-restore:${origin.mediaId}") {
            TorrentMediaCache(
                origin = origin,
                initialMetadata = metadata,
                fileHandle = getFileHandle(EncodedTorrentInfo.createRaw(data), metadata, parentContext),
            )
        }
    }

    /**
     * Records written before per-episode completion tracking have no [MediaCacheMetadata.pathInTorrent];
     * the torrent row is their only source of completion and file choice.
     *
     * Only the caller knows whether the media has a single record: the row describes the torrent, so for a
     * season pack it would wrongly mark every episode as completed with the same file.
     */
    suspend fun backfillFromTorrentRow(mediaId: String, metadata: MediaCacheMetadata): MediaCacheMetadata {
        if (metadata.pathInTorrent != null) return metadata
        val entity = dao.get(mediaId) ?: return metadata
        return metadata.copy(
            completed = entity.completed,
            pathInTorrent = entity.pathInTorrent.takeIf { it.isNotEmpty() },
        )
    }

    private suspend fun deleteCompletedLocalFile(
        mediaId: String,
        metadata: MediaCacheMetadata,
        file: SystemPath,
    ) {
        val remaining = remainingRecordsOfMedia(mediaId)
        val fileStillInUse = remaining.any { it.pathInTorrent != null && it.pathInTorrent == metadata.pathInTorrent }
        if (fileStillInUse) {
            logger.info { "Another record still plays ${metadata.pathInTorrent}, keeping the file" }
        } else {
            withContext(Dispatchers.IO_) {
                if (!file.exists()) {
                    logger.info { "Torrent cache does not exist, ignoring: $file" }
                    return@withContext
                }
                logger.info { "Deleting torrent cache without the engine: $file" }
                try {
                    file.delete()
                } catch (_: FileNotFoundException) {
                } catch (e: IOException) {
                    logger.warn("Failed to delete cache file $file", e)
                }
            }
        }
        if (remaining.isNotEmpty()) {
            logger.info { "${remaining.size} record(s) of $mediaId remain, keeping torrent_cache row" }
            return
        }
        dao.deleteByMediaId(mediaId)
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

            val selectedFile = metadata.pathInTorrent
                ?.let { path -> files.firstOrNull { it.pathInTorrent == path } }
                ?: TorrentMediaResolver.selectVideoFileEntry(
                    files,
                    { fileName },
                    listOf(metadata.episodeName),
                    episodeSort = metadata.episodeSort,
                    episodeEp = metadata.episodeEp,
                    listingComplete = (session as? PartialListing)?.listingComplete ?: true,
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
    ): TorrentMediaCache {
        if (!supports(origin)) throw UnsupportedOperationException("Media is not supported by this engine $this: ${origin.download}")
        // 创建缓存需要保证 torrent engine 一直可用, 所以 getFileHandle 直接启动协程创建好缓存.
        @OptIn(EnsureTorrentEngineIsAccessible::class)
        engineAccess.withServiceRequest("TorrentMediaCacheEngine#$this-createCache:${origin.mediaId}") {
            val downloader = torrentEngine.getDownloader()
            val data = downloader.fetchTorrent(origin.download.uri)

            dao.upsert(
                TorrentCacheInfoEntity(
                    mediaId = origin.mediaId,
                    torrentData = data.data,
                    relativeDir = downloader.getSaveDirForTorrent(data).absolutePath.let { path ->
                        val stripped = path.substringAfter(baseSaveDirProvider.saveDir)
                        if (path == stripped) {
                            throw UnsupportedOperationException(
                                "Failed to strip torrent save path of media ${origin.mediaId}, " +
                                        "path: $path, base: ${baseSaveDirProvider.saveDir}",
                            )
                        }
                        stripped
                    },
                ),
            )

            return TorrentMediaCache(
                origin = origin,
                initialMetadata = metadata,
                fileHandle = getFileHandle(data, metadata, parentContext),
            )
        }
    }

    @OptIn(ExperimentalStdlibApi::class)
    override suspend fun deleteUnusedCaches(all: List<MediaCache>) {
        if (!withContext(Dispatchers.IO_) { torrentEngine.saveDir.exists() }) {
            logger.debug { "$mediaSourceId: engine save dir does not exist, skipping cache pruning." }
            return
        }
        if (!torrentEngine.isSupported) {
            logger.debug { "$mediaSourceId: engine is not supported, skipping cache pruning." }
            return
        }

        // 只需要在删除缓存的时候 torrent engine 可用, 不需要保证一直可用
        @OptIn(EnsureTorrentEngineIsAccessible::class)
        engineAccess.withServiceRequest("TorrentMediaCacheEngine#$this-deleteUnusedCaches") {
            val downloader = torrentEngine.getDownloader()

            val allowedAbsolute = buildSet {
                dao.batchGet(all.map { it.origin.mediaId }).forEach {
                    add(Path(baseSaveDirProvider.saveDir).resolve(it.relativeDir).inSystem.absolutePath)
                    add(downloader.getSaveDirForTorrent(EncodedTorrentInfo.createRaw(it.torrentData)).absolutePath)
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

    // Completion and file choice belong to each episode record, not the shared torrent row.
    private fun Media.resolveCompletedFromDataStore(
        metadata: MediaCacheMetadata,
        entity: TorrentCacheInfoEntity,
    ): SystemPath? {
        if (!metadata.completed) return null
        val pathInTorrent = metadata.pathInTorrent?.takeIf { it.isNotEmpty() } ?: return null

        val file = Path(baseSaveDirProvider.saveDir, entity.relativeDir).resolve(pathInTorrent).inSystem
        if (!file.exists() || file.isDirectory()) {
            return null
        }

        return file
    }
}
