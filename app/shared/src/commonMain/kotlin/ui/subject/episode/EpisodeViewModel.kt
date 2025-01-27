/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.subject.episode

import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.paging.cachedIn
import androidx.paging.map
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
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
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.retry
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.him188.ani.app.data.models.episode.renderEpisodeEp
import me.him188.ani.app.data.models.preference.VideoScaffoldConfig
import me.him188.ani.app.data.models.subject.SubjectInfo
import me.him188.ani.app.data.models.subject.SubjectProgressInfo
import me.him188.ani.app.data.network.protocol.DanmakuInfo
import me.him188.ani.app.data.repository.episode.BangumiCommentRepository
import me.him188.ani.app.data.repository.episode.EpisodeCollectionRepository
import me.him188.ani.app.data.repository.player.DanmakuRegexFilterRepository
import me.him188.ani.app.data.repository.subject.SubjectCollectionRepository
import me.him188.ani.app.data.repository.user.SettingsRepository
import me.him188.ani.app.domain.danmaku.DanmakuManager
import me.him188.ani.app.domain.danmaku.SetDanmakuEnabledUseCase
import me.him188.ani.app.domain.episode.EpisodeCompletionContext.isKnownCompleted
import me.him188.ani.app.domain.episode.EpisodeDanmakuLoader
import me.him188.ani.app.domain.episode.EpisodeFetchSelectPlayState
import me.him188.ani.app.domain.episode.EpisodeSession
import me.him188.ani.app.domain.episode.SubjectEpisodeInfoBundle
import me.him188.ani.app.domain.episode.UnsafeEpisodeSessionApi
import me.him188.ani.app.domain.episode.episodeIdFlow
import me.him188.ani.app.domain.episode.getCurrentEpisodeId
import me.him188.ani.app.domain.episode.infoBundleFlow
import me.him188.ani.app.domain.episode.infoLoadErrorFlow
import me.him188.ani.app.domain.episode.mediaSelectorFlow
import me.him188.ani.app.domain.foundation.LoadError
import me.him188.ani.app.domain.media.cache.EpisodeCacheStatus
import me.him188.ani.app.domain.media.cache.MediaCacheManager
import me.him188.ani.app.domain.media.fetch.MediaSourceManager
import me.him188.ani.app.domain.media.fetch.MediaSourceResultsFilterer
import me.him188.ani.app.domain.media.resolver.MediaResolver
import me.him188.ani.app.domain.player.CacheProgressProvider
import me.him188.ani.app.domain.player.extension.AutoSelectExtension
import me.him188.ani.app.domain.player.extension.MarkAsWatchedExtension
import me.him188.ani.app.domain.player.extension.RememberPlayProgressExtension
import me.him188.ani.app.domain.player.extension.SaveMediaPreferenceExtension
import me.him188.ani.app.domain.player.extension.SwitchMediaOnPlayerErrorExtension
import me.him188.ani.app.domain.player.extension.SwitchNextEpisodeExtension
import me.him188.ani.app.domain.session.AuthState
import me.him188.ani.app.domain.usecase.GlobalKoin
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
import me.him188.ani.app.ui.danmaku.UIDanmakuEvent
import me.him188.ani.app.ui.foundation.AbstractViewModel
import me.him188.ani.app.ui.foundation.AuthState
import me.him188.ani.app.ui.foundation.HasBackgroundScope
import me.him188.ani.app.ui.foundation.launchInBackground
import me.him188.ani.app.ui.foundation.stateOf
import me.him188.ani.app.ui.settings.danmaku.DanmakuRegexFilterState
import me.him188.ani.app.ui.subject.AiringLabelState
import me.him188.ani.app.ui.subject.collection.components.EditableSubjectCollectionTypeState
import me.him188.ani.app.ui.subject.details.state.SubjectDetailsStateFactory
import me.him188.ani.app.ui.subject.details.state.SubjectDetailsStateLoader
import me.him188.ani.app.ui.subject.episode.details.EpisodeCarouselState
import me.him188.ani.app.ui.subject.episode.details.EpisodeDetailsState
import me.him188.ani.app.ui.subject.episode.mediaFetch.MediaSelectorState
import me.him188.ani.app.ui.subject.episode.mediaFetch.MediaSourceInfoProvider
import me.him188.ani.app.ui.subject.episode.mediaFetch.MediaSourceResultListPresentation
import me.him188.ani.app.ui.subject.episode.mediaFetch.MediaSourceResultListPresenter
import me.him188.ani.app.ui.subject.episode.mediaFetch.createTestMediaSelectorState
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
import me.him188.ani.datasources.api.topic.isDoneOrDropped
import me.him188.ani.utils.coroutines.flows.FlowRestarter
import me.him188.ani.utils.coroutines.flows.flowOfEmptyList
import me.him188.ani.utils.coroutines.flows.restartable
import me.him188.ani.utils.coroutines.sampleWithInitial
import me.him188.ani.utils.logging.error
import me.him188.ani.utils.logging.warn
import me.him188.ani.utils.platform.annotations.TestOnly
import org.koin.core.Koin
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.openani.mediamp.MediampPlayer
import org.openani.mediamp.MediampPlayerFactory
import org.openani.mediamp.features.chapters
import kotlin.time.Duration.Companion.milliseconds


@Stable
data class EpisodePageState(
    val mediaSelectorState: MediaSelectorState,
    val mediaSourceResultListPresentation: MediaSourceResultListPresentation,
    val danmakuStatistics: DanmakuStatistics,
    val subjectPresentation: SubjectPresentation,
    val episodePresentation: EpisodePresentation,
    val danmakuEnabled: Boolean,
    val danmakuConfig: DanmakuConfig,
    val isLoading: Boolean = false,
    val loadError: LoadError? = null,
    val isPlaceholder: Boolean = false,
)

/**
 * 要查看有关剧集播放页的详细信息，请参阅 PR 文档 [#1439](https://github.com/open-ani/animeko/pull/1439).
 *
 * @see EpisodeFetchSelectPlayState
 */
@Stable
class EpisodeViewModel(
    val subjectId: Int,
    initialEpisodeId: Int,
    initialIsFullscreen: Boolean = false,
    context: Context,
    val getCurrentDate: () -> PackedDate = { PackedDate.now() },
    private val koin: Koin = GlobalKoin,
) : KoinComponent, AbstractViewModel(), HasBackgroundScope {
    // region dependencies
    private val playerStateFactory: MediampPlayerFactory<*> by inject()
    private val subjectCollectionRepository: SubjectCollectionRepository by inject()
    private val episodeCollectionRepository: EpisodeCollectionRepository by inject()
    private val mediaCacheManager: MediaCacheManager by inject()
    private val danmakuManager: DanmakuManager by inject()
    private val settingsRepository: SettingsRepository by inject()
    private val danmakuRegexFilterRepository: DanmakuRegexFilterRepository by inject()
    private val mediaSourceManager: MediaSourceManager by inject()
    private val bangumiCommentRepository: BangumiCommentRepository by inject()
    private val subjectDetailsStateFactory: SubjectDetailsStateFactory by inject()
    private val setDanmakuEnabledUseCase: SetDanmakuEnabledUseCase by inject()
    // endregion

    val player: MediampPlayer =
        playerStateFactory.create(context, backgroundScope.coroutineContext)

    @OptIn(UnsafeEpisodeSessionApi::class)
    private val fetchPlayState = EpisodeFetchSelectPlayState(
        subjectId, initialEpisodeId, player, backgroundScope,
        extensions = listOf(
            RememberPlayProgressExtension,
            MarkAsWatchedExtension,
            SwitchNextEpisodeExtension.Factory(
                getNextEpisode = { currentEpisodeId ->
                    val list = episodeCollectionsFlow.first()
                    val subject = subjectCollectionFlow.first()
                    val currentIndex = list.indexOfFirst { it.episodeId == currentEpisodeId }
                    if (currentIndex == -1) {
                        null
                    } else {
                        val nextEpisode = list.getOrNull(currentIndex + 1) ?: return@Factory null

                        if (!nextEpisode.episodeInfo.isKnownCompleted(subject.recurrence)) {
                            null
                        } else {
                            nextEpisode.episodeId
                        }
                    }
                },
            ),
            SwitchMediaOnPlayerErrorExtension,
            AutoSelectExtension,
            SaveMediaPreferenceExtension,
        ),
        koin,
        sharingStarted = SharingStarted.WhileSubscribed(5_000),
    )

    val mediaResolver: MediaResolver get() = fetchPlayState.playerSession.mediaResolver

    // region Subject and episode data info flows
    @UnsafeEpisodeSessionApi
    private val episodeIdFlow get() = fetchPlayState.episodeIdFlow

    @UnsafeEpisodeSessionApi
    private val subjectEpisodeInfoBundleFlow: Flow<SubjectEpisodeInfoBundle?> get() = fetchPlayState.infoBundleFlow

    @UnsafeEpisodeSessionApi
    private val subjectEpisodeInfoBundleLoadErrorFlow = fetchPlayState.infoLoadErrorFlow
        .filterNotNull()
        .stateIn(backgroundScope, SharingStarted.WhileSubscribed(), null)

    @UnsafeEpisodeSessionApi
    private val subjectCollectionFlow =
        subjectEpisodeInfoBundleFlow.filterNotNull().map { it.subjectCollectionInfo }
            .distinctUntilChanged()

    @UnsafeEpisodeSessionApi
    private val subjectInfoFlow = subjectCollectionFlow.map { it.subjectInfo }.distinctUntilChanged()

    @UnsafeEpisodeSessionApi
    private val episodeCollectionFlow = subjectEpisodeInfoBundleFlow.map { it?.episodeCollectionInfo }
        .distinctUntilChanged()

    private val episodeCollectionsFlow = episodeCollectionRepository.subjectEpisodeCollectionInfosFlow(subjectId)
        .shareInBackground()

    @UnsafeEpisodeSessionApi
    private val episodeInfoFlow = episodeCollectionFlow.map { it?.episodeInfo }.distinctUntilChanged()
    // endregion


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
    @OptIn(UnsafeEpisodeSessionApi::class)
    val videoStatisticsFlow: Flow<VideoStatistics> = VideoStatisticsCollector(
        fetchPlayState.mediaSelectorFlow
            .filterNotNull(), // // TODO: 2025/1/3 check filterNotNull
        fetchPlayState.playerSession.videoLoadingState,
        player,
        mediaSourceInfoProvider,
        mediaSourceLoading = fetchPlayState.episodeSessionFlow.flatMapLatest { it.mediaSourceLoadingFlow },
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

    @OptIn(UnsafeEpisodeSessionApi::class)
    val episodeDetailsState: EpisodeDetailsState = kotlin.run {
        EpisodeDetailsState(
            subjectInfo = subjectInfoFlow.produceState(SubjectInfo.Empty),
            airingLabelState = AiringLabelState(
                subjectCollectionFlow.map { it.airingInfo }.produceState(null),
                subjectCollectionFlow.map {
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
    @OptIn(UnsafeEpisodeSessionApi::class)
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
                launchInBackground {
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

    @OptIn(UnsafeEpisodeSessionApi::class)
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
    @OptIn(UnsafeEpisodeSessionApi::class)
    val episodeSelectorState: EpisodeSelectorState = EpisodeSelectorState(
        itemsFlow = episodeCollectionsFlow.combine(subjectCollectionFlow) { list, subject ->
            list.map {
                it.toPresentation(subject.recurrence)
            }
        },
        onSelect = {
            launchInBackground {
                switchEpisode(it.episodeId)
            }
        },
        currentEpisodeId = episodeIdFlow,
        parentCoroutineContext = backgroundScope.coroutineContext,
    )


    @OptIn(UnsafeEpisodeSessionApi::class)
    private val danmakuLoader = EpisodeDanmakuLoader(
        player = player,
        // TODO: 2025/1/6 this is not very good. May see old data. 
        selectedMedia = fetchPlayState.mediaSelectorFlow.transformLatest {
            if (it == null) {
                emit(null)
            } else {
                emitAll(it.selected)
            }
        },
        bundleFlow = fetchPlayState.infoBundleFlow.filterNotNull().distinctUntilChanged(),
        backgroundScope,
        koin,
        sharingStarted = SharingStarted.WhileSubscribed(5_000),
    )

    /**
     * Danmaku event flow to be processed by UI DanmakuHost.
     */
    val uiDanmakuEventFlow = danmakuManager.selfId.flatMapLatest { selfId ->
        fun createDanmakuPresentation(
            data: Danmaku,
            selfId: String?,
        ) = DanmakuPresentation(data, isSelf = selfId == data.senderId)

        danmakuLoader.danmakuEventFlow.mapNotNull { event ->
            when (event) {
                is DanmakuEvent.Add -> {
                    val data = event.danmaku
                    if (data.text.isBlank()) {
                        null
                    } else {
                        UIDanmakuEvent.Add(createDanmakuPresentation(data, selfId))
                    }
                }

                is DanmakuEvent.Repopulate -> {
                    UIDanmakuEvent.Repopulate(
                        event.list
                            .filter { it.text.any { c -> !c.isWhitespace() } }
                            .map { createDanmakuPresentation(it, selfId) },
                        player.getCurrentPositionMillis(),
                    )
                }
            }
        }
    }.shareInBackground(
        started = SharingStarted.WhileSubscribed(5000), // Must be some time, because when switching full-screen (i.e. configuration change), UI may stop collect for some milliseconds.
        replay = 1,
    ) // This is lazy. If user puts app into background, queries will abort.


    private val commentStateRestarter = FlowRestarter()

    @OptIn(UnsafeEpisodeSessionApi::class)
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

    @OptIn(UnsafeEpisodeSessionApi::class)
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

    val pageState = fetchPlayState.episodeSessionFlow.transformLatest { episodeSession ->
        coroutineScope {
            emitAll(createPageStateFlow(episodeSession))
            awaitCancellation()
        }
    }.stateIn(backgroundScope, started = SharingStarted.WhileSubscribed(5_000), null)

    private fun CoroutineScope.createPageStateFlow(episodeSession: EpisodeSession): Flow<EpisodePageState> {
        val mediaSourceResultsFlow = MediaSourceResultListPresenter(
            MediaSourceResultsFilterer(
                results = episodeSession.fetchSelectFlow.map {
                    it?.mediaFetchSession?.mediaSourceResults ?: emptyList()
                },
                settings = settingsRepository.mediaSelectorSettings.flow,
                flowScope = this,
            ).filteredSourceResults,
            flowScope = this,
        ).presentationFlow
        return me.him188.ani.utils.coroutines.flows.combine(
            episodeSession.infoBundleFlow.distinctUntilChanged().onStart { emit(null) },
            episodeSession.infoLoadErrorStateFlow,
            episodeSession.fetchSelectFlow,
            combine(
                danmakuLoader.danmakuLoadingStateFlow,
                danmakuLoader.fetchResults,
                settingsRepository.danmakuEnabled.flow,
                ::DanmakuStatistics,
            ).distinctUntilChanged(),
            settingsRepository.danmakuEnabled.flow,
            settingsRepository.danmakuConfig.flow,
            mediaSourceResultsFlow,
        ) { subjectEpisodeBundle, loadError, fetchSelect, danmakuStatistics, danmakuEnabled, danmakuConfig, mediaSourceResultsPresentation ->

            val (subject, episode) = if (subjectEpisodeBundle == null) {
                SubjectPresentation.Placeholder to EpisodePresentation.Placeholder
            } else { // modern JVM will optimize out the Pair creation
                Pair(
                    subjectEpisodeBundle.subjectInfo.toPresentation(),
                    subjectEpisodeBundle.episodeCollectionInfo.toPresentation(subjectEpisodeBundle.subjectCollectionInfo.recurrence),
                )
            }

            if (loadError != null) { // TODO: 2025/1/6 display load error in UI 
                logger.warn { "InfoBundle load error: loadError" }
            }

            EpisodePageState(
                mediaSelectorState = if (fetchSelect != null) MediaSelectorState(
                    fetchSelect.mediaSelector,
                    mediaSourceInfoProvider,
                    backgroundScope,
                ) else {
                    // TODO: 2025/1/22 We should not use createTestMediaSelectorState
                    @OptIn(TestOnly::class)
                    createTestMediaSelectorState(backgroundScope)
                },
                mediaSourceResultListPresentation = MediaSourceResultListPresentation(mediaSourceResultsPresentation),
                danmakuStatistics = danmakuStatistics,
                subjectPresentation = subject,
                episodePresentation = episode,
                danmakuEnabled = danmakuEnabled,
                danmakuConfig = danmakuConfig,
                isLoading = subjectEpisodeBundle == null,
                loadError = loadError,
            )
        }
    }

    suspend fun switchEpisode(episodeId: Int) {
        // 关闭弹窗
        withContext(Dispatchers.Main.immediate) {
            episodeDetailsState.showEpisodes = false
        }

        // 在后台 dispatchers 中操作
        backgroundScope.launch {
            fetchPlayState.switchEpisode(episodeId)
        }.join()
    }

    @OptIn(UnsafeEpisodeSessionApi::class)
    suspend fun postDanmaku(danmaku: DanmakuInfo): Danmaku {
        return withContext(Dispatchers.Default) {
            danmakuManager.post(fetchPlayState.getCurrentEpisodeId(), danmaku)
        }
    }

    fun setDanmakuEnabled(enabled: Boolean) {
        launchInBackground {
            setDanmakuEnabledUseCase(enabled)
        }
    }

    fun refreshFetch() {
        launchInBackground {
            // Although it's flow, it should be ready.
            fetchPlayState.episodeSessionFlow.flatMapLatest { it.fetchSelectFlow }
                .map { it?.mediaFetchSession }
                .filterNotNull()
                .firstOrNull()
                ?.restartAll()
        }
    }

    fun restartSource(instanceId: String) {
        launchInBackground {
            fetchPlayState.episodeSessionFlow.flatMapLatest { it.fetchSelectFlow }
                .map { it?.mediaFetchSession }
                .filterNotNull()
                .firstOrNull()
                ?.mediaSourceResults
                ?.find { it.instanceId == instanceId }
                ?.restart()
        }
    }

    fun onUIReady() {
        fetchPlayState.onUIReady()
    }

    init {
        // 跳过 OP 和 ED
        launchInBackground {
            settingsRepository.videoScaffoldConfig.flow
                .map { it.autoSkipOpEd }
                .distinctUntilChanged()
                .debounce(1000)
                .collectLatest { enabled ->
                    if (!enabled) return@collectLatest

                    // 设置启用
                    @OptIn(UnsafeEpisodeSessionApi::class)
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
    }

    override fun onCleared() {
        super.onCleared()
        backgroundScope.launch(NonCancellable + CoroutineName("EpisodeViewModel#onCleared")) {
            fetchPlayState.onClose()
            withContext(Dispatchers.Main) {
                player.stopPlayback()
            }
        }
    }

    override fun getKoin(): Koin = koin

    fun setDanmakuSourceEnabled(providerId: String, enabled: Boolean) {
        danmakuLoader.setEnabled(providerId, enabled)
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