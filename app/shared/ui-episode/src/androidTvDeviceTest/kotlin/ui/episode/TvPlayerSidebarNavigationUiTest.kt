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
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import kotlinx.coroutines.flow.emptyFlow
import me.him188.ani.app.domain.player.VideoLoadingState
import me.him188.ani.app.ui.framework.AniComposeUiTest
import me.him188.ani.app.ui.framework.runAniComposeUiTest
import me.him188.ani.danmaku.api.DanmakuContent
import me.him188.ani.danmaku.api.DanmakuInfo
import me.him188.ani.danmaku.api.DanmakuLocation
import me.him188.ani.danmaku.api.DanmakuServiceId
import me.him188.ani.danmaku.api.provider.DanmakuProviderId
import me.him188.ani.danmaku.ui.DanmakuPresentation
import me.him188.ani.leanback.ui.foundation.theme.AniTvTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TvPlayerSidebarNavigationUiTest {
    private class Fixture(
        val together: TvTogetherState = TvTogetherState(requiresLogin = true),
        val options: TvPlayerOptionsState = TvPlayerOptionsState(),
    ) {
        val commands = mutableListOf<TvPlaybackCommand>()
        val intents = mutableListOf<TvEpisodeIntent>()
        val togetherIntents = mutableListOf<TvTogetherIntent>()
        val machine = TvPlayerStateMachine({ TvPlaybackSnapshot(true, 20_000, 60_000) }, commands::add)
        var panel by mutableStateOf(TvPlayerPanelState())
        lateinit var backDispatcher: OnBackPressedDispatcher

        fun onIntent(intent: TvEpisodeIntent): Boolean {
            intents += intent
            return machine.onIntent(intent)
        }
    }

    @Test
    fun danmakuSettingsCloseOnBackAndLeftFromTheFocusedItem() = runAniComposeUiTest {
        val fixture = Fixture()
        showPlayer(fixture)
        openPanel(TvPlayerPanel.DanmakuSettings)
        onNodeWithText("字号").assertIsFocused()
        runOnIdle { fixture.backDispatcher.onBackPressed() }
        assertPanelClosed(TvPlayerPanel.DanmakuSettings)

        key(Key.DirectionCenter)
        onNodeWithText("字号").assertIsFocused()
        key(Key.DirectionLeft)
        assertPanelClosed(TvPlayerPanel.DanmakuSettings)
        assertTrue(fixture.intents.none { it is TvEpisodeIntent.AdjustDanmaku })
        assertTrue(fixture.commands.isEmpty())
    }

    @Test
    fun danmakuValuesCanStillDecreaseAfterEnteringAdjustment() = runAniComposeUiTest {
        val fixture = Fixture()
        showPlayer(fixture)
        openPanel(TvPlayerPanel.DanmakuSettings)
        key(Key.DirectionCenter)
        onNodeWithText("字号").assertIsFocused()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "调整中"))
        key(Key.DirectionLeft)
        key(Key.DirectionRight)
        assertEquals(
            listOf(-1, 1),
            fixture.intents.filterIsInstance<TvEpisodeIntent.AdjustDanmaku>().map { it.direction },
        )
        runOnIdle { fixture.backDispatcher.onBackPressed() }
        onNodeWithText("字号").assertIsFocused()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, ""))
        key(Key.DirectionLeft)
        assertPanelClosed(TvPlayerPanel.DanmakuSettings)
        assertTrue(fixture.commands.isEmpty())
    }

    @Test
    fun timingAdjustmentStillAppliesImmediatelyAndCanReset() = runAniComposeUiTest {
        val origin = TvDanmakuOrigin(
            DanmakuServiceId("test"), DanmakuProviderId("test"), "测试来源", "已匹配", true, 0, false,
        )
        val fixture = Fixture(options = TvPlayerOptionsState(danmakuOrigins = listOf(origin)))
        showPlayer(fixture)
        openPanel(TvPlayerPanel.DanmakuSettings)
        repeat(TvDanmakuProperty.entries.size + 1) { key(Key.DirectionDown) }
        onNodeWithText("时间校准").assertIsFocused()
        key(Key.DirectionCenter)
        key(Key.DirectionLeft)
        key(Key.DirectionRight)
        key(Key.DirectionCenter)
        assertEquals(
            listOf(-500L, 500L, null),
            fixture.intents.filterIsInstance<TvEpisodeIntent.ShiftDanmakuSource>().map { it.deltaMillis },
        )
        onNodeWithText("时间校准").assertIsFocused()
        key(Key.DirectionLeft)
        assertPanelClosed(TvPlayerPanel.DanmakuSettings)
        assertTrue(fixture.commands.isEmpty())
    }

    @Test
    fun holdingLeftAfterClosingDoesNotNavigateOrSeekBehindThePanel() = runAniComposeUiTest {
        val fixture = Fixture()
        showPlayer(fixture)
        openPanel(TvPlayerPanel.DanmakuSettings)
        onRoot().performKeyInput { keyDown(Key.DirectionLeft) }
        waitForIdle()
        assertPanelClosed(TvPlayerPanel.DanmakuSettings)
        onRoot().performKeyInput {
            advanceEventTime(2_000)
            keyUp(Key.DirectionLeft)
        }
        assertPanelClosed(TvPlayerPanel.DanmakuSettings)
        assertEquals(1, fixture.intents.count { it == TvEpisodeIntent.Back })
        assertTrue(fixture.commands.isEmpty())
        key(Key.DirectionLeft)
        onNodeWithTag("tv-player-chip-Comments").assertIsFocused()
    }

    @Test
    fun danmakuListReturnsToItsSettingsEntryBeforeThePanelCanClose() = runAniComposeUiTest {
        val fixture = Fixture()
        showPlayer(fixture)
        openPanel(TvPlayerPanel.DanmakuSettings)
        repeat(TvDanmakuProperty.entries.size) { key(Key.DirectionDown) }
        onNodeWithTag("tv-danmaku-list-button").assertIsFocused()
        key(Key.DirectionCenter)
        onNodeWithText("还没有弹幕").assertIsFocused()
        key(Key.DirectionLeft)
        onNodeWithText("还没有弹幕").assertIsFocused()
        runOnIdle { fixture.backDispatcher.onBackPressed() }
        onNodeWithTag("tv-danmaku-list-button").assertIsFocused()
        key(Key.DirectionLeft)
        assertPanelClosed(TvPlayerPanel.DanmakuSettings)
        assertTrue(fixture.commands.isEmpty())
    }

    @Test
    fun danmakuUpdatesKeepTheFocusedItemWhenItsIndexChanges() = runAniComposeUiTest {
        val fixture = Fixture().apply {
            panel = TvPlayerPanelState(danmaku = listOf(danmaku(3), danmaku(2), danmaku(1)))
        }
        showPlayer(fixture)
        openDanmakuList()
        onNodeWithText("弹幕 3").assertIsFocused()
        val firstNodeId = onNodeWithText("弹幕 3").fetchSemanticsNode().id
        runOnIdle { fixture.panel = fixture.panel.copy(danmaku = listOf(danmaku(4)) + fixture.panel.danmaku) }
        onNodeWithText("弹幕 3").assertIsFocused()
        assertEquals(firstNodeId, onNodeWithText("弹幕 3").fetchSemanticsNode().id)

        key(Key.DirectionUp)
        onNodeWithText("弹幕 2").assertIsFocused()
        val focusedNodeId = onNodeWithText("弹幕 2").fetchSemanticsNode().id
        runOnIdle {
            // Repopulation recreates presentations. IDs from different services can overlap.
            fixture.panel = fixture.panel.copy(danmaku = listOf(
                danmaku(5), danmaku(4), danmaku(3),
                danmaku(2, DanmakuServiceId.Dandanplay, "另一来源的弹幕 2"),
                danmaku(2), danmaku(1), danmaku(1),
            ))
        }
        onNodeWithText("弹幕 2").assertIsFocused()
        assertEquals(focusedNodeId, onNodeWithText("弹幕 2").fetchSemanticsNode().id)
        key(Key.DirectionLeft)
        onNodeWithText("弹幕 2").assertIsFocused()
        runOnIdle { fixture.backDispatcher.onBackPressed() }
        onNodeWithTag("tv-danmaku-list-button").assertIsFocused()
        assertTrue(fixture.commands.isEmpty())
    }

    @Test
    fun removingFocusedDanmakuKeepsFocusInTheListIncludingEmptyTransitions() = runAniComposeUiTest {
        val fixture = Fixture().apply {
            panel = TvPlayerPanelState(danmaku = listOf(danmaku(3), danmaku(2), danmaku(1)))
        }
        showPlayer(fixture)
        openDanmakuList()
        key(Key.DirectionUp)
        onNodeWithText("弹幕 2").assertIsFocused()
        runOnIdle { fixture.panel = fixture.panel.copy(danmaku = listOf(danmaku(3), danmaku(1))) }
        onNodeWithText("弹幕 1").assertIsFocused()
        runOnIdle { fixture.panel = fixture.panel.copy(danmaku = emptyList()) }
        onNodeWithText("还没有弹幕").assertIsFocused()
        runOnIdle { fixture.panel = fixture.panel.copy(danmaku = listOf(danmaku(5), danmaku(4))) }
        onNodeWithText("弹幕 5").assertIsFocused()
        runOnIdle { fixture.panel = fixture.panel.copy(danmaku = listOf(danmaku(7), danmaku(6))) }
        onNodeWithText("弹幕 7").assertIsFocused()
        assertTrue(fixture.commands.isEmpty())
    }

    @Test
    fun danmakuUpdatesAfterReturningDoNotStealFocusFromSettingsOrController() = runAniComposeUiTest {
        val fixture = Fixture()
        showPlayer(fixture)
        openDanmakuList()
        onNodeWithText("还没有弹幕").assertIsFocused()
        runOnIdle { fixture.panel = fixture.panel.copy(danmaku = listOf(danmaku(1))) }
        onNodeWithText("弹幕 1").assertIsFocused()
        runOnIdle { fixture.backDispatcher.onBackPressed() }
        onNodeWithTag("tv-danmaku-list-button").assertIsFocused()
        runOnIdle { fixture.panel = fixture.panel.copy(danmaku = listOf(danmaku(2))) }
        onNodeWithTag("tv-danmaku-list-button").assertIsFocused()
        key(Key.DirectionLeft)
        runOnIdle { fixture.panel = fixture.panel.copy(danmaku = listOf(danmaku(3))) }
        assertPanelClosed(TvPlayerPanel.DanmakuSettings)
        assertTrue(fixture.commands.isEmpty())
    }

    @Test
    fun replacingAScrolledDanmakuListKeepsFocusNearThePreviousPosition() = runAniComposeUiTest {
        val fixture = Fixture().apply {
            panel = TvPlayerPanelState(danmaku = List(30) { danmaku(100 - it) })
        }
        showPlayer(fixture)
        openDanmakuList()
        repeat(12) { key(Key.DirectionUp) }
        onNodeWithText("弹幕 88").assertIsFocused()
        runOnIdle { fixture.panel = fixture.panel.copy(danmaku = List(30) { danmaku(200 - it) }) }
        onNodeWithText("弹幕 188").assertIsFocused()
        runOnIdle { fixture.panel = fixture.panel.copy(danmaku = listOf(danmaku(3), danmaku(2), danmaku(1))) }
        onNodeWithText("弹幕 1").assertIsFocused()
        runOnIdle { fixture.backDispatcher.onBackPressed() }
        onNodeWithTag("tv-danmaku-list-button").assertIsFocused()
        assertTrue(fixture.commands.isEmpty())
    }

    @Test
    fun togetherTopLevelClosesWithoutActivatingItsFocusedAction() = runAniComposeUiTest {
        val fixture = Fixture()
        showPlayer(fixture)
        openPanel(TvPlayerPanel.Together)
        onNodeWithText("登录账号").assertIsFocused()
        key(Key.DirectionLeft)
        assertPanelClosed(TvPlayerPanel.Together)
        key(Key.DirectionCenter)
        onNodeWithText("登录账号").assertIsFocused()
        runOnIdle { fixture.backDispatcher.onBackPressed() }
        assertPanelClosed(TvPlayerPanel.Together)
        assertTrue(fixture.intents.none { it == TvEpisodeIntent.OpenLogin })
        assertTrue(fixture.commands.isEmpty())
    }

    @Test
    fun togetherLeaveConfirmationReturnsToTheRoomWithoutLeaving() = runAniComposeUiTest {
        val fixture = Fixture(TvTogetherState(joined = true, isHost = true, roomName = "测试房间"))
        showPlayer(fixture)
        openPanel(TvPlayerPanel.Together)
        onNodeWithText("解散房间").assertIsFocused()
        key(Key.DirectionCenter)
        onNodeWithText("确定解散房间").assertIsFocused()
        key(Key.DirectionLeft)
        onNodeWithText("确定解散房间").assertIsFocused()
        runOnIdle { fixture.backDispatcher.onBackPressed() }
        onNodeWithText("解散房间").assertIsFocused()
        key(Key.DirectionLeft)
        assertPanelClosed(TvPlayerPanel.Together)
        assertTrue(fixture.togetherIntents.none { it == TvTogetherIntent.Leave })
        assertTrue(fixture.commands.isEmpty())
    }

    @Test
    fun closingWhileJoiningCancelsTheRequestAndClosesInOnePress() = runAniComposeUiTest {
        val fixture = Fixture(TvTogetherState(joining = true, roomName = "测试房间"))
        showPlayer(fixture)
        openPanel(TvPlayerPanel.Together)
        key(Key.DirectionDown)
        key(Key.DirectionDown)
        onNodeWithText("正在加入… · 取消").assertIsFocused()
        runOnIdle { fixture.backDispatcher.onBackPressed() }
        assertPanelClosed(TvPlayerPanel.Together)
        assertEquals(1, fixture.togetherIntents.count { it == TvTogetherIntent.CancelJoin })
        key(Key.DirectionCenter)
        key(Key.DirectionLeft)
        assertPanelClosed(TvPlayerPanel.Together)
        assertEquals(2, fixture.togetherIntents.count { it == TvTogetherIntent.CancelJoin })
        assertTrue(fixture.commands.isEmpty())
    }

    private fun AniComposeUiTest.showPlayer(fixture: Fixture) {
        setContent {
            AniTvTheme {
                val overlay by fixture.machine.states.collectAsState()
                val dispatcher = checkNotNull(LocalOnBackPressedDispatcherOwner.current).onBackPressedDispatcher
                SideEffect { fixture.backDispatcher = dispatcher }
                TvEpisodeScreen(
                    uiState = TvEpisodeUiState(
                        loadingState = VideoLoadingState.Succeed(false),
                        positionMillis = 20_000,
                        durationMillis = 60_000,
                        overlay = overlay,
                        options = fixture.options,
                        panel = fixture.panel,
                    ),
                    togetherState = fixture.together,
                    onTogetherIntent = fixture.togetherIntents::add,
                    commentsPager = emptyFlow(),
                    focusRequests = fixture.machine.focusRequests,
                    actionEvents = emptyFlow(),
                    onIntent = fixture::onIntent,
                    video = { Box(it.background(Color.Black)) },
                    resolver = {},
                    danmaku = {},
                )
            }
        }
    }

    private fun AniComposeUiTest.openPanel(panel: TvPlayerPanel) {
        onNodeWithTag("tv-player-seekbar").assertIsFocused()
        key(Key.DirectionUp)
        repeat(TvPlayerPanel.entries.indexOf(panel)) { key(Key.DirectionRight) }
        onNodeWithTag("tv-player-chip-${panel.name}").assertIsFocused()
        key(Key.DirectionCenter)
        onNodeWithTag("tv-player-sidebar").assertIsDisplayed()
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Focused))
    }

    private fun AniComposeUiTest.assertPanelClosed(panel: TvPlayerPanel) {
        onNodeWithTag("tv-player-sidebar").assertDoesNotExist()
        onNodeWithTag("tv-player-chip-${panel.name}").assertIsFocused()
    }

    private fun AniComposeUiTest.openDanmakuList() {
        openPanel(TvPlayerPanel.DanmakuSettings)
        repeat(TvDanmakuProperty.entries.size) { key(Key.DirectionDown) }
        onNodeWithTag("tv-danmaku-list-button").assertIsFocused()
        key(Key.DirectionCenter)
    }

    private fun danmaku(
        id: Int,
        serviceId: DanmakuServiceId = DanmakuServiceId.Animeko,
        text: String = "弹幕 $id",
    ) = DanmakuPresentation(
        DanmakuInfo(
            id.toString(), serviceId, "sender",
            DanmakuContent(id * 1_000L, 0xFFFFFF, text, DanmakuLocation.NORMAL),
        ),
        isSelf = false,
    )

    private fun AniComposeUiTest.key(key: Key) {
        onRoot().performKeyInput { pressKey(key) }
        waitForIdle()
    }
}
