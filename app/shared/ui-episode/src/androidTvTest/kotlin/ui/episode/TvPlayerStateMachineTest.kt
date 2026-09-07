/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.leanback.ui.episode

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TvPlayerStateMachineTest {
    private class Fixture {
        var playback = TvPlaybackSnapshot(playing = true, positionMillis = 20_000, durationMillis = 60_000)
        val commands = mutableListOf<TvPlaybackCommand>()
        val machine = TvPlayerStateMachine({ playback }) { command ->
            commands += command
            when (command) {
                TvPlaybackCommand.TogglePause -> playback = playback.copy(playing = !playback.playing)
                else -> Unit
            }
        }
        val state get() = machine.states.value

        fun key(key: TvRemoteKey, down: Boolean = true, repeats: Int = 0, time: Long = 1_000) =
            machine.onIntent(TvEpisodeIntent.RemoteKey(key, down, repeats, time, true, false, false))
    }

    @Test
    fun `arrows only preview and confirm commits once`() {
        val f = Fixture()
        f.key(TvRemoteKey.Right, time = 100) // Includes events near boot; no phantom previous press.
        assertTrue(f.commands.isEmpty())
        assertEquals(25_000L, f.state.scrubMillis)
        f.key(TvRemoteKey.Right, time = 500)
        assertEquals(30_000L, f.state.scrubMillis)
        assertTrue(f.commands.isEmpty())
        f.key(TvRemoteKey.Confirm)
        assertEquals(TvPlaybackCommand.SeekTo(30_000), f.commands.last())
        assertNull(f.state.scrubMillis)
    }

    @Test
    fun `back cancels seek preview without changing playback`() {
        val f = Fixture()
        f.key(TvRemoteKey.Right)
        f.key(TvRemoteKey.Right, time = 1_100)
        f.machine.onIntent(TvEpisodeIntent.Back)
        assertNull(f.state.scrubMillis)
        assertTrue(f.state.controlsVisible)
        assertTrue(f.commands.isEmpty())
    }

    @Test
    fun `preview stays inside video duration`() {
        val f = Fixture()
        f.playback = f.playback.copy(positionMillis = 55_000)
        f.key(TvRemoteKey.Right)
        f.key(TvRemoteKey.Right, time = 1_100)
        assertEquals(60_000L, f.state.scrubMillis)
        repeat(20) { f.key(TvRemoteKey.Left) }
        assertEquals(0L, f.state.scrubMillis)
    }

    @Test
    fun `long confirm releases original speed without toggling pause`() {
        val f = Fixture()
        f.machine.onIntent(TvEpisodeIntent.Back)
        f.key(TvRemoteKey.Confirm)
        f.key(TvRemoteKey.Confirm, repeats = 1)
        f.key(TvRemoteKey.Confirm, repeats = 2)
        assertTrue(f.state.speedHolding)
        f.key(TvRemoteKey.Confirm, down = false)
        assertFalse(f.state.speedHolding)
        assertEquals(
            listOf<TvPlaybackCommand>(TvPlaybackCommand.SpeedHold(true), TvPlaybackCommand.SpeedHold(false)),
            f.commands,
        )
    }

    @Test
    fun `leaving composition releases held speed and stray key up does not pause`() {
        val f = Fixture()
        f.machine.onIntent(TvEpisodeIntent.Back)
        f.key(TvRemoteKey.Confirm)
        f.key(TvRemoteKey.Confirm, repeats = 1)
        f.machine.onIntent(TvEpisodeIntent.ReleaseHeldSpeed)
        f.key(TvRemoteKey.Confirm, down = false)
        assertEquals(
            listOf<TvPlaybackCommand>(TvPlaybackCommand.SpeedHold(true), TvPlaybackCommand.SpeedHold(false)),
            f.commands,
        )
    }

    @Test
    fun `short confirm pauses and shows controls`() {
        val f = Fixture()
        f.machine.onIntent(TvEpisodeIntent.Back)
        f.key(TvRemoteKey.Confirm)
        assertTrue(f.commands.isEmpty())
        f.key(TvRemoteKey.Confirm, down = false)
        assertEquals(listOf<TvPlaybackCommand>(TvPlaybackCommand.TogglePause), f.commands)
        assertTrue(f.state.controlsVisible)
    }

    @Test
    fun `source replaces open panel and back returns to controller`() {
        val f = Fixture()
        f.machine.onIntent(TvEpisodeIntent.TogglePanel(TvPlayerPanel.DanmakuSettings))
        f.machine.onIntent(TvEpisodeIntent.OpenSourceDialog)
        assertTrue(f.machine.onIntent(TvEpisodeIntent.Back))
        assertFalse(f.state.sourceDialogVisible)
        assertNull(f.state.activePanel)
        assertTrue(f.state.controlsVisible)
        f.machine.onIntent(TvEpisodeIntent.Back)
        assertFalse(f.state.controlsVisible)
        assertFalse(f.machine.onIntent(TvEpisodeIntent.Back))
    }

    @Test
    fun `source dialog awaiting focus consumes navigation keys without operating player`() {
        val f = Fixture()
        f.machine.onIntent(TvEpisodeIntent.OpenSourceDialog)
        assertTrue(f.key(TvRemoteKey.Right))
        assertTrue(f.key(TvRemoteKey.Confirm))
        assertTrue(f.commands.isEmpty())
        assertFalse(
            f.machine.onIntent(
                TvEpisodeIntent.RemoteKey(
                    TvRemoteKey.Right,
                    true,
                    0,
                    1_000,
                    false,
                    false,
                    true,
                ),
            ),
        )
    }

    @Test
    fun `auto hide respects pause panels dialog and seek preview`() {
        val f = Fixture()
        f.playback = f.playback.copy(playing = false)
        f.machine.autoHide()
        assertTrue(f.state.controlsVisible)
        f.playback = f.playback.copy(playing = true)
        f.machine.onIntent(TvEpisodeIntent.TogglePanel(TvPlayerPanel.Comments))
        f.machine.autoHide()
        assertTrue(f.state.controlsVisible)
        f.machine.onIntent(TvEpisodeIntent.Back)
        f.machine.onIntent(TvEpisodeIntent.OpenSourceDialog)
        f.machine.autoHide()
        assertTrue(f.state.controlsVisible)
        f.machine.onIntent(TvEpisodeIntent.Back)
        f.key(TvRemoteKey.Right)
        f.key(TvRemoteKey.Right, time = 1_100)
        f.machine.autoHide()
        assertTrue(f.state.controlsVisible)
        f.machine.onIntent(TvEpisodeIntent.Back)
        f.machine.autoHide()
        assertFalse(f.state.controlsVisible)
    }

    @Test
    fun `explicit media play and pause keys do not toggle in the opposite direction`() {
        val f = Fixture()
        f.key(TvRemoteKey.Play)
        assertTrue(f.commands.isEmpty())
        f.key(TvRemoteKey.Pause)
        f.key(TvRemoteKey.Pause)
        assertEquals(1, f.commands.size)
        f.key(TvRemoteKey.Play)
        f.key(TvRemoteKey.Play, repeats = 1)
        assertEquals(2, f.commands.size)
        assertTrue(f.playback.playing)
    }

    @Test
    fun `menu closes source panel and restores controller without playback commands`() {
        val f = Fixture()
        f.machine.onIntent(TvEpisodeIntent.OpenSourceDialog)
        f.key(TvRemoteKey.Menu)
        assertFalse(f.state.sourceDialogVisible)
        assertTrue(f.state.controlsVisible)
        assertTrue(f.commands.isEmpty())
    }

    @Test
    fun `unknown duration cannot enter seek preview`() {
        val f = Fixture()
        f.playback = f.playback.copy(durationMillis = 0)
        f.key(TvRemoteKey.Right)
        assertNull(f.state.scrubMillis)
        assertTrue(f.commands.isEmpty())
    }

    @Test
    fun `speed dialog traps playback navigation and prevents auto hide`() {
        val f = Fixture()
        f.machine.onIntent(TvEpisodeIntent.OpenDialog(TvPlayerDialog.Speed))
        assertFalse(f.key(TvRemoteKey.Right))
        f.machine.autoHide()
        assertTrue(f.state.controlsVisible)
        assertTrue(f.commands.isEmpty())
        f.machine.onIntent(TvEpisodeIntent.Back)
        assertNull(f.state.dialog)
    }

    @Test
    fun `hidden controller arrows also preview without seeking`() {
        val f = Fixture()
        f.machine.onIntent(TvEpisodeIntent.Back)
        f.key(TvRemoteKey.Left)
        assertEquals(15_000L, f.state.scrubMillis)
        assertTrue(f.state.controlsVisible)
        f.machine.onIntent(TvEpisodeIntent.Back)
        assertTrue(f.commands.isEmpty())
    }

    @Test
    fun `up from operation bar always targets timeline`() = runBlocking {
        val f = Fixture()
        assertTrue(f.machine.onIntent(TvEpisodeIntent.RemoteKey(TvRemoteKey.Up, true, 0, 1_000, false, true, false)))
        assertEquals(TvPlayerFocusRequest.SeekBar, f.machine.focusRequests.first())
        assertTrue(f.commands.isEmpty())
    }

    @Test
    fun `selecting episode hides controls and selecting source returns to controls`() {
        val f = Fixture()
        f.machine.episodeSelected()
        assertFalse(f.state.controlsVisible)
        f.machine.onIntent(TvEpisodeIntent.OpenSourceDialog)
        f.machine.mediaSelected()
        assertFalse(f.state.sourceDialogVisible)
        assertTrue(f.state.controlsVisible)
    }

    @Test
    fun `episode button opens inline strip and back restores its button`() = runBlocking {
        val f = Fixture()
        f.machine.onIntent(TvEpisodeIntent.TogglePanel(TvPlayerPanel.Comments))
        f.machine.onIntent(TvEpisodeIntent.ToggleEpisodeStrip)
        assertTrue(f.state.stripExpanded)
        assertNull(f.state.activePanel)
        assertNull(f.state.dialog)
        f.machine.autoHide()
        assertTrue(f.state.controlsVisible)
        f.machine.onIntent(TvEpisodeIntent.Back)
        assertFalse(f.state.stripExpanded)
        assertTrue(f.state.controlsVisible)
        assertEquals(TvPlayerFocusRequest.EpisodesButton, f.machine.focusRequests.first())
        assertTrue(f.commands.isEmpty())
    }

    @Test
    fun `episode actions preserve strip while modal takes focus`() = runBlocking {
        val f = Fixture()
        f.machine.onIntent(TvEpisodeIntent.ToggleEpisodeStrip)
        f.machine.onIntent(TvEpisodeIntent.OpenDialog(TvPlayerDialog.EpisodeActions))
        f.machine.onIntent(TvEpisodeIntent.StripFocusLost)
        assertTrue(f.state.stripExpanded)
        assertEquals(TvPlayerDialog.EpisodeActions, f.state.dialog)
        f.machine.onIntent(TvEpisodeIntent.Back)
        assertTrue(f.state.stripExpanded)
        assertNull(f.state.dialog)
        assertEquals(TvPlayerFocusRequest.DialogButton(TvPlayerDialog.EpisodeActions), f.machine.focusRequests.first())
        f.machine.onIntent(TvEpisodeIntent.StripFocusLost)
        assertFalse(f.state.stripExpanded)
    }

    @Test
    fun `danmaku dialogs keep settings and return focus to their entry`() = runBlocking {
        for (dialog in listOf(TvPlayerDialog.DanmakuList, TvPlayerDialog.DanmakuMatch)) {
            val f = Fixture()
            f.machine.onIntent(TvEpisodeIntent.TogglePanel(TvPlayerPanel.DanmakuSettings))
            f.machine.onIntent(TvEpisodeIntent.OpenDialog(dialog))
            assertEquals(TvPlayerPanel.DanmakuSettings, f.state.activePanel)
            f.machine.onIntent(TvEpisodeIntent.Back)
            assertNull(f.state.dialog)
            assertEquals(TvPlayerPanel.DanmakuSettings, f.state.activePanel)
            assertEquals(TvPlayerFocusRequest.DialogButton(dialog), f.machine.focusRequests.first())
        }
    }

    @Test
    fun `source speed and menu replace an expanded strip`() {
        for (intent in listOf(TvEpisodeIntent.OpenSourceDialog, TvEpisodeIntent.OpenDialog(TvPlayerDialog.Speed))) {
            val f = Fixture()
            f.machine.onIntent(TvEpisodeIntent.ToggleEpisodeStrip)
            f.machine.onIntent(intent)
            assertFalse(f.state.stripExpanded)
        }
        val f = Fixture()
        f.machine.onIntent(TvEpisodeIntent.ToggleEpisodeStrip)
        f.key(TvRemoteKey.Menu)
        assertFalse(f.state.stripExpanded)
        assertTrue(f.state.controlsVisible)
        assertTrue(f.commands.isEmpty())
    }

    @Test
    fun `sidebar opening blocks stale timeline focus from seeking`() {
        val f = Fixture()
        f.machine.onIntent(TvEpisodeIntent.TogglePanel(TvPlayerPanel.Comments))
        assertTrue(f.state.sidebarVisible)
        assertTrue(f.key(TvRemoteKey.Right))
        assertTrue(f.key(TvRemoteKey.Confirm))
        assertNull(f.state.scrubMillis)
        assertTrue(f.commands.isEmpty())
        // Once inside the sidebar, directional keys belong to its list or adjustment controls.
        assertFalse(
            f.machine.onIntent(
                TvEpisodeIntent.RemoteKey(
                    TvRemoteKey.Right, true, 0, 1_000, false, false, false, sidebarFocused = true,
                )
            )
        )
        assertTrue(f.commands.isEmpty())
    }

    @Test
    fun `sidebar back restores its exact chip and keeps controls visible`() = runBlocking {
        for (panel in listOf(
            TvPlayerPanel.Comments,
            TvPlayerPanel.DanmakuSettings,
            TvPlayerPanel.Together
        )) {
            val f = Fixture()
            f.machine.onIntent(TvEpisodeIntent.TogglePanel(panel))
            f.machine.autoHide()
            assertTrue(f.state.sidebarVisible)
            assertTrue(f.machine.onIntent(TvEpisodeIntent.Back))
            assertFalse(f.state.sidebarVisible)
            assertTrue(f.state.controlsVisible)
            assertEquals(TvPlayerFocusRequest.PanelChip(panel), f.machine.focusRequests.first())
            assertTrue(f.commands.isEmpty())
        }
    }

    @Test
    fun `opening a sidebar replaces source dialog and seek preview`() {
        val f = Fixture()
        f.key(TvRemoteKey.Right)
        f.machine.onIntent(TvEpisodeIntent.OpenSourceDialog)
        f.machine.onIntent(TvEpisodeIntent.TogglePanel(TvPlayerPanel.DanmakuSettings))
        assertTrue(f.state.sidebarVisible)
        assertFalse(f.state.sourceDialogVisible)
        assertNull(f.state.scrubMillis)
        assertNull(f.state.dialog)
        assertFalse(f.state.canAutoHide)
        assertTrue(f.commands.isEmpty())
    }

    @Test
    fun `down from operation bar opens recommendations directly`() = runBlocking {
        val f = Fixture()
        assertTrue(f.machine.onIntent(TvEpisodeIntent.RemoteKey(TvRemoteKey.Down, true, 0, 1_000, false, true, false)))
        assertEquals(TvPlayerFocusRequest.Recommendations, f.machine.focusRequests.first())
        assertFalse(f.state.stripExpanded)
        assertTrue(f.state.recommendationsVisible)
        assertTrue(f.commands.isEmpty())
    }

    @Test
    fun `recommendations replace controller content and prevent auto hide`() = runBlocking {
        val f = Fixture()
        f.machine.onIntent(TvEpisodeIntent.ToggleEpisodeStrip)
        f.machine.onIntent(TvEpisodeIntent.OpenRecommendations)
        assertTrue(f.state.recommendationsVisible)
        assertFalse(f.state.stripExpanded)
        assertFalse(f.state.sidebarVisible)
        assertFalse(f.state.canAutoHide)
        assertEquals(TvPlayerFocusRequest.Recommendations, f.machine.focusRequests.first())
        f.machine.autoHide()
        assertTrue(f.state.controlsVisible)
        assertTrue(f.state.recommendationsVisible)
        assertTrue(f.commands.isEmpty())
    }

    @Test
    fun `up and back leave recommendations and restore timeline focus`() = runBlocking {
        for (exit in listOf(
            TvEpisodeIntent.Back,
            TvEpisodeIntent.CloseRecommendations,
            TvEpisodeIntent.RemoteKey(TvRemoteKey.Up, true, 0, 1_000, false, false, false, recommendationsFocused = true),
        )) {
            val f = Fixture()
            f.machine.onIntent(TvEpisodeIntent.OpenRecommendations)
            assertEquals(TvPlayerFocusRequest.Recommendations, f.machine.focusRequests.first())
            assertTrue(f.machine.onIntent(exit))
            assertFalse(f.state.recommendationsVisible)
            assertTrue(f.state.controlsVisible)
            assertEquals(TvPlayerFocusRequest.SeekBar, f.machine.focusRequests.first())
            assertTrue(f.commands.isEmpty())
        }
    }

    @Test
    fun `recommendation transition cannot seek or toggle playback through stale controller focus`() {
        val f = Fixture()
        f.machine.onIntent(TvEpisodeIntent.OpenRecommendations)
        for (key in listOf(TvRemoteKey.Left, TvRemoteKey.Right, TvRemoteKey.Confirm)) {
            assertTrue(f.key(key))
            assertTrue(f.key(key, down = false))
        }
        assertNull(f.state.scrubMillis)
        assertTrue(f.commands.isEmpty())
        assertFalse(f.machine.onIntent(
            TvEpisodeIntent.RemoteKey(TvRemoteKey.Right, true, 0, 1_000, false, false, false, recommendationsFocused = true),
        ))
    }

    @Test
    fun `source panel dialog episode strip and menu replace recommendations`() {
        for (intent in listOf(
            TvEpisodeIntent.OpenSourceDialog,
            TvEpisodeIntent.TogglePanel(TvPlayerPanel.Comments),
            TvEpisodeIntent.OpenDialog(TvPlayerDialog.Speed),
            TvEpisodeIntent.ToggleEpisodeStrip,
            TvEpisodeIntent.RemoteKey(TvRemoteKey.Menu, true, 0, 1_000, false, false, false),
        )) {
            val f = Fixture()
            f.machine.onIntent(TvEpisodeIntent.OpenRecommendations)
            f.machine.onIntent(intent)
            assertFalse(f.state.recommendationsVisible)
            assertTrue(f.state.controlsVisible)
        }
    }
}
