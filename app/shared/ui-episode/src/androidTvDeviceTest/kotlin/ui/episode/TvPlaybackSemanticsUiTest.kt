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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
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
import me.him188.ani.app.ui.lang.watch_together_leave
import me.him188.ani.app.ui.lang.watch_together_member_offline
import me.him188.ani.app.ui.lang.watch_together_state_buffering
import me.him188.ani.app.ui.watchtogether.WatchTogetherMemberPresence
import me.him188.ani.app.ui.watchtogether.WatchTogetherMemberPresentation
import me.him188.ani.app.ui.watchtogether.WatchTogetherPlaybackPresentation
import me.him188.ani.app.videoplayer.ui.progress.MediaProgressFramePreviewState
import me.him188.ani.leanback.ui.foundation.theme.AniTvTheme
import me.him188.ani.leanback.ui.watchtogether.TvTogetherState
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class TvPlaybackSemanticsUiTest {
    @Test
    fun centralIndicatorFollowsSelectionResolutionBufferingAndFailure() = runAniComposeUiTest {
        var state by mutableStateOf(TvEpisodeUiState())
        showPlayer { state }
        assertMessage(Lang.subject_episode_video_loading_auto_selecting)
        runOnIdle { state = state.copy(loadingState = VideoLoadingState.ResolvingSource) }
        assertMessage(Lang.subject_episode_video_loading_resolving_source)
        runOnIdle { state = state.copy(loadingState = VideoLoadingState.DecodingData(false)) }
        assertMessage(Lang.subject_episode_video_loading_decoding_data)
        runOnIdle { state = state.copy(loadingState = VideoLoadingState.Succeed(false)) }
        onNodeWithTag("tv-player-loading").assertDoesNotExist()
        runOnIdle { state = state.copy(isBuffering = true) }
        assertMessage(Lang.subject_episode_video_loading_buffering)
        saveScreenshot("tv-buffering")
        runOnIdle { state = state.copy(isBuffering = false, playerError = true) }
        assertMessage(Lang.subject_episode_video_loading_player_error)
        runOnIdle { state = state.copy(playerError = false, loadingState = VideoLoadingState.NetworkError) }
        assertMessage(Lang.subject_episode_video_loading_cause_network_error)
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
        val presentation = TvPlayerPresentationState({ TvPlaybackSnapshot(false, 20_000, 60_000) }, {})
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
        state: () -> TvEpisodeUiState,
    ) {
        setContent {
            AniTvTheme {
                val uiState = state()
                TvEpisodeScreen(
                    uiState = uiState, togetherState = togetherState(), onTogetherIntent = {},
                    commentsPager = emptyFlow(), actionEvents = emptyFlow(),
                    onIntent = { true }, video = { Box(it.background(Color(0xFF1E2A38))) }, resolver = {}, danmaku = {},
                    modifier = Modifier.testTag("tv-semantics-test"),
                    presentationState = presentationState ?: rememberTvPlayerPresentationState(uiState) { true },
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
