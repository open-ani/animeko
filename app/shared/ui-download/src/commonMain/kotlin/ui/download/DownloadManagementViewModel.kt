/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.download

import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.him188.ani.app.data.repository.player.EpisodePlayHistoryRepository
import me.him188.ani.app.data.repository.subject.OfflineSubjectDisplayInfo
import me.him188.ani.app.data.repository.subject.SubjectCollectionRepository
import me.him188.ani.app.data.repository.subject.staticSubjectImageLargeUrl
import me.him188.ani.app.domain.media.cache.engine.MediaStats
import me.him188.ani.app.domain.media.cache.engine.sum
import me.him188.ani.app.domain.media.cache.storage.MediaCacheStorage
import me.him188.ani.app.domain.media.download.DownloadOperations
import me.him188.ani.app.domain.media.download.MediaDownloadManager
import me.him188.ani.app.domain.media.download.ObserveDownloadsUseCase
import me.him188.ani.app.ui.download.components.DownloadItem
import me.him188.ani.app.ui.download.components.SubjectDownloadGroup
import me.him188.ani.app.ui.download.components.toDownloadItem
import me.him188.ani.app.ui.foundation.AbstractViewModel
import me.him188.ani.datasources.api.topic.UnifiedCollectionType
import me.him188.ani.utils.coroutines.sampleWithInitial

class DownloadManagementViewModel(
    downloadManager: MediaDownloadManager,
    subjects: SubjectCollectionRepository,
    histories: EpisodePlayHistoryRepository,
    observeDownloads: ObserveDownloadsUseCase,
    private val operations: DownloadOperations,
) : AbstractViewModel() {
    private val operationFailures = MutableStateFlow(0)
    private val downloads = observeDownloads().shareInBackground()
    private val subjectMetadata = downloads.map { list ->
        list.map { it.metadata.subjectId.toIntOrNull() ?: 0 }.toSet()
    }.distinctUntilChanged().flatMapLatest { ids ->
        if (ids.isEmpty()) flowOf(emptyMap()) else combine(ids.map { id ->
            combine(
                subjects.getSubjectCollectionTypeOffline(id).onStart { emit(null) },
                subjects.getSubjectDisplayInfoOffline(id).onStart { emit(null) },
            ) { type, info -> id to SubjectMetadata(type, info) }
        }) { it.toMap() }
    }
    private val overallStats = downloadManager.enabledStorages.overallStatsFlow().sampleWithInitial(1.seconds)

    val uiState = combine(downloads, subjectMetadata, histories.flow, overallStats, operationFailures) { downloads, metadata, histories, stats, failures ->
        val historyByEpisode = histories.associateBy { it.episodeId }
        val groups = downloads.groupBy { it.metadata.subjectId.toIntOrNull() ?: 0 }.map { (subjectId, snapshots) ->
            val subject = metadata[subjectId]
            val entries = snapshots.map { it.toDownloadItem(subject?.type, historyByEpisode[it.metadata.episodeId.toIntOrNull()]) }
            SubjectDownloadGroup(
                subjectId = subjectId,
                subjectName = subject?.info?.displayName ?: entries.first().subjectName,
                entries = entries,
                collectionType = subject?.type,
                imageUrl = subject?.info?.imageLarge ?: staticSubjectImageLargeUrl(subjectId),
                totalEpisodeCount = subject?.info?.totalEpisodes?.takeIf { it > 0 },
            )
        }.sortedWith(
            compareByDescending<SubjectDownloadGroup> { it.entries.any { entry -> !entry.isFinished } }
                .thenByDescending { it.entries.maxOfOrNull { entry -> entry.creationTime ?: 0 } },
        )
        DownloadManagementUiState(stats, groups, isLoading = false, failedOperationCount = failures)
    }.stateInBackground(DownloadManagementUiState.Placeholder)

    fun pauseDownload(item: DownloadItem) = execute(setOf(item.id), DownloadOperations.Action.Pause)
    fun resumeDownload(item: DownloadItem) = execute(setOf(item.id), DownloadOperations.Action.Resume)
    fun deleteDownload(item: DownloadItem) = execute(setOf(item.id), DownloadOperations.Action.Delete)
    fun dismissOperationError() { operationFailures.value = 0 }

    private fun execute(ids: Set<String>, action: DownloadOperations.Action) {
        val pending = operations.submit(ids, action)
        backgroundScope.launch {
            val result = pending.await()
            operationFailures.update { it + result.failures.size }
        }
    }

    private data class SubjectMetadata(val type: UnifiedCollectionType?, val info: OfflineSubjectDisplayInfo?)
}

internal fun Flow<List<MediaCacheStorage>>.overallStatsFlow(): Flow<MediaStats> {
    return flatMapLatest { storages ->
        if (storages.isEmpty()) {
            flowOf(MediaStats.Zero)
        } else {
            storages.map { it.stats }.sum()
        }
    }
}
