/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.subject.episode

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.outlined.Analytics
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.DisplaySettings
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import me.him188.ani.app.data.models.preference.DarkMode
import me.him188.ani.app.data.models.preference.VideoScaffoldConfig
import me.him188.ani.app.domain.media.player.ChunkState
import me.him188.ani.app.domain.media.player.MediaCacheProgressInfo
import me.him188.ani.app.domain.media.player.staticMediaCacheProgressState
import me.him188.ani.app.domain.player.VideoLoadingState
import me.him188.ani.app.tools.rememberUiMonoTasker
import me.him188.ani.app.ui.episode.share.MediaShareData
import me.him188.ani.app.ui.foundation.LocalIsPreviewing
import me.him188.ani.app.ui.foundation.LocalPlatform
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.foundation.TextWithBorder
import me.him188.ani.app.ui.foundation.animation.AniAnimatedVisibility
import me.him188.ani.app.ui.foundation.animation.LocalAniMotionScheme
import me.him188.ani.app.ui.foundation.effects.cursorVisibility
import me.him188.ani.app.ui.foundation.icons.AniIcons
import me.him188.ani.app.ui.foundation.icons.Forward80
import me.him188.ani.app.ui.foundation.icons.Forward85
import me.him188.ani.app.ui.foundation.icons.Forward90
import me.him188.ani.app.ui.foundation.icons.RightPanelClose
import me.him188.ani.app.ui.foundation.icons.RightPanelOpen
import me.him188.ani.app.ui.foundation.icons.SubtitleGear
import me.him188.ani.app.ui.foundation.ifNotNullThen
import me.him188.ani.app.ui.foundation.ifThen
import me.him188.ani.app.ui.foundation.input.LocalActiveInputSource
import me.him188.ani.app.ui.foundation.input.trackActiveInputSource
import me.him188.ani.app.ui.foundation.interaction.WindowDragArea
import me.him188.ani.app.ui.foundation.rememberDebugSettingsViewModel
import me.him188.ani.app.ui.foundation.theme.AniTheme
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.always_on_top
import me.him188.ani.app.ui.lang.subject_episode_cache
import me.him188.ani.app.ui.lang.subject_episode_collapse_sidebar
import me.him188.ani.app.ui.lang.subject_episode_danmaku_settings_title
import me.him188.ani.app.ui.lang.subject_episode_expand_sidebar
import me.him188.ani.app.ui.lang.subject_episode_external_links
import me.him188.ani.app.ui.lang.subject_episode_fast_forward_seconds
import me.him188.ani.app.ui.lang.subject_episode_more_options
import me.him188.ani.app.ui.lang.subject_episode_preview_mode
import me.him188.ani.app.ui.lang.subject_episode_select_media_source
import me.him188.ani.app.ui.lang.video_player_stats_title_hide
import me.him188.ani.app.ui.lang.video_player_stats_title_show
import me.him188.ani.app.ui.lang.video_player_video_enhancement
import me.him188.ani.app.ui.lang.watch_together_title
import me.him188.ani.app.ui.mediafetch.TestMediaSourceResultListPresentation
import me.him188.ani.app.ui.mediafetch.ViewKind
import me.him188.ani.app.ui.mediafetch.rememberTestMediaSelectorState
import me.him188.ani.app.ui.mediafetch.request.TestMediaFetchRequest
import me.him188.ani.app.ui.settings.danmaku.createTestDanmakuRegexFilterState
import me.him188.ani.app.ui.subject.episode.details.components.ShareEpisodeDropdown
import me.him188.ani.app.ui.subject.episode.details.components.VideoEnhancementDropdown
import me.him188.ani.app.ui.subject.episode.video.DEFAULT_OP_ED_SKIP_DURATION
import me.him188.ani.app.ui.subject.episode.video.components.EpisodeVideoSideSheetPage
import me.him188.ani.app.ui.subject.episode.video.components.EpisodeVideoSideSheets
import me.him188.ani.app.ui.subject.episode.video.components.FloatingFullscreenSwitchButton
import me.him188.ani.app.ui.subject.episode.video.components.SideSheets
import me.him188.ani.app.ui.subject.episode.video.components.rememberStatusBarHeightAsState
import me.him188.ani.app.ui.subject.episode.video.loading.EpisodeVideoLoadingIndicator
import me.him188.ani.app.ui.subject.episode.video.sidesheet.DanmakuRegexFilterSettings
import me.him188.ani.app.ui.subject.episode.video.sidesheet.EpisodeSelectorSheet
import me.him188.ani.app.ui.subject.episode.video.sidesheet.MediaSelectorSheet
import me.him188.ani.app.ui.subject.episode.video.settings.LocalSideSheetRootWindowInsets
import me.him188.ani.app.ui.subject.episode.video.sidesheet.rememberTestEpisodeSelectorState
import me.him188.ani.app.ui.subject.episode.video.topbar.EpisodePlayerTitle
import me.him188.ani.app.ui.watchtogether.LocalWatchTogetherPlayerController
import me.him188.ani.app.videoplayer.ui.ControllerVisibility
import me.him188.ani.app.videoplayer.ui.MutablePlayerFullscreenState
import me.him188.ani.app.videoplayer.ui.NoOpVideoAspectRatio
import me.him188.ani.app.videoplayer.ui.PlaybackSpeedControllerState
import me.him188.ani.app.videoplayer.ui.PlayerControllerState
import me.him188.ani.app.videoplayer.ui.PlayerFullscreenState
import me.him188.ani.app.videoplayer.ui.PlayerStatsOverlay
import me.him188.ani.app.videoplayer.ui.VideoAspectRatioControllerState
import me.him188.ani.app.videoplayer.ui.VideoPlayer
import me.him188.ani.app.videoplayer.ui.VideoScaffold
import me.him188.ani.app.videoplayer.ui.VideoSideSheetsController
import me.him188.ani.app.videoplayer.ui.gesture.GestureFamily
import me.him188.ani.app.videoplayer.ui.gesture.GestureIndicator
import me.him188.ani.app.videoplayer.ui.gesture.GestureIndicatorState
import me.him188.ani.app.videoplayer.ui.gesture.GestureLock
import me.him188.ani.app.videoplayer.ui.gesture.LevelController
import me.him188.ani.app.videoplayer.ui.gesture.LockableVideoGestureHost
import me.him188.ani.app.videoplayer.ui.gesture.NoOpLevelController
import me.him188.ani.app.videoplayer.ui.gesture.ScreenshotButton
import me.him188.ani.app.videoplayer.ui.gesture.SkipDirection
import me.him188.ani.app.videoplayer.ui.gesture.SwipeSeekerConfig
import me.him188.ani.app.videoplayer.ui.gesture.SwipeSeekerState.Companion.swipeToSeek
import me.him188.ani.app.videoplayer.ui.gesture.gestureFamilyOf
import me.him188.ani.app.videoplayer.ui.gesture.hasPointerDevice
import me.him188.ani.app.videoplayer.ui.gesture.longPressFastSkip
import me.him188.ani.app.videoplayer.ui.gesture.mouseFamily
import me.him188.ani.app.videoplayer.ui.gesture.rememberGestureIndicatorState
import me.him188.ani.app.videoplayer.ui.gesture.rememberPlayerFastSkipState
import me.him188.ani.app.videoplayer.ui.gesture.rememberSwipeSeekerState
import me.him188.ani.app.videoplayer.ui.gesture.swipeBrightnessControlWithIndicator
import me.him188.ani.app.videoplayer.ui.gesture.swipeVolumeControlWithIndicator
import me.him188.ani.app.videoplayer.ui.hasPageAsState
import me.him188.ani.app.videoplayer.ui.progress.AudioSwitcher
import me.him188.ani.app.videoplayer.ui.progress.MediaProgressFramePreviewState
import me.him188.ani.app.videoplayer.ui.progress.MediaProgressIndicatorText
import me.him188.ani.app.videoplayer.ui.progress.MediaProgressSliderDefaults
import me.him188.ani.app.videoplayer.ui.progress.PlayerControllerBar
import me.him188.ani.app.videoplayer.ui.progress.PlayerControllerDefaults
import me.him188.ani.app.videoplayer.ui.progress.PlayerControllerDefaults.SpeedSwitcher
import me.him188.ani.app.videoplayer.ui.progress.PlayerControllerDefaults.VideoAspectRatioSelector
import me.him188.ani.app.videoplayer.ui.progress.PlayerProgressSliderState
import me.him188.ani.app.videoplayer.ui.progress.ProgressSliderCenteredPreviewFrame
import me.him188.ani.app.videoplayer.ui.progress.SubtitleSwitcher
import me.him188.ani.app.videoplayer.ui.progress.TouchSeekState
import me.him188.ani.app.videoplayer.ui.progress.rememberMediaProgressSliderState
import me.him188.ani.app.videoplayer.ui.rememberAlwaysOnRequester
import me.him188.ani.app.videoplayer.ui.rememberPlayerStatsState
import me.him188.ani.app.videoplayer.ui.rememberVideoControllerState
import me.him188.ani.app.videoplayer.ui.rememberVideoSideSheetsController
import me.him188.ani.app.videoplayer.ui.top.PlayerTopBar
import me.him188.ani.app.videoplayer.ui.top.SystemTime
import me.him188.ani.app.videoplayer.videoenhancement.VideoEnhancementController
import me.him188.ani.app.videoplayer.videoenhancement.VideoEnhancementMode
import me.him188.ani.utils.platform.annotations.TestOnly
import me.him188.ani.utils.platform.isAndroid
import me.him188.ani.utils.platform.isDesktop
import me.him188.ani.utils.platform.isMobile
import org.jetbrains.compose.resources.stringResource
import org.openani.mediamp.MediampPlayer
import org.openani.mediamp.features.PlaybackSpeed
import org.openani.mediamp.features.audioTracks
import org.openani.mediamp.features.subtitleTracks
import org.openani.mediamp.test.TestMediampPlayer
import org.openani.mediamp.togglePlayWhenReady
import kotlin.time.Duration

internal const val TAG_EPISODE_VIDEO_TOP_BAR = "EpisodeVideoTopBar"

internal const val TAG_HOVER_MODE_GESTURE_AREA = "HoverModeGestureArea"

internal const val TAG_DANMAKU_SETTINGS_SHEET = "DanmakuSettingsSheet"
internal const val TAG_SHOW_MEDIA_SELECTOR = "ShowMediaSelector"
internal const val TAG_VIDEO_ENHANCEMENT = "VideoEnhancement"
internal const val TAG_SHOW_SETTINGS = "ShowSettings"
internal const val TAG_COLLAPSE_SIDEBAR = "collapseSidebar"
internal const val TAG_WATCH_TOGETHER_MENU_ITEM = "WatchTogetherMenuItem"

internal const val TAG_MEDIA_SELECTOR_SHEET = "MediaSelectorSheet"
internal const val TAG_EPISODE_SELECTOR_SHEET = "EpisodeSelectorSheet"

/**
 * 悬停模式判定的最小铰链夹角, 单位为度. 夹角不超过该值时视为完全折叠, 不进入悬停模式.
 */
internal const val HOVER_MODE_MIN_HINGE_ANGLE_DEGREES = 30f

/**
 * 悬停模式判定的最大铰链夹角, 单位为度. 铰链夹角不超过该值时视为半折叠.
 */
internal const val HOVER_MODE_MAX_HINGE_ANGLE_DEGREES = 160f

/**
 * 铰链夹角是否处于悬停模式的半折叠区间: 大于 [HOVER_MODE_MIN_HINGE_ANGLE_DEGREES] 且不超过 [HOVER_MODE_MAX_HINGE_ANGLE_DEGREES].
 */
internal val Float.isHingeAngleInHoverModeRange: Boolean
    get() = this > HOVER_MODE_MIN_HINGE_ANGLE_DEGREES && this <= HOVER_MODE_MAX_HINGE_ANGLE_DEGREES

/**
 * 剧集详情页面顶部的视频控件.
 * @param title 仅在全屏时显示的标题
 * @param fullscreenState 全屏状态与全屏请求. 控制栏按钮、双击、F 键、上下滑手势全部走它
 * @param hoverMode 折叠屏悬停模式: 上半屏为视频与覆盖层控制器, 下半屏为播放控制器面板.
 * 面板与正常全屏一样未操作则自动隐藏, 隐藏时下半屏只响应快捷手势 (横滑快进/快退, 双击暂停等), 单击恢复显示.
 * 锁定时下半屏仅显示锁定按钮 (同样自动隐藏, 单击重新显示), 不响应其他手势. 仅在全屏时生效.
 */
@Composable
internal fun EpisodeVideoImpl(
    playerState: MediampPlayer,
    expanded: Boolean,
    hoverMode: Boolean = false,
    hasNextEpisode: Boolean,
    onClickNextEpisode: () -> Unit,
    playerControllerState: PlayerControllerState,
    opEdSkipDuration: Duration = DEFAULT_OP_ED_SKIP_DURATION,
    onClickSkipOpEd: (currentPositionMillis: Long) -> Unit = {
        playerState.skip(opEdSkipDuration.inWholeMilliseconds)
    },
    title: @Composable () -> Unit,
    danmakuHost: @Composable () -> Unit,
    danmakuEnabled: Boolean,
    onToggleDanmaku: () -> Unit,
    videoLoadingStateFlow: Flow<VideoLoadingState>,
    fullscreenState: PlayerFullscreenState,
    alwaysOnTop: Boolean = false,
    onToggleAlwaysOnTop: (() -> Unit)? = null,
    danmakuEditor: @Composable() (RowScope.() -> Unit),
    onClickScreenshot: () -> Unit,
    detachedProgressSlider: @Composable () -> Unit,
    sidebarVisible: Boolean,
    onToggleSidebar: (isCollapsed: Boolean) -> Unit,
    progressSliderState: PlayerProgressSliderState,
    cacheProgressInfoFlow: Flow<MediaCacheProgressInfo>,
    framePreview: MediaProgressFramePreviewState? = null,
    audioController: LevelController,
    brightnessController: LevelController,
    playbackSpeedControllerState: PlaybackSpeedControllerState?,
    videoAspectRatioControllerState: VideoAspectRatioControllerState?,
    videoEnhancement: VideoEnhancementController? = null,
    leftBottomTips: @Composable () -> Unit,
    fullscreenSwitchButton: @Composable () -> Unit,
    sideSheets: @Composable (controller: VideoSideSheetsController<EpisodeVideoSideSheetPage>) -> Unit,
    shareData: MediaShareData,
    onClickCache: () -> Unit,
    modifier: Modifier = Modifier,
    maintainAspectRatio: Boolean = !expanded,
    gestureFamily: GestureFamily = gestureFamilyOf(
        LocalActiveInputSource.current.current,
        LocalPlatform.current.mouseFamily,
    ),
    fastForwardSpeed: Float = 3f,
    contentWindowInsets: WindowInsets = WindowInsets(0.dp),
) {
    // Don't rememberSavable. 刻意让每次切换都是隐藏的
    var isLocked by remember { mutableStateOf(false) }
    var showPlayerStats by remember { mutableStateOf(false) }
    val playerStats by rememberPlayerStatsState(playerState)
    val sheetsController = rememberVideoSideSheetsController<EpisodeVideoSideSheetPage>()
    val anySideSheetVisible by sheetsController.hasPageAsState()
    val previewModeText = stringResource(Lang.subject_episode_preview_mode)
    val watchTogetherPlayerController = LocalWatchTogetherPlayerController.current

    // auto hide cursor
    val videoInteractionSource = remember { MutableInteractionSource() }
    val isVideoHovered by videoInteractionSource.collectIsHoveredAsState()
    val showCursor by remember(playerControllerState) {
        derivedStateOf {
            !isVideoHovered || (playerControllerState.visibility.bottomBar
                    || playerControllerState.visibility.detachedSlider
                    || anySideSheetVisible)
        }
    }
    val indicatorState = rememberGestureIndicatorState()
    val swipeSeekerConfig = SwipeSeekerConfig.Default
    val touchSeekState = rememberPlayerTouchSeekState(
        controllerState = playerControllerState,
        indicatorState = indicatorState,
        swipeSeekerConfig = swipeSeekerConfig,
    )
    val videoPropertiesState by playerState.mediaProperties.collectAsState(null)
    val enableSwipeToSeek by remember {
        derivedStateOf {
            videoPropertiesState?.let { it.durationMillis != 0L } == true
        }
    }
    val indicatorTasker = rememberUiMonoTasker()
    // 暂停/恢复并给出指示器反馈. 上半屏双击与悬停模式下半屏手势区共用
    val onTogglePauseResumeWithIndicator: () -> Unit = {
        if (playerState.state.value.playWhenReady) {
            indicatorTasker.launch {
                indicatorState.showPausedLong()
            }
        } else {
            indicatorTasker.launch {
                indicatorState.showResumedLong()
            }
        }
        playerState.togglePlayWhenReady()
    }

    AniTheme(darkModeOverride = DarkMode.DARK) {
        val progressSliderColors = MediaProgressSliderDefaults.colors()
        // 选集、字幕、画面适应、倍速等功能按钮: 非悬停时在底部控制栏末尾, 悬停模式时在下半屏面板的功能行
        val playerFeatureActions: @Composable RowScope.() -> Unit = {
            if (expanded) {
                PlayerControllerDefaults.SelectEpisodeIcon(
                    onClick = { sheetsController.navigateTo(EpisodeVideoSideSheetPage.EPISODE_SELECTOR) },
                )

                if (LocalPlatform.current.isDesktop()) {
                    playerState.audioTracks?.let {
                        PlayerControllerDefaults.AudioSwitcher(it)
                    }
                }

                playerState.subtitleTracks?.let {
                    PlayerControllerDefaults.SubtitleSwitcher(it)
                }

                val videoAspectRatioAlwaysOnRequester =
                    rememberAlwaysOnRequester(playerControllerState, "videoAspectRatioSelector")
                videoAspectRatioControllerState?.also { controller ->
                    VideoAspectRatioSelector(controller) {
                        if (it) {
                            videoAspectRatioAlwaysOnRequester.request()
                        } else {
                            videoAspectRatioAlwaysOnRequester.cancelRequest()
                        }
                    }
                }

                val playbackSpeedAlwaysOnRequester =
                    rememberAlwaysOnRequester(playerControllerState, "speedSwitcher")
                playbackSpeedControllerState?.also { controller ->
                    SpeedSwitcher(controller) {
                        if (it) {
                            playbackSpeedAlwaysOnRequester.request()
                        } else {
                            playbackSpeedAlwaysOnRequester.cancelRequest()
                        }
                    }
                }
            }
        }
        // 顶栏右侧按钮组: 非悬停时在顶栏, 悬停模式时在下半屏面板的功能行
        val topBarActions: @Composable RowScope.() -> Unit = {
            EpisodeVideoTopBarActions(
                playerState = playerState,
                expanded = expanded,
                opEdSkipDuration = opEdSkipDuration,
                onClickSkipOpEd = onClickSkipOpEd,
                sheetsController = sheetsController,
                shareData = shareData,
                onClickCache = onClickCache,
                onClickWatchTogether = watchTogetherPlayerController::toggle,
                playerControllerState = playerControllerState,
                videoEnhancement = videoEnhancement,
                sidebarVisible = sidebarVisible,
                onToggleSidebar = onToggleSidebar,
                playerStatsVisible = showPlayerStats,
                onTogglePlayerStats = { showPlayerStats = !showPlayerStats },
                alwaysOnTop = alwaysOnTop,
                onToggleAlwaysOnTop = onToggleAlwaysOnTop,
            )
        }
        // 悬停模式下半部分的常驻控制器面板, 内容与非悬停时覆盖在视频上的 bottomBar 相同
        val playerControllerBar: @Composable RowScope.() -> Unit = {
            PlayerControllerBar(
                startActions = {
                    val playWhenReady by remember(playerState) { playerState.state.map { it.playWhenReady } }
                        .collectAsStateWithLifecycle(false)
                    PlayerControllerDefaults.PlaybackIcon(
                        isPlaying = { playWhenReady },
                        onClick = { playerState.togglePlayWhenReady() },
                    )

                    if (hasNextEpisode && expanded) {
                        PlayerControllerDefaults.NextEpisodeIcon(
                            onClick = onClickNextEpisode,
                        )
                    }
                    PlayerControllerDefaults.DanmakuIcon(
                        danmakuEnabled,
                        onClick = { onToggleDanmaku() },
                    )

                    val audioLevelController = audioController as? MediampAudioLevelController
                    // 用「有没有鼠标」而不是「此刻在用鼠标」: 后者会让这个常驻控件随输入方式反复显隐.
                    val hasMouse = hasPointerDevice(
                        LocalPlatform.current,
                        LocalActiveInputSource.current.hasSeenMouse,
                    )
                    if (expanded && audioLevelController != null && hasMouse) {
                        val level by audioLevelController.levelFlow.collectAsState()
                        val isMute by audioLevelController.muteFlow.collectAsState()

                        PlayerControllerDefaults.AudioIcon(
                            level,
                            isMute = isMute,
                            maxValue = audioLevelController.range.endInclusive,
                            onClick = {
                                audioLevelController.toggleMute()
                            },
                            onchange = {
                                audioLevelController.setLevel(it)
                            },
                            controllerState = playerControllerState,
                        )
                    }
                },
                progressIndicator = {
                    MediaProgressIndicatorText(
                        progressSliderState,
                        playbackSpeedState = playbackSpeedControllerState,
                    )
                },
                progressSlider = {
                    PlayerControllerDefaults.MediaProgressSlider(
                        progressSliderState,
                        cacheProgressInfoFlow = cacheProgressInfoFlow,
                        showPreviewTimeTextOnThumb = expanded,
                        framePreview = framePreview,
                        showFramePreviewInPopup = expanded,
                        touchSeekState = touchSeekState,
                    )
                },
                danmakuEditor = danmakuEditor,
                endActions = {
                    playerFeatureActions()
                    PlayerControllerDefaults.FullscreenIcon(fullscreenState)
                },
                expanded = expanded,
                sliderOnly = playerControllerState.visibility == ControllerVisibility.InlineSliderOnly,
            )
        }
        val videoScaffold: @Composable (Modifier) -> Unit = { scaffoldModifier ->
        VideoScaffold(
            expanded = expanded,
            modifier = scaffoldModifier
                .hoverable(videoInteractionSource)
                .cursorVisibility(showCursor),
            contentWindowInsets = contentWindowInsets,
            maintainAspectRatio = maintainAspectRatio,
            controllerState = playerControllerState,
            gestureLocked = isLocked,
            topBar = {
                WindowDragArea {
                    PlayerTopBar(
                        Modifier.testTag(TAG_EPISODE_VIDEO_TOP_BAR),
                        title = if (expanded) {
                            { title() }
                        } else {
                            null
                        },
                        actions = {
                            // 悬停模式时顶栏只保留返回键和标题, 右侧按钮组移到下半屏面板的功能行
                            if (!hoverMode) {
                                topBarActions()
                            }
                        },
                        // VideoScaffold already applies top/horizontal insets around the top bar.
                        // Passing the same insets into TopAppBar duplicates the status-bar padding on iOS portrait.
                        windowInsets = WindowInsets(0.dp),
                    )
                }
            },
            centerOverlay = if (expanded && LocalPlatform.current.isMobile()) {
                { SystemTime() }
            } else {
                {}
            },
            video = {
                if (LocalIsPreviewing.current) {
                    Text(previewModeText)
                } else {
                    // Save the status bar height to offset the video player
                    val statusBarHeight by rememberStatusBarHeightAsState()

                    VideoPlayer(
                        playerState,
                        Modifier
                            .ifThen(statusBarHeight != 0.dp) {
                                offset(x = -statusBarHeight / 2, y = 0.dp)
                            }
                            .onSizeChanged {
                                videoEnhancement?.setViewportSize(it.width, it.height)
                            }
                            .matchParentSize(),
                    )
                }
            },
            danmakuHost = {
                AniAnimatedVisibility(
                    danmakuEnabled,
                ) {
                    Box(Modifier.matchParentSize()) {
                        danmakuHost()
                    }
                }
            },
            gestureHost = {
                val swipeSeekerState = rememberSwipeSeekerState(
                    constraints.maxWidth,
                    swipeSeekerConfig,
                ) {
                    playerState.skip(it * 1000L)
                }
                LockableVideoGestureHost(
                    playerControllerState,
                    swipeSeekerState,
                    progressSliderState,
                    playerState,
                    locked = isLocked,
                    enableSwipeToSeek = enableSwipeToSeek,
                    audioController = audioController,
                    brightnessController = brightnessController,
                    playbackSpeedControllerState,
                    fullscreenState,
                    Modifier,
                    onTogglePauseResume = onTogglePauseResumeWithIndicator,
                    onToggleDanmaku = onToggleDanmaku,
                    onTogglePlayerStats = {
                        showPlayerStats = !showPlayerStats
                    },
                    family = gestureFamily,
                    indicatorState,
                    fastForwardSpeed = fastForwardSpeed,
                )
            },
            playerStatsOverlay = {
                if (showPlayerStats) {
                    PlayerStatsOverlay(playerStats)
                }
            },
            floatingMessage = {
                Column {
                    val videoLoadingState by videoLoadingStateFlow.collectAsStateWithLifecycle(VideoLoadingState.Initial)
                    EpisodeVideoLoadingIndicator(
                        playerState,
                        videoLoadingState,
                        optimizeForFullscreen = expanded, // TODO: 这对 PC 其实可能不太好
                    )
                    val debugViewModel = rememberDebugSettingsViewModel()
                    @OptIn(TestOnly::class)
                    if (debugViewModel.isAppInDebugMode && debugViewModel.showControllerAlwaysOnRequesters) {
                        TextWithBorder(
                            "Always on requesters: \n" +
                                    playerControllerState.getAlwaysOnRequesters().joinToString("\n"),
                            style = MaterialTheme.typography.labelLarge,
                        )

                        TextWithBorder(
                            "ControllerVisibility: \n" + playerControllerState.visibility,
                            style = MaterialTheme.typography.labelLarge,
                        )

                        TextWithBorder(
                            "expanded: $expanded",
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
            },
            framePreviewOverlay = {
                if (!expanded) {
                    ProgressSliderCenteredPreviewFrame(
                        frame = framePreview?.frame,
                        borderColor = progressSliderColors.previewTimeBackgroundColor,
                    )
                }
            },
            rhsButtons = {
                // 悬停模式时截图按钮移到下半屏面板的顶部右侧
                if (expanded && !hoverMode && (LocalPlatform.current.isDesktop() || LocalPlatform.current.isAndroid())) {
                    ScreenshotButton(
                        onClick = onClickScreenshot,
                    )
                }
            },
            gestureLock = {
                // 悬停模式时锁定按钮移到下半屏面板的顶部右侧
                if (expanded && !hoverMode) {
                    GestureLock(isLocked = isLocked, onClick = { isLocked = !isLocked })
                }
            },
            bottomBar = if (hoverMode) ({}) else playerControllerBar,
            detachedProgressSlider = if (hoverMode) ({}) else detachedProgressSlider,
            floatingBottomEnd = { fullscreenSwitchButton() },
            rhsSheet = if (hoverMode) ({}) else ({ sideSheets(sheetsController) }),
            leftBottomTips = leftBottomTips,
        )
        }

        // 悬停模式切换时保持 videoScaffold 的组合位置不变 (仅改变尺寸与对齐),
        // 避免视频 surface 与控制器状态子树被销毁重建.
        Box(if (hoverMode) modifier.fillMaxSize() else modifier) {
            videoScaffold(
                if (hoverMode) {
                    Modifier.fillMaxHeight(0.5f).align(Alignment.TopCenter)
                } else {
                    Modifier
                },
            )
            if (hoverMode) {
                // 悬停模式: 上半屏为视频与覆盖层控制器, 下半屏为播放控制器面板.
                // 面板与正常全屏的底部控制栏一样随控制器显隐状态自动隐藏;
                // 隐藏时下半屏由手势区接管, 只响应快捷手势, 单击恢复显示;
                // 锁定时下半屏仅显示锁定按钮 (同样自动隐藏, 单击重新显示), 不响应其他手势.
                // 各状态之间的切换与正常全屏一样使用渐现渐隐动画.
                val panelVisible = playerControllerState.visibility.bottomBar
                val enterTransition = LocalAniMotionScheme.current.animatedVisibility.standardEnter
                val exitTransition = LocalAniMotionScheme.current.animatedVisibility.standardExit
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .fillMaxHeight(0.5f)
                        .background(Color.Black),
                ) {
                    val bottomContent = when {
                        isLocked -> HoverModeBottomContent.LOCKED
                        panelVisible -> HoverModeBottomContent.CONTROLLER_PANEL
                        else -> HoverModeBottomContent.GESTURE_AREA
                    }
                    AnimatedContent(
                        targetState = bottomContent,
                        transitionSpec = { enterTransition togetherWith exitTransition },
                        modifier = Modifier.fillMaxSize(),
                        label = "HoverModeBottomContent",
                    ) { content ->
                        when (content) {
                            HoverModeBottomContent.LOCKED -> HoverModeLockedPanel(
                                controllerState = playerControllerState,
                                onUnlock = { isLocked = false },
                                contentWindowInsets = contentWindowInsets,
                                modifier = Modifier.fillMaxSize(),
                            )

                            HoverModeBottomContent.CONTROLLER_PANEL -> HoverModeControllerPanel(
                                playerState = playerState,
                                playerControllerState = playerControllerState,
                                onTogglePauseResume = onTogglePauseResumeWithIndicator,
                                hasNextEpisode = hasNextEpisode,
                                onClickNextEpisode = onClickNextEpisode,
                                danmakuEnabled = danmakuEnabled,
                                onToggleDanmaku = onToggleDanmaku,
                                progressSliderState = progressSliderState,
                                cacheProgressInfoFlow = cacheProgressInfoFlow,
                                framePreview = framePreview,
                                touchSeekState = touchSeekState,
                                playbackSpeedControllerState = playbackSpeedControllerState,
                                fullscreenState = fullscreenState,
                                danmakuEditor = danmakuEditor,
                                topActions = {
                                    ScreenshotButton(onClick = onClickScreenshot, bordered = false)
                                    GestureLock(isLocked = isLocked, onClick = { isLocked = !isLocked }, bordered = false)
                                    topBarActions()
                                },
                                featureActions = {
                                    playerFeatureActions()
                                    PlayerControllerDefaults.FullscreenIcon(fullscreenState)
                                },
                                contentWindowInsets = contentWindowInsets,
                                gestureFamily = gestureFamily,
                                modifier = Modifier.fillMaxSize(),
                            )

                            HoverModeBottomContent.GESTURE_AREA -> HoverModeGestureArea(
                                controllerState = playerControllerState,
                                playerState = playerState,
                                enableSwipeToSeek = enableSwipeToSeek,
                                onTogglePauseResume = onTogglePauseResumeWithIndicator,
                                audioController = audioController,
                                brightnessController = brightnessController,
                                gestureFamily = gestureFamily,
                                swipeSeekerConfig = swipeSeekerConfig,
                                fastForwardSpeed = fastForwardSpeed,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                }

                // 悬停模式时, 侧边弹出窗口 (数据源, 选集, 弹幕设置等) 在下半屏面板区的右侧弹出;
                // 窗口位于屏幕下半部分, 不接触状态栏, 提供零 insets 使窗口覆盖面板的整个高度,
                // 否则 sheet 顶部会被状态栏 inset 向下推, 遮挡不住面板顶部右侧的按钮行
                CompositionLocalProvider(LocalSideSheetRootWindowInsets provides WindowInsets(0.dp)) {
                    Box(
                        Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .fillMaxHeight(0.5f),
                    ) {
                        sideSheets(sheetsController)
                    }
                }
            }
        }
    }
}

/**
 * 悬停模式下半屏的播放控制器面板, 显示时完全占用屏幕下半部分.
 *
 * - 顶部右侧: [topActions] (截图, 锁定, 跳过 OP/ED, 画质增强, 数据源, 更多等);
 * - 居中: 播放/暂停, 下一集;
 * - 下部整行: 弹幕开关 + 弹幕输入框;
 * - 下部整行: 进度条;
 * - 底部: 左侧时间与倍速, 右侧 [featureActions] (选集, 画面适应, 字幕, 倍速, 退出全屏等).
 *
 * 面板随控制器显隐状态显示与自动隐藏 (与正常全屏的底部控制栏一致); 隐藏期间下半屏由
 * [HoverModeGestureArea] 接管. 按住面板期间请求控制器常驻, 避免交互中途被自动隐藏.
 * 点击面板空白处与正常全屏一致: 隐藏控制器 (鼠标约定下单击为暂停/恢复, 双击暂停/恢复).
 */
@Composable
private fun HoverModeControllerPanel(
    playerState: MediampPlayer,
    playerControllerState: PlayerControllerState,
    onTogglePauseResume: () -> Unit,
    hasNextEpisode: Boolean,
    onClickNextEpisode: () -> Unit,
    danmakuEnabled: Boolean,
    onToggleDanmaku: () -> Unit,
    progressSliderState: PlayerProgressSliderState,
    cacheProgressInfoFlow: Flow<MediaCacheProgressInfo>,
    framePreview: MediaProgressFramePreviewState?,
    touchSeekState: TouchSeekState,
    playbackSpeedControllerState: PlaybackSpeedControllerState?,
    fullscreenState: PlayerFullscreenState,
    danmakuEditor: @Composable RowScope.() -> Unit,
    topActions: @Composable RowScope.() -> Unit,
    featureActions: @Composable RowScope.() -> Unit,
    contentWindowInsets: WindowInsets,
    gestureFamily: GestureFamily,
    modifier: Modifier = Modifier,
) {
    val alwaysOnRequester = rememberAlwaysOnRequester(playerControllerState, "hoverModeControllerPanel")
    val inputSourceState = LocalActiveInputSource.current
    CompositionLocalProvider(LocalContentColor provides Color.White) {
        Column(
            modifier
                .background(Color.Black)
                .trackActiveInputSource(inputSourceState)
                // 点击面板空白处隐藏控制器 (与正常全屏点击空白处一致); 按钮与进度条自行消费事件不受影响
                .combinedClickable(
                    remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {
                        // 按本次事件的指针类型解析, 组合期算好的 family 会慢一拍
                        val tapFamily = gestureFamilyOf(inputSourceState.latest, gestureFamily)
                        if (tapFamily.clickToPauseResume) {
                            onTogglePauseResume()
                        }
                        if (tapFamily.clickToToggleController) {
                            playerControllerState.toggleFullVisible()
                        }
                    },
                    onDoubleClick = {
                        val tapFamily = gestureFamilyOf(inputSourceState.latest, gestureFamily)
                        if (tapFamily.doubleClickToPauseResume) {
                            onTogglePauseResume()
                        }
                    },
                )
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val event = awaitPointerEvent()
                        if (event.changes.all { it.pressed }) {
                            // 按住面板期间保持显示, 松开后重新计时自动隐藏
                            alwaysOnRequester.request()
                        }
                        var releaseEvent = awaitPointerEvent()
                        while (releaseEvent.changes.any { it.pressed }) {
                            releaseEvent = awaitPointerEvent()
                        }
                        alwaysOnRequester.cancelRequest()
                    }
                }
                .windowInsetsPadding(
                    contentWindowInsets.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom),
                )
                .padding(horizontal = 16.dp),
        ) {
            // 顶部右侧按钮组: 截图, 锁定, 跳过 OP/ED, 画质增强, 数据源, 更多. 下移避开铰链区域
            Row(
                Modifier.fillMaxWidth().padding(top = 12.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                topActions()
            }
            // 居中: 播放/暂停完全居中, 下一集在其右侧固定偏移处
            Box(Modifier.fillMaxWidth().weight(1f)) {
                val playWhenReady by remember(playerState) { playerState.state.map { it.playWhenReady } }
                    .collectAsStateWithLifecycle(false)
                PlayerControllerDefaults.PlaybackIcon(
                    isPlaying = { playWhenReady },
                    onClick = { playerState.togglePlayWhenReady() },
                    modifier = Modifier.align(Alignment.Center),
                )
                if (hasNextEpisode) {
                    PlayerControllerDefaults.NextEpisodeIcon(
                        onClick = onClickNextEpisode,
                        modifier = Modifier.align(Alignment.Center).offset(x = 88.dp),
                    )
                }
            }
            // 弹幕开关 + 弹幕输入框. 与进度条之间留间距, 避免拖动进度条时误触
            Row(
                Modifier.fillMaxWidth().padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PlayerControllerDefaults.DanmakuIcon(danmakuEnabled, onClick = { onToggleDanmaku() })
                danmakuEditor()
            }
            // 进度条
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                PlayerControllerDefaults.MediaProgressSlider(
                    progressSliderState,
                    cacheProgressInfoFlow = cacheProgressInfoFlow,
                    showPreviewTimeTextOnThumb = true,
                    framePreview = framePreview,
                    showFramePreviewInPopup = true,
                    touchSeekState = touchSeekState,
                )
            }
            // 底部: 左侧时间与倍速, 右侧选集, 画面适应, 字幕, 倍速, 退出全屏
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                MediaProgressIndicatorText(
                    progressSliderState,
                    playbackSpeedState = playbackSpeedControllerState,
                )
                Spacer(Modifier.weight(1f))
                featureActions()
            }
        }
    }
}

/**
 * 悬停模式下半屏显示的内容, 用于状态间渐现渐隐切换.
 */
private enum class HoverModeBottomContent {
    /** 锁定: 仅显示锁定按钮 */
    LOCKED,

    /** 控制器面板 */
    CONTROLLER_PANEL,

    /** 面板隐藏时的手势区 */
    GESTURE_AREA,
}

/**
 * 悬停模式锁定状态下下半屏的内容: 仅显示锁定按钮, 不响应其他手势.
 *
 * 与正常全屏锁定一致: 锁定按钮随控制器显隐状态渐现渐隐, 无操作自动隐藏 (由上半屏的
 * LockedScreenGestureHost 计时), 隐藏时单击下半屏重新显示锁定按钮.
 * 按钮位置与控制器面板顶部按钮行一致, 方便原位点按解锁.
 */
@Composable
private fun HoverModeLockedPanel(
    controllerState: PlayerControllerState,
    onUnlock: () -> Unit,
    contentWindowInsets: WindowInsets,
    modifier: Modifier = Modifier,
) {
    val lockVisible = controllerState.visibility.gestureLock
    CompositionLocalProvider(LocalContentColor provides Color.White) {
        Box(modifier) {
            if (!lockVisible) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .clickable(
                            remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { controllerState.toggleFullVisible(true) },
                        ),
                )
            }
            AniAnimatedVisibility(
                visible = lockVisible,
                modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth(),
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(
                            contentWindowInsets.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom),
                        )
                        .padding(horizontal = 16.dp)
                        .padding(top = 12.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    GestureLock(isLocked = true, onClick = onUnlock, bordered = false)
                }
            }
        }
    }
}

/**
 * 悬停模式下半屏控制器面板隐藏时的手势区, 完全占用屏幕下半部分.
 *
 * - 单击: 恢复显示控制器面板;
 * - 双击: 暂停/恢复;
 * - 横滑: 快进/快退;
 * - 左侧竖滑: 亮度; 右侧竖滑: 音量;
 * - 长按: 倍速快进.
 *
 * 手势反馈指示器显示在下半屏顶部 (靠近铰链); 暂停/恢复的指示仍复用上半屏视频的指示器.
 * 锁定时不挂载本手势区, 下半屏由 [HoverModeLockedPanel] 接管.
 */
@Composable
private fun HoverModeGestureArea(
    controllerState: PlayerControllerState,
    playerState: MediampPlayer,
    enableSwipeToSeek: Boolean,
    onTogglePauseResume: () -> Unit,
    audioController: LevelController,
    brightnessController: LevelController,
    gestureFamily: GestureFamily,
    swipeSeekerConfig: SwipeSeekerConfig,
    fastForwardSpeed: Float,
    modifier: Modifier = Modifier,
) {
    val indicatorState = rememberGestureIndicatorState()
    BoxWithConstraints(modifier.testTag(TAG_HOVER_MODE_GESTURE_AREA)) {
        val maxHeight = maxHeight
        val swipeSeekerState = rememberSwipeSeekerState(
            constraints.maxWidth,
            swipeSeekerConfig,
        ) {
            playerState.skip(it * 1000L)
        }

        Box(Modifier.align(Alignment.TopCenter).padding(top = 16.dp)) {
            GestureIndicator(indicatorState, swipeSeekerState = swipeSeekerState)
        }

        val inputSourceState = LocalActiveInputSource.current
        val fastSkipState = playerState.features[PlaybackSpeed]?.let {
            rememberPlayerFastSkipState(it, indicatorState, fastForwardSpeed)
        }
        // 与上半屏手势区一致, 滑动类手势只属于触摸约定
        val swipeGesturesEnabled = gestureFamily == GestureFamily.TOUCH
        Box(
            Modifier
                .fillMaxSize()
                .trackActiveInputSource(inputSourceState)
                .combinedClickable(
                    remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {
                        // 按本次事件的指针类型解析, 组合期算好的 family 会慢一拍
                        val tapFamily = gestureFamilyOf(inputSourceState.latest, gestureFamily)
                        if (tapFamily.clickToPauseResume) {
                            onTogglePauseResume()
                        }
                        if (tapFamily.clickToToggleController) {
                            controllerState.toggleFullVisible(true)
                        }
                    },
                    onDoubleClick = {
                        val tapFamily = gestureFamilyOf(inputSourceState.latest, gestureFamily)
                        if (tapFamily.doubleClickToPauseResume) {
                            onTogglePauseResume()
                        }
                    },
                )
                .ifThen(enableSwipeToSeek) {
                    swipeToSeek(
                        swipeSeekerState,
                        Orientation.Horizontal,
                        enabled = swipeGesturesEnabled && !indicatorState.isAdjustingLevel,
                    )
                },
        ) {
            Row(
                Modifier
                    .matchParentSize()
                    // 长按快进不消费事件, 因此不影响点击; 由 down 事件的指针类型过滤, 不影响鼠标长按
                    .ifNotNullThen(fastSkipState) {
                        longPressFastSkip(it, SkipDirection.FORWARD, requiredPointerType = PointerType.Touch)
                    },
            ) {
                // 左侧竖滑调节亮度; 挂载看能力 (没有 BrightnessManager 时传进来是 NoOp), 是否响应看 enabled
                Box(
                    Modifier
                        .ifThen(brightnessController !== NoOpLevelController) {
                            swipeBrightnessControlWithIndicator(
                                brightnessController,
                                ((maxHeight - 100.dp) / 40).coerceAtLeast(2.dp),
                                indicatorState,
                                enabled = swipeGesturesEnabled && !swipeSeekerState.isSeeking,
                            )
                        }
                        .weight(1f)
                        .fillMaxHeight(),
                )
                // 中间区域不挂手势: 悬停模式已是全屏, 竖滑中部不切换全屏
                Box(Modifier.weight(1f).fillMaxHeight())
                // 右侧竖滑调节音量
                Box(
                    Modifier
                        .ifThen(audioController !== NoOpLevelController) {
                            swipeVolumeControlWithIndicator(
                                audioController,
                                ((maxHeight - 100.dp) / 40).coerceAtLeast(2.dp),
                                indicatorState,
                                enabled = swipeGesturesEnabled && !swipeSeekerState.isSeeking,
                            )
                        }
                        .weight(1f)
                        .fillMaxHeight(),
                )
            }
        }
    }
}

/**
 * 将进度条的通用触摸状态机接入播放器 UI：拖动期间保留 inline progress slider，
 * 手指向上滑过取消阈值时持续显示取消提示。
 *
 * 状态始终存在，是否响应触摸由 [MediaProgressSlider] 按本次指针事件判断，避免输入设备切换后的
 * 第一次拖动仍受组合期 [GestureFamily] 影响。
 */
@Composable
private fun rememberPlayerTouchSeekState(
    controllerState: PlayerControllerState,
    indicatorState: GestureIndicatorState,
    swipeSeekerConfig: SwipeSeekerConfig,
): TouchSeekState {
    val density = LocalDensity.current
    return remember(controllerState, indicatorState, swipeSeekerConfig, density) {
        // 同一 TouchSeekState 生命周期内，每次请求都由固定 requester 和 indicator ticket 撤销。
        val controllerRequester = Any()
        var indicatorTicket: Int? = null
        fun stopCancellationIndicator() {
            indicatorTicket?.let(indicatorState::stopSeekCancellation)
            indicatorTicket = null
        }
        TouchSeekState(
            swipeSeekerConfig = swipeSeekerConfig,
            density = density,
            onStateChanged = { state ->
                when (state) {
                    // 手势结束：恢复控制器的正常显隐，并关闭可能存在的取消提示。
                    TouchSeekState.State.Idle -> {
                        controllerState.cancelRequestInlineProgressSlider(controllerRequester)
                        stopCancellationIndicator()
                    }

                    // 正常拖动：保留 bottom bar 内正在接收触摸事件的原进度条。
                    TouchSeekState.State.Seeking -> {
                        controllerState.setRequestInlineProgressSlider(controllerRequester)
                        stopCancellationIndicator()
                    }

                    // 进入取消区域：进度条保持原位，只将中央指示器切换为取消提示。
                    TouchSeekState.State.Cancelling -> {
                        indicatorTicket = indicatorState.startSeekCancellation()
                    }
                }
            },
        )
    }
}

@Composable
private fun EpisodeVideoTopBarActions(
    playerState: MediampPlayer,
    expanded: Boolean,
    opEdSkipDuration: Duration,
    onClickSkipOpEd: (currentPositionMillis: Long) -> Unit,
    sheetsController: VideoSideSheetsController<EpisodeVideoSideSheetPage>,
    shareData: MediaShareData,
    onClickCache: () -> Unit,
    onClickWatchTogether: () -> Unit,
    playerControllerState: PlayerControllerState,
    videoEnhancement: VideoEnhancementController?,
    sidebarVisible: Boolean,
    onToggleSidebar: (isCollapsed: Boolean) -> Unit,
    playerStatsVisible: Boolean,
    onTogglePlayerStats: () -> Unit,
    alwaysOnTop: Boolean = false,
    onToggleAlwaysOnTop: (() -> Unit)? = null,
) {
    var showShareDropdown by rememberSaveable { mutableStateOf(false) }
    var showMoreDropdown by rememberSaveable { mutableStateOf(false) }
    var showVideoEnhancementDropdown by rememberSaveable { mutableStateOf(false) }

    val dropdownAlwaysOnRequester = rememberAlwaysOnRequester(playerControllerState, "topBarExternalActions")
    val isExternalDropdownVisible = showShareDropdown || showMoreDropdown || showVideoEnhancementDropdown
    val skipDurationSeconds = opEdSkipDuration.inWholeSeconds

    val fastForwardSecondsText = stringResource(Lang.subject_episode_fast_forward_seconds, skipDurationSeconds)
    val selectMediaSourceText = stringResource(Lang.subject_episode_select_media_source)
    val danmakuSettingsTitleText = stringResource(Lang.subject_episode_danmaku_settings_title)
    val moreOptionsText = stringResource(Lang.subject_episode_more_options)
    val externalLinksText = stringResource(Lang.subject_episode_external_links)
    val cacheText = stringResource(Lang.subject_episode_cache)
    val watchTogetherText = stringResource(Lang.watch_together_title)
    val showPlayerStatsText = stringResource(Lang.video_player_stats_title_show)
    val hidePlayerStatsText = stringResource(Lang.video_player_stats_title_hide)
    val collapseSidebarText = stringResource(Lang.subject_episode_collapse_sidebar)
    val expandSidebarText = stringResource(Lang.subject_episode_expand_sidebar)
    val videoEnhancementTitleText = stringResource(Lang.video_player_video_enhancement)

    DisposableEffect(dropdownAlwaysOnRequester, isExternalDropdownVisible) {
        if (isExternalDropdownVisible) {
            dropdownAlwaysOnRequester.request()
        } else {
            dropdownAlwaysOnRequester.cancelRequest()
        }
        onDispose {
            if (isExternalDropdownVisible) {
                dropdownAlwaysOnRequester.cancelRequest()
            }
        }
    }

    IconButton({ onClickSkipOpEd(playerState.currentPositionMillis.value) }) {
        val icon = when (skipDurationSeconds) {
            85L -> AniIcons.Forward85
            90L -> AniIcons.Forward90
            else -> AniIcons.Forward80
        }
        Icon(icon, fastForwardSecondsText)
    }

    if (expanded) {
        if (videoEnhancement != null) {
            Box {
                val mode by videoEnhancement.mode.collectAsState()
                IconButton(
                    onClick = { showVideoEnhancementDropdown = true },
                    modifier = Modifier.testTag(TAG_VIDEO_ENHANCEMENT),
                ) {
                    Icon(
                        Icons.Rounded.AutoAwesome,
                        contentDescription = videoEnhancementTitleText,
                        tint = if (mode != VideoEnhancementMode.OFF) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            LocalContentColor.current
                        },
                    )
                }

                VideoEnhancementDropdown(
                    videoEnhancement,
                    showVideoEnhancementDropdown,
                    onDismissRequest = { showVideoEnhancementDropdown = false },
                )
            }
        }

        IconButton(
            { sheetsController.navigateTo(EpisodeVideoSideSheetPage.MEDIA_SELECTOR) },
            Modifier.testTag(TAG_SHOW_MEDIA_SELECTOR),
        ) {
            Icon(Icons.Rounded.DisplaySettings, contentDescription = selectMediaSourceText)
        }
    }

    if (LocalPlatform.current.isDesktop() && onToggleAlwaysOnTop != null) {
        val alwaysOnTopText = stringResource(Lang.always_on_top)
        TooltipBox(
            positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
            tooltip = { PlainTooltip { Text(alwaysOnTopText) } },
            state = rememberTooltipState(),
        ) {
            IconButton(onToggleAlwaysOnTop) {
                Icon(
                    if (alwaysOnTop) Icons.Rounded.PushPin else Icons.Outlined.PushPin,
                    contentDescription = alwaysOnTopText,
                )
            }
        }
    }

    Box {
        IconButton({ showMoreDropdown = true }) {
            Icon(Icons.Rounded.MoreVert, contentDescription = moreOptionsText)
        }
        DropdownMenu(
            expanded = showMoreDropdown,
            onDismissRequest = { showMoreDropdown = false },
        ) {
            DropdownMenuItem(
                text = { Text(watchTogetherText) },
                onClick = {
                    showMoreDropdown = false
                    onClickWatchTogether()
                },
                leadingIcon = { Icon(Icons.Rounded.Groups, null) },
                modifier = Modifier.testTag(TAG_WATCH_TOGETHER_MENU_ITEM),
            )
            if (!expanded && videoEnhancement != null) {
                val mode by videoEnhancement.mode.collectAsState()
                DropdownMenuItem(
                    text = { Text(videoEnhancementTitleText) },
                    onClick = {
                        showMoreDropdown = false
                        showVideoEnhancementDropdown = true
                    },
                    leadingIcon = {
                        Icon(
                            Icons.Rounded.AutoAwesome,
                            contentDescription = videoEnhancementTitleText,
                            tint = if (mode != VideoEnhancementMode.OFF) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                LocalContentColor.current
                            },
                        )
                    },
                )
            }
            DropdownMenuItem(
                text = { Text(danmakuSettingsTitleText) },
                onClick = {
                    showMoreDropdown = false
                    sheetsController.navigateTo(EpisodeVideoSideSheetPage.PLAYER_SETTINGS)
                },
                leadingIcon = {
                    Icon(AniIcons.SubtitleGear, contentDescription = danmakuSettingsTitleText)
                },
            )
            DropdownMenuItem(
                text = { Text(if (playerStatsVisible) hidePlayerStatsText else showPlayerStatsText) },
                onClick = {
                    showMoreDropdown = false
                    onTogglePlayerStats()
                },
                leadingIcon = { Icon(Icons.Outlined.Analytics, null) },
            )
            DropdownMenuItem(
                text = { Text(externalLinksText) },
                onClick = {
                    showMoreDropdown = false
                    showShareDropdown = true
                },
                leadingIcon = { Icon(Icons.AutoMirrored.Rounded.OpenInNew, null) },
            )
            DropdownMenuItem(
                text = { Text(cacheText) },
                onClick = {
                    showMoreDropdown = false
                    onClickCache()
                },
                leadingIcon = { Icon(Icons.Rounded.Download, null) },
            )
        }
        ShareEpisodeDropdown(
            shareData,
            showShareDropdown,
            onDismissRequest = { showShareDropdown = false },
        )
        if (videoEnhancement != null && !expanded) {
            VideoEnhancementDropdown(
                videoEnhancement,
                showVideoEnhancementDropdown,
                onDismissRequest = { showVideoEnhancementDropdown = false },
            )
        }
    }

    if (expanded && LocalPlatform.current.isDesktop()) {
        IconButton(
            { onToggleSidebar(!sidebarVisible) },
            Modifier.testTag(TAG_COLLAPSE_SIDEBAR),
        ) {
            if (sidebarVisible) {
                Icon(AniIcons.RightPanelClose, contentDescription = collapseSidebarText)
            } else {
                Icon(AniIcons.RightPanelOpen, contentDescription = expandSidebarText)
            }
        }
    }
}

@Stable
object EpisodeVideoDefaults

@PreviewLightDark
@Preview(name = "Landscape Fullscreen", device = "spec:width=1280dp,height=800dp,dpi=240")
@Composable
private fun PreviewVideoScaffoldFullscreen() {
    PreviewVideoScaffoldImpl(expanded = true)
}

@PreviewLightDark
@Preview(name = "Portrait", heightDp = 300)
@Composable
private fun PreviewVideoScaffold() {
    PreviewVideoScaffoldImpl(expanded = false)
}

@PreviewLightDark
@Preview(name = "Detached Slider Fullscreen", device = "spec:width=1280dp,height=800dp,dpi=240")
@Composable
private fun PreviewDetachedSliderFullscreen() {
    PreviewVideoScaffoldImpl(expanded = true, controllerVisibility = ControllerVisibility.DetachedSliderOnly)
}

@PreviewLightDark
@Preview(name = "Detached Slider", heightDp = 300)
@Composable
private fun PreviewDetachedSlider() {
    PreviewVideoScaffoldImpl(expanded = false, controllerVisibility = ControllerVisibility.DetachedSliderOnly)
}

@PreviewLightDark
@Preview(name = "Hover Mode", device = "spec:width=420dp,height=960dp,dpi=440")
@Composable
private fun PreviewVideoScaffoldHoverMode() {
    PreviewVideoScaffoldImpl(expanded = true, hoverMode = true)
}

@OptIn(TestOnly::class)
@Composable
private fun PreviewVideoScaffoldImpl(
    expanded: Boolean,
    hoverMode: Boolean = false,
    controllerVisibility: ControllerVisibility = ControllerVisibility.Visible
) = ProvideCompositionLocalsForPreview {
    val scope = rememberCoroutineScope()
    val playerState = remember {
        TestMediampPlayer(scope.coroutineContext)
    }

    val controllerState = rememberVideoControllerState(initialVisibility = controllerVisibility)
    var isMediaSelectorVisible by remember { mutableStateOf(false) }
    var isEpisodeSelectorVisible by remember { mutableStateOf(false) }
    var danmakuEnabled by remember { mutableStateOf(true) }

    val progressSliderState = rememberMediaProgressSliderState(
        playerState,
        onPreview = {
            // not yet supported
        },
        onPreviewFinished = {
            playerState.seekTo(it)
        },
    )
    val videoScaffoldConfig = VideoScaffoldConfig.Default
    val fullscreenState = remember(expanded) { MutablePlayerFullscreenState(expanded) }
    val cacheProgressInfoFlow = staticMediaCacheProgressState(ChunkState.NONE).flow
    EpisodeVideoImpl(
        playerState = playerState,
        expanded = expanded,
        hoverMode = hoverMode,
        hasNextEpisode = true,
        onClickNextEpisode = {},
        playerControllerState = controllerState,
        onClickSkipOpEd = { playerState.skip(DEFAULT_OP_ED_SKIP_DURATION.inWholeMilliseconds) },
        title = {
            EpisodePlayerTitle(
                "28",
                "因为下次再见的时候就会很难为情",
                "葬送的芙莉莲",
            )
        },
        danmakuHost = {},
        danmakuEnabled = danmakuEnabled,
        onToggleDanmaku = { danmakuEnabled = !danmakuEnabled },
        videoLoadingStateFlow = MutableStateFlow(VideoLoadingState.Succeed(isBt = true)),
        fullscreenState = fullscreenState,
        danmakuEditor = {
            val (value, onValueChange) = remember { mutableStateOf("") }
            PlayerControllerDefaults.DanmakuTextField(
                value = value,
                onValueChange = onValueChange,
                Modifier.weight(1f),
            )
        },
        onClickScreenshot = {},
        detachedProgressSlider = {
            PlayerControllerDefaults.MediaProgressSlider(
                progressSliderState,
                cacheProgressInfoFlow = cacheProgressInfoFlow,
                enabled = false,
            )
        },
        sidebarVisible = true,
        onToggleSidebar = {},
        progressSliderState = progressSliderState,
        cacheProgressInfoFlow = cacheProgressInfoFlow,
        audioController = NoOpLevelController,
        brightnessController = NoOpLevelController,
        playbackSpeedControllerState = null,
        videoAspectRatioControllerState = remember {
            VideoAspectRatioControllerState(NoOpVideoAspectRatio, scope)
        },
        leftBottomTips = {
            PlayerControllerDefaults.LeftBottomTips(
                onClick = {},
                modifier = Modifier.padding(if (expanded) 16.dp else 8.dp),
            )
        },
        fullscreenSwitchButton = {
            EpisodeVideoDefaults.FloatingFullscreenSwitchButton(
                videoScaffoldConfig.fullscreenSwitchMode,
                fullscreenState,
            )
        },
        sideSheets = { sheetsController ->
            EpisodeVideoDefaults.SideSheets(
                sheetsController,
                controllerState,
                playerSettingsPage = {
                    EpisodeVideoSideSheets.DanmakuSettingsNavigatorSheet(
                        expanded = expanded,
                        state = createTestDanmakuRegexFilterState(),
                        onDismissRequest = { goBack() },
                        onNavigateToFilterSettings = {
                            sheetsController.navigateTo(EpisodeVideoSideSheetPage.EDIT_DANMAKU_REGEX_FILTER)
                        },
                    )
                },
                editDanmakuRegexFilterPage = {
                    DanmakuRegexFilterSettings(
                        state = createTestDanmakuRegexFilterState(),
                        onDismissRequest = { goBack() },
                        expanded = expanded,
                    )
                },
                mediaSelectorPage = {
                    val (viewKind, onViewKindChange) = rememberSaveable { mutableStateOf(ViewKind.WEB) }
                    EpisodeVideoSideSheets.MediaSelectorSheet(
                        mediaSelectorState = rememberTestMediaSelectorState(),
                        mediaSourceResultListPresentation = TestMediaSourceResultListPresentation,
                        viewKind = viewKind,
                        onViewKindChange = onViewKindChange,
                        fetchRequest = TestMediaFetchRequest,
                        onFetchRequestChange = {},
                        onDismissRequest = { goBack() },
                        onRefresh = {},
                        onRestartSource = {},
                    )
                },
                episodeSelectorPage = {
                    EpisodeVideoSideSheets.EpisodeSelectorSheet(
                        state = rememberTestEpisodeSelectorState(),
                        onDismissRequest = { goBack() },
                    )
                },
            )
        },
        shareData = MediaShareData.from(null, null),
        onClickCache = {},
    )
}
