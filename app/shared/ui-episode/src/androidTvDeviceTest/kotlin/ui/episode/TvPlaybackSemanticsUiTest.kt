/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.leanback.ui.episode

import android.graphics.Bitmap
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import me.him188.ani.app.domain.media.fetch.MediaSourceFetchState
import me.him188.ani.app.domain.mediasource.web.PageExpectation
import me.him188.ani.app.domain.mediasource.web.SolveRequest
import me.him188.ani.app.domain.mediasource.web.WebCaptchaKind
import me.him188.ani.app.domain.player.VideoLoadingState
import me.him188.ani.app.ui.framework.AniComposeUiTest
import me.him188.ani.app.ui.framework.runAniComposeUiTest
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.media_source_results_failed
import me.him188.ani.app.ui.lang.subject_episode_video_loading_auto_selecting
import me.him188.ani.app.ui.lang.subject_episode_video_loading_buffering
import me.him188.ani.app.ui.lang.subject_episode_video_loading_cause_network_error
import me.him188.ani.app.ui.lang.subject_episode_video_loading_decoding_data
import me.him188.ani.app.ui.lang.subject_episode_video_loading_player_error
import me.him188.ani.app.ui.lang.subject_episode_video_loading_resolving_source
import me.him188.ani.app.ui.lang.video_player_pause
import me.him188.ani.app.ui.lang.video_player_play
import me.him188.ani.app.ui.lang.watch_together_leave
import me.him188.ani.app.ui.lang.watch_together_member_offline
import me.him188.ani.app.ui.lang.watch_together_state_buffering
import me.him188.ani.app.ui.watchtogether.WatchTogetherMemberPresence
import me.him188.ani.app.ui.watchtogether.WatchTogetherMemberPresentation
import me.him188.ani.app.ui.watchtogether.WatchTogetherPlaybackPresentation
import me.him188.ani.app.videoplayer.ui.progress.MediaProgressFramePreviewState
import me.him188.ani.leanback.ui.episode.playback.TvChapter
import me.him188.ani.leanback.ui.episode.playback.TvPlaybackInteractionState
import me.him188.ani.leanback.ui.episode.presentation.TvPlaybackSnapshot
import me.him188.ani.leanback.ui.episode.presentation.TvPlayerAction
import me.him188.ani.leanback.ui.episode.presentation.TvPlayerDialog
import me.him188.ani.leanback.ui.episode.presentation.TvPlayerPanel
import me.him188.ani.leanback.ui.episode.presentation.TvPlayerPresentationState
import me.him188.ani.leanback.ui.episode.presentation.rememberTvPlayerPresentationState
import me.him188.ani.leanback.ui.episode.source.TvPlayerSourceDialog
import me.him188.ani.leanback.ui.episode.source.TvSourceGroup
import me.him188.ani.leanback.ui.episode.source.TvSourceSelectionState
import me.him188.ani.leanback.ui.episode.source.rememberTvSourceDialogState
import me.him188.ani.leanback.ui.foundation.theme.AniTvTheme
import me.him188.ani.leanback.ui.watchtogether.TvTogetherState
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import org.openani.mediamp.MediaStatus
import org.openani.mediamp.PlaybackErrorCode
import org.openani.mediamp.PlaybackException
import org.openani.mediamp.PlayerState
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class TvPlaybackSemanticsUiTest {
    @Test
    fun speedStepperKeepsOneFocusTargetDuringDirectionalAdjustment() = runAniComposeUiTest {
        var state by mutableStateOf(TvEpisodeUiState(
            playerState = PlayerState(MediaStatus.Ready, true, false),
            loadingState = VideoLoadingState.Succeed(false),
            durationMillis = 60_000,
            playbackSpeed = 1f,
        ))
        val presentation = TvPlayerPresentationState(
            { TvPlaybackSnapshot(state.playerState, 20_000, 60_000) }, {},
        )
        presentation.onAction(TvPlayerAction.OpenDialog(TvPlayerDialog.Speed))
        val steps = mutableListOf<Int>()
        lateinit var backDispatcher: OnBackPressedDispatcher
        showPlayer(presentationState = presentation, onBackDispatcher = { backDispatcher = it }, onIntent = { intent ->
            if (intent is TvEpisodeIntent.AdjustSpeed) {
                steps += intent.direction
                state = state.copy(playbackSpeed = state.playbackSpeed + intent.direction * .25f)
            }
            true
        }) { state }
        onNodeWithTag("tv-speed-control").assertIsFocused()
        key(Key.DirectionRight)
        assertEquals(1.25f, state.playbackSpeed)
        onNodeWithTag("tv-speed-control").assertIsFocused().assertTextContains("1.25", substring = true)
        saveScreenshot("tv-speed-shared-stepper")
        key(Key.DirectionLeft)
        assertEquals(1f, state.playbackSpeed)
        assertEquals(listOf(1, -1), steps)
        onNodeWithTag("tv-speed-control").assertIsFocused()
        // Android Back is dispatched by the activity, outside Compose's synthetic key input.
        runOnUiThread { backDispatcher.onBackPressed() }
        waitForIdle()
        waitUntil(timeoutMillis = 5_000) {
            onAllNodes(hasTestTag("tv-speed-button") and isFocused()).fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithTag("tv-speed-button").assertIsFocused()
    }

    @Test
    fun seekBarAndFirstButtonTogglePlaybackAndKeepFocusEvenWhileBuffering() = runAniComposeUiTest {
        var state by mutableStateOf(TvEpisodeUiState(
            playerState = PlayerState(MediaStatus.Ready, true, false),
            loadingState = VideoLoadingState.Succeed(false),
            durationMillis = 60_000,
        ))
        var toggles = 0
        showPlayer(onIntent = { intent ->
            if (intent == TvEpisodeIntent.TogglePause) {
                toggles++
                state = state.copy(playerState = state.playerState.copy(playWhenReady = !state.playerState.playWhenReady))
            }
            true
        }) { state }
        val playPause = onNodeWithTag("tv-play-pause-button")
        val nodeId = playPause.fetchSemanticsNode().id
        for (buffering in listOf(false, true)) {
            runOnIdle { state = state.copy(playerState = state.playerState.copy(isBuffering = buffering)) }
            onNodeWithTag("tv-player-seekbar").assertIsFocused()
            playPause.assertContentDescriptionEquals(playerTestString(Lang.video_player_pause))
            key(Key.DirectionCenter)
            assertFalse(state.playerState.playWhenReady)
            onNodeWithTag("tv-player-seekbar").assertIsFocused()
            playPause.assertContentDescriptionEquals(playerTestString(Lang.video_player_play))
            key(Key.DirectionCenter)
            assertTrue(state.playerState.playWhenReady)
            key(Key.DirectionDown)
            playPause.assertIsFocused().assertContentDescriptionEquals(playerTestString(Lang.video_player_pause))
            if (!buffering) saveScreenshot("tv-play-pause-playing")
            key(Key.DirectionCenter)
            assertFalse(state.playerState.playWhenReady)
            playPause.assertIsFocused().assertContentDescriptionEquals(playerTestString(Lang.video_player_play))
            assertEquals(nodeId, playPause.fetchSemanticsNode().id)
            if (!buffering) saveScreenshot("tv-play-pause-paused")
            key(Key.DirectionCenter)
            assertTrue(state.playerState.playWhenReady)
            playPause.assertIsFocused().assertContentDescriptionEquals(playerTestString(Lang.video_player_pause))
            key(Key.DirectionUp)
            onNodeWithTag("tv-player-seekbar").assertIsFocused()
        }
        assertEquals(8, toggles)
    }

    @Test
    fun centralIndicatorFollowsSelectionResolutionBufferingAndFailure() = runAniComposeUiTest {
        var state by mutableStateOf(TvEpisodeUiState())
        showPlayer { state }
        assertMessage(Lang.subject_episode_video_loading_auto_selecting)
        runOnIdle { state = state.copy(loadingState = VideoLoadingState.ResolvingSource) }
        assertMessage(Lang.subject_episode_video_loading_resolving_source)
        runOnIdle { state = state.copy(loadingState = VideoLoadingState.DecodingData(false)) }
        assertMessage(Lang.subject_episode_video_loading_decoding_data)
        runOnIdle {
            state = state.copy(
                loadingState = VideoLoadingState.Succeed(false),
                playerState = PlayerState(MediaStatus.Ready, true, false),
            )
        }
        onNodeWithTag("tv-player-loading").assertDoesNotExist()
        runOnIdle { state = state.copy(playerState = state.playerState.copy(isBuffering = true)) }
        assertMessage(Lang.subject_episode_video_loading_buffering)
        saveScreenshot("tv-buffering")
        runOnIdle {
            state = state.copy(playerState = PlayerState(
                MediaStatus.Error(PlaybackException(PlaybackErrorCode.DECODING, "Test decoding failure")), false, false,
            ))
        }
        assertMessage(Lang.subject_episode_video_loading_player_error)
        runOnIdle { state = state.copy(playerState = PlayerState.Initial, loadingState = VideoLoadingState.NetworkError) }
        assertMessage(Lang.subject_episode_video_loading_cause_network_error)
    }

    @Test
    fun mediaKeysUsePlaybackIntentWhileBuffering() = runAniComposeUiTest {
        var state by mutableStateOf(TvEpisodeUiState(
            playerState = PlayerState(MediaStatus.Ready, true, true),
            loadingState = VideoLoadingState.Succeed(false),
        ))
        val toggles = mutableListOf<TvEpisodeIntent>()
        showPlayer(onIntent = { intent ->
            if (intent == TvEpisodeIntent.TogglePause) {
                toggles += intent
                state = state.copy(playerState = state.playerState.copy(playWhenReady = !state.playerState.playWhenReady))
            }
            true
        }) { state }
        key(Key.MediaPlay)
        assertTrue(toggles.isEmpty())
        key(Key.MediaPause)
        key(Key.MediaPause)
        assertEquals(1, toggles.size)
        assertFalse(state.playerState.playWhenReady)
        key(Key.MediaPlay)
        key(Key.MediaPlay)
        assertEquals(2, toggles.size)
        assertTrue(state.playerState.playWhenReady)
    }

    @Test
    fun autoHideTimerRestartsAfterBufferingEnds() = runAniComposeUiTest {
        var state by mutableStateOf(TvEpisodeUiState(
            playerState = PlayerState(MediaStatus.Ready, true, false),
            loadingState = VideoLoadingState.Succeed(false),
        ))
        showPlayer { state }
        mainClock.autoAdvance = false
        mainClock.advanceTimeBy(3_000)
        runOnIdle { state = state.copy(playerState = state.playerState.copy(isBuffering = true)) }
        mainClock.advanceTimeBy(6_000)
        onNodeWithTag("tv-player-seekbar").assertIsDisplayed()
        runOnIdle { state = state.copy(playerState = state.playerState.copy(isBuffering = false)) }
        mainClock.advanceTimeBy(3_000)
        onNodeWithTag("tv-player-seekbar").assertIsDisplayed()
        mainClock.advanceTimeBy(3_000)
        onNodeWithTag("tv-player-seekbar").assertDoesNotExist()
    }

    @Test
    fun seekPreviewUsesCircularLoadingAndHonorsCapabilityAndPreference() = runAniComposeUiTest {
        var state by mutableStateOf(TvEpisodeUiState(
            loadingState = VideoLoadingState.Succeed(false),
            durationMillis = 60_000,
            interaction = TvPlaybackInteractionState(20_000),
            options = TvPlayerOptionsState(
                previewAvailable = true, previewLoading = true,
                chapters = listOf(TvChapter("片头", 0, 30_000)),
            ),
        ))
        showPlayer { state }
        onNodeWithTag("tv-seek-preview-loading").assertIsDisplayed()
        onNodeWithText("片头").assertIsDisplayed()
        onNodeWithText("正在加载预览…").assertDoesNotExist()
        saveScreenshot("tv-seek-preview-loading")
        runOnIdle {
            state = state.copy(options = state.options.copy(
                videoConfig = state.options.videoConfig.copy(enableFramePreview = false),
            ))
        }
        onNodeWithTag("tv-seek-preview-frame").assertDoesNotExist()
        onNodeWithText("片头").assertIsDisplayed()
        runOnIdle {
            state = state.copy(options = state.options.copy(
                videoConfig = state.options.videoConfig.copy(enableFramePreview = true), previewAvailable = false,
            ))
        }
        onNodeWithTag("tv-seek-preview-frame").assertDoesNotExist()
    }

    @Test
    fun sourceVerificationIsDistinctFromRetryAndShowsBusyAndUnsupportedStates() = runAniComposeUiTest {
        val request = SolveRequest("source", "https://example.invalid", WebCaptchaKind.Unknown, PageExpectation.AnyContent)
        var group by mutableStateOf(TvSourceGroup(
            "captcha", "source", "需要验证的源", null, MediaSourceFetchState.CaptchaRequired(request, 0), emptyList(),
        ))
        val intents = mutableListOf<TvEpisodeIntent>()
        setContent {
            AniTvTheme {
                TvPlayerSourceDialog(
                    TvSourceSelectionState(listOf(group), loading = false), rememberTvSourceDialogState(), null,
                    Modifier.testTag("tv-semantics-test"),
                ) { intents += it; true }
            }
        }
        onNodeWithText("需要验证的源").assertIsDisplayed()
        key(Key.DirectionDown)
        key(Key.DirectionDown)
        onNodeWithTag("tv-source-action-captcha").assertIsFocused()
        key(Key.DirectionCenter)
        assertEquals<TvEpisodeIntent>(TvEpisodeIntent.ResolveSourceCaptcha("captcha"), intents.single())
        runOnIdle { group = group.copy(isResolvingCaptcha = true) }
        onNodeWithTag("tv-source-action-captcha").assertIsNotEnabled()
        saveScreenshot("tv-source-verification-busy")
        runOnIdle { group = group.copy(isResolvingCaptcha = false, isCaptchaSupported = false) }
        onNodeWithTag("tv-source-action-captcha").assertIsNotEnabled()
        runOnIdle { group = group.copy(state = MediaSourceFetchState.Failed(IllegalStateException(), 1)) }
        onNodeWithText(playerTestString(Lang.media_source_results_failed)).assertIsDisplayed()
    }

    @Test
    fun roomControlsRemainAvailableWithoutPlaybackAndOfflineMembersDoNotAppearWatching() = runAniComposeUiTest {
        var together by mutableStateOf(TvTogetherState(joined = true, roomName = "测试房间"))
        val presentation = TvPlayerPresentationState({ TvPlaybackSnapshot(PlayerState.Initial, 20_000, 60_000) }, {})
        presentation.onAction(TvPlayerAction.TogglePanel(TvPlayerPanel.Together))
        showPlayer(togetherState = { together }, presentationState = presentation) {
            TvEpisodeUiState(
                loadingState = VideoLoadingState.Succeed(false),
            )
        }
        onNodeWithTag("tv-together-follow").assertIsDisplayed()
        onNodeWithText(playerTestString(Lang.watch_together_leave)).assertIsDisplayed()
        val buffering = WatchTogetherPlaybackPresentation("番剧", "2", "第二集", 20_000, 60_000, false, true, false)
        runOnIdle {
            together = together.copy(
                playback = buffering,
                members = listOf(WatchTogetherMemberPresentation(
                    userId = "member", nickname = "掉线的观众", avatarUrl = null, isHost = false, isSelf = false,
                    following = true, state = WatchTogetherMemberPresence.DISCONNECTED,
                    watching = buffering, disconnectedMinutes = 3,
                )),
            )
        }
        assertMessage(Lang.watch_together_state_buffering)
        onNodeWithText(runBlocking { getString(Lang.watch_together_member_offline, 3) }, substring = true).assertIsDisplayed()
    }

    @Test
    fun pendingOrMissingPreviewFramesRetainTheLastFrameAndMediaChangesClearTheCache() = runTest {
        val first = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888).asImageBitmap()
        val pending = CompletableDeferred<Unit>()
        var fetches = 0
        val preview = MediaProgressFramePreviewState(fetchFrame = { position ->
            fetches++
            when (position) {
                0L -> first
                2_000L -> { pending.await(); null }
                else -> null
            }
        }, debounceMillis = 0)
        preview.requestFrame(0)
        assertSame(first, preview.frame)
        val request = launch { preview.requestFrame(2_000) }
        runCurrent()
        assertTrue(preview.isLoading)
        assertSame(first, preview.frame)
        request.cancelAndJoin()
        assertFalse(preview.isLoading)
        assertSame(first, preview.frame)
        preview.requestFrame(4_000)
        assertSame(first, preview.frame)
        preview.onPreviewFinished()
        assertNull(preview.frame)
        preview.requestFrame(0)
        assertEquals(3, fetches)
        preview.onMediaChanged()
        assertNull(preview.frame)
        preview.requestFrame(0)
        assertEquals(4, fetches)
    }

    private fun AniComposeUiTest.showPlayer(
        togetherState: () -> TvTogetherState = { TvTogetherState() },
        presentationState: TvPlayerPresentationState? = null,
        onIntent: (TvEpisodeIntent) -> Boolean = { true },
        onBackDispatcher: (OnBackPressedDispatcher) -> Unit = {},
        state: () -> TvEpisodeUiState,
    ) {
        setContent {
            val backDispatcher = checkNotNull(LocalOnBackPressedDispatcherOwner.current).onBackPressedDispatcher
            SideEffect { onBackDispatcher(backDispatcher) }
            AniTvTheme {
                val uiState = state()
                TvEpisodeScreen(
                    uiState = uiState, togetherState = togetherState(), onTogetherIntent = {},
                    commentsPager = emptyFlow(), actionEvents = emptyFlow(),
                    onIntent = onIntent, video = { Box(it.background(Color(0xFF1E2A38))) }, resolver = {}, danmaku = {},
                    modifier = Modifier.testTag("tv-semantics-test"),
                    presentationState = presentationState ?: rememberTvPlayerPresentationState(uiState, onIntent),
                )
            }
        }
    }

    private fun AniComposeUiTest.key(key: Key) {
        onRoot().performKeyInput { pressKey(key) }
        waitForIdle()
    }

    private fun AniComposeUiTest.assertMessage(resource: StringResource) {
        val text = runBlocking { getString(resource) }
        // TextWithBorder draws both the outline and the foreground text.
        onAllNodesWithText(text, substring = true).onFirst().assertIsDisplayed()
    }

    private fun AniComposeUiTest.saveScreenshot(name: String) {
        val bitmap = onNodeWithTag("tv-semantics-test").captureToImage().asAndroidBitmap()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.getExternalFilesDir("screenshots"), "$name.png")
        file.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
    }
}
