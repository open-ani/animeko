/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.leanback.ui.episode.presentation

import androidx.compose.runtime.saveable.SaverScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import me.him188.ani.datasources.api.topic.UnifiedCollectionType
import me.him188.ani.leanback.ui.episode.TvEpisodeEvent
import me.him188.ani.leanback.ui.episode.TvEpisodeIntent
import me.him188.ani.leanback.ui.episode.playback.TvPlaybackInteractionState
import me.him188.ani.leanback.ui.subject.collection.TvCollectionPrompt
import org.openani.mediamp.MediaStatus
import org.openani.mediamp.PlayerState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TvPlayerPresentationStateTest {
    private val saveScope = object : SaverScope {
        override fun canBeSaved(value: Any): Boolean = true
    }

    private class Fixture {
        var playback = TvPlaybackSnapshot(PlayerState(MediaStatus.Ready, true, false), positionMillis = 20_000, durationMillis = 60_000)
        val commands = mutableListOf<TvPlaybackCommand>()
        private val interaction = TvPlaybackInteractionState()
        val machine = TvPlayerPresentationState(
            playback = { playback },
            playbackInteraction = interaction,
            execute = { command ->
                when (command) {
                    is TvPlaybackCommand.PreviewBy -> interaction.setPreview(
                        ((interaction.scrubMillis ?: playback.positionMillis) + command.deltaMillis).coerceIn(0, playback.durationMillis),
                    )
                    is TvPlaybackCommand.PreviewSeek -> interaction.setPreview(command.positionMillis)
                    else -> {
                        commands += command
                        when (command) {
                            TvPlaybackCommand.TogglePause -> playback = playback.copy(
                                playerState = playback.playerState.copy(playWhenReady = !playback.playerState.playWhenReady),
                            )
                            is TvPlaybackCommand.SpeedHold -> interaction.setSpeedHolding(command.engaged)
                            else -> Unit
                        }
                    }
                }
            },
        )
        val state get() = machine.states.value

        fun key(key: TvRemoteKey, down: Boolean = true, repeats: Int = 0, time: Long = 1_000) =
            machine.onAction(TvPlayerAction.RemoteKey(key, down, repeats, time, true, false, false))
    }

    @Test
    fun `restoration preserves presentation without persisting playback interaction`() {
        val f = Fixture()
        f.machine.episodeChanged(10)
        f.machine.roomChanged(true, "room")
        f.machine.onAction(TvPlayerAction.TogglePanel(TvPlayerPanel.Together))
        f.machine.confirmLeave = true
        f.machine.statsVisible = true
        val saver = TvPlayerPresentationState.saver { TvPlayerPresentationState({ f.playback }, {}) }
        val saved = with(saver) { saveScope.save(f.machine) }
        val restored = checkNotNull(saver.restore(checkNotNull(saved)))
        restored.episodeChanged(10)
        restored.roomChanged(true, "room")
        assertEquals(TvPlayerPanel.Together, restored.states.value.activePanel)
        assertTrue(restored.confirmLeave)
        assertTrue(restored.statsVisible)
        assertNull(restored.states.value.scrubMillis)
        assertFalse(restored.states.value.speedHolding)
        restored.roomChanged(false, "")
        assertFalse(restored.confirmLeave)
    }

    @Test
    fun `restored episode action closes only when its episode changes`() {
        val f = Fixture()
        f.machine.episodeChanged(10)
        f.machine.episodeActionId = 10
        f.machine.onAction(TvPlayerAction.OpenDialog(TvPlayerDialog.EpisodeActions))
        val saver = TvPlayerPresentationState.saver { TvPlayerPresentationState({ f.playback }, {}) }
        val saved = with(saver) { saveScope.save(f.machine) }
        val restored = checkNotNull(saver.restore(checkNotNull(saved)))
        restored.episodeChanged(10)
        assertEquals(TvPlayerDialog.EpisodeActions, restored.states.value.dialog)
        assertEquals(f.state.surfaceId, restored.states.value.surfaceId)
        restored.episodeChanged(11)
        assertNull(restored.states.value.dialog)
    }

    @Test
    fun `late source and collection results cannot affect a reopened surface`() {
        val f = Fixture()
        f.machine.onAction(TvPlayerAction.OpenSourceDialog)
        val sourceRequest = f.state.surfaceId
        f.machine.onAction(TvPlayerAction.Back)
        f.machine.onAction(TvPlayerAction.OpenSourceDialog)
        f.machine.onEvent(TvEpisodeEvent.MediaSelected(sourceRequest))
        assertTrue(f.state.sourceDialogVisible)
        f.machine.onEvent(TvEpisodeEvent.MediaSelected(f.state.surfaceId))
        assertFalse(f.state.sourceDialogVisible)
        f.machine.onAction(TvPlayerAction.TogglePanel(TvPlayerPanel.Collection))
        val collectionRequest = (f.machine.tagRequest(TvEpisodeIntent.SetCollection(UnifiedCollectionType.DONE)) as TvEpisodeIntent.SetCollection).requestId
        f.machine.onAction(TvPlayerAction.Back)
        f.machine.onAction(TvPlayerAction.TogglePanel(TvPlayerPanel.Collection))
        f.machine.onEvent(TvEpisodeEvent.CollectionChanged(UnifiedCollectionType.DONE, collectionRequest))
        assertNull(f.machine.collectionPrompt)
        f.machine.onEvent(TvEpisodeEvent.CollectionChanged(UnifiedCollectionType.DONE, f.state.surfaceId))
        assertEquals(TvCollectionPrompt.MarkAllWatched, f.machine.collectionPrompt)
    }

    @Test
    fun `late episode result cannot close another dialog or a reopened instance`() {
        val f = Fixture()
        f.machine.episodeActionId = 10
        f.machine.onAction(TvPlayerAction.OpenDialog(TvPlayerDialog.EpisodeActions))
        val requestId = f.state.surfaceId
        f.machine.onAction(TvPlayerAction.Back)
        f.machine.onAction(TvPlayerAction.OpenDialog(TvPlayerDialog.Speed))
        f.machine.completeEpisodeAction(10, requestId)
        assertEquals(TvPlayerDialog.Speed, f.state.dialog)
        f.machine.onEvent(TvEpisodeEvent.PlaybackRetried(requestId))
        assertEquals(TvPlayerDialog.Speed, f.state.dialog)
        f.machine.onAction(TvPlayerAction.Back)
        f.machine.onAction(TvPlayerAction.OpenDialog(TvPlayerDialog.EpisodeActions))
        f.machine.completeEpisodeAction(10, requestId)
        assertEquals(TvPlayerDialog.EpisodeActions, f.state.dialog)
        f.machine.completeEpisodeAction(10, f.state.surfaceId)
        assertNull(f.state.dialog)
    }

    @Test
    fun `late matching result cannot close another dialog`() {
        val f = Fixture()
        f.machine.onAction(TvPlayerAction.OpenDialog(TvPlayerDialog.DanmakuMatch))
        val requestId = f.state.surfaceId
        f.machine.onAction(TvPlayerAction.OpenDialog(TvPlayerDialog.Speed))
        f.machine.completeMatching(requestId)
        assertEquals(TvPlayerDialog.Speed, f.state.dialog)
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
        f.machine.onAction(TvPlayerAction.Back)
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
        f.machine.onAction(TvPlayerAction.Back)
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
        f.machine.onAction(TvPlayerAction.Back)
        f.key(TvRemoteKey.Confirm)
        f.key(TvRemoteKey.Confirm, repeats = 1)
        f.machine.releaseHeldSpeed()
        f.key(TvRemoteKey.Confirm, down = false)
        assertEquals(
            listOf<TvPlaybackCommand>(TvPlaybackCommand.SpeedHold(true), TvPlaybackCommand.SpeedHold(false)),
            f.commands,
        )
    }

    @Test
    fun `short confirm pauses and shows controls`() {
        val f = Fixture()
        f.machine.onAction(TvPlayerAction.Back)
        f.key(TvRemoteKey.Confirm)
        assertTrue(f.commands.isEmpty())
        f.key(TvRemoteKey.Confirm, down = false)
        assertEquals(listOf<TvPlaybackCommand>(TvPlaybackCommand.TogglePause), f.commands)
        assertTrue(f.state.controlsVisible)
    }

    @Test
    fun `source replaces open panel and back returns to controller`() {
        val f = Fixture()
        f.machine.onAction(TvPlayerAction.TogglePanel(TvPlayerPanel.DanmakuSettings))
        f.machine.onAction(TvPlayerAction.OpenSourceDialog)
        assertTrue(f.machine.onAction(TvPlayerAction.Back))
        assertFalse(f.state.sourceDialogVisible)
        assertNull(f.state.activePanel)
        assertTrue(f.state.controlsVisible)
        f.machine.onAction(TvPlayerAction.Back)
        assertFalse(f.state.controlsVisible)
        assertFalse(f.machine.onAction(TvPlayerAction.Back))
    }

    @Test
    fun `source dialog awaiting focus consumes navigation keys without operating player`() {
        val f = Fixture()
        f.machine.onAction(TvPlayerAction.OpenSourceDialog)
        assertTrue(f.key(TvRemoteKey.Right))
        assertTrue(f.key(TvRemoteKey.Confirm))
        assertTrue(f.commands.isEmpty())
        assertFalse(
            f.machine.onAction(
                TvPlayerAction.RemoteKey(
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
        f.playback = f.playback.copy(playerState = f.playback.playerState.copy(playWhenReady = false))
        f.machine.autoHide()
        assertTrue(f.state.controlsVisible)
        f.playback = f.playback.copy(playerState = f.playback.playerState.copy(playWhenReady = true))
        f.machine.onAction(TvPlayerAction.TogglePanel(TvPlayerPanel.Comments))
        f.machine.autoHide()
        assertTrue(f.state.controlsVisible)
        f.machine.onAction(TvPlayerAction.Back)
        f.machine.onAction(TvPlayerAction.OpenSourceDialog)
        f.machine.autoHide()
        assertTrue(f.state.controlsVisible)
        f.machine.onAction(TvPlayerAction.Back)
        f.key(TvRemoteKey.Right)
        f.key(TvRemoteKey.Right, time = 1_100)
        f.machine.autoHide()
        assertTrue(f.state.controlsVisible)
        f.machine.onAction(TvPlayerAction.Back)
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
        assertTrue(f.playback.playerState.playWhenReady)
    }

    @Test
    fun `media keys respect playback intent during opening and buffering`() {
        for (playerState in listOf(
            PlayerState(MediaStatus.Opening, true, false),
            PlayerState(MediaStatus.Ready, true, true),
        )) {
            val f = Fixture()
            f.playback = f.playback.copy(playerState = playerState)
            f.key(TvRemoteKey.Play)
            assertTrue(f.commands.isEmpty())
            f.key(TvRemoteKey.Pause)
            f.key(TvRemoteKey.Pause)
            assertEquals(1, f.commands.size)
            assertFalse(f.playback.playerState.playWhenReady)
            f.key(TvRemoteKey.Play)
            f.key(TvRemoteKey.Play, repeats = 1)
            assertEquals(2, f.commands.size)
            assertTrue(f.playback.playerState.playWhenReady)
        }
    }

    @Test
    fun `pausing while opening or buffering restores controls and timeline focus`() = runBlocking {
        for (playerState in listOf(
            PlayerState(MediaStatus.Opening, true, false),
            PlayerState(MediaStatus.Ready, true, true),
        )) {
            val f = Fixture()
            f.playback = f.playback.copy(playerState = playerState)
            f.machine.onAction(TvPlayerAction.Back)
            assertEquals(TvPlayerFocusRequest.Root, f.machine.focusRequests.first())
            f.key(TvRemoteKey.Confirm)
            f.key(TvRemoteKey.Confirm, down = false)
            assertEquals(listOf<TvPlaybackCommand>(TvPlaybackCommand.TogglePause), f.commands)
            assertFalse(f.playback.playerState.playWhenReady)
            assertTrue(f.state.controlsVisible)
            assertEquals(TvPlayerFocusRequest.SeekBar, f.machine.focusRequests.first())
        }
    }

    @Test
    fun `auto hide waits for actual playback and resumes after buffering`() {
        val f = Fixture()
        for (playerState in listOf(
            PlayerState.Initial,
            PlayerState(MediaStatus.Opening, true, false),
            PlayerState(MediaStatus.Ready, true, true),
            PlayerState(MediaStatus.Ready, false, false),
            PlayerState(MediaStatus.Ended, false, false),
        )) {
            f.playback = f.playback.copy(playerState = playerState)
            f.machine.autoHide()
            assertTrue(f.state.controlsVisible)
        }
        f.playback = f.playback.copy(playerState = PlayerState(MediaStatus.Ready, true, false))
        f.machine.autoHide()
        assertFalse(f.state.controlsVisible)
    }

    @Test
    fun `menu closes source panel and restores controller without playback commands`() {
        val f = Fixture()
        f.machine.onAction(TvPlayerAction.OpenSourceDialog)
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
        f.machine.onAction(TvPlayerAction.OpenDialog(TvPlayerDialog.Speed))
        assertFalse(f.key(TvRemoteKey.Right))
        f.machine.autoHide()
        assertTrue(f.state.controlsVisible)
        assertTrue(f.commands.isEmpty())
        f.machine.onAction(TvPlayerAction.Back)
        assertNull(f.state.dialog)
    }

    @Test
    fun `hidden controller arrows also preview without seeking`() {
        val f = Fixture()
        f.machine.onAction(TvPlayerAction.Back)
        f.key(TvRemoteKey.Left)
        assertEquals(15_000L, f.state.scrubMillis)
        assertTrue(f.state.controlsVisible)
        f.machine.onAction(TvPlayerAction.Back)
        assertTrue(f.commands.isEmpty())
    }

    @Test
    fun `up from operation bar always targets timeline`() = runBlocking {
        val f = Fixture()
        assertTrue(f.machine.onAction(TvPlayerAction.RemoteKey(TvRemoteKey.Up, true, 0, 1_000, false, true, false)))
        assertEquals(TvPlayerFocusRequest.SeekBar, f.machine.focusRequests.first())
        assertTrue(f.commands.isEmpty())
    }

    @Test
    fun `selecting episode hides controls and selecting source returns to controls`() {
        val f = Fixture()
        f.machine.episodeSelected()
        assertFalse(f.state.controlsVisible)
        f.machine.onAction(TvPlayerAction.OpenSourceDialog)
        f.machine.mediaSelected()
        assertFalse(f.state.sourceDialogVisible)
        assertTrue(f.state.controlsVisible)
    }

    @Test
    fun `episode button opens inline strip and back restores its button`() = runBlocking {
        val f = Fixture()
        f.machine.onAction(TvPlayerAction.TogglePanel(TvPlayerPanel.Comments))
        f.machine.onAction(TvPlayerAction.ToggleEpisodeStrip)
        assertTrue(f.state.stripExpanded)
        assertNull(f.state.activePanel)
        assertNull(f.state.dialog)
        f.machine.autoHide()
        assertTrue(f.state.controlsVisible)
        f.machine.onAction(TvPlayerAction.Back)
        assertFalse(f.state.stripExpanded)
        assertTrue(f.state.controlsVisible)
        assertEquals(TvPlayerFocusRequest.EpisodesButton, f.machine.focusRequests.first())
        assertTrue(f.commands.isEmpty())
    }

    @Test
    fun `episode actions preserve strip while modal takes focus`() = runBlocking {
        val f = Fixture()
        f.machine.onAction(TvPlayerAction.ToggleEpisodeStrip)
        f.machine.onAction(TvPlayerAction.OpenDialog(TvPlayerDialog.EpisodeActions))
        f.machine.onAction(TvPlayerAction.StripFocusLost)
        assertTrue(f.state.stripExpanded)
        assertEquals(TvPlayerDialog.EpisodeActions, f.state.dialog)
        f.machine.onAction(TvPlayerAction.Back)
        assertTrue(f.state.stripExpanded)
        assertNull(f.state.dialog)
        assertEquals(TvPlayerFocusRequest.DialogButton(TvPlayerDialog.EpisodeActions), f.machine.focusRequests.first())
        f.machine.onAction(TvPlayerAction.StripFocusLost)
        assertFalse(f.state.stripExpanded)
    }

    @Test
    fun `danmaku dialogs keep settings and return focus to their entry`() = runBlocking {
        for (dialog in listOf(TvPlayerDialog.DanmakuList, TvPlayerDialog.DanmakuMatch)) {
            val f = Fixture()
            f.machine.onAction(TvPlayerAction.TogglePanel(TvPlayerPanel.DanmakuSettings))
            f.machine.onAction(TvPlayerAction.OpenDialog(dialog))
            assertEquals(TvPlayerPanel.DanmakuSettings, f.state.activePanel)
            f.machine.onAction(TvPlayerAction.Back)
            assertNull(f.state.dialog)
            assertEquals(TvPlayerPanel.DanmakuSettings, f.state.activePanel)
            assertEquals(TvPlayerFocusRequest.DialogButton(dialog), f.machine.focusRequests.first())
        }
    }

    @Test
    fun `source speed and menu replace an expanded strip`() {
        for (intent in listOf(TvPlayerAction.OpenSourceDialog, TvPlayerAction.OpenDialog(TvPlayerDialog.Speed))) {
            val f = Fixture()
            f.machine.onAction(TvPlayerAction.ToggleEpisodeStrip)
            f.machine.onAction(intent)
            assertFalse(f.state.stripExpanded)
        }
        val f = Fixture()
        f.machine.onAction(TvPlayerAction.ToggleEpisodeStrip)
        f.key(TvRemoteKey.Menu)
        assertFalse(f.state.stripExpanded)
        assertTrue(f.state.controlsVisible)
        assertTrue(f.commands.isEmpty())
    }

    @Test
    fun `sidebar opening blocks stale timeline focus from seeking`() {
        val f = Fixture()
        f.machine.onAction(TvPlayerAction.TogglePanel(TvPlayerPanel.Comments))
        assertTrue(f.state.sidebarVisible)
        assertTrue(f.key(TvRemoteKey.Right))
        assertTrue(f.key(TvRemoteKey.Confirm))
        assertNull(f.state.scrubMillis)
        assertTrue(f.commands.isEmpty())
        // Once inside the sidebar, directional keys belong to its list or adjustment controls.
        assertFalse(
            f.machine.onAction(
                TvPlayerAction.RemoteKey(
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
            f.machine.onAction(TvPlayerAction.TogglePanel(panel))
            f.machine.autoHide()
            assertTrue(f.state.sidebarVisible)
            assertTrue(f.machine.onAction(TvPlayerAction.Back))
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
        f.machine.onAction(TvPlayerAction.OpenSourceDialog)
        f.machine.onAction(TvPlayerAction.TogglePanel(TvPlayerPanel.DanmakuSettings))
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
        assertTrue(f.machine.onAction(TvPlayerAction.RemoteKey(TvRemoteKey.Down, true, 0, 1_000, false, true, false)))
        assertEquals(TvPlayerFocusRequest.Recommendations, f.machine.focusRequests.first())
        assertFalse(f.state.stripExpanded)
        assertTrue(f.state.recommendationsVisible)
        assertTrue(f.commands.isEmpty())
    }

    @Test
    fun `recommendations replace controller content and prevent auto hide`() = runBlocking {
        val f = Fixture()
        f.machine.onAction(TvPlayerAction.ToggleEpisodeStrip)
        f.machine.onAction(TvPlayerAction.OpenRecommendations)
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
            TvPlayerAction.Back,
            TvPlayerAction.CloseRecommendations,
            TvPlayerAction.RemoteKey(TvRemoteKey.Up, true, 0, 1_000, false, false, false, recommendationsFocused = true),
        )) {
            val f = Fixture()
            f.machine.onAction(TvPlayerAction.OpenRecommendations)
            assertEquals(TvPlayerFocusRequest.Recommendations, f.machine.focusRequests.first())
            assertTrue(f.machine.onAction(exit))
            assertFalse(f.state.recommendationsVisible)
            assertTrue(f.state.controlsVisible)
            assertEquals(TvPlayerFocusRequest.SeekBar, f.machine.focusRequests.first())
            assertTrue(f.commands.isEmpty())
        }
    }

    @Test
    fun `recommendation transition cannot seek or toggle playback through stale controller focus`() {
        val f = Fixture()
        f.machine.onAction(TvPlayerAction.OpenRecommendations)
        for (key in listOf(TvRemoteKey.Left, TvRemoteKey.Right, TvRemoteKey.Confirm)) {
            assertTrue(f.key(key))
            assertTrue(f.key(key, down = false))
        }
        assertNull(f.state.scrubMillis)
        assertTrue(f.commands.isEmpty())
        assertFalse(f.machine.onAction(
            TvPlayerAction.RemoteKey(TvRemoteKey.Right, true, 0, 1_000, false, false, false, recommendationsFocused = true),
        ))
    }

    @Test
    fun `source panel dialog episode strip and menu replace recommendations`() {
        for (intent in listOf(
            TvPlayerAction.OpenSourceDialog,
            TvPlayerAction.TogglePanel(TvPlayerPanel.Comments),
            TvPlayerAction.OpenDialog(TvPlayerDialog.Speed),
            TvPlayerAction.ToggleEpisodeStrip,
            TvPlayerAction.RemoteKey(TvRemoteKey.Menu, true, 0, 1_000, false, false, false),
        )) {
            val f = Fixture()
            f.machine.onAction(TvPlayerAction.OpenRecommendations)
            f.machine.onAction(intent)
            assertFalse(f.state.recommendationsVisible)
            assertTrue(f.state.controlsVisible)
        }
    }
}
