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
import me.him188.ani.app.ui.foundation.rememberDebugSettingsViewModel
import me.him188.ani.app.ui.foundation.theme.AniTheme
import me.him188.ani.app.ui.subject.episode.video.components.EpisodeVideoSideSheetPage
import me.him188.ani.app.ui.subject.episode.video.components.rememberStatusBarHeightAsState
import me.him188.ani.app.ui.subject.episode.video.loading.EpisodeVideoLoadingIndicator
import me.him188.ani.app.videoplayer.ui.PlaybackSpeedControllerState
import me.him188.ani.app.videoplayer.ui.PlayerControllerState
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
import me.him188.ani.app.videoplayer.ui.progress.PlayerProgressSliderState
import me.him188.ani.app.videoplayer.ui.progress.SubtitleSwitcher
import me.him188.ani.app.videoplayer.ui.rememberAlwaysOnRequester
import me.him188.ani.app.videoplayer.ui.rememberVideoSideSheetsController
import me.him188.ani.app.videoplayer.ui.top.PlayerTopBar
import me.him188.ani.utils.platform.annotations.TestOnly
import me.him188.ani.utils.platform.isDesktop
import org.openani.mediamp.MediampPlayer
import org.openani.mediamp.features.audioTracks
import org.openani.mediamp.features.subtitleTracks
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
    leftBottomTips: @Composable () -> Unit,
    fullscreenSwitchButton: @Composable () -> Unit,
    sideSheets: @Composable (controller: VideoSideSheetsController<EpisodeVideoSideSheetPage>) -> Unit,
    modifier: Modifier = Modifier,
    maintainAspectRatio: Boolean = !expanded,
    gestureFamily: GestureFamily = LocalPlatform.current.mouseFamily,
    fastForwardSpeed: Float = 3f,
    contentWindowInsets: WindowInsets = WindowInsets(0.dp),
) {
    // Don't rememberSavable. 刻意让每次切换都是隐藏的
    var isLocked by remember { mutableStateOf(false) }
    val sheetsController = rememberVideoSideSheetsController<EpisodeVideoSideSheetPage>()
    val anySideSheetVisible by sheetsController.hasPageAsState()

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

    AniTheme(darkModeOverride = DarkMode.DARK) {
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
                            IconButton({ onClickSkip85(playerState.getCurrentPositionMillis()) }) {
                                Icon(AniIcons.Forward85, "快进 85 秒")
                            }
                            if (expanded) {
                                IconButton(
                                    { sheetsController.navigateTo(EpisodeVideoSideSheetPage.MEDIA_SELECTOR) },
                                    Modifier.testTag(TAG_SHOW_MEDIA_SELECTOR),
                                ) {
                                    Icon(Icons.Rounded.DisplaySettings, contentDescription = "数据源")
                                }
                            }
                            IconButton(
                                { sheetsController.navigateTo(EpisodeVideoSideSheetPage.PLAYER_SETTINGS) },
                                Modifier.testTag(TAG_SHOW_SETTINGS),
                            ) {
                                Icon(Icons.Rounded.Settings, contentDescription = "设置")
                            }
                            if (expanded && LocalPlatform.current.isDesktop()) {
                                IconButton(
                                    { onToggleSidebar(!sidebarVisible) },
                                    Modifier.testTag(TAG_COLLAPSE_SIDEBAR),
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
                val indicatorState = rememberGestureIndicatorState()
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

                            val alwaysOnRequester = rememberAlwaysOnRequester(playerControllerState, "speedSwitcher")
                            playbackSpeedControllerState?.also { controller ->
                                SpeedSwitcher(controller) {
                                    if (it) {
                                        alwaysOnRequester.request()
                                    } else {
                                        alwaysOnRequester.cancelRequest()
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

@Stable
object EpisodeVideoDefaults
