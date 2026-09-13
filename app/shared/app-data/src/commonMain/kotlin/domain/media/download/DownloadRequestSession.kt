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
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import me.him188.ani.app.data.models.episode.EpisodeInfo
import me.him188.ani.app.data.models.preference.MediaPreference
import me.him188.ani.app.data.models.subject.SubjectInfo
import me.him188.ani.app.data.repository.media.EpisodePreferencesRepository
import me.him188.ani.app.data.repository.subject.SubjectCollectionRepository
import me.him188.ani.app.domain.media.fetch.MediaFetchSession
import me.him188.ani.app.domain.media.fetch.MediaSourceManager
import me.him188.ani.app.domain.media.fetch.create
import me.him188.ani.app.domain.media.fetch.createFetchFetchSession
import me.him188.ani.app.domain.media.selector.MediaSelector
import me.him188.ani.app.domain.media.selector.MediaSelectorFactory
import me.him188.ani.datasources.api.Media
import me.him188.ani.datasources.api.MediaCacheMetadata
import me.him188.ani.datasources.api.source.MediaFetchRequest
import me.him188.ani.datasources.api.topic.contains
import me.him188.ani.datasources.api.topic.isSingleEpisode
import me.him188.ani.datasources.api.unwrapCached
import me.him188.ani.utils.coroutines.childScope
import me.him188.ani.utils.logging.logger
import me.him188.ani.utils.logging.warn

/**
 * [DownloadRequestSession] 的状态.
 */
sealed interface DownloadRequestState {
    /**
     * 尚未完成的剧集, 按请求顺序; 结束后为空.
     */
    val pendingEpisodeIds: List<Int>

    sealed interface Working : DownloadRequestState {
        /**
         * 正在处理的剧集.
         */
        val episodeId: Int
    }

    /**
     * 正在加载条目与剧集信息, 并检查可复用的合集下载.
     */
    data class Preparing(
        override val episodeId: Int,
        override val pendingEpisodeIds: List<Int>,
    ) : Working

    /**
     * 等待用户通过 [DownloadRequestSession.select] 选定资源; 查询持续进行, 与弹窗是否可见无关.
     */
    class AwaitingSelection internal constructor(
        override val episodeId: Int,
        override val pendingEpisodeIds: List<Int>,
        val fetchSession: MediaFetchSession,
        val selector: MediaSelector,
        internal val choice: CompletableDeferred<Media>,
    ) : Working

    /**
     * 正在持久化.
     */
    data class Creating(
        override val episodeId: Int,
        override val pendingEpisodeIds: List<Int>,
    ) : Working

    /**
     * [error] 非 `null` 表示在某一集失败并中止, 已创建的下载保留; 为 `null` 表示完成或被取消.
     */
    data class Finished(val error: Throwable? = null) : DownloadRequestState {
        override val pendingEpisodeIds: List<Int> get() = emptyList()
    }
}

/**
 * 为一个条目的若干剧集添加下载: 逐集处理, 一集持久化完成后才处理下一集, 任何一步失败都结束会话.
 * 每集先尝试复用本条目已有的合集资源, 否则查询并等待用户选源, 保存偏好后通过 [AddDownloadUseCase] 持久化.
 *
 * 会话随父作用域取消; 已开始持久化的那一集在应用作用域中继续完成. [select] 与 [cancel] 可在任意线程调用.
 */
class DownloadRequestSession internal constructor(
    val subjectId: Int,
    episodeIds: List<Int>,
    private val subjects: SubjectCollectionRepository,
    private val preferences: EpisodePreferencesRepository,
    private val sources: MediaSourceManager,
    private val selectors: MediaSelectorFactory,
    private val downloadManager: MediaDownloadManager,
    private val addDownload: AddDownloadUseCase,
    parentScope: CoroutineScope,
) {
    val episodeIds: List<Int> = episodeIds.distinct().also {
        require(it.isNotEmpty()) { "episodeIds must not be empty" }
    }

    private val scope = parentScope.childScope()
    private val started = atomic(false)
    private val mutableState = MutableStateFlow<DownloadRequestState>(
        DownloadRequestState.Preparing(this.episodeIds.first(), this.episodeIds),
    )
    val state: StateFlow<DownloadRequestState> = mutableState.asStateFlow()

    /**
     * 本会话已创建下载的资源. [MediaDownloadManager.downloads] 是异步聚合的, 复用检查时一并参考.
     */
    private val createdMedia = mutableListOf<Media>()

    init {
        // 作用域结束时状态收敛到 Finished, 包括尚未 start 就被取消的情况.
        scope.coroutineContext.job.invokeOnCompletion { finish(error = null) }
    }

    /**
     * 只能调用一次.
     */
    fun start() {
        check(started.compareAndSet(expect = false, update = true)) { "Session has already been started" }
        scope.launch { run() }
    }

    /**
     * 只在等待该集选源时生效.
     * @return 是否接受了本次选择
     */
    fun select(episodeId: Int, media: Media): Boolean {
        val current = state.value as? DownloadRequestState.AwaitingSelection ?: return false
        if (current.episodeId != episodeId) return false
        return current.choice.complete(media)
    }

    /**
     * 取消查询、停止等待并跳过剩余剧集; 正在持久化的那一集继续完成.
     */
    fun cancel() {
        scope.cancel()
        finish(error = null)
    }

    private fun finish(error: Throwable?) {
        mutableState.update { if (it is DownloadRequestState.Finished) it else DownloadRequestState.Finished(error) }
    }

    private suspend fun run() {
        try {
            val remaining = ArrayDeque(episodeIds)
            while (remaining.isNotEmpty()) {
                processEpisode(remaining.first(), remaining.toList())
                remaining.removeFirst()
            }
            finish(error = null)
        } catch (e: CancellationException) {
            finish(error = null)
            throw e
        } catch (e: Exception) {
            logger.warn(e) { "Download request for subject $subjectId stopped at episode ${state.value.pendingEpisodeIds.firstOrNull()}" }
            finish(e)
        } finally {
            scope.cancel()
        }
    }

    private suspend fun processEpisode(episodeId: Int, pending: List<Int>) {
        mutableState.value = DownloadRequestState.Preparing(episodeId, pending)
        val collection = subjects.subjectCollectionFlow(subjectId).first()
        val subject = collection.subjectInfo
        val episode = collection.episodes.firstOrNull { it.episodeId == episodeId }?.episodeInfo
            ?: throw NoSuchElementException("Episode $episodeId is not in subject $subjectId")
        val existing = downloadManager.downloadsForSubject(subjectId).first().map { it.origin }
        val media = findReusableSeasonMedia(episode, existing + createdMedia)
            ?: awaitSelection(episodeId, pending, subject, episode)

        mutableState.value = DownloadRequestState.Creating(episodeId, pending)
        val metadata = MediaCacheMetadata(MediaFetchRequest.create(subject, episode))
        // 持久化交给应用作用域, 会话取消时这一集仍会完成; 结果以 Result 传回, 失败不取消应用作用域.
        downloadManager.backgroundScope
            .async { runCatching { addDownload(subject, episode, media, metadata) } }
            .await()
            .getOrThrow()
        createdMedia += media
    }

    private suspend fun awaitSelection(
        episodeId: Int,
        pending: List<Int>,
        subject: SubjectInfo,
        episode: EpisodeInfo,
    ): Media = coroutineScope {
        val fetchSession = sources.createFetchFetchSession(flowOf(MediaFetchRequest.create(subject, episode)))
        val selector = selectors.create(subjectId, episodeId, fetchSession.cumulativeResults)
        // 保持查询进行, 与弹窗是否可见无关.
        launch { fetchSession.cumulativeResults.collect() }
        // 记录弹窗内的偏好变更, 确定资源后一并保存.
        val latestPreference = MutableStateFlow<MediaPreference?>(null)
        launch(start = CoroutineStart.UNDISPATCHED) {
            selector.events.onChangePreference.collect { latestPreference.value = it }
        }

        val choice = CompletableDeferred<Media>()
        mutableState.value = DownloadRequestState.AwaitingSelection(episodeId, pending, fetchSession, selector, choice)
        try {
            val media = choice.await()
            mutableState.value = DownloadRequestState.Creating(episodeId, pending)
            selectAndSavePreference(selector, media, latestPreference)
            media
        } finally {
            coroutineContext.cancelChildren()
        }
    }

    /**
     * 先订阅事件再选择, 拿到本次选择的偏好并在提交前保存; 没有新事件时沿用弹窗内最近的偏好.
     */
    private suspend fun selectAndSavePreference(
        selector: MediaSelector,
        media: Media,
        latest: StateFlow<MediaPreference?>,
    ) = coroutineScope {
        val broadcast = async(start = CoroutineStart.UNDISPATCHED) { selector.events.onChangePreference.first() }
        val preference = if (selector.select(media)) {
            withTimeoutOrNull(PREFERENCE_BROADCAST_TIMEOUT) { broadcast.await() } ?: latest.value
        } else {
            latest.value
        }
        broadcast.cancel()
        preference?.let { preferences.setMediaPreference(subjectId, it) }
    }

    private companion object {
        private val logger = logger<DownloadRequestSession>()
        private val PREFERENCE_BROADCAST_TIMEOUT = 5.seconds
    }
}

/**
 * 创建 [DownloadRequestSession]; 会话随 [create] 传入的父作用域取消.
 */
class DownloadRequestSessionFactory(
    private val subjects: SubjectCollectionRepository,
    private val preferences: EpisodePreferencesRepository,
    private val sources: MediaSourceManager,
    private val selectors: MediaSelectorFactory,
    private val downloadManager: MediaDownloadManager,
    private val addDownload: AddDownloadUseCase,
) {
    /**
     * 创建会话但不开始处理.
     */
    fun create(subjectId: Int, episodeIds: List<Int>, parentScope: CoroutineScope): DownloadRequestSession =
        DownloadRequestSession(
            subjectId, episodeIds,
            subjects, preferences, sources, selectors, downloadManager, addDownload,
            parentScope,
        )
}

/**
 * 在 [candidates] 中寻找 [Media.episodeRange] 覆盖 [episode] 的合集资源, 单集资源不复用.
 */
internal fun findReusableSeasonMedia(episode: EpisodeInfo, candidates: List<Media>): Media? =
    candidates.firstOrNull { media ->
        val range = media.episodeRange ?: return@firstOrNull false
        !range.isSingleEpisode() &&
                (episode.ep?.let { range.contains(it) } == true || range.contains(episode.sort))
    }?.unwrapCached()
