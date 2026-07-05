/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.videoplayer.ui.gesture

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import me.him188.ani.app.ui.foundation.effects.ComposeKey
import me.him188.ani.app.ui.foundation.effects.onKey
import me.him188.ani.app.videoplayer.ui.PlaybackSpeedControllerState

/**
 * Installs the player keyboard commands on a single focus target.
 *
 * The caller owns focus policy. Commands are active only while the modified node owns focus, so a text field or
 * another player control can temporarily take keyboard input without triggering playback commands.
 */
internal fun Modifier.playerKeyboardShortcuts(
    seekerState: SwipeSeekerState,
    fastSkipState: FastSkipState?,
    playbackSpeedControllerState: PlaybackSpeedControllerState?,
    volumeEnabled: Boolean,
    onVolumeUp: (fineAdjustment: Boolean) -> Unit,
    onVolumeDown: (fineAdjustment: Boolean) -> Unit,
    onTogglePauseResume: () -> Unit,
    onToggleFullscreen: () -> Unit,
    onExitFullscreen: () -> Unit,
    onToggleDanmaku: () -> Unit,
    onTogglePlayerStats: () -> Unit,
): Modifier {
    var result = keyboardSeekAndFastForward(
        onSeekBackward = { seekerState.onSeek(-5) },
        onSeekForward = { seekerState.onSeek(5) },
        fastSkipState = fastSkipState,
    )
    if (volumeEnabled) {
        result = result.onKeyEvent { event ->
            if (event.type == KeyEventType.KeyUp) return@onKeyEvent false
            when (event.key) {
                ComposeKey.DirectionUp -> {
                    onVolumeUp(event.isShiftPressed)
                    true
                }

                ComposeKey.DirectionDown -> {
                    onVolumeDown(event.isShiftPressed)
                    true
                }

                else -> false
            }
        }
    }
    result = result
        .onKey(ComposeKey.Spacebar, onTogglePauseResume)
        .onKey(ComposeKey.Escape, onExitFullscreen)
        .onKey(ComposeKey.F, onToggleFullscreen)
    if (playbackSpeedControllerState != null) {
        result = result
            .onKey(ComposeKey.A, playbackSpeedControllerState::speedDown)
            .onKey(ComposeKey.D, playbackSpeedControllerState::speedUp)
            .onKey(ComposeKey.S, playbackSpeedControllerState::reset)
    }
    return result
        .onKey(ComposeKey.B, onToggleDanmaku)
        .onKey(ComposeKey.Tab, onTogglePlayerStats)
        // The same node carries combinedClickable, which treats Enter as a click when focused.
        // Enter is not a player shortcut, so swallow it; DPad center is left for clickable so that
        // remote/DPad activation still works like a tap.
        .onPreviewKeyEvent { event ->
            event.key == ComposeKey.Enter || event.key == ComposeKey.NumPadEnter
        }
}
