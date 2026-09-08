/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.media.download

import androidx.compose.runtime.Stable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import me.him188.ani.app.domain.media.cache.EpisodeCacheStatus
import me.him188.ani.app.domain.media.cache.MediaCache
import me.him188.ani.app.domain.media.cache.MediaCacheState
import me.him188.ani.app.domain.media.cache.engine.MediaCacheEngineKey
import me.him188.ani.app.domain.media.cache.storage.MediaCacheStorage
import me.him188.ani.app.ui.foundation.HasBackgroundScope
import me.him188.ani.datasources.api.Media
import me.him188.ani.datasources.api.source.MediaSourceKind
import me.him188.ani.utils.coroutines.flows.flowOfEmptyList

/**
 * 管理跨存储的持久化视频下载, 包括手动下载与播放时自动保存的下载.
 *
 * [MediaCacheStorage] 负责实际下载及文件持久化; 播放引擎的临时缓冲不属于此管理器.
 */
class MediaDownloadManager(
    val storagesIncludingDisabled: List<MediaCacheStorage>,
    override val backgroundScope: CoroutineScope,
) : HasBackgroundScope {
    val enabledStorages: Flow<List<MediaCacheStorage>> = flowOf(storagesIncludingDisabled)

    /** Resolves the default download destination in registration order, preserving PikPak routing. */
    suspend fun defaultStorageFor(media: Media): MediaCacheStorage {
        val supported = enabledStorages.first().filter { it.engine.supports(media) }
        // The HTTP engine supports BT media only when PikPak is enabled and can resolve it.
        if (media.kind == MediaSourceKind.BitTorrent) {
            supported.firstOrNull { it.engine.engineKey == MediaCacheEngineKey.WebM3u }?.let { return it }
        }
        return checkNotNull(supported.firstOrNull()) { "No download storage supports this media" }
    }

    private val downloadsFlow: Flow<List<MediaCache>> by lazy {
        val flows = storagesIncludingDisabled.map { it.listFlow }
        if (flows.isEmpty()) {
            flowOfEmptyList()
        } else combine(flows) {
            it.asSequence().flatten().toList()
        }
    }

    @Stable
    fun downloadsForSubject(
        subjectId: Int,
    ): Flow<List<MediaCache>> {
        val subjectIdString = subjectId.toString()
        return downloadsFlow.map { list ->
            list.filter { download ->
                download.metadata.subjectId == subjectIdString
            }
        }
    }

    /**
     * Observes the aggregate download status of an episode from persisted storage records.
     */
    @Stable
    fun downloadStatusForEpisode(
        subjectId: Int,
        episodeId: Int,
    ): Flow<EpisodeCacheStatus> {
        val subjectIdString = subjectId.toString()
        val episodeIdString = episodeId.toString()
        return downloadsFlow.transformLatest { list ->
            var completedDownload: MediaCache? = null
            var pendingDownload: MediaCache? = null

            for (download in list) {
                if (download.metadata.subjectId == subjectIdString && download.metadata.episodeId == episodeIdString) {
                    when (download.state.first()) {
                        MediaCacheState.COMPLETED -> completedDownload = download
                        MediaCacheState.IN_PROGRESS,
                        MediaCacheState.PAUSED,
                            -> pendingDownload = download

                        MediaCacheState.FAILED -> Unit
                    }
                }
            }

            val target = completedDownload ?: pendingDownload
            if (target == null) {
                emit(EpisodeCacheStatus.NotCached)
            } else {
                emitAll(
                    target.state.combine(target.fileStats) { state, stats ->
                        when (state) {
                            MediaCacheState.COMPLETED -> EpisodeCacheStatus.Cached(totalSize = stats.totalSize)
                            MediaCacheState.IN_PROGRESS,
                            MediaCacheState.PAUSED,
                                -> EpisodeCacheStatus.Caching(
                                progress = stats.downloadProgress,
                                totalSize = stats.totalSize,
                            )

                            MediaCacheState.FAILED -> EpisodeCacheStatus.NotCached
                        }
                    },
                )
            }
        }.flowOn(Dispatchers.Default)
    }

    suspend fun deleteDownload(download: MediaCache): Boolean {
        for (storage in enabledStorages.first()) {
            if (storage.delete(download)) {
                return true
            }
        }
        return false
    }

    suspend fun deleteFirstDownload(filter: (MediaCache) -> Boolean): Boolean {
        for (storage in enabledStorages.first()) {
            if (storage.deleteFirst(filter)) {
                return true
            }
        }
        return false
    }

    suspend fun findFirstDownload(filter: (MediaCache) -> Boolean): MediaCache? {
        for (storage in enabledStorages.first()) {
            storage.listFlow.first().find(filter)?.let {
                return it
            }
        }
        return null
    }

    suspend fun findAllDownloads(filter: (MediaCache) -> Boolean): List<MediaCache> {
        val result = mutableListOf<MediaCache>()
        for (storage in enabledStorages.first()) {
            val downloads = storage.listFlow.first().filter(filter)
            result.addAll(downloads)
        }
        return result
    }

    suspend fun closeDownloads() = supervisorScope {
        for (storage in enabledStorages.first()) {
            for (download in storage.listFlow.first()) {
                launch { download.close() }
            }
        }
    }

    companion object {
        /**
         * 本地数据源不允许有多个实例. 必须是 Factory:MediaSource:Instance = 1:1:1 的关系.
         */
        const val LOCAL_FS_MEDIA_SOURCE_ID = "local-file-system"
    }
}
