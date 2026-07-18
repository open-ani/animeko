/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.subject.episode

import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.outlined.Analytics
import androidx.compose.material.icons.rounded.DisplaySettings
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewLightDark
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
import me.him188.ani.app.ui.foundation.effects.cursorVisibility
import me.him188.ani.app.ui.foundation.icons.AniIcons
import me.him188.ani.app.ui.foundation.icons.Forward80
import me.him188.ani.app.ui.foundation.icons.Forward85
import me.him188.ani.app.ui.foundation.icons.Forward90
import me.him188.ani.app.ui.foundation.icons.RightPanelClose
import me.him188.ani.app.ui.foundation.icons.RightPanelOpen
import me.him188.ani.app.ui.foundation.icons.SubtitleGear
import me.him188.ani.app.ui.foundation.ifThen
import me.him188.ani.app.ui.foundation.interaction.WindowDragArea
import me.him188.ani.app.ui.foundation.rememberDebugSettingsViewModel
import me.him188.ani.app.ui.foundation.theme.AniTheme
import me.him188.ani.app.ui.lang.Lang
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
import me.him188.ani.app.ui.mediafetch.TestMediaSourceResultListPresentation
import me.him188.ani.app.ui.mediafetch.ViewKind
import me.him188.ani.app.ui.mediafetch.rememberTestMediaSelectorState
import me.him188.ani.app.ui.mediafetch.request.TestMediaFetchRequest
import me.him188.ani.app.ui.settings.danmaku.createTestDanmakuRegexFilterState
import me.him188.ani.app.ui.subject.episode.details.components.ShareEpisodeDropdown
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
import me.him188.ani.app.ui.subject.episode.video.sidesheet.rememberTestEpisodeSelectorState
import me.him188.ani.app.ui.subject.episode.video.topbar.EpisodePlayerTitle
import me.him188.ani.app.videoplayer.ui.ControllerVisibility
import me.him188.ani.app.videoplayer.ui.NoOpVideoAspectRatio
import me.him188.ani.app.videoplayer.ui.PlaybackSpeedControllerState
import me.him188.ani.app.videoplayer.ui.PlayerControllerState
import me.him188.ani.app.videoplayer.ui.PlayerStatsOverlay
import me.him188.ani.app.videoplayer.ui.VideoAspectRatioControllerState
import me.him188.ani.app.videoplayer.ui.VideoPlayer
import me.him188.ani.app.videoplayer.ui.VideoScaffold
import me.him188.ani.app.videoplayer.ui.VideoSideSheetsController
import me.him188.ani.app.videoplayer.ui.gesture.GestureFamily
import me.him188.ani.app.videoplayer.ui.gesture.GestureIndicatorState
import me.him188.ani.app.videoplayer.ui.gesture.GestureLock
import me.him188.ani.app.videoplayer.ui.gesture.LevelController
import me.him188.ani.app.videoplayer.ui.gesture.LockableVideoGestureHost
import me.him188.ani.app.videoplayer.ui.gesture.NoOpLevelController
import me.him188.ani.app.videoplayer.ui.gesture.ScreenshotButton
import me.him188.ani.app.videoplayer.ui.gesture.SwipeSeekerConfig
import me.him188.ani.app.videoplayer.ui.gesture.isInCancelArea
import me.him188.ani.app.videoplayer.ui.gesture.mouseFamily
import me.him188.ani.app.videoplayer.ui.gesture.rememberGestureIndicatorState
import me.him188.ani.app.videoplayer.ui.gesture.rememberSwipeSeekerState
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
import me.him188.ani.utils.platform.annotations.TestOnly
import me.him188.ani.utils.platform.isAndroid
import me.him188.ani.utils.platform.isDesktop
import me.him188.ani.utils.platform.isMobile
import org.jetbrains.compose.resources.stringResource
import org.openani.mediamp.MediampPlayer
import org.openani.mediamp.features.audioTracks
import org.openani.mediamp.features.subtitleTracks
import org.openani.mediamp.isPlaying
import org.openani.mediamp.test.TestMediampPlayer
import org.openani.mediamp.togglePause
import kotlin.time.Duration

internal const val TAG_EPISODE_VIDEO_TOP_BAR = "EpisodeVideoTopBar"

internal const val TAG_DANMAKU_SETTINGS_SHEET = "DanmakuSettingsSheet"
internal const val TAG_SHOW_MEDIA_SELECTOR = "ShowMediaSelector"
internal const val TAG_SHOW_SETTINGS = "ShowSettings"
internal const val TAG_COLLAPSE_SIDEBAR = "collapseSidebar"

internal const val TAG_MEDIA_SELECTOR_SHEET = "MediaSelectorSheet"
internal const val TAG_EPISODE_SELECTOR_SHEET = "EpisodeSelectorSheet"

/**
 * 剧集详情页面顶部的视频控件.
 * @param title 仅在全屏时显示的标题
 */
@Composable
internal fun EpisodeVideoImpl(
    playerState: MediampPlayer,
    expanded: Boolean,
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
    onClickFullScreen: () -> Unit,
    onExitFullscreen: () -> Unit,
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
    leftBottomTips: @Composable () -> Unit,
    fullscreenSwitchButton: @Composable () -> Unit,
    sideSheets: @Composable (controller: VideoSideSheetsController<EpisodeVideoSideSheetPage>) -> Unit,
    shareData: MediaShareData,
    onClickCache: () -> Unit,
    modifier: Modifier = Modifier,
    maintainAspectRatio: Boolean = !expanded,
    isFullscreen: Boolean = expanded,
    gestureFamily: GestureFamily = LocalPlatform.current.mouseFamily,
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
    // 桌面设备可能同时支持鼠标和触摸；当前 GestureFamily 不能按单次输入来源分流，
    // 因此桌面端仍使用 MOUSE 分支。后续实现来源级分流时再支持桌面触摸手势。
    // TODO: 根据触控能力与平台特性建立设备抽象，并据此选择手势策略。
    val touchSeekState = rememberPlayerTouchSeekState(
        enabled = gestureFamily == GestureFamily.TOUCH,
        controllerState = playerControllerState,
        indicatorState = indicatorState,
    )

    AniTheme(darkModeOverride = DarkMode.DARK) {
        val progressSliderColors = MediaProgressSliderDefaults.colors()
        VideoScaffold(
            expanded = expanded,
            modifier = modifier
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
                            EpisodeVideoTopBarActions(
                                playerState = playerState,
                                expanded = expanded,
                                opEdSkipDuration = opEdSkipDuration,
                                onClickSkipOpEd = onClickSkipOpEd,
                                sheetsController = sheetsController,
                                shareData = shareData,
                                onClickCache = onClickCache,
                                playerControllerState = playerControllerState,
                                sidebarVisible = sidebarVisible,
                                onToggleSidebar = onToggleSidebar,
                                playerStatsVisible = showPlayerStats,
                                onTogglePlayerStats = { showPlayerStats = !showPlayerStats },
                            )
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
                val swipeSeekerState = rememberSwipeSeekerState(constraints.maxWidth) {
                    playerState.skip(it * 1000L)
                }
                val videoPropertiesState by playerState.mediaProperties.collectAsState(null)
                val enableSwipeToSeek by remember {
                    derivedStateOf {
                        videoPropertiesState?.let { it.durationMillis != 0L } == true
                    }
                }

                val indicatorTasker = rememberUiMonoTasker()
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
                    Modifier,
                    onTogglePauseResume = {
                        if (playerState.playbackState.value.isPlaying) {
                            indicatorTasker.launch {
                                indicatorState.showPausedLong()
                            }
                        } else {
                            indicatorTasker.launch {
                                indicatorState.showResumedLong()
                            }
                        }
                        playerState.togglePause()
                    },
                    onToggleFullscreen = onClickFullScreen,
                    onExitFullscreen = onExitFullscreen,
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
            touchSeekState = touchSeekState,
            framePreviewOverlay = {
                if (!expanded) {
                    ProgressSliderCenteredPreviewFrame(
                        frame = framePreview?.frame,
                        borderColor = progressSliderColors.previewTimeBackgroundColor,
                    )
                }
            },
            rhsButtons = {
                if (expanded && (LocalPlatform.current.isDesktop() || LocalPlatform.current.isAndroid())) {
                    ScreenshotButton(
                        onClick = onClickScreenshot,
                    )
                }
            },
            gestureLock = {
                if (expanded) {
                    GestureLock(isLocked = isLocked, onClick = { isLocked = !isLocked })
                }
            },
            bottomBar = {
                PlayerControllerBar(
                    startActions = {
                        val isPlaying by remember(playerState) { playerState.playbackState.map { it.isPlaying } }
                            .collectAsStateWithLifecycle(false)
                        PlayerControllerDefaults.PlaybackIcon(
                            isPlaying = { isPlaying },
                            onClick = { playerState.togglePause() },
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
                        if (expanded && audioLevelController != null && gestureFamily == GestureFamily.MOUSE) {
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
                        PlayerControllerDefaults.FullscreenIcon(
                            isFullscreen,
                            onClickFullscreen = onClickFullScreen,
                        )
                    },
                    expanded = expanded,
                    sliderOnly = playerControllerState.visibility == ControllerVisibility.InlineSliderOnly,
                )
            },
            detachedProgressSlider = detachedProgressSlider,
            floatingBottomEnd = { fullscreenSwitchButton() },
            rhsSheet = { sideSheets(sheetsController) },
            leftBottomTips = leftBottomTips,
        )
    }
}

/**
 * 将进度条的通用触摸状态机接入播放器 UI：拖动期间保留 inline progress slider，
 * 手指进入取消区域时持续显示取消提示。非触屏分支返回 `null`，不改变原有交互。
 */
@Composable
private fun rememberPlayerTouchSeekState(
    enabled: Boolean,
    controllerState: PlayerControllerState,
    indicatorState: GestureIndicatorState,
): TouchSeekState? {
    if (!enabled) return null

    return remember(controllerState, indicatorState) {
        // requester 和 indicator ticket 跨状态迁移保持不变，确保每次请求都由同一实例撤销。
        val controllerRequester = Any()
        var indicatorTicket: Int? = null
        fun stopCancellationIndicator() {
            indicatorTicket?.let(indicatorState::stopSeekCancellation)
            indicatorTicket = null
        }
        TouchSeekState(
            isInCancelArea = SwipeSeekerConfig.Default::isInCancelArea,
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
    playerControllerState: PlayerControllerState,
    sidebarVisible: Boolean,
    onToggleSidebar: (isCollapsed: Boolean) -> Unit,
    playerStatsVisible: Boolean,
    onTogglePlayerStats: () -> Unit,
) {
    var showShareDropdown by rememberSaveable { mutableStateOf(false) }
    var showMoreDropdown by rememberSaveable { mutableStateOf(false) }
    val dropdownAlwaysOnRequester = rememberAlwaysOnRequester(playerControllerState, "topBarExternalActions")
    val isExternalDropdownVisible = showShareDropdown || showMoreDropdown
    val skipDurationSeconds = opEdSkipDuration.inWholeSeconds
    val fastForwardSecondsText = stringResource(Lang.subject_episode_fast_forward_seconds, skipDurationSeconds)
    val selectMediaSourceText = stringResource(Lang.subject_episode_select_media_source)
    val danmakuSettingsTitleText = stringResource(Lang.subject_episode_danmaku_settings_title)
    val moreOptionsText = stringResource(Lang.subject_episode_more_options)
    val externalLinksText = stringResource(Lang.subject_episode_external_links)
    val cacheText = stringResource(Lang.subject_episode_cache)
    val showPlayerStatsText = stringResource(Lang.video_player_stats_title_show)
    val hidePlayerStatsText = stringResource(Lang.video_player_stats_title_hide)
    val collapseSidebarText = stringResource(Lang.subject_episode_collapse_sidebar)
    val expandSidebarText = stringResource(Lang.subject_episode_expand_sidebar)

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

    IconButton({ onClickSkipOpEd(playerState.getCurrentPositionMillis()) }) {
        val icon = when (skipDurationSeconds) {
            85L -> AniIcons.Forward85
            90L -> AniIcons.Forward90
            else -> AniIcons.Forward80
        }
        Icon(icon, fastForwardSecondsText)
    }

    if (expanded) {
        IconButton(
            { sheetsController.navigateTo(EpisodeVideoSideSheetPage.MEDIA_SELECTOR) },
            Modifier.testTag(TAG_SHOW_MEDIA_SELECTOR),
        ) {
            Icon(Icons.Rounded.DisplaySettings, contentDescription = selectMediaSourceText)
        }
    }

    IconButton(
        { sheetsController.navigateTo(EpisodeVideoSideSheetPage.PLAYER_SETTINGS) },
        Modifier.testTag(TAG_SHOW_SETTINGS),
    ) {
        Icon(AniIcons.SubtitleGear, contentDescription = danmakuSettingsTitleText)
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

@OptIn(TestOnly::class)
@Composable
private fun PreviewVideoScaffoldImpl(
    expanded: Boolean,
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
    val onClickFullScreen = { }
    val cacheProgressInfoFlow = staticMediaCacheProgressState(ChunkState.NONE).flow
    EpisodeVideoImpl(
        playerState = playerState,
        expanded = expanded,
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
        onClickFullScreen = onClickFullScreen,
        onExitFullscreen = { },
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
                modifier = Modifier.padding(if (expanded) 16.dp else 8.dp)
            )
        },
        fullscreenSwitchButton = {
            EpisodeVideoDefaults.FloatingFullscreenSwitchButton(
                videoScaffoldConfig.fullscreenSwitchMode,
                isFullscreen = expanded,
                onClickFullScreen,
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
