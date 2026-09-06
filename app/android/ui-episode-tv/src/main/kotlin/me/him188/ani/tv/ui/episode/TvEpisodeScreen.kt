/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.tv.ui.episode

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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.unit.dp
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import me.him188.ani.app.data.models.episode.EpisodeComment
import me.him188.ani.app.domain.player.VideoLoadingState
import me.him188.ani.app.ui.foundation.navigation.BackHandler
import me.him188.ani.tv.ui.foundation.focus.TV_CONFIRM_KEYS
import me.him188.ani.tv.ui.foundation.focus.TvFocusKey
import me.him188.ani.tv.ui.foundation.focus.rememberTvFocusScope
import me.him188.ani.tv.ui.foundation.focus.tvFocusAnchor
import me.him188.ani.tv.ui.foundation.focus.tvFocusExit
import me.him188.ani.tv.ui.foundation.focus.tvFocusLink
import me.him188.ani.tv.ui.foundation.focus.tvFocusNavSignal
import org.openani.mediamp.PlaybackState
import org.openani.mediamp.features.AspectRatioMode
import android.view.KeyEvent as AndroidKeyEvent

/*
 * TV 播放页 (atv-architecture.md §8).
 *
 * 覆盖层状态机 (§8.2, PR 语义 1:1):
 *   HIDDEN (纯视频) | CONTROLS (控制层)   正交子态: 选集条展开 · 拖拽预览 (scrub) ·
 *   按住倍速 · 数据源弹窗
 *
 * 按键全部收敛在根部唯一 onPreviewKeyEvent 路由 (§8.2 保留的 PR 交互架构):
 * - HIDDEN: 确认短按=播↔停 (暂停时唤出控制层), 长按 (系统连发判定, 同 tvLongPressKey
 *   判据) = 2.5x 倍速松开还原; ←→ 单按 ±5s 静默 seek + 中央闪烁, ~620ms 内连按 (含按住
 *   连发) 升级拖拽预览; ↑↓ 唤出控制层.
 * - CONTROLS: 焦点在进度条时 ←→/确认沿用 seek/播停语义, 其余按键交给焦点系统;
 *   图标行按下进入选集条; 任意按键刷新 5s 自动隐藏 (暂停/拖拽/弹窗不隐藏).
 * - 拖拽预览: ←→ 移动预览点, 确认跳转, 返回取消.
 * - 全局: MediaPlayPause 播停 / MediaFastForward 下一集 / MediaRewind 上一集.
 * - 返回逐层: 弹窗 → 拖拽 → 选集条 → 控制层 → 退出 (BackHandler 分层).
 */

/** 播放页焦点锚点. Root 仅 HIDDEN 态可聚焦 (无焦点持有者按键派发会整体失效). */
private enum class TvPlayerFocus : TvFocusKey {
    Root, SeekBar, IconRow, IconRowEntry, StripCurrent, SourceDialog, SourceDialogEntry,
    PanelHost, PanelEntry,
}

/** 胶囊按钮锚点 (面板关闭/向下退出时焦点回对应胶囊). */
private data class PanelChipKey(val panel: TvPlayerPanel) : TvFocusKey

@Composable
fun TvEpisodeScreen(
    uiState: TvEpisodeUiState,
    commentsPager: Flow<PagingData<EpisodeComment>>,
    focusRequests: Flow<TvPlayerFocusRequest>,
    onIntent: (TvEpisodeIntent) -> Boolean,
    video: @Composable (Modifier) -> Unit,
    resolver: @Composable () -> Unit,
    danmaku: @Composable (Modifier) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state = uiState.overlay
    val playbackState = uiState.playbackState
    val loadingState = uiState.loadingState
    val title = uiState.title
    val mediaLabel = uiState.mediaLabel
    val bufferedFraction = uiState.bufferedFraction
    val playbackSpeed = uiState.playbackSpeed
    val aspectRatioMode = uiState.aspectRatioMode
    val stripEpisodes = uiState.episodes
    val currentEpisodeId = uiState.currentEpisodeId
    val mediaCandidates = uiState.mediaCandidates
    val selectedMedia = uiState.selectedMedia
    val positionMillis = uiState.positionMillis
    val clockText = uiState.clockText
    val latestState by rememberUpdatedState(uiState)

    val focus = rememberTvFocusScope()
    focus.Resolver()
    focus.InitialFocus(if (state.controlsVisible) TvPlayerFocus.SeekBar else TvPlayerFocus.Root)
    val stripListState = rememberLazyListState()
    val dialogListState = rememberLazyListState()
    LaunchedEffect(focusRequests, focus) {
        focusRequests.collect { request ->
            when (request) {
                TvPlayerFocusRequest.Root -> focus.request(TvPlayerFocus.Root)
                TvPlayerFocusRequest.SeekBar -> focus.request(TvPlayerFocus.SeekBar)
                is TvPlayerFocusRequest.PanelChip -> focus.request(PanelChipKey(request.panel))
            }
        }
    }

    // Translate platform events into facts. All playback/overlay decisions belong to the ViewModel.
    fun handleKey(event: KeyEvent): Boolean = onIntent(TvEpisodeIntent.RemoteKey(
        key = event.key.toTvRemoteKey(),
        isDown = event.type == KeyEventType.KeyDown,
        repeatCount = event.repeatCountCompat,
        eventTimeMillis = event.eventTimeMillisCompat,
        seekBarFocused = focus.isFocused(TvPlayerFocus.SeekBar),
        iconRowFocused = focus.isFocused(TvPlayerFocus.IconRow),
        sourceDialogFocused = focus.isFocused(TvPlayerFocus.SourceDialog),
    ))

    // 面板打开: 焦点送入口条目 (request 悬挂语义天然等数据 —— 空面板锚点不附着,
    // 焦点留在胶囊, 用户按键自动放弃在途请求)
    LaunchedEffect(state.activePanel) {
        if (state.activePanel != null) focus.request(TvPlayerFocus.PanelEntry)
    }

    // 选集条展开: 等列表数据就绪 → 滚到当前集 → 送焦当前集卡 (全事件驱动)
    LaunchedEffect(state.stripExpanded) {
        if (!state.stripExpanded) return@LaunchedEffect
        val episodes = snapshotFlow { latestState.episodes }.first { it.isNotEmpty() }
        val index = episodes.indexOfFirst { it.episodeId == latestState.currentEpisodeId }
        if (index >= 0) stripListState.scrollToItem(index)
        focus.request(TvPlayerFocus.StripCurrent)
    }

    // 数据源弹窗打开: 等候选就绪 → 滚到当前选中 → 送焦
    LaunchedEffect(state.sourceDialogVisible) {
        if (!state.sourceDialogVisible) return@LaunchedEffect
        val list = snapshotFlow { latestState.mediaCandidates }.first { it.isNotEmpty() }
        val index = list.indexOf(latestState.selectedMedia).takeIf { it >= 0 } ?: 0
        dialogListState.scrollToItem(index)
        focus.request(TvPlayerFocus.SourceDialogEntry)
    }

    BackHandler(enabled = state.handlesBack) { onIntent(TvEpisodeIntent.Back) }

    Box(
        modifier
            .fillMaxSize()
            .background(Color.Black)
            .tvFocusNavSignal(focus)
            .onPreviewKeyEvent(::handleKey)
            .tvFocusAnchor(focus, TvPlayerFocus.Root)
            // HIDDEN 层 Root 兜底持焦; CONTROLS 层禁止空间搜索落回 Root
            .focusProperties { canFocus = !state.controlsVisible }
            .focusable(),
    ) {
        // canFocus=false: 嵌入的播放器 View 不参与焦点 (空间搜索落进去会"看不见的焦点"死角)
        video(Modifier.fillMaxSize().focusProperties { canFocus = false })
        resolver()
        danmaku(Modifier.fillMaxSize())

        // 取源/加载状态
        val loading = loadingState
        if (loading !is VideoLoadingState.Succeed) {
            Text(
                text = when (loading) {
                    is VideoLoadingState.Failed -> "加载失败: $loading"
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
                clockText = clockText,
                mediaLabel = mediaLabel,
                positionMillis = positionMillis,
                durationMillis = uiState.durationMillis,
                bufferedFraction = bufferedFraction,
                scrubMillis = state.scrubMillis,
                playStateLabel = when (playbackState) {
                    PlaybackState.PLAYING -> "播放中"
                    PlaybackState.PAUSED -> "已暂停"
                    PlaybackState.PAUSED_BUFFERING, PlaybackState.READY -> "缓冲中"
                    PlaybackState.FINISHED -> "已结束"
                    PlaybackState.ERROR -> "出错"
                    else -> "加载中"
                },
                speedLabel = formatSpeedLabel(playbackSpeed),
                aspectLabel = when (aspectRatioMode) {
                    AspectRatioMode.FIT -> "适应"
                    AspectRatioMode.STRETCH -> "拉伸"
                    AspectRatioMode.CROP -> "裁剪"
                },
                activePanel = state.activePanel,
                seekBarModifier = Modifier
                    .tvFocusAnchor(focus, TvPlayerFocus.SeekBar)
                    // 显式方向链接: 上达胶囊行首钮, 下达图标行首钮 ——
                    // 空间搜索会落到不可见的视频/根节点 (§14.4-4 边缘元素显式声明去向)
                    .tvFocusLink(
                        focus,
                        up = PanelChipKey(TvPlayerPanel.Recommendations),
                        down = TvPlayerFocus.IconRowEntry,
                    )
                    .focusable(),
                iconRowModifier = Modifier
                    .tvFocusAnchor(focus, TvPlayerFocus.IconRow)
                    .focusGroup(),
                seekBackButtonModifier = Modifier
                    .tvFocusAnchor(focus, TvPlayerFocus.IconRowEntry)
                    .tvFocusLink(focus, up = TvPlayerFocus.SeekBar),
                capsuleAnchor = { panel -> Modifier.tvFocusAnchor(focus, PanelChipKey(panel)) },
                onTogglePanel = { panel ->
                    onIntent(TvEpisodeIntent.TogglePanel(panel))
                },
                panelHost = state.activePanel?.let { panel ->
                    @Composable {
                        val relatedSubjects = uiState.panel.relatedSubjects
                        val staff = uiState.panel.staff
                        val characters = uiState.panel.characters
                        val danmakuList = uiState.panel.danmaku
                        val comments = if (panel == TvPlayerPanel.Comments) {
                            commentsPager.collectAsLazyPagingItems()
                        } else null
                        TvPlayerPanelHost(
                            panel = panel,
                            relatedSubjects = relatedSubjects,
                            staff = staff,
                            characters = characters,
                            comments = comments,
                            danmakuList = danmakuList,
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
                onSeekBack = { onIntent(TvEpisodeIntent.SeekBack) },
                onNextEpisode = { onIntent(TvEpisodeIntent.NextEpisode) },
                onSeekForward = { onIntent(TvEpisodeIntent.SeekForward) },
                onOpenSourceDialog = { onIntent(TvEpisodeIntent.OpenSourceDialog) },
                onCycleSpeed = { onIntent(TvEpisodeIntent.CycleSpeed) },
                onCycleAspect = { onIntent(TvEpisodeIntent.CycleAspectRatio) },
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
                                    // 焦点离开选集条 (按上回图标行等) 即收起
                                    stripHadFocus = false
                                    onIntent(TvEpisodeIntent.StripFocusLost)
                                }
                            },
                            currentCardModifier = Modifier
                                .tvFocusAnchor(focus, TvPlayerFocus.StripCurrent),
                            onClickEpisode = { onIntent(TvEpisodeIntent.SelectEpisode(it.episodeId)) },
                        )
                    }
                },
            )
        }

        // 按住倍速指示
        if (state.speedHolding) {
            PlayerCenterCapsule(
                "${formatSpeedLabel(TV_SPEED_HOLD_FACTOR)} 快进中 ▶▶",
                Modifier.align(Alignment.TopCenter).padding(top = 48.dp),
            )
        }

        // 中央闪烁 (±5s 静默 seek 反馈)
        state.seekFlash?.let { (text, _) ->
            PlayerCenterCapsule(text, Modifier.align(Alignment.Center))
        }

        // 数据源选择弹窗 (最上层)
        if (state.sourceDialogVisible) {
            TvPlayerSourceDialog(
                candidates = mediaCandidates,
                selected = selectedMedia,
                listState = dialogListState,
                containerModifier = Modifier.tvFocusAnchor(focus, TvPlayerFocus.SourceDialog),
                entryAnchorModifier = Modifier
                    .tvFocusAnchor(focus, TvPlayerFocus.SourceDialogEntry),
                onSelect = { onIntent(TvEpisodeIntent.SelectMedia(it)) },
            )
        }
    }
}

@Composable
private fun PlayerCenterCapsule(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier
            .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(24.dp))
            .padding(horizontal = 18.dp, vertical = 10.dp),
        style = MaterialTheme.typography.titleMedium,
        color = Color.White,
    )
}

/** 倍速展示: 1.0 -> "1x", 1.25 -> "1.25x". */
private fun formatSpeedLabel(speed: Float): String {
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
    Key.MediaFastForward, Key.MediaSkipForward, Key.MediaNext -> TvRemoteKey.Next
    Key.MediaRewind, Key.MediaSkipBackward, Key.MediaPrevious -> TvRemoteKey.Previous
    else -> TvRemoteKey.Other
}

private val KeyEvent.repeatCountCompat: Int
    get() = (nativeKeyEvent as? AndroidKeyEvent)?.repeatCount ?: 0

private val KeyEvent.eventTimeMillisCompat: Long
    get() = (nativeKeyEvent as? AndroidKeyEvent)?.eventTime ?: 0L
