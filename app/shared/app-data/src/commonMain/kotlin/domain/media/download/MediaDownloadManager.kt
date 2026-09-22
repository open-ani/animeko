/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.media.download

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import me.him188.ani.app.domain.media.cache.EpisodeCacheStatus
import me.him188.ani.app.domain.media.cache.MediaCache
import me.him188.ani.app.domain.media.cache.MediaCacheState
import me.him188.ani.app.domain.media.cache.engine.MediaCacheEngineKey
import me.him188.ani.app.domain.media.cache.engine.MediaStats
import me.him188.ani.app.domain.media.cache.engine.sum
import me.him188.ani.app.domain.media.cache.storage.MediaCacheStorage
import me.him188.ani.app.domain.media.resolver.EpisodeMetadata
import me.him188.ani.app.ui.foundation.HasBackgroundScope
import me.him188.ani.datasources.api.Media
import me.him188.ani.datasources.api.MediaCacheMetadata
import me.him188.ani.datasources.api.source.MediaSourceKind
import me.him188.ani.utils.coroutines.flows.flowOfEmptyList

/**
 * 聚合各 [MediaCacheStorage] 的持久化下载, 为每条记录维持稳定的 [MediaDownload] 实例, 提供创建、删除与查询入口. 与应用同生命周期.
 */
class MediaDownloadManager(
    val storages: List<MediaCacheStorage>,
    override val backgroundScope: CoroutineScope,
) : HasBackgroundScope {
    /**
     * 每个存储都给出首个列表后才有首个元素. 存储在启动时异步恢复记录, 恢复完成前列表可能为空.
     * 相同 id 的记录只保留注册顺序靠前的一个; 离开列表的实例在此 [MediaDownload.close].
     */
    private val loadedDownloads: SharedFlow<List<MediaDownload>> = storages
        .map { storage -> storage.listFlow.map { caches -> storage to caches } }
        .let { flows -> if (flows.isEmpty()) flowOf(emptyList()) else combine(flows) { it.toList() } }
        .scan(emptyList<MediaDownload>()) { previous, entries ->
            val existing = previous.associateBy { it.cache }
            // 旧数据可能在多个存储中有同一记录, 先按 id 去重, 避免为被丢弃的记录构造永不关闭的实例.
            val next = entries
                .flatMap { (storage, caches) -> caches.map { cache -> storage to cache } }
                .distinctBy { (_, cache) -> cache.cacheId }
                .map { (storage, cache) -> existing[cache] ?: MediaDownload(cache, storage, backgroundScope) }
            val retained = next.toHashSet()
            previous.forEach { download -> if (download !in retained) download.close() }
            next
        }
        .drop(1)
        .shareIn(backgroundScope, SharingStarted.Eagerly, replay = 1)

    /**
     * 供同步读取, 比 [snapshots] 晚一个 dispatch; 存储给出首个列表前为空.
     */
    val downloads: StateFlow<List<MediaDownload>> =
        loadedDownloads.stateIn(backgroundScope, SharingStarted.Eagerly, emptyList())

    fun downloadsForSubject(subjectId: Int): Flow<List<MediaDownload>> {
        val subjectKey = subjectId.toString()
        return loadedDownloads.map { list -> list.filter { it.metadata.subjectId == subjectKey } }.distinctUntilChanged()
    }

    /**
     * 各存储给出首个列表后才输出.
     * @param subjectId 为 `null` 时观察全部
     */
    fun snapshots(subjectId: Int? = null): Flow<List<DownloadSnapshot>> {
        val source = if (subjectId == null) loadedDownloads else downloadsForSubject(subjectId)
        return source.flatMapLatest { it.combineSnapshots() }
    }

    /**
     * 所有存储的传输统计之和.
     */
    val overallStats: Flow<MediaStats> =
        if (storages.isEmpty()) flowOf(MediaStats.Zero) else storages.map { it.stats }.sum()

    /**
     * 某一集的下载状态: 有完成的为 [EpisodeCacheStatus.Cached], 否则有进行中或暂停的为 [EpisodeCacheStatus.Caching].
     * 直接观察 [MediaCache.state] 与 [MediaCache.fileStats], 不计算速度: 剧集列表会为每一集调用本方法.
     */
    fun downloadStatusForEpisode(subjectId: Int, episodeId: Int): Flow<EpisodeCacheStatus> {
        val subjectKey = subjectId.toString()
        val episodeKey = episodeId.toString()
        return loadedDownloads
            .map { list -> list.filter { it.metadata.subjectId == subjectKey && it.metadata.episodeId == episodeKey } }
            .distinctUntilChanged()
            .flatMapLatest { matching ->
                if (matching.isEmpty()) {
                    flowOf(EpisodeCacheStatus.NotCached)
                } else {
                    combine(matching.map { it.cache.episodeProgress() }) { it.toEpisodeCacheStatus() }
                }
            }
            .distinctUntilChanged()
            .flowOn(Dispatchers.Default)
    }

    /**
     * 按注册顺序取第一个支持该资源的存储; BT 资源在 PikPak 引擎可用时优先经它下载.
     * @throws UnsupportedOperationException 没有存储支持该资源
     */
    fun defaultStorageFor(media: Media): MediaCacheStorage {
        val supported = storages.filter { it.engine.supports(media) }
        if (media.kind == MediaSourceKind.BitTorrent) {
            supported.firstOrNull { it.engine.engineKey == MediaCacheEngineKey.WebM3u }?.let { return it }
        }
        return supported.firstOrNull()
            ?: throw UnsupportedOperationException("No download storage supports media ${media.mediaId}")
    }

    /**
     * 在 [storage] 中创建并持久化下载; 任一存储已有同一资源同一集的记录时直接返回该记录. 传输由引擎异步进行.
     */
    suspend fun createDownload(
        media: Media,
        metadata: MediaCacheMetadata,
        episodeMetadata: EpisodeMetadata,
        storage: MediaCacheStorage = defaultStorageFor(media),
    ): MediaCache {
        for (other in storages) {
            if (other === storage) continue
            other.listFlow.first().firstOrNull { it.isSameMediaAndEpisode(media, metadata) }?.let { return it }
        }
        return storage.cache(media, metadata, episodeMetadata)
    }

    /**
     * 基于 [downloads] 的当前值, 比存储滞后一个 dispatch; 需要与存储一致时用 [findCaches].
     */
    fun findDownload(id: String): MediaDownload? = downloads.value.firstOrNull { it.id == id }

    /**
     * 按实例查找, 语义同 [findDownload].
     */
    fun downloadOf(cache: MediaCache): MediaDownload? = downloads.value.firstOrNull { it.cache === cache }

    /**
     * 直接读取各存储的当前记录, 与刚完成的创建或删除一致.
     */
    suspend fun findCaches(filter: (MediaCache) -> Boolean): List<MediaCache> =
        storages.flatMap { it.listFlow.first() }.filter(filter)

    /**
     * 返回 `false` 表示记录已不存在.
     */
    suspend fun delete(download: MediaDownload): Boolean = download.storage.delete(download.cache)

    /**
     * 记录尚未出现在 [downloads] 中时直接交给各存储处理.
     */
    suspend fun deleteDownload(cache: MediaCache): Boolean {
        downloadOf(cache)?.let { return delete(it) }
        return storages.any { it.delete(cache) }
    }

    private fun List<MediaDownload>.combineSnapshots(): Flow<List<DownloadSnapshot>> =
        if (isEmpty()) flowOfEmptyList() else combine(map { it.snapshot }) { it.toList() }

    companion object {
        /**
         * 本地数据源不允许有多个实例. 必须是 Factory:MediaSource:Instance = 1:1:1 的关系.
         */
        const val LOCAL_FS_MEDIA_SOURCE_ID = "local-file-system"
    }
}

private fun MediaCache.isSameMediaAndEpisode(media: Media, metadata: MediaCacheMetadata): Boolean =
    origin.mediaId == media.mediaId &&
            this.metadata.subjectId == metadata.subjectId &&
            this.metadata.episodeId == metadata.episodeId

/**
 * 单条记录的状态与文件统计, 供 [MediaDownloadManager.downloadStatusForEpisode] 汇总.
 */
private data class EpisodeProgress(
    val state: MediaCacheState,
    val fileStats: MediaCache.FileStats,
)

private fun MediaCache.episodeProgress(): Flow<EpisodeProgress> =
    combine(state, fileStats) { state, fileStats -> EpisodeProgress(state, fileStats) }

private fun Array<EpisodeProgress>.toEpisodeCacheStatus(): EpisodeCacheStatus {
    firstOrNull { it.state == MediaCacheState.COMPLETED }?.let {
        return EpisodeCacheStatus.Cached(totalSize = it.fileStats.totalSize)
    }
    firstOrNull { it.state == MediaCacheState.IN_PROGRESS || it.state == MediaCacheState.PAUSED }?.let {
        return EpisodeCacheStatus.Caching(progress = it.fileStats.downloadProgress, totalSize = it.fileStats.totalSize)
    }
    return EpisodeCacheStatus.NotCached
}
