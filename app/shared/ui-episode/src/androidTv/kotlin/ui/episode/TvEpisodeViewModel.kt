/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.tv.ui.episode

import androidx.compose.runtime.Stable
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
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
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.him188.ani.app.data.repository.episode.EpisodeCollectionRepository
import me.him188.ani.app.data.repository.media.SelectorMediaSourceEpisodeCacheRepository
import me.him188.ani.app.data.repository.subject.SubjectCollectionRepository
import me.him188.ani.app.data.repository.user.SettingsRepository
import me.him188.ani.app.domain.episode.EpisodeCompletionContext.isKnownCompleted
import me.him188.ani.app.domain.episode.SubjectRecommendation
import me.him188.ani.app.domain.episode.UnsafeEpisodeSessionApi
import me.him188.ani.app.domain.episode.episodeIdFlow
import me.him188.ani.app.domain.episode.infoBundleFlow
import me.him188.ani.app.domain.episode.mediaSelectorFlow
import me.him188.ani.app.domain.media.fetch.MediaSourceFetchState
import me.him188.ani.app.domain.mediasource.web.captcha.SolveOutcome
import me.him188.ani.app.domain.mediasource.web.captcha.WebSessionManager
import me.him188.ani.app.domain.player.VideoLoadingState
import me.him188.ani.app.navigation.SubjectDetailPlaceholder
import me.him188.ani.app.platform.ContextMP
import me.him188.ani.app.ui.danmaku.UIDanmakuEvent
import me.him188.ani.app.ui.subject.episode.EpisodeViewModel
import me.him188.ani.app.videoplayer.ui.androidPlayerStatsFlow
import me.him188.ani.app.videoplayer.ui.progress.createMediaProgressFramePreviewState
import me.him188.ani.app.videoplayer.ui.progress.subtitleLanguage
import me.him188.ani.danmaku.api.provider.MatchingDanmakuProvider
import me.him188.ani.danmaku.ui.DanmakuPresentation
import me.him188.ani.datasources.api.Media
import me.him188.ani.datasources.api.topic.UnifiedCollectionType
import me.him188.ani.tv.ui.episode.controls.TvSubtitleOption
import me.him188.ani.tv.ui.episode.danmaku.TvDanmakuMatchState
import me.him188.ani.tv.ui.episode.danmaku.TvDanmakuOrigin
import me.him188.ani.tv.ui.episode.danmaku.adjustForTv
import me.him188.ani.tv.ui.episode.playback.TvPlaybackInteractionState
import me.him188.ani.tv.ui.episode.playback.TvSkipPrompt
import me.him188.ani.tv.ui.episode.recommendation.tvNavigationSubjectId
import me.him188.ani.tv.ui.episode.source.TvSourceSelectionState
import me.him188.ani.tv.ui.episode.source.tvSourceGroups
import me.him188.ani.tv.ui.foundation.TvNavigationEvent
import me.him188.ani.tv.ui.foundation.TvNavigationEvents
import org.koin.core.Koin
import org.openani.mediamp.features.AspectRatioMode
import org.openani.mediamp.features.PlaybackSpeed
import org.openani.mediamp.features.VideoAspectRatio
import org.openani.mediamp.features.subtitleTracks
import org.openani.mediamp.togglePlayWhenReady

/**
 * 将共享播放状态映射为 TV 展示状态，并将遥控器 Intent 接入共享播放操作。
 */
@Stable
@OptIn(UnsafeEpisodeSessionApi::class)
class TvEpisodeViewModel(
    subjectId: Int,
    initialEpisodeId: Int,
    context: ContextMP,
    koin: Koin,
    private val episodeCollectionRepository: EpisodeCollectionRepository,
    private val subjectCollectionRepository: SubjectCollectionRepository,
    private val settingsRepository: SettingsRepository,
    private val selectorEpisodeCacheRepository: SelectorMediaSourceEpisodeCacheRepository,
    private val webSessionManager: WebSessionManager,
) : EpisodeViewModel(subjectId, initialEpisodeId, initialIsFullscreen = true, context = context, koin = koin) {
    private val resolvingCaptchaSources = MutableStateFlow<Set<String>>(emptySet())
    private val playbackInteraction = TvPlaybackInteractionState()
    override val isAutoSkipOpEdAllowed: Boolean
        get() = playbackInteraction.scrubMillis == null && player.state.value.isPlaying
    private val observeStats = MutableStateFlow(false)
    private val playerOptions = MutableStateFlow(TvPlayerOptionsState())
    private val danmakuMatch = MutableStateFlow(TvDanmakuMatchState())
    private var matchingJob: Job? = null
    private val sourceSelection = MutableStateFlow(TvSourceSelectionState())
    private val events = Channel<TvEpisodeEvent>(Channel.BUFFERED)
    val actionEvents = events.receiveAsFlow()
    private var pausedByLifecycle = false

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
        combine(episodeCollectionsFlow, subjectCollectionFlow) { list, subject ->
            list.map { collection ->
                val info = collection.episodeInfo
                TvStripEpisode(
                    episodeId = collection.episodeId,
                    sort = info.sort.toString(),
                    title = info.nameCn.ifBlank { info.name },
                    watched = collection.collectionType == UnifiedCollectionType.DONE,
                    stillUrl = info.imageLarge,
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

    private fun togglePause() {
        if (!canControlPlayback()) return
        player.togglePlayWhenReady()
    }

    private fun seekTo(positionMillis: Long) {
        if (!canControlPlayback()) return
        val duration = player.mediaProperties.value?.durationMillis?.takeIf { it > 0 } ?: Long.MAX_VALUE
        player.seekTo(positionMillis.coerceIn(0, duration))
    }

    private fun requestEpisode(episodeId: Int) {
        if (playbackAutomationSuppressed.value) {
            showMessage(TvPlayerMessage.FollowingHost)
            return
        }
        backgroundScope.launch { switchEpisode(episodeId) }
    }

    /** 上一集 (-1) / 下一集 (+1); 到列表边界则不动 (媒体键 RW/FF, §8.2 全局键). */
    private fun switchToNeighborEpisode(offset: Int) {
        if (playbackAutomationSuppressed.value) {
            showMessage(TvPlayerMessage.FollowingHost)
            return
        }
        backgroundScope.launch {
            val list = episodeCollectionsFlow.first()
            val index = list.indexOfFirst { it.episodeId == currentEpisodeIdFlow.value }
            if (index == -1) return@launch
            val target = list.getOrNull(index + offset) ?: return@launch
            if (offset > 0 && !target.episodeInfo.isKnownCompleted(subjectCollectionFlow.first().recurrence)) return@launch
            switchEpisode(target.episodeId)
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

    private val panelRecommendations = recommendationsFlow
        .map<List<SubjectRecommendation>, List<SubjectRecommendation>?> { it }
        .onStart { emit(null) }
        .catch { emit(emptyList()) }

    private val panelState = combine(panelRecommendations, episodeDanmakuLoader.fetchResults) { recommendations, fetchResults ->
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
        combine(playbackSpeedStateFlow, aspectRatioModeFlow) { speed, aspect -> speed to aspect }
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
        state.copy(playbackSpeed = options.first, aspectRatioMode = options.second)
    }.combine(selection) { state, selected ->
        state.copy(
            episodes = selected.episodes,
            currentEpisodeId = selected.currentEpisodeId,
            selectedMedia = selected.selectedMedia,
        )
    }
        .combine(panelState) { state, panel -> state.copy(panel = panel) }
        .combine(positionFlow) { state, position -> state.copy(positionMillis = position) }
        .combine(cacheProgressInfoFlow) { state, cache -> state.copy(cacheProgress = cache) }
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
                if (intent.episodeId != currentEpisodeIdFlow.value) requestEpisode(intent.episodeId)
                events.trySend(TvEpisodeEvent.EpisodeSelected(intent.episodeId))
            }

            is TvEpisodeIntent.SelectMedia -> {
                if (sourceSelection.value.groups.none { group -> group.items.any { it.media == intent.media } }) return true
                selectMedia(intent.media, intent.requestId)
            }

            TvEpisodeIntent.ToggleDanmaku -> setDanmakuEnabled(!playerOptions.value.danmakuEnabled)
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
                    if (!playbackAutomationSuppressed.value) player.play()
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
                    setDanmakuSourceEnabled(it.serviceId, !it.enabled)
                }

            is TvEpisodeIntent.ShiftDanmakuSource -> playerOptions.value.danmakuOrigins.find { it.serviceId == intent.serviceId }
                ?.let {
                    setDanmakuSourceShiftMillis(
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
                setEpisodeCollectionType(
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
        setPlaybackSpeed(value)
    }

    private fun canControlPlayback(): Boolean {
        if (!playbackAutomationSuppressed.value) return true
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
        playerSkipOpEdState.cancelSkipOpEd()
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
            videoEnhancement?.mode?.collect { mode -> playerOptions.update { it.copy(enhancementMode = mode) } }
        }
        backgroundScope.launch {
            subjectCollectionFlow.collect { collection -> playerOptions.update { it.copy(collectionType = collection.collectionType) } }
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
        val preview = createMediaProgressFramePreviewState(player, 384, 216) ?: return
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
                                launch {
                                    try { preview.prewarm(player.getCurrentPositionMillis()) }
                                    catch (e: CancellationException) { throw e }
                                    catch (_: Exception) { /* 预览不可用时仍可拖动进度条。 */ }
                                }
                                snapshotFlow { playbackInteraction.scrubMillis }.distinctUntilChanged().collectLatest { position ->
                                    if (position == null) preview.onPreviewFinished()
                                    else try { preview.requestFrame(position) }
                                    catch (e: CancellationException) { throw e }
                                    catch (_: Exception) { preview.onPreviewFinished() }
                                }
                            }
                        }
                    }
                }
        }
    }

    private fun observeAutoSkip() {
        backgroundScope.launch {
            progressChaptersFlow.collect { chapters ->
                playerOptions.update { it.copy(chapters = chapters) }
            }
        }
        backgroundScope.launch(Dispatchers.Main) {
            val enabled = combine(settingsRepository.videoScaffoldConfig.flow, playbackAutomationSuppressed, snapshotFlow { playbackInteraction.scrubMillis != null }, player.state) {
                    settings, suppressed, previewing, playback ->
                settings.autoSkipOpEd && !suppressed && !previewing && playback.isPlaying
            }
            combine(snapshotFlow { playerSkipOpEdState.pendingChapter }, player.currentPositionMillis, enabled) { chapter, position, enabled ->
                chapter?.takeIf { enabled }?.let {
                    TvSkipPrompt(it.name, ((it.offsetMillis - position + 999) / 1_000).toInt().coerceAtLeast(0))
                }
            }.collect { prompt -> playerOptions.update { it.copy(skipPrompt = prompt) } }
        }
    }

    init {
        observePlayerOptions()
        backgroundScope.launch(Dispatchers.Main) {
            player.state.collect { danmakuHostState.setPaused(!it.isPlaying) }
        }
        backgroundScope.launch(Dispatchers.Main) {
            uiDanmakuEventFlow.collect { event ->
                when (event) {
                    is UIDanmakuEvent.Add -> danmakuHostState.trySend(event.presentation)
                    is UIDanmakuEvent.Repopulate -> danmakuHostState.repopulate(
                        event.list,
                        event.currentPositionMillis,
                    )
                }
            }
        }
    }

    init {
        backgroundScope.launch {
            collectDanmakuConfig()
        }
        backgroundScope.launch {
            // 弹幕列表面板数据: 与渲染层共享同一 uiDanmakuEventFlow (shareIn), 不重复拉取
            uiDanmakuEventFlow.collect { event ->
                when (event) {
                    is UIDanmakuEvent.Repopulate -> danmakuList.value = event.list.asReversed()
                    is UIDanmakuEvent.Add ->
                        danmakuList.value =
                            (listOf(event.presentation) + danmakuList.value).take(DANMAKU_LIST_CAP)
                }
            }
        }
    }

    override fun onCleared() {
        setSpeedHold(false)
        super.onCleared()
    }


    companion object {
        /** 弹幕列表面板保留上限. */
        const val DANMAKU_LIST_CAP = 500
    }
}
