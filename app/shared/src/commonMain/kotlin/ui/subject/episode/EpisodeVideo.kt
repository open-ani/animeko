/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.offset
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DisplaySettings
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import me.him188.ani.app.data.models.preference.DarkMode
import me.him188.ani.app.domain.media.player.MediaCacheProgressInfo
import me.him188.ani.app.domain.player.VideoLoadingState
import me.him188.ani.app.tools.rememberUiMonoTasker
import me.him188.ani.app.ui.foundation.LocalIsPreviewing
import me.him188.ani.app.ui.foundation.LocalPlatform
import me.him188.ani.app.ui.foundation.TextWithBorder
import me.him188.ani.app.ui.foundation.animation.AniAnimatedVisibility
import me.him188.ani.app.ui.foundation.effects.cursorVisibility
import me.him188.ani.app.ui.foundation.icons.AniIcons
import me.him188.ani.app.ui.foundation.icons.Forward85
import me.him188.ani.app.ui.foundation.icons.RightPanelClose
import me.him188.ani.app.ui.foundation.icons.RightPanelOpen
import me.him188.ani.app.ui.foundation.ifThen
import me.him188.ani.app.ui.foundation.interaction.WindowDragArea
import me.him188.ani.app.ui.foundation.isTv
import me.him188.ani.app.ui.foundation.FOCUS_REQ_DELAY_MILLIS
import me.him188.ani.app.ui.foundation.rememberDebugSettingsViewModel
import me.him188.ani.app.ui.foundation.theme.AniTheme
import me.him188.ani.app.ui.subject.episode.video.components.EpisodeVideoSideSheetPage
import me.him188.ani.app.ui.subject.episode.video.components.rememberStatusBarHeightAsState
import me.him188.ani.app.ui.subject.episode.video.loading.EpisodeVideoLoadingIndicator
import me.him188.ani.app.videoplayer.ui.PlaybackSpeedControllerState
import me.him188.ani.app.videoplayer.ui.PlayerControllerState
import me.him188.ani.app.videoplayer.ui.VideoAspectRatioControllerState
import me.him188.ani.app.videoplayer.ui.VideoPlayer
import me.him188.ani.app.videoplayer.ui.VideoScaffold
import me.him188.ani.app.videoplayer.ui.VideoSideSheetsController
import me.him188.ani.app.videoplayer.ui.gesture.GestureFamily
import me.him188.ani.app.videoplayer.ui.gesture.GestureLock
import me.him188.ani.app.videoplayer.ui.gesture.LevelController
import me.him188.ani.app.videoplayer.ui.gesture.LockableVideoGestureHost
import me.him188.ani.app.videoplayer.ui.gesture.ScreenshotButton
import me.him188.ani.app.videoplayer.ui.gesture.mouseFamily
import me.him188.ani.app.videoplayer.ui.gesture.rememberGestureIndicatorState
import me.him188.ani.app.videoplayer.ui.gesture.rememberSwipeSeekerState
import me.him188.ani.app.videoplayer.ui.hasPageAsState
import me.him188.ani.app.videoplayer.ui.progress.AudioSwitcher
import me.him188.ani.app.videoplayer.ui.progress.MediaProgressIndicatorText
import me.him188.ani.app.videoplayer.ui.progress.PlayerControllerBar
import me.him188.ani.app.videoplayer.ui.progress.PlayerControllerDefaults
import me.him188.ani.app.videoplayer.ui.progress.PlayerControllerDefaults.SpeedSwitcher
import me.him188.ani.app.videoplayer.ui.progress.PlayerControllerDefaults.VideoAspectRatioSelector
import me.him188.ani.app.videoplayer.ui.progress.PlayerProgressSliderState
import me.him188.ani.app.videoplayer.ui.progress.SubtitleSwitcher
import me.him188.ani.app.videoplayer.ui.rememberAlwaysOnRequester
import me.him188.ani.app.videoplayer.ui.rememberVideoSideSheetsController
import me.him188.ani.app.videoplayer.ui.top.PlayerTopBar
import me.him188.ani.app.videoplayer.ui.top.SystemTime
import me.him188.ani.utils.platform.annotations.TestOnly
import me.him188.ani.utils.platform.isDesktop
import me.him188.ani.utils.platform.isMobile
import org.openani.mediamp.MediampPlayer
import org.openani.mediamp.features.audioTracks
import org.openani.mediamp.features.subtitleTracks
import org.openani.mediamp.features.PlaybackSpeed

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import me.him188.ani.app.videoplayer.ui.gesture.rememberPlayerFastSkipState

import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.foundation.focusable
import org.openani.mediamp.togglePause

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
    onClickSkip85: (currentPositionMillis: Long) -> Unit = { playerState.skip(85_000L) },
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
    audioController: LevelController,
    brightnessController: LevelController,
    playbackSpeedControllerState: PlaybackSpeedControllerState?,
    videoAspectRatioControllerState: VideoAspectRatioControllerState?,
    leftBottomTips: @Composable () -> Unit,
    fullscreenSwitchButton: @Composable () -> Unit,
    sideSheets: @Composable (controller: VideoSideSheetsController<EpisodeVideoSideSheetPage>) -> Unit,
    modifier: Modifier = Modifier,
    maintainAspectRatio: Boolean = !expanded,
    gestureFamily: GestureFamily = LocalPlatform.current.mouseFamily,
    fastForwardSpeed: Float = 3f,
    contentWindowInsets: WindowInsets = WindowInsets(0.dp),


) {
    BoxWithConstraints(modifier) {
    // Don't rememberSavable. 刻意让每次切换都是隐藏的
    var isLocked by remember { mutableStateOf(false) }
    val sheetsController = rememberVideoSideSheetsController<EpisodeVideoSideSheetPage>()
    val anySideSheetVisible by sheetsController.hasPageAsState()

    // Focus management for Key Events (Media Keys & D-pad Seeking)
    val internalFocusRequester = remember { FocusRequester() }
    val requestFocus = { internalFocusRequester.requestFocus() }

    // Request focus ONLY ONCE when entering the composition (Fixes #FocusLoss on UI hide)
    LaunchedEffect(Unit) {
        requestFocus()
    }

    // Default Focus for Overlay
    // User requested "Detail" button (Likely Sidebar on Desktop, or Settings on TV)
    val settingsFocusRequester = remember { FocusRequester() }
    val sidebarFocusRequester = remember { FocusRequester() }
    val selectEpisodeFocusRequester = remember { FocusRequester() }
    val mediaSelectorFocusRequester = remember { FocusRequester() }
    
    // Track which FocusRequester should receive focus when side sheet closes
    var lastSideSheetFocusRequester by remember { mutableStateOf<FocusRequester?>(null) }
    
    val isBottomBarVisible = playerControllerState.visibility.bottomBar
    val isDesktop = LocalPlatform.current.isDesktop()
    val focusManager = LocalFocusManager.current
    
    // Track if any button currently has focus
    var topBarButtonHasFocus by remember { mutableStateOf(false) }
    var bottomBarButtonHasFocus by remember { mutableStateOf(false) }
    val anyButtonHasFocus by remember { derivedStateOf { topBarButtonHasFocus || bottomBarButtonHasFocus } }
    
    // Auto-focus settings button on Enter key press (only if no button has focus) (TV only)
    val isTv = LocalPlatform.current.isTv()
    LaunchedEffect(isBottomBarVisible, isTv, expanded) {
        if (isBottomBarVisible && isTv && expanded) {
            // Small delay to avoid frequent calls during rapid visibility changes
            kotlinx.coroutines.delay(FOCUS_REQ_DELAY_MILLIS)
            sidebarFocusRequester.requestFocus()
        }
    }

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

    // Hoisted state for finding seek/skip controller
    val indicatorTasker = rememberUiMonoTasker()
    val indicatorState = rememberGestureIndicatorState()
    val fastSkipState = rememberPlayerFastSkipState(
        requireNotNull(playerState.features[PlaybackSpeed]) {
            "PlaybackSpeed feature is required to initialize fast skip state"
        },
        indicatorState,
        fastForwardSpeed
    )

    // Swipe Seeker State (reused for keyboard seeking)
    val swipeSeekerState = rememberSwipeSeekerState(constraints.maxWidth) {
        playerState.skip(it * 1000L)
    }

    AniTheme(darkModeOverride = DarkMode.DARK) {
        VideoScaffold(
            expanded = expanded,
            modifier = Modifier
                .matchParentSize()
                .hoverable(videoInteractionSource)
                .cursorVisibility(showCursor)
                // Apply Focus and Key Listeners here
                .focusRequester(internalFocusRequester)
                .focusable()
                .onKeyEvent { keyEvent ->
                    // Handle Android TV remote Media Keys (play/pause) and navigation
                    
                    // Specific handling for Back key to support KeyUp (prevent repeat)
                    if (keyEvent.key == Key.Back) {
                        if (keyEvent.type == KeyEventType.KeyUp) {
                            // Priority 1: Close side sheet if open and return focus to the button that opened it
                            if (anySideSheetVisible) {
                                sheetsController.close()
                                // Request focus back to the button that opened the side sheet
                                lastSideSheetFocusRequester?.requestFocus()
                                lastSideSheetFocusRequester = null
                                true
                            }
                            // Priority 2: Handle Back key to return focus to video player
                            else if (anyButtonHasFocus) {
                                focusManager.clearFocus()
                                requestFocus()
                                true
                            } else {
                                false
                            }
                        } else {
                            // Consume KeyDown to prevent system back action while holding
                            true
                        }
                    } else if (keyEvent.type == KeyEventType.KeyDown) {
                        when (keyEvent.key) {
                            Key.DirectionLeft -> {
                                // Seek backward when no button has focus and no side sheet is open
                                if (!anyButtonHasFocus && !anySideSheetVisible) {
                                    swipeSeekerState.onSeek(-5)
                                    true
                                } else {
                                    false
                                }
                            }
                            Key.DirectionRight -> {
                                // Seek forward when no button has focus and no side sheet is open
                                if (!anyButtonHasFocus && !anySideSheetVisible) {
                                    swipeSeekerState.onSeek(5)
                                    true
                                } else {
                                    false
                                }
                            }
                            Key.Enter, Key.DirectionCenter -> {
                                // Show controller and request focus to settings button if no button currently has focus
                                if (!anyButtonHasFocus) {
                                    // First show the controller
                                    playerControllerState.toggleFullVisible(true)
                                    // Then request focus to settings button after a short delay to allow UI to appear
                                    settingsFocusRequester.requestFocus()
                                    true
                                } else {
                                    false
                                }
                            }
                            Key.MediaPlayPause -> {
                                playerState.togglePause()
                                true
                            }
                            Key.MediaPlay -> {
                                playerState.togglePause()
                                true
                            }
                            Key.MediaPause -> {
                                playerState.togglePause()
                                true
                            }
                            else -> false
                        }
                    } else {
                        false
                    }
                },
            contentWindowInsets = contentWindowInsets,
            maintainAspectRatio = maintainAspectRatio,
            controllerState = playerControllerState,
            gestureLocked = isLocked,
            topBar = {
                WindowDragArea {
                    PlayerTopBar(
                        Modifier
                            .testTag(TAG_EPISODE_VIDEO_TOP_BAR)
                            .onFocusEvent { focusState ->
                                if (topBarButtonHasFocus != focusState.hasFocus) {
                                    topBarButtonHasFocus = focusState.hasFocus
                                }
                            },

                        title = if (expanded) {
                            { title() }
                        } else {
                            null
                        },
                        actions = {
                            IconButton({ onClickSkip85(playerState.getCurrentPositionMillis()) }) {
                                Icon(AniIcons.Forward85, "快进 85 秒")
                            }
                            if (expanded) {
                                IconButton(
                                    {
                                        lastSideSheetFocusRequester = mediaSelectorFocusRequester
                                        sheetsController.navigateTo(EpisodeVideoSideSheetPage.MEDIA_SELECTOR)
                                    },
                                    Modifier
                                        .testTag(TAG_SHOW_MEDIA_SELECTOR)
                                        .focusRequester(mediaSelectorFocusRequester),
                                ) {
                                    Icon(Icons.Rounded.DisplaySettings, contentDescription = "数据源")
                                }
                            }
                            IconButton(
                                {
                                    lastSideSheetFocusRequester = settingsFocusRequester
                                    sheetsController.navigateTo(EpisodeVideoSideSheetPage.PLAYER_SETTINGS)
                                },
                                Modifier.testTag(TAG_SHOW_SETTINGS).focusRequester(settingsFocusRequester),
                            ) {
                                Icon(Icons.Rounded.Settings, contentDescription = "设置")
                            }
                            if (expanded && LocalPlatform.current.isDesktop()) {
                                IconButton(
                                    { onToggleSidebar(!sidebarVisible) },
                                    Modifier.testTag(TAG_COLLAPSE_SIDEBAR).focusRequester(sidebarFocusRequester),
                                ) {
                                    if (sidebarVisible) {
                                        Icon(AniIcons.RightPanelClose, contentDescription = "折叠侧边栏")
                                    } else {
                                        Icon(AniIcons.RightPanelOpen, contentDescription = "展开侧边栏")
                                    }
                                }
                            }
                        },
                        windowInsets = contentWindowInsets,
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
                    Text("预览模式")
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
                // Moved swipeSeekerState up
                val videoPropertiesState by playerState.mediaProperties.collectAsState(null)

                val enableSwipeToSeek by remember {
                    derivedStateOf {
                        videoPropertiesState?.let { it.durationMillis != 0L } == true
                    }
                }

                // Moved indicatorTasker, indicatorState up
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

                        requestFocus()
                        playerState.togglePause()
                    },
                    onToggleFullscreen = onClickFullScreen,
                    onExitFullscreen = onExitFullscreen,
                    onToggleDanmaku = onToggleDanmaku,
                    family = gestureFamily,
                    indicatorState,
                    fastForwardSpeed = fastForwardSpeed,
                )
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
            rhsButtons = {
                if (expanded && LocalPlatform.current.isDesktop()) {
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
                    controllerState = playerControllerState,
                    onButtonFocusChanged = { hasFocus -> 
                        if (bottomBarButtonHasFocus != hasFocus) {
                            bottomBarButtonHasFocus = hasFocus
                        }
                    },
                    startActions = {
                        val isPlaying by remember(playerState) { playerState.playbackState.map { it.isPlaying } }
                            .collectAsStateWithLifecycle(false)
                        PlayerControllerDefaults.PlaybackIcon(
                            isPlaying = { isPlaying },
                            onClick = {
                                requestFocus()
                                playerState.togglePause()
                            },
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
                        MediaProgressIndicatorText(progressSliderState)
                    },
                    progressSlider = {
                        PlayerControllerDefaults.MediaProgressSlider(
                            progressSliderState,
                            cacheProgressInfoFlow = cacheProgressInfoFlow,
                            showPreviewTimeTextOnThumb = expanded,
                        )
                    },
                    danmakuEditor = danmakuEditor,
                    endActions = {
                        if (expanded) {
                            PlayerControllerDefaults.SelectEpisodeIcon(
                                onClick = {
                                    lastSideSheetFocusRequester = selectEpisodeFocusRequester
                                    sheetsController.navigateTo(EpisodeVideoSideSheetPage.EPISODE_SELECTOR)
                                },
                                modifier = Modifier.focusRequester(selectEpisodeFocusRequester)
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
                            expanded,
                            onClickFullscreen = onClickFullScreen,
                        )
                    },
                    expanded = expanded,
                )
            },
            detachedProgressSlider = detachedProgressSlider,
            floatingBottomEnd = { fullscreenSwitchButton() },
            rhsSheet = { sideSheets(sheetsController) },
            leftBottomTips = leftBottomTips,
        )
    }
    }
}

@Stable
object EpisodeVideoDefaults
