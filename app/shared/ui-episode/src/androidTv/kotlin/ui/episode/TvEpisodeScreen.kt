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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import me.him188.ani.app.data.models.episode.EpisodeComment
import me.him188.ani.app.domain.player.VideoLoadingState
import me.him188.ani.app.ui.foundation.navigation.BackHandler
import me.him188.ani.datasources.api.topic.UnifiedCollectionType
import me.him188.ani.leanback.ui.foundation.focus.TV_CONFIRM_KEYS
import me.him188.ani.leanback.ui.foundation.focus.TvFocusKey
import me.him188.ani.leanback.ui.foundation.focus.rememberTvFocusScope
import me.him188.ani.leanback.ui.foundation.focus.tvFocusAnchor
import me.him188.ani.leanback.ui.foundation.focus.tvFocusExit
import me.him188.ani.leanback.ui.foundation.focus.tvFocusLink
import me.him188.ani.leanback.ui.foundation.focus.tvFocusNavSignal
import org.openani.mediamp.features.AspectRatioMode
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
 * - 返回逐层: 弹窗 → 拖拽 → 选集条 → 控制层 → 退出 (BackHandler 分层).
 */

/** 播放页焦点锚点. Root 仅 HIDDEN 态可聚焦 (无焦点持有者按键派发会整体失效). */
private enum class TvPlayerFocus : TvFocusKey {
    Root, SeekBar, IconRow, IconRowEntry, SourceDialog, SourceDialogEntry,
    PanelHost, PanelEntry,
    SourceButton, SpeedButton, SubtitleButton, EpisodesButton, DialogHost, DialogEntry,
    DanmakuListButton, DanmakuMatchButton,
}

/** 胶囊按钮锚点 (面板关闭/向下退出时焦点回对应胶囊). */
private data class PanelChipKey(val panel: TvPlayerPanel) : TvFocusKey

/** Confirmation content has its own anchor so a request cannot reach the previous lazy item. */
private data class CollectionPanelEntryKey(val prompt: TvCollectionPrompt?) : TvFocusKey

private data class TogetherPanelEntryKey(val joined: Boolean, val confirmLeave: Boolean) : TvFocusKey

private data class EpisodeCardKey(val episodeId: Int) : TvFocusKey

@Composable
fun TvEpisodeScreen(
    uiState: TvEpisodeUiState,
    togetherState: TvTogetherState,
    onTogetherIntent: (TvTogetherIntent) -> Unit,
    commentsPager: Flow<PagingData<EpisodeComment>>,
    focusRequests: Flow<TvPlayerFocusRequest>,
    actionEvents: Flow<TvEpisodeEvent>,
    onIntent: (TvEpisodeIntent) -> Boolean,
    video: @Composable (Modifier) -> Unit,
    resolver: @Composable () -> Unit,
    danmaku: @Composable (Modifier) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state = uiState.overlay
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
    var episodeActionId by rememberSaveable { mutableStateOf<Int?>(null) }
    val latestEpisodeActionId by rememberUpdatedState(episodeActionId)
    var collectionPrompt by rememberSaveable { mutableStateOf<TvCollectionPrompt?>(null) }
    var confirmLeave by rememberSaveable(togetherState.joined, togetherState.roomName) { mutableStateOf(false) }
    val panelEntryKey = when (state.activePanel) {
        TvPlayerPanel.Collection -> CollectionPanelEntryKey(collectionPrompt)
        TvPlayerPanel.Together -> TogetherPanelEntryKey(togetherState.joined, confirmLeave)
        else -> TvPlayerFocus.PanelEntry
    }

    LaunchedEffect(actionEvents) {
        actionEvents.collect { event ->
            collectionPrompt = when (event) {
                is TvEpisodeEvent.CollectionChanged ->
                    TvCollectionPrompt.MarkAllWatched.takeIf { event.type == UnifiedCollectionType.DONE }

                TvEpisodeEvent.AllEpisodesWatched -> null
            }
        }
    }

    val focus = rememberTvFocusScope()
    focus.Resolver()
    focus.InitialFocus(if (state.controlsVisible) TvPlayerFocus.SeekBar else TvPlayerFocus.Root)
    val stripListState = rememberLazyListState()
    LaunchedEffect(focusRequests, focus) {
        focusRequests.collectLatest { request ->
            // Wait for the overlay removal to reach composition before leaving its focus trap.
            snapshotFlow { latestState.overlay }.first { overlay ->
                when (request) {
                    TvPlayerFocusRequest.Root -> !overlay.controlsVisible
                    else -> overlay.controlsVisible && !overlay.sourceDialogVisible && overlay.dialog == null
                }
            }
            when (request) {
                TvPlayerFocusRequest.Root -> focus.request(TvPlayerFocus.Root)
                TvPlayerFocusRequest.SeekBar -> focus.request(TvPlayerFocus.SeekBar)
                TvPlayerFocusRequest.SourceButton -> focus.request(TvPlayerFocus.SourceButton)
                TvPlayerFocusRequest.EpisodesButton -> focus.request(TvPlayerFocus.EpisodesButton)
                is TvPlayerFocusRequest.DialogButton -> focus.request(
                    when (request.dialog) {
                        TvPlayerDialog.Speed -> TvPlayerFocus.SpeedButton
                        TvPlayerDialog.Subtitles -> TvPlayerFocus.SubtitleButton
                        TvPlayerDialog.DanmakuMatch -> TvPlayerFocus.DanmakuMatchButton
                        TvPlayerDialog.DanmakuList -> TvPlayerFocus.DanmakuListButton
                        TvPlayerDialog.EpisodeActions -> EpisodeCardKey(
                            latestEpisodeActionId ?: latestState.currentEpisodeId,
                        )
                    },
                )

                is TvPlayerFocusRequest.PanelChip -> focus.request(PanelChipKey(request.panel))
            }
        }
    }

    // Translate platform events into playback input; local UI navigation stays in its components.
    fun handleKey(event: KeyEvent): Boolean = onIntent(
        TvEpisodeIntent.RemoteKey(
            key = event.key.toTvRemoteKey(),
            isDown = event.type == KeyEventType.KeyDown,
            repeatCount = event.repeatCountCompat,
            eventTimeMillis = event.eventTimeMillisCompat,
            seekBarFocused = focus.isFocused(TvPlayerFocus.SeekBar),
            iconRowFocused = focus.isFocused(TvPlayerFocus.IconRow),
            sourceDialogFocused = focus.isFocused(TvPlayerFocus.SourceDialog),
        ),
    )

    // 面板打开: 焦点送入口条目 (request 悬挂语义天然等数据 —— 空面板锚点不附着,
    // 焦点留在胶囊, 用户按键自动放弃在途请求)
    LaunchedEffect(state.activePanel) {
        if (state.activePanel != null) focus.request(panelEntryKey)
        if (state.activePanel == TvPlayerPanel.Together) onTogetherIntent(TvTogetherIntent.Open)
    }
    LaunchedEffect(state.dialog, uiState.panel.danmaku.isEmpty()) {
        if (state.dialog != null) focus.request(TvPlayerFocus.DialogEntry)
    }
    LaunchedEffect(uiState.danmakuMatch.selectedSubject?.id) {
        if (state.dialog == TvPlayerDialog.DanmakuMatch) focus.request(TvPlayerFocus.DialogEntry)
    }
    LaunchedEffect(
        collectionPrompt,
        uiState.options.collectionBusy,
    ) {
        if (state.activePanel == TvPlayerPanel.Collection && !uiState.options.collectionBusy) focus.request(
            panelEntryKey,
        )
    }
    LaunchedEffect(togetherState.joined, confirmLeave) {
        if (state.activePanel == TvPlayerPanel.Together) focus.request(panelEntryKey)
    }

    // 选集条展开: 等列表数据就绪 → 滚到当前集 → 送焦当前集卡 (全事件驱动)
    LaunchedEffect(state.stripExpanded) {
        if (!state.stripExpanded) return@LaunchedEffect
        val episodes = snapshotFlow { latestState.episodes }.first { it.isNotEmpty() }
        val index = episodes.indexOfFirst { it.episodeId == latestState.currentEpisodeId }
        if (index >= 0) stripListState.scrollToItem(index)
        focus.request(EpisodeCardKey(episodes.getOrNull(index)?.episodeId ?: episodes.first().episodeId))
    }

    // 模式切换始终可操作, 不等待源结果返回。
    LaunchedEffect(state.sourceDialogVisible) {
        if (!state.sourceDialogVisible) return@LaunchedEffect
        focus.request(TvPlayerFocus.SourceDialogEntry)
    }

    BackHandler(enabled = state.handlesBack || uiState.options.skipPrompt != null) {
        when {
            uiState.options.skipPrompt != null -> onIntent(TvEpisodeIntent.Back)
            state.activePanel == TvPlayerPanel.Together && togetherState.joining -> onTogetherIntent(TvTogetherIntent.CancelJoin)
            state.activePanel == TvPlayerPanel.Together && confirmLeave -> confirmLeave = false
            state.activePanel == TvPlayerPanel.Collection && collectionPrompt != null -> collectionPrompt = null

            else -> onIntent(TvEpisodeIntent.Back)
        }
    }

    Box(
        modifier
            .fillMaxSize()
            .background(Color.Black)
            .onSizeChanged { onIntent(TvEpisodeIntent.ViewportChanged(it.width, it.height)) }
            .tvFocusNavSignal(focus)
            .onPreviewKeyEvent(::handleKey)
            .tvFocusAnchor(focus, TvPlayerFocus.Root)
            // HIDDEN 层 Root 兜底持焦; CONTROLS 层禁止空间搜索落回 Root
            .focusProperties { canFocus = !state.controlsVisible }
            .focusable(),
    ) {
        // canFocus=false: 嵌入的播放器 View 不参与焦点 (空间搜索落进去会"看不见的焦点"死角)
        video(
            Modifier
                .fillMaxSize()
                .focusProperties { canFocus = false },
        )
        resolver()
        if (uiState.options.danmakuEnabled) danmaku(Modifier.fillMaxSize())

        // 取源/加载状态
        val loading = loadingState
        val sourceError = uiState.sources.error
        val noResults =
            !uiState.sources.loading && uiState.sources.groups.none { group -> group.items.any { it.excludedReason == null } } && selectedMedia == null
        if (loading !is VideoLoadingState.Succeed) {
            Text(
                text = sourceError ?: if (noResults) "没有找到可用资源" else when (loading) {
                    is VideoLoadingState.Failed -> "播放失败"
                    VideoLoadingState.Initial, VideoLoadingState.ResolvingSource -> "正在取源…"
                    else -> "加载中…"
                },
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
            )
        }
        if (loadingState is VideoLoadingState.Failed || sourceError != null || noResults) {
            Column(
                Modifier
                    .align(Alignment.Center)
                    .padding(top = 105.dp)
                    .width(260.dp),
            ) {
                TvOptionRow("重试播放") { onIntent(TvEpisodeIntent.RetryPlayback) }
                TvOptionRow("选择其他数据源") { onIntent(TvEpisodeIntent.OpenSourceDialog) }
            }
        }

        // 控制层
        AnimatedVisibility(
            visible = state.controlsVisible,
            modifier = Modifier.fillMaxSize(),
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            var stripHadFocus by remember { mutableStateOf(false) }
            TvPlayerControlsOverlay(
                title = title,
                sourceIconUrl = uiState.sources.groups.firstOrNull { it.sourceId == selectedMedia?.mediaSourceId }?.iconUrl,
                positionMillis = positionMillis,
                durationMillis = uiState.durationMillis,
                bufferedFraction = bufferedFraction,
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
                    .tvFocusAnchor(focus, TvPlayerFocus.IconRowEntry)
                    .tvFocusLink(focus, up = TvPlayerFocus.SeekBar),
                sourceButtonModifier = Modifier.tvFocusAnchor(focus, TvPlayerFocus.SourceButton),
                speedButtonModifier = Modifier.tvFocusAnchor(focus, TvPlayerFocus.SpeedButton),
                subtitleButtonModifier = Modifier.tvFocusAnchor(focus, TvPlayerFocus.SubtitleButton),
                episodesButtonModifier = Modifier.tvFocusAnchor(focus, TvPlayerFocus.EpisodesButton),
                capsuleAnchor = { panel -> Modifier.tvFocusAnchor(focus, PanelChipKey(panel)) },
                onTogglePanel = { panel ->
                    onIntent(TvEpisodeIntent.TogglePanel(panel))
                },
                panelHost = state.activePanel?.let { panel ->
                    @Composable {
                        val relatedSubjects = uiState.panel.relatedSubjects
                        val comments = if (panel == TvPlayerPanel.Comments) {
                            commentsPager.collectAsLazyPagingItems()
                        } else null
                        val panelModifier = Modifier
                            .tvFocusAnchor(focus, TvPlayerFocus.PanelHost)
                            .tvFocusExit(focus, FocusDirection.Down to PanelChipKey(panel))
                        val entryModifier = Modifier.tvFocusAnchor(focus, panelEntryKey)
                        if (panel in setOf(
                                TvPlayerPanel.Collection,
                                TvPlayerPanel.DanmakuSettings,
                                TvPlayerPanel.VideoSettings,
                                TvPlayerPanel.Together,
                            )
                        ) {
                            TvInteractivePanel(
                                panel,
                                uiState,
                                togetherState,
                                onIntent,
                                onTogetherIntent,
                                entryModifier,
                                collectionPrompt = collectionPrompt,
                                onCollectionPromptChange = { collectionPrompt = it },
                                confirmLeave = confirmLeave,
                                onConfirmLeaveChange = { confirmLeave = it },
                                danmakuListModifier = Modifier.tvFocusAnchor(focus, TvPlayerFocus.DanmakuListButton),
                                danmakuMatchModifier = Modifier.tvFocusAnchor(focus, TvPlayerFocus.DanmakuMatchButton),
                                modifier = panelModifier,
                            )
                        } else TvPlayerPanelHost(
                            panel = panel,
                            relatedSubjects = relatedSubjects,
                            comments = comments,
                            panelModifier = Modifier
                                .tvFocusAnchor(focus, TvPlayerFocus.PanelHost)
                                // 向下离开面板回对应胶囊 (面板保持打开); 其余方向停留
                                .tvFocusExit(focus, FocusDirection.Down to PanelChipKey(panel)),
                            entryAnchorModifier = Modifier
                                .tvFocusAnchor(focus, TvPlayerFocus.PanelEntry),
                            onClickSubject = { onIntent(TvEpisodeIntent.OpenSubject(it.subjectId)) },
                        )
                    }
                },
                onNextEpisode = { onIntent(TvEpisodeIntent.NextEpisode) },
                onOpenSourceDialog = { onIntent(TvEpisodeIntent.OpenSourceDialog) },
                onOpenSpeed = { onIntent(TvEpisodeIntent.OpenDialog(TvPlayerDialog.Speed)) },
                onCycleAspect = { onIntent(TvEpisodeIntent.CycleAspectRatio) },
                onToggleDanmaku = { onIntent(TvEpisodeIntent.ToggleDanmaku) },
                onSubtitles = { onIntent(TvEpisodeIntent.OpenDialog(TvPlayerDialog.Subtitles)) },
                onEpisodes = { onIntent(TvEpisodeIntent.ToggleEpisodeStrip) },
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
                                    onIntent(TvEpisodeIntent.StripFocusLost)
                                }
                            }.focusGroup(),
                            cardModifier = { Modifier.tvFocusAnchor(focus, EpisodeCardKey(it.episodeId)) },
                            onClickEpisode = { onIntent(TvEpisodeIntent.SelectEpisode(it.episodeId)) },
                            onLongClickEpisode = {
                                episodeActionId = it.episodeId
                                onIntent(TvEpisodeIntent.OpenDialog(TvPlayerDialog.EpisodeActions))
                            },
                        )
                    }
                },
            )
        }

        // 按住倍速指示
        if (state.speedHolding) {
            PlayerCenterCapsule(
                "${formatSpeedLabel(uiState.options.videoConfig.fastForwardSpeed)} 快进中 ▶▶",
                Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 48.dp),
            )
        }

        // 数据源选择弹窗 (最上层)
        if (state.sourceDialogVisible) {
            TvPlayerSourceDialog(
                state = uiState.sources,
                dialogState = sourceDialogState,
                selected = selectedMedia,
                containerModifier = Modifier.tvFocusAnchor(focus, TvPlayerFocus.SourceDialog),
                entryAnchorModifier = Modifier
                    .tvFocusAnchor(focus, TvPlayerFocus.SourceDialogEntry),
                onIntent = onIntent,
            )
        }
        state.dialog?.let { dialog ->
            val entryModifier = Modifier.tvFocusAnchor(focus, TvPlayerFocus.DialogEntry)
            TvOptionModal(
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
                    TvPlayerDialog.Speed -> TvSpeedDialog(uiState, onIntent, entryModifier)
                    TvPlayerDialog.Subtitles -> LazyColumn {
                        item {
                            TvOptionRow(
                                "关闭字幕",
                                modifier = entryModifier,
                                selected = uiState.options.selectedSubtitleId == null,
                            ) { onIntent(TvEpisodeIntent.SelectSubtitle(null)) }
                        }
                        items(uiState.options.subtitles, key = { it.id }) { subtitle ->
                            TvOptionRow(
                                subtitle.label,
                                selected = subtitle.id == uiState.options.selectedSubtitleId,
                            ) { onIntent(TvEpisodeIntent.SelectSubtitle(subtitle.id)) }
                        }
                        if (uiState.options.subtitles.isEmpty()) item {
                            Text(
                                "此资源没有可切换的字幕",
                                color = TvPlayerSurfaceDefaults.Muted,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            )
                        }
                    }

                    TvPlayerDialog.EpisodeActions -> {
                        val episode = stripEpisodes.find { it.episodeId == episodeActionId }
                        if (episode != null) {
                            TvOptionRow("播放 ${episode.sortLabel}", modifier = entryModifier) {
                                onIntent(
                                    TvEpisodeIntent.SelectEpisode(episode.episodeId),
                                )
                            }
                            TvOptionRow(if (episode.watched) "标记为未看" else "标记为已看") {
                                onIntent(
                                    TvEpisodeIntent.SetEpisodeWatched(
                                        episode.episodeId,
                                        !episode.watched,
                                    ),
                                )
                            }
                        }
                    }

                    TvPlayerDialog.DanmakuMatch -> TvDanmakuMatchPanel(uiState.danmakuMatch, onIntent, entryModifier)
                    TvPlayerDialog.DanmakuList -> TvDanmakuListDialog(uiState.panel.danmaku, entryModifier)
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
    }
}

@Composable
private fun PlayerCenterCapsule(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier
            .background(TvPlayerSurfaceDefaults.Container, RoundedCornerShape(28.dp))
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
