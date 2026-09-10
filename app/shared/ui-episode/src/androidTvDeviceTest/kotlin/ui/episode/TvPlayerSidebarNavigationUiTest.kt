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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.emptyFlow
import me.him188.ani.app.domain.player.VideoLoadingState
import me.him188.ani.app.ui.framework.AniComposeUiTest
import me.him188.ani.app.ui.framework.runAniComposeUiTest
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.tv_player_adjusting
import me.him188.ani.danmaku.api.DanmakuContent
import me.him188.ani.danmaku.api.DanmakuInfo
import me.him188.ani.danmaku.api.DanmakuLocation
import me.him188.ani.danmaku.api.DanmakuServiceId
import me.him188.ani.danmaku.api.provider.DanmakuMatchMethod
import me.him188.ani.danmaku.api.provider.DanmakuProviderId
import me.him188.ani.danmaku.ui.DanmakuPresentation
import me.him188.ani.leanback.ui.episode.danmaku.TvDanmakuOrigin
import me.him188.ani.leanback.ui.episode.danmaku.TvDanmakuProperty
import me.him188.ani.leanback.ui.episode.presentation.TvPlaybackCommand
import me.him188.ani.leanback.ui.episode.presentation.TvPlaybackSnapshot
import me.him188.ani.leanback.ui.episode.presentation.TvPlayerPanel
import me.him188.ani.leanback.ui.episode.presentation.TvPlayerPresentationState
import me.him188.ani.leanback.ui.foundation.theme.AniTvTheme
import me.him188.ani.leanback.ui.watchtogether.TvTogetherIntent
import me.him188.ani.leanback.ui.watchtogether.TvTogetherState
import org.openani.mediamp.MediaStatus
import org.openani.mediamp.PlayerState
import java.io.File
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
        val machine = TvPlayerPresentationState({ TvPlaybackSnapshot(PlayerState(MediaStatus.Ready, true, false), 20_000, 60_000) }, commands::add)
        var panel by mutableStateOf(TvPlayerPanelState())
        lateinit var backDispatcher: OnBackPressedDispatcher

        fun onIntent(intent: TvEpisodeIntent): Boolean {
            intents += intent
            return true
        }
    }

    @Test
    fun danmakuSettingsCloseOnBackAndLeftFromTheFocusedItem() = runAniComposeUiTest {
        val fixture = Fixture()
        showPlayer(fixture)
        openPanel(TvPlayerPanel.DanmakuSettings)
        onNodeWithTag("tv-danmaku-property-FontSize").assertIsFocused()
        runOnIdle { fixture.backDispatcher.onBackPressed() }
        assertPanelClosed(TvPlayerPanel.DanmakuSettings)

        key(Key.DirectionCenter)
        onNodeWithTag("tv-danmaku-property-FontSize").assertIsFocused()
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
        onNodeWithTag("tv-danmaku-property-FontSize").assertIsFocused()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, playerTestString(Lang.tv_player_adjusting)))
        key(Key.DirectionLeft)
        key(Key.DirectionRight)
        assertEquals(
            listOf(-1, 1),
            fixture.intents.filterIsInstance<TvEpisodeIntent.AdjustDanmaku>().map { it.direction },
        )
        runOnIdle { fixture.backDispatcher.onBackPressed() }
        onNodeWithTag("tv-danmaku-property-FontSize").assertIsFocused()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, ""))
        key(Key.DirectionLeft)
        assertPanelClosed(TvPlayerPanel.DanmakuSettings)
        assertTrue(fixture.commands.isEmpty())
    }

    @Test
    fun timingAdjustmentStillAppliesImmediatelyAndCanReset() = runAniComposeUiTest {
        val origin = TvDanmakuOrigin(
            DanmakuServiceId("test"), DanmakuProviderId("test"), DanmakuMatchMethod.NoMatch, 0, true, 0, false,
        )
        val fixture = Fixture(options = TvPlayerOptionsState(danmakuOrigins = listOf(origin)))
        showPlayer(fixture)
        openPanel(TvPlayerPanel.DanmakuSettings)
        repeat(TvDanmakuProperty.entries.size + 1) { key(Key.DirectionDown) }
        onNodeWithTag("tv-danmaku-timing-test").assertIsFocused()
        key(Key.DirectionCenter)
        key(Key.DirectionLeft)
        key(Key.DirectionRight)
        key(Key.DirectionCenter)
        assertEquals(
            listOf(-500L, 500L, null),
            fixture.intents.filterIsInstance<TvEpisodeIntent.ShiftDanmakuSource>().map { it.deltaMillis },
        )
        onNodeWithTag("tv-danmaku-timing-test").assertIsFocused()
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
        onNodeWithTag("tv-danmaku-list-empty").assertIsFocused()
        key(Key.DirectionLeft)
        onNodeWithTag("tv-danmaku-list-empty").assertIsFocused()
        runOnIdle { fixture.backDispatcher.onBackPressed() }
        onNodeWithTag("tv-danmaku-list-button").assertIsFocused()
        key(Key.DirectionLeft)
        assertPanelClosed(TvPlayerPanel.DanmakuSettings)
        assertTrue(fixture.commands.isEmpty())
    }

    @Test
    fun loadingDanmakuListTransfersFocusToContentAndStillReturnsToSettings() = runAniComposeUiTest {
        val fixture = Fixture().apply { panel = TvPlayerPanelState(danmakuLoading = true) }
        showPlayer(fixture)
        openDanmakuList()
        onNodeWithTag("tv-danmaku-list-loading").assertIsDisplayed()
        onNodeWithTag("tv-danmaku-placeholder-0", useUnmergedTree = true).assertIsFocused().assertHasNoClickAction()
        onNodeWithTag("tv-danmaku-placeholder-1", useUnmergedTree = true).assertIsDisplayed().assertHasNoClickAction()
        onNodeWithTag("tv-danmaku-list-empty").assertDoesNotExist()
        saveScreenshot("danmaku-loading-skeleton")
        runOnIdle { fixture.backDispatcher.onBackPressed() }
        onNodeWithTag("tv-danmaku-list-button").assertIsFocused()
        key(Key.DirectionCenter)
        onNodeWithTag("tv-danmaku-placeholder-0", useUnmergedTree = true).assertIsFocused()
        runOnIdle { fixture.panel = TvPlayerPanelState(danmaku = listOf(danmaku(2), danmaku(1))) }
        onNodeWithText("弹幕 2").assertIsFocused()
        onNodeWithTag("tv-danmaku-list-loading").assertDoesNotExist()
        runOnIdle { fixture.panel = fixture.panel.copy(danmakuLoading = true) }
        onNodeWithText("弹幕 2").assertIsFocused()
        onNodeWithTag("tv-danmaku-list-loading").assertDoesNotExist()
        runOnIdle { fixture.backDispatcher.onBackPressed() }
        onNodeWithTag("tv-danmaku-list-button").assertIsFocused()
        runOnIdle { fixture.panel = TvPlayerPanelState(danmaku = listOf(danmaku(3))) }
        onNodeWithTag("tv-danmaku-list-button").assertIsFocused()
        key(Key.DirectionLeft)
        assertPanelClosed(TvPlayerPanel.DanmakuSettings)
        assertTrue(fixture.commands.isEmpty())
    }

    @Test
    fun danmakuReloadFromAScrolledListCanFinishEmptyWithoutLosingFocus() = runAniComposeUiTest {
        val fixture = Fixture().apply { panel = TvPlayerPanelState(danmaku = List(30) { danmaku(100 - it) }) }
        showPlayer(fixture)
        openDanmakuList()
        repeat(15) { key(Key.DirectionUp) }
        onNodeWithText("弹幕 85").assertIsFocused()
        runOnIdle { fixture.panel = TvPlayerPanelState(danmakuLoading = true) }
        onNodeWithTag("tv-danmaku-placeholder-0", useUnmergedTree = true).assertIsFocused()
        runOnIdle { fixture.panel = TvPlayerPanelState() }
        onNodeWithTag("tv-danmaku-list-empty").assertIsFocused()
        onNodeWithTag("tv-danmaku-list-loading").assertDoesNotExist()
        runOnIdle { fixture.backDispatcher.onBackPressed() }
        onNodeWithTag("tv-danmaku-list-button").assertIsFocused()
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
        onNodeWithTag("tv-danmaku-list-empty").assertIsFocused()
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
        onNodeWithTag("tv-danmaku-list-empty").assertIsFocused()
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
        onNodeWithTag("tv-together-login").assertIsFocused()
        key(Key.DirectionLeft)
        assertPanelClosed(TvPlayerPanel.Together)
        key(Key.DirectionCenter)
        onNodeWithTag("tv-together-login").assertIsFocused()
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
        onNodeWithTag("tv-together-playback").assertIsFocused()
        key(Key.DirectionDown)
        onNodeWithTag("tv-together-leave").assertIsFocused()
        key(Key.DirectionCenter)
        onNodeWithTag("tv-together-stay").assertIsFocused()
        key(Key.DirectionLeft)
        onNodeWithTag("tv-together-stay").assertIsFocused()
        runOnIdle { fixture.backDispatcher.onBackPressed() }
        onNodeWithTag("tv-together-leave").assertIsFocused()
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
        onNodeWithTag("tv-together-submit").assertIsFocused()
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
                val dispatcher = checkNotNull(LocalOnBackPressedDispatcherOwner.current).onBackPressedDispatcher
                SideEffect { fixture.backDispatcher = dispatcher }
                TvEpisodeScreen(
                    uiState = TvEpisodeUiState(
                        loadingState = VideoLoadingState.Succeed(false),
                        positionMillis = 20_000,
                        durationMillis = 60_000,
                        options = fixture.options,
                        panel = fixture.panel,
                    ),
                    togetherState = fixture.together,
                    onTogetherIntent = fixture.togetherIntents::add,
                    commentsPager = emptyFlow(),
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

    private fun AniComposeUiTest.saveScreenshot(name: String) {
        val bitmap = onRoot().captureToImage().asAndroidBitmap()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.getExternalFilesDir("screenshots"), "$name.png")
        file.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
    }
}
