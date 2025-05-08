/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.subject.episode

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.MutableStateFlow
import me.him188.ani.app.data.models.preference.DarkMode
import me.him188.ani.app.data.models.preference.FullscreenSwitchMode
import me.him188.ani.app.data.models.preference.VideoScaffoldConfig
import me.him188.ani.app.domain.media.player.ChunkState
import me.him188.ani.app.domain.media.player.staticMediaCacheProgressState
import me.him188.ani.app.domain.player.VideoLoadingState
import me.him188.ani.app.ui.danmaku.PlayerDanmakuEditor
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
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
import me.him188.ani.app.videoplayer.ui.ControllerVisibility
import me.him188.ani.app.videoplayer.ui.NoOpPlaybackSpeedController
import me.him188.ani.app.videoplayer.ui.PlaybackSpeedControllerState
import me.him188.ani.app.videoplayer.ui.PlayerControllerState
import me.him188.ani.app.videoplayer.ui.gesture.GestureFamily
import me.him188.ani.app.videoplayer.ui.gesture.NoOpLevelController
import me.him188.ani.app.videoplayer.ui.gesture.VIDEO_GESTURE_MOUSE_MOVE_SHOW_CONTROLLER_DURATION
import me.him188.ani.app.videoplayer.ui.gesture.VIDEO_GESTURE_TOUCH_SHOW_CONTROLLER_DURATION
import me.him188.ani.app.videoplayer.ui.progress.PlayerControllerDefaults
import me.him188.ani.app.videoplayer.ui.progress.PlayerProgressSliderState
import me.him188.ani.app.videoplayer.ui.progress.TAG_DANMAKU_ICON_BUTTON
import me.him188.ani.app.videoplayer.ui.progress.TAG_MEDIA_PROGRESS_INDICATOR_TEXT
import me.him188.ani.app.videoplayer.ui.progress.TAG_PROGRESS_SLIDER
import me.him188.ani.app.videoplayer.ui.progress.TAG_PROGRESS_SLIDER_PREVIEW_POPUP
import me.him188.ani.app.videoplayer.ui.progress.TAG_SELECT_EPISODE_ICON_BUTTON
import me.him188.ani.app.videoplayer.ui.progress.TAG_SPEED_SWITCHER_DROPDOWN_MENU
import me.him188.ani.app.videoplayer.ui.progress.TAG_SPEED_SWITCHER_TEXT_BUTTON
import me.him188.ani.app.videoplayer.ui.top.PlayerTopBar
import me.him188.ani.danmaku.ui.DanmakuConfig
import org.junit.jupiter.api.Disabled
import org.openani.mediamp.DummyMediampPlayer
import kotlin.test.Test
import kotlin.test.assertEquals
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
    private val SemanticsNodeInteractionsProvider.mediaProgressIndicatorText: SemanticsNodeInteraction
        get() = onNodeWithTag(TAG_MEDIA_PROGRESS_INDICATOR_TEXT, useUnmergedTree = true)

    @Composable
    private fun Player(gestureFamily: GestureFamily, playerControllerState: PlayerControllerState = controllerState) {
        ProvideCompositionLocalsForPreview(darkMode = DarkMode.DARK) {
            val scope = rememberCoroutineScope()
            val playerState = remember {
                DummyMediampPlayer(scope.coroutineContext)
            }
            val expanded = true
            val cacheProgressInfoFlow = staticMediaCacheProgressState(ChunkState.NONE).flow
            EpisodeVideoImpl(
                playerState = playerState,
                expanded = expanded,
                hasNextEpisode = true,
                onClickNextEpisode = {},
                playerControllerState = playerControllerState,
                title = { PlayerTopBar() },
                danmakuHost = {},
                danmakuEnabled = false,
                onToggleDanmaku = {},
                videoLoadingStateFlow = remember { MutableStateFlow(VideoLoadingState.Succeed(isBt = true)) },
                onClickFullScreen = {},
                onExitFullscreen = {},
                danmakuEditor = {
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
                    )
                },
                onClickScreenshot = {},
                detachedProgressSlider = {
                    PlayerControllerDefaults.MediaProgressSlider(
                        progressSliderState,
                        cacheProgressInfoFlow = cacheProgressInfoFlow,
                        Modifier.testTag(TAG_DETACHED_PROGRESS_SLIDER),
                        enabled = false,
                    )
                },
                sidebarVisible = true,
                onToggleSidebar = {},
                progressSliderState = progressSliderState,
                cacheProgressInfoFlow = cacheProgressInfoFlow,
                audioController = NoOpLevelController,
                brightnessController = NoOpLevelController,
                onVolumeChanged = { _, _ -> },
                playbackSpeedControllerState = remember {
                    PlaybackSpeedControllerState(NoOpPlaybackSpeedController, scope = scope)
                },
                leftBottomTips = {},
                fullscreenSwitchButton = {
                    EpisodeVideoDefaults.FloatingFullscreenSwitchButton(
                        FullscreenSwitchMode.ONLY_IN_CONTROLLER,
                        isFullscreen = expanded,
                        onClickFullScreen = {},
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
                            EpisodeVideoSideSheets.DanmakuRegexFilterSettings(
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
                gestureFamily = gestureFamily,
                modifier = Modifier.testTag("PLAYER"),
            )
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
        onRoot().performClick()
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
        root.performClick()
        runOnIdle {
            mainClock.advanceTimeBy(1000L)
            waitUntil(timeoutMillis = WAIT_TIMEOUT) { topBar.exists() }
            assertEquals(NORMAL_VISIBLE, controllerState.visibility)
        }

        root.performClick()
        runOnIdle {
            mainClock.advanceTimeUntil(timeoutMillis = WAIT_TIMEOUT) { topBar.doesNotExist() }
            waitUntil(timeoutMillis = WAIT_TIMEOUT) { topBar.doesNotExist() }
            assertEquals(NORMAL_INVISIBLE, controllerState.visibility)
        }
    }

    private fun AniComposeUiTest.testClickAndWaitForHide() {
        // 点击来显示控制器
        runOnIdle {
            mainClock.autoAdvance = false // 三秒后会自动隐藏, 这里不能让他自动前进时间
            onRoot().performClick()
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
        root.performClick()
        mainClock.advanceTimeUntil(timeoutMillis = WAIT_TIMEOUT) { topBar.exists() }
        runOnIdle {
            assertEquals(
                NORMAL_VISIBLE,
                controllerState.visibility,
            )
        }

        root.performClick()
        mainClock.advanceTimeUntil(timeoutMillis = WAIT_TIMEOUT) { topBar.doesNotExist() }
        runOnIdle {
            assertEquals(
                NORMAL_INVISIBLE,
                controllerState.visibility,
            )
        }
        // 过了 1 秒用户又点击显示
        mainClock.advanceTimeBy(1000L)
        root.performClick()
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
        root.performClick()
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
            root.performClick()
            mainClock.advanceTimeUntil(timeoutMillis = WAIT_TIMEOUT) { topBar.exists() }
            runOnIdle {
                assertEquals(NORMAL_VISIBLE, controllerState.visibility)
            }
            danmakuIconButton.performClick()
            root.performClick()
            mainClock.advanceTimeUntil(timeoutMillis = WAIT_TIMEOUT) { topBar.doesNotExist() }
            runOnIdle {
                assertEquals(NORMAL_INVISIBLE, controllerState.visibility)
            }
        }

    /**
     * @see GestureFamily.swipeToSeek
     */
    @Test
    fun `touch - swipeToSeek shows detached slider`() = runAniComposeUiTest {
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

        // 松开手指
        root.performTouchInput {
            up()
        }
        runOnIdle {
            waitUntil(timeoutMillis = WAIT_TIMEOUT) { detachedProgressSlider.doesNotExist() }
            assertEquals(NORMAL_INVISIBLE, controllerState.visibility)
        }
    }

    /**
     * @see GestureFamily.swipeToSeek
     */
    @Test
    fun `touch - swipe when controller is already fully visible`() = runAniComposeUiTest {
        setContent {
            Player(GestureFamily.TOUCH)
        }
        waitForIdle()
        val root = onAllNodes(isRoot()).onFirst()

        runOnUiThread {
            mainClock.autoAdvance = false
            root.performClick() // 显示全部控制器 
        }
        runOnIdle {
            mainClock.advanceTimeBy(1000L)
            waitUntil(timeoutMillis = WAIT_TIMEOUT) { topBar.exists() }
            detachedProgressSlider.assertDoesNotExist()
        }

        runOnUiThread {
            root.performTouchInput {
                down(centerLeft)
                moveBy(Offset(width / 2f, 0f))
            }
        }
        runOnIdle {
            waitUntil(timeoutMillis = WAIT_TIMEOUT) { previewPopup.exists() }
            detachedProgressSlider.assertDoesNotExist()
            assertEquals(NORMAL_VISIBLE, controllerState.visibility)
        }

        runOnUiThread {
            root.performTouchInput {
                up()
            }
        }
        runOnIdle {
            waitUntil(timeoutMillis = WAIT_TIMEOUT) { previewPopup.doesNotExist() }
            detachedProgressSlider.assertDoesNotExist()
            assertEquals(NORMAL_VISIBLE, controllerState.visibility)
        }
    }

    @Test
    fun `touch - drag when controller is already fully visible`() = runAniComposeUiTest {
        setContent {
            Player(GestureFamily.TOUCH)
        }
        waitForIdle()
        val root = onAllNodes(isRoot()).onFirst()

        mainClock.autoAdvance = false
        root.performClick() // 显示全部控制器
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
        mainClock.advanceTimeByFrame() // renders the next frame (i.e. update derivedStateOf and Text)
        mediaProgressIndicatorText.assertTextEquals("00:47 / 01:40")
        runOnUiThread {
            assertEquals(NORMAL_VISIBLE, controllerState.visibility)
        }

        // 松开手指
        root.performTouchInput {
            up()
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
            root.performClick() // 显示全部控制器
            runOnIdle {
                mainClock.advanceTimeBy(1000L)
                waitUntil(timeoutMillis = WAIT_TIMEOUT) { topBar.exists() }
                detachedProgressSlider.assertDoesNotExist()
            }

            runOnUiThread {
                progressSlider.performTouchInput {
                    down(centerLeft)
                    moveBy(Offset(centerX, 0f))
                }
            }
            runOnIdle {
                waitUntil(timeoutMillis = WAIT_TIMEOUT) { onNodeWithText("00:48 / 01:40").exists() }
                assertEquals(NORMAL_VISIBLE, controllerState.visibility)
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

    /**
     * @see GestureFamily.swipeToSeek
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

        // 松开手指
        root.performTouchInput {
            up()
        }
        runOnIdle {
            waitUntil(timeoutMillis = WAIT_TIMEOUT) { detachedProgressSlider.doesNotExist() }
            assertEquals(NORMAL_INVISIBLE, controllerState.visibility)
            assertEquals(false, progressSliderState.isPreviewing)
            assertEquals(0.47f, progressSliderState.displayPositionRatio)
        }

        currentPositionMillis += 5000L // 播放 5 秒

        mainClock.autoAdvance = false
        root.performClick()
        runOnIdle {
            mainClock.advanceTimeBy(1000L)
            waitUntil(timeoutMillis = WAIT_TIMEOUT) { topBar.exists() }
            assertEquals(0.52f, progressSliderState.displayPositionRatio)
        }
    }

    @Test
    fun `touch - hover to always on - danmaku settings sheet`() = runAniComposeUiTest {
        testSideSheetRequestAlwaysOn(
            gestureFamily = GestureFamily.TOUCH,
            openSideSheet = { onNodeWithTag(TAG_SHOW_SETTINGS).performClick() },
            waitForSideSheetOpen = { waitUntil(timeoutMillis = WAIT_TIMEOUT) { onNodeWithTag(TAG_DANMAKU_SETTINGS_SHEET).exists() } },
            waitForSideSheetClose = { waitUntil(timeoutMillis = WAIT_TIMEOUT) { onNodeWithTag(TAG_DANMAKU_SETTINGS_SHEET).doesNotExist() } },
        )
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
     * @see GestureFamily.mouseHoverForController
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
            onRoot().performTouchInput { // Move 事件才能触发 
                swipe(centerLeft, center)
            }
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
     * @see GestureFamily.mouseHoverForController
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
            // 目前的 controller mouseHoverForController 依赖 Move 事件, 但 compose 似乎有点问题
            // 所以额外广播一个事件
            root.performTouchInput {
                swipe(center, center - Offset(1f, 1f))
            }
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
    fun `mouse - hover to always on - danmaku settings sheet`() = runAniComposeUiTest {
        testSideSheetRequestAlwaysOn(
            gestureFamily = GestureFamily.MOUSE,
            openSideSheet = { onNodeWithTag(TAG_SHOW_SETTINGS).performClick() },
            waitForSideSheetOpen = { waitUntil(timeoutMillis = WAIT_TIMEOUT) { onNodeWithTag(TAG_DANMAKU_SETTINGS_SHEET).exists() } },
            waitForSideSheetClose = { waitUntil(timeoutMillis = WAIT_TIMEOUT) { onNodeWithTag(TAG_DANMAKU_SETTINGS_SHEET).doesNotExist() } },
        )
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
            player.performMouseInput {
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