/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.subject.episode

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onChild
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.swipe
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.MutableStateFlow
import me.him188.ani.app.data.models.preference.DarkMode
import me.him188.ani.app.data.models.preference.FullscreenSwitchMode
import me.him188.ani.app.data.models.preference.VideoScaffoldConfig
import me.him188.ani.app.domain.media.player.ChunkState
import me.him188.ani.app.domain.media.player.staticMediaCacheProgressState
import me.him188.ani.app.domain.player.VideoLoadingState
import me.him188.ani.app.platform.PlatformWindow
import me.him188.ani.app.ui.danmaku.PlayerDanmakuEditor
import me.him188.ani.app.ui.episode.share.MediaShareData
import me.him188.ani.app.ui.foundation.LocalPlatform
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.foundation.input.LocalActiveInputSource
import me.him188.ani.app.ui.foundation.layout.LocalPlatformWindow
import me.him188.ani.app.ui.foundation.navigation.BackHandler
import me.him188.ani.app.ui.foundation.navigation.onBackNavigationInput
import me.him188.ani.app.ui.framework.AniComposeUiTest
import me.him188.ani.app.ui.framework.doesNotExist
import me.him188.ani.app.ui.framework.exists
import me.him188.ani.app.ui.framework.runAniComposeUiTest
import me.him188.ani.app.ui.mediafetch.TestMediaSourceResultListPresentation
import me.him188.ani.app.ui.mediafetch.ViewKind
import me.him188.ani.app.ui.mediafetch.rememberTestMediaSelectorState
import me.him188.ani.app.ui.mediafetch.request.TestMediaFetchRequest
import me.him188.ani.app.ui.settings.danmaku.createTestDanmakuRegexFilterState
import me.him188.ani.app.ui.subject.episode.video.components.DanmakuSettingsSheet
import me.him188.ani.app.ui.subject.episode.video.components.EpisodeVideoSideSheetPage
import me.him188.ani.app.ui.subject.episode.video.components.EpisodeVideoSideSheets
import me.him188.ani.app.ui.subject.episode.video.components.FloatingFullscreenSwitchButton
import me.him188.ani.app.ui.subject.episode.video.components.SideSheets
import me.him188.ani.app.ui.subject.episode.video.sidesheet.DanmakuRegexFilterSettings
import me.him188.ani.app.ui.subject.episode.video.sidesheet.EpisodeSelectorSheet
import me.him188.ani.app.ui.subject.episode.video.sidesheet.MediaSelectorSheet
import me.him188.ani.app.ui.subject.episode.video.sidesheet.rememberTestEpisodeSelectorState
import me.him188.ani.app.ui.watchtogether.LocalWatchTogetherPlayerController
import me.him188.ani.app.ui.watchtogether.WatchTogetherPlayerController
import me.him188.ani.app.videoplayer.ui.ControllerVisibility
import me.him188.ani.app.videoplayer.ui.NoOpPlaybackSpeedController
import me.him188.ani.app.videoplayer.ui.NoOpVideoAspectRatio
import me.him188.ani.app.videoplayer.ui.PlaybackSpeedControllerState
import me.him188.ani.app.videoplayer.ui.PlayerControllerState
import me.him188.ani.app.videoplayer.ui.VideoAspectRatioControllerState
import me.him188.ani.app.videoplayer.ui.gesture.GestureFamily
import me.him188.ani.app.videoplayer.ui.gesture.LevelController
import me.him188.ani.app.videoplayer.ui.gesture.NoOpLevelController
import me.him188.ani.app.videoplayer.ui.gesture.VIDEO_GESTURE_MOUSE_MOVE_SHOW_CONTROLLER_DURATION
import me.him188.ani.app.videoplayer.ui.gesture.VIDEO_GESTURE_TOUCH_SHOW_CONTROLLER_DURATION
import me.him188.ani.app.videoplayer.ui.gesture.gestureFamilyOf
import me.him188.ani.app.videoplayer.ui.gesture.mouseFamily
import me.him188.ani.app.videoplayer.ui.progress.MediaProgressFramePreviewState
import me.him188.ani.app.videoplayer.ui.progress.PlayerControllerDefaults
import me.him188.ani.app.videoplayer.ui.progress.PlayerProgressSliderState
import me.him188.ani.app.videoplayer.ui.progress.TAG_DANMAKU_ICON_BUTTON
import me.him188.ani.app.videoplayer.ui.progress.TAG_FULL_SCREEN_BUTTON
import me.him188.ani.app.videoplayer.ui.progress.TAG_MEDIA_PROGRESS_INDICATOR_TEXT
import me.him188.ani.app.videoplayer.ui.progress.TAG_PROGRESS_SLIDER
import me.him188.ani.app.videoplayer.ui.progress.TAG_PROGRESS_SLIDER_CENTERED_PREVIEW_FRAME
import me.him188.ani.app.videoplayer.ui.progress.TAG_PROGRESS_SLIDER_PREVIEW_FRAME
import me.him188.ani.app.videoplayer.ui.progress.TAG_PROGRESS_SLIDER_PREVIEW_POPUP
import me.him188.ani.app.videoplayer.ui.progress.TAG_SELECT_EPISODE_ICON_BUTTON
import me.him188.ani.app.videoplayer.ui.progress.TAG_SPEED_SWITCHER_DROPDOWN_MENU
import me.him188.ani.app.videoplayer.ui.progress.TAG_SPEED_SWITCHER_SLIDER
import me.him188.ani.app.videoplayer.ui.progress.TAG_SPEED_SWITCHER_TEXT_BUTTON
import me.him188.ani.app.videoplayer.ui.top.PlayerTopBar
import me.him188.ani.danmaku.ui.DanmakuConfig
import me.him188.ani.utils.platform.Arch
import me.him188.ani.utils.platform.Platform
import org.junit.jupiter.api.Disabled
import org.openani.mediamp.InternalForInheritanceMediampApi
import org.openani.mediamp.MediaStatus
import org.openani.mediamp.features.PlaybackSpeed
import org.openani.mediamp.source.UriMediaData
import org.openani.mediamp.test.TestMediampPlayer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

private const val TAG_DETACHED_PROGRESS_SLIDER = "detachedProgressSlider"
private const val TAG_DANMAKU_EDITOR = "danmakuEditor"

const val WAIT_TIMEOUT = 10_000L // Longer for slow CI

/**
 * 测试显示/隐藏进度条和 [GestureFamily]
 */
class EpisodeVideoControllerTest {
    private companion object {
        private val NORMAL_INVISIBLE = ControllerVisibility(
            topBar = false,
            bottomBar = false,
            floatingBottomEnd = true,
            rhsBar = false,
            gestureLock = false,
            detachedSlider = false,
        )

        private val LOCKED_VISIBLE = ControllerVisibility(
            topBar = false,
            bottomBar = false,
            floatingBottomEnd = true,
            rhsBar = false,
            gestureLock = true,
            detachedSlider = false,
        )

        private val NORMAL_VISIBLE = ControllerVisibility(
            topBar = true,
            bottomBar = true,
            floatingBottomEnd = false,
            rhsBar = true,
            gestureLock = true,
            detachedSlider = false,
        )

        private val PREVIEW_DETACHED_SLIDER = ControllerVisibility(
            topBar = false,
            bottomBar = false,
            floatingBottomEnd = false,
            rhsBar = false,
            gestureLock = false,
            detachedSlider = true,
        )

        private val PREVIEW_INLINE_SLIDER = ControllerVisibility(
            topBar = false,
            bottomBar = true,
            floatingBottomEnd = false,
            rhsBar = false,
            gestureLock = false,
            detachedSlider = false,
        )
    }


    private val controllerState = PlayerControllerState(ControllerVisibility.Invisible)
    private var currentPositionMillis by mutableLongStateOf(0L)
    private val progressSliderState: PlayerProgressSliderState = PlayerProgressSliderState(
        { currentPositionMillis },
        { 100_000 },
        { persistentListOf() },
        onPreview = {},
        onPreviewFinished = { currentPositionMillis = it },
    )

    private val SemanticsNodeInteractionsProvider.detachedProgressSlider
        get() = onNodeWithTag(TAG_DETACHED_PROGRESS_SLIDER, useUnmergedTree = true)
    private val SemanticsNodeInteractionsProvider.topBar
        get() = onNodeWithTag(TAG_EPISODE_VIDEO_TOP_BAR, useUnmergedTree = true)
    private val SemanticsNodeInteractionsProvider.previewPopup
        get() = onNodeWithTag(TAG_PROGRESS_SLIDER_PREVIEW_POPUP, useUnmergedTree = true)
    private val SemanticsNodeInteractionsProvider.progressSlider
        get() = onNodeWithTag(TAG_PROGRESS_SLIDER, useUnmergedTree = true)
    private val SemanticsNodeInteractionsProvider.danmakuEditor
        get() = onNodeWithTag(TAG_DANMAKU_EDITOR, useUnmergedTree = true)
    private val SemanticsNodeInteractionsProvider.danmakuIconButton
        get() = onNodeWithTag(TAG_DANMAKU_ICON_BUTTON, useUnmergedTree = true)
    private val SemanticsNodeInteractionsProvider.player
        get() = onNodeWithTag("PLAYER", useUnmergedTree = true)

    private val SemanticsNodeInteractionsProvider.fullScreenButton
        get() = onNodeWithTag(TAG_FULL_SCREEN_BUTTON, useUnmergedTree = true)
    private val SemanticsNodeInteractionsProvider.videoGestureHost
        get() = onNodeWithTag("VideoGestureHost", useUnmergedTree = true)
    private val SemanticsNodeInteractionsProvider.mediaProgressIndicatorText: SemanticsNodeInteraction
        get() = onNodeWithTag(TAG_MEDIA_PROGRESS_INDICATOR_TEXT, useUnmergedTree = true)

    @Composable
    private fun Player(
        /** null 表示不写死, 由 [EpisodeVideoImpl] 按当前输入设备推导 —— 混合设备的行为只能这样测. */
        gestureFamily: GestureFamily?,
        playerControllerState: PlayerControllerState = controllerState,
        onClickFullScreen: () -> Unit = {},
        onExitFullscreen: () -> Unit = {},
        onToggleDanmaku: () -> Unit = {},
        audioController: LevelController = NoOpLevelController,
        playbackSpeed: PlaybackSpeed = NoOpPlaybackSpeedController,
        onCommitPlaybackSpeed: (Float) -> Unit = {},
        opEdSkipDuration: Duration = 85.seconds,
        watchTogetherPlayerController: WatchTogetherPlayerController? = null,
        onPlayerStateCreated: (TestMediampPlayer) -> Unit = {},
        onPlatformWindow: (PlatformWindow) -> Unit = {},
        platformWindowOverride: PlatformWindow? = null,
        showDanmakuEditor: () -> Boolean = { true },
        onEditorEscape: (() -> Unit)? = null,
        expanded: Boolean = true,
        isFullscreen: Boolean = expanded,
        framePreview: MediaProgressFramePreviewState? = null,
        cacheChunkState: ChunkState = ChunkState.NONE,
    ) {
        ProvideCompositionLocalsForPreview(darkMode = DarkMode.DARK) {
            val platformWindow = platformWindowOverride ?: LocalPlatformWindow.current
            val actualWatchTogetherPlayerController = watchTogetherPlayerController
                ?: remember { WatchTogetherPlayerController() }
            CompositionLocalProvider(
                LocalPlatformWindow provides platformWindow,
                LocalWatchTogetherPlayerController provides actualWatchTogetherPlayerController,
            ) {
                onPlatformWindow(platformWindow)
                val scope = rememberCoroutineScope()
                val playerState = remember {
                    TestMediampPlayer(scope.coroutineContext).also(onPlayerStateCreated)
                }
                // The v2 TestMediampPlayer starts with no media (MediaStatus.Idle), where seek and
                // play/pause commands are no-ops and mediaProperties is null (which disables
                // swipe-to-seek). Load the default 100s fake media paused, matching the v1 test
                // player's always-loaded baseline. The machine runs on this composition's
                // dispatcher, so the load completes during the next idle sync.
                LaunchedEffect(playerState) {
                    playerState.setMediaData(UriMediaData("file:///test.mp4"))
                }
                BackHandler(enabled = isFullscreen, onExitFullscreen)
                val cacheProgressInfoFlow = staticMediaCacheProgressState(cacheChunkState).flow
                EpisodeVideoImpl(
                    playerState = playerState,
                    expanded = expanded,
                    hasNextEpisode = true,
                    onClickNextEpisode = {},
                    playerControllerState = playerControllerState,
                    opEdSkipDuration = opEdSkipDuration,
                    title = { PlayerTopBar() },
                    danmakuHost = {},
                    danmakuEnabled = false,
                    onToggleDanmaku = onToggleDanmaku,
                    videoLoadingStateFlow = remember { MutableStateFlow(VideoLoadingState.Succeed(isBt = true)) },
                    onClickFullScreen = onClickFullScreen,
                    onExitFullscreen = onExitFullscreen,
                    danmakuEditor = {
                        if (showDanmakuEditor()) {
                            PlayerDanmakuEditor(
                                text = "",
                                onTextChange = {},
                                isSending = { false },
                                onSend = {},
                                danmakuTextPlaceholder = "",
                                playerState = playerState,
                                videoScaffoldConfig = VideoScaffoldConfig.Default,
                                playerControllerState = playerControllerState,
                                modifier = Modifier.testTag(TAG_DANMAKU_EDITOR),
                                onEscape = onEditorEscape,
                            )
                        }
                    },
                    onClickScreenshot = {},
                    detachedProgressSlider = {
                        PlayerControllerDefaults.MediaProgressSlider(
                            progressSliderState,
                            cacheProgressInfoFlow = cacheProgressInfoFlow,
                            Modifier.testTag(TAG_DETACHED_PROGRESS_SLIDER),
                            enabled = false,
                            framePreview = framePreview,
                            showFramePreviewInPopup = expanded,
                        )
                    },
                    sidebarVisible = true,
                    onToggleSidebar = {},
                    progressSliderState = progressSliderState,
                    cacheProgressInfoFlow = cacheProgressInfoFlow,
                    framePreview = framePreview,
                    audioController = audioController,
                    brightnessController = NoOpLevelController,
                    playbackSpeedControllerState = remember(playbackSpeed) {
                        PlaybackSpeedControllerState(
                            playbackSpeed = playbackSpeed,
                            onCommitSpeed = onCommitPlaybackSpeed,
                            scope = scope,
                        )
                    },
                    videoAspectRatioControllerState = remember {
                        VideoAspectRatioControllerState(NoOpVideoAspectRatio, scope)
                    },
                    leftBottomTips = {},
                    fullscreenSwitchButton = {
                        EpisodeVideoDefaults.FloatingFullscreenSwitchButton(
                            FullscreenSwitchMode.ONLY_IN_CONTROLLER,
                            isFullscreen = isFullscreen,
                            onClickFullScreen = if (!isFullscreen) onClickFullScreen else onExitFullscreen,
                        )
                    },
                    sideSheets = { sheetsController ->
                        EpisodeVideoDefaults.SideSheets(
                            sheetsController,
                            playerControllerState,
                            playerSettingsPage = {
                                EpisodeVideoSideSheets.DanmakuSettingsSheet(
                                    danmakuConfig = DanmakuConfig.Default,
                                    setDanmakuConfig = {},
                                    enableRegexFilter = true,
                                    onNavigateToFilterSettings = {
                                        sheetsController.navigateTo(EpisodeVideoSideSheetPage.EDIT_DANMAKU_REGEX_FILTER)
                                    },
                                    switchDanmakuRegexFilterCompletely = {},
                                    onDismissRequest = { goBack() },
                                    Modifier.testTag(TAG_DANMAKU_SETTINGS_SHEET),
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
                                val (fetchRequest, onFetchRequestChange) = rememberSaveable {
                                    mutableStateOf(
                                        TestMediaFetchRequest,
                                    )
                                }
                                EpisodeVideoSideSheets.MediaSelectorSheet(
                                    mediaSelectorState = rememberTestMediaSelectorState(),
                                    mediaSourceResultListPresentation = TestMediaSourceResultListPresentation,
                                    viewKind = viewKind,
                                    onViewKindChange = onViewKindChange,
                                    fetchRequest = fetchRequest,
                                    onFetchRequestChange = onFetchRequestChange,
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
                    gestureFamily = gestureFamily ?: gestureFamilyOf(
                        LocalActiveInputSource.current.current,
                        LocalPlatform.current.mouseFamily,
                    ),
                    shareData = MediaShareData(null, null),
                    onClickCache = {},
                    isFullscreen = isFullscreen,
                    modifier = Modifier.testTag("PLAYER"),
                )
            }
        }
    }

    private class TestLevelController(
        initialLevel: Float,
        override val levelStep: Float = 0.01f,
    ) : LevelController {
        override var level: Float = initialLevel
            private set

        override val range: ClosedRange<Float> = 0f..1f

        override fun setLevel(level: Float) {
            this.level = level.coerceIn(range.start, range.endInclusive)
        }
    }

    @OptIn(InternalForInheritanceMediampApi::class)
    private class TestPlaybackSpeed(initialSpeed: Float) : PlaybackSpeed {
        private val state = MutableStateFlow(initialSpeed)

        override val valueFlow = state
        override val value: Float
            get() = state.value

        override fun set(speed: Float) {
            state.value = speed
        }
    }

    @Test
    fun `forward opening button follows configured duration`() = runAniComposeUiTest {
        lateinit var playerState: TestMediampPlayer
        var opEdSkipDuration by mutableStateOf(85.seconds)
        val visibleControllerState = PlayerControllerState(NORMAL_VISIBLE)
        setContent {
            Player(
                GestureFamily.MOUSE,
                playerControllerState = visibleControllerState,
                opEdSkipDuration = opEdSkipDuration,
                onPlayerStateCreated = { playerState = it },
            )
        }

        runOnIdle {
            assertEquals(MediaStatus.Ready, playerState.state.value.mediaStatus)
        }

        for (durationSeconds in listOf(80, 85, 90)) {
            runOnIdle {
                opEdSkipDuration = durationSeconds.seconds
                playerState.injectPosition(5_000L)
            }
            waitForIdle() // let the state machine process the injected position

            // The click triggers playerState.skip; commands must run on the machine's
            // dispatcher thread (the compose UI thread), so dispatch the input from it.
            runOnIdle {
                onNodeWithContentDescription("Fast forward $durationSeconds seconds").performClick()
            }

            runOnIdle {
                assertEquals((durationSeconds + 5) * 1_000L, playerState.currentPositionMillis.value)
            }
        }
    }

    @Test
    fun `player menu shows watch together first and dispatches click`() = runAniComposeUiTest {
        var watchTogetherClicks = 0
        val visibleControllerState = PlayerControllerState(NORMAL_VISIBLE)
        val watchTogetherPlayerController = WatchTogetherPlayerController { watchTogetherClicks++ }
        setContent {
            Player(
                GestureFamily.MOUSE,
                playerControllerState = visibleControllerState,
                watchTogetherPlayerController = watchTogetherPlayerController,
            )
        }

        onNodeWithContentDescription("More options").performClick()

        val watchTogetherItem = onNodeWithTag(TAG_WATCH_TOGETHER_MENU_ITEM)
        val playerStatsItem = onNodeWithText("Show Playback Info")
        waitUntil(timeoutMillis = WAIT_TIMEOUT) {
            watchTogetherItem.exists() && playerStatsItem.exists()
        }
        val watchTogetherTop = watchTogetherItem.fetchSemanticsNode().boundsInRoot.top
        val playerStatsTop = playerStatsItem.fetchSemanticsNode().boundsInRoot.top
        assertTrue(watchTogetherTop < playerStatsTop)

        watchTogetherItem.performClick()

        runOnIdle {
            assertEquals(1, watchTogetherClicks)
        }
        watchTogetherItem.doesNotExist()
    }

    @Test
    fun `watch together popup follows controller while video fills page`() = runAniComposeUiTest {
        val visibleControllerState = PlayerControllerState(NORMAL_VISIBLE)
        val watchTogetherPlayerController = WatchTogetherPlayerController()
        var isFullscreen by mutableStateOf(true)
        var isExpandedLayout by mutableStateOf(false)
        var sidebarVisible by mutableStateOf(true)
        mainClock.autoAdvance = false
        setContent {
            CompositionLocalProvider(LocalWatchTogetherPlayerController provides watchTogetherPlayerController) {
                WatchTogetherPopupVisibilityEffect(
                    playerControllerState = visibleControllerState,
                    isFullscreen = isFullscreen,
                    isExpandedLayout = isExpandedLayout,
                    sidebarVisible = sidebarVisible,
                )
            }
        }

        runOnIdle {
            assertTrue(watchTogetherPlayerController.isDraggablePopupVisible)
            visibleControllerState.toggleFullVisible(false)
        }
        waitUntil(timeoutMillis = WAIT_TIMEOUT) {
            !watchTogetherPlayerController.isDraggablePopupVisible
        }

        runOnIdle {
            visibleControllerState.toggleFullVisible(true)
        }
        waitUntil(timeoutMillis = WAIT_TIMEOUT) {
            watchTogetherPlayerController.isDraggablePopupVisible
        }

        runOnIdle {
            visibleControllerState.toggleFullVisible(false)
        }
        waitUntil(timeoutMillis = WAIT_TIMEOUT) {
            !watchTogetherPlayerController.isDraggablePopupVisible
        }

        runOnIdle {
            isFullscreen = false
        }
        waitUntil(timeoutMillis = WAIT_TIMEOUT) {
            watchTogetherPlayerController.isDraggablePopupVisible
        }

        runOnIdle {
            isExpandedLayout = true
            sidebarVisible = false
        }
        waitUntil(timeoutMillis = WAIT_TIMEOUT) {
            !watchTogetherPlayerController.isDraggablePopupVisible
        }

        runOnIdle {
            sidebarVisible = true
        }
        waitUntil(timeoutMillis = WAIT_TIMEOUT) {
            watchTogetherPlayerController.isDraggablePopupVisible
        }
    }

    /**
     * @see GestureFamily.clickToToggleController
     */
    @Test
    fun `touch - clickToToggleController - show`() = runAniComposeUiTest {
        setContent {
            Player(GestureFamily.TOUCH)
        }
        runOnIdle {
            assertEquals(
                NORMAL_INVISIBLE,
                controllerState.visibility,
            )
        }

        mainClock.autoAdvance = false
        onRoot().performTouchInput { click() }
        runOnIdle {
            mainClock.advanceTimeBy(1000L)
            waitUntil(timeoutMillis = WAIT_TIMEOUT) { topBar.exists() }
            assertEquals(
                NORMAL_VISIBLE,
                controllerState.visibility,
            )
        }
    }

    /**
     * @see GestureFamily.clickToToggleController
     */
    @Test
    fun `touch - clickToToggleController - hide`() = runAniComposeUiTest {
        setContent {
            Player(GestureFamily.TOUCH)
        }
        runOnIdle {
            assertEquals(NORMAL_INVISIBLE, controllerState.visibility)
        }

        val root = onAllNodes(isRoot()).onFirst()
        mainClock.autoAdvance = false
        root.performTouchInput { click() }
        runOnIdle {
            mainClock.advanceTimeBy(1000L)
            waitUntil(timeoutMillis = WAIT_TIMEOUT) { topBar.exists() }
            assertEquals(NORMAL_VISIBLE, controllerState.visibility)
        }

        root.performTouchInput { click() }
        runOnIdle {
            mainClock.advanceTimeUntil(timeoutMillis = WAIT_TIMEOUT) { topBar.doesNotExist() }
            waitUntil(timeoutMillis = WAIT_TIMEOUT) { topBar.doesNotExist() }
            assertEquals(NORMAL_INVISIBLE, controllerState.visibility)
        }
    }

    /**
     * 混合输入设备 (带触屏的 Windows 二合一、接了鼠标的 Android 平板) 上, 点击语义必须按本次事件的
     * 指针类型解析, 而不是按当前的 [GestureFamily].
     *
     * 这里刻意把 family 固定成 [GestureFamily.MOUSE], 相当于用户一路用鼠标走到播放页;
     * 随后的第一次触摸就必须立刻是触摸语义 (显隐控制器), 而不是鼠标语义 (暂停/恢复) ——
     * 不允许出现「先点一次预热、第二次才生效」.
     *
     * @see tapGestureFamilyOf
     */
    @Test
    fun `hybrid - first touch after mouse uses touch semantics immediately`() = runAniComposeUiTest {
        lateinit var playerState: TestMediampPlayer
        setContent {
            Player(GestureFamily.MOUSE, onPlayerStateCreated = { playerState = it })
        }
        runOnIdle {
            // Media is loaded paused by Player (the v1 test's PAUSED baseline).
            assertEquals(MediaStatus.Ready, playerState.state.value.mediaStatus)
            assertFalse(playerState.state.value.playWhenReady)
            assertEquals(NORMAL_INVISIBLE, controllerState.visibility)
        }

        val root = onAllNodes(isRoot()).onFirst()
        mainClock.autoAdvance = false

        // 鼠标点击: 鼠标语义 = 播放/暂停, 控制器不显示
        // (点击会触发播放器命令, 必须在机器线程 (UI 线程) 上派发)
        runOnUiThread {
            root.performMouseInput { click() }
        }
        mainClock.advanceTimeBy(1000L)
        runOnIdle {
            assertTrue(playerState.state.value.playWhenReady)
            assertEquals(NORMAL_INVISIBLE, controllerState.visibility)
        }

        // 紧接着的第一次触摸: 必须立刻显示控制器, 且不能再切换播放状态
        runOnUiThread {
            root.performTouchInput { click() }
        }
        mainClock.advanceTimeBy(1000L)
        runOnIdle {
            waitUntil(timeoutMillis = WAIT_TIMEOUT) { topBar.exists() }
            assertEquals(NORMAL_VISIBLE, controllerState.visibility)
            assertTrue(playerState.state.value.playWhenReady)
        }
    }

    /**
     * 鼠标按下到抬起之间必然会有位移 (手抖、触控板按压), 这一下必须仍然是一次点击.
     *
     * 滑动手势不能靠消费位移来做指针类型过滤: 消费会让 combinedClickable 的点击判定被取消,
     * 桌面上就成了「按下动一像素就点不动播放器」. 过滤只能由 family 在组合期门控挂载.
     */
    @Test
    fun `mouse - click with pointer drift still toggles playback`() = runAniComposeUiTest {
        lateinit var playerState: TestMediampPlayer
        setContent {
            Player(GestureFamily.MOUSE, onPlayerStateCreated = { playerState = it })
        }
        runOnIdle {
            assertEquals(MediaStatus.Ready, playerState.state.value.mediaStatus)
            assertFalse(playerState.state.value.playWhenReady)
        }

        val root = onAllNodes(isRoot()).onFirst()
        runOnUiThread {
            root.performMouseInput {
                moveTo(center)
                press()
                moveTo(center + Offset(1f, 0f))
                release()
            }
        }
        waitForIdle()
        runOnIdle {
            assertTrue(playerState.state.value.playWhenReady)
        }
    }

    /**
     * 混合设备从鼠标切到手指后, 滑动 seek 从第二次手势起生效.
     *
     * 第一次触摸只能把 family 切过来: 触摸没有 hover, 类型要到 down 才知道, 而 Compose 在 down
     * 时就为这个 pointer 固定了命中路径, 随后才挂载的 draggable 不在路径里, 收不到后续 Move.
     * 这是门控方案的既定代价 —— 换成在事件里消费位移来过滤, 代价会变成鼠标按下漂移就点不动播放器.
     */
    @Test
    fun `hybrid - touch drag after mouse takes effect from the second gesture`() = runAniComposeUiTest {
        setContent {
            Player(gestureFamily = null)
        }
        waitForIdle()

        val root = onAllNodes(isRoot()).onFirst()

        // 鼠标走一遍, family 落到 MOUSE (MOUSE 语义下点击会切换播放, 命令必须在 UI 线程派发)
        runOnUiThread {
            root.performMouseInput {
                moveTo(center)
                click()
                exit()
            }
        }
        runOnIdle {
            detachedProgressSlider.assertDoesNotExist()
        }

        // 第一次触摸: 只把 family 切到 TOUCH, 拖不出 seek.
        // 位移没有拖动手势接管, 于是被判成一次点击 —— 会切换播放状态, 命令必须在 UI 线程派发.
        runOnUiThread {
            root.performTouchInput {
                down(centerLeft)
                moveBy(Offset(width / 2f, 0f))
                up()
            }
        }
        runOnIdle {
            assertEquals(false, progressSliderState.isPreviewing)
        }

        // 第二次触摸: draggable 已挂载, 正常进入 seek
        root.performTouchInput {
            down(centerLeft)
            moveBy(Offset(width / 2f, 0f))
        }
        runOnIdle {
            assertEquals(true, progressSliderState.isPreviewing)
            waitUntil(timeoutMillis = WAIT_TIMEOUT) { detachedProgressSlider.exists() }
            assertEquals(PREVIEW_DETACHED_SLIDER, controllerState.visibility)
        }

        // 松开手指 (释放会触发 playerState.skip, 播放器命令必须在机器线程 (UI 线程) 上调用)
        runOnUiThread {
            root.performTouchInput { up() }
        }
    }

    @Test
    fun `hybrid - first mouse move after touch shows controller immediately`() = runAniComposeUiTest {
        setContent {
            Player(GestureFamily.TOUCH)
        }
        waitForIdle()

        val root = onAllNodes(isRoot()).onFirst()
        root.performTouchInput { click() }
        root.performTouchInput { click() }
        runOnIdle {
            assertEquals(NORMAL_INVISIBLE, controllerState.visibility)
        }

        root.slightlyMoveFromCenterToRight()
        runOnIdle {
            waitUntil(timeoutMillis = WAIT_TIMEOUT) { topBar.exists() }
            assertEquals(NORMAL_VISIBLE, controllerState.visibility)
        }
    }

    /**
     * @see swipeToFullscreen
     */
    @Test
    fun `touch - swipeMidForFullscreen - swipe up enters and swipe down exits`() = runAniComposeUiTest {
        var fullscreenCount = 0
        var exitFullscreenCount = 0
        setContent {
            var fullscreen by remember { mutableStateOf(false) }
            Player(
                GestureFamily.TOUCH,
                onClickFullScreen = {
                    fullscreen = true
                    fullscreenCount++
                },
                onExitFullscreen = {
                    fullscreen = false
                    exitFullscreenCount++
                },
                isFullscreen = fullscreen,
            )
        }
        waitForIdle()

        // 未在全屏时, 在中间区域向下滑动：无效果
        videoGestureHost.performTouchInput {
            swipe(start = Offset(centerX, centerY - 200f), end = Offset(centerX, centerY + 200f))
        }

        runOnIdle {
            assertEquals(0, fullscreenCount)
            assertEquals(0, exitFullscreenCount)
        }

        // 未在全屏时, 在中间区域向上滑动: 进入全屏
        videoGestureHost.performTouchInput {
            swipe(start = Offset(centerX, centerY + 200f), end = Offset(centerX, centerY - 200f))
        }
        runOnIdle {
            assertEquals(1, fullscreenCount)
            assertEquals(0, exitFullscreenCount)
        }

        // 全屏时, 在中间区域向上滑动: 无效果
        videoGestureHost.performTouchInput {
            swipe(start = Offset(centerX, centerY + 200f), end = Offset(centerX, centerY - 200f))
        }
        runOnIdle {
            assertEquals(1, fullscreenCount)
            assertEquals(0, exitFullscreenCount)
        }

        // 全屏时，向下滑动，退出全屏
        videoGestureHost.performTouchInput {
            swipe(start = Offset(centerX, centerY - 200f), end = Offset(centerX, centerY + 200f))
        }
        runOnIdle {
            assertEquals(1, fullscreenCount)
            assertEquals(1, exitFullscreenCount)
        }

        // 退出全屏后向下滑动，无效果
        videoGestureHost.performTouchInput {
            swipe(start = Offset(centerX, centerY - 200f), end = Offset(centerX, centerY + 200f))
        }
        runOnIdle {
            assertEquals(1, fullscreenCount)
            assertEquals(1, exitFullscreenCount)
        }
    }

    @Test
    fun `touch - keyboard shortcuts - playback fullscreen danmaku seek volume and speed`() = runAniComposeUiTest {
        lateinit var playerState: TestMediampPlayer
        lateinit var playbackSpeed: TestPlaybackSpeed
        val committedPlaybackSpeeds = mutableListOf<Float>()
        val audioController = TestLevelController(0.5f, levelStep = 0.04f)
        var fullscreenCount = 0
        var exitFullscreenCount = 0
        var toggleDanmakuCount = 0
        setContent {
            CompositionLocalProvider(LocalPlatform provides Platform.Android(Arch.ARMV8A)) {
                val scope = rememberCoroutineScope()
                var fullscreen by remember { mutableStateOf(false) }
                playbackSpeed = remember { TestPlaybackSpeed(1f) }
                Player(
                    GestureFamily.TOUCH,
                    onClickFullScreen = {
                        fullscreenCount++
                        fullscreen = true
                    },
                    onExitFullscreen = {
                        exitFullscreenCount++
                        fullscreen = false
                    },
                    onToggleDanmaku = { toggleDanmakuCount++ },
                    audioController = audioController,
                    playbackSpeed = playbackSpeed,
                    onCommitPlaybackSpeed = { committedPlaybackSpeeds.add(it) },
                    onPlayerStateCreated = { playerState = it },
                    isFullscreen = fullscreen,
                )
            }
        }
        waitForIdle()
        runOnIdle {
            // Media is loaded paused by Player; drive the fake playback clock to 20s.
            assertEquals(MediaStatus.Ready, playerState.state.value.mediaStatus)
            playerState.injectPosition(20_000L)
        }
        waitForIdle() // let the state machine process the injected position

        videoGestureHost.performKeyInput {
            pressKey(Key.F)
            pressKey(Key.Escape)
            pressKey(Key.F)
            pressKey(Key.B)
        }
        waitForIdle()
        runOnIdle {
            assertEquals(1, fullscreenCount)
            assertEquals(1, exitFullscreenCount)
            assertEquals(1, toggleDanmakuCount)
        }

        // Space/arrow keys trigger player commands, which must run on the machine's
        // dispatcher thread (the compose UI thread), so dispatch these inputs from it.
        runOnIdle {
            videoGestureHost.performKeyInput {
                pressKey(Key.Spacebar)
            }
        }
        waitForIdle()
        runOnIdle {
            assertTrue(playerState.state.value.playWhenReady)
        }

        runOnIdle {
            videoGestureHost.performKeyInput {
                pressKey(Key.DirectionRight)
            }
        }
        waitForIdle()
        runOnIdle {
            assertEquals(25_000L, playerState.currentPositionMillis.value)
        }

        runOnIdle {
            videoGestureHost.performKeyInput {
                pressKey(Key.DirectionLeft)
            }
        }
        waitForIdle()
        runOnIdle {
            assertEquals(20_000L, playerState.currentPositionMillis.value)
        }

        videoGestureHost.performKeyInput {
            pressKey(Key.DirectionUp)
        }
        waitForIdle()
        runOnIdle {
            assertEquals(0.6f, audioController.level)
        }

        videoGestureHost.performKeyInput {
            pressKey(Key.DirectionDown)
        }
        waitForIdle()
        runOnIdle {
            assertEquals(0.5f, audioController.level)
        }

        videoGestureHost.performKeyInput {
            keyDown(Key.ShiftLeft)
            pressKey(Key.DirectionUp)
            keyUp(Key.ShiftLeft)
        }
        waitForIdle()
        runOnIdle {
            assertEquals(0.54f, audioController.level)
        }

        videoGestureHost.performKeyInput {
            keyDown(Key.ShiftLeft)
            pressKey(Key.DirectionDown)
            keyUp(Key.ShiftLeft)
        }
        waitForIdle()
        runOnIdle {
            assertEquals(0.5f, audioController.level)
        }

        videoGestureHost.performKeyInput {
            pressKey(Key.A)
        }
        waitForIdle()
        runOnIdle {
            assertEquals(0.75f, playbackSpeed.value)
        }

        videoGestureHost.performKeyInput {
            pressKey(Key.D)
        }
        waitForIdle()
        runOnIdle {
            assertEquals(1f, playbackSpeed.value)
        }

        videoGestureHost.performKeyInput {
            pressKey(Key.S)
        }
        waitForIdle()
        runOnIdle {
            assertEquals(1f, playbackSpeed.value)
            assertEquals(listOf(0.75f, 1f, 1f), committedPlaybackSpeeds)
        }
    }

    @Test
    fun `touch - keyboard shortcuts - digit keys jump to speed presets`() = runAniComposeUiTest {
        lateinit var playbackSpeed: TestPlaybackSpeed
        val committedPlaybackSpeeds = mutableListOf<Float>()
        setContent {
            CompositionLocalProvider(LocalPlatform provides Platform.Android(Arch.ARMV8A)) {
                playbackSpeed = remember { TestPlaybackSpeed(1f) }
                Player(
                    GestureFamily.TOUCH,
                    playbackSpeed = playbackSpeed,
                    onCommitPlaybackSpeed = { committedPlaybackSpeeds.add(it) },
                )
            }
        }
        waitForIdle()

        videoGestureHost.performKeyInput {
            pressKey(Key.Two)
        }
        waitForIdle()
        runOnIdle {
            assertEquals(2f, playbackSpeed.value)
        }

        // 3x 超出默认范围 0.5x..2.5x, clamp 到上界而不是不响应
        videoGestureHost.performKeyInput {
            pressKey(Key.Three)
        }
        waitForIdle()
        runOnIdle {
            assertEquals(2.5f, playbackSpeed.value)
        }

        videoGestureHost.performKeyInput {
            pressKey(Key.NumPad1)
        }
        waitForIdle()
        runOnIdle {
            assertEquals(1f, playbackSpeed.value)
            assertEquals(listOf(2f, 2.5f, 1f), committedPlaybackSpeeds)
        }
    }

    @Test
    fun `touch - keyboard shortcuts - yield focus to editor and reclaim it from video click`() = runAniComposeUiTest {
        lateinit var playerState: TestMediampPlayer
        var toggleDanmakuCount = 0
        val visibleControllerState = PlayerControllerState(NORMAL_VISIBLE)
        setContent {
            CompositionLocalProvider(LocalPlatform provides Platform.Android(Arch.ARMV8A)) {
                Player(
                    GestureFamily.TOUCH,
                    playerControllerState = visibleControllerState,
                    onToggleDanmaku = { toggleDanmakuCount++ },
                    onPlayerStateCreated = { playerState = it },
                )
            }
        }
        waitForIdle()
        runOnIdle {
            // Media is loaded paused by Player (the v1 test's PAUSED baseline).
            assertEquals(MediaStatus.Ready, playerState.state.value.mediaStatus)
            assertFalse(playerState.state.value.playWhenReady)
        }

        videoGestureHost.assertIsFocused()
        danmakuEditor.performClick()
        danmakuEditor.onChild().assertIsFocused()
        danmakuEditor.performKeyInput {
            pressKey(Key.B)
            pressKey(Key.Spacebar)
        }
        waitForIdle()
        runOnIdle {
            assertEquals(0, toggleDanmakuCount)
            assertFalse(playerState.state.value.playWhenReady)
        }

        // 必须是触摸点击: 这里只是把焦点从编辑器夺回来, 而 desktop 的 performClick() 是鼠标点击,
        // 鼠标语义下会顺带切换播放状态, 后面对 Spacebar 的断言就反了.
        videoGestureHost.performTouchInput { click() }
        // combinedClickable 带 onDoubleClick, onClick 要等双击判定窗口过后才发;
        // CMP 1.11 起桌面端触摸输入会进入 Touch input mode, 不再有按下即抢焦点的捷径.
        mainClock.advanceTimeBy(1000L)
        videoGestureHost.assertIsFocused()
        // Spacebar triggers a player command; dispatch from the machine's (UI) thread.
        runOnIdle {
            videoGestureHost.performKeyInput {
                pressKey(Key.B)
                pressKey(Key.Spacebar)
            }
        }
        waitForIdle()
        runOnIdle {
            assertEquals(1, toggleDanmakuCount)
            assertTrue(playerState.state.value.playWhenReady)
        }
    }

    @Test
    fun `touch - keyboard shortcuts - do not reclaim focus when tab leaves editor`() = runAniComposeUiTest {
        lateinit var playerState: TestMediampPlayer
        var toggleDanmakuCount = 0
        val visibleControllerState = PlayerControllerState(NORMAL_VISIBLE)
        setContent {
            CompositionLocalProvider(LocalPlatform provides Platform.Android(Arch.ARMV8A)) {
                Player(
                    GestureFamily.TOUCH,
                    playerControllerState = visibleControllerState,
                    onToggleDanmaku = { toggleDanmakuCount++ },
                    onPlayerStateCreated = { playerState = it },
                )
            }
        }
        waitForIdle()
        runOnIdle {
            // Media is loaded paused by Player (the v1 test's PAUSED baseline).
            assertEquals(MediaStatus.Ready, playerState.state.value.mediaStatus)
            assertFalse(playerState.state.value.playWhenReady)
        }

        danmakuEditor.performClick()
        danmakuEditor.onChild().assertIsFocused()
        danmakuEditor.performKeyInput {
            pressKey(Key.Tab)
        }
        waitForIdle()

        videoGestureHost.assertIsNotFocused()
        onRoot().performKeyInput {
            pressKey(Key.B)
            pressKey(Key.Spacebar)
        }
        waitForIdle()
        runOnIdle {
            assertEquals(0, toggleDanmakuCount)
            assertFalse(playerState.state.value.playWhenReady)
        }
    }

    @Test
    fun `touch - back navigation input - escape dismisses detached editor before exiting fullscreen`() =
        runAniComposeUiTest {
            var exitFullscreenCount = 0
            var editorEscapeCount = 0
            var showDanmakuEditor by mutableStateOf(true)
            val visibleControllerState = PlayerControllerState(NORMAL_VISIBLE)
            setContent {
                CompositionLocalProvider(LocalPlatform provides Platform.Android(Arch.ARMV8A)) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .onBackNavigationInput { exitFullscreenCount++ },
                    ) {
                        Player(
                            GestureFamily.TOUCH,
                            playerControllerState = visibleControllerState,
                            onExitFullscreen = { exitFullscreenCount++ },
                            showDanmakuEditor = { showDanmakuEditor },
                            onEditorEscape = {
                                editorEscapeCount++
                                showDanmakuEditor = false
                            },
                        )
                    }
                }
            }
            waitForIdle()

            danmakuEditor.performClick()
            danmakuEditor.onChild().assertIsFocused()
            danmakuEditor.performKeyInput {
                pressKey(Key.Escape)
            }
            waitForIdle()

            videoGestureHost.assertIsFocused()
            danmakuEditor.doesNotExist()
            runOnIdle {
                assertEquals(1, editorEscapeCount)
                assertEquals(0, exitFullscreenCount)
            }

            videoGestureHost.performKeyInput {
                pressKey(Key.Escape)
            }
            waitForIdle()
            runOnIdle {
                assertEquals(1, exitFullscreenCount)
            }
        }

    @Test
    fun `mouse - keyboard shortcuts - reclaim focus from editor on mouse move`() = runAniComposeUiTest {
        val visibleControllerState = PlayerControllerState(NORMAL_VISIBLE)
        setContent {
            Player(
                GestureFamily.MOUSE,
                playerControllerState = visibleControllerState,
            )
        }
        waitForIdle()

        videoGestureHost.assertIsFocused()
        danmakuEditor.performClick()
        danmakuEditor.onChild().assertIsFocused()

        videoGestureHost.slightlyMoveFromCenterToRight()
        waitForIdle()
        videoGestureHost.assertIsFocused()
    }

    @Test
    fun `mouse - keyboard shortcuts - reclaim focus when editor closes`() = runAniComposeUiTest {
        lateinit var playerState: TestMediampPlayer
        var showDanmakuEditor by mutableStateOf(true)
        val visibleControllerState = PlayerControllerState(NORMAL_VISIBLE)
        setContent {
            Player(
                GestureFamily.MOUSE,
                playerControllerState = visibleControllerState,
                onPlayerStateCreated = { playerState = it },
                showDanmakuEditor = { showDanmakuEditor },
            )
        }
        waitForIdle()
        runOnIdle {
            // Media is loaded paused by Player (the v1 test's PAUSED baseline).
            assertEquals(MediaStatus.Ready, playerState.state.value.mediaStatus)
            assertFalse(playerState.state.value.playWhenReady)
        }

        danmakuEditor.performClick()
        danmakuEditor.onChild().assertIsFocused()
        runOnIdle {
            showDanmakuEditor = false
        }
        waitForIdle()

        videoGestureHost.assertIsFocused()
        // Spacebar triggers a player command; dispatch from the machine's (UI) thread.
        runOnIdle {
            videoGestureHost.performKeyInput {
                pressKey(Key.Spacebar)
            }
        }
        waitForIdle()
        runOnIdle {
            assertTrue(playerState.state.value.playWhenReady)
        }
    }

    @Test
    fun `mouse - keyboard shortcuts - reclaim focus after fullscreen button click on mouse move`() =
        runAniComposeUiTest {
            var fullscreenCount = 0
            val visibleControllerState = PlayerControllerState(NORMAL_VISIBLE)
            setContent {
                Player(
                    GestureFamily.MOUSE,
                    playerControllerState = visibleControllerState,
                    onClickFullScreen = { fullscreenCount++ },
                )
            }
            waitForIdle()

            videoGestureHost.assertIsFocused()
            fullScreenButton.performClick()
            waitForIdle()
            runOnIdle {
                assertEquals(1, fullscreenCount)
            }

            videoGestureHost.slightlyMoveFromCenterToRight()
            waitForIdle()
            videoGestureHost.assertIsFocused()
        }

    @Test
    fun `mouse - keyboard shortcuts - enter does not activate click gesture`() = runAniComposeUiTest {
        lateinit var playerState: TestMediampPlayer
        val visibleControllerState = PlayerControllerState(NORMAL_VISIBLE)
        setContent {
            Player(
                GestureFamily.MOUSE,
                playerControllerState = visibleControllerState,
                onPlayerStateCreated = { playerState = it },
            )
        }
        waitForIdle()
        runOnIdle {
            // Media is loaded paused by Player (the v1 test's PAUSED baseline).
            assertEquals(MediaStatus.Ready, playerState.state.value.mediaStatus)
            assertFalse(playerState.state.value.playWhenReady)
        }

        videoGestureHost.assertIsFocused()
        videoGestureHost.performKeyInput {
            pressKey(Key.Enter)
            pressKey(Key.NumPadEnter)
        }
        waitForIdle()
        runOnIdle {
            // Enter must not activate the click gesture (which would toggle play in MOUSE mode).
            assertFalse(playerState.state.value.playWhenReady)
        }
    }

    @Test
    fun `mouse - keyboard shortcuts - I toggles playback info and Tab does not`() = runAniComposeUiTest {
        val visibleControllerState = PlayerControllerState(NORMAL_VISIBLE)
        setContent {
            Player(
                GestureFamily.MOUSE,
                playerControllerState = visibleControllerState,
            )
        }
        waitForIdle()

        videoGestureHost.assertIsFocused()
        videoGestureHost.performKeyInput {
            pressKey(Key.Tab)
        }
        waitForIdle()
        onNodeWithText("Playback Info", substring = true).doesNotExist()

        videoGestureHost.performMouseInput { click() }
        videoGestureHost.performKeyInput {
            pressKey(Key.I)
        }
        waitUntil(timeoutMillis = WAIT_TIMEOUT) {
            onNodeWithText("Playback Info", substring = true).exists()
        }

        videoGestureHost.performKeyInput {
            pressKey(Key.I)
        }
        waitUntil(timeoutMillis = WAIT_TIMEOUT) {
            !onNodeWithText("Playback Info", substring = true).exists()
        }
    }

    @Test
    fun `touch - keyboard shortcuts - reclaim focus from editor on mouse move`() = runAniComposeUiTest {
        val visibleControllerState = PlayerControllerState(NORMAL_VISIBLE)
        setContent {
            CompositionLocalProvider(LocalPlatform provides Platform.Android(Arch.ARMV8A)) {
                Player(
                    GestureFamily.TOUCH,
                    playerControllerState = visibleControllerState,
                )
            }
        }
        waitForIdle()

        videoGestureHost.assertIsFocused()
        danmakuEditor.performClick()
        danmakuEditor.onChild().assertIsFocused()

        videoGestureHost.slightlyMoveFromCenterToRight()
        waitForIdle()
        videoGestureHost.assertIsFocused()
    }

    @Test
    fun `mouse - keyboard shortcuts - reclaim focus when fullscreen changes`() = runAniComposeUiTest {
        val visibleControllerState = PlayerControllerState(NORMAL_VISIBLE)
        var currentFullscreen = false
        setContent {
            var fullscreen by remember { mutableStateOf(currentFullscreen) }
            Player(
                GestureFamily.MOUSE,
                playerControllerState = visibleControllerState,
                onClickFullScreen = {
                    fullscreen = true
                    currentFullscreen = true
                },
                onExitFullscreen = {
                    fullscreen = false
                    currentFullscreen = false
                },
                isFullscreen = fullscreen,
            )
        }
        waitForIdle()

        fullScreenButton.performClick()
        waitForIdle()
        assertTrue(currentFullscreen)
        videoGestureHost.assertIsFocused()

        fullScreenButton.performClick()
        waitForIdle()
        assertFalse(currentFullscreen)
        videoGestureHost.assertIsFocused()
    }

    @Test
    fun `mouse - keyboard shortcuts - lost editor focus when fullscreen changes`() = runAniComposeUiTest {
        val visibleControllerState = PlayerControllerState(NORMAL_VISIBLE)
        setContent {
            var fullscreen by remember { mutableStateOf(false) }
            Player(
                GestureFamily.MOUSE,
                playerControllerState = visibleControllerState,
                onClickFullScreen = { fullscreen = true },
                onExitFullscreen = { fullscreen = false },
                isFullscreen = fullscreen,
            )
        }
        waitForIdle()

        danmakuEditor.performClick()
        danmakuEditor.onChild().assertIsFocused()

        fullScreenButton.performClick()
        waitForIdle()
        danmakuEditor.onChild().assertIsNotFocused()

        fullScreenButton.performClick()
        waitForIdle()
        danmakuEditor.onChild().assertIsNotFocused()
    }

    private fun AniComposeUiTest.testClickAndWaitForHide() {
        // 点击来显示控制器
        runOnIdle {
            mainClock.autoAdvance = false // 三秒后会自动隐藏, 这里不能让他自动前进时间
            onRoot().performTouchInput { click() }
        }
        runOnIdle {
            mainClock.advanceTimeBy(1000L)
            waitUntil(timeoutMillis = WAIT_TIMEOUT) { topBar.exists() }
            assertEquals(
                NORMAL_VISIBLE,
                controllerState.visibility,
            )
        }

        // 等待隐藏
        runOnIdle {
            mainClock.advanceTimeBy(VIDEO_GESTURE_MOUSE_MOVE_SHOW_CONTROLLER_DURATION.inWholeMilliseconds)
            mainClock.autoAdvance = true
        }
        runOnIdle {
            waitUntil(timeoutMillis = WAIT_TIMEOUT) { topBar.doesNotExist() }
            assertEquals(
                NORMAL_INVISIBLE,
                controllerState.visibility,
            )
        }
    }

    /**
     * @see GestureFamily.autoHideController
     */
    @Test
    fun `touch - autoHideController - wait for hide`() = runAniComposeUiTest {
        setContent {
            Player(GestureFamily.TOUCH)
        }
        runOnIdle {
            assertEquals(NORMAL_INVISIBLE, controllerState.visibility)
        }

        testClickAndWaitForHide()
        testClickAndWaitForHide()
    }

    /**
     * @see GestureFamily.autoHideController
     */
    @Test
    fun `touch - autoHideController - default show controller`() = runAniComposeUiTest {
        val controllerState = PlayerControllerState(ControllerVisibility.Visible)
        mainClock.autoAdvance = false
        setContent {
            Player(GestureFamily.TOUCH, controllerState)
        }
        runOnIdle {
            assertEquals(NORMAL_VISIBLE, controllerState.visibility)
        }
        // 等待隐藏
        mainClock.advanceTimeBy(VIDEO_GESTURE_TOUCH_SHOW_CONTROLLER_DURATION.inWholeMilliseconds)
        mainClock.autoAdvance = true
        runOnIdle {
            mainClock.advanceTimeUntil(timeoutMillis = WAIT_TIMEOUT) { topBar.doesNotExist() }
            assertEquals(
                NORMAL_INVISIBLE,
                controllerState.visibility,
            )
        }
    }

    /**
     * 用户点击屏幕显示控制器, 然后用户点击隐藏, 过了 1 秒用户又点击显示,
     * advance 时间 2.5 秒, 控制器仍然显示,
     * 再经过 0.5 秒, 也就是达到 VIDEO_GESTURE_TOUCH_SHOW_CONTROLLER_DURATION, 才会隐藏控制器
     * @see GestureFamily.autoHideController
     */
    @Test
    fun `touch - autoHideController - the timer starts with each click`() = runAniComposeUiTest {
        setContent {
            Player(GestureFamily.TOUCH)
        }
        runOnIdle {
            assertEquals(NORMAL_INVISIBLE, controllerState.visibility)
        }

        val root = onAllNodes(isRoot()).onFirst()

        mainClock.autoAdvance = false // 三秒后会自动隐藏, 这里不能让他自动前进时间
        root.performTouchInput { click() }
        mainClock.advanceTimeUntil(timeoutMillis = WAIT_TIMEOUT) { topBar.exists() }
        runOnIdle {
            assertEquals(
                NORMAL_VISIBLE,
                controllerState.visibility,
            )
        }

        root.performTouchInput { click() }
        mainClock.advanceTimeUntil(timeoutMillis = WAIT_TIMEOUT) { topBar.doesNotExist() }
        runOnIdle {
            assertEquals(
                NORMAL_INVISIBLE,
                controllerState.visibility,
            )
        }
        // 过了 1 秒用户又点击显示
        mainClock.advanceTimeBy(1000L)
        root.performTouchInput { click() }
        mainClock.advanceTimeUntil(timeoutMillis = WAIT_TIMEOUT) { topBar.exists() }
        runOnIdle {
            assertEquals(
                NORMAL_VISIBLE,
                controllerState.visibility,
            )
        }
        // advance 时间 2.5 秒, 控制器仍然显示
        mainClock.advanceTimeBy(VIDEO_GESTURE_TOUCH_SHOW_CONTROLLER_DURATION.inWholeMilliseconds - 500L)
        mainClock.advanceTimeUntil(timeoutMillis = WAIT_TIMEOUT) { topBar.exists() }
        runOnIdle {
            assertEquals(
                NORMAL_VISIBLE,
                controllerState.visibility,
            )
        }
        // 再经过 0.5 秒, 也就是达到 VIDEO_GESTURE_TOUCH_SHOW_CONTROLLER_DURATION, 才会隐藏控制器
        mainClock.advanceTimeBy(500L)
        mainClock.advanceTimeUntil(timeoutMillis = WAIT_TIMEOUT) { topBar.doesNotExist() }
        runOnIdle {
            assertEquals(
                NORMAL_INVISIBLE,
                controllerState.visibility,
            )
        }
    }

    /**
     * @see GestureFamily.autoHideController
     */
    @Test
    fun `touch - autoHideController - edit danmaku`() = runAniComposeUiTest {
        setContent {
            Player(GestureFamily.TOUCH)
        }
        runOnIdle {
            assertEquals(NORMAL_INVISIBLE, controllerState.visibility)
            danmakuEditor.assertDoesNotExist()
        }
        val root = onAllNodes(isRoot()).onFirst()

        mainClock.autoAdvance = false
        root.performTouchInput { click() }
        mainClock.advanceTimeUntil(timeoutMillis = WAIT_TIMEOUT) { danmakuEditor.exists() }
        runOnIdle {
            assertEquals(NORMAL_VISIBLE, controllerState.visibility)
        }
        danmakuEditor.performClick()
        mainClock.advanceTimeBy((VIDEO_GESTURE_TOUCH_SHOW_CONTROLLER_DURATION + 1.seconds).inWholeMilliseconds)
        mainClock.advanceTimeUntil(timeoutMillis = WAIT_TIMEOUT) { danmakuEditor.exists() }
        runOnIdle {
            assertEquals(NORMAL_VISIBLE, controllerState.visibility)
        }
    }

    /**
     * @see GestureFamily.autoHideController
     */
    @Test
    fun `touch - autoHideController - click danmaku icon button and toggle controller visibility immediately`() =
        runAniComposeUiTest {
            setContent {
                Player(GestureFamily.TOUCH)
            }
            runOnIdle {
                assertEquals(NORMAL_INVISIBLE, controllerState.visibility)
            }
            val root = onAllNodes(isRoot()).onFirst()

            mainClock.autoAdvance = false
            root.performTouchInput { click() }
            mainClock.advanceTimeUntil(timeoutMillis = WAIT_TIMEOUT) { topBar.exists() }
            runOnIdle {
                assertEquals(NORMAL_VISIBLE, controllerState.visibility)
            }
            // 必须也用触摸: 鼠标点击会让指针停在按钮上持续 hover, alwaysOn 不释放, 控制器就不会隐藏
            danmakuIconButton.performTouchInput { click() }
            root.performTouchInput { click() }
            mainClock.advanceTimeUntil(timeoutMillis = WAIT_TIMEOUT) { topBar.doesNotExist() }
            runOnIdle {
                assertEquals(NORMAL_INVISIBLE, controllerState.visibility)
            }
        }

    /**
     * @see SwipeSeekerState.Companion.swipeToSeek
     */
    @Test
    fun `touch - swipeToSeek shows detached slider when controller is hidden`() = runAniComposeUiTest {
        setContent {
            Player(GestureFamily.TOUCH)
        }
        waitForIdle()
        val root = onAllNodes(isRoot()).onFirst()
        val detachedProgressSlider =
            onNodeWithTag(TAG_DETACHED_PROGRESS_SLIDER, useUnmergedTree = true)

        // 初始没有进度条
        runOnIdle {
            assertEquals(NORMAL_INVISIBLE, controllerState.visibility)
            detachedProgressSlider.assertDoesNotExist()
        }

        // 按下手指并移动, 显示独立进度条
        root.performTouchInput {
            down(centerLeft)
            moveBy(Offset(width / 2f, 0f))
        }
        runOnIdle {
            waitUntil(timeoutMillis = WAIT_TIMEOUT) { detachedProgressSlider.exists() }
            assertEquals(PREVIEW_DETACHED_SLIDER, controllerState.visibility)
//            root.assertScreenshot("/screenshots/EpisodeVideoControllerTest.touch___swipeToSeek_shows_detached_slider.png")
        }

        // 松开手指 (释放会触发 playerState.skip, 播放器命令必须在机器线程 (UI 线程) 上调用)
        runOnUiThread {
            root.performTouchInput {
                up()
            }
        }
        runOnIdle {
            waitUntil(timeoutMillis = WAIT_TIMEOUT) { detachedProgressSlider.doesNotExist() }
            assertEquals(NORMAL_INVISIBLE, controllerState.visibility)
        }
    }

    @Test
    fun `touch - swipeToSeek cancellation updates the hint and preview`() = runAniComposeUiTest {
        setContent {
            Player(GestureFamily.TOUCH)
        }
        waitForIdle()
        val cancelHint = onNodeWithText("Release to cancel")

        videoGestureHost.performTouchInput {
            val start = Offset(width * 0.2f, height * 0.8f)
            down(start)
            moveTo(Offset(width * 0.8f, start.y))
        }
        runOnIdle {
            assertTrue(progressSliderState.isPreviewing)
            cancelHint.assertDoesNotExist()
        }

        videoGestureHost.performTouchInput {
            moveTo(Offset(width * 0.8f, height * 0.1f))
        }
        runOnIdle {
            cancelHint.assertExists()
            assertFalse(progressSliderState.isPreviewing)
        }

        videoGestureHost.performTouchInput {
            moveTo(Offset(width * 0.8f, height * 0.75f))
        }
        runOnIdle {
            cancelHint.assertDoesNotExist()
            assertTrue(progressSliderState.isPreviewing)
        }

        videoGestureHost.performTouchInput {
            moveTo(Offset(width * 0.8f, height * 0.1f))
        }
        runOnIdle {
            cancelHint.assertExists()
            assertFalse(progressSliderState.isPreviewing)
        }

        mainClock.autoAdvance = false
        videoGestureHost.performTouchInput {
            up()
        }

        mainClock.advanceTimeBy(100L)
        runOnIdle {
            cancelHint.assertExists()
        }

        mainClock.advanceTimeBy(500L)
        runOnIdle {
            cancelHint.assertDoesNotExist()
            assertFalse(progressSliderState.isPreviewing)
            assertEquals(0L, currentPositionMillis)
        }
        mainClock.autoAdvance = true
    }

    /**
     * @see SwipeSeekerState.Companion.swipeToSeek
     */
    @Test
    fun `touch - swipe hides visible controls without moving slider`() = runAniComposeUiTest {
        setContent {
            Player(GestureFamily.TOUCH)
        }
        waitForIdle()
        val root = onAllNodes(isRoot()).onFirst()

        runOnUiThread {
            mainClock.autoAdvance = false
            root.performTouchInput { click() } // 显示全部控制器
        }
        runOnIdle {
            mainClock.advanceTimeBy(1000L)
            waitUntil(timeoutMillis = WAIT_TIMEOUT) { topBar.exists() }
            detachedProgressSlider.assertDoesNotExist()
        }
        val progressSliderBoundsBeforeDrag = progressSlider.fetchSemanticsNode().boundsInRoot

        runOnUiThread {
            root.performTouchInput {
                down(centerLeft)
                moveBy(Offset(width / 2f, 0f))
            }
        }
        runOnIdle {
            mainClock.advanceTimeBy(1000L)
            waitUntil(timeoutMillis = WAIT_TIMEOUT) { previewPopup.exists() }
            // Top controls remain laid out but are drawn transparently, preserving the top scrim.
            topBar.assertExists()
            detachedProgressSlider.assertDoesNotExist()
            progressSlider.assertExists()
            assertEquals(
                progressSliderBoundsBeforeDrag,
                progressSlider.fetchSemanticsNode().boundsInRoot,
            )
            onNodeWithTag(TAG_PROGRESS_SLIDER_PREVIEW_FRAME, useUnmergedTree = true).assertDoesNotExist()
            assertEquals(PREVIEW_INLINE_SLIDER, controllerState.visibility)
        }

        runOnUiThread {
            root.performTouchInput {
                up()
            }
        }
        runOnIdle {
            mainClock.advanceTimeBy(1000L)
            waitUntil(timeoutMillis = WAIT_TIMEOUT) { previewPopup.doesNotExist() }
            detachedProgressSlider.assertDoesNotExist()
            assertEquals(NORMAL_VISIBLE, controllerState.visibility)
        }
    }

    @Test
    fun `compact frame preview shows centered image and time-only popup`() = runAniComposeUiTest {
        val visibleControllerState = PlayerControllerState(NORMAL_VISIBLE)
        val framePreview = MediaProgressFramePreviewState(
            fetchFrame = { ImageBitmap(width = 160, height = 90) },
            debounceMillis = 0,
        )
        setContent {
            Player(
                gestureFamily = GestureFamily.MOUSE,
                playerControllerState = visibleControllerState,
                expanded = false,
                framePreview = framePreview,
                cacheChunkState = ChunkState.DONE,
            )
        }
        waitForIdle()

        runOnUiThread {
            progressSlider.performMouseInput { moveTo(center) }
        }
        waitUntil(timeoutMillis = WAIT_TIMEOUT) {
            previewPopup.exists() &&
                    onNodeWithTag(TAG_PROGRESS_SLIDER_CENTERED_PREVIEW_FRAME, useUnmergedTree = true).exists()
        }

        onNodeWithTag(TAG_PROGRESS_SLIDER_PREVIEW_FRAME, useUnmergedTree = true).assertDoesNotExist()
        val playerCenter = player.fetchSemanticsNode().boundsInRoot.center
        val frameCenter = onNodeWithTag(
            TAG_PROGRESS_SLIDER_CENTERED_PREVIEW_FRAME,
            useUnmergedTree = true,
        ).fetchSemanticsNode().boundsInRoot.center
        assertEquals(playerCenter, frameCenter)
    }

    @Test
    fun `touch - drag when controller is already fully visible`() = runAniComposeUiTest {
        setContent {
            Player(GestureFamily.TOUCH)
        }
        waitForIdle()
        val root = onAllNodes(isRoot()).onFirst()

        mainClock.autoAdvance = false
        root.performTouchInput { click() } // 显示全部控制器
        mainClock.advanceTimeBy(1000L)
        waitForIdle()

        topBar.assertExists()
        detachedProgressSlider.assertDoesNotExist()

        mainClock.autoAdvance = false
        root.performTouchInput {
            down(centerLeft)
            moveBy(Offset(centerX, 0f))
        }
        waitForIdle() // does nothing because autoAdvance is false
        mainClock.advanceTimeBy(1000L)
        previewPopup.assertExists()
        onAllNodesWithText("00:47", useUnmergedTree = true).onFirst().assertExists()
        runOnUiThread {
            assertEquals(PREVIEW_INLINE_SLIDER, controllerState.visibility)
        }

        // 松开手指 (释放会触发 playerState.skip, 播放器命令必须在机器线程 (UI 线程) 上调用)
        runOnUiThread {
            root.performTouchInput {
                up()
            }
        }
        waitForIdle()

        runOnIdle {
            waitUntil(timeoutMillis = WAIT_TIMEOUT) { onNodeWithText("00:47 / 01:40").exists() }
            assertEquals(NORMAL_VISIBLE, controllerState.visibility)
        }
    }

    @Test
    fun `touch - drag when controller is already fully visible and can still play`() =
        runAniComposeUiTest {
            setContent {
                Player(GestureFamily.TOUCH)
            }
            waitForIdle()
            val root = onAllNodes(isRoot()).onFirst()

            mainClock.autoAdvance = false
            root.performTouchInput { click() } // 显示全部控制器
            runOnIdle {
                mainClock.advanceTimeBy(1000L)
                waitUntil(timeoutMillis = WAIT_TIMEOUT) { topBar.exists() }
                detachedProgressSlider.assertDoesNotExist()
            }
            val progressSliderBoundsBeforeDrag = progressSlider.fetchSemanticsNode().boundsInRoot

            runOnUiThread {
                progressSlider.performTouchInput {
                    down(centerLeft)
                    moveBy(Offset(centerX, 0f))
                }
            }
            runOnIdle {
                mainClock.advanceTimeBy(1000L)
                waitUntil(timeoutMillis = WAIT_TIMEOUT) {
                    controllerState.visibility == ControllerVisibility.InlineSliderOnly
                }
                topBar.assertExists()
                // Other controller content stays composed so the slider keeps the same layout,
                // but PlayerControllerBar draws it transparently while dragging.
                mediaProgressIndicatorText.assertExists()
                progressSlider.assertExists()
                assertEquals(
                    progressSliderBoundsBeforeDrag,
                    progressSlider.fetchSemanticsNode().boundsInRoot,
                )
                assertEquals(true, progressSliderState.isPreviewing)
            }

            // 松开手指
            runOnUiThread {
                root.performTouchInput {
                    up()
                }
            }

            runOnIdle {
                waitUntil(timeoutMillis = WAIT_TIMEOUT) { onNodeWithText("00:48 / 01:40").exists() }
                assertEquals(NORMAL_VISIBLE, controllerState.visibility)
            }

            currentPositionMillis += 5000L // 播放 5 秒

            runOnIdle {
                waitUntil(timeoutMillis = WAIT_TIMEOUT) { onNodeWithText("00:53 / 01:40").exists() }
                assertEquals(NORMAL_VISIBLE, controllerState.visibility)
            }
        }

    @Test
    fun `hybrid - first touch progress slider drag after mouse can be cancelled`() = runAniComposeUiTest {
        setContent {
            Player(GestureFamily.MOUSE)
        }
        waitForIdle()

        mainClock.autoAdvance = false
        player.slightlyMoveFromCenterToRight()
        mainClock.advanceTimeBy(1000L)
        waitForIdle()

        val playerBounds = player.fetchSemanticsNode().boundsInRoot
        val sliderBounds = progressSlider.fetchSemanticsNode().boundsInRoot
        progressSlider.performTouchInput {
            down(centerLeft)
            moveTo(Offset(width * 0.75f, centerY))
        }
        runOnIdle {
            assertEquals(true, progressSliderState.isPreviewing)
        }

        progressSlider.performTouchInput {
            moveTo(playerBounds.topLeft + Offset(1f, 1f) - sliderBounds.topLeft)
        }
        runOnIdle {
            waitUntil(timeoutMillis = WAIT_TIMEOUT) {
                onNodeWithText("Release to cancel").exists()
            }
            assertEquals(false, progressSliderState.isPreviewing)
            assertEquals(ControllerVisibility.InlineSliderOnly, controllerState.visibility)
        }

        progressSlider.performTouchInput {
            moveTo(Offset(width * 0.6f, centerY))
        }
        runOnIdle {
            mainClock.advanceTimeBy(1000L)
            waitUntil(timeoutMillis = WAIT_TIMEOUT) {
                onNodeWithText("Release to cancel").doesNotExist()
            }
            assertEquals(true, progressSliderState.isPreviewing)
        }

        progressSlider.performTouchInput {
            moveTo(playerBounds.topLeft + Offset(1f, 1f) - sliderBounds.topLeft)
        }
        runOnIdle {
            waitUntil(timeoutMillis = WAIT_TIMEOUT) {
                onNodeWithText("Release to cancel").exists()
            }
        }

        progressSlider.performTouchInput {
            up()
        }
        runOnIdle {
            mainClock.advanceTimeBy(1000L)
            waitUntil(timeoutMillis = WAIT_TIMEOUT) {
                onNodeWithText("Release to cancel").doesNotExist()
            }
            assertEquals(0L, currentPositionMillis)
            assertEquals(false, progressSliderState.isPreviewing)
        }
    }

    @Test
    fun `mouse - previewing progress slider keeps full controller visible`() = runAniComposeUiTest {
        setContent {
            Player(GestureFamily.MOUSE)
        }
        waitForIdle()

        runOnUiThread {
            controllerState.toggleFullVisible(true)
            progressSliderState.previewPositionRatio(0.5f)
        }
        runOnIdle {
            waitUntil(timeoutMillis = WAIT_TIMEOUT) { topBar.exists() }
            assertEquals(NORMAL_VISIBLE, controllerState.visibility)
            mediaProgressIndicatorText.assertExists()
            progressSlider.assertExists()
        }

        runOnUiThread {
            progressSliderState.cancelPreview()
        }
    }

    /**
     * @see SwipeSeekerState.Companion.swipeToSeek
     */
    @Test // https://github.com/open-ani/ani/issues/720
    fun `touch - swipeToSeek shows detached slider and can still play`() = runAniComposeUiTest {
        setContent {
            Player(GestureFamily.TOUCH)
        }
        waitForIdle()
        val root = onAllNodes(isRoot()).onFirst()
        val detachedProgressSlider =
            onNodeWithTag(TAG_DETACHED_PROGRESS_SLIDER, useUnmergedTree = true)

        // 初始没有进度条
        runOnIdle {
            assertEquals(NORMAL_INVISIBLE, controllerState.visibility)
            detachedProgressSlider.assertDoesNotExist()
            assertEquals(false, progressSliderState.isPreviewing)
            assertEquals(0.0f, progressSliderState.displayPositionRatio)
        }

        // 按下手指并移动, 显示独立进度条
        root.performTouchInput {
            down(centerLeft)
            moveBy(Offset(width / 2f, 0f))
        }
        runOnIdle {
            waitUntil(timeoutMillis = WAIT_TIMEOUT) { detachedProgressSlider.exists() }
            assertEquals(PREVIEW_DETACHED_SLIDER, controllerState.visibility)
            assertEquals(true, progressSliderState.isPreviewing)
            assertEquals(0.47f, progressSliderState.displayPositionRatio)
        }

        // 松开手指 (释放会触发 playerState.skip, 播放器命令必须在机器线程 (UI 线程) 上调用)
        runOnUiThread {
            root.performTouchInput {
                up()
            }
        }
        runOnIdle {
            waitUntil(timeoutMillis = WAIT_TIMEOUT) { detachedProgressSlider.doesNotExist() }
            assertEquals(NORMAL_INVISIBLE, controllerState.visibility)
            assertEquals(false, progressSliderState.isPreviewing)
            assertEquals(0.47f, progressSliderState.displayPositionRatio)
        }

        currentPositionMillis += 5000L // 播放 5 秒

        mainClock.autoAdvance = false
        root.performTouchInput { click() }
        runOnIdle {
            mainClock.advanceTimeBy(1000L)
            waitUntil(timeoutMillis = WAIT_TIMEOUT) { topBar.exists() }
            assertEquals(0.52f, progressSliderState.displayPositionRatio)
        }
    }

    @Test
    @Disabled // Sometimes fail on CI
    fun `touch - hover to always on - media selector sheet`() = runAniComposeUiTest {
        testSideSheetRequestAlwaysOn(
            gestureFamily = GestureFamily.TOUCH,
            openSideSheet = { onNodeWithTag(TAG_SHOW_MEDIA_SELECTOR).performClick() },
            waitForSideSheetOpen = { waitUntil(timeoutMillis = WAIT_TIMEOUT) { onNodeWithTag(TAG_MEDIA_SELECTOR_SHEET).exists() } },
            waitForSideSheetClose = { waitUntil(timeoutMillis = WAIT_TIMEOUT) { onNodeWithTag(TAG_MEDIA_SELECTOR_SHEET).doesNotExist() } },
        )
    }

    @Test
    fun `touch - hover to always on - episode selector sheet`() = runAniComposeUiTest {
        testSideSheetRequestAlwaysOn(
            gestureFamily = GestureFamily.TOUCH,
            openSideSheet = { onNodeWithTag(TAG_SELECT_EPISODE_ICON_BUTTON).performClick() },
            waitForSideSheetOpen = { waitUntil(timeoutMillis = WAIT_TIMEOUT) { onNodeWithTag(TAG_EPISODE_SELECTOR_SHEET).exists() } },
            waitForSideSheetClose = { waitUntil(timeoutMillis = WAIT_TIMEOUT) { onNodeWithTag(TAG_EPISODE_SELECTOR_SHEET).doesNotExist() } },
        )
    }

    @Test
    fun `touch - speed switcher slider - drag updates speed and commits on release`() = runAniComposeUiTest {
        lateinit var playbackSpeed: TestPlaybackSpeed
        val committed = mutableListOf<Float>()
        setContent {
            val scope = rememberCoroutineScope()
            playbackSpeed = remember { TestPlaybackSpeed(1f) }
            Player(
                GestureFamily.TOUCH,
                playbackSpeed = playbackSpeed,
                onCommitPlaybackSpeed = { committed.add(it) },
            )
        }
        onRoot().performTouchInput { click() }
        runOnIdle {
            waitUntil(timeoutMillis = WAIT_TIMEOUT) { onNodeWithTag(TAG_SPEED_SWITCHER_TEXT_BUTTON).exists() }
        }
        onNodeWithTag(TAG_SPEED_SWITCHER_TEXT_BUTTON).performClick()
        runOnIdle {
            waitUntil(timeoutMillis = WAIT_TIMEOUT) { onNodeWithTag(TAG_SPEED_SWITCHER_SLIDER).exists() }
        }

        // 向右拖动 Slider: 拖动期间实时预览, 松手提交最终值
        onNodeWithTag(TAG_SPEED_SWITCHER_SLIDER).performTouchInput {
            down(center)
            moveBy(Offset(width * 0.2f, 0f))
        }
        runOnIdle {
            val value = playbackSpeed.value
            assertTrue(value > 1f, "expected speed to increase from 1.0, but was $value")
            assertTrue(committed.isEmpty(), "speed must not be committed until the drag finishes")
        }
        mediaProgressIndicatorText.assertTextEquals("00:00 / 01:40 (-00:57)")

        onNodeWithTag(TAG_SPEED_SWITCHER_SLIDER).performTouchInput {
            up()
        }
        runOnIdle {
            val value = playbackSpeed.value
            assertEquals(listOf(value), committed)
        }
    }

    @Test
    fun `touch - hover to always on - speed switcher`() = runAniComposeUiTest {
        // 并非 side sheet
        testSideSheetRequestAlwaysOn(
            gestureFamily = GestureFamily.TOUCH,
            openSideSheet = { onNodeWithTag(TAG_SPEED_SWITCHER_TEXT_BUTTON).performClick() },
            waitForSideSheetOpen = {
                waitUntil(timeoutMillis = WAIT_TIMEOUT) {
                    onNodeWithTag(
                        TAG_SPEED_SWITCHER_DROPDOWN_MENU,
                    ).exists()
                }
            },
            waitForSideSheetClose = {
                waitUntil(timeoutMillis = WAIT_TIMEOUT) {
                    onNodeWithTag(
                        TAG_SPEED_SWITCHER_DROPDOWN_MENU,
                    ).doesNotExist()
                }
            },
        )
    }
    ///////////////////////////////////////////////////////////////////////////
    // mouse
    ///////////////////////////////////////////////////////////////////////////

    /**
     * [GestureFamily.MOUSE] 在屏幕中间滑动鼠标, 会临时显示几秒控制器. 几秒后自动隐藏.
     *
     * @see hasPointerDevice
     */
    @Test
    fun `mouse - mouseHoverForController - center screen`() = runAniComposeUiTest {
        setContent {
            Player(GestureFamily.MOUSE)
        }
        runOnIdle {
            assertEquals(
                NORMAL_INVISIBLE,
                controllerState.visibility,
            )
        }
        testMoveMouseAndWaitForHide()
    }

    private fun AniComposeUiTest.testMoveMouseAndWaitForHide() {
        // 移动鼠标来显示控制器
        runOnIdle {
            mainClock.autoAdvance = false // 三秒后会自动隐藏, 这里不能让他自动前进时间
            player.slightlyMoveFromCenterToRight()
        }
        runOnIdle {
            waitUntil(timeoutMillis = WAIT_TIMEOUT) { topBar.exists() }
            assertEquals(
                NORMAL_VISIBLE,
                controllerState.visibility,
            )
        }


        // 等待隐藏
        runOnIdle {
            mainClock.advanceTimeBy(VIDEO_GESTURE_MOUSE_MOVE_SHOW_CONTROLLER_DURATION.inWholeMilliseconds)
            mainClock.autoAdvance = true
        }
        runOnIdle {
            waitUntil(timeoutMillis = WAIT_TIMEOUT) { topBar.doesNotExist() }
            assertEquals(
                NORMAL_INVISIBLE,
                controllerState.visibility,
            )
        }
    }

    /**
     * [GestureFamily.MOUSE] 在屏幕中间滑动鼠标, 会临时显示几秒控制器. 几秒后自动隐藏.
     * 隐藏后再次移动鼠标, 应当能重新显示几秒然后隐藏.
     *
     * @see hasPointerDevice
     */
    @Test
    fun `mouse - mouseHoverForController - center screen twice`() = runAniComposeUiTest {
        setContent {
            Player(GestureFamily.MOUSE)
        }
        runOnIdle {
            assertEquals(
                NORMAL_INVISIBLE,
                controllerState.visibility,
            )
        }

        testMoveMouseAndWaitForHide()
        // 隐藏后再次移动鼠标
        testMoveMouseAndWaitForHide()
    }

    ///////////////////////////////////////////////////////////////////////////
    // 鼠标悬浮在控制器上保持显示 (always on)
    ///////////////////////////////////////////////////////////////////////////

    /**
     * 鼠标悬浮在控制器上, 会保持显示
     */
    @Test
    fun `mouse - hover to always on - bottom bar`() = runAniComposeUiTest {
        val root = onAllNodes(isRoot()).onFirst()
        testRequestAlwaysOn(
            performGesture = {
                // 鼠标移动到控制器上
                root.performMouseInput {
                    moveTo(bottomCenter) // 肯定在 bottomBar 区域内
                }
            },
            gestureFamily = GestureFamily.MOUSE,
            expectAlwaysOn = true,
        )
    }

    /**
     * 鼠标悬浮在控制器上, 会保持显示
     */
    @Test
    fun `mouse - hover to always on - top bar`() = runAniComposeUiTest {
        val root = onAllNodes(isRoot()).onFirst()
        testRequestAlwaysOn(
            performGesture = {
                // 鼠标移动到控制器上
                root.performMouseInput {
                    moveTo(topCenter) // 肯定在 topBar 区域内
                }
            },
            gestureFamily = GestureFamily.MOUSE,
            expectAlwaysOn = true,
        )
    }

    ///////////////////////////////////////////////////////////////////////////
    // 打开 side sheets 后 request always on, 关闭后取消
    /////////////////////////////////////////////////////////////////////////// 

    private fun AniComposeUiTest.testSideSheetRequestAlwaysOn(
        gestureFamily: GestureFamily,
        openSideSheet: () -> Unit,
        waitForSideSheetOpen: () -> Unit,
        waitForSideSheetClose: () -> Unit,
    ) {
        val root = onAllNodes(isRoot()).onFirst()
        testRequestAlwaysOn(
            performGesture = {
                openSideSheet()
                waitForIdle()
                root.performMouseInput {
                    moveTo(centerRight)
                }
                waitForIdle()
                waitForSideSheetOpen()
                runOnIdle {
                    assertEquals(true, controllerState.alwaysOn)
                }
            },
            gestureFamily = gestureFamily,
            expectAlwaysOn = true,
        )
        // 点击外部, 关闭 side sheet
        runOnUiThread {
            mainClock.autoAdvance = false
        }
        runOnIdle {
            root.performTouchInput {
                click(center)
            }
            // 关闭面板后移动鼠标, 触发控制器的自动隐藏计时.
            root.slightlyMoveFromCenterToRight()
        }
        runOnIdle {
            waitForSideSheetClose()
            assertEquals(false, controllerState.alwaysOn)
        }
        // 随后应当隐藏控制器
        runOnIdle {
            mainClock.advanceTimeBy((VIDEO_GESTURE_MOUSE_MOVE_SHOW_CONTROLLER_DURATION + 1.seconds).inWholeMilliseconds)
        }
        runOnUiThread {
            mainClock.autoAdvance = true
        }
        waitForIdle()
        assertControllerVisible(false)
    }

    @Test
    @Disabled // Sometimes fail on CI
    fun `mouse - hover to always on - media selector sheet`() = runAniComposeUiTest {
        testSideSheetRequestAlwaysOn(
            gestureFamily = GestureFamily.MOUSE,
            openSideSheet = { onNodeWithTag(TAG_SHOW_MEDIA_SELECTOR).performClick() },
            waitForSideSheetOpen = { waitUntil(timeoutMillis = WAIT_TIMEOUT) { onNodeWithTag(TAG_MEDIA_SELECTOR_SHEET).exists() } },
            waitForSideSheetClose = { waitUntil(timeoutMillis = WAIT_TIMEOUT) { onNodeWithTag(TAG_MEDIA_SELECTOR_SHEET).doesNotExist() } },
        )
    }

    @Test
    fun `mouse - hover to always on - episode selector sheet`() = runAniComposeUiTest {
        testSideSheetRequestAlwaysOn(
            gestureFamily = GestureFamily.MOUSE,
            openSideSheet = { onNodeWithTag(TAG_SELECT_EPISODE_ICON_BUTTON).performClick() },
            waitForSideSheetOpen = { waitUntil(timeoutMillis = WAIT_TIMEOUT) { onNodeWithTag(TAG_EPISODE_SELECTOR_SHEET).exists() } },
            waitForSideSheetClose = { waitUntil(timeoutMillis = WAIT_TIMEOUT) { onNodeWithTag(TAG_EPISODE_SELECTOR_SHEET).doesNotExist() } },
        )
    }

    @Test
    fun `mouse - hover to always on - speed switcher`() = runAniComposeUiTest {
        // 并非 side sheet
        testSideSheetRequestAlwaysOn(
            gestureFamily = GestureFamily.MOUSE,
            openSideSheet = { onNodeWithTag(TAG_SPEED_SWITCHER_TEXT_BUTTON).performClick() },
            waitForSideSheetOpen = {
                waitUntil(timeoutMillis = WAIT_TIMEOUT) {
                    onNodeWithTag(
                        TAG_SPEED_SWITCHER_DROPDOWN_MENU,
                    ).exists()
                }
            },
            waitForSideSheetClose = {
                waitUntil(timeoutMillis = WAIT_TIMEOUT) {
                    onNodeWithTag(
                        TAG_SPEED_SWITCHER_DROPDOWN_MENU,
                    ).doesNotExist()
                }
            },
        )
    }

    ///////////////////////////////////////////////////////////////////////////
    // MOUSE 模式下单击鼠标
    ///////////////////////////////////////////////////////////////////////////

    /**
     * 手指单击控制器, 不会触发保持显示
     */
    @Test
    fun `mouse - clicking does not request always on - bottom bar`() = runAniComposeUiTest {
        val root = onAllNodes(isRoot()).onFirst()
        testRequestAlwaysOn(
            performGesture = {
                // 手指单击控制器
                root.performTouchInput {
                    click(bottomCenter) // 肯定在 bottomBar 区域内
                }
            },
            gestureFamily = GestureFamily.MOUSE,
            expectAlwaysOn = false,
        )
    }

    /**
     * 手指单击控制器, 不会触发保持显示
     */
    @Test
    fun `mouse - clicking does not request always on - top bar`() = runAniComposeUiTest {
        val root = onAllNodes(isRoot()).onFirst()
        testRequestAlwaysOn(
            performGesture = {
                // 手指单击控制器
                root.performTouchInput {
                    click(topCenter) // 肯定在 topBar 区域内
                }
            },
            gestureFamily = GestureFamily.MOUSE,
            expectAlwaysOn = false,
        )
    }

    /**
     * 流程:
     * 1. 模拟点击, 显示控制器
     * 2. [performGesture]
     * 3. 等待动画后, 根据 [expectAlwaysOn] 检查是否显示控制器
     */
    private fun AniComposeUiTest.testRequestAlwaysOn(
        performGesture: () -> Unit,
        gestureFamily: GestureFamily,
        expectAlwaysOn: Boolean = false,
    ) {
        setContent {
            Player(gestureFamily)
        }
        runOnIdle {
            assertEquals(
                NORMAL_INVISIBLE,
                controllerState.visibility,
            )
        }

        // 显示控制器
        mainClock.autoAdvance = false
        if (gestureFamily == GestureFamily.MOUSE) {
            player.slightlyMoveFromCenterToRight()
        } else {
            // 必须是触摸点击: 点击语义按事件自身的指针类型解析, 鼠标点击在这里是「暂停」而不是「显隐控制器」
            player.performTouchInput {
                click()
            }
        }
        mainClock.advanceTimeBy(1001)
        runOnIdle {
            topBar.assertExists()
            assertEquals(
                NORMAL_VISIBLE,
                controllerState.visibility,
            )
        }

        runOnUiThread {
            performGesture()
        }

        runOnUiThread {
            mainClock.advanceTimeBy((VIDEO_GESTURE_MOUSE_MOVE_SHOW_CONTROLLER_DURATION + 1.seconds).inWholeMilliseconds)
            mainClock.autoAdvance = true
        }
        runOnIdle {
            assertEquals(expectAlwaysOn, controllerState.alwaysOn)
            assertControllerVisible(expectAlwaysOn)
        }
    }

    private fun SemanticsNodeInteraction.slightlyMoveFromCenterToRight() = performMouseInput {
        moveTo(center, delayMillis = 0)
        moveBy(Offset(100f, 0f), delayMillis = 0)
    }

    private fun AniComposeUiTest.assertControllerVisible(visible: Boolean) = runOnIdle {
        if (visible) {
            waitUntil(timeoutMillis = WAIT_TIMEOUT) { topBar.exists() }
            assertEquals(
                NORMAL_VISIBLE,
                controllerState.visibility,
            )
        } else {
            waitUntil(timeoutMillis = WAIT_TIMEOUT) { topBar.doesNotExist() }
            assertEquals(
                NORMAL_INVISIBLE,
                controllerState.visibility,
            )
        }
    }
}
