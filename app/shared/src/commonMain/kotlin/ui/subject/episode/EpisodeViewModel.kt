/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.subject.episode

import androidx.annotation.MainThread
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.paging.cachedIn
import androidx.paging.map
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.retry
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.him188.ani.app.data.models.episode.renderEpisodeEp
import me.him188.ani.app.data.models.preference.VideoScaffoldConfig
import me.him188.ani.app.data.models.subject.SubjectInfo
import me.him188.ani.app.data.models.subject.SubjectProgressInfo
import me.him188.ani.app.data.repository.episode.BangumiCommentRepository
import me.him188.ani.app.data.repository.episode.EpisodeCollectionRepository
import me.him188.ani.app.data.repository.media.EpisodePreferencesRepository
import me.him188.ani.app.data.repository.player.DanmakuRegexFilterRepository
import me.him188.ani.app.data.repository.player.EpisodePlayHistoryRepository
import me.him188.ani.app.data.repository.subject.SubjectCollectionRepository
import me.him188.ani.app.data.repository.user.SettingsRepository
import me.him188.ani.app.domain.danmaku.DanmakuManager
import me.him188.ani.app.domain.episode.EpisodeFetchPlayState
import me.him188.ani.app.domain.foundation.LoadError
import me.him188.ani.app.domain.media.cache.EpisodeCacheStatus
import me.him188.ani.app.domain.media.cache.MediaCacheManager
import me.him188.ani.app.domain.media.fetch.FilteredMediaSourceResults
import me.him188.ani.app.domain.media.fetch.MediaSourceManager
import me.him188.ani.app.domain.media.resolver.MediaResolver
import me.him188.ani.app.domain.player.CacheProgressProvider
import me.him188.ani.app.domain.player.SavePlayProgressUseCase
import me.him188.ani.app.domain.session.AuthState
import me.him188.ani.app.platform.Context
import me.him188.ani.app.ui.comment.BangumiCommentSticker
import me.him188.ani.app.ui.comment.CommentContext
import me.him188.ani.app.ui.comment.CommentEditorState
import me.him188.ani.app.ui.comment.CommentMapperContext
import me.him188.ani.app.ui.comment.CommentMapperContext.parseToUIComment
import me.him188.ani.app.ui.comment.CommentState
import me.him188.ani.app.ui.comment.EditCommentSticker
import me.him188.ani.app.ui.comment.TurnstileState
import me.him188.ani.app.ui.comment.reloadAndGetToken
import me.him188.ani.app.ui.foundation.AbstractViewModel
import me.him188.ani.app.ui.foundation.AuthState
import me.him188.ani.app.ui.foundation.HasBackgroundScope
import me.him188.ani.app.ui.foundation.launchInBackground
import me.him188.ani.app.ui.foundation.launchInMain
import me.him188.ani.app.ui.foundation.stateOf
import me.him188.ani.app.ui.settings.danmaku.DanmakuRegexFilterState
import me.him188.ani.app.ui.subject.AiringLabelState
import me.him188.ani.app.ui.subject.collection.components.EditableSubjectCollectionTypeState
import me.him188.ani.app.ui.subject.details.state.SubjectDetailsStateFactory
import me.him188.ani.app.ui.subject.details.state.SubjectDetailsStateLoader
import me.him188.ani.app.ui.subject.episode.danmaku.PlayerDanmakuState
import me.him188.ani.app.ui.subject.episode.details.EpisodeCarouselState
import me.him188.ani.app.ui.subject.episode.details.EpisodeDetailsState
import me.him188.ani.app.ui.subject.episode.mediaFetch.MediaSelectorState
import me.him188.ani.app.ui.subject.episode.mediaFetch.MediaSourceInfoProvider
import me.him188.ani.app.ui.subject.episode.mediaFetch.MediaSourceResultsPresentation
import me.him188.ani.app.ui.subject.episode.statistics.VideoStatistics
import me.him188.ani.app.ui.subject.episode.statistics.VideoStatisticsCollector
import me.him188.ani.app.ui.subject.episode.video.DanmakuStatistics
import me.him188.ani.app.ui.subject.episode.video.PlayerSkipOpEdState
import me.him188.ani.app.ui.subject.episode.video.sidesheet.EpisodeSelectorState
import me.him188.ani.app.videoplayer.ui.ControllerVisibility
import me.him188.ani.app.videoplayer.ui.PlayerControllerState
import me.him188.ani.danmaku.api.Danmaku
import me.him188.ani.danmaku.api.DanmakuEvent
import me.him188.ani.danmaku.api.DanmakuPresentation
import me.him188.ani.danmaku.ui.DanmakuConfig
import me.him188.ani.datasources.api.PackedDate
import me.him188.ani.datasources.api.topic.UnifiedCollectionType
import me.him188.ani.datasources.api.topic.isDoneOrDropped
import me.him188.ani.utils.coroutines.cancellableCoroutineScope
import me.him188.ani.utils.coroutines.flows.FlowRestarter
import me.him188.ani.utils.coroutines.flows.flowOfEmptyList
import me.him188.ani.utils.coroutines.flows.restartable
import me.him188.ani.utils.coroutines.retryWithBackoffDelay
import me.him188.ani.utils.coroutines.sampleWithInitial
import me.him188.ani.utils.logging.error
import me.him188.ani.utils.logging.info
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.openani.mediamp.MediampPlayer
import org.openani.mediamp.MediampPlayerFactory
import org.openani.mediamp.PlaybackState
import org.openani.mediamp.features.chapters
import kotlin.time.Duration.Companion.milliseconds


@Stable
class EpisodePageState(
    val mediaSelectorState: MediaSelectorState,
    val mediaSourceResultsPresentation: MediaSourceResultsPresentation,
    val danmakuStatistics: DanmakuStatistics,
    val subjectPresentation: SubjectPresentation,
    val episodePresentation: EpisodePresentation,
    val isLoading: Boolean = false,
    val loadError: LoadError? = null,
    val isPlaceholder: Boolean = false,
)

@Stable
class EpisodeViewModel(
    val subjectId: Int,
    initialEpisodeId: Int,
    initialIsFullscreen: Boolean = false,
    context: Context,
    val getCurrentDate: () -> PackedDate = { PackedDate.now() },
) : KoinComponent, AbstractViewModel(), HasBackgroundScope {
    // region dependencies
    private val playerStateFactory: MediampPlayerFactory<*> by inject()
    private val subjectCollectionRepository: SubjectCollectionRepository by inject()
    private val episodeCollectionRepository: EpisodeCollectionRepository by inject()
    private val mediaCacheManager: MediaCacheManager by inject()
    private val danmakuManager: DanmakuManager by inject()
    val mediaResolver: MediaResolver by inject()
    private val settingsRepository: SettingsRepository by inject()
    private val danmakuRegexFilterRepository: DanmakuRegexFilterRepository by inject()
    private val mediaSourceManager: MediaSourceManager by inject()
    private val episodePreferencesRepository: EpisodePreferencesRepository by inject()
    private val bangumiCommentRepository: BangumiCommentRepository by inject()
    private val episodePlayHistoryRepository: EpisodePlayHistoryRepository by inject()
    private val savePlayProgressUseCase: SavePlayProgressUseCase by inject()
    private val subjectDetailsStateFactory: SubjectDetailsStateFactory by inject()
    // endregion

    val player: MediampPlayer =
        playerStateFactory.create(context, backgroundScope.coroutineContext)

    private val fetchPlayState = EpisodeFetchPlayState(subjectId, initialEpisodeId, player, backgroundScope)

    // region Subject and episode data info flows
    private val episodeIdFlow get() = fetchPlayState.episodeIdFlow
    private val subjectEpisodeInfoBundleFlow get() = fetchPlayState.infoBundleFlow
    private val subjectEpisodeInfoBundleLoadErrorFlow get() = fetchPlayState.infoLoadErrorFlow

    private val subjectCollectionFlow =
        subjectEpisodeInfoBundleFlow.filterNotNull().map { it.subjectCollectionInfo }

    private val subjectInfoFlow = subjectCollectionFlow.map { it.subjectInfo }
    private val episodeCollectionFlow = subjectEpisodeInfoBundleFlow.map { it?.episodeCollectionInfo }

    private val episodeCollectionsFlow = episodeCollectionRepository.subjectEpisodeCollectionInfosFlow(subjectId)
        .shareInBackground()

    private val episodeInfoFlow = episodeCollectionFlow.map { it?.episodeInfo }.distinctUntilChanged()
    // endregion


    private val mediaFetchSession get() = fetchPlayState.fetchSelectFlow.map { it?.mediaFetchSession }
    private val mediaSelector get() = fetchPlayState.fetchSelectFlow.map { it?.mediaSelector }

    val playerControllerState = PlayerControllerState(ControllerVisibility.Invisible)
    private val mediaSourceInfoProvider: MediaSourceInfoProvider = MediaSourceInfoProvider(
        getSourceInfoFlow = { mediaSourceManager.infoFlowByMediaSourceId(it) },
    )

    val cacheProgressInfoFlow = CacheProgressProvider(
        player, backgroundScope,
    ).cacheProgressInfoFlow

    /**
     * "视频统计" bottom sheet 显示内容
     */
    val videoStatisticsFlow: Flow<VideoStatistics> = VideoStatisticsCollector(
        fetchPlayState.fetchSelectFlow.map { it?.mediaSelector }
            .filterNotNull(), // // TODO: 2025/1/3 check filterNotNull
        fetchPlayState.playerSession.videoLoadingState,
        player,
        mediaSourceInfoProvider,
        mediaSourceLoading = fetchPlayState.mediaSourceLoadingFlow,
        backgroundScope,
    ).videoStatisticsFlow

    val videoScaffoldConfig: VideoScaffoldConfig by settingsRepository.videoScaffoldConfig
        .flow.produceState(VideoScaffoldConfig.Default)

    val danmakuRegexFilterState = DanmakuRegexFilterState(
        list = danmakuRegexFilterRepository.flow.produceState(emptyList()),
        add = {
            launchInBackground { danmakuRegexFilterRepository.add(it) }
        },
        edit = { regex, filter ->
            launchInBackground {
                danmakuRegexFilterRepository.update(filter.id, filter.copy(regex = regex))
            }
        },
        remove = {
            launchInBackground { danmakuRegexFilterRepository.remove(it) }
        },
        switch = {
            launchInBackground {
                danmakuRegexFilterRepository.update(it.id, it.copy(enabled = !it.enabled))
            }
        },
    )

    val authState: AuthState = AuthState()

    val episodeDetailsState: EpisodeDetailsState = kotlin.run {
        EpisodeDetailsState(
            subjectInfo = subjectInfoFlow.produceState(SubjectInfo.Empty),
            airingLabelState = AiringLabelState(
                subjectCollectionFlow.map { it.airingInfo }.produceState(null),
                subjectCollectionFlow.map { it ->
                    SubjectProgressInfo.compute(it.subjectInfo, it.episodes, getCurrentDate(), it.recurrence)
                }
                    .produceState(null),
            ),
            subjectDetailsStateLoader = SubjectDetailsStateLoader(subjectDetailsStateFactory, backgroundScope),
        )
    }

    /**
     * 剧集列表
     */
    val episodeCarouselState: EpisodeCarouselState = kotlin.run {
        val episodeCacheStatusListState by episodeCollectionsFlow.flatMapLatest { list ->
            if (list.isEmpty()) {
                return@flatMapLatest flowOfEmptyList()
            }
            combine(
                list.map { collection ->
                    mediaCacheManager.cacheStatusForEpisode(subjectId, collection.episodeId).map {
                        collection.episodeId to it
                    }
                },
            ) {
                it.toList()
            }
        }.produceState(emptyList())

        val collectionButtonEnabled = MutableStateFlow(false)
        EpisodeCarouselState(
            episodes = episodeCollectionsFlow.produceState(emptyList()),
            playingEpisode = episodeIdFlow.combine(episodeCollectionsFlow) { id, collections ->
                collections.firstOrNull { it.episodeId == id }
            }.produceState(null),
            cacheStatus = {
                episodeCacheStatusListState.firstOrNull { status ->
                    status.first == it.episodeInfo.episodeId
                }?.second ?: EpisodeCacheStatus.NotCached
            },
            onSelect = {
                launchInMain {
                    switchEpisode(it.episodeInfo.episodeId)
                }
            },
            onChangeCollectionType = { episode, it ->
                collectionButtonEnabled.value = false
                launchInBackground {
                    try {
                        episodeCollectionRepository.setEpisodeCollectionType(
                            subjectId,
                            episodeId = episode.episodeInfo.episodeId,
                            collectionType = it,
                        )
                    } finally {
                        collectionButtonEnabled.value = true
                    }
                }
            },
            backgroundScope = backgroundScope,
        )
    }

    val editableSubjectCollectionTypeState: EditableSubjectCollectionTypeState =
        EditableSubjectCollectionTypeState(
            selfCollectionTypeFlow = subjectCollectionFlow
                .map { it.collectionType },
            hasAnyUnwatched = {
                val collections =
                    episodeCollectionsFlow.firstOrNull() ?: return@EditableSubjectCollectionTypeState true
                collections.any { !it.collectionType.isDoneOrDropped() }
            },
            onSetSelfCollectionType = { subjectCollectionRepository.setSubjectCollectionTypeOrDelete(subjectId, it) },
            onSetAllEpisodesWatched = {
                episodeCollectionRepository.setAllEpisodesWatched(subjectId)
            },
            backgroundScope,
        )

    var isFullscreen: Boolean by mutableStateOf(initialIsFullscreen)
    var sidebarVisible: Boolean by mutableStateOf(true)
    val commentLazyStaggeredGirdState: LazyStaggeredGridState = LazyStaggeredGridState()

    /**
     * 播放器内切换剧集
     */
    val episodeSelectorState: EpisodeSelectorState = EpisodeSelectorState(
        itemsFlow = episodeCollectionsFlow.combine(subjectCollectionFlow) { list, subject ->
            list.map {
                it.toPresentation(subject.recurrence)
            }
        },
        onSelect = {
            launchInMain {
                switchEpisode(it.episodeId)
            }
        },
        currentEpisodeId = episodeIdFlow,
        parentCoroutineContext = backgroundScope.coroutineContext,
    )

    val danmaku = PlayerDanmakuState(
        player = player,
        bundleFlow = fetchPlayState.infoBundleFlow.filterNotNull(),
        danmakuEnabled = settingsRepository.danmakuEnabled.flow.produceState(false),
        danmakuConfig = settingsRepository.danmakuConfig.flow.produceState(DanmakuConfig.Default),
        onSend = { info ->
            danmakuManager.post(episodeIdFlow.value, info)
        },
        onSetEnabled = {
            settingsRepository.danmakuEnabled.set(it)
        },
        onHideController = {
            playerControllerState.toggleFullVisible(false)
        },
        backgroundScope,
    )

    private val commentStateRestarter = FlowRestarter()
    val episodeCommentState: CommentState = CommentState(
        list = episodeIdFlow
            .restartable(commentStateRestarter)
            .flatMapLatest { episodeId ->
                bangumiCommentRepository.subjectEpisodeCommentsPager(episodeId)
                    .map { page ->
                        page.map { it.parseToUIComment() }
                    }
            }.cachedIn(backgroundScope),
        countState = stateOf(null),
        onSubmitCommentReaction = { _, _ -> },
        backgroundScope = backgroundScope,
    )

    val turnstileState = TurnstileState(
        "https://next.bgm.tv/p1/turnstile?redirect_uri=${TurnstileState.CALLBACK_INTERCEPTION_PREFIX}",
    )

    val commentEditorState: CommentEditorState = CommentEditorState(
        showExpandEditCommentButton = true,
        initialEditExpanded = false,
        panelTitle = subjectInfoFlow
            .combine(episodeInfoFlow) { sub, epi -> "${sub.displayName} ${epi?.renderEpisodeEp()}" }
            .produceState(null),
        stickers = flowOf(BangumiCommentSticker.map { EditCommentSticker(it.first, it.second) })
            .produceState(emptyList()),
        richTextRenderer = { text ->
            withContext(Dispatchers.Default) {
                with(CommentMapperContext) { parseBBCode(text) }
            }
        },
        onSend = { context, content ->
            val token = suspend {
                withContext(Dispatchers.Main) { turnstileState.reloadAndGetToken() }
            }
                .asFlow()
                .retry(3)
                .catch {
                    if (it !is CancellationException) {
                        logger.error(it) { "Failed to get token, see exception" }
                    }
                }
                .firstOrNull()

            if (token == null) return@CommentEditorState false

            try {
                bangumiCommentRepository.postEpisodeComment(context, content, token)
                commentStateRestarter.restart() // 评论发送成功了, 刷新一下
                return@CommentEditorState true
            } catch (e: Exception) {
                if (e !is CancellationException) {
                    logger.error(e) { "Failed to post comment, see exception" }
                }
                return@CommentEditorState false
            }
        },
        backgroundScope = backgroundScope,
    )

    val playerSkipOpEdState: PlayerSkipOpEdState = PlayerSkipOpEdState(
        chapters = (player.chapters ?: flowOf(emptyList())).produceState(emptyList()),
        onSkip = {
            player.seekTo(it)
        },
        videoLength = player.mediaProperties.mapNotNull { it?.durationMillis?.milliseconds }
            .produceState(0.milliseconds),
    )

    val pageState = combine(
        subjectEpisodeInfoBundleFlow,
        subjectEpisodeInfoBundleLoadErrorFlow,
        fetchPlayState.fetchSelectFlow.filterNotNull(),
        danmaku.danmakuStatisticsFlow,
    ) { subjectEpisodeBundle, loadError, fetchSelect, danmakuStatistics ->

        val (subject, episode) = if (subjectEpisodeBundle == null) {
            SubjectPresentation.Placeholder to EpisodePresentation.Placeholder
        } else { // modern JVM will optimize out the Pair creation
            Pair(
                subjectEpisodeBundle.subjectInfo.toPresentation(),
                subjectEpisodeBundle.episodeCollectionInfo.toPresentation(subjectEpisodeBundle.subjectCollectionInfo.recurrence),
            )
        }

        EpisodePageState(
            mediaSelectorState = MediaSelectorState(
                fetchSelect.mediaSelector,
                mediaSourceInfoProvider,
                backgroundScope,
            ),
            mediaSourceResultsPresentation = MediaSourceResultsPresentation(
                FilteredMediaSourceResults(
                    results = flowOf(fetchSelect.mediaFetchSession.mediaSourceResults),
                    settings = settingsRepository.mediaSelectorSettings.flow,
                ),
                backgroundScope.coroutineContext,
            ),
            danmakuStatistics = danmakuStatistics,
            subjectPresentation = subject,
            episodePresentation = episode,
            isLoading = subjectEpisodeBundle == null,
            loadError = loadError,
        )
    }.stateIn(backgroundScope, started = SharingStarted.WhileSubscribed(), null)

    private val selfUserId = danmakuManager.selfId

    /**
     * 保存播放进度的入口有4个：退出播放页，切换剧集，同集切换数据源，暂停播放
     * 其中 切换剧集 和 同集切换数据源 虽然都是切换数据源，但它们并不能合并成一个入口，
     * 因为 切换数据源 是依赖 PlayerLauncher collect mediaSelector.selected 实现的，
     * 它会在 mediaSelector.unselect() 任意时间后发现 selected 已经改变，导致 episodeId 可能已经改变，从而将当前集的播放进度保存到新的剧集中
     */
    private suspend fun savePlayProgress() {
        withContext(Dispatchers.Main.immediate) {
            savePlayProgressUseCase(
                player.playbackState.value,
                player.getCurrentPositionMillis(),
                player.mediaProperties.value?.durationMillis ?: 0L,
                episodeIdFlow.value,
            )
        }
    }

    suspend fun switchEpisode(episodeId: Int) {
        withContext(Dispatchers.Main.immediate) {
            savePlayProgress()
            fetchPlayState.setEpisodeId(episodeId)
            episodeDetailsState.showEpisodes = false // 选择后关闭弹窗
            player.stop()
        }
    }

    fun refreshFetch() {
        launchInBackground {
            mediaFetchSession.firstOrNull()?.restartAll()
        }
    }

    @MainThread
    suspend fun stopPlaying() {
        // 退出播放页前保存播放进度
        savePlayProgress()
        player.stop()
    }

    fun startBackgroundTasks() {
        fetchPlayState.startBackgroundTasks()
    }

    init {
        launchInMain { // state changes must be in main thread
            player.playbackState.collect {
                danmaku.danmakuHostState.setPaused(!it.isPlaying)
            }
        }

        launchInBackground {
            cancellableCoroutineScope {
                val selfId = selfUserId.stateIn(this)
                danmaku.danmakuEventFlow.collect { event ->
                    when (event) {
                        is DanmakuEvent.Add -> {
                            val data = event.danmaku
                            if (data.text.isBlank()) return@collect
                            danmaku.danmakuHostState.trySend(
                                createDanmakuPresentation(data, selfId.value),
                            )
                        }

                        is DanmakuEvent.Repopulate -> {
                            danmaku.danmakuHostState.repopulate(
                                event.list
                                    .filter { it.text.any { c -> !c.isWhitespace() } }
                                    .map { createDanmakuPresentation(it, selfId.value) },
                            )

                        }
                    }
                }
                cancelScope()
            }
        }

        // 自动标记看完
        launchInBackground {
            settingsRepository.videoScaffoldConfig.flow
                .map { it.autoMarkDone }
                .distinctUntilChanged()
                .debounce(1000)
                .collectLatest { enabled ->
                    if (!enabled) return@collectLatest

                    // 设置启用

                    mediaFetchSession.collectLatest {
                        cancellableCoroutineScope {
                            combine(
                                player.currentPositionMillis.sampleWithInitial(5000),
                                player.mediaProperties.map { it?.durationMillis }.debounce(5000),
                                player.playbackState,
                            ) { pos, max, playback ->
                                if (max == null || !playback.isPlaying) return@combine
                                if (episodeCollectionFlow.first()?.collectionType == UnifiedCollectionType.DONE) {
                                    cancelScope() // 已经看过了
                                }
                                if (pos > max.toFloat() * 0.9) {
                                    logger.info { "观看到 90%, 标记看过" }
                                    suspend {
                                        episodeCollectionRepository.setEpisodeCollectionType(
                                            subjectId,
                                            episodeIdFlow.value,
                                            UnifiedCollectionType.DONE,
                                        )
                                    }.asFlow().retryWithBackoffDelay().first()
                                    cancelScope() // 标记成功一次后就不要再检查了
                                }
                            }.collect()
                        }
                    }
                }
        }

        launchInBackground {
            settingsRepository.videoScaffoldConfig.flow
                .map { it.autoPlayNext }
                .distinctUntilChanged()
                .collectLatest { enabled ->
                    if (!enabled) return@collectLatest

                    player.playbackState.collect { playback ->
                        if (playback == PlaybackState.FINISHED
                            && player.mediaProperties.value.let { prop ->
                                prop != null && prop.durationMillis > 0L && prop.durationMillis - player.currentPositionMillis.value < 5000
                            }
                        ) {
                            logger.info("播放完毕，切换下一集")
                            launchInMain {// state changes must be in main thread
                                episodeSelectorState.takeIf { it.hasNextEpisode }?.selectNext()
                            }
                        }
                    }
                }
        }

        // 跳过 OP 和 ED
        launchInBackground {
            settingsRepository.videoScaffoldConfig.flow
                .map { it.autoSkipOpEd }
                .distinctUntilChanged()
                .debounce(1000)
                .collectLatest { enabled ->
                    if (!enabled) return@collectLatest

                    // 设置启用
                    combine(
                        player.currentPositionMillis.sampleWithInitial(1000),
                        episodeIdFlow,
                        episodeCollectionsFlow,
                    ) { pos, id, collections ->
                        // 不止一集并且当前是第一集时不跳过
                        if (collections.size > 1 && collections.getOrNull(0)?.episodeId == id) return@combine
                        playerSkipOpEdState.update(pos)
                    }.collect()
                }
        }

        launchInBackground {
            mediaSelector.mapLatest { selector ->
                if (selector == null) return@mapLatest
                selector.events.onBeforeSelect.collect {
                    // 切换 数据源 前保存播放进度
                    withContext(Dispatchers.Main) {
                        savePlayProgress()
                    }
                }
            }
        }
        launchInBackground {
            player.playbackState.collect {
                when (it) {
                    // 加载播放进度
                    PlaybackState.READY -> {
                        val positionMillis =
                            episodePlayHistoryRepository.getPositionMillisByEpisodeId(episodeId = episodeIdFlow.value)
                        if (positionMillis == null) {
                            logger.info { "Did not find saved position" }
                        } else {
                            logger.info { "Loaded saved position: $positionMillis, waiting for video properties" }
                            player.mediaProperties.filter { it != null && it.durationMillis > 0L }.firstOrNull()
                            logger.info { "Loaded saved position: $positionMillis, video properties ready, seeking" }
                            withContext(Dispatchers.Main) { // android must call in main thread
                                player.seekTo(positionMillis)
                            }
                        }
                    }

                    PlaybackState.PAUSED -> savePlayProgress()

                    PlaybackState.FINISHED -> {
                        if (player.mediaProperties.value.let { it != null && it.durationMillis > 0L }) {
                            // 视频长度有效, 说明正常播放中
                            episodePlayHistoryRepository.remove(episodeIdFlow.value)
                        } else {
                            // 视频加载失败或者在切换数据源时又切换了另一个数据源, 不要删除记录
                        }
                    }

                    else -> Unit
                }
            }
        }
    }

    private fun createDanmakuPresentation(
        data: Danmaku,
        selfId: String?,
    ) = DanmakuPresentation(
        data,
        isSelf = selfId == data.senderId,
    )

    override fun onCleared() {
        super.onCleared()
        backgroundScope.launch(NonCancellable + CoroutineName("EpisodeViewModel#onCleared")) {
            stopPlaying()
        }
    }
}


private suspend fun BangumiCommentRepository.postEpisodeComment(
    context: CommentContext,
    content: String,
    turnstileToken: String
) {
    when (context) {
        is CommentContext.Episode ->
            postEpisodeComment(context.episodeId, content, turnstileToken, null)

        is CommentContext.EpisodeReply ->
            postEpisodeComment(context.episodeId, content, turnstileToken, context.commentId)

        is CommentContext.SubjectReview -> error("unreachable on postEpisodeComment")
    }
}