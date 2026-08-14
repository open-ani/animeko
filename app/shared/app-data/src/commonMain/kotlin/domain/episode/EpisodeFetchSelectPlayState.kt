/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.episode

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import me.him188.ani.app.data.models.episode.displayName
import me.him188.ani.app.data.repository.media.SelectorMediaSourceEpisodeCacheRepository
import me.him188.ani.app.domain.foundation.LoadError
import me.him188.ani.app.domain.media.fetch.MediaFetchSession
import me.him188.ani.app.domain.media.fetch.MediaSourceFetchState
import me.him188.ani.app.domain.media.resolver.toEpisodeMetadata
import me.him188.ani.app.domain.media.selector.MediaSelector
import me.him188.ani.app.domain.mediasource.web.WebSearchEpisodeInfo
import me.him188.ani.app.domain.player.ExtensionException
import me.him188.ani.app.domain.player.PlayerExtensionManager
import me.him188.ani.app.domain.player.VideoLoadingState
import me.him188.ani.app.domain.player.extension.EpisodePlayerExtensionFactory
import me.him188.ani.app.domain.player.extension.ExtensionBackgroundTaskScope
import me.him188.ani.app.domain.player.extension.PlayerExtension
import me.him188.ani.app.domain.player.extension.PlayerExtensionEvent
import me.him188.ani.app.domain.usecase.GlobalKoin
import me.him188.ani.datasources.api.DefaultMedia
import me.him188.ani.datasources.api.source.MediaFetchRequest
import me.him188.ani.datasources.api.source.MediaSourceKind
import me.him188.ani.utils.analytics.Analytics
import me.him188.ani.utils.analytics.AnalyticsEvent.Companion.EpisodeSwitch
import me.him188.ani.utils.logging.info
import me.him188.ani.utils.logging.logger
import org.koin.core.Koin
import org.openani.mediamp.MediampPlayer
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.cancellation.CancellationException


/**
 * 用于管理单个番剧集数（episode）的数据获取、媒体资源选择与播放流程，并在内部协调这些流程的切换和更新。
 *
 * 要查看有关剧集 查询-选择-播放 架构的详细信息，请参阅 PR 文档 [#1439](https://github.com/open-ani/animeko/pull/1439).
 *
 * ### 主要功能
 * - **获取与维护 Episode 数据**：通过 [EpisodeSession] 提供 [SubjectEpisodeInfoBundle]、[MediaFetchSession]、[MediaSelector] 等播放时需要的数据.
 * - **播放器扩展管理**：可在播放流程中加载多个 [EpisodePlayerExtensionFactory] 提供的扩展, 例如自动连播. 详见 [PlayerExtension]
 * - **切换 Episode**：调用 [switchEpisode] 切换到新的 `episodeId`，会关闭旧的 [EpisodeSession] 并重置播放器状态。
 * - **UI 生命周期对接**：在 [onUIReady] 时机启动需要依赖 UI 就绪的后台任务，例如部分扩展初始化。
 *
 * ### 生命周期
 * 1. **初始化**：初始化提供 [isInitialized], 将会为它创建一个 [EpisodeSession]. 但不会立即启动任何后台任务. 需要等待 [onUIReady] 时才会启动.
 * 2. **切换 episode**：在需要切换到新的 episode 时调用 [switchEpisode]。旧的 [EpisodeSession] 及其所有后台协程会被停止，新的 episode 会重新开始资源加载与播放流程。
 *
 * ### 注意 [UnsafeEpisodeSessionApi]
 * 如果在 `combine` 多个 flow 时（例如 [episodeSessionFlow]、[infoBundleFlow]、[mediaFetchSessionFlow] 等），要注意可能会出现数据不一致的情况。
 * 当 [switchEpisode] 被调用后，一些 Flow 可能仍在处理旧的数据或在协程中引用旧的 `episodeId`。若要安全地组合多个 Flow，请务必在同一个 [EpisodeSession] 上进行或参照注解文档 [UnsafeEpisodeSessionApi]。
 */
class EpisodeFetchSelectPlayState(
    val subjectId: Int,
    initialEpisodeId: Int,
    player: MediampPlayer,
    private val backgroundScope: CoroutineScope,
    extensions: List<EpisodePlayerExtensionFactory<*>>,
    private val koin: Koin = GlobalKoin,
    private val sharingStarted: SharingStarted = SharingStarted.WhileSubscribed(),
    private val mainDispatcher: CoroutineContext = Dispatchers.Main.immediate,
    private val analyticsContext: AnalyticsContext = object : AnalyticsContext {},
) {
    interface AnalyticsContext {
        suspend fun isFullscreen(): Boolean? = false
    }

    private val _episodeSessionFlow = MutableStateFlow(
        newEpisodeSession(initialEpisodeId),
    )

    /**
     * A flow of [EpisodeSession].
     * TODO Document
     */
    val episodeSessionFlow: StateFlow<EpisodeSession> = _episodeSessionFlow.asStateFlow()

    val playerSession = PlayerSession(
        player,
        koin,
        mainDispatcher,
    )

    /**
     * 跨 episode 共享的 Web 源剧集链接缓存, 用于切换 episode 时跳过搜索直接播放.
     * @see WebEpisodeLinkCacheExtension
     */
    val webEpisodeLinkCache = WebEpisodeLinkCache()

    private val extensionManager by lazy {
        val intrinsicExtensions = listOf(
            EpisodePlayerExtensionFactory { context, _ ->
                LoadMediaOnSelectExtension { episodeId ->
                    backgroundScope.launch { context.broadcast(MediaLoadedEvent(episodeId)) }
                }
            },
            EpisodePlayerExtensionFactory { _, _ -> WebEpisodeLinkCacheExtension() },
        )

        PlayerExtensionManager(
            intrinsicExtensions + extensions,
            this, koin,
        ) // leaking 'this', but should be fine
    }

    private val switchEpisodeLock = Mutex()

    /**
     * Switch to a new episode.
     *
     * This function flushes all background tasks and starts new ones.
     */
    suspend fun switchEpisode(episodeId: Int) {
        Analytics.recordEvent(
            EpisodeSwitch,
            mapOf(
                "subject_id" to subjectId,
                "episode_id" to episodeId,
                "is_fullscreen" to analyticsContext.isFullscreen(),
            ),
        )

        currentCoroutineContext()[InSwitchEpisode]?.let { element ->
            error(
                "Recursive switchEpisode call detected. " +
                        "You wanted to switch to $episodeId, while you are already switching to ${element.newEpisodeId}.",
            )
        }

        /**
         * Caution: switchEpisode maybe called from a session scope task that was launched from [PlayerExtension.onStart].
         *
         * At step 1 we close the scope. This will cancel all session scope tasks, including the current one running this line of code.
         *
         * So we launch a new coroutine to do the actual work.
         */
        backgroundScope.launch {
            switchEpisodeLock.withLock {
                withContext(InSwitchEpisode(episodeId)) {
                    // 1. 停止上一个 episode 生命周期内的所有后台任务.
                    logger.info { "SwitchEpisode($episodeId): Stopping previous scope" }
                    _episodeSessionFlow.value.sessionScope.coroutineContext.job.cancelAndJoin()

                    // 2. 暂停播放, '冻结'播放器状态. 此时还不能 stop, 因为要调用扩展.
                    logger.info { "SwitchEpisode($episodeId): Pausing player" }
                    withContext(mainDispatcher) {
                        // 按播放意图判断: 缓冲中也应当暂停 (v1 只在 PLAYING 时暂停, 是个缺陷)
                        if (player.state.value.playWhenReady) {
                            player.pause()
                        }
                    }

                    // 3. 调用扩展, 使用旧播放器的状态.
                    logger.info { "SwitchEpisode($episodeId): Calling extension onBeforeSwitchEpisode" }
                    extensionManager.call {
                        it.onBeforeSwitchEpisode(episodeId)
                    }

                    // 4. 停止播放器, 清空播放器状态.
                    logger.info { "SwitchEpisode($episodeId): Stopping player" }
                    playerSession.stopPlayback()

                    // 5. 创建新的 fetchSelectSession
                    logger.info { "SwitchEpisode($episodeId): Propagate newEpisodeSession" }
                    val newSession = newEpisodeSession(episodeId)
                    _episodeSessionFlow.value = newSession

                    // 6. Suspend until background tasks are started.
                    logger.info { "SwitchEpisode($episodeId): Start background tasks" }
                    newSession.startSessionScopeTasks()

                    logger.info { "SwitchEpisode($episodeId): Complete" }
                }
            }
        }.join()
    }

    private fun newEpisodeSession(episodeId: Int) = EpisodeSession(
        subjectId,
        episodeId,
        koin,
        backgroundScope.coroutineContext,
        sharingStarted,
    )

    private val uiReady = CompletableDeferred<Unit>()

    fun onUIReady() {
        uiReady.complete(Unit)

        /**
         * Check if we need to startBackgroundTasks. This is needed, because initial value of [_episodeSessionFlow] does not call startBackgroundTasks.
         */
        episodeSessionFlow.value.let { session ->
            if (!session.sessionScopeTasksStarted.value) {
                backgroundScope.launch {
                    session.startSessionScopeTasks() // Will check again if backgroundTasksStarted so thread-safe.
                }
            }
        }
    }

    /**
     * Called when view model is cleared
     */
    suspend fun onClose() {
        extensionManager.call { it.onClose() }
        playerSession.stopPlayback()
    }

    /**
     * @see EpisodeSession.restartLoad
     */
    fun restartLoad() {
        episodeSessionFlow.value.restartLoad()
    }

    private suspend fun EpisodeSession.startSessionScopeTasks() {
        /**
         * Session-scope tasks are non-stopping, and is not aware of app lifecycle.
         * They must only be launched when the user is viewing the page.
         */
        uiReady.await()

        if (sessionScopeTasksStarted.getAndUpdate { true }) {
            return // already started
        }
        val episodeSession = this

        // We've set backgroundTasksStarted = true, so we must ensure tasks are launched (i.e. this coroutine not cancelled)
        withContext(NonCancellable) {
            // Start all extensions in session's scope.
            extensionManager.call { extension ->
                extension.onStart(episodeSession, ExtensionBackgroundTaskScopeImpl(extension, sessionScope))
            }
        }
    }

    private class ExtensionBackgroundTaskScopeImpl(
        private val extension: PlayerExtension,
        private val scope: CoroutineScope,
    ) : ExtensionBackgroundTaskScope {
        override fun launch(subName: String, block: suspend CoroutineScope.() -> Unit): Job {
            return scope.launch(
                CoroutineName(extension.name + "." + subName),
                start = CoroutineStart.UNDISPATCHED, // TODO
            ) {
                try {
                    block()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    throw ExtensionException(
                        "Unhandled exception in background scope from task '$subName' launched by extension '$extension'",
                        e,
                    )
                }
            }
        }
    }

    /**
     * An intrinsic extension that is automatically and forcefully added to the extension manager.
     *
     * This extension calls [PlayerSession.loadMedia] when a new media is selected.
     */
    private inner class LoadMediaOnSelectExtension(
        private val onMediaLoaded: (episodeId: Int) -> Unit = { }
    ) : PlayerExtension("LoadMediaOnSelect") {
        override fun onStart(episodeSession: EpisodeSession, backgroundTaskScope: ExtensionBackgroundTaskScope) {
            backgroundTaskScope.launch("LoadMediaOnSelect") {
                episodeSessionFlow.collectLatest { episodeSession ->
                    episodeSession.fetchSelectFlow.collectLatest fetchSelect@{ fetchSelect ->
                        if (fetchSelect == null) return@fetchSelect

                        // `filterNotNull()` is needed. Even when media is unselect, we should not stop the player.
                        fetchSelect.mediaSelector.selected.filterNotNull().collectLatest { media ->
                            val episodeInfo = episodeSession.infoBundleFlow
                                .filterNotNull()
                                .first()
                                .episodeInfo

                            playerSession.loadMedia(media, episodeInfo.toEpisodeMetadata())
                            onMediaLoaded(episodeInfo.episodeId)
                        }
                    }
                }
            }
        }
    }

    /**
     * An intrinsic extension that remembers all episode links of the selected web source channel,
     * and fast-selects from that cache when switching episodes.
     *
     * @see WebEpisodeLinkCache
     */
    private inner class WebEpisodeLinkCacheExtension : PlayerExtension("WebEpisodeLinkCache") {
        override fun onStart(episodeSession: EpisodeSession, backgroundTaskScope: ExtensionBackgroundTaskScope) {
            // 快速路径: 新 session 启动时, 若缓存的线路上有当前集的链接,
            // 直接选择合成的 media, 跳过搜索, 直达「匹配视频」阶段.
            // 决策结果写入 EpisodeSession.webFastPathHit: 命中时数据源搜索和自动选择都不会启动.
            backgroundTaskScope.launch("SelectFromCache") {
                var hit = false
                try {
                    if (webEpisodeLinkCache.state.value == null) return@launch
                    val episodeInfo = episodeSession.infoBundleFlow.filterNotNull().first().episodeInfo
                    val media = webEpisodeLinkCache.createMediaFor(
                        episodeInfo.sort, episodeInfo.ep, episodeInfo.displayName,
                    ) ?: return@launch
                    val fetchSelect = episodeSession.fetchSelectFlow.filterNotNull().first()
                    if (fetchSelect.mediaSelector.selected.value != null) return@launch
                    // 先决策再选择: 下游 (summary 等) 观察到 selected 时必须已能看到 hit = true,
                    // 否则会走「查询候选列表」路径触发搜索.
                    hit = true
                    episodeSession.decideWebFastPath(hit = true)
                    logger.info {
                        "WebEpisodeLinkCache: fast-selecting cached link for episode ${episodeInfo.sort}: ${media.originalUrl}"
                    }
                    fetchSelect.mediaSelector.select(media)
                } finally {
                    if (!hit) episodeSession.decideWebFastPath(hit = false)
                }
            }

            // 快速路径命中后, 若有任意数据源开始查询 (例如用户打开了数据源选择器, 其 UI 订阅会触发查询),
            // 解除门控, 让查询保持存活并跑完, 避免 UI 关闭后查询被取消.
            backgroundTaskScope.launch("ReleaseGateOnQueryStart") {
                if (episodeSession.webFastPathHit.filterNotNull().first() != true) return@launch
                val fetchSelect = episodeSession.fetchSelectFlow.filterNotNull().first()
                val results = fetchSelect.mediaFetchSession.mediaSourceResults
                if (results.isEmpty()) return@launch
                combine(results.map { it.state }) { states ->
                    states.any { it !is MediaSourceFetchState.Idle && it !is MediaSourceFetchState.Disabled }
                }.first { it }
                logger.info { "WebEpisodeLinkCache: a media source query has started, releasing the fetch gate" }
                episodeSession.decideWebFastPath(hit = false)
            }

            // 记录选中的 web 线路上的所有剧集链接; 选中非 web 资源时清除缓存.
            backgroundTaskScope.launch("RememberLinks") {
                val repository = koin.getOrNull<SelectorMediaSourceEpisodeCacheRepository>() ?: return@launch
                episodeSession.fetchSelectFlow.collectLatest { fetchSelect ->
                    if (fetchSelect == null) return@collectLatest
                    fetchSelect.mediaSelector.selected.filterNotNull().distinctUntilChanged().collect { media ->
                        if (media.kind != MediaSourceKind.WEB) {
                            webEpisodeLinkCache.invalidate()
                            return@collect
                        }
                        val defaultMedia = media as? DefaultMedia ?: return@collect
                        val request = fetchSelect.mediaFetchSession.request.first()
                        val episodes = findCachedEpisodes(repository, defaultMedia, request) ?: return@collect
                        webEpisodeLinkCache.update(defaultMedia, episodes)
                    }
                }
            }

            // 缓存的链接加载失败时清除缓存, 之后回到完整搜索流程.
            backgroundTaskScope.launch("InvalidateOnFailure") {
                playerSession.videoLoadingState.collect { state ->
                    if (state !is VideoLoadingState.Failed) return@collect
                    if (state is VideoLoadingState.Cancelled) return@collect // 正常切换也会产生 Cancelled
                    val cached = webEpisodeLinkCache.state.value ?: return@collect
                    val fetchSelect = episodeSession.fetchSelectFlow.filterNotNull().first()
                    val selected = fetchSelect.mediaSelector.selected.value ?: return@collect
                    if (selected.mediaId != cached.media.mediaId) return@collect

                    logger.info { "WebEpisodeLinkCache: invalidating cache because loading failed: $state" }
                    webEpisodeLinkCache.invalidate()
                    if (episodeSession.webFastPathHit.value == true) {
                        // 本集是快速路径选中的, 搜索还没启动:
                        // 撤销选择并解除门控 (顺序重要: 自动选择重启时 selected 必须已为 null),
                        // 让自动选择在搜索结果上重新选择
                        // (可能会选到同线路的新链接, 若仍失败则由 SwitchMediaOnPlayerErrorExtension 拉黑换源).
                        fetchSelect.mediaSelector.unselect()
                        episodeSession.decideWebFastPath(hit = false)
                    }
                }
            }
        }

        /**
         * 从 [SelectorMediaSourceEpisodeCacheRepository] (搜索时写入的全量剧集缓存) 中
         * 找到 [media] 所在线路上的所有剧集.
         */
        private suspend fun findCachedEpisodes(
            repository: SelectorMediaSourceEpisodeCacheRepository,
            media: DefaultMedia,
            request: MediaFetchRequest,
        ): List<WebSearchEpisodeInfo>? {
            val pageSubjectName = media.properties.subjectName ?: return null
            val channel = media.properties.alliance.ifEmpty { null }
            for (searchName in request.subjectNames) {
                for (cache in repository.getCache(media.mediaSourceId, searchName)) {
                    if (cache.webSubjectInfo.name != pageSubjectName) continue
                    val episodes = cache.webEpisodeInfos.filter { it.channel == channel && it.episodeSortOrEp != null }
                    if (episodes.isNotEmpty()) return episodes
                }
            }
            return null
        }
    }

    private companion object {
        private val logger = logger<EpisodeFetchSelectPlayState>()
    }

    /**
     * Event of intrinsic [LoadMediaOnSelectExtension] to indicate that the current media is loaded into player.
     */
    class MediaLoadedEvent(val episodeId: Int) : PlayerExtensionEvent
}

/**
 * A flow of the error that occurred during the loading of [infoBundleFlow].
 */
@UnsafeEpisodeSessionApi
val EpisodeFetchSelectPlayState.infoLoadErrorFlow: Flow<LoadError?> get() = episodeSessionFlow.flatMapLatest { it.infoLoadErrorStateFlow }

/**
 * Combined subject- and episode-related details.
 *
 * Flow re-emits (almost immediately) when [episode switches][EpisodeFetchSelectPlayState.switchEpisode].
 *
 * When an error occurs, the flow emits `null`, and the error can be observed from [infoLoadErrorFlow].
 */
@UnsafeEpisodeSessionApi
val EpisodeFetchSelectPlayState.infoBundleFlow get() = episodeSessionFlow.flatMapLatest { it.infoBundleFlow }

@UnsafeEpisodeSessionApi
val EpisodeFetchSelectPlayState.mediaFetchSessionFlow: Flow<MediaFetchSession?>
    get() = episodeSessionFlow.flatMapLatest { it.fetchSelectFlow }.map { it?.mediaFetchSession }

@UnsafeEpisodeSessionApi
val EpisodeFetchSelectPlayState.mediaSelectorFlow: Flow<MediaSelector?>
    get() = episodeSessionFlow.flatMapLatest { it.fetchSelectFlow }.map { it?.mediaSelector }

@UnsafeEpisodeSessionApi
val EpisodeFetchSelectPlayState.episodeIdFlow get() = episodeSessionFlow.map { it.episodeId }

val EpisodeFetchSelectPlayState.player get() = playerSession.player

/**
 * Gets the episodeId at the current moment.
 */
@UnsafeEpisodeSessionApi
suspend fun EpisodeFetchSelectPlayState.getCurrentEpisodeId(): Int {
    return episodeIdFlow.first()
}


/**
 * Marks an API as unsafe to use when collecting from multiple flows marked with this annotation.
 *
 * - If you are collecting a [EpisodeFetchSelectPlayState.episodeSessionFlow] flow, calling this method is safe, and you can opt in [UnsafeEpisodeSessionApi].
 * - If you are collecting a flatmap-ed flow from [EpisodeFetchSelectPlayState.episodeSessionFlow], it's NOT safe to call this method.
 *
 * Example of WRONG use-case:
 * ```
 * episodeSession.flatMapLatest { it.fetchSelectFlow }.collectLatest { fetchSelect ->
 *     fetchSelect.mediaSelectorFlow.filterNotNull().flatMapLatest { it.selected }.collectLatest { media ->
 *         // Selected media has changed, let's save user's preferences!
 *         val episodeId = getCurrentEpisodeId() // WRONG.
 *         savePreference(episodeId, media.mediaProperties)
 *     }
 * }
 * ```
 *
 * In the above example, your code works fine if nobody is calling [EpisodeFetchSelectPlayState.switchEpisode].
 * However, if there do, your `getCurrentEpisodeId()` may receive a new episodeId. So you end up saving the preference for the new episode.
 *
 * A correct way to do this is to `collectLatest` from [EpisodeFetchSelectPlayState.episodeSessionFlow] directly.
 *
 * ```
 * episodeSession.collectLatest { session ->
 *     session.mediaSelectorFlow.flatMapLatest { it.mediaSelector.selected }.collectLatest { media ->
 *         val episodeId = session.episodeId // Correct. We are using the episodeId from the session that you are observing mediaSelectorFlow from.
 *         savePreference(episodeId, media.mediaProperties)
 *     }
 * }
 * ```
 */
@RequiresOptIn(
    message = "This flow API is unsafe for use. When you collect from multiple flows marked with this annotation, you may see inconsistent (old) data from one flow." +
            "You must not combine these unsafe flows. If you need to combine them, use flows from fetchSelectSession",
    level = RequiresOptIn.Level.ERROR,
)
annotation class UnsafeEpisodeSessionApi


/**
 * A context element that indicates that the coroutine is in the process of switching episodes [EpisodeFetchSelectPlayState.switchEpisode].
 */
private class InSwitchEpisode(
    val newEpisodeId: Int,
) : AbstractCoroutineContextElement(InSwitchEpisode) {
    companion object Key : CoroutineContext.Key<InSwitchEpisode>
}
