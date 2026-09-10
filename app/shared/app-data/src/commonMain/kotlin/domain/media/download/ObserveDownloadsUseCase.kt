/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.media.download

import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.him188.ani.app.domain.media.cache.MediaCache
import me.him188.ani.app.domain.media.cache.MediaCacheState
import me.him188.ani.app.domain.media.cache.engine.MediaCacheEngineKey
import me.him188.ani.app.tools.Progress
import me.him188.ani.app.torrent.api.files.averageRate
import me.him188.ani.datasources.api.MediaCacheMetadata
import me.him188.ani.datasources.api.topic.FileSize
import me.him188.ani.datasources.api.topic.FileSize.Companion.bytes
import me.him188.ani.utils.coroutines.sampleWithInitial

data class DownloadSnapshot(
    val id: String,
    val metadata: MediaCacheMetadata,
    val status: MediaCacheState,
    val progress: Progress,
    val totalSize: FileSize,
    val downloadSpeed: FileSize,
    val canPlay: Boolean,
    val sourceId: String,
    val engineKey: MediaCacheEngineKey,
)

/** Each download keeps its progress collector until removed; adding another item does not reset rates. */
class ObserveDownloadsUseCase(private val downloadManager: MediaDownloadManager) {
    operator fun invoke(subjectId: Int? = null): Flow<List<DownloadSnapshot>> = channelFlow {
        val snapshots = MutableStateFlow<Map<String, DownloadSnapshot>>(emptyMap())
        val expectedIds = MutableStateFlow<Set<String>?>(null)
        val collectors = mutableMapOf<String, Pair<MediaCache, Job>>()
        val output = launch {
            combine(snapshots, expectedIds) { values, ids ->
                if (ids == null || !values.keys.containsAll(ids)) null else ids.mapNotNull(values::get)
            }.collect { if (it != null) send(it) }
        }
        downloadManager.enabledStorages.flatMapLatest { storages ->
            if (storages.isEmpty()) flowOf(emptyList()) else combine(storages.map { storage ->
                storage.listFlow.map { entries -> entries.map { it to storage.engine.engineKey } }
            }) { it.toList().flatten() }
        }.collect { allEntries ->
            val entries = allEntries.filter { subjectId == null || it.first.metadata.subjectId.toIntOrNull() == subjectId }
                .distinctBy { it.first.cacheId }
            val ids = entries.mapTo(hashSetOf()) { it.first.cacheId }
            expectedIds.value = ids
            collectors.keys.filter { it !in ids }.forEach { id -> collectors.remove(id)?.second?.cancel() }
            snapshots.update { it.filterKeys { id -> id in ids } }
            for ((cache, engineKey) in entries) {
                if (collectors[cache.cacheId]?.first === cache) continue
                collectors.remove(cache.cacheId)?.second?.cancel()
                collectors[cache.cacheId] = cache to launch {
                    observeDownload(cache, engineKey).collect { snapshot ->
                        ensureActive()
                        snapshots.update { it + (cache.cacheId to snapshot) }
                    }
                }
            }
        }
        awaitClose {
            output.cancel()
            collectors.values.forEach { it.second.cancel() }
        }
    }

    private fun observeDownload(cache: MediaCache, engineKey: MediaCacheEngineKey): Flow<DownloadSnapshot> {
        val stats = cache.fileStats.combine(cache.fileStats.map { it.downloadedBytes.inBytes }.averageRate()) { value, rate ->
            value to rate.bytes
        }.sampleWithInitial(1.seconds)
        return combine(stats, cache.state, cache.canPlay) { (stats, speed), status, canPlay ->
            DownloadSnapshot(
                id = cache.cacheId,
                metadata = cache.metadata,
                status = status,
                progress = stats.downloadProgress,
                totalSize = stats.totalSize,
                downloadSpeed = speed,
                canPlay = canPlay,
                sourceId = cache.origin.mediaSourceId,
                engineKey = engineKey,
            )
        }.distinctUntilChanged()
    }
}
