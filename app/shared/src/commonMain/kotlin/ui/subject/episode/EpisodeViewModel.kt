/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.subject.episode

import androidx.annotation.UiThread
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.paging.cachedIn
import androidx.paging.map
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.him188.ani.app.data.models.episode.displayName
import me.him188.ani.app.data.models.episode.renderEpisodeEp
import me.him188.ani.app.data.models.preference.VideoScaffoldConfig
import me.him188.ani.app.data.models.subject.SubjectInfo
import me.him188.ani.app.data.models.subject.SubjectProgressInfo
import me.him188.ani.app.data.models.subject.nameCnOrName
import me.him188.ani.app.data.network.AutoSkipRepository
import me.him188.ani.app.data.repository.episode.BangumiCommentRepository
import me.him188.ani.app.data.repository.episode.EpisodeCollectionRepository
import me.him188.ani.app.data.repository.player.DanmakuRegexFilterRepository
import me.him188.ani.app.data.repository.subject.SubjectCollectionRepository
import me.him188.ani.app.data.repository.user.SettingsRepository
import me.him188.ani.app.domain.comment.PostCommentUseCase
import me.him188.ani.app.domain.comment.TurnstileState
import me.him188.ani.app.domain.danmaku.DanmakuManager
import me.him188.ani.app.domain.danmaku.SetDanmakuEnabledUseCase
import me.him188.ani.app.domain.episode.EpisodeCompletionContext.isKnownCompleted
import me.him188.ani.app.domain.episode.EpisodeDanmakuLoader
import me.him188.ani.app.domain.episode.EpisodeFetchSelectPlayState
import me.him188.ani.app.domain.episode.EpisodeSession
import me.him188.ani.app.domain.episode.SetEpisodeCollectionTypeUseCase
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
import me.him188.ani.app.domain.mediasource.instance.GetMediaSourceInstancesUseCase
import me.him188.ani.app.domain.player.CacheProgressProvider
import me.him188.ani.app.domain.player.extension.AnalyticsExtension
import me.him188.ani.app.domain.player.extension.AutoSelectExtension
import me.him188.ani.app.domain.player.extension.CacheOnBtPlayExtension
import me.him188.ani.app.domain.player.extension.MarkAsWatchedExtension
import me.him188.ani.app.domain.player.extension.RememberPlayProgressExtension
import me.him188.ani.app.domain.player.extension.SaveMediaPreferenceExtension
import me.him188.ani.app.domain.player.extension.SwitchMediaOnPlayerErrorExtension
import me.him188.ani.app.domain.player.extension.SwitchNextEpisodeExtension
import me.him188.ani.app.domain.settings.GetMediaSelectorSettingsUseCase
import me.him188.ani.app.domain.usecase.GlobalKoin
import me.him188.ani.app.platform.Context
import me.him188.ani.app.ui.comment.BangumiCommentSticker
import me.him188.ani.app.ui.comment.CommentEditorState
import me.him188.ani.app.ui.comment.CommentMapperContext
import me.him188.ani.app.ui.comment.CommentMapperContext.parseToUIComment
import me.him188.ani.app.ui.comment.CommentState
import me.him188.ani.app.ui.comment.EditCommentSticker
import me.him188.ani.app.ui.danmaku.UIDanmakuEvent
import me.him188.ani.app.ui.episode.PlayingEpisodeSummary
import me.him188.ani.app.ui.episode.danmaku.MatchingDanmakuPresenter
import me.him188.ani.app.ui.episode.danmaku.MatchingDanmakuUiState
import me.him188.ani.app.ui.episode.share.MediaShareData
import me.him188.ani.app.ui.foundation.AbstractViewModel
import me.him188.ani.app.ui.foundation.HasBackgroundScope
import me.him188.ani.app.ui.foundation.launchInBackground
import me.him188.ani.app.ui.foundation.stateOf
import me.him188.ani.app.ui.mediafetch.MediaSelectorState
import me.him188.ani.app.ui.mediafetch.MediaSourceInfoProvider
import me.him188.ani.app.ui.mediafetch.MediaSourceResultListPresentation
import me.him188.ani.app.ui.mediafetch.MediaSourceResultListPresenter
import me.him188.ani.app.ui.mediafetch.ViewKind
import me.him188.ani.app.ui.mediafetch.createTestMediaSelectorState
import me.him188.ani.app.ui.mediaselect.summary.MediaSelectorSummary
import me.him188.ani.app.ui.mediaselect.summary.MediaSelectorSummaryStateProducer
import me.him188.ani.app.ui.mediaselect.summary.selectedMaybeExcludedMediaFlow
import me.him188.ani.app.ui.settings.danmaku.DanmakuRegexFilterState
import me.him188.ani.app.ui.subject.AiringLabelState
import me.him188.ani.app.ui.subject.collection.components.EditableSubjectCollectionTypeState
import me.him188.ani.app.ui.subject.details.state.SubjectDetailsStateFactory
import me.him188.ani.app.ui.subject.details.state.SubjectDetailsStateLoader
import me.him188.ani.app.ui.subject.episode.details.EpisodeCarouselState
import me.him188.ani.app.ui.subject.episode.details.EpisodeDetailsState
import me.him188.ani.app.ui.subject.episode.statistics.DanmakuStatistics
import me.him188.ani.app.ui.subject.episode.statistics.VideoStatistics
import me.him188.ani.app.ui.subject.episode.statistics.VideoStatisticsCollector
import me.him188.ani.app.ui.subject.episode.video.PlayerSkipOpEdState
import me.him188.ani.app.ui.subject.episode.video.sidesheet.EpisodeSelectorState
import me.him188.ani.app.ui.user.SelfInfoStateProducer
import me.him188.ani.app.ui.user.SelfInfoUiState
import me.him188.ani.app.videoplayer.ui.ControllerVisibility
import me.him188.ani.app.videoplayer.ui.PlayerControllerState
import me.him188.ani.danmaku.api.DanmakuContent
import me.him188.ani.danmaku.api.DanmakuEvent
import me.him188.ani.danmaku.api.DanmakuInfo
import me.him188.ani.danmaku.api.DanmakuServiceId
import me.him188.ani.danmaku.api.provider.DanmakuFetchResult
import me.him188.ani.danmaku.api.provider.DanmakuProviderId
import me.him188.ani.danmaku.ui.DanmakuConfig
import me.him188.ani.danmaku.ui.DanmakuHostState
import me.him188.ani.danmaku.ui.DanmakuPresentation
import me.him188.ani.danmaku.ui.DanmakuTrackProperties
import me.him188.ani.datasources.api.PackedDate
import me.him188.ani.datasources.api.source.MediaFetchRequest
import me.him188.ani.datasources.api.source.MediaSourceKind
import me.him188.ani.datasources.api.topic.isDoneOrDropped
import me.him188.ani.utils.coroutines.SingleTaskExecutor
import me.him188.ani.utils.coroutines.flows.FlowRestarter
import me.him188.ani.utils.coroutines.flows.flowOfEmptyList
import me.him188.ani.utils.coroutines.flows.flowOfNull
import me.him188.ani.utils.coroutines.flows.restartable
import me.him188.ani.utils.coroutines.sampleWithInitial
import me.him188.ani.utils.logging.info
import me.him188.ani.utils.logging.warn
import me.him188.ani.utils.platform.annotations.TestOnly
import org.koin.core.Koin
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.openani.mediamp.InternalMediampApi
import org.openani.mediamp.MediampPlayer
import org.openani.mediamp.MediampPlayerFactory
import org.openani.mediamp.features.chapters
import org.openani.mediamp.metadata.Chapter
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds


@Stable
data class EpisodePageState(
    val selfInfo: SelfInfoUiState,
    val mediaSelectorState: MediaSelectorState,
    val mediaSourceResultListPresentation: MediaSourceResultListPresentation,
    val danmakuStatistics: DanmakuStatistics,
    val subjectPresentation: SubjectPresentation,
    val episodePresentation: EpisodePresentation,
    val danmakuEnabled: Boolean,
    val danmakuConfig: DanmakuConfig,
    val isLoading: Boolean = false,
    val loadError: EpisodePageLoadError? = null,
    val isPlaceholder: Boolean = false,
    val playingEpisodeSummary: PlayingEpisodeSummary?, // null means placeholder TODO: should distinguish placeholder
    val mediaSelectorSummary: MediaSelectorSummary,
    val initialMediaSelectorViewKind: ViewKind,
    val matchingDanmakuPresenter: MatchingDanmakuPresenter?,
    val matchingDanmakuUiState: MatchingDanmakuUiState?,
    val fetchRequest: MediaFetchRequest?,
    val shareData: MediaShareData,
)

/**
 * 播放页的加载错误
 */
sealed class EpisodePageLoadError {
    /**
     * 关键的条目和剧集信息加载错误.
     *
     * 这只包含 [SubjectEpisodeInfoBundle.subjectInfo] 和 [SubjectEpisodeInfoBundle.episodeInfo].
     *
     * 这两个信息是极其关键的信息, 如果加载错误就无法显示整个页面.
     */
    data class SubjectError(
        val loadError: LoadError,
    ) : EpisodePageLoadError()

    /**
     * [SubjectEpisodeInfoBundle.seriesInfo] 或者 [SubjectEpisodeInfoBundle.subjectCompleted] 等用来让查询更准确的信息加载错误.
     *
     * 缺少这些信息仍然可以继续查询和播放, 只是不太准确.
     * 注意, 这可能会在离线播放时发生.
     */
    data class SeriesError(
        val loadError: LoadError,
    ) : EpisodePageLoadError()
}

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
    private val postCommentUseCase: PostCommentUseCase by inject()
    private val autoSkipRepository: AutoSkipRepository by inject()
    private val getMediaSelectorSettings: GetMediaSelectorSettingsUseCase by inject()
    private val getMediaSourceInstances: GetMediaSourceInstancesUseCase by inject()
    val turnstileState: TurnstileState by inject()
    val setEpisodeCollectionType: SetEpisodeCollectionTypeUseCase by inject()
    // endregion

    private val tasker = SingleTaskExecutor(backgroundScope.coroutineContext)

    val player: MediampPlayer =
        playerStateFactory.create(context, backgroundScope.coroutineContext)

    @OptIn(UnsafeEpisodeSessionApi::class)
    private val fetchPlayState = EpisodeFetchSelectPlayState(
        subjectId, initialEpisodeId, player, backgroundScope,
        extensions = listOf(
            AnalyticsExtension,
            RememberPlayProgressExtension,
            MarkAsWatchedExtension,
            CacheOnBtPlayExtension,
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
        analyticsContext = object : EpisodeFetchSelectPlayState.AnalyticsContext {
            override suspend fun isFullscreen(): Boolean? {
                return withContext(Dispatchers.Main) { this@EpisodeViewModel.isFullscreen }
            }
        },
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

    val playerVolumeFlow: Flow<VideoScaffoldConfig.PlayerVolume> =
        settingsRepository.videoScaffoldConfig.flow.map { it.playerVolume }

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


    private val selfInfoFlow = SelfInfoStateProducer(koin = getKoin()).flow

    private fun initialMediaSelectorViewKindFlow(): Flow<ViewKind> =
        settingsRepository.mediaSelectorSettings.flow.map { settings ->
            when (settings.preferKind) {
                MediaSourceKind.WEB -> ViewKind.WEB
                MediaSourceKind.BitTorrent -> ViewKind.BT
                MediaSourceKind.LocalCache -> ViewKind.WEB
                null -> ViewKind.WEB
            }
        }


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
    val commentLazyGirdState: LazyGridState = LazyGridState()

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
            data: DanmakuInfo,
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
                        withContext(Dispatchers.Main) {
                            player.getCurrentPositionMillis()
                        },
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
        onSend = { context, content -> postCommentUseCase(context, content) },
        backgroundScope = backgroundScope,
    )

    // Combine original chapters with AutoSkip rules fetched from server
    @OptIn(UnsafeEpisodeSessionApi::class, InternalMediampApi::class)
    private val autoSkipChaptersFlow: Flow<List<Chapter>> =
        fetchPlayState.episodeSessionFlow.flatMapLatest { session ->
            autoSkipRepository.rulesFlow(session.episodeId)
        }.combine(
            player.mediaProperties.mapNotNull { it?.durationMillis?.milliseconds },
        ) { times, videoLength ->
            val durationMillis = when {
                videoLength > 20.minutes -> 85_000L
                videoLength > 10.minutes -> 55_000L
                else -> 0L
            }
            if (durationMillis == 0L) {
                emptyList()
            } else {
                times.map { t ->
                    Chapter(
                        "AutoSkip",
                        durationMillis,
                        t.toLong() * 1000L,
                    )
                }
            }
        }

    private val enableAutoSkip = false

    private val combinedChaptersFlow: Flow<List<Chapter>> =
        combine(
            (player.chapters ?: flowOf(emptyList())),
            if (enableAutoSkip) autoSkipChaptersFlow else emptyFlow(),
        ) { a, b -> if (b.isEmpty()) a else (a + b) }

    // Chapters to be displayed on progress slider (merged with AutoSkip rules)
    val progressChaptersFlow: Flow<List<Chapter>> = combinedChaptersFlow

    val playerSkipOpEdState: PlayerSkipOpEdState = PlayerSkipOpEdState(
        chapters = combinedChaptersFlow.produceState(emptyList()),
        onSkip = {
            player.seekTo(it)
        },
        videoLength = player.mediaProperties.mapNotNull { it?.durationMillis?.milliseconds }
            .produceState(0.milliseconds),
    )

    private val matchingDanmakuProviderId = MutableStateFlow<DanmakuProviderId?>(null)

    val pageState = fetchPlayState.episodeSessionFlow.transformLatest { episodeSession ->
        logger.info { "Switching to new episodeSession ${episodeSession.episodeId}" }
        coroutineScope {
            emitAll(createPageStateFlow(episodeSession))
            awaitCancellation()
        }
    }.stateIn(backgroundScope, started = SharingStarted.WhileSubscribed(5_000), null)

    private val danmakuConfigState = mutableStateOf(DanmakuConfig.Default)
    val danmakuHostState = DanmakuHostState(danmakuConfigState, DanmakuTrackProperties.Default)

    private fun CoroutineScope.createPageStateFlow(episodeSession: EpisodeSession): Flow<EpisodePageState> {
        // 保证数据源会一直查询, 否则会显示许多 CANCELLED 日志
        episodeSession.fetchSelectFlow.flatMapLatest {
            it?.mediaFetchSession?.cumulativeResults ?: flowOfEmptyList()
        }.launchIn(this)

        val filteredSourceResults = MediaSourceResultsFilterer(
            results = episodeSession.fetchSelectFlow.map {
                it?.mediaFetchSession?.mediaSourceResults ?: emptyList()
            },
            settings = settingsRepository.mediaSelectorSettings.flow,
            flowScope = this,
        ).filteredSourceResults
            .shareIn(this, started = SharingStarted.Lazily, replay = 1)

        val mediaSourceResultsFlow = MediaSourceResultListPresenter(
            filteredSourceResults,
        ).presentationFlow
            .shareIn(this, SharingStarted.Lazily, replay = 1)

        val matchingDanmakuPresenter = matchingDanmakuProviderId.map { providerId ->
            danmakuLoader.fetchers
                .find { fetcher ->
                    fetcher.providerId == providerId && fetcher.supportsInteractiveMatching
                }
                ?.startInteractiveMatch()
                ?.let {
                    MatchingDanmakuPresenter(it, this)
                }
        }.shareIn(this, started = SharingStarted.Lazily, replay = 1)

        val mediaSelectorSummaryStateProducer = MediaSelectorSummaryStateProducer(
            episodeSession.fetchSelectFlow.mapNotNull { it?.mediaSelector }
                .flatMapLatest { it.selectedMaybeExcludedMediaFlow }
                .onStart { emit(null) },
            filteredSourceResults,
            getMediaSelectorSettings(),
            getMediaSourceInstances.getAsMediaSourceInfoWithId(),
        ).flow.stateIn(
            this,
            started = SharingStarted.Lazily,
            initialValue = MediaSelectorSummary.AutoSelecting(listOf(), estimate = 10.seconds),
        )

        val selectedMediaFlow =
            episodeSession.fetchSelectFlow.flatMapLatest { it?.mediaSelector?.selected ?: flowOfNull() }
        return me.him188.ani.utils.coroutines.flows.combine(
            selfInfoFlow,
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
            episodeSession.fetchSelectFlow.map { fetchSelect ->
                if (fetchSelect != null) {
                    MediaSelectorState(
                        fetchSelect.mediaSelector,
                        filteredSourceResults,
                        mediaSourceInfoProvider,
                        backgroundScope,
                        koin.get(),
                    )
                } else {
                    // TODO: 2025/1/22 We should not use createTestMediaSelectorState
                    @OptIn(TestOnly::class)
                    createTestMediaSelectorState(backgroundScope)
                }
            },
            mediaSourceResultsFlow.map { MediaSourceResultListPresentation(it) },
            mediaSelectorSummaryStateProducer,
            initialMediaSelectorViewKindFlow(),
            matchingDanmakuPresenter,
            matchingDanmakuPresenter.flatMapLatest { it?.uiState ?: flowOfNull() },
            combine(selectedMediaFlow, player.mediaData) { selectedMedia, mediaData ->
                MediaShareData.from(selectedMedia, mediaData)
            },
        ) { authState, subjectEpisodeBundle, subjectLoadError, fetchSelect, danmakuStatistics, danmakuEnabled, danmakuConfig, mediaSelectorState, mediaSourceResultsPresentation, mediaSelectorSummary, initialMediaSelectorViewKind, matchingDanmakuPresenter, matchingDanmaku, shareData ->

            val (subject, episode) = if (subjectEpisodeBundle == null) {
                SubjectPresentation.Placeholder to EpisodePresentation.Placeholder
            } else { // modern JVM will optimize out the Pair creation
                Pair(
                    subjectEpisodeBundle.subjectInfo.toPresentation(),
                    subjectEpisodeBundle.episodeCollectionInfo.toPresentation(subjectEpisodeBundle.subjectCollectionInfo.recurrence),
                )
            }

            if (subjectLoadError != null) { // TODO: 2025/1/6 display load error in UI 
                logger.warn { "InfoBundle load error: $subjectLoadError" }
            }

            fun getLoadError(): EpisodePageLoadError? {
                // 注意, 这是有显示优先级的. 优先显示重大错误.
                subjectLoadError?.let {
                    return EpisodePageLoadError.SubjectError(subjectLoadError)
                }
                return null
            }

            EpisodePageState(
                selfInfo = authState,
                mediaSelectorState = mediaSelectorState,
                mediaSourceResultListPresentation = mediaSourceResultsPresentation,
                danmakuStatistics = danmakuStatistics,
                subjectPresentation = subject,
                episodePresentation = episode,
                danmakuEnabled = danmakuEnabled,
                danmakuConfig = danmakuConfig,
                isLoading = subjectEpisodeBundle == null,
                loadError = getLoadError(),
                playingEpisodeSummary = if (subjectEpisodeBundle == null) {
                    null
                } else {
                    PlayingEpisodeSummary(
                        episodeSort = subjectEpisodeBundle.episodeInfo.sort,
                        episodeName = subjectEpisodeBundle.episodeInfo.displayName,
                        subjectName = subjectEpisodeBundle.subjectInfo.displayName,
                        subjectTags = listOf(), // todo: tags, see figma
                        subjectCoverUrl = subjectEpisodeBundle.subjectInfo.imageLarge,
                        rating = subjectEpisodeBundle.subjectInfo.ratingInfo,
                        selfRatingInfo = subjectEpisodeBundle.subjectCollectionInfo.selfRatingInfo,
                    )
                },
                mediaSelectorSummary = mediaSelectorSummary,
                initialMediaSelectorViewKind = initialMediaSelectorViewKind,
                matchingDanmakuPresenter = matchingDanmakuPresenter,
                matchingDanmakuUiState = matchingDanmaku?.copy(
                    initialQuery = subjectEpisodeBundle?.subjectInfo?.nameCnOrName ?: "",
                ),
                fetchRequest = fetchSelect?.mediaFetchSession?.request?.first(),
                shareData = shareData,
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
    suspend fun postDanmaku(danmaku: DanmakuContent): DanmakuInfo {
        return withContext(Dispatchers.Default) {
            danmakuManager.post(fetchPlayState.getCurrentEpisodeId(), danmaku)
        }
    }

    fun setDanmakuEnabled(enabled: Boolean) {
        launchInBackground {
            setDanmakuEnabledUseCase(enabled)
        }
    }

    fun savePlayerVolume(volume: Float, mute: Boolean) {
        launchInBackground {
            tasker.invoke {
                delay(200)
                settingsRepository.videoScaffoldConfig
                    .update { copy(playerVolume = VideoScaffoldConfig.PlayerVolume(volume, mute)) }
            }
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

    /**
     * UI handler for the "skip 85 seconds" button.
     * Reports the action to server with throttling and then performs the seek.
     */
    @OptIn(UnsafeEpisodeSessionApi::class)
    fun onClickSkip85(currentPositionMillis: Long) {
        // Seek immediately for UX
        player.skip(85_000L)
        // Report in background
        launchInBackground {
            logger.info { "Reporting skip 85 at ${currentPositionMillis / 1000}s" }
            val episodeId = fetchPlayState.getCurrentEpisodeId()
            val selected = fetchPlayState.episodeSessionFlow.firstOrNull()
                ?.fetchSelectFlow
                ?.firstOrNull()
                ?.mediaSelector
                ?.selected
                ?.firstOrNull()
            val mediaSourceId = selected?.mediaSourceId ?: return@launchInBackground
            val timeSeconds = (currentPositionMillis / 1000).toInt()
            if (timeSeconds < 0 || timeSeconds > 200 * 60) {
                logger.warn { "Refusing to report skip 85 at invalid time ${timeSeconds}s" }
                return@launchInBackground
            }
            autoSkipRepository.reportSkip(episodeId, mediaSourceId, timeSeconds)
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

    @UiThread
    suspend fun collectDanmakuConfig() {
        pageState
            .filterNotNull()
            .collect { state ->
                danmakuConfigState.value = state.danmakuConfig
            }
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

    fun setDanmakuSourceEnabled(serviceId: DanmakuServiceId, enabled: Boolean) {
        danmakuLoader.setEnabled(serviceId, enabled)
    }

    fun startMatchingDanmaku(id: DanmakuProviderId) {
        matchingDanmakuProviderId.value = id
    }

    fun cancelMatchingDanmaku() {
        matchingDanmakuProviderId.value = null
    }

    fun onMatchingDanmakuComplete(provider: DanmakuProviderId, result: List<DanmakuFetchResult>) {
        danmakuLoader.overrideResults(provider, result)
        cancelMatchingDanmaku()
    }

    fun updateFetchRequest(request: MediaFetchRequest) {
        launchInBackground {
            fetchPlayState.episodeSessionFlow
                .firstOrNull()
                ?.fetchSelectFlow
                ?.firstOrNull()
                ?.mediaFetchSession
                ?.setFetchRequest(request)
        }
    }

    @OptIn(UnsafeEpisodeSessionApi::class)
    fun retryLoad(error: EpisodePageLoadError) {
        launchInBackground {
            when (error) {
                is EpisodePageLoadError.SeriesError -> {
                    fetchPlayState.restartLoad()
                }

                is EpisodePageLoadError.SubjectError -> {
                    fetchPlayState.restartLoad()
                }
            }
        }
    }
}
