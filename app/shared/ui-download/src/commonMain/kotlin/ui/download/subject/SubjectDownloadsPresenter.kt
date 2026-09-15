/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.download.subject

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import me.him188.ani.app.data.models.subject.SubjectCollectionInfo
import me.him188.ani.app.data.models.subject.nameCnOrName
import me.him188.ani.app.data.repository.player.EpisodePlayHistoryRepository
import me.him188.ani.app.data.repository.subject.SubjectCollectionRepository
import me.him188.ani.app.data.repository.user.SettingsRepository
import me.him188.ani.app.domain.media.download.DownloadOperation
import me.him188.ani.app.domain.media.download.DownloadOperations
import me.him188.ani.app.domain.media.download.DownloadRequestSession
import me.him188.ani.app.domain.media.download.DownloadRequestSessionFactory
import me.him188.ani.app.domain.media.download.DownloadRequestState
import me.him188.ani.app.domain.media.download.DownloadSnapshot
import me.him188.ani.app.domain.media.download.MediaDownloadManager
import me.him188.ani.app.domain.media.fetch.MediaSourceManager
import me.him188.ani.app.ui.download.DownloadOperationRunner
import me.him188.ani.app.ui.download.components.toDownloadItem
import me.him188.ani.app.ui.mediafetch.MediaSourceInfoProvider
import me.him188.ani.datasources.api.Media
import me.him188.ani.utils.coroutines.childScope

/**
 * 一个条目的下载页状态与操作, 同一时刻至多持有一个 [DownloadRequestSession].
 *
 * 由页面 ViewModel 创建, [close] 取消其作用域与进行中的选源会话; 已交给应用作用域的持久化与批量操作不受影响.
 *
 * @param initialTitle 已知的条目名, 在条目信息加载完成前作为标题.
 */
class SubjectDownloadsPresenter(
    val subjectId: Int,
    parentScope: CoroutineScope,
    subjects: SubjectCollectionRepository,
    histories: EpisodePlayHistoryRepository,
    settings: SettingsRepository,
    sources: MediaSourceManager,
    downloadManager: MediaDownloadManager,
    private val sessionFactory: DownloadRequestSessionFactory,
    operations: DownloadOperations,
    initialTitle: String? = null,
) : AutoCloseable {
    private val scope = parentScope.childScope()
    private val reloadCount = MutableStateFlow(0)
    private val operationRunner = DownloadOperationRunner(operations, scope)
    private val session = MutableStateFlow<DownloadRequestSession?>(null)
    private val requestState: Flow<DownloadRequestState?> = session.flatMapLatest { it?.state ?: flowOf(null) }

    val selectorSettings = settings.mediaSelectorSettings.flow
    val sourceInfoProvider = MediaSourceInfoProvider(sources::infoFlowByMediaSourceId)

    /**
     * 在订阅之外保留加载结果, 重新订阅时不回到加载中.
     */
    private val subjectLoad = MutableStateFlow(LoadState<SubjectCollectionInfo>())
    private val downloadsLoad = MutableStateFlow(LoadState<List<DownloadSnapshot>>())

    private val subject = reloadCount.flatMapLatest { subjects.subjectCollectionFlow(subjectId).asLoadState(subjectLoad) }
    private val downloads = reloadCount.flatMapLatest { downloadManager.snapshots(subjectId).asLoadState(downloadsLoad) }

    val uiState: StateFlow<SubjectDownloadsUiState> =
        combine(subject, downloads, histories.flow, requestState) { subject, downloads, histories, request ->
            val info = subject.value
            val historyByEpisode = histories.associateBy { it.episodeId }
            val items = downloads.value.orEmpty().map { snapshot ->
                val history = snapshot.metadata.episodeId.toIntOrNull()?.let { historyByEpisode[it] }
                snapshot.toDownloadItem(info?.collectionType, history)
            }
            SubjectDownloadsUiState(
                title = info?.subjectInfo?.nameCnOrName ?: initialTitle,
                items = buildSubjectDownloadItems(info?.downloadEpisodes().orEmpty(), items),
                downloads = items,
                totalEpisodes = info?.episodes?.size,
                episodesLoading = subject.loading,
                downloadsLoading = downloads.loading,
                episodesFailed = subject.failed,
                downloadsFailed = downloads.failed,
                request = request.toRequestUiState(),
            )
        }.stateIn(scope, SharingStarted.WhileSubscribed(5000), SubjectDownloadsUiState(title = initialTitle))

    /**
     * 同一个等待选源状态始终对应同一个 [DownloadMediaPickerState], 供 UI 作为 key; 离开该状态后清空.
     */
    private val currentPicker = MutableStateFlow<Pair<DownloadRequestState.AwaitingSelection, DownloadMediaPickerState>?>(null)

    /**
     * `null` 表示没有需要展示的弹窗.
     */
    val requestDialogs: StateFlow<DownloadRequestDialogState?> = requestState
        .map { it.toDialogState() }
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), null)

    /**
     * 加载期间保留已有的值.
     */
    fun reload() {
        subjectLoad.update { it.copy(loading = true, failed = false) }
        downloadsLoad.update { it.copy(loading = true, failed = false) }
        reloadCount.update { it + 1 }
    }

    /**
     * 批量操作累计的失败数, [dismissOperationFailures] 后归零.
     */
    val operationFailures: StateFlow<Int> get() = operationRunner.failedCount

    fun dismissOperationFailures() = operationRunner.dismissFailures()

    /**
     * 正在等待其他剧集选源时取消该会话并为本集重新开启; 正在等待本集选源、准备或持久化时不做任何事.
     * @return 是否开启了新会话
     */
    fun requestDownload(episodeId: Int): Boolean {
        val current = session.value
        when (val state = current?.state?.value) {
            is DownloadRequestState.AwaitingSelection -> {
                if (state.episodeId == episodeId) return false
                current?.cancel()
            }

            is DownloadRequestState.Working -> return false
            null, is DownloadRequestState.Finished -> Unit
        }
        val created = sessionFactory.create(subjectId, listOf(episodeId), scope)
        session.value = created
        created.start()
        return true
    }

    fun cancelRequest() {
        session.getAndUpdate { null }?.cancel()
    }

    fun selectMedia(episodeId: Int, media: Media) {
        session.value?.select(episodeId, media)
    }

    fun pauseDownloads(ids: Set<String>) = operationRunner.run(ids, DownloadOperation.Pause)
    fun resumeDownloads(ids: Set<String>) = operationRunner.run(ids, DownloadOperation.Resume)
    fun deleteDownloads(ids: Set<String>) = operationRunner.run(ids, DownloadOperation.Delete)
    fun pauseAll() = pauseDownloads(uiState.value.downloads.mapTo(hashSetOf()) { it.id })
    fun resumeAll() = resumeDownloads(uiState.value.downloads.mapTo(hashSetOf()) { it.id })

    val isClosed: Boolean get() = !scope.isActive

    /**
     * 取消进行中的选源会话与状态共享; 已交给应用作用域的工作继续完成.
     */
    override fun close() {
        scope.cancel()
    }

    private fun DownloadRequestState?.toDialogState(): DownloadRequestDialogState? {
        if (this !is DownloadRequestState.AwaitingSelection) currentPicker.value = null
        return when (this) {
            null -> null
            is DownloadRequestState.AwaitingSelection -> DownloadRequestDialogState(selection = pickerFor(this))
            is DownloadRequestState.Finished -> if (error == null) null else DownloadRequestDialogState(failed = true)
            is DownloadRequestState.Preparing, is DownloadRequestState.Creating -> DownloadRequestDialogState()
        }
    }

    private fun pickerFor(state: DownloadRequestState.AwaitingSelection): DownloadMediaPickerState {
        currentPicker.value?.let { (key, picker) -> if (key === state) return picker }
        return DownloadMediaPickerState(state.episodeId, state.fetchSession, state.selector)
            .also { currentPicker.value = state to it }
    }
}

private fun DownloadRequestState?.toRequestUiState() = DownloadRequestUiState(
    episodeIds = this?.pendingEpisodeIds?.toSet().orEmpty(),
    busy = this is DownloadRequestState.Preparing || this is DownloadRequestState.Creating,
    canCancel = this != null && this !is DownloadRequestState.Finished,
)

/**
 * 首个值到达前 [loading] 为 `true`; 上游异常时 [failed] 为 `true` 并保留最后一次成功的值.
 */
private data class LoadState<T>(val value: T? = null, val loading: Boolean = true, val failed: Boolean = false)

/**
 * 以 [holder] 的当前值开始, 收到新值或失败时更新 [holder].
 */
private fun <T> Flow<T>.asLoadState(holder: MutableStateFlow<LoadState<T>>): Flow<LoadState<T>> = flow {
    emit(holder.value)
    try {
        collect { value -> emit(holder.updateAndGet { LoadState(value, loading = false) }) }
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        emit(holder.updateAndGet { it.copy(loading = false, failed = true) })
    }
}

/**
 * 供页面 ViewModel 按条目创建 [SubjectDownloadsPresenter].
 */
class SubjectDownloadsPresenterFactory(
    private val subjects: SubjectCollectionRepository,
    private val histories: EpisodePlayHistoryRepository,
    private val settings: SettingsRepository,
    private val sources: MediaSourceManager,
    private val downloadManager: MediaDownloadManager,
    private val sessionFactory: DownloadRequestSessionFactory,
    private val operations: DownloadOperations,
) {
    fun create(subjectId: Int, parentScope: CoroutineScope, initialTitle: String? = null): SubjectDownloadsPresenter =
        SubjectDownloadsPresenter(
            subjectId, parentScope, subjects, histories, settings, sources, downloadManager, sessionFactory, operations,
            initialTitle,
        )
}
