/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.leanback.ui.episode

import android.graphics.Bitmap
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.paging.PagingData
import androidx.paging.cachedIn
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.him188.ani.app.data.models.episode.EpisodeComment
import me.him188.ani.app.data.network.AutoSkipRepository
import me.him188.ani.app.data.network.TmdbImageService
import me.him188.ani.app.data.network.matchToEpisodes
import me.him188.ani.app.data.network.newestAiredDateStringOrNull
import me.him188.ani.app.data.repository.episode.EpisodeCollectionRepository
import me.him188.ani.app.data.repository.episode.EpisodeCommentRepository
import me.him188.ani.app.data.repository.media.SelectorMediaSourceEpisodeCacheRepository
import me.him188.ani.app.data.repository.subject.SubjectCollectionRepository
import me.him188.ani.app.data.repository.user.SettingsRepository
import me.him188.ani.app.domain.danmaku.DanmakuRepository
import me.him188.ani.app.domain.episode.EpisodeCompletionContext.isKnownCompleted
import me.him188.ani.app.domain.episode.EpisodeDanmakuLoader
import me.him188.ani.app.domain.episode.EpisodeFetchSelectPlayState
import me.him188.ani.app.domain.episode.GetSubjectRecommendationUseCase
import me.him188.ani.app.domain.episode.SubjectRecommendation
import me.him188.ani.app.domain.episode.UnsafeEpisodeSessionApi
import me.him188.ani.app.domain.episode.episodeIdFlow
import me.him188.ani.app.domain.episode.infoBundleFlow
import me.him188.ani.app.domain.episode.mediaSelectorFlow
import me.him188.ani.app.domain.media.fetch.MediaSourceFetchState
import me.him188.ani.app.domain.media.resolver.MediaResolver
import me.him188.ani.app.domain.mediasource.web.captcha.SolveOutcome
import me.him188.ani.app.domain.mediasource.web.captcha.WebSessionManager
import me.him188.ani.app.domain.player.VideoLoadingState
import me.him188.ani.app.domain.player.extension.AutoSelectExtension
import me.him188.ani.app.domain.player.extension.MarkAsWatchedExtension
import me.him188.ani.app.domain.player.extension.ObserveWebMediaSourcePreferenceExtension
import me.him188.ani.app.domain.player.extension.PlaybackSpeedExtension
import me.him188.ani.app.domain.player.extension.RememberPlayProgressExtension
import me.him188.ani.app.domain.player.extension.SaveMediaPreferenceExtension
import me.him188.ani.app.domain.player.extension.SwitchMediaOnPlayerErrorExtension
import me.him188.ani.app.domain.player.extension.SwitchNextEpisodeExtension
import me.him188.ani.app.domain.player.extension.WatchTogetherPlayerExtension
import me.him188.ani.app.domain.settings.GetDanmakuRegexFilterListFlowUseCase
import me.him188.ani.app.domain.watchtogether.PlaybackAutomationGate
import me.him188.ani.app.navigation.SubjectDetailPlaceholder
import me.him188.ani.app.platform.ContextMP
import me.him188.ani.app.ui.foundation.AbstractViewModel
import me.him188.ani.app.videoplayer.ui.androidPlayerStatsFlow
import me.him188.ani.app.videoplayer.ui.progress.MediaProgressFramePreviewState
import me.him188.ani.app.videoplayer.ui.progress.subtitleLanguage
import me.him188.ani.app.videoplayer.videoenhancement.VideoEnhancementMode
import me.him188.ani.app.videoplayer.videoenhancement.createVideoEnhancementController
import me.him188.ani.danmaku.api.DanmakuEvent
import me.him188.ani.danmaku.api.DanmakuInfo
import me.him188.ani.danmaku.api.provider.MatchingDanmakuProvider
import me.him188.ani.danmaku.ui.DanmakuConfig
import me.him188.ani.danmaku.ui.DanmakuHostState
import me.him188.ani.danmaku.ui.DanmakuPresentation
import me.him188.ani.danmaku.ui.DanmakuTrackProperties
import me.him188.ani.datasources.api.Media
import me.him188.ani.datasources.api.topic.UnifiedCollectionType
import me.him188.ani.leanback.ui.episode.controls.TvSubtitleOption
import me.him188.ani.leanback.ui.episode.danmaku.TvDanmakuMatchState
import me.him188.ani.leanback.ui.episode.danmaku.TvDanmakuOrigin
import me.him188.ani.leanback.ui.episode.danmaku.TvUIDanmakuEvent
import me.him188.ani.leanback.ui.episode.danmaku.adjustForTv
import me.him188.ani.leanback.ui.episode.playback.TvAutoSkipController
import me.him188.ani.leanback.ui.episode.playback.TvChapter
import me.him188.ani.leanback.ui.episode.playback.TvPlaybackInteractionState
import me.him188.ani.leanback.ui.episode.recommendation.tvNavigationSubjectId
import me.him188.ani.leanback.ui.episode.source.TvSourceSelectionState
import me.him188.ani.leanback.ui.episode.source.tvSourceGroups
import me.him188.ani.leanback.ui.foundation.TvNavigationEvent
import me.him188.ani.leanback.ui.foundation.TvNavigationEvents
import org.koin.core.Koin
import org.openani.mediamp.ExperimentalMediampApi
import org.openani.mediamp.MediampPlayer
import org.openani.mediamp.MediampPlayerFactory
import org.openani.mediamp.features.AspectRatioMode
import org.openani.mediamp.features.Buffering
import org.openani.mediamp.features.FramePreview
import org.openani.mediamp.features.PlaybackSpeed
import org.openani.mediamp.features.VideoAspectRatio
import org.openani.mediamp.features.chapters
import org.openani.mediamp.features.subtitleTracks
import org.openani.mediamp.togglePlayWhenReady

/**
 * TV 播放页薄 VM (atv-architecture.md §8.1): 与手机共用同一套播放编排 (app-data domain),
 * 播放、弹幕、选源及一起看的操作决策均在此处处理, UI 只消费状态并发送 Intent.
 */
@Stable
class TvEpisodeViewModel(
    val subjectId: Int,
    initialEpisodeId: Int,
    context: ContextMP,
    private val koin: Koin,
    private val playerStateFactory: MediampPlayerFactory<*>,
    private val episodeCollectionRepository: EpisodeCollectionRepository,
    private val subjectCollectionRepository: SubjectCollectionRepository,
    private val danmakuRepository: DanmakuRepository,
    private val settingsRepository: SettingsRepository,
    private val getDanmakuRegexFilterListFlowUseCase: GetDanmakuRegexFilterListFlowUseCase,
    private val episodeCommentRepository: EpisodeCommentRepository,
    private val getSubjectRecommendations: GetSubjectRecommendationUseCase,
    private val autoSkipRepository: AutoSkipRepository,
    private val tmdbImageService: TmdbImageService,
    private val selectorEpisodeCacheRepository: SelectorMediaSourceEpisodeCacheRepository,
    private val webSessionManager: WebSessionManager,
    private val playbackAutomationGate: PlaybackAutomationGate,
) : AbstractViewModel() {

    val player: MediampPlayer =
        playerStateFactory.create(context, backgroundScope.coroutineContext)

    private val playbackInteraction = TvPlaybackInteractionState()
    private val observeStats = MutableStateFlow(false)
    private val playerOptions = MutableStateFlow(TvPlayerOptionsState())
    private val danmakuMatch = MutableStateFlow(TvDanmakuMatchState())
    private var matchingJob: Job? = null
    private val sourceSelection = MutableStateFlow(TvSourceSelectionState())
    private val resolvingCaptchaSources = MutableStateFlow<Set<String>>(emptySet())
    private val events = Channel<TvEpisodeEvent>(Channel.BUFFERED)
    val actionEvents = events.receiveAsFlow()
    private val episodeStills = MutableStateFlow<Map<Int, String>>(emptyMap())
    private val playbackSpeedOverride = MutableStateFlow<Float?>(null)
    private val playbackSpeedFlow: Flow<Float> =
        combine(settingsRepository.videoScaffoldConfig.flow, playbackSpeedOverride) { config, override ->
            override ?: config.playbackSpeed
        }.distinctUntilChanged()
    private val videoEnhancement = createVideoEnhancementController(
        player,
        settingsRepository.playerKernelConfig.flow,
        backgroundScope.coroutineContext,
    )
    private val autoSkip = TvAutoSkipController()
    private var pausedByLifecycle = false

    private val episodeCollectionsFlow = episodeCollectionRepository
        .subjectEpisodeCollectionInfosFlow(subjectId)
        .shareIn(backgroundScope, SharingStarted.WhileSubscribed(5_000), replay = 1)

    private val subjectCollectionFlow = subjectCollectionRepository
        .subjectCollectionFlow(subjectId)
        .shareIn(backgroundScope, SharingStarted.WhileSubscribed(5_000), replay = 1)

    @OptIn(UnsafeEpisodeSessionApi::class)
    private val fetchPlayState = EpisodeFetchSelectPlayState(
        subjectId, initialEpisodeId, player, backgroundScope,
        extensions = listOf(
            PlaybackSpeedExtension.Factory(playbackSpeedFlow),
            RememberPlayProgressExtension,
            WatchTogetherPlayerExtension,
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
            ObserveWebMediaSourcePreferenceExtension,
        ),
        koin,
        sharingStarted = SharingStarted.WhileSubscribed(5_000),
    )

    // region 页面状态

    /** 播放页顶部两行标题: 条目名 / 「第 NN 集 集标题」(对齐参考版). */


    @OptIn(UnsafeEpisodeSessionApi::class)
    private val titleFlow: StateFlow<TvEpisodeTitle> = fetchPlayState.infoBundleFlow
        .filterNotNull()
        .map { bundle ->
            val episode = bundle.episodeCollectionInfo.episodeInfo
            TvEpisodeTitle(
                subjectName = bundle.subjectCollectionInfo.subjectInfo.displayName,
                episodeSort = episode.sort.toString(),
                episodeName = episode.nameCn.ifBlank { episode.name },
            )
        }
        .stateIn(backgroundScope, SharingStarted.WhileSubscribed(5_000), TvEpisodeTitle("", ""))

    /** 当前选中数据源名 (播放器底栏展示). */
    @OptIn(UnsafeEpisodeSessionApi::class)
    private val currentMediaLabel: StateFlow<String?> = fetchPlayState.mediaSelectorFlow
        .transformLatest { selector ->
            if (selector == null) {
                emit(null)
            } else {
                emitAll(selector.selected.map { it?.properties?.alliance })
            }
        }
        .stateIn(backgroundScope, SharingStarted.WhileSubscribed(5_000), null)

    private val videoLoadingState: StateFlow<VideoLoadingState> =
        fetchPlayState.playerSession.videoLoadingState

    /** 选集条条目 (§8.3): 集序号 + 标题 + 已看标记. */

    private val episodeStripFlow: StateFlow<List<TvStripEpisode>> =
        combine(episodeCollectionsFlow, episodeStills, subjectCollectionFlow) { list, stills, subject ->
            list.map { collection ->
                val info = collection.episodeInfo
                TvStripEpisode(
                    episodeId = collection.episodeId,
                    sort = info.sort.toString(),
                    title = info.nameCn.ifBlank { info.name },
                    watched = collection.collectionType == UnifiedCollectionType.DONE,
                    stillUrl = stills[collection.episodeId],
                    isKnownBroadcast = info.isKnownCompleted(subject.recurrence),
                )
            }
        }
            .stateIn(backgroundScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 当前播放的分集 (切集后随会话切换). */
    @OptIn(UnsafeEpisodeSessionApi::class)
    private val currentEpisodeIdFlow: StateFlow<Int> = fetchPlayState.episodeIdFlow
        .stateIn(backgroundScope, SharingStarted.WhileSubscribed(5_000), initialEpisodeId)

    // endregion

    // region 数据源选择 (§8.1: 仅 WEB 源; TV 未装配缓存/torrent, 双保险过滤)

    @OptIn(UnsafeEpisodeSessionApi::class)
    private val selectedMedia: StateFlow<Media?> = fetchPlayState.mediaSelectorFlow
        .flatMapLatest { selector -> selector?.selected ?: flowOf(null) }
        .stateIn(backgroundScope, SharingStarted.WhileSubscribed(5_000), null)

    @OptIn(UnsafeEpisodeSessionApi::class)
    private fun selectMedia(media: Media, requestId: Long) {
        backgroundScope.launch {
            fetchPlayState.mediaSelectorFlow.filterNotNull().first().select(media)
            events.send(TvEpisodeEvent.MediaSelected(requestId))
        }
    }

    // endregion

    // region 辅助内容: 推荐为条目级, 评论随当前集, 弹幕为已加载列表


    private val recommendationsFlow: StateFlow<List<SubjectRecommendation>?> = flow<List<SubjectRecommendation>?> {
        emit(getSubjectRecommendations(subjectId))
    }
        .catch { emit(emptyList()) }
        // A player's subject does not change. Keep the same recommendation items and focus keys on reopen.
        .stateIn(backgroundScope, SharingStarted.Lazily, null)

    /** 当前集的评论 (只读, §1.2); 切集自动换源. */
    val episodeCommentsPager: Flow<PagingData<EpisodeComment>> = currentEpisodeIdFlow
        .flatMapLatest { episodeCommentRepository.subjectEpisodeCommentsPager(it.toLong()) }
        .cachedIn(backgroundScope)

    /** 已加载弹幕 (新→旧; Repopulate 重置 + Add 头插, 面板 reverseLayout 吸底展示). */
    private val danmakuList = MutableStateFlow<List<DanmakuPresentation>>(emptyList())
    private val danmakuListFlow: StateFlow<List<DanmakuPresentation>> = danmakuList

    // endregion

    // region 播放器能力 (mediamp features)

    private val playbackSpeedFeature get() = player.features[PlaybackSpeed]
    private val aspectRatioFeature get() = player.features[VideoAspectRatio]

    private val playbackSpeedStateFlow: StateFlow<Float> =
        (playbackSpeedFeature?.valueFlow ?: flowOf(1f))
            .stateIn(backgroundScope, SharingStarted.WhileSubscribed(5_000), playbackSpeedFeature?.value ?: 1f)

    private val aspectRatioModeFlow: StateFlow<AspectRatioMode> =
        aspectRatioFeature?.mode
            ?: MutableStateFlow(AspectRatioMode.FIT)

    @OptIn(ExperimentalMediampApi::class)
    private val bufferedFractionFlow: StateFlow<Float> =
        (player.features[Buffering]?.bufferedPercentage ?: flowOf(0))
            .map { it / 100f }
            .stateIn(backgroundScope, SharingStarted.WhileSubscribed(5_000), 0f)

    /** 确认键按住 2.5x 快进 (附录 A: 长按 500ms, 松开还原原倍速). */
    private var speedBeforeHold: Float? = null

    private fun setSpeedHold(engaged: Boolean) {
        if (engaged && !canControlPlayback()) return
        val feature = playbackSpeedFeature ?: return
        playbackInteraction.setSpeedHolding(engaged)
        if (engaged) {
            if (speedBeforeHold == null) speedBeforeHold = feature.value
            feature.set(playerOptions.value.videoConfig.fastForwardSpeed)
        } else {
            speedBeforeHold?.let { feature.set(it) }
            speedBeforeHold = null
        }
    }

    private fun cycleAspectRatio() {
        val feature = aspectRatioFeature ?: return
        val modes = AspectRatioMode.entries
        feature.setMode(modes[(modes.indexOf(feature.mode.value) + 1) % modes.size])
    }

    // endregion

    // region 弹幕 (接线拷自手机 EpisodeViewModel, atv-architecture.md §8.3)

    @OptIn(UnsafeEpisodeSessionApi::class)
    private val episodeDanmakuLoader = EpisodeDanmakuLoader(
        player = player,
        selectedMedia = fetchPlayState.mediaSelectorFlow.transformLatest {
            if (it == null) {
                emit(null)
            } else {
                emitAll(it.selected)
            }
        },
        bundleFlow = fetchPlayState.infoBundleFlow.filterNotNull().distinctUntilChanged(),
        danmakuRepository = danmakuRepository,
        getDanmakuRegexFilterListFlowUseCase = getDanmakuRegexFilterListFlowUseCase,
        backgroundScope,
        sharingStarted = SharingStarted.WhileSubscribed(5_000),
    )

    private val danmakuEventFlow: Flow<TvUIDanmakuEvent> = danmakuRepository.selfId.flatMapLatest { selfId ->
        fun createDanmakuPresentation(data: DanmakuInfo, selfId: String?) =
            DanmakuPresentation(data, isSelf = selfId == data.senderId)

        episodeDanmakuLoader.danmakuEventFlow.mapNotNull { event ->
            when (event) {
                is DanmakuEvent.Add -> {
                    val data = event.danmaku
                    if (data.text.isBlank()) {
                        null
                    } else {
                        TvUIDanmakuEvent.Add(createDanmakuPresentation(data, selfId))
                    }
                }

                is DanmakuEvent.Repopulate -> {
                    TvUIDanmakuEvent.Repopulate(
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
    }.shareIn(backgroundScope, SharingStarted.WhileSubscribed(5_000), replay = 1)

    private val danmakuConfigState = mutableStateOf(DanmakuConfig.Default)
    val danmakuHostState = DanmakuHostState(danmakuConfigState, DanmakuTrackProperties.Default)

    // endregion

    /** Web 源解析器 (WebView), 页面需调用其 ComposeContent() 完成挂载 (同手机 EpisodePage). */
    val mediaResolver: MediaResolver get() = fetchPlayState.playerSession.mediaResolver

    /** 启动扩展系统 (AutoSelect/自动连播/进度记忆等), 由页面首帧调用 (同手机 EpisodePage). */
    private fun onUIReady() {
        fetchPlayState.onUIReady()
    }

    private fun togglePause() {
        if (!canControlPlayback()) return
        player.togglePlayWhenReady()
    }

    private fun seekTo(positionMillis: Long) {
        if (!canControlPlayback()) return
        val duration = player.mediaProperties.value?.durationMillis?.takeIf { it > 0 } ?: Long.MAX_VALUE
        player.seekTo(positionMillis.coerceIn(0, duration))
    }

    private fun switchEpisode(episodeId: Int) {
        if (playbackAutomationGate.suppressed.value) {
            showMessage(TvPlayerMessage.FollowingHost)
            return
        }
        backgroundScope.launch { fetchPlayState.switchEpisode(episodeId) }
    }

    /** 上一集 (-1) / 下一集 (+1); 到列表边界则不动 (媒体键 RW/FF, §8.2 全局键). */
    private fun switchToNeighborEpisode(offset: Int) {
        if (playbackAutomationGate.suppressed.value) {
            showMessage(TvPlayerMessage.FollowingHost)
            return
        }
        backgroundScope.launch {
            val list = episodeCollectionsFlow.first()
            val index = list.indexOfFirst { it.episodeId == currentEpisodeIdFlow.value }
            if (index == -1) return@launch
            val target = list.getOrNull(index + offset) ?: return@launch
            if (offset > 0 && !target.episodeInfo.isKnownCompleted(subjectCollectionFlow.first().recurrence)) return@launch
            fetchPlayState.switchEpisode(target.episodeId)
        }
    }

    private val navigation = TvNavigationEvents()
    val navigationEvents = navigation.events
    private var uiReady = false

    private val positionFlow = flow {
        while (true) {
            emit(withContext(Dispatchers.Main) { player.getCurrentPositionMillis() })
            delay(500)
        }
    }.stateIn(backgroundScope, SharingStarted.WhileSubscribed(5_000), 0L)

    private val panelState = combine(recommendationsFlow, episodeDanmakuLoader.fetchResults) { recommendations, fetchResults ->
        TvPlayerPanelState(
            recommendations = recommendations.orEmpty(),
            recommendationsLoading = recommendations == null,
            // Keep the same loading/empty distinction as the shared DanmakuListStateProducer.
            danmakuLoading = fetchResults.isEmpty(),
        )
    }

    /** Collected only while the UI displays the live list. */
    val danmakuListItems: StateFlow<List<DanmakuPresentation>> = danmakuListFlow

    private val options =
        combine(bufferedFractionFlow, playbackSpeedStateFlow, aspectRatioModeFlow) { buffer, speed, aspect ->
            Triple(buffer, speed, aspect)
        }
    private data class Selection(
        val episodes: List<TvStripEpisode>,
        val currentEpisodeId: Int,
        val selectedMedia: Media?,
    )

    private val selection =
        combine(episodeStripFlow, currentEpisodeIdFlow, selectedMedia) { episodes, episodeId, selected ->
            Selection(episodes, episodeId, selected)
        }
    val uiState = combine(
        titleFlow,
        player.state,
        videoLoadingState,
        currentMediaLabel,
        player.mediaProperties,
    ) { title, playback, loading, label, properties ->
        TvEpisodeUiState(
            title = title,
            interaction = playbackInteraction,
            playerState = playback,
            loadingState = loading,
            mediaLabel = label,
            durationMillis = properties?.durationMillis ?: 0,
        )
    }.combine(options) { state, options ->
        state.copy(bufferedFraction = options.first, playbackSpeed = options.second, aspectRatioMode = options.third)
    }.combine(selection) { state, selected ->
        state.copy(
            episodes = selected.episodes,
            currentEpisodeId = selected.currentEpisodeId,
            selectedMedia = selected.selectedMedia,
        )
    }
        .combine(panelState) { state, panel -> state.copy(panel = panel) }
        .combine(positionFlow) { state, position -> state.copy(positionMillis = position) }
        .combine(sourceSelection) { state, sources -> state.copy(sources = sources) }
        .combine(playerOptions) { state, options -> state.copy(options = options) }
        .combine(danmakuMatch) { state, matching -> state.copy(danmakuMatch = matching) }
        .stateIn(
            backgroundScope,
            SharingStarted.WhileSubscribed(5_000),
            TvEpisodeUiState(
                currentEpisodeId = initialEpisodeId,
                interaction = playbackInteraction,
                panel = TvPlayerPanelState(recommendationsLoading = true, danmakuLoading = true),
            ),
        )

    fun onIntent(intent: TvEpisodeIntent): Boolean {
        when (intent) {
            TvEpisodeIntent.UiReady -> if (!uiReady) {
                uiReady = true
                onUIReady()
            }

            is TvEpisodeIntent.SelectEpisode -> {
                if (episodeStripFlow.value.none { it.episodeId == intent.episodeId }) return true
                if (!canControlPlayback()) return true
                if (intent.episodeId != currentEpisodeIdFlow.value) switchEpisode(intent.episodeId)
                events.trySend(TvEpisodeEvent.EpisodeSelected(intent.episodeId))
            }

            is TvEpisodeIntent.SelectMedia -> {
                if (sourceSelection.value.groups.none { group -> group.items.any { it.media == intent.media } }) return true
                selectMedia(intent.media, intent.requestId)
            }

            TvEpisodeIntent.ToggleDanmaku -> runAction { settingsRepository.danmakuEnabled.update { !this } }
            is TvEpisodeIntent.ObserveStats -> observeStats.value = intent.enabled
            TvEpisodeIntent.OpenLogin -> navigation.emit(TvNavigationEvent.Login)
            TvEpisodeIntent.CancelAutoSkip -> cancelAutoSkip()
            TvEpisodeIntent.TogglePause -> togglePause()
            is TvEpisodeIntent.PreviewBy -> {
                val duration = player.mediaProperties.value?.durationMillis ?: 0
                if (duration > 0) {
                    val base = playbackInteraction.scrubMillis ?: player.getCurrentPositionMillis()
                    playbackInteraction.setPreview((base + intent.deltaMillis).coerceIn(0, duration))
                }
            }
            is TvEpisodeIntent.PreviewSeek -> {
                val duration = player.mediaProperties.value?.durationMillis ?: 0
                playbackInteraction.setPreview(intent.positionMillis?.coerceIn(0, duration.coerceAtLeast(0)))
            }
            is TvEpisodeIntent.SeekTo -> seekTo(intent.positionMillis)
            is TvEpisodeIntent.SwitchNeighbor -> switchToNeighborEpisode(intent.offset)
            TvEpisodeIntent.NextEpisode -> switchToNeighborEpisode(1)
            is TvEpisodeIntent.HoldSpeed -> setSpeedHold(intent.engaged)
            TvEpisodeIntent.ReleaseHeldSpeed -> setSpeedHold(false)
            TvEpisodeIntent.CycleAspectRatio -> cycleAspectRatio()
            is TvEpisodeIntent.CancelDanmakuMatch -> {
                if (danmakuMatch.value.requestId == intent.requestId) matchingJob?.cancel()
            }
            TvEpisodeIntent.BackDanmakuMatch -> {
                matchingJob?.cancel()
                danmakuMatch.update { it.copy(selectedSubject = null, episodes = emptyList(), loading = false, error = null) }
            }

            is TvEpisodeIntent.SetSpeed -> setSpeed(intent.speed)
            is TvEpisodeIntent.AdjustSpeed -> setSpeed(playbackSpeedStateFlow.value + intent.direction * .25f)
            is TvEpisodeIntent.SetDefaultSpeed -> runAction {
                settingsRepository.videoScaffoldConfig.update {
                    copy(
                        playbackSpeed = intent.speed.coerceIn(
                            minPlaybackSpeed,
                            maxPlaybackSpeed,
                        ),
                    )
                }
            }

            is TvEpisodeIntent.SetHoldSpeed -> runAction {
                settingsRepository.videoScaffoldConfig.update {
                    copy(
                        fastForwardSpeed = intent.speed.coerceIn(
                            minPlaybackSpeed,
                            maxPlaybackSpeed,
                        ),
                    )
                }
            }

            TvEpisodeIntent.ToggleRememberSpeed -> runAction {
                settingsRepository.videoScaffoldConfig.update {
                    copy(
                        rememberPlaybackSpeed = !rememberPlaybackSpeed,
                        playbackSpeed = if (!rememberPlaybackSpeed) playbackSpeedStateFlow.value else playbackSpeed,
                    )
                }
            }

            is TvEpisodeIntent.SelectSubtitle -> {
                val tracks = player.subtitleTracks
                if (intent.id == null) tracks?.select(null)
                else runAction {
                    tracks?.candidates?.first()?.firstOrNull { it.id.toString() == intent.id }
                        ?.let { tracks.select(it) }
                }
                events.trySend(TvEpisodeEvent.SubtitleSelected(intent.requestId))
            }

            is TvEpisodeIntent.SetEnhancement -> {
                videoEnhancement?.setMode(intent.mode)
            }

            is TvEpisodeIntent.ViewportChanged -> videoEnhancement?.setViewportSize(intent.width, intent.height)
            is TvEpisodeIntent.ForegroundChanged -> {
                if (!intent.foreground) {
                    setSpeedHold(false)
                    pausedByLifecycle = player.state.value.playWhenReady
                    if (pausedByLifecycle) player.pause()
                } else if (pausedByLifecycle) {
                    pausedByLifecycle = false
                    if (!playbackAutomationGate.suppressed.value) player.play()
                }
            }

            is TvEpisodeIntent.AdjustDanmaku -> adjustDanmaku(intent)
            is TvEpisodeIntent.MatchDanmaku -> {
                if (episodeDanmakuLoader.getInteractiveDanmakuFetcherOrNull(intent.providerId)?.supportsInteractiveMatching != true) return true
                danmakuMatch.value =
                    TvDanmakuMatchState(providerId = intent.providerId, query = titleFlow.value.subjectName, requestId = intent.requestId)
                searchDanmaku()
            }

            is TvEpisodeIntent.DanmakuQuery -> danmakuMatch.update { it.copy(query = intent.value) }
            TvEpisodeIntent.SearchDanmaku -> searchDanmaku()
            is TvEpisodeIntent.SelectDanmakuSubject -> matchAction { provider ->
                val subject = danmakuMatch.value.subjects.find { it.id == intent.id } ?: return@matchAction
                danmakuMatch.update { it.copy(selectedSubject = subject, episodes = emptyList()) }
                val episodes = provider.fetchEpisodeList(subject)
                danmakuMatch.update { it.copy(episodes = episodes) }
            }

            is TvEpisodeIntent.SelectDanmakuEpisode -> matchAction { provider ->
                val state = danmakuMatch.value
                val subject = state.selectedSubject ?: return@matchAction
                val episode = state.episodes.find { it.id == intent.id } ?: return@matchAction
                val results = provider.fetchDanmakuList(subject, episode)
                episodeDanmakuLoader.overrideResults(state.providerId ?: return@matchAction, results)
                events.send(TvEpisodeEvent.DanmakuMatched(state.requestId))
                showMessage(TvPlayerMessage.DanmakuMatched)
            }

            is TvEpisodeIntent.ToggleDanmakuSource -> playerOptions.value.danmakuOrigins.find { it.serviceId == intent.serviceId }
                ?.let {
                    episodeDanmakuLoader.setEnabled(it.serviceId, !it.enabled)
                }

            is TvEpisodeIntent.ShiftDanmakuSource -> playerOptions.value.danmakuOrigins.find { it.serviceId == intent.serviceId }
                ?.let {
                    episodeDanmakuLoader.setShiftMillis(
                        it.serviceId,
                        intent.deltaMillis?.let { delta -> (it.shiftMillis + delta).coerceIn(-30_000, 30_000) } ?: 0,
                    )
                }

            is TvEpisodeIntent.SetCollection -> setCollection(intent.type, intent.requestId)
            is TvEpisodeIntent.MarkAllWatched -> collectionAction {
                episodeCollectionRepository.setAllEpisodesWatched(subjectId)
                events.send(TvEpisodeEvent.AllEpisodesWatched(intent.requestId))
            }

            is TvEpisodeIntent.SetEpisodeWatched -> runAction {
                episodeCollectionRepository.setEpisodeCollectionType(
                    subjectId,
                    intent.episodeId,
                    if (intent.watched) UnifiedCollectionType.DONE else UnifiedCollectionType.WISH,
                )
                events.send(TvEpisodeEvent.EpisodeWatched(intent.episodeId, intent.requestId))
            }

            is TvEpisodeIntent.RetrySources -> retrySources(intent.instanceId)
            is TvEpisodeIntent.ResolveSourceCaptcha -> resolveSourceCaptcha(intent.instanceId)
            is TvEpisodeIntent.RetryPlayback -> retryPlayback(intent.requestId)
            is TvEpisodeIntent.OpenRecommendation -> {
                val recommendation = intent.recommendation
                val targetSubjectId = recommendation.tvNavigationSubjectId
                if (targetSubjectId != null) {
                    navigation.emit(
                        TvNavigationEvent.Subject(
                            targetSubjectId,
                            SubjectDetailPlaceholder(
                                id = targetSubjectId,
                                name = recommendation.name,
                                nameCN = recommendation.nameCn.orEmpty(),
                                coverUrl = recommendation.imageUrl,
                            ),
                        ),
                    )
                }
            }
        }
        return true
    }

    private var messageJob: Job? = null
    private fun showMessage(message: TvPlayerMessage) {
        messageJob?.cancel()
        playerOptions.update { it.copy(message = message) }
        messageJob = backgroundScope.launch {
            delay(4_000)
            playerOptions.update { it.copy(message = null) }
        }
    }

    private fun runAction(block: suspend () -> Unit) = backgroundScope.launch(Dispatchers.Main) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            showMessage(TvPlayerMessage.OperationFailed)
        }
    }

    private fun setSpeed(speed: Float) {
        if (!canControlPlayback()) return
        val config = playerOptions.value.videoConfig
        val value = speed.coerceIn(config.minPlaybackSpeed, config.maxPlaybackSpeed)
        playbackSpeedOverride.value = value
        playbackSpeedFeature?.set(value)
        if (config.rememberPlaybackSpeed) runAction { settingsRepository.videoScaffoldConfig.update { copy(playbackSpeed = value) } }
    }

    private fun canControlPlayback(): Boolean {
        if (!playbackAutomationGate.suppressed.value) return true
        showMessage(TvPlayerMessage.FollowingHost)
        return false
    }

    private fun setCollection(type: UnifiedCollectionType, requestId: Long) = collectionAction {
        subjectCollectionRepository.setSubjectCollectionTypeOrDelete(
            subjectId,
            type.takeUnless { it == UnifiedCollectionType.NOT_COLLECTED },
        )
        playerOptions.update { it.copy(collectionType = type) }
        events.send(TvEpisodeEvent.CollectionChanged(type, requestId))
    }

    private fun collectionAction(block: suspend () -> Unit) {
        if (playerOptions.value.collectionBusy) return
        playerOptions.update { it.copy(collectionBusy = true) }
        runAction {
            try {
                block()
            } finally {
                playerOptions.update { it.copy(collectionBusy = false) }
            }
        }
    }

    private fun adjustDanmaku(intent: TvEpisodeIntent.AdjustDanmaku) = runAction {
        settingsRepository.danmakuConfig.update { adjustForTv(intent.property, intent.direction) }
    }

    private fun cancelAutoSkip() {
        autoSkip.cancel()
        playerOptions.update { it.copy(skipPrompt = null) }
    }

    private fun searchDanmaku() = matchAction { provider ->
        val query = danmakuMatch.value.query.trim()
        if (query.isBlank()) {
            danmakuMatch.update { it.copy(error = TvPlayerError.EmptyDanmakuQuery) }
            return@matchAction
        }
        danmakuMatch.update { it.copy(selectedSubject = null, subjects = emptyList(), episodes = emptyList()) }
        val subjects = provider.fetchSubjectList(query)
        danmakuMatch.update { it.copy(subjects = subjects, searched = true) }
    }

    private fun matchAction(block: suspend (MatchingDanmakuProvider) -> Unit) {
        val provider = episodeDanmakuLoader.getInteractiveDanmakuFetcherOrNull(danmakuMatch.value.providerId)
            ?.startInteractiveMatch() ?: return
        val previous = matchingJob
        previous?.cancel()
        matchingJob = backgroundScope.launch(Dispatchers.Main) {
            previous?.join()
            danmakuMatch.update { it.copy(loading = true, error = null) }
            try {
                block(provider)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                danmakuMatch.update { it.copy(error = TvPlayerError.DanmakuSearchFailed) }
            } finally {
                danmakuMatch.update { it.copy(loading = false) }
            }
        }
    }

    @OptIn(UnsafeEpisodeSessionApi::class)
    private fun retrySources(instanceId: String?) = runAction {
        val session = fetchPlayState.episodeSessionFlow.value
        if (session.infoLoadErrorStateFlow.value != null) {
            session.restartLoad()
            return@runAction
        }
        val bundle = session.fetchSelectFlow.filterNotNull().first()
        if (instanceId == null) {
            selectorEpisodeCacheRepository.clearByRequestedSubject(subjectId)
            bundle.mediaFetchSession.restartAll()
        } else {
            val source =
                bundle.mediaFetchSession.mediaSourceResults.find { it.instanceId == instanceId } ?: return@runAction
            selectorEpisodeCacheRepository.clearByRequestedSubjectAndSource(subjectId, source.mediaSourceId)
            source.restart()
        }
    }

    @OptIn(UnsafeEpisodeSessionApi::class)
    private fun resolveSourceCaptcha(instanceId: String) = runAction {
        if (instanceId in resolvingCaptchaSources.value || !webSessionManager.isInteractiveSupported) return@runAction
        val bundle = fetchPlayState.episodeSessionFlow.value.fetchSelectFlow.filterNotNull().first()
        val source = bundle.mediaFetchSession.mediaSourceResults.find { it.instanceId == instanceId } ?: return@runAction
        val request = (source.state.value as? MediaSourceFetchState.CaptchaRequired)?.request ?: return@runAction
        resolvingCaptchaSources.update { it + instanceId }
        try {
            if (webSessionManager.solve(request, interactive = true) == SolveOutcome.Solved) {
                selectorEpisodeCacheRepository.clearByRequestedSubjectAndSource(subjectId, source.mediaSourceId)
                source.restart()
            }
        } finally {
            resolvingCaptchaSources.update { it - instanceId }
        }
    }

    @OptIn(UnsafeEpisodeSessionApi::class)
    private fun retryPlayback(requestId: Long) = runAction {
        selectorEpisodeCacheRepository.clearByRequestedSubject(subjectId)
        fetchPlayState.switchEpisode(currentEpisodeIdFlow.value)
        events.send(TvEpisodeEvent.PlaybackRetried(requestId))
    }

    @OptIn(UnsafeEpisodeSessionApi::class)
    private fun observePlayerOptions() {
        backgroundScope.launch(Dispatchers.Main) {
            currentEpisodeIdFlow.collect {
                matchingJob?.cancel()
                danmakuMatch.value = TvDanmakuMatchState()
                playbackInteraction.setPreview(null)
            }
        }
        backgroundScope.launch {
            combine(
                settingsRepository.videoScaffoldConfig.flow,
                settingsRepository.danmakuEnabled.flow,
                settingsRepository.danmakuConfig.flow,
            ) { video, enabled, danmaku ->
                playerOptions.update { it.copy(videoConfig = video, danmakuEnabled = enabled, danmakuConfig = danmaku) }
            }.collect()
        }
        backgroundScope.launch {
            val mode = settingsRepository.videoScaffoldConfig.flow.first().videoEnhancementDefaultMode
            videoEnhancement?.setMode(VideoEnhancementMode.valueOf(mode.name))
            videoEnhancement?.mode?.collect { mode -> playerOptions.update { it.copy(enhancementMode = mode) } }
        }
        backgroundScope.launch {
            subjectCollectionFlow.collect { collection -> playerOptions.update { it.copy(collectionType = collection.collectionType) } }
        }
        backgroundScope.launch {
            try {
                val collection = subjectCollectionFlow.first()
                val stills = tmdbImageService.getEpisodeStills(
                    subjectId,
                    collection.subjectInfo.name,
                    "zh-CN",
                    newestWantedAirDate = collection.episodes.newestAiredDateStringOrNull(),
                )
                    .matchToEpisodes(collection.episodes).mapNotNull { (id, media) -> media.stillUrl?.let { id to it } }
                    .toMap()
                episodeStills.value = stills
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) { /* Titles remain usable without artwork. */
            }
        }
        backgroundScope.launch {
            fetchPlayState.episodeSessionFlow.flatMapLatest { session ->
                val groupsFlow = session.fetchSelectFlow.flatMapLatest { bundle ->
                    if (bundle == null) flowOf(null) else tvSourceGroups(
                        bundle.mediaFetchSession, bundle.mediaSelector, webSessionManager.isInteractiveSupported,
                    )
                }
                combine(groupsFlow, session.infoLoadErrorStateFlow, resolvingCaptchaSources) { groups, error, resolving ->
                    TvSourceSelectionState(
                        groups = groups.orEmpty().map { it.copy(isResolvingCaptcha = it.instanceId in resolving) },
                        loading = error == null && (groups == null || groups.any { it.loading }),
                        error = error?.let { TvPlayerError.SourceInfoUnavailable },
                    )
                }
            }.collect { source -> sourceSelection.value = source }
        }
        backgroundScope.launch(Dispatchers.Main) {
            player.subtitleTracks?.let { tracks ->
                combine(tracks.candidates, tracks.selected) { candidates, selected ->
                    playerOptions.update {
                        it.copy(
                            supportsSubtitles = true,
                            subtitles = candidates.map { TvSubtitleOption(it.id.toString(), it.subtitleLanguage) },
                            selectedSubtitleId = selected?.id?.toString(),
                        )
                    }
                }.collect()
            }
        }
        backgroundScope.launch {
            episodeDanmakuLoader.fetchResults.collect { origins ->
                playerOptions.update {
                    it.copy(
                        danmakuOrigins = origins.map { origin ->
                            TvDanmakuOrigin(
                                origin.serviceId,
                                origin.providerId,
                                origin.matchInfo.method,
                                origin.matchInfo.count,
                                origin.config.enabled,
                                origin.config.shiftMillis,
                                episodeDanmakuLoader.getInteractiveDanmakuFetcherOrNull(origin.providerId)?.supportsInteractiveMatching == true,
                            )
                        },
                    )
                }
            }
        }
        backgroundScope.launch {
            observeStats
                .collectLatest { visible ->
                    if (visible) androidPlayerStatsFlow(player).collect { stats -> playerOptions.update { it.copy(stats = stats) } }
                }
        }
        observePreview()
        observeAutoSkip()
    }

    private fun observePreview() {
        val feature = player.features[FramePreview] ?: return
        val preview = MediaProgressFramePreviewState(fetchFrame = { position ->
            try {
                feature.getPreviewFrame(position, 384, 216)?.let {
                    Bitmap.createBitmap(it.pixels, it.width, it.height, Bitmap.Config.ARGB_8888).asImageBitmap()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
        })
        backgroundScope.launch(Dispatchers.Main) {
            settingsRepository.videoScaffoldConfig.flow.map { it.enableFramePreview }.distinctUntilChanged()
                .collectLatest { enabled ->
                    preview.onMediaChanged()
                    playerOptions.update { it.copy(previewAvailable = enabled, preview = null, previewLoading = false) }
                    if (!enabled) return@collectLatest
                    coroutineScope {
                        launch {
                            snapshotFlow { preview.frame to preview.isLoading }.collect { (frame, loading) ->
                                playerOptions.update { it.copy(preview = frame, previewLoading = loading) }
                            }
                        }
                        player.mediaData.collectLatest { data ->
                            preview.onMediaChanged()
                            if (data == null) return@collectLatest
                            coroutineScope {
                                launch { preview.prewarm(player.getCurrentPositionMillis()) }
                                snapshotFlow { playbackInteraction.scrubMillis }.distinctUntilChanged().collectLatest { position ->
                                    if (position == null) preview.onPreviewFinished() else preview.requestFrame(position)
                                }
                            }
                        }
                    }
                }
        }
    }

    @OptIn(UnsafeEpisodeSessionApi::class)
    private fun observeAutoSkip() {
        backgroundScope.launch {
            val server = currentEpisodeIdFlow.flatMapLatest { id ->
                autoSkipRepository.rulesFlow(id).onStart { emit(emptyList()) }.catch { emit(emptyList()) }
            }
            combine(
                server,
                player.mediaProperties,
                player.chapters ?: flowOf(emptyList()),
                settingsRepository.videoScaffoldConfig.flow,
            ) { times, properties, chapters, config ->
                val duration = properties?.durationMillis ?: 0L
                val length = when {
                    duration > 1_200_000 -> config.opEdSkipDuration.inWholeMilliseconds
                    duration > 600_000 -> 55_000L
                    else -> 0L
                }
                val remote = if (length == 0L) emptyList() else times.sorted()
                    .mapIndexed { index, offset -> TvChapter(if (index == 0) "OP" else "ED", offset, length) }
                (chapters.map {
                    TvChapter(
                        it.name,
                        it.offsetMillis,
                        it.durationMillis,
                    )
                } + remote).distinctBy { it.offsetMillis }.sortedBy { it.offsetMillis }
            }.collect { chapters -> playerOptions.update { it.copy(chapters = chapters) } }
        }
        backgroundScope.launch(Dispatchers.Main) {
            player.mediaData.collect {
                autoSkip.reset()
                playerOptions.update { it.copy(skipPrompt = null) }
            }
        }
        backgroundScope.launch(Dispatchers.Main) {
            while (true) {
                val options = playerOptions.value
                val firstEpisode =
                    episodeStripFlow.value.let { it.size > 1 && it.first().episodeId == currentEpisodeIdFlow.value }
                val prompt = autoSkip.update(
                    player.getCurrentPositionMillis(),
                    player.mediaProperties.value?.durationMillis ?: 0L,
                    options.chapters,
                    options.videoConfig.autoSkipOpEd && player.state.value.isPlaying && !firstEpisode && !playbackAutomationGate.suppressed.value && playbackInteraction.scrubMillis == null,
                    onSkip = ::seekTo,
                )
                playerOptions.update { it.copy(skipPrompt = prompt) }
                delay(200)
            }
        }
    }

    init {
        observePlayerOptions()
        backgroundScope.launch(Dispatchers.Main) {
            player.state.collect { danmakuHostState.setPaused(!it.isPlaying) }
        }
        backgroundScope.launch(Dispatchers.Main) {
            danmakuEventFlow.collect { event ->
                when (event) {
                    is TvUIDanmakuEvent.Add -> danmakuHostState.trySend(event.presentation)
                    is TvUIDanmakuEvent.Repopulate -> danmakuHostState.repopulate(
                        event.list,
                        event.currentPositionMillis,
                    )
                }
            }
        }
    }

    init {
        backgroundScope.launch {
            settingsRepository.danmakuConfig.flow.collect { danmakuConfigState.value = it }
        }
        backgroundScope.launch {
            // 弹幕列表面板数据: 与渲染层共享同一 danmakuEventFlow (shareIn), 不重复拉取
            danmakuEventFlow.collect { event ->
                when (event) {
                    is TvUIDanmakuEvent.Repopulate -> danmakuList.value = event.list.asReversed()
                    is TvUIDanmakuEvent.Add ->
                        danmakuList.value =
                            (listOf(event.presentation) + danmakuList.value).take(DANMAKU_LIST_CAP)
                }
            }
        }
        backgroundScope.launch {
            // 保证数据源会一直查询 (拷自手机 EpisodePageState): MediaFetchSession 是冷流,
            // 必须有订阅者 cumulativeResults 才会真正向各数据源发起请求, AutoSelect 才有候选可选.
            fetchPlayState.episodeSessionFlow.collectLatest { session ->
                session.fetchSelectFlow.flatMapLatest { bundle ->
                    bundle?.mediaFetchSession?.cumulativeResults ?: flowOf(emptyList())
                }.collect()
            }
        }
    }

    override fun onCleared() {
        setSpeedHold(false)
        videoEnhancement?.close()
        super.onCleared()
        backgroundScope.launch(NonCancellable + CoroutineName("TvEpisodeViewModel#onCleared")) {
            fetchPlayState.onClose()
        }
    }


    companion object {
        /** 弹幕列表面板保留上限. */
        const val DANMAKU_LIST_CAP = 500
    }
}
