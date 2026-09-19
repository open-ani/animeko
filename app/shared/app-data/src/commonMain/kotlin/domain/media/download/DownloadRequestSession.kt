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
import me.him188.ani.app.data.models.episode.displayName
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
import me.him188.ani.datasources.api.EpisodeSort
import me.him188.ani.datasources.api.Media
import me.him188.ani.datasources.api.MediaCacheMetadata
import me.him188.ani.datasources.api.isLocalCache
import me.him188.ani.datasources.api.source.MediaFetchRequest
import me.him188.ani.datasources.api.source.MediaSourceKind
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
     * 尚未完成的剧集, 按处理顺序; 结束后为空.
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
     * 用户已选定资源 [chosen], 等待通过 [DownloadRequestSession.confirmEpisodes] 勾选要一并下载的集,
     * 或通过 [DownloadRequestSession.backToSelection] 回到选源. [fetchSession] 与 [selector] 与选源时相同, 不重建.
     *
     * 只有 [chosen] 所属的线路 (数据源与字幕组) 还能覆盖当前集以外的集时才进入此状态.
     */
    class SelectingEpisodes internal constructor(
        override val episodeId: Int,
        override val pendingEpisodeIds: List<Int>,
        val fetchSession: MediaFetchSession,
        val selector: MediaSelector,
        val chosen: Media,
        /**
         * 条目的全部剧集及其在该线路上的处置, 按剧集顺序.
         */
        val options: List<DownloadEpisodeOption>,
        internal val decision: CompletableDeferred<Set<Int>?>,
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
 * 选集时条目的一集.
 */
data class DownloadEpisodeOption(
    val episodeId: Int,
    val sort: EpisodeSort,
    val name: String,
    val availability: Availability,
    /**
     * 将要使用的资源名: 网页源为源上的集名 (如 "第03集"), BT 源为种子标题. 不可下载时为 `null`.
     */
    val resourceTitle: String?,
    /**
     * 是否为发起下载的那一集.
     */
    val isCurrent: Boolean,
) {
    enum class Availability {
        AVAILABLE,
        ALREADY_DOWNLOADED,
        UNMATCHED,
    }
}

/**
 * 为一个条目的若干剧集添加下载: 逐集处理, 一集持久化完成后才处理下一集, 任何一步失败都结束会话.
 * 每集先尝试复用本条目已有的合集资源, 否则查询并等待用户选源; 用户选定资源后, 若该线路还能覆盖其他集,
 * 进入选集 ([DownloadRequestState.SelectingEpisodes]), 确认后按 [planBatchDownload] 为勾选的集逐集创建记录,
 * 偏好只保存一次. 已创建的集不再重复处理.
 *
 * 会话随父作用域取消; 已开始持久化的那一集在应用作用域中继续完成. 所有操作可在任意线程调用.
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
     * 本会话已创建的下载. [MediaDownloadManager.downloads] 是异步聚合的, 复用检查时一并参考.
     */
    private val created = mutableListOf<ExistingDownload>()

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
     * 只在选集时生效. [episodeIds] 中不可下载的集被忽略, 发起下载的那一集总会创建.
     * @return 是否接受了本次确认
     */
    fun confirmEpisodes(episodeIds: Set<Int>): Boolean {
        val current = state.value as? DownloadRequestState.SelectingEpisodes ?: return false
        return current.decision.complete(episodeIds)
    }

    /**
     * 只在选集时生效: 回到选源, 查询会话不重建.
     * @return 是否接受了本次操作
     */
    fun backToSelection(): Boolean {
        val current = state.value as? DownloadRequestState.SelectingEpisodes ?: return false
        return current.decision.complete(null)
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
                val handled = processEpisode(remaining.first(), remaining.toList())
                remaining.removeAll { it in handled }
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

    /**
     * @return 已创建记录的剧集, 总是包含 [episodeId].
     */
    private suspend fun processEpisode(episodeId: Int, pending: List<Int>): Set<Int> {
        mutableState.value = DownloadRequestState.Preparing(episodeId, pending)
        val collection = subjects.subjectCollectionFlow(subjectId).first()
        val subject = collection.subjectInfo
        val episodes = collection.episodes.map { it.episodeInfo }
        val episode = episodes.firstOrNull { it.episodeId == episodeId }
            ?: throw NoSuchElementException("Episode $episodeId is not in subject $subjectId")
        val existing = existingDownloads()
        findReusableSeasonMedia(episode, existing.map { it.origin })?.let { media ->
            createAll(subject, listOf(episode to media), pending)
            return setOf(episodeId)
        }

        val batch = awaitSelection(episodeId, pending, subject, episode, episodes, existing)
        // 发起下载的那一集已有记录时不在这一批里, 也算处理完, 否则会反复回到选源
        return createAll(subject, batch, pending) + episodeId
    }

    /**
     * 会话结束后状态保持 [DownloadRequestState.Finished]: 交给应用作用域的持久化在取消后仍在进行, 它的状态写入被忽略.
     */
    private fun setStateUnlessFinished(state: DownloadRequestState) {
        mutableState.update { if (it is DownloadRequestState.Finished) it else state }
    }

    /**
     * 依次持久化 [batch]. 整批交给应用作用域, 会话在此期间被取消 (如离开页面) 时已确认的这一批仍会全部完成;
     * 某一集失败时停止, 失败不取消应用作用域.
     *
     * @return 已创建记录的集
     */
    private suspend fun createAll(
        subject: SubjectInfo,
        batch: List<Pair<EpisodeInfo, Media>>,
        pending: List<Int>,
    ): Set<Int> {
        val batchIds = batch.map { (target, _) -> target.episodeId }
        return downloadManager.backgroundScope.async {
            runCatching {
                val handled = mutableSetOf<Int>()
                for ((target, media) in batch) {
                    val pendingNow = batchIds.filter { it !in handled } + pending.filter { it !in batchIds }
                    setStateUnlessFinished(DownloadRequestState.Creating(target.episodeId, pendingNow))
                    addDownload(subject, target, media, MediaCacheMetadata(MediaFetchRequest.create(subject, target)))
                    created += ExistingDownload(media, target.episodeId)
                    handled += target.episodeId
                }
                handled
            }
        }.await().getOrThrow()
    }

    private suspend fun existingDownloads(): List<ExistingDownload> =
        downloadManager.downloadsForSubject(subjectId).first().mapNotNull { download ->
            download.metadata.episodeId.toIntOrNull()?.let { ExistingDownload(download.origin, it) }
        } + created

    /**
     * 查询并等待用户选源与选集.
     *
     * @return 要创建的记录, 发起下载的那一集在前, 其余按剧集顺序.
     */
    private suspend fun awaitSelection(
        episodeId: Int,
        pending: List<Int>,
        subject: SubjectInfo,
        episode: EpisodeInfo,
        episodes: List<EpisodeInfo>,
        existing: List<ExistingDownload>,
    ): List<Pair<EpisodeInfo, Media>> = coroutineScope {
        val fetchSession = sources.createFetchFetchSession(flowOf(MediaFetchRequest.create(subject, episode, episodes)))
        val selector = selectors.create(subjectId, episodeId, fetchSession.cumulativeResults, fetchRequest = fetchSession.latestRequest)
        // 保持查询进行, 与弹窗是否可见无关.
        launch { fetchSession.cumulativeResults.collect() }
        // 记录弹窗内的偏好变更, 确定资源后一并保存.
        val latestPreference = MutableStateFlow<MediaPreference?>(null)
        launch(start = CoroutineStart.UNDISPATCHED) {
            selector.events.onChangePreference.collect { latestPreference.value = it }
        }

        try {
            while (true) {
                val choice = CompletableDeferred<Media>()
                mutableState.value =
                    DownloadRequestState.AwaitingSelection(episodeId, pending, fetchSession, selector, choice)
                val chosen = choice.await()

                // 同一线路 (数据源 + 字幕组 + 条目名) 的条目级候选; 预览按全部集规划.
                // BT 源的结果不按条目名过滤, 同字幕组的其他条目 (如 "坂本日常" 之于 "日常") 靠标题里的条目名与所选资源或条目一致排除.
                val chosenNames = chosen.lineSubjectNames()
                val group = selector.subjectCandidates.first()
                    .mapNotNull { it.result }
                    .filter { !it.isLocalCache() && it.isSameLineAs(chosen, chosenNames, subject.allNames) }
                val preview = planBatchDownload(episodes, group, existing, pinned = chosen, pinnedEpisodeId = episodeId)
                val options = episodes.map { it.toOption(preview.getValue(it.episodeId), isCurrent = it.episodeId == episodeId) }

                // 该线路只覆盖当前这一话时不需要选集, 与单集下载相同; 这一话已有记录时什么都不创建.
                if (options.none { !it.isCurrent && it.availability == DownloadEpisodeOption.Availability.AVAILABLE }) {
                    mutableState.value = DownloadRequestState.Creating(episodeId, pending)
                    selectAndSavePreference(selector, chosen, latestPreference)
                    return@coroutineScope listOfNotNull(preview.getValue(episodeId).mediaOrNull?.let { episode to it })
                }

                val decision = CompletableDeferred<Set<Int>?>()
                mutableState.value = DownloadRequestState.SelectingEpisodes(
                    episodeId, pending, fetchSession, selector, chosen, options, decision,
                )
                val picked = decision.await() ?: continue // 返回选源

                // 确认时只按所选集规划, 合集与单集的取舍可能与预览不同.
                val targets = listOf(episode) + episodes.filter { it.episodeId != episodeId && it.episodeId in picked }
                val plan = planBatchDownload(targets, group, existing, pinned = chosen, pinnedEpisodeId = episodeId)
                val batch = targets.mapNotNull { target ->
                    plan.getValue(target.episodeId).mediaOrNull?.let { target to it }
                }
                val batchIds = batch.map { (target, _) -> target.episodeId }
                mutableState.value = DownloadRequestState.Creating(episodeId, batchIds + pending.filter { it !in batchIds })
                selectAndSavePreference(selector, chosen, latestPreference)
                return@coroutineScope batch
            }
            @Suppress("UNREACHABLE_CODE")
            error("unreachable")
        } finally {
            coroutineContext.cancelChildren()
        }
    }

    private fun EpisodeInfo.toOption(plan: EpisodeDownloadPlan, isCurrent: Boolean): DownloadEpisodeOption {
        val media = plan.mediaOrNull
        return DownloadEpisodeOption(
            episodeId = episodeId,
            sort = sort,
            name = displayName,
            availability = when (plan) {
                EpisodeDownloadPlan.AlreadyDownloaded -> DownloadEpisodeOption.Availability.ALREADY_DOWNLOADED
                EpisodeDownloadPlan.Uncovered -> DownloadEpisodeOption.Availability.UNMATCHED
                is EpisodeDownloadPlan.Create, is EpisodeDownloadPlan.Reuse -> DownloadEpisodeOption.Availability.AVAILABLE
            },
            resourceTitle = media?.let {
                if (it.kind == MediaSourceKind.WEB) it.properties.episodeName ?: it.originalTitle else it.originalTitle
            },
            isCurrent = isCurrent,
        )
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
