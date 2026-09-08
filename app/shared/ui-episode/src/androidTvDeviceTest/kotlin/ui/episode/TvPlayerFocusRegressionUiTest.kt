/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.leanback.ui.episode

import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.paging.PagingData
import androidx.test.platform.app.InstrumentationRegistry
import com.github.panpf.sketch.Sketch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import me.him188.ani.app.data.models.UserInfo
import me.him188.ani.app.data.models.episode.EpisodeComment
import me.him188.ani.app.data.models.episode.EpisodeCommentSource
import me.him188.ani.app.domain.player.VideoLoadingState
import me.him188.ani.app.ui.foundation.LocalSketch
import me.him188.ani.app.ui.framework.AniComposeUiTest
import me.him188.ani.app.ui.framework.runAniComposeUiTest
import me.him188.ani.danmaku.api.DanmakuServiceId
import me.him188.ani.danmaku.api.provider.DanmakuEpisode
import me.him188.ani.danmaku.api.provider.DanmakuProviderId
import me.him188.ani.danmaku.api.provider.DanmakuSubject
import me.him188.ani.leanback.ui.foundation.theme.AniTvTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// Regression scenarios for dynamic content and player overlay focus.
class TvPlayerFocusRegressionUiTest {
    private class Fixture {
        val machine = TvPlayerPresentationState({ TvPlaybackSnapshot(true, 20_000, 60_000) }, {})
        var options by mutableStateOf(TvPlayerOptionsState())
        var matching by mutableStateOf(TvDanmakuMatchState())
        var together by mutableStateOf(TvTogetherState(requiresLogin = true))
        val comments = MutableStateFlow(PagingData.from(listOf(comment("old"))))
        lateinit var backDispatcher: OnBackPressedDispatcher

        fun onIntent(intent: TvEpisodeIntent): Boolean = when (intent) {
            is TvEpisodeIntent.SelectDanmakuSubject -> {
                matching = matching.copy(
                    selectedSubject = matching.subjects.first { it.id == intent.id },
                    episodes = (1..40).map { DanmakuEpisode("ep-$it", "匹配剧集 $it") },
                )
                true
            }
            is TvEpisodeIntent.MatchDanmaku -> {
                matching = matching.copy(providerId = intent.providerId)
                true
            }
            TvEpisodeIntent.BackDanmakuMatch -> {
                matching = matching.copy(selectedSubject = null)
                true
            }
            else -> true
        }
    }

    @Test
    fun restoringThePlayerKeepsTheTogetherConfirmationAndItsFocus() = runAniComposeUiTest {
        var visible by mutableStateOf(true)
        val uiState = TvEpisodeUiState(
            currentEpisodeId = 1,
            loadingState = VideoLoadingState.Succeed(false),
            positionMillis = 20_000,
            durationMillis = 60_000,
        )
        val together = TvTogetherState(joined = true, roomName = "测试房间")
        setContent {
            val holder = rememberSaveableStateHolder()
            AniTvTheme {
                if (visible) holder.SaveableStateProvider("player") {
                    TvEpisodeScreen(
                        uiState = uiState,
                        togetherState = together,
                        onTogetherIntent = {},
                        commentsPager = emptyFlow(),
                        actionEvents = emptyFlow(),
                        onIntent = { true },
                        video = { Box(it.background(Color.Black)) },
                        resolver = {},
                        danmaku = {},
                    )
                }
            }
        }
        openPanel(TvPlayerPanel.Together)
        onNodeWithText("跟随房主").assertIsFocused()
        key(Key.DirectionDown)
        key(Key.DirectionCenter)
        onNodeWithText("确定退出房间").assertIsFocused()
        runOnIdle { visible = false }
        onNodeWithTag("tv-player-sidebar").assertDoesNotExist()
        runOnIdle { visible = true }
        onNodeWithText("确定退出房间").assertIsFocused()
    }

    @Test
    fun firstMatchingSubjectSwitchRestoresTheNewEntry() = runAniComposeUiTest {
        val fixture = Fixture().apply {
            matching = TvDanmakuMatchState(query = "查询", subjects = (1..40).map { DanmakuSubject("s-$it", "匹配条目 $it") })
        }
        showPlayer(fixture)
        runOnIdle { fixture.machine.onAction(TvPlayerAction.OpenDialog(TvPlayerDialog.DanmakuMatch)) }
        key(Key.DirectionDown)
        key(Key.DirectionDown)
        onNodeWithText("匹配条目 1").assertIsFocused()
        key(Key.DirectionCenter)
        onNodeWithText("返回番剧列表").assertIsFocused()
    }

    @Test
    fun matchingSubjectSwitchMustComposeTheNewEntryBeforeFocusing() = runAniComposeUiTest {
        val fixture = Fixture().apply {
            matching = TvDanmakuMatchState(query = "查询", subjects = (1..40).map { DanmakuSubject("s-$it", "匹配条目 $it") })
        }
        showPlayer(fixture)
        runOnIdle { fixture.machine.onAction(TvPlayerAction.OpenDialog(TvPlayerDialog.DanmakuMatch)) }
        onNodeWithText("查询").assertIsFocused()
        repeat(18) { key(Key.DirectionDown) }
        onNodeWithText("匹配条目 17").assertIsFocused()
        key(Key.DirectionCenter)
        assertTrue(
            onAllNodes(hasText("返回番剧列表") and isFocused()).fetchSemanticsNodes().size == 1,
            "New matching entry unavailable; focused: ${focusSummary()}",
        )
        runOnIdle { fixture.backDispatcher.onBackPressed() }
        onNodeWithText("匹配条目 17").assertIsFocused()
    }

    @Test
    fun returningFromMatchingMustRestoreTheSelectedProvider() = runAniComposeUiTest {
        val fixture = Fixture().apply {
            options = options.copy(danmakuOrigins = listOf(origin("1"), origin("2")))
        }
        showPlayer(fixture)
        openPanel(TvPlayerPanel.DanmakuSettings)
        repeat(TvDanmakuProperty.entries.size + 6) { key(Key.DirectionDown) }
        onNode(hasText("重新匹配弹幕") and hasText("来源 2")).assertIsFocused()
        key(Key.DirectionCenter)
        runOnIdle { fixture.backDispatcher.onBackPressed() }
        onNode(hasText("重新匹配弹幕") and hasText("来源 2")).assertIsFocused()
        key(Key.DirectionCenter)
        runOnIdle { fixture.options = fixture.options.copy(danmakuOrigins = listOf(origin("1"))) }
        runOnIdle { fixture.backDispatcher.onBackPressed() }
        onNodeWithTag("tv-danmaku-list-button").assertIsFocused()
    }

    @Test
    fun userNavigationDuringSidebarExitMustCancelTheOldReturnFocus() = runAniComposeUiTest {
        val fixture = Fixture()
        showPlayer(fixture)
        openPanel(TvPlayerPanel.DanmakuSettings)
        onNodeWithText("字号").assertIsFocused()
        mainClock.autoAdvance = false
        runOnIdle { fixture.backDispatcher.onBackPressed() }
        mainClock.advanceTimeBy(64)
        key(Key.DirectionLeft)
        val moved = onAllNodes(isFocused()).fetchSemanticsNodes().single()
        val tag = moved.config.getOrElse(SemanticsProperties.TestTag) { "" }
        assertTrue(tag.startsWith("tv-player-"), "Navigation did not move to player: $tag")
        mainClock.advanceTimeBy(500)
        mainClock.autoAdvance = true
        val settled = onAllNodes(isFocused()).fetchSemanticsNodes().single()
        assertEquals(moved.id, settled.id, "Late focus request overwrote user navigation from $tag to ${settled.config}")
    }

    @Test
    fun loginRequirementChangeMustKeepFocusInTheTogetherPanel() = runAniComposeUiTest {
        val fixture = Fixture().apply { together = TvTogetherState(roomName = "room") }
        showPlayer(fixture)
        openPanel(TvPlayerPanel.Together)
        onNodeWithText("room").assertIsFocused()
        runOnIdle { fixture.together = fixture.together.copy(requiresLogin = true) }
        assertTrue(
            onAllNodes(hasText("登录账号") and isFocused()).fetchSemanticsNodes().size == 1,
            "Login entry lost focus: ${focusSummary()}",
        )
    }

    @Test
    fun removedCommentMustHaveAFocusFallbackWhenLeavingItsDetail() = runAniComposeUiTest {
        val fixture = Fixture()
        showPlayer(fixture)
        openPanel(TvPlayerPanel.Comments)
        onNodeWithTag("tv-comment-old").assertIsFocused()
        key(Key.DirectionCenter)
        runOnIdle { fixture.comments.value = PagingData.from(listOf(comment("replacement"))) }
        runOnIdle { fixture.backDispatcher.onBackPressed() }
        assertTrue(
            onNodeWithTag("tv-comment-replacement").fetchSemanticsNode().config[SemanticsProperties.Focused],
            "Replacement comment lost focus: ${focusSummary()}",
        )
    }

    private fun AniComposeUiTest.showPlayer(fixture: Fixture) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        setContent {
            val sketch = remember { Sketch(context) }
            DisposableEffect(sketch) { onDispose { sketch.shutdown() } }
            CompositionLocalProvider(LocalSketch provides sketch) {
                AniTvTheme {
                    val dispatcher = checkNotNull(LocalOnBackPressedDispatcherOwner.current).onBackPressedDispatcher
                    SideEffect { fixture.backDispatcher = dispatcher }
                    TvEpisodeScreen(
                        uiState = TvEpisodeUiState(
                            loadingState = VideoLoadingState.Succeed(false),
                            positionMillis = 20_000,
                            durationMillis = 60_000,
                            options = fixture.options,
                            danmakuMatch = fixture.matching,
                        ),
                        togetherState = fixture.together,
                        onTogetherIntent = {},
                        commentsPager = fixture.comments,
                        presentationState = fixture.machine,
                        actionEvents = emptyFlow(),
                        onIntent = fixture::onIntent,
                        video = { Box(it.background(Color.Black)) },
                        resolver = {},
                        danmaku = {},
                    )
                }
            }
        }
    }

    private fun AniComposeUiTest.openPanel(panel: TvPlayerPanel) {
        onNodeWithTag("tv-player-seekbar").assertIsFocused()
        key(Key.DirectionUp)
        repeat(TvPlayerPanel.entries.indexOf(panel)) { key(Key.DirectionRight) }
        key(Key.DirectionCenter)
    }

    private fun AniComposeUiTest.key(key: Key) {
        onRoot().performKeyInput { pressKey(key) }
        waitForIdle()
    }

    private fun AniComposeUiTest.focusSummary() =
        onAllNodes(isFocused()).fetchSemanticsNodes().joinToString { it.config.toString() }

    companion object {
        private fun origin(id: String) = TvDanmakuOrigin(
            DanmakuServiceId(id), DanmakuProviderId(id), "来源 $id", "已匹配", true, 0, true,
        )

        private fun comment(id: String) = EpisodeComment(
            stableId = id, source = EpisodeCommentSource.BANGUMI,
            sourceCommentId = id, commentId = id, episodeId = 1,
            createdAt = 1_788_739_200_000L, content = "评论正文 $id",
            author = UserInfo(id, "viewer", "观众"),
        )
    }
}
