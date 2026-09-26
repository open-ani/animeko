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
import me.him188.ani.app.data.repository.player.EpisodePlayHistoryRepository
import me.him188.ani.app.data.repository.subject.OfflineSubjectDisplayInfo
import me.him188.ani.app.data.repository.subject.SubjectCollectionRepository
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
    operations: DownloadOperations,
    private val presenters: SubjectDownloadsPresenterFactory,
    coroutineContext: CoroutineContext = EmptyCoroutineContext,
) : AbstractViewModel(coroutineContext) {
    private val operationRunner = DownloadOperationRunner(operations, backgroundScope)

    private val currentSubjectPresenter = MutableStateFlow<SubjectDownloadsPresenter?>(null)

    /**
     * 详情栏展示的条目, `null` 表示未展示.
     */
    val subjectPresenter: StateFlow<SubjectDownloadsPresenter?> = currentSubjectPresenter.asStateFlow()

    /**
     * 上一条目的实例被关闭, 其选源会话随之取消; 相同条目不做任何事.
     * @param subjectName 已知的条目名, 在条目信息加载完成前作为标题
     */
    fun selectSubject(subjectId: Int?, subjectName: String? = null) {
        val previous = currentSubjectPresenter.value
        if (previous?.subjectId == subjectId) return
        currentSubjectPresenter.value = subjectId?.let { presenters.create(it, backgroundScope, subjectName) }
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

    val uiState = combine(downloads, subjectMetadata, histories.flow, overallStats) { downloads, metadata, histories, stats ->
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
                imageUrl = subject?.info?.imageThumb,
                totalEpisodeCount = subject?.info?.totalEpisodes?.takeIf { it > 0 },
            )
        }.sortedWith(
            // 有未完成下载的条目在前, 其余按最新一条下载的创建时间降序.
            compareByDescending<SubjectDownloadGroup> { it.hasUnfinished }
                .thenByDescending { it.entries.maxOfOrNull { entry -> entry.creationTime ?: 0 } },
        )
        DownloadManagementUiState(stats, groups, isLoading = false)
    }.stateInBackground(DownloadManagementUiState.Placeholder)

    fun pauseDownloads(ids: Set<String>) = operationRunner.run(ids, DownloadOperation.Pause)
    fun resumeDownloads(ids: Set<String>) = operationRunner.run(ids, DownloadOperation.Resume)
    fun deleteDownloads(ids: Set<String>) = operationRunner.run(ids, DownloadOperation.Delete)

    fun pauseDownload(item: DownloadItem) = operationRunner.run(setOf(item.id), DownloadOperation.Pause)
    fun resumeDownload(item: DownloadItem) = operationRunner.run(setOf(item.id), DownloadOperation.Resume)
    fun deleteDownload(item: DownloadItem) = operationRunner.run(setOf(item.id), DownloadOperation.Delete)

    /**
     * 批量操作累计的失败数, [dismissOperationFailures] 后归零.
     */
    val operationFailures: StateFlow<Int> get() = operationRunner.failedCount

    fun dismissOperationFailures() = operationRunner.dismissFailures()

    private data class SubjectMetadata(val type: UnifiedCollectionType?, val info: OfflineSubjectDisplayInfo?)
}
