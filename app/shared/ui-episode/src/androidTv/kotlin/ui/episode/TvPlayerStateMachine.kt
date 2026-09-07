/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.leanback.ui.episode

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow

/** Playback facts are supplied by the ViewModel, never read from a Composable. */
internal data class TvPlaybackSnapshot(val playing: Boolean, val positionMillis: Long, val durationMillis: Long)

internal sealed interface TvPlaybackCommand {
    data object TogglePause : TvPlaybackCommand
    data class SeekTo(val positionMillis: Long) : TvPlaybackCommand
    data class SwitchNeighbor(val offset: Int) : TvPlaybackCommand
    data class SpeedHold(val engaged: Boolean) : TvPlaybackCommand
    data object CycleAspectRatio : TvPlaybackCommand
}

/** ViewModel-owned reducer for remote input and overlay transitions. */
internal class TvPlayerStateMachine(
    private val playback: () -> TvPlaybackSnapshot,
    private val execute: (TvPlaybackCommand) -> Unit,
) {
    private val state = MutableStateFlow(TvPlayerOverlayState())
    val states = state.asStateFlow()
    private val focus = Channel<TvPlayerFocusRequest>(Channel.BUFFERED)
    val focusRequests = focus.receiveAsFlow()
    private var trackingConfirm = false
    private var firedConfirm = false

    fun onIntent(intent: TvEpisodeIntent): Boolean {
        when (intent) {
            is TvEpisodeIntent.RemoteKey -> return onKey(intent)
            TvEpisodeIntent.Back -> return back()
            TvEpisodeIntent.OpenRecommendations -> openRecommendations()
            TvEpisodeIntent.CloseRecommendations -> closeRecommendations()
            TvEpisodeIntent.ToggleEpisodeStrip -> {
                releaseHeldSpeed()
                val expanded = !state.value.stripExpanded
                state.value = state.value.copy(
                    controlsVisible = true,
                    recommendationsVisible = false,
                    stripExpanded = expanded,
                    activePanel = null,
                    scrubMillis = null,
                    dialog = null,
                    sourceDialogVisible = false,
                )
                if (!expanded) focus.trySend(TvPlayerFocusRequest.EpisodesButton)
                bump()
            }

            TvEpisodeIntent.NextEpisode -> {
                execute(TvPlaybackCommand.SwitchNeighbor(1)); bump()
            }

            is TvEpisodeIntent.TogglePanel -> {
                releaseHeldSpeed()
                state.value = state.value.copy(
                    controlsVisible = true,
                    recommendationsVisible = false,
                    sourceDialogVisible = false,
                    activePanel = intent.panel.takeUnless { it == state.value.activePanel },
                    scrubMillis = null,
                    stripExpanded = false,
                    dialog = null,
                )
                bump()
            }

            TvEpisodeIntent.OpenSourceDialog -> {
                releaseHeldSpeed()
                state.value = state.value.copy(
                    sourceDialogVisible = true,
                    controlsVisible = true,
                    recommendationsVisible = false,
                    stripExpanded = false,
                    scrubMillis = null,
                    activePanel = null,
                    dialog = null,
                )
                bump()
            }

            is TvEpisodeIntent.OpenDialog -> {
                releaseHeldSpeed()
                state.value = state.value.copy(
                    dialog = intent.dialog,
                    controlsVisible = true,
                    recommendationsVisible = false,
                    activePanel = state.value.activePanel.takeIf {
                        intent.dialog == TvPlayerDialog.DanmakuList || intent.dialog == TvPlayerDialog.DanmakuMatch
                    },
                    stripExpanded = state.value.stripExpanded && intent.dialog == TvPlayerDialog.EpisodeActions,
                    scrubMillis = null,
                    sourceDialogVisible = false,
                )
                bump()
            }

            TvEpisodeIntent.CycleAspectRatio -> {
                execute(TvPlaybackCommand.CycleAspectRatio); bump()
            }

            TvEpisodeIntent.StripFocusLost -> if (state.value.dialog == null) {
                state.value = state.value.copy(stripExpanded = false)
            }

            TvEpisodeIntent.ReleaseHeldSpeed -> releaseHeldSpeed()
            else -> return false
        }
        return true
    }

    fun episodeSelected() = hideControls()

    fun mediaSelected() {
        state.value = state.value.copy(sourceDialogVisible = false)
        bump()
        focus.trySend(TvPlayerFocusRequest.SourceButton)
    }

    fun closeDialog() {
        val dialog = state.value.dialog ?: return
        state.value = state.value.copy(dialog = null)
        bump()
        focus.trySend(TvPlayerFocusRequest.DialogButton(dialog))
    }

    fun sourceControls() {
        releaseHeldSpeed()
        state.value = state.value.copy(
            controlsVisible = true,
            recommendationsVisible = false,
            sourceDialogVisible = false,
            dialog = null,
            stripExpanded = false,
            activePanel = null,
            scrubMillis = null,
        )
        bump()
        focus.trySend(TvPlayerFocusRequest.SourceButton)
    }

    fun autoHide() {
        if (playback().playing && state.value.canAutoHide) hideControls()
    }

    private fun bump() {
        state.value = state.value.copy(interactionGeneration = state.value.interactionGeneration + 1)
    }

    private fun showControls() {
        releaseHeldSpeed()
        state.value = state.value.copy(controlsVisible = true, recommendationsVisible = false)
        bump()
        focus.trySend(TvPlayerFocusRequest.SeekBar)
    }

    private fun hideControls() {
        releaseHeldSpeed()
        state.value = state.value.copy(
            controlsVisible = false,
            recommendationsVisible = false,
            stripExpanded = false,
            activePanel = null,
            scrubMillis = null,
            dialog = null,
        )
        focus.trySend(TvPlayerFocusRequest.Root)
    }

    private fun releaseHeldSpeed() {
        if (firedConfirm || state.value.speedHolding) execute(TvPlaybackCommand.SpeedHold(false))
        trackingConfirm = false
        firedConfirm = false
        state.value = state.value.copy(speedHolding = false)
    }

    private fun moveScrub(deltaMillis: Long) {
        val playback = playback()
        if (playback.durationMillis <= 0) return
        val upperBound = playback.durationMillis
        val base = state.value.scrubMillis ?: playback.positionMillis
        state.value = state.value.copy(
            controlsVisible = true,
            recommendationsVisible = false,
            scrubMillis = (base + deltaMillis).coerceIn(0, upperBound),
            activePanel = null,
            stripExpanded = false,
        )
        bump()
        focus.trySend(TvPlayerFocusRequest.SeekBar)
    }

    private fun togglePause() {
        val wasPlaying = playback().playing
        execute(TvPlaybackCommand.TogglePause)
        if (wasPlaying && !state.value.controlsVisible) showControls() else bump()
    }

    private fun back(): Boolean {
        when {
            state.value.dialog != null -> closeDialog()
            state.value.sourceDialogVisible -> mediaSelected()
            state.value.recommendationsVisible -> closeRecommendations()
            state.value.activePanel != null -> {
                val panel = state.value.activePanel!!
                state.value = state.value.copy(activePanel = null)
                bump()
                focus.trySend(TvPlayerFocusRequest.PanelChip(panel))
            }

            state.value.scrubMillis != null -> {
                state.value = state.value.copy(scrubMillis = null); bump()
            }

            state.value.stripExpanded -> {
                state.value = state.value.copy(stripExpanded = false)
                bump()
                focus.trySend(TvPlayerFocusRequest.EpisodesButton)
            }

            state.value.controlsVisible -> hideControls()
            else -> return false
        }
        return true
    }

    private fun openRecommendations() {
        releaseHeldSpeed()
        state.value = state.value.copy(
            controlsVisible = true,
            recommendationsVisible = true,
            stripExpanded = false,
            activePanel = null,
            scrubMillis = null,
            dialog = null,
            sourceDialogVisible = false,
        )
        bump()
        focus.trySend(TvPlayerFocusRequest.Recommendations)
    }

    private fun closeRecommendations() {
        if (!state.value.recommendationsVisible) return
        state.value = state.value.copy(recommendationsVisible = false)
        bump()
        focus.trySend(TvPlayerFocusRequest.SeekBar)
    }

    private fun onKey(event: TvEpisodeIntent.RemoteKey): Boolean {
        val key = event.key
        val isDown = event.isDown
        val isNewPress = isDown && event.repeatCount == 0
        if (key == TvRemoteKey.Menu) {
            if (isNewPress) sourceControls()
            return true
        }
        // Explicit Play/Pause keys are idempotent, while PlayPause toggles.
        when (key) {
            TvRemoteKey.PlayPause, TvRemoteKey.Play, TvRemoteKey.Pause -> {
                if (isNewPress && (key == TvRemoteKey.PlayPause ||
                            (key == TvRemoteKey.Play && !playback().playing) ||
                            (key == TvRemoteKey.Pause && playback().playing))
                ) togglePause()
                return true
            }

            TvRemoteKey.Next, TvRemoteKey.Previous -> {
                if (isNewPress) {
                    execute(TvPlaybackCommand.SwitchNeighbor(if (key == TvRemoteKey.Next) 1 else -1))
                    showControls()
                }
                return true
            }

            else -> Unit
        }
        if (state.value.sourceDialogVisible) {
            return !event.sourceDialogFocused && key in navigationKeys
        }
        if (state.value.dialog != null) return false
        if (state.value.recommendationsVisible) {
            if (isDown) bump()
            if (key == TvRemoteKey.Up) {
                if (isNewPress) closeRecommendations()
                return true
            }
            // Do not send navigation to the outgoing controller while the row is entering.
            return !event.recommendationsFocused && key in navigationKeys
        }
        // Until the sidebar has acquired focus, D-pad input must not operate the player behind it.
        if (state.value.sidebarVisible) {
            if (isDown) bump()
            return !event.sidebarFocused && key in navigationKeys
        }
        if (state.value.scrubMillis != null) {
            when (key) {
                TvRemoteKey.Left, TvRemoteKey.Right -> if (isDown) moveScrub(if (key == TvRemoteKey.Right) 5_000 else -5_000)
                TvRemoteKey.Confirm -> if (isNewPress) {
                    execute(TvPlaybackCommand.SeekTo(state.value.scrubMillis!!))
                    state.value = state.value.copy(scrubMillis = null)
                    bump()
                }

                TvRemoteKey.Up, TvRemoteKey.Down -> if (isNewPress) {
                    state.value = state.value.copy(scrubMillis = null)
                    bump()
                }

                else -> return false
            }
            return true
        }
        if (!state.value.controlsVisible) {
            when (key) {
                TvRemoteKey.Confirm -> {
                    if (isDown) {
                        if (isNewPress) {
                            releaseHeldSpeed()
                            trackingConfirm = true
                        } else if (trackingConfirm && !firedConfirm) {
                            firedConfirm = true
                            execute(TvPlaybackCommand.SpeedHold(true))
                            state.value = state.value.copy(speedHolding = true)
                        }
                    } else if (trackingConfirm) {
                        val wasHeld = firedConfirm
                        releaseHeldSpeed()
                        if (!wasHeld) togglePause()
                    }
                }

                TvRemoteKey.Left, TvRemoteKey.Right -> if (isDown) moveScrub(if (key == TvRemoteKey.Right) 5_000 else -5_000)
                TvRemoteKey.Up, TvRemoteKey.Down -> if (isNewPress) showControls()
                else -> return false
            }
            return true
        }
        if (isDown) bump()
        if (event.seekBarFocused) {
            when (key) {
                TvRemoteKey.Left, TvRemoteKey.Right -> {
                    if (isDown) moveScrub(if (key == TvRemoteKey.Right) 5_000 else -5_000)
                    return true
                }

                TvRemoteKey.Confirm -> {
                    if (isNewPress) togglePause(); return true
                }

                else -> Unit
            }
        }
        if (event.iconRowFocused && key == TvRemoteKey.Up) {
            if (isNewPress) focus.trySend(TvPlayerFocusRequest.SeekBar)
            return true
        }
        if (event.iconRowFocused && key == TvRemoteKey.Down) {
            if (isNewPress) openRecommendations()
            return true
        }
        return false
    }

    private companion object {
        val navigationKeys =
            setOf(TvRemoteKey.Left, TvRemoteKey.Right, TvRemoteKey.Up, TvRemoteKey.Down, TvRemoteKey.Confirm)
    }
}
