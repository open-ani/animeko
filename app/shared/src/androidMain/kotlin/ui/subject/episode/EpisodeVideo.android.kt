/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.subject.episode

import android.content.res.Configuration.UI_MODE_NIGHT_NO
import android.content.res.Configuration.UI_MODE_NIGHT_YES
import android.content.res.Configuration.UI_MODE_TYPE_NORMAL
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import kotlinx.coroutines.flow.MutableStateFlow
import me.him188.ani.app.data.models.preference.VideoScaffoldConfig
import me.him188.ani.app.domain.media.player.ChunkState
import me.him188.ani.app.domain.media.player.staticMediaCacheProgressState
import me.him188.ani.app.domain.player.VideoLoadingState
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.foundation.preview.PHONE_LANDSCAPE
import me.him188.ani.app.ui.mediafetch.TestMediaSourceResultListPresentation
import me.him188.ani.app.ui.mediafetch.ViewKind
import me.him188.ani.app.ui.mediafetch.rememberTestMediaSelectorState
import me.him188.ani.app.ui.mediafetch.request.TestMediaFetchRequest
import me.him188.ani.app.ui.settings.danmaku.createTestDanmakuRegexFilterState
import me.him188.ani.app.ui.subject.episode.video.components.EpisodeVideoSideSheetPage
import me.him188.ani.app.ui.subject.episode.video.components.EpisodeVideoSideSheets
import me.him188.ani.app.ui.subject.episode.video.components.FloatingFullscreenSwitchButton
import me.him188.ani.app.ui.subject.episode.video.components.SideSheets
import me.him188.ani.app.ui.subject.episode.video.sidesheet.DanmakuRegexFilterSettings
import me.him188.ani.app.ui.subject.episode.video.sidesheet.EpisodeSelectorSheet
import me.him188.ani.app.ui.subject.episode.video.sidesheet.MediaSelectorSheet
import me.him188.ani.app.ui.subject.episode.video.sidesheet.rememberTestEpisodeSelectorState
import me.him188.ani.app.ui.subject.episode.video.topbar.EpisodePlayerTitle
import me.him188.ani.app.videoplayer.ui.ControllerVisibility
import me.him188.ani.app.videoplayer.ui.NoOpPlaybackSpeedController
import me.him188.ani.app.videoplayer.ui.PlaybackSpeedControllerState
import me.him188.ani.app.videoplayer.ui.gesture.NoOpLevelController
import me.him188.ani.app.videoplayer.ui.progress.PlayerControllerDefaults
import me.him188.ani.app.videoplayer.ui.progress.rememberMediaProgressSliderState
import me.him188.ani.app.videoplayer.ui.rememberVideoControllerState
import me.him188.ani.utils.platform.annotations.TestOnly
import org.openani.mediamp.DummyMediampPlayer


@Preview("Landscape Fullscreen - Light", device = PHONE_LANDSCAPE, uiMode = UI_MODE_NIGHT_NO)
@Preview("Landscape Fullscreen - Dark", device = PHONE_LANDSCAPE, uiMode = UI_MODE_NIGHT_YES or UI_MODE_TYPE_NORMAL)
@Composable
private fun PreviewVideoScaffoldFullscreen() {
    PreviewVideoScaffoldImpl(expanded = true)
}

@Preview("Portrait - Light", heightDp = 300, device = Devices.PHONE, uiMode = UI_MODE_NIGHT_NO)
@Preview("Portrait - Dark", heightDp = 300, device = Devices.PHONE, uiMode = UI_MODE_NIGHT_YES or UI_MODE_TYPE_NORMAL)
@Composable
private fun PreviewVideoScaffold() {
    PreviewVideoScaffoldImpl(expanded = false)
}

@Preview("Landscape Fullscreen - Light", device = PHONE_LANDSCAPE, uiMode = UI_MODE_NIGHT_NO)
@Preview("Landscape Fullscreen - Dark", device = PHONE_LANDSCAPE, uiMode = UI_MODE_NIGHT_YES or UI_MODE_TYPE_NORMAL)
@Composable
private fun PreviewDetachedSliderFullscreen() {
    PreviewVideoScaffoldImpl(expanded = true, controllerVisibility = ControllerVisibility.DetachedSliderOnly)
}

@Preview("Portrait - Light", heightDp = 300, device = Devices.PHONE, uiMode = UI_MODE_NIGHT_NO)
@Preview("Portrait - Dark", heightDp = 300, device = Devices.PHONE, uiMode = UI_MODE_NIGHT_YES or UI_MODE_TYPE_NORMAL)
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
        DummyMediampPlayer(scope.coroutineContext)
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
        onClickSkip85 = { playerState.skip(85_000L) },
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
        playbackSpeedControllerState = remember {
            PlaybackSpeedControllerState(NoOpPlaybackSpeedController, scope = scope)
        },
        leftBottomTips = {
            PlayerControllerDefaults.LeftBottomTips(onClick = {})
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
    )

//    VideoScaffold(
//        expanded = true,
//        modifier = Modifier,
//        controllersVisible = { controllerVisible },
//        gestureLocked = { isLocked },
//        topBar = {
//            EpisodeVideoTopBar(
//                title = {
//                    EpisodePlayerTitle(
//                        ep = "28",
//                        episodeTitle = "因为下次再见的时候会很难为情",
//                        subjectTitle = "葬送的芙莉莲"
//                    )
//                },
//
//                settings = {
//                    var config by remember {
//                        mutableStateOf(DanmakuConfig.Default)
//                    }
//                    var showSettings by remember { mutableStateOf(false) }
//                    if (showSettings) {
//                        EpisodeVideoSettingsSideSheet(
//                            onDismissRequest = { showSettings = false },
//                        ) {
//                            EpisodeVideoSettings(
//                                config,
//                                { config = it },
//                            )
//                        }
//                    }
//
//                }
//            )
//        },
//        video = {
////            AniKamelImage(resource = asyncPainterResource(data = "https://picsum.photos/536/354"))
//        },
//        danmakuHost = {
//        },
//        gestureHost = {
//            val swipeSeekerState = rememberSwipeSeekerState(constraints.maxWidth) {
//                playerState.seekTo(playerState.currentPositionMillis.value + it * 1000)
//            }
//            val indicatorState = rememberGestureIndicatorState()
//            val tasker = rememberUiMonoTasker()
//            val controllerState = rememberVideoControllerState()
//            LockableVideoGestureHost(
//                controllerState,
//                swipeSeekerState,
//                indicatorState,
//                fastSkipState = rememberPlayerFastSkipState(playerState, indicatorState),
//                locked = isLocked,
//                Modifier.padding(top = 100.dp),
//                onTogglePauseResume = {
//                    if (playerState.playbackState.value.isPlaying) {
//                        tasker.launch {
//                            indicatorState.showPausedLong()
//                        }
//                    } else {
//                        tasker.launch {
//                            indicatorState.showResumedLong()
//                        }
//                    }
//                    playerState.togglePause()
//                },
//            )
//        },
//        floatingMessage = {
//            Column {
//                EpisodeVideoLoadingIndicator(VideoLoadingState.Succeed, speedProvider = { 233.kiloBytes })
//            }
//
//        },
//        rhsBar = {
//            GestureLock(isLocked = isLocked, onClick = { isLocked = !isLocked })
//        },
//        bottomBar = {
//            val progressSliderState =
//                rememberProgressSliderState(playerState = playerState, onPreview = {}, onPreviewFinished = {})
//            PlayerControllerBar(
//                startActions = {
//                    val playing = playerState.playbackState.collectAsStateWithLifecycle()
//                    PlayerControllerDefaults.PlaybackIcon(
//                        isPlaying = { playing.value.isPlaying },
//                        onClick = { }
//                    )
//
//                    PlayerControllerDefaults.DanmakuIcon(
//                        true,
//                        onClick = { }
//                    )
//
//                },
//                progressIndicator = {
//                    ProgressIndicator(progressSliderState)
//                },
//                progressSlider = {
//                    ProgressSlider(progressSliderState)
//                },
//                danmakuEditor = {
//                    AniTheme(isDark = true) {
//                        var text by rememberSaveable { mutableStateOf("") }
//                        var sending by remember { mutableStateOf(false) }
//                        LaunchedEffect(key1 = sending) {
//                            if (sending) {
//                                delay(3.seconds)
//                                sending = false
//                            }
//                        }
//                        PlayerControllerDefaults.DanmakuTextField(
//                            text,
//                            onValueChange = { text = it },
//                            isSending = sending,
//                            onSend = {
//                                sending = true
//                                text = ""
//                            },
//                            modifier = Modifier.weight(1f)
//                        )
//                    }
//                },
//                endActions = {
//                    PlayerControllerDefaults.SubtitleSwitcher(playerState.subtitleTracks)
//                    val speed by playerState.playbackSpeed.collectAsStateWithLifecycle()
//                    SpeedSwitcher(
//                        speed,
//                        { playerState.setPlaybackSpeed(it) },
//                    )
//                    PlayerControllerDefaults.FullscreenIcon(
//                        isFullscreen,
//                        onClickFullscreen = {},
//                    )
//                },
//                expanded = isFullscreen,
//                Modifier.fillMaxWidth(),
//            )
//        },
//    )
}
