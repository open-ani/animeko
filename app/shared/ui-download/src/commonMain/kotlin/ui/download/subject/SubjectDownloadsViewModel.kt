/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.download.subject

import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.him188.ani.app.data.models.subject.nameCnOrName
import me.him188.ani.app.data.repository.player.EpisodePlayHistoryRepository
import me.him188.ani.app.data.repository.subject.SubjectCollectionRepository
import me.him188.ani.app.data.repository.user.SettingsRepository
import me.him188.ani.app.domain.media.download.AddDownloadsState
import me.him188.ani.app.domain.media.download.EpisodeDownloadState
import me.him188.ani.app.domain.media.download.DownloadOperations
import me.him188.ani.app.domain.media.download.AddDownloadsSessionFactory
import me.him188.ani.app.domain.media.download.ObserveDownloadsUseCase
import me.him188.ani.app.domain.media.fetch.MediaSourceManager
import me.him188.ani.app.ui.download.components.toDownloadItem
import me.him188.ani.app.ui.foundation.AbstractViewModel
import me.him188.ani.app.ui.mediafetch.MediaSourceInfoProvider
import me.him188.ani.datasources.api.Media

class SubjectDownloadsViewModel(
    val subjectId: Int,
    subjects: SubjectCollectionRepository,
    histories: EpisodePlayHistoryRepository,
    settings: SettingsRepository,
    sources: MediaSourceManager,
    observeDownloads: ObserveDownloadsUseCase,
    sessionFactory: AddDownloadsSessionFactory,
    private val operations: DownloadOperations,
    coroutineContext: CoroutineContext = EmptyCoroutineContext,
) : AbstractViewModel(coroutineContext) {
    private val reload = MutableStateFlow(0)
    private val operationFailures = MutableStateFlow(0)
    private val session = sessionFactory.create(subjectId, backgroundScope, submitWhenReady = true)
    val requestState = session.state
    val selectorSettings = settings.mediaSelectorSettings.flow
    val sourceInfoProvider = MediaSourceInfoProvider(sources::infoFlowByMediaSourceId)

    private val subject = reload.flatMapLatest { subjects.subjectCollectionFlow(subjectId).asLoadState() }
    private val downloads = reload.flatMapLatest { observeDownloads(subjectId).asLoadState() }

    val uiState = combine(subject, downloads, histories.flow, operationFailures, requestState) { subject, downloads, histories, failures, request ->
        val info = subject.value
        val historyByEpisode = histories.associateBy { it.episodeId }
        val items = downloads.value.orEmpty().map {
            it.toDownloadItem(info?.collectionType, historyByEpisode[it.metadata.episodeId.toIntOrNull()])
        }
        SubjectDownloadsUiState(
            title = info?.subjectInfo?.nameCnOrName,
            items = buildSubjectDownloadItems(info?.downloadEpisodes().orEmpty(), items),
            downloads = items,
            totalEpisodes = info?.episodes?.size,
            episodesLoading = subject.loading,
            downloadsLoading = downloads.loading,
            episodesFailed = subject.failed,
            downloadsFailed = downloads.failed,
            failedOperationCount = failures,
            request = DownloadRequestUiState(
                episodeIds = (request as? AddDownloadsState.Active)?.episodeIds.orEmpty(),
                busy = request is AddDownloadsState.Submitting ||
                        (request as? AddDownloadsState.Editing)?.episodes?.values?.any {
                            it is EpisodeDownloadState.Preparing || it is EpisodeDownloadState.SelectingMedia
                        } == true,
                canCancel = request is AddDownloadsState.Editing,
            ),
        )
    }.stateIn(backgroundScope, SharingStarted.WhileSubscribed(5000), SubjectDownloadsUiState())

    fun reload() { reload.update { it + 1 } }
    fun dismissOperationError() { operationFailures.value = 0 }
    fun requestDownload(episodeId: Int) = session.start(setOf(episodeId))
    fun cancelRequest(requestId: Long) = session.cancel(requestId)
    fun retryRequest(requestId: Long) = session.retry(requestId)
    fun selectMedia(requestId: Long, episodeId: Int, media: Media) = session.selectMedia(requestId, episodeId, media)

    fun pauseDownloads(ids: Set<String>) = execute(ids, DownloadOperations.Action.Pause)
    fun resumeDownloads(ids: Set<String>) = execute(ids, DownloadOperations.Action.Resume)
    fun deleteDownloads(ids: Set<String>) = execute(ids, DownloadOperations.Action.Delete)
    fun pauseAll() = pauseDownloads(uiState.value.downloads.mapTo(hashSetOf()) { it.id })
    fun resumeAll() = resumeDownloads(uiState.value.downloads.mapTo(hashSetOf()) { it.id })

    private fun execute(ids: Set<String>, action: DownloadOperations.Action) {
        val pending = operations.submit(ids, action)
        backgroundScope.launch {
            val result = pending.await()
            operationFailures.update { it + result.failures.size }
        }
    }
}

private data class LoadState<T>(val value: T? = null, val loading: Boolean = true, val failed: Boolean = false)

private fun <T> Flow<T>.asLoadState(): Flow<LoadState<T>> = flow {
    var latest: T? = null
    emit(LoadState())
    try {
        collect {
            latest = it
            emit(LoadState(it, loading = false))
        }
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        emit(LoadState(latest, loading = false, failed = true))
    }
}
