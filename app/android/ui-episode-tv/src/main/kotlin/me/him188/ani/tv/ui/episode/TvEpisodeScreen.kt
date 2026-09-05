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
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
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
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import me.him188.ani.app.domain.player.VideoLoadingState
import me.him188.ani.app.ui.foundation.navigation.BackHandler
import me.him188.ani.app.videoplayer.ui.VideoPlayer
import me.him188.ani.tv.ui.foundation.focus.TV_CONFIRM_KEYS
import me.him188.ani.tv.ui.foundation.focus.TvFocusKey
import me.him188.ani.tv.ui.foundation.focus.rememberTvFocusScope
import me.him188.ani.tv.ui.foundation.focus.tvFocusAnchor
import me.him188.ani.tv.ui.foundation.focus.tvFocusLink
import me.him188.ani.tv.ui.foundation.focus.tvFocusNavSignal
import org.openani.mediamp.PlaybackState
import org.openani.mediamp.features.AspectRatioMode
import org.openani.mediamp.isPlaying
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
}

/** 播放页交互参数 (附录 A 时序). */
private object TvEpisodeScreenDefaults {
    /** ←→ 单按 seek 步长. */
    const val SeekStepMillis = 5_000L

    /** 连按升级拖拽预览的窗口. */
    const val SeekUpgradeWindowMillis = 620L

    /** 控制层自动隐藏 (暂停/拖拽/弹窗不隐藏). */
    const val ControlsAutoHideMillis = 5_000L

    /** 中央闪烁展示时长. */
    const val SeekFlashMillis = 650L

    /** 图标行回跳/前跳步长 (Replay10/Forward30 图标语义). */
    const val IconSeekBackMillis = 10_000L
    const val IconSeekForwardMillis = 30_000L
}

/** 覆盖层状态机 (§8.2). [controlsVisible] false = HIDDEN 层. */
@Stable
private class TvPlayerOverlayState {
    var controlsVisible by mutableStateOf(true)

    /** 交互代数: 任意按键自增, 重置自动隐藏计时. */
    var interactionGeneration by mutableIntStateOf(0)
        private set

    var stripExpanded by mutableStateOf(false)

    /** 拖拽预览位置; null = 非预览态. */
    var scrubMillis by mutableStateOf<Long?>(null)

    var speedHolding by mutableStateOf(false)

    var sourceDialogVisible by mutableStateOf(false)

    /** 中央闪烁 (±5s 静默 seek 反馈): 文案 + 代数 (连按重新计时). */
    var seekFlash by mutableStateOf<Pair<String, Int>?>(null)

    fun bump() {
        interactionGeneration++
    }

    fun flash(text: String) {
        seekFlash = text to ((seekFlash?.second ?: 0) + 1)
    }
}

/** 确认键按住追踪 (判据同 tvLongPressKey: 系统 KeyDown 连发计数, 首个连发即长按). */
private class ConfirmHoldTracker {
    var tracking = false
    var fired = false

    fun reset() {
        tracking = false
        fired = false
    }
}

@Composable
fun TvEpisodeScreen(
    viewModel: TvEpisodeViewModel,
    modifier: Modifier = Modifier,
) {
    val player = viewModel.player
    val playbackState by player.playbackState.collectAsState()
    val loadingState by viewModel.videoLoadingState.collectAsState()
    val title by viewModel.titleFlow.collectAsState()
    val mediaLabel by viewModel.currentMediaLabel.collectAsState()
    val mediaProperties by player.mediaProperties.collectAsState()
    val bufferedFraction by viewModel.bufferedFractionFlow.collectAsState()
    val playbackSpeed by viewModel.playbackSpeedStateFlow.collectAsState()
    val aspectRatioMode by viewModel.aspectRatioModeFlow.collectAsState()
    val stripEpisodes by viewModel.episodeStripFlow.collectAsState()
    val currentEpisodeId by viewModel.currentEpisodeIdFlow.collectAsState()
    val mediaCandidates by viewModel.mediaCandidates.collectAsState()
    val selectedMedia by viewModel.selectedMedia.collectAsState()

    val focus = rememberTvFocusScope()
    focus.Resolver()
    focus.InitialFocus(TvPlayerFocus.SeekBar)
    val state = remember { TvPlayerOverlayState() }
    val holdTracker = remember { ConfirmHoldTracker() }
    val stripListState = rememberLazyListState()
    val dialogListState = rememberLazyListState()

    LaunchedEffect(Unit) {
        viewModel.onUIReady() // 启动 AutoSelect/自动连播等扩展 (§8.1)
    }

    // 位置显示节拍 (控制层可见时 500ms 刷新; UI 展示用, 非焦点时序)
    var positionMillis by remember { mutableLongStateOf(0L) }
    LaunchedEffect(player, state.controlsVisible) {
        if (!state.controlsVisible) return@LaunchedEffect
        while (isActive) {
            positionMillis = player.getCurrentPositionMillis()
            delay(500)
        }
    }

    // 顶栏时钟
    var clockText by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        val format = SimpleDateFormat("HH:mm", Locale.getDefault())
        while (isActive) {
            clockText = format.format(Date())
            delay(30_000)
        }
    }

    fun showControls() {
        state.controlsVisible = true
        state.bump()
        focus.request(TvPlayerFocus.SeekBar)
    }

    fun hideControls() {
        state.stripExpanded = false
        state.scrubMillis = null
        state.controlsVisible = false
        focus.request(TvPlayerFocus.Root)
    }

    /** 进入/推进拖拽预览: 预览点从当前位置 (或上个预览点) 平移 [deltaMillis]. */
    fun moveScrub(deltaMillis: Long) {
        val duration = mediaProperties?.durationMillis ?: 0L
        val upperBound = if (duration > 0) duration else Long.MAX_VALUE
        val base = state.scrubMillis ?: player.getCurrentPositionMillis()
        state.controlsVisible = true
        state.bump()
        state.scrubMillis = (base + deltaMillis).coerceIn(0L, upperBound)
        focus.request(TvPlayerFocus.SeekBar)
    }

    // 连按升级窗口判定用上次单按 seek 的按键事件时间 (事件自带时钟, 无轮询)
    val lastSeekPressAt = remember { LongHolder() }

    /** ←→ 单按: ±5s 静默 seek + 中央闪烁; [SeekUpgradeWindowMillis] 内连按升级拖拽预览. */
    fun onSeekPress(deltaMillis: Long, eventTimeMillis: Long) {
        if (eventTimeMillis - lastSeekPressAt.value <= TvEpisodeScreenDefaults.SeekUpgradeWindowMillis) {
            moveScrub(deltaMillis)
        } else {
            viewModel.seekBy(deltaMillis)
            state.flash(if (deltaMillis > 0) "+5 秒" else "-5 秒")
        }
        lastSeekPressAt.value = eventTimeMillis
    }

    fun togglePauseAndSurface() {
        val wasPlaying = playbackState.isPlaying
        viewModel.togglePause()
        // 暂停时唤出控制层 (§8.2; 暂停态不自动隐藏)
        if (wasPlaying && !state.controlsVisible) showControls() else state.bump()
    }

    fun handleKey(event: KeyEvent): Boolean {
        val key = event.key
        val isDown = event.type == KeyEventType.KeyDown
        val isNewPress = isDown && event.repeatCountCompat == 0

        // 全局媒体键 (§8.2): 播停 / 下一集 / 上一集
        when (key) {
            Key.MediaPlayPause, Key.MediaPlay, Key.MediaPause -> {
                if (isNewPress) togglePauseAndSurface()
                return true
            }

            Key.MediaFastForward, Key.MediaSkipForward, Key.MediaNext -> {
                if (isNewPress) {
                    viewModel.switchToNeighborEpisode(1)
                    showControls()
                }
                return true
            }

            Key.MediaRewind, Key.MediaSkipBackward, Key.MediaPrevious -> {
                if (isNewPress) {
                    viewModel.switchToNeighborEpisode(-1)
                    showControls()
                }
                return true
            }
        }

        // 数据源弹窗打开: 焦点未入弹窗 (查询中列表还空) 时吞掉导航键, 防止误动下层控制层
        if (state.sourceDialogVisible) {
            if (!focus.isFocused(TvPlayerFocus.SourceDialog) &&
                (key in TV_DPAD_KEYS || key in TV_CONFIRM_KEYS)
            ) {
                return true
            }
            return false
        }

        // 拖拽预览态 (§8.2): ←→ 移动预览点, 确认跳转; 返回取消走 BackHandler
        if (state.scrubMillis != null) {
            when (key) {
                Key.DirectionLeft -> {
                    if (isDown) moveScrub(-TvEpisodeScreenDefaults.SeekStepMillis)
                    return true
                }

                Key.DirectionRight -> {
                    if (isDown) moveScrub(TvEpisodeScreenDefaults.SeekStepMillis)
                    return true
                }

                in TV_CONFIRM_KEYS -> {
                    if (isNewPress) {
                        state.scrubMillis?.let { viewModel.seekTo(it) }
                        state.scrubMillis = null
                        state.bump()
                    }
                    return true
                }

                Key.DirectionUp, Key.DirectionDown -> {
                    if (isNewPress) {
                        state.scrubMillis = null
                        state.bump()
                    }
                    return true
                }
            }
            return false
        }

        if (!state.controlsVisible) {
            // HIDDEN 层
            when (key) {
                in TV_CONFIRM_KEYS -> {
                    if (isDown) {
                        if (isNewPress) {
                            holdTracker.reset()
                            holdTracker.tracking = true
                        } else if (holdTracker.tracking && !holdTracker.fired) {
                            // 首个系统连发即长按 (≈500ms, 同 tvLongPressKey 判据): 2.5x 倍速
                            holdTracker.fired = true
                            viewModel.setSpeedHold(true)
                            state.speedHolding = true
                        }
                    } else {
                        val tracked = holdTracker.tracking
                        val fired = holdTracker.fired
                        holdTracker.reset()
                        if (tracked) {
                            if (fired) {
                                viewModel.setSpeedHold(false)
                                state.speedHolding = false
                            } else {
                                togglePauseAndSurface()
                            }
                        }
                    }
                    return true
                }

                Key.DirectionLeft -> {
                    if (isDown) onSeekPress(-TvEpisodeScreenDefaults.SeekStepMillis, event.eventTimeMillisCompat)
                    return true
                }

                Key.DirectionRight -> {
                    if (isDown) onSeekPress(TvEpisodeScreenDefaults.SeekStepMillis, event.eventTimeMillisCompat)
                    return true
                }

                Key.DirectionUp, Key.DirectionDown -> {
                    if (isNewPress) showControls()
                    return true
                }
            }
            return false
        }

        // CONTROLS 层: 任意按键刷新自动隐藏计时, 其余交给焦点系统
        if (isDown) state.bump()

        if (focus.isFocused(TvPlayerFocus.SeekBar)) {
            when (key) {
                Key.DirectionLeft -> {
                    if (isDown) onSeekPress(-TvEpisodeScreenDefaults.SeekStepMillis, event.eventTimeMillisCompat)
                    return true
                }

                Key.DirectionRight -> {
                    if (isDown) onSeekPress(TvEpisodeScreenDefaults.SeekStepMillis, event.eventTimeMillisCompat)
                    return true
                }

                in TV_CONFIRM_KEYS -> {
                    if (isNewPress) togglePauseAndSurface()
                    return true
                }
            }
        }

        // 图标行按下 → 展开选集条 (焦点送往当前集卡, 数据/滚动就绪后事件驱动送达)
        if (focus.isFocused(TvPlayerFocus.IconRow) && key == Key.DirectionDown) {
            if (isNewPress && !state.stripExpanded) state.stripExpanded = true
            return true
        }

        return false
    }

    // 自动隐藏 5s: 暂停 / 拖拽预览 / 选集条聚焦浏览外的弹窗 打开时不隐藏 (附录 A)
    val isPlaying = playbackState.isPlaying
    LaunchedEffect(state.interactionGeneration, isPlaying) {
        if (state.controlsVisible && isPlaying &&
            state.scrubMillis == null && !state.sourceDialogVisible
        ) {
            delay(TvEpisodeScreenDefaults.ControlsAutoHideMillis)
            hideControls()
        }
    }

    // 中央闪烁自动消隐
    LaunchedEffect(state.seekFlash) {
        val flash = state.seekFlash ?: return@LaunchedEffect
        delay(TvEpisodeScreenDefaults.SeekFlashMillis)
        if (state.seekFlash == flash) state.seekFlash = null
    }

    // 选集条展开: 等列表数据就绪 → 滚到当前集 → 送焦当前集卡 (全事件驱动)
    LaunchedEffect(state.stripExpanded) {
        if (!state.stripExpanded) return@LaunchedEffect
        val episodes = snapshotFlow { stripEpisodes }.first { it.isNotEmpty() }
        val index = episodes.indexOfFirst { it.episodeId == currentEpisodeId }
        if (index >= 0) stripListState.scrollToItem(index)
        focus.request(TvPlayerFocus.StripCurrent)
    }

    // 数据源弹窗打开: 等候选就绪 → 滚到当前选中 → 送焦
    LaunchedEffect(state.sourceDialogVisible) {
        if (!state.sourceDialogVisible) return@LaunchedEffect
        val list = snapshotFlow { mediaCandidates }.first { it.isNotEmpty() }
        val index = list.indexOf(selectedMedia).takeIf { it >= 0 } ?: 0
        dialogListState.scrollToItem(index)
        focus.request(TvPlayerFocus.SourceDialogEntry)
    }

    // 返回键逐层 (§8.2): 弹窗 → 拖拽 → 选集条 → 控制层 → 系统退出播放页
    BackHandler(
        enabled = state.sourceDialogVisible || state.scrubMillis != null ||
            state.stripExpanded || state.controlsVisible,
    ) {
        when {
            state.sourceDialogVisible -> {
                state.sourceDialogVisible = false
                state.bump()
                focus.request(TvPlayerFocus.SeekBar)
            }

            state.scrubMillis != null -> {
                state.scrubMillis = null
                state.bump()
            }

            state.stripExpanded -> {
                state.stripExpanded = false
                state.bump()
                focus.request(TvPlayerFocus.SeekBar)
            }

            else -> hideControls()
        }
    }

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
        VideoPlayer(
            player,
            Modifier
                .fillMaxSize()
                .focusProperties { canFocus = false },
        )

        // 挂载 Web 源解析器 (WebView, 不可见), 否则 WebVideoSourceResolver not attached
        viewModel.mediaResolver.ComposeContent()

        TvPlayerDanmakuHost(
            player = player,
            danmakuHostState = viewModel.danmakuHostState,
            danmakuEvent = viewModel.danmakuEventFlow,
            modifier = Modifier.fillMaxSize(),
        )

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
                durationMillis = mediaProperties?.durationMillis ?: 0L,
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
                seekBarModifier = Modifier
                    .tvFocusAnchor(focus, TvPlayerFocus.SeekBar)
                    // 显式方向链接: 上方胶囊行是静态信息, 下方直达图标行首钮 ——
                    // 空间搜索会落到不可见的视频/根节点 (§14.4-4 边缘元素显式声明去向)
                    .tvFocusLink(focus, down = TvPlayerFocus.IconRowEntry)
                    .focusProperties { up = FocusRequester.Cancel }
                    .focusable(),
                iconRowModifier = Modifier
                    .tvFocusAnchor(focus, TvPlayerFocus.IconRow)
                    .focusGroup(),
                seekBackButtonModifier = Modifier
                    .tvFocusAnchor(focus, TvPlayerFocus.IconRowEntry)
                    .tvFocusLink(focus, up = TvPlayerFocus.SeekBar),
                onSeekBack = {
                    viewModel.seekBy(-TvEpisodeScreenDefaults.IconSeekBackMillis)
                    state.bump()
                },
                onNextEpisode = { viewModel.switchToNeighborEpisode(1) },
                onSeekForward = {
                    viewModel.seekBy(TvEpisodeScreenDefaults.IconSeekForwardMillis)
                    state.bump()
                },
                onOpenSourceDialog = { state.sourceDialogVisible = true },
                onCycleSpeed = {
                    viewModel.cycleSpeed()
                    state.bump()
                },
                onCycleAspect = {
                    viewModel.cycleAspectRatio()
                    state.bump()
                },
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
                                    state.stripExpanded = false
                                }
                            },
                            currentCardModifier = Modifier
                                .tvFocusAnchor(focus, TvPlayerFocus.StripCurrent),
                            onClickEpisode = { episode ->
                                if (episode.episodeId != currentEpisodeId) {
                                    viewModel.switchEpisode(episode.episodeId)
                                }
                                // 选集后回纯视频看加载 (PR 语义); 送焦到常驻 Root 锚点 ——
                                // 聚焦卡随选集条收起销毁, 焦点跌落会与"送焦回控制层"竞态
                                hideControls()
                            },
                        )
                    }
                },
            )
        }

        // 按住倍速指示
        if (state.speedHolding) {
            PlayerCenterCapsule(
                "${formatSpeedLabel(TvEpisodeViewModel.SPEED_HOLD_FACTOR)} 快进中 ▶▶",
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
                onSelect = { media ->
                    viewModel.selectMedia(media)
                    state.sourceDialogVisible = false
                    state.bump()
                    focus.request(TvPlayerFocus.SeekBar)
                },
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

/** 装箱的 Long (remember 里存按键事件时间用). */
private class LongHolder {
    var value: Long = 0L
}

private val TV_DPAD_KEYS = setOf(
    Key.DirectionUp, Key.DirectionDown, Key.DirectionLeft, Key.DirectionRight,
)

private val KeyEvent.repeatCountCompat: Int
    get() = (nativeKeyEvent as? android.view.KeyEvent)?.repeatCount ?: 0

private val KeyEvent.eventTimeMillisCompat: Long
    get() = (nativeKeyEvent as? android.view.KeyEvent)?.eventTime ?: 0L
