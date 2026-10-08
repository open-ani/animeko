/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.videoplayer.ui.gesture

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import me.him188.ani.app.ui.foundation.effects.ComposeKey
import me.him188.ani.app.videoplayer.ui.PlayerKeyboardHandler
import me.him188.ani.app.videoplayer.ui.nextPlaybackSpeed

private val PLAYBACK_SPEED_SHORTCUTS = listOf(
    ComposeKey.One to 1f,
    ComposeKey.NumPad1 to 1f,
    ComposeKey.Two to 2f,
    ComposeKey.NumPad2 to 2f,
    ComposeKey.Three to 3f,
    ComposeKey.NumPad3 to 3f,
)

/** Builds the playback commands dispatched by the player's keyboard scope. */
@Composable
internal fun rememberPlayerKeyboardShortcuts(
    seekerState: SwipeSeekerState,
    fastSkipState: FastSkipState?,
    currentPlaybackSpeed: Float?,
    playbackSpeedRange: ClosedFloatingPointRange<Float>,
    onPlaybackSpeedChanged: (Float) -> Unit,
    volumeEnabled: Boolean,
    onVolumeUp: (fineAdjustment: Boolean) -> Unit,
    onVolumeDown: (fineAdjustment: Boolean) -> Unit,
    onTogglePauseResume: () -> Unit,
    onToggleFullscreen: () -> Unit,
    onToggleDanmaku: () -> Unit,
    onTogglePlayerStats: () -> Unit,
): PlayerKeyboardHandler {
    val seekHandler = rememberKeyboardSeekHandler(
        onSeekBackward = { seekerState.onSeek(-5) },
        onSeekForward = { seekerState.onSeek(5) },
        fastSkipState = fastSkipState,
    )
    val volumeCommands by rememberUpdatedState<(KeyEvent) -> Boolean> volume@{ event ->
        if (!volumeEnabled) return@volume false
        when (event.key) {
            ComposeKey.DirectionUp -> {
                if (event.type == KeyEventType.KeyDown) onVolumeUp(event.isShiftPressed)
                true
            }
            ComposeKey.DirectionDown -> {
                if (event.type == KeyEventType.KeyDown) onVolumeDown(event.isShiftPressed)
                true
            }
            else -> false
        }
    }
    val releaseCommands by rememberUpdatedState<(KeyEvent) -> Boolean> release@{ event ->
        val action = when (event.key) {
            ComposeKey.Spacebar -> onTogglePauseResume
            ComposeKey.F -> onToggleFullscreen
            ComposeKey.B -> onToggleDanmaku
            ComposeKey.I -> onTogglePlayerStats
            else -> null
        }
        if (action != null) {
            if (event.type == KeyEventType.KeyUp) action()
            return@release true
        }
        if (currentPlaybackSpeed == null) return@release false
        val speed = when (event.key) {
            ComposeKey.A -> nextPlaybackSpeed(currentPlaybackSpeed, playbackSpeedRange, -1)
            ComposeKey.D -> nextPlaybackSpeed(currentPlaybackSpeed, playbackSpeedRange, 1)
            ComposeKey.S -> 1f.coerceIn(playbackSpeedRange)
            else -> PLAYBACK_SPEED_SHORTCUTS.firstOrNull { it.first == event.key }?.second
                ?.coerceIn(playbackSpeedRange) ?: return@release false
        }
        if (event.type == KeyEventType.KeyUp) onPlaybackSpeedChanged(speed)
        true
    }
    return remember(seekHandler) {
        object : PlayerKeyboardHandler {
            override fun invoke(event: KeyEvent): Boolean =
                seekHandler(event) || volumeCommands(event) || releaseCommands(event)
            override fun cancel() = seekHandler.cancel()
        }
    }
}
