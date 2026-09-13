/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.download

import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
import me.him188.ani.app.domain.media.download.DownloadOperation
import me.him188.ani.app.domain.media.download.DownloadOperations
import me.him188.ani.app.domain.media.download.MediaDownloadManager
import me.him188.ani.app.ui.download.components.DownloadItem
import me.him188.ani.app.ui.download.components.SubjectDownloadGroup
import me.him188.ani.app.ui.download.components.toDownloadItem
import me.him188.ani.app.ui.download.subject.SubjectDownloadsPresenter
import me.him188.ani.app.ui.download.subject.SubjectDownloadsPresenterFactory
import me.him188.ani.app.ui.foundation.AbstractViewModel
import me.him188.ani.datasources.api.topic.UnifiedCollectionType
import me.him188.ani.utils.coroutines.sampleWithInitial

/**
 * 全局下载管理页面: 所有存储中的下载按条目分组展示.
 *
 * @param coroutineContext [backgroundScope] 的额外 context, 测试时传入测试调度器.
 */
class DownloadManagementViewModel(
    downloadManager: MediaDownloadManager,
    subjects: SubjectCollectionRepository,
    histories: EpisodePlayHistoryRepository,
    private val operations: DownloadOperations,
    private val presenters: SubjectDownloadsPresenterFactory,
    coroutineContext: CoroutineContext = EmptyCoroutineContext,
) : AbstractViewModel(coroutineContext) {
    private val operationFailures = MutableStateFlow(0)

    private val currentSubjectPresenter = MutableStateFlow<SubjectDownloadsPresenter?>(null)

    /**
     * 详情栏展示的条目, `null` 表示未展示.
     */
    val subjectPresenter: StateFlow<SubjectDownloadsPresenter?> = currentSubjectPresenter.asStateFlow()

    /**
     * 上一条目的实例被关闭, 其选源会话随之取消; 相同条目不做任何事.
     */
    fun selectSubject(subjectId: Int?) {
        val previous = currentSubjectPresenter.value
        if (previous?.subjectId == subjectId) return
        currentSubjectPresenter.value = subjectId?.let { presenters.create(it, backgroundScope) }
        previous?.close()
    }
    private val downloads = downloadManager.snapshots().shareInBackground()

    /**
     * 数据库返回前以 `null` 占位, 列表不必等待条目信息.
     */
    private val subjectMetadata = downloads
        .map { list -> list.mapTo(hashSetOf()) { it.metadata.subjectId.toIntOrNull() ?: 0 } }
        .distinctUntilChanged()
        .flatMapLatest { ids ->
            if (ids.isEmpty()) {
                flowOf(emptyMap())
            } else {
                combine(
                    ids.map { id ->
                        combine(
                            subjects.getSubjectCollectionTypeOffline(id).onStart { emit(null) },
                            subjects.getSubjectDisplayInfoOffline(id).onStart { emit(null) },
                        ) { type, info -> id to SubjectMetadata(type, info) }
                    },
                ) { it.toMap() }
            }
        }
    private val overallStats = downloadManager.overallStats.sampleWithInitial(1.seconds)

    val uiState = combine(downloads, subjectMetadata, histories.flow, overallStats, operationFailures) { downloads, metadata, histories, stats, failures ->
        val historyByEpisode = histories.associateBy { it.episodeId }
        val groups = downloads.groupBy { it.metadata.subjectId.toIntOrNull() ?: 0 }.map { (subjectId, snapshots) ->
            val subject = metadata[subjectId]
            val entries = snapshots.map { snapshot ->
                val history = snapshot.metadata.episodeId.toIntOrNull()?.let { historyByEpisode[it] }
                snapshot.toDownloadItem(subject?.type, history)
            }
            SubjectDownloadGroup(
                subjectId = subjectId,
                subjectName = subject?.info?.displayName ?: entries.first().subjectName,
                entries = entries,
                collectionType = subject?.type,
                imageUrl = subject?.info?.imageLarge ?: staticSubjectImageLargeUrl(subjectId),
                totalEpisodeCount = subject?.info?.totalEpisodes?.takeIf { it > 0 },
            )
        }.sortedWith(
            // 有未完成下载的条目在前, 其余按最新一条下载的创建时间降序.
            compareByDescending<SubjectDownloadGroup> { it.hasUnfinished }
                .thenByDescending { it.entries.maxOfOrNull { entry -> entry.creationTime ?: 0 } },
        )
        DownloadManagementUiState(stats, groups, isLoading = false, failedOperationCount = failures)
    }.stateInBackground(DownloadManagementUiState.Placeholder)

    fun pauseDownload(item: DownloadItem) = execute(setOf(item.id), DownloadOperation.Pause)
    fun resumeDownload(item: DownloadItem) = execute(setOf(item.id), DownloadOperation.Resume)
    fun deleteDownload(item: DownloadItem) = execute(setOf(item.id), DownloadOperation.Delete)

    fun dismissOperationError() {
        operationFailures.value = 0
    }

    /**
     * 因忙碌被拒绝的下载不计入失败.
     */
    private fun execute(ids: Set<String>, operation: DownloadOperation) {
        if (ids.isEmpty()) return
        val pending = operations.submit(ids, operation)
        backgroundScope.launch {
            val failures = pending.await().failures.size
            if (failures > 0) operationFailures.update { it + failures }
        }
    }

    private data class SubjectMetadata(val type: UnifiedCollectionType?, val info: OfflineSubjectDisplayInfo?)
}
