/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.tv.ui.episode

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
                is TvPlaybackCommand.SeekBy -> playback =
                    playback.copy(positionMillis = playback.positionMillis + command.deltaMillis)

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
}
