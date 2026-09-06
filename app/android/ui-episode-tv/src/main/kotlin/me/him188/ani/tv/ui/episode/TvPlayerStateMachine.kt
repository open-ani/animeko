/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.tv.ui.episode

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow

/** Playback facts are supplied by the ViewModel, never read from a Composable. */
internal data class TvPlaybackSnapshot(val playing: Boolean, val positionMillis: Long, val durationMillis: Long)

internal sealed interface TvPlaybackCommand {
    data object TogglePause : TvPlaybackCommand
    data class SeekBy(val deltaMillis: Long) : TvPlaybackCommand
    data class SeekTo(val positionMillis: Long) : TvPlaybackCommand
    data class SwitchNeighbor(val offset: Int) : TvPlaybackCommand
    data class SpeedHold(val engaged: Boolean) : TvPlaybackCommand
    data object CycleSpeed : TvPlaybackCommand
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
    private var lastSeekPressAt: Long? = null

    fun onIntent(intent: TvEpisodeIntent): Boolean {
        when (intent) {
            is TvEpisodeIntent.RemoteKey -> return onKey(intent)
            TvEpisodeIntent.Back -> return back()
            TvEpisodeIntent.SeekBack -> { execute(TvPlaybackCommand.SeekBy(-10_000)); bump() }
            TvEpisodeIntent.SeekForward -> { execute(TvPlaybackCommand.SeekBy(30_000)); bump() }
            TvEpisodeIntent.NextEpisode -> { execute(TvPlaybackCommand.SwitchNeighbor(1)); bump() }
            is TvEpisodeIntent.TogglePanel -> {
                state.value = state.value.copy(activePanel = intent.panel.takeUnless { it == state.value.activePanel })
                bump()
            }
            TvEpisodeIntent.OpenSourceDialog -> {
                releaseHeldSpeed()
                state.value = state.value.copy(sourceDialogVisible = true, controlsVisible = true)
                bump()
            }
            TvEpisodeIntent.CycleSpeed -> { execute(TvPlaybackCommand.CycleSpeed); bump() }
            TvEpisodeIntent.CycleAspectRatio -> { execute(TvPlaybackCommand.CycleAspectRatio); bump() }
            TvEpisodeIntent.StripFocusLost -> state.value = state.value.copy(stripExpanded = false)
            TvEpisodeIntent.ReleaseHeldSpeed -> releaseHeldSpeed()
            else -> return false
        }
        return true
    }

    fun episodeSelected() = hideControls()

    fun mediaSelected() {
        state.value = state.value.copy(sourceDialogVisible = false)
        bump()
        focus.trySend(TvPlayerFocusRequest.SeekBar)
    }

    fun autoHide() {
        if (playback().playing && state.value.canAutoHide) hideControls()
    }

    fun clearFlash(flash: Pair<String, Int>) {
        if (state.value.seekFlash == flash) state.value = state.value.copy(seekFlash = null)
    }

    private fun bump() {
        state.value = state.value.copy(interactionGeneration = state.value.interactionGeneration + 1)
    }

    private fun showControls() {
        releaseHeldSpeed()
        state.value = state.value.copy(controlsVisible = true)
        bump()
        focus.trySend(TvPlayerFocusRequest.SeekBar)
    }

    private fun hideControls() {
        releaseHeldSpeed()
        state.value = state.value.copy(controlsVisible = false, stripExpanded = false, activePanel = null, scrubMillis = null)
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
        val upperBound = playback.durationMillis.takeIf { it > 0 } ?: Long.MAX_VALUE
        val base = state.value.scrubMillis ?: playback.positionMillis
        state.value = state.value.copy(
            controlsVisible = true,
            scrubMillis = (base + deltaMillis).coerceIn(0, upperBound),
        )
        bump()
        focus.trySend(TvPlayerFocusRequest.SeekBar)
    }

    private fun seekPress(deltaMillis: Long, eventTimeMillis: Long) {
        val previous = lastSeekPressAt
        if (previous != null && eventTimeMillis - previous in 0..620) {
            moveScrub(deltaMillis)
        } else {
            execute(TvPlaybackCommand.SeekBy(deltaMillis))
            state.value = state.value.copy(seekFlash =
                (if (deltaMillis > 0) "+5 秒" else "-5 秒") to ((state.value.seekFlash?.second ?: 0) + 1),
            )
        }
        lastSeekPressAt = eventTimeMillis
    }

    private fun togglePause() {
        val wasPlaying = playback().playing
        execute(TvPlaybackCommand.TogglePause)
        if (wasPlaying && !state.value.controlsVisible) showControls() else bump()
    }

    private fun back(): Boolean {
        when {
            state.value.sourceDialogVisible -> mediaSelected()
            state.value.activePanel != null -> {
                val panel = state.value.activePanel!!
                state.value = state.value.copy(activePanel = null)
                bump()
                focus.trySend(TvPlayerFocusRequest.PanelChip(panel))
            }
            state.value.scrubMillis != null -> { state.value = state.value.copy(scrubMillis = null); bump() }
            state.value.stripExpanded -> {
                state.value = state.value.copy(stripExpanded = false)
                bump()
                focus.trySend(TvPlayerFocusRequest.SeekBar)
            }
            state.value.controlsVisible -> hideControls()
            else -> return false
        }
        return true
    }

    private fun onKey(event: TvEpisodeIntent.RemoteKey): Boolean {
        val key = event.key
        val isDown = event.isDown
        val isNewPress = isDown && event.repeatCount == 0
        // Explicit Play/Pause keys are idempotent, while PlayPause toggles.
        when (key) {
            TvRemoteKey.PlayPause, TvRemoteKey.Play, TvRemoteKey.Pause -> {
                if (isNewPress && (key == TvRemoteKey.PlayPause ||
                        (key == TvRemoteKey.Play && !playback().playing) ||
                        (key == TvRemoteKey.Pause && playback().playing))) togglePause()
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
                TvRemoteKey.Left, TvRemoteKey.Right -> if (isDown) seekPress(if (key == TvRemoteKey.Right) 5_000 else -5_000, event.eventTimeMillis)
                TvRemoteKey.Up, TvRemoteKey.Down -> if (isNewPress) showControls()
                else -> return false
            }
            return true
        }
        if (isDown) bump()
        if (event.seekBarFocused) {
            when (key) {
                TvRemoteKey.Left, TvRemoteKey.Right -> {
                    if (isDown) seekPress(if (key == TvRemoteKey.Right) 5_000 else -5_000, event.eventTimeMillis)
                    return true
                }
                TvRemoteKey.Confirm -> { if (isNewPress) togglePause(); return true }
                else -> Unit
            }
        }
        if (event.iconRowFocused && key == TvRemoteKey.Down) {
            if (isNewPress) state.value = state.value.copy(stripExpanded = true)
            return true
        }
        return false
    }

    private companion object {
        val navigationKeys = setOf(TvRemoteKey.Left, TvRemoteKey.Right, TvRemoteKey.Up, TvRemoteKey.Down, TvRemoteKey.Confirm)
    }
}
