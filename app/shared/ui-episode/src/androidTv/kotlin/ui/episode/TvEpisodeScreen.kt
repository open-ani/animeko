/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.leanback.ui.episode

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.paging.LoadState
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import me.him188.ani.app.data.models.episode.EpisodeComment
import me.him188.ani.app.domain.player.VideoLoadingState
import me.him188.ani.app.ui.foundation.navigation.BackHandler
import me.him188.ani.app.ui.subject.episode.video.loading.EpisodeVideoLoadingIndicator
import me.him188.ani.app.ui.subject.episode.video.loading.shouldShowVideoLoadingIndicator
import me.him188.ani.app.videoplayer.ui.PlayerStatsOverlay
import me.him188.ani.danmaku.ui.DanmakuPresentation
import me.him188.ani.datasources.api.topic.FileSize
import me.him188.ani.leanback.ui.foundation.focus.TV_CONFIRM_KEYS
import me.him188.ani.leanback.ui.foundation.focus.rememberTvFocusScope
import me.him188.ani.leanback.ui.foundation.focus.requestPrepared
import me.him188.ani.leanback.ui.foundation.focus.tvFocusAnchor
import me.him188.ani.leanback.ui.foundation.focus.tvFocusExit
import me.him188.ani.leanback.ui.foundation.focus.tvFocusLink
import me.him188.ani.leanback.ui.foundation.focus.tvFocusNavSignal
import me.him188.ani.leanback.ui.foundation.widgets.TvOptionDefaults
import me.him188.ani.leanback.ui.foundation.widgets.TvOptionRow
import me.him188.ani.leanback.ui.watchtogether.TvTogetherIntent
import me.him188.ani.leanback.ui.watchtogether.TvTogetherState
import me.him188.ani.leanback.ui.watchtogether.TvWatchTogetherPanel
import org.openani.mediamp.features.AspectRatioMode
import org.openani.mediamp.isPlaying
import android.view.KeyEvent as AndroidKeyEvent

/*
 * TV 播放页 (atv-architecture.md §8).
 *
 * 覆盖层状态机 (§8.2, PR 语义 1:1):
 *   HIDDEN (纯视频) | CONTROLS (控制层)   正交子态: 选集条展开 · 拖拽预览 (scrub) ·
 *   按住倍速 · 数据源弹窗
 *
 * 播放控制按键收敛在根部 onPreviewKeyEvent 路由 (§8.2); 纯 UI 导航由对应组件处理:
 * - HIDDEN: 确认短按=播↔停 (暂停时唤出控制层), 长按 (系统连发判定, 同 tvLongPressKey
 *   判据) = 配置的倍速, 松开还原; ←→ 总是进入画面预览, 确认才 seek; ↑↓ 唤出控制层.
 * - CONTROLS: 焦点在进度条时 ←→/确认沿用 seek/播停语义, 其余按键交给焦点系统;
 *   选集按钮展开药丸上方的横向剧照条; 任意按键刷新 5s 自动隐藏 (暂停/拖拽/弹窗不隐藏).
 * - 拖拽预览: ←→ 移动预览点, 确认跳转, 返回取消.
 * - 全局: MediaPlayPause 播停 / MediaFastForward 下一集 / MediaRewind 上一集.
 * - 底部按钮按下进入推荐横排, 标题栏保持显示; 按上/返回恢复底部控制器并将焦点归还进度条.
 * - 返回逐层: 弹窗 → 推荐/拖拽 → 选集条 → 控制层 → 退出 (BackHandler 分层).
 */

@Composable
internal fun TvEpisodeScreen(
    uiState: TvEpisodeUiState,
    togetherState: TvTogetherState,
    onTogetherIntent: (TvTogetherIntent) -> Unit,
    commentsPager: Flow<PagingData<EpisodeComment>>,
    actionEvents: Flow<TvEpisodeEvent>,
    onIntent: (TvEpisodeIntent) -> Boolean,
    video: @Composable (Modifier) -> Unit,
    resolver: @Composable () -> Unit,
    danmaku: @Composable (Modifier) -> Unit,
    modifier: Modifier = Modifier,
    danmakuList: Flow<List<DanmakuPresentation>> = flowOf(uiState.panel.danmaku),
    presentationState: TvPlayerPresentationState = rememberTvPlayerPresentationState(uiState, onIntent),
) {
    val dispatch: (TvEpisodeIntent) -> Boolean = { onIntent(presentationState.tagRequest(it)) }
    val state by presentationState.states.collectAsState()
    val onAction = presentationState::onAction
    val loadingState = uiState.loadingState
    val title = uiState.title
    val bufferedFraction = uiState.bufferedFraction
    val playbackSpeed = uiState.playbackSpeed
    val aspectRatioMode = uiState.aspectRatioMode
    val stripEpisodes = uiState.episodes
    val currentEpisodeId = uiState.currentEpisodeId
    val selectedMedia = uiState.selectedMedia
    val positionMillis = uiState.positionMillis
    val latestState by rememberUpdatedState(uiState)
    val sourceDialogState = rememberTvSourceDialogState()
    var episodeActionId by presentationState::episodeActionId
    var collectionPrompt by presentationState::collectionPrompt
    var confirmLeave by presentationState::confirmLeave
    var commentDetail by presentationState::commentDetail
    var commentReturn by presentationState::commentReturn
    var danmakuAdjustment by presentationState::danmakuAdjustment
    var closingSidebarWithLeft by remember { mutableStateOf(false) }
    val sidebarTransition = updateTransition(state.sidebarVisible, label = "player-sidebar")
    var lastSidebar by remember { mutableStateOf<TvPlayerPanel?>(null) }
    val sidebar = state.activePanel?.takeIf { it.presentation == TvPlayerPanelPresentation.Sidebar } ?: lastSidebar
    val panelIdentity = state.activePanel ?: sidebar.takeIf { sidebarTransition.currentState }
    val panelListState = key(panelIdentity) { rememberLazyListState() }
    val danmakuSettingsState = remember(panelListState) { TvDanmakuSettingsPanelState(panelListState) }
    val recommendationListState = rememberLazyListState()
    val recommendationTransition = updateTransition(state.recommendationsVisible, label = "player-recommendations")
    SideEffect { if (state.sidebarVisible) lastSidebar = state.activePanel }
    val panelEntryKey = when (panelIdentity) {
        TvPlayerPanel.Collection -> CollectionPanelEntryKey(collectionPrompt)
        TvPlayerPanel.Together -> TogetherPanelEntryKey(togetherState.requiresLogin, togetherState.joined, confirmLeave)
        else -> PanelEntryKey(panelIdentity)
    }

    LaunchedEffect(actionEvents, presentationState) {
        actionEvents.collect(presentationState::onEvent)
    }
    LaunchedEffect(uiState.currentEpisodeId) { presentationState.episodeChanged(uiState.currentEpisodeId) }
    LaunchedEffect(togetherState.joined, togetherState.roomName) {
        presentationState.roomChanged(togetherState.joined, togetherState.roomName)
    }
    LaunchedEffect(state.interactionGeneration, state.canAutoHide, uiState.playbackState) {
        if (state.canAutoHide && uiState.playbackState.isPlaying) {
            delay(5_000)
            presentationState.autoHide()
        }
    }
    LaunchedEffect(presentationState.statsVisible) { dispatch(TvEpisodeIntent.ObserveStats(presentationState.statsVisible)) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(presentationState, lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) presentationState.releaseHeldSpeed()
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            presentationState.releaseHeldSpeed()
            dispatch(TvEpisodeIntent.ObserveStats(false))
        }
    }
    DisposableEffect(state.dialog, state.surfaceId) {
        onDispose {
            if (state.dialog == TvPlayerDialog.DanmakuMatch) {
                dispatch(TvEpisodeIntent.CancelDanmakuMatch(state.surfaceId))
            }
        }
    }
    val focus = rememberTvFocusScope()
    focus.Resolver()
    val stripListState = rememberLazyListState()
    TvPlayerFocusEffects(
        focus, presentationState, state, uiState, togetherState, onTogetherIntent,
        panelEntryKey, panelListState, danmakuSettingsState,
        recommendationListState, recommendationTransition, stripListState,
    )

    fun back() {
        when {
            !state.sidebarVisible && uiState.options.skipPrompt != null -> dispatch(TvEpisodeIntent.CancelAutoSkip)
            state.dialog == TvPlayerDialog.DanmakuMatch && uiState.danmakuMatch.selectedSubject != null -> dispatch(TvEpisodeIntent.BackDanmakuMatch)
            else -> {
                if (state.activePanel == TvPlayerPanel.Together && togetherState.joining) onTogetherIntent(TvTogetherIntent.CancelJoin)
                onAction(TvPlayerAction.Back)
            }
        }
    }
    BackHandler(enabled = state.handlesBack || uiState.options.skipPrompt != null, onBack = ::back)

    val sidebarAtTopLevel = state.sidebarVisible && state.dialog == null && commentDetail == null && danmakuAdjustment == null &&
            !(state.activePanel == TvPlayerPanel.Together && confirmLeave)

    // Ignore navigation into outgoing content until the recommendation transition settles.
    fun handleKey(event: KeyEvent): Boolean {
        if (event.key == Key.DirectionLeft && (closingSidebarWithLeft || sidebarAtTopLevel)) {
            if (event.type == KeyEventType.KeyDown && event.repeatCountCompat == 0 && !closingSidebarWithLeft) {
                closingSidebarWithLeft = true
                back()
            } else if (event.type == KeyEventType.KeyUp) {
                closingSidebarWithLeft = false
            }
            // Consume the whole press, including repeats after the panel starts closing.
            return true
        }
        val remoteKey = event.key.toTvRemoteKey()
        if (recommendationTransition.isRunning) {
            when (remoteKey) {
                TvRemoteKey.Left, TvRemoteKey.Right, TvRemoteKey.Down, TvRemoteKey.Confirm -> return true
                TvRemoteKey.Up -> if (!state.recommendationsVisible) return true
                else -> Unit
            }
        }
        return onAction(
            TvPlayerAction.RemoteKey(
                key = remoteKey,
                isDown = event.type == KeyEventType.KeyDown,
                repeatCount = event.repeatCountCompat,
                eventTimeMillis = event.eventTimeMillisCompat,
                seekBarFocused = focus.isFocused(TvPlayerFocus.SeekBar),
                iconRowFocused = focus.isFocused(TvPlayerFocus.IconRow),
                sourceDialogFocused = focus.isFocused(TvPlayerFocus.SourceDialog),
                sidebarFocused = focus.isFocused(TvPlayerFocus.Sidebar),
                recommendationsFocused = focus.isFocused(TvPlayerFocus.RecommendationsRow),
                dialogFocused = focus.isFocused(TvPlayerFocus.DialogHost) || (state.dialog == TvPlayerDialog.DanmakuList && focus.isFocused(TvPlayerFocus.Sidebar)),
            ),
        )
    }

    @Composable
    fun InteractivePanel(panel: TvPlayerPanel, panelModifier: Modifier = Modifier) {
        val entry = Modifier.tvFocusAnchor(focus, panelEntryKey)
        when (panel) {
            TvPlayerPanel.Collection -> TvPlayerCollectionPanel(
                collectionType = uiState.options.collectionType,
                busy = uiState.options.collectionBusy,
                collectionPrompt = collectionPrompt,
                onCollectionPromptChange = { collectionPrompt = it },
                onIntent = dispatch,
                listState = panelListState,
                entryModifier = entry,
                modifier = panelModifier,
            )
            TvPlayerPanel.DanmakuSettings -> TvPlayerDanmakuSettingsPanel(
                config = uiState.options.danmakuConfig,
                origins = uiState.options.danmakuOrigins,
                onIntent = dispatch,
                onOpenList = { onAction(TvPlayerAction.OpenDialog(TvPlayerDialog.DanmakuList)) },
                onMatch = { origin ->
                    presentationState.matchReturnSource = origin.serviceId
                    onAction(TvPlayerAction.OpenDialog(TvPlayerDialog.DanmakuMatch))
                    dispatch(TvEpisodeIntent.MatchDanmaku(origin.providerId, presentationState.states.value.surfaceId))
                },
                danmakuAdjustment = danmakuAdjustment,
                onDanmakuAdjustmentChange = { danmakuAdjustment = it },
                state = danmakuSettingsState,
                focus = focus,
                entryModifier = entry,
                modifier = panelModifier,
            )
            TvPlayerPanel.VideoSettings -> TvPlayerVideoSettingsPanel(
                enhancementMode = uiState.options.enhancementMode,
                statsVisible = presentationState.statsVisible,
                onSetEnhancement = { dispatch(TvEpisodeIntent.SetEnhancement(it)) },
                onToggleStats = { presentationState.statsVisible = !presentationState.statsVisible },
                listState = panelListState,
                entryModifier = entry,
                modifier = panelModifier,
            )
            TvPlayerPanel.Together -> TvWatchTogetherPanel(
                together = togetherState,
                onTogetherIntent = onTogetherIntent,
                onLogin = { dispatch(TvEpisodeIntent.OpenLogin) },
                confirmLeave = confirmLeave,
                onConfirmLeaveChange = { confirmLeave = it },
                listState = panelListState,
                entryModifier = entry,
                modifier = panelModifier,
            )
            TvPlayerPanel.Comments -> Unit
        }
    }

    TvPlayerPageLayout(
        sidebarTransition = sidebarTransition,
        modifier = modifier
            .tvFocusNavSignal(focus)
            .onPreviewKeyEvent(::handleKey)
            .tvFocusAnchor(focus, TvPlayerFocus.Root)
            .focusProperties { canFocus = !state.controlsVisible }
            .focusable(),
        video = { videoModifier ->
            Box(videoModifier.onSizeChanged { dispatch(TvEpisodeIntent.ViewportChanged(it.width, it.height)) }) {
                video(Modifier.fillMaxSize().focusProperties { canFocus = false })
                if (uiState.options.danmakuEnabled) danmaku(Modifier.fillMaxSize())
            }
        },
        status = {
            if (presentationState.statsVisible) PlayerStatsOverlay(
                uiState.options.stats,
                Modifier.align(Alignment.TopStart).padding(start = 48.dp, top = 100.dp),
                showHideHint = false,
            )

            // 取源/加载状态
            val loading = loadingState
            val sourceError = uiState.sources.error
            val noResults =
                !uiState.sources.loading && uiState.sources.groups.none { group -> group.items.any { it.excludedReason == null } } && selectedMedia == null
            if (shouldShowVideoLoadingIndicator(loading, uiState.isBuffering, uiState.playerError)) {
                EpisodeVideoLoadingIndicator(
                    state = loading,
                    speedProvider = { FileSize.Unspecified },
                    optimizeForFullscreen = true,
                    playerError = uiState.playerError,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                        .testTag("tv-player-loading"),
                    textStyle = MaterialTheme.typography.bodyLarge,
                )
            }
            if (loadingState is VideoLoadingState.Failed || uiState.playerError || sourceError != null || noResults) {
                Column(
                    Modifier
                        .align(Alignment.Center)
                        .padding(top = 105.dp)
                        .width(260.dp),
                ) {
                    TvOptionRow("重试播放") { dispatch(TvEpisodeIntent.RetryPlayback()) }
                    TvOptionRow("选择其他数据源") { onAction(TvPlayerAction.OpenSourceDialog) }
                }
            }
        },
        controller = {
            // 控制层
            AnimatedVisibility(
                visible = state.controlsVisible,
                modifier = Modifier.fillMaxSize(),
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                TvBottomControllerLayout(
                    transition = recommendationTransition,
                    recommendations = {
                        TvPlayerRecommendationsRow(
                            recommendations = uiState.panel.recommendations,
                            loading = uiState.panel.recommendationsLoading,
                            listState = recommendationListState,
                            entryModifier = Modifier.tvFocusAnchor(focus, TvPlayerFocus.RecommendationsEntry),
                            trapFocus = state.recommendationsVisible,
                            onClick = {
                                if (state.recommendationsVisible && !recommendationTransition.isRunning) {
                                    dispatch(TvEpisodeIntent.OpenRecommendation(it))
                                }
                            },
                            modifier = Modifier.tvFocusAnchor(focus, TvPlayerFocus.RecommendationsRow),
                        )
                    },
                    controller = {
                        var stripHadFocus by remember { mutableStateOf(false) }
                        var nextEpisodeHadFocus by remember { mutableStateOf(false) }
                        LaunchedEffect(uiState.hasNextEpisode) {
                            if (!uiState.hasNextEpisode && nextEpisodeHadFocus) {
                                nextEpisodeHadFocus = false
                                focus.request(TvPlayerFocus.EpisodesButton)
                            }
                        }
                        TvPlayerControlsOverlay(
                            sourceIconUrl = uiState.sources.groups.firstOrNull { it.sourceId == selectedMedia?.mediaSourceId }?.iconUrl,
                            sourceLabel = selectedMedia?.properties?.alliance?.ifBlank { "默认线路" } ?: "选源",
                            positionMillis = positionMillis,
                            durationMillis = uiState.durationMillis,
                            bufferedFraction = bufferedFraction,
                            hasNextEpisode = uiState.hasNextEpisode,
                            scrubMillis = state.scrubMillis,
                            speedLabel = formatSpeedLabel(playbackSpeed),
                            aspectLabel = when (aspectRatioMode) {
                                AspectRatioMode.FIT -> "适应"
                                AspectRatioMode.STRETCH -> "拉伸"
                                AspectRatioMode.CROP -> "裁剪"
                            },
                            activePanel = state.activePanel,
                            options = uiState.options,
                            seekBarModifier = Modifier
                                .tvFocusAnchor(focus, TvPlayerFocus.SeekBar)
                                // 显式方向链接: 上达胶囊行首钮, 下达图标行首钮 ——
                                // 空间搜索会落到不可见的视频/根节点 (§14.4-4 边缘元素显式声明去向)
                                .tvFocusLink(
                                    focus,
                                    up = PanelChipKey(TvPlayerPanel.Collection),
                                    down = TvPlayerFocus.IconRowEntry,
                                )
                                .focusable(),
                            iconRowModifier = Modifier
                                .tvFocusAnchor(focus, TvPlayerFocus.IconRow)
                                .focusGroup(),
                            nextEpisodeButtonModifier = Modifier
                                .onFocusChanged {
                                    if (latestState.hasNextEpisode) nextEpisodeHadFocus = it.isFocused
                                }
                                .tvFocusAnchor(focus, TvPlayerFocus.IconRowEntry)
                                .tvFocusLink(focus, up = TvPlayerFocus.SeekBar),
                            sourceButtonModifier = Modifier.tvFocusAnchor(focus, TvPlayerFocus.SourceButton),
                            speedButtonModifier = Modifier.tvFocusAnchor(focus, TvPlayerFocus.SpeedButton),
                            subtitleButtonModifier = Modifier.tvFocusAnchor(focus, TvPlayerFocus.SubtitleButton),
                            episodesButtonModifier = Modifier.tvFocusAnchor(focus, TvPlayerFocus.EpisodesButton),
                            capsuleAnchor = { panel -> Modifier.tvFocusAnchor(focus, PanelChipKey(panel)) },
                            onTogglePanel = { panel ->
                                onAction(TvPlayerAction.TogglePanel(panel))
                            },
                            panelHost = state.activePanel?.takeIf { it.presentation == TvPlayerPanelPresentation.Popup }
                                ?.let { panel ->
                                    @Composable {
                                        key(panel) {
                                            InteractivePanel(
                                                panel,
                                                Modifier.tvFocusAnchor(focus, TvPlayerFocus.PanelHost)
                                                    .tvFocusExit(focus, FocusDirection.Down to PanelChipKey(panel)),
                                            )
                                        }
                                    }
                                },
                            onNextEpisode = { dispatch(TvEpisodeIntent.NextEpisode) },
                            onOpenSourceDialog = { onAction(TvPlayerAction.OpenSourceDialog) },
                            onOpenSpeed = { onAction(TvPlayerAction.OpenDialog(TvPlayerDialog.Speed)) },
                            onCycleAspect = { dispatch(TvEpisodeIntent.CycleAspectRatio) },
                            onToggleDanmaku = { dispatch(TvEpisodeIntent.ToggleDanmaku) },
                            onSubtitles = { onAction(TvPlayerAction.OpenDialog(TvPlayerDialog.Subtitles)) },
                            onEpisodes = { onAction(TvPlayerAction.ToggleEpisodeStrip) },
                            modifier = Modifier.testTag("tv-player-controller"),
                            episodeStrip = {
                                // 选集条滑入/滑出 250ms (附录 A); 收起后离开组合, 卡片锚点随之脱离
                                AnimatedVisibility(
                                    visible = state.stripExpanded,
                                    enter = slideInVertically(tween(250)) { it / 2 } + fadeIn(tween(250)),
                                    exit = slideOutVertically(tween(250)) { it / 2 } + fadeOut(tween(250)),
                                ) {
                                    TvPlayerEpisodeStrip(
                                        episodes = stripEpisodes,
                                        currentEpisodeId = currentEpisodeId,
                                        listState = stripListState,
                                        stripModifier = Modifier.onFocusChanged {
                                            if (it.hasFocus) {
                                                stripHadFocus = true
                                            } else if (stripHadFocus) {
                                                // 焦点离开选集条即收起; 剧集操作弹窗由状态机保留选集条。
                                                stripHadFocus = false
                                                onAction(TvPlayerAction.StripFocusLost)
                                            }
                                        }.focusGroup(),
                                        cardModifier = { Modifier.tvFocusAnchor(focus, EpisodeCardKey(it.episodeId)) },
                                        onClickEpisode = { dispatch(TvEpisodeIntent.SelectEpisode(it.episodeId)) },
                                        onLongClickEpisode = {
                                            episodeActionId = it.episodeId
                                            onAction(TvPlayerAction.OpenDialog(TvPlayerDialog.EpisodeActions))
                                        },
                                    )
                                }
                            },
                        )
                    },
                )
            }
        },
        title = {
            // Keep the header in its own layer, independent of recommendation transitions.
            AnimatedVisibility(
                visible = state.controlsVisible,
                modifier = Modifier.align(Alignment.TopStart).fillMaxWidth(),
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                TvPlayerTitleBar(title)
            }
        },
        indicator = {
            // 按住倍速指示
            if (state.speedHolding) {
                PlayerCenterCapsule(
                    "${formatSpeedLabel(uiState.options.videoConfig.fastForwardSpeed)} 快进中 ▶▶",
                    Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 48.dp),
                )
            }
        },
        resolver = resolver,
        sidebar = {
            sidebar?.let { panel ->
                key(panel) {
                    val comments =
                        if (panel == TvPlayerPanel.Comments) commentsPager.collectAsLazyPagingItems() else null
                    LaunchedEffect(panel, comments?.loadState?.refresh, comments?.itemCount == 0) {
                        if (state.sidebarVisible && commentDetail == null && commentReturn == null && !focus.isFocused(TvPlayerFocus.Sidebar)) {
                            // Empty/loading panels have no focusable content. The request is
                            // delivered when an item or retry action becomes available.
                            focus.request(panelEntryKey)
                        }
                    }
                    LaunchedEffect(commentDetail, commentReturn) {
                        val target = commentReturn
                        if (commentDetail == null && target != null && comments != null) {
                            focus.requestPrepared {
                                // Cached/static PagingData can expose items while refresh is still Loading.
                                snapshotFlow { comments.itemCount > 0 || comments.loadState.refresh !is LoadState.Loading }
                                    .first { it }
                                if (comments.itemCount == 0) return@requestPrepared panelEntryKey
                                val currentIndex = (0 until comments.itemCount).firstOrNull { comments.peek(it)?.stableId == target.first }
                                    ?: target.second.coerceIn(0, comments.itemCount - 1)
                                panelListState.scrollToItem(currentIndex)
                                comments.peek(currentIndex)?.let { CommentKey(it.stableId) } ?: panelEntryKey
                            }
                            commentReturn = null
                        }
                    }
                    val showingDanmakuList = state.dialog == TvPlayerDialog.DanmakuList
                    TvPlayerSidePanel(
                        title = when {
                            commentDetail != null -> "评论全文"
                            showingDanmakuList -> "弹幕列表"
                            else -> panel.title
                        },
                        trapFocus = state.sidebarVisible && (state.dialog == null || showingDanmakuList),
                        modifier = Modifier.tvFocusAnchor(focus, TvPlayerFocus.Sidebar),
                        endPadding = if (panel == TvPlayerPanel.Together) 48.dp else 28.dp,
                    ) {
                        when {
                            commentDetail != null -> TvCommentDetail(
                                commentDetail!!,
                                Modifier.weight(1f).tvFocusAnchor(focus, TvPlayerFocus.PanelEntry),
                            )

                            showingDanmakuList -> TvDanmakuListDialog(
                                danmakuList.collectAsState(emptyList()).value,
                                focus,
                                TvPlayerFocus.DialogEntry,
                            )

                            panel == TvPlayerPanel.Comments -> TvPlayerComments(
                                comments = comments,
                                entryAnchorModifier = Modifier.tvFocusAnchor(focus, panelEntryKey),
                                onClickComment = { comment, index ->
                                    commentReturn = comment.stableId to index
                                    commentDetail = comment
                                },
                                commentAnchor = { Modifier.tvFocusAnchor(focus, CommentKey(it.stableId)) },
                                listState = panelListState,
                                modifier = Modifier.weight(1f),
                            )

                            else -> InteractivePanel(panel, Modifier.weight(1f))
                        }
                    }
                }
            }
        },
        overlays = {
            // 数据源选择弹窗 (最上层)
            if (state.sourceDialogVisible) {
                TvPlayerSourceDialog(
                    state = uiState.sources,
                    dialogState = sourceDialogState,
                    selected = selectedMedia,
                    containerModifier = Modifier.tvFocusAnchor(focus, TvPlayerFocus.SourceDialog),
                    onIntent = dispatch,
                )
            }
            state.dialog?.takeUnless { it == TvPlayerDialog.DanmakuList }?.let { dialog ->
                val entryModifier = Modifier.tvFocusAnchor(focus, TvPlayerFocus.DialogEntry)
                TvPlayerDialogSurface(
                    dialog = dialog,
                    when (dialog) {
                        TvPlayerDialog.Speed -> "播放速度"
                        TvPlayerDialog.Subtitles -> "字幕"
                        TvPlayerDialog.EpisodeActions -> "剧集操作"
                        TvPlayerDialog.DanmakuMatch -> "匹配弹幕"
                        TvPlayerDialog.DanmakuList -> "弹幕列表"
                    },
                    Modifier.tvFocusAnchor(focus, TvPlayerFocus.DialogHost),
                    subtitle = when (dialog) {
                        TvPlayerDialog.DanmakuMatch -> "搜索番剧，选择对应剧集的弹幕"
                        else -> null
                    },
                ) {
                    when (dialog) {
                        TvPlayerDialog.Speed -> TvSpeedDialog(uiState, dispatch, entryModifier)
                        TvPlayerDialog.Subtitles -> TvSubtitleDialog(uiState.options, dispatch, entryModifier)

                        TvPlayerDialog.EpisodeActions -> {
                            val episode = stripEpisodes.find { it.episodeId == episodeActionId }
                            if (episode != null) {
                                TvOptionRow("播放 ${episode.sortLabel}", modifier = entryModifier) {
                                    dispatch(
                                        TvEpisodeIntent.SelectEpisode(episode.episodeId),
                                    )
                                }
                                TvOptionRow(if (episode.watched) "标记为未看" else "标记为已看") {
                                    dispatch(
                                        TvEpisodeIntent.SetEpisodeWatched(
                                            episode.episodeId,
                                            !episode.watched,
                                            requestId = state.surfaceId,
                                        ),
                                    )
                                }
                            }
                        }

                        TvPlayerDialog.DanmakuMatch -> TvDanmakuMatchPanel(uiState.danmakuMatch, dispatch, entryModifier)
                        TvPlayerDialog.DanmakuList -> Unit
                    }
                }
            }
            uiState.options.skipPrompt?.let { prompt ->
                PlayerCenterCapsule(
                    "${prompt.secondsRemaining} 秒后跳过 ${prompt.name}",
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 100.dp, end = 48.dp)
                        .testTag("tv-auto-skip-popup"),
                )
            }
            uiState.options.message?.let { message ->
                PlayerCenterCapsule(
                    message,
                    Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 100.dp),
                )
            }
        },
    )
}

@Composable
private fun PlayerCenterCapsule(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier
            .background(TvOptionDefaults.Container, RoundedCornerShape(28.dp))
            .padding(horizontal = 20.dp, vertical = 12.dp),
        style = MaterialTheme.typography.titleSmall,
        color = Color.White,
    )
}

/** 倍速展示: 1.0 -> "1x", 1.25 -> "1.25x". */
internal fun formatSpeedLabel(speed: Float): String {
    val text = if (speed == speed.toLong().toFloat()) {
        speed.toLong().toString()
    } else {
        speed.toString()
    }
    return "${text}x"
}

/** Map Android key codes to the platform-independent player intent vocabulary. */
private fun Key.toTvRemoteKey(): TvRemoteKey = when (this) {
    Key.DirectionLeft -> TvRemoteKey.Left
    Key.DirectionRight -> TvRemoteKey.Right
    Key.DirectionUp -> TvRemoteKey.Up
    Key.DirectionDown -> TvRemoteKey.Down
    in TV_CONFIRM_KEYS -> TvRemoteKey.Confirm
    Key.MediaPlayPause -> TvRemoteKey.PlayPause
    Key.MediaPlay -> TvRemoteKey.Play
    Key.MediaPause -> TvRemoteKey.Pause
    Key.Menu -> TvRemoteKey.Menu
    Key.MediaFastForward, Key.MediaSkipForward, Key.MediaNext -> TvRemoteKey.Next
    Key.MediaRewind, Key.MediaSkipBackward, Key.MediaPrevious -> TvRemoteKey.Previous
    else -> TvRemoteKey.Other
}

private val KeyEvent.repeatCountCompat: Int
    get() = (nativeKeyEvent as? AndroidKeyEvent)?.repeatCount ?: 0

private val KeyEvent.eventTimeMillisCompat: Long
    get() = (nativeKeyEvent as? AndroidKeyEvent)?.eventTime ?: 0L
