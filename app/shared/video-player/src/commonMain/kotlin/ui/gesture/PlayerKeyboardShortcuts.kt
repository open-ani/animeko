/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.videoplayer.ui.gesture

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import me.him188.ani.app.data.models.preference.PlayerShortcut
import me.him188.ani.app.data.models.preference.PlayerShortcutAction
import me.him188.ani.app.data.models.preference.PlayerShortcutConfig
import me.him188.ani.app.tools.rememberUiMonoTasker
import me.him188.ani.app.ui.foundation.input.matches
import me.him188.ani.app.videoplayer.ui.nextPlaybackSpeed

/**
 * 一次按键匹配到的操作和它命中的按键组合.
 */
@Immutable
class ResolvedPlayerShortcut(
    val action: PlayerShortcutAction,
    val shortcut: PlayerShortcut,
)

/**
 * 按 [PlayerShortcutConfig] 把按键解析成操作.
 *
 * 修饰键必须正好匹配, 所以 Ctrl+Space 不会触发只绑定了 Space 的操作, 页面上的其他快捷键可以安全地使用带修饰键的组合.
 * 唯一的例外是音量: 绑定本身不含 Shift 时, 按住 Shift 也能命中, 由调用方把它当作微调 (见 [isFineAdjustment]).
 * 精确匹配优先, 因此把 Shift+↑ 绑定到别的操作后, 它不再被音量命中.
 */
class PlayerShortcutResolver(config: PlayerShortcutConfig) {
    private val bindings: Map<PlayerShortcutAction, List<PlayerShortcut>> = config.bindings

    fun resolve(key: Key, ctrl: Boolean, shift: Boolean, alt: Boolean, meta: Boolean): ResolvedPlayerShortcut? {
        for ((action, shortcuts) in bindings) {
            for (shortcut in shortcuts) {
                if (shortcut.matches(key, ctrl, shift, alt, meta)) return ResolvedPlayerShortcut(action, shortcut)
            }
        }
        if (shift) {
            for (action in VOLUME_ACTIONS) {
                for (shortcut in bindings.getValue(action)) {
                    if (!shortcut.shift && shortcut.matches(key, ctrl, shift, alt, meta, ignoreShift = true)) {
                        return ResolvedPlayerShortcut(action, shortcut)
                    }
                }
            }
        }
        return null
    }

    /**
     * RTL 布局下左右方向键互换, 让「后退」始终在视觉上的后方.
     */
    fun resolve(event: KeyEvent, layoutDirection: LayoutDirection): ResolvedPlayerShortcut? {
        val key = if (layoutDirection == LayoutDirection.Rtl) {
            when (event.key) {
                Key.DirectionLeft -> Key.DirectionRight
                Key.DirectionRight -> Key.DirectionLeft
                else -> event.key
            }
        } else {
            event.key
        }
        return resolve(key, event.isCtrlPressed, event.isShiftPressed, event.isAltPressed, event.isMetaPressed)
    }

    companion object {
        private val VOLUME_ACTIONS = listOf(PlayerShortcutAction.VOLUME_UP, PlayerShortcutAction.VOLUME_DOWN)

        /**
         * 音量操作里, 按住 Shift 且绑定本身不含 Shift 时为微调.
         */
        fun isFineAdjustment(resolved: ResolvedPlayerShortcut, event: KeyEvent): Boolean =
            event.isShiftPressed && !resolved.shortcut.shift
    }
}

/**
 * Installs the player keyboard commands on a single focus target.
 *
 * The caller owns focus policy. Commands are active only while the modified node owns focus, so a text field or
 * another player control can temporarily take keyboard input without triggering playback commands.
 *
 * Toggle-style commands fire on key up. Volume fires on every key down so that holding the key keeps adjusting.
 * Seek forward fires on key up unless the key was held long enough to start fast skipping.
 */
internal fun Modifier.playerKeyboardShortcuts(
    shortcuts: PlayerShortcutConfig,
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
): Modifier = composed(
    inspectorInfo = {
        name = "playerKeyboardShortcuts"
    },
) {
    val layoutDirection = LocalLayoutDirection.current
    val resolver = remember(shortcuts) { PlayerShortcutResolver(shortcuts) }

    val currentPlaybackSpeedState by rememberUpdatedState(currentPlaybackSpeed)
    val playbackSpeedRangeState by rememberUpdatedState(playbackSpeedRange)
    val onPlaybackSpeedChangedState by rememberUpdatedState(onPlaybackSpeedChanged)
    val volumeEnabledState by rememberUpdatedState(volumeEnabled)
    val onVolumeUpState by rememberUpdatedState(onVolumeUp)
    val onVolumeDownState by rememberUpdatedState(onVolumeDown)
    val onTogglePauseResumeState by rememberUpdatedState(onTogglePauseResume)
    val onToggleFullscreenState by rememberUpdatedState(onToggleFullscreen)
    val onToggleDanmakuState by rememberUpdatedState(onToggleDanmaku)
    val onTogglePlayerStatsState by rememberUpdatedState(onTogglePlayerStats)
    val fastSkipStateState by rememberUpdatedState(fastSkipState)

    val fastSkipTasker = rememberUiMonoTasker()
    var fastSkipTicket by remember { mutableStateOf<Int?>(null) }

    fun changePlaybackSpeed(compute: (current: Float) -> Float): Boolean {
        val current = currentPlaybackSpeedState ?: return false
        onPlaybackSpeedChangedState(compute(current).coerceIn(playbackSpeedRangeState))
        return true
    }

    /**
     * 松开时触发的操作. 返回 `false` 表示该操作当前不可用, 按键交还给页面.
     */
    fun performOnKeyUp(action: PlayerShortcutAction): Boolean = when (action) {
        PlayerShortcutAction.TOGGLE_PAUSE_RESUME -> {
            onTogglePauseResumeState(); true
        }

        PlayerShortcutAction.TOGGLE_FULLSCREEN -> {
            onToggleFullscreenState(); true
        }

        PlayerShortcutAction.TOGGLE_DANMAKU -> {
            onToggleDanmakuState(); true
        }

        PlayerShortcutAction.TOGGLE_PLAYER_STATS -> {
            onTogglePlayerStatsState(); true
        }

        PlayerShortcutAction.SEEK_BACKWARD -> {
            seekerState.onSeek(-5); true
        }

        PlayerShortcutAction.PLAYBACK_SPEED_DOWN -> changePlaybackSpeed {
            nextPlaybackSpeed(it, playbackSpeedRangeState, -1)
        }

        PlayerShortcutAction.PLAYBACK_SPEED_UP -> changePlaybackSpeed {
            nextPlaybackSpeed(it, playbackSpeedRangeState, 1)
        }

        PlayerShortcutAction.PLAYBACK_SPEED_RESET, PlayerShortcutAction.PLAYBACK_SPEED_1X -> changePlaybackSpeed { 1f }
        PlayerShortcutAction.PLAYBACK_SPEED_2X -> changePlaybackSpeed { 2f }
        PlayerShortcutAction.PLAYBACK_SPEED_3X -> changePlaybackSpeed { 3f }

        PlayerShortcutAction.SEEK_FORWARD, PlayerShortcutAction.VOLUME_UP, PlayerShortcutAction.VOLUME_DOWN ->
            error("$action is handled on key down")
    }

    fun isAvailable(action: PlayerShortcutAction): Boolean = when (action) {
        PlayerShortcutAction.VOLUME_UP, PlayerShortcutAction.VOLUME_DOWN -> volumeEnabledState

        PlayerShortcutAction.PLAYBACK_SPEED_DOWN, PlayerShortcutAction.PLAYBACK_SPEED_UP,
        PlayerShortcutAction.PLAYBACK_SPEED_RESET, PlayerShortcutAction.PLAYBACK_SPEED_1X,
        PlayerShortcutAction.PLAYBACK_SPEED_2X, PlayerShortcutAction.PLAYBACK_SPEED_3X -> currentPlaybackSpeedState != null

        else -> true
    }

    onPreviewKeyEvent { event ->
        // The same node carries combinedClickable, which treats Enter as a click when focused.
        // Enter is not a player shortcut, so swallow it; DPad center is left for clickable so that
        // remote/DPad activation still works like a tap.
        if (event.key == Key.Enter || event.key == Key.NumPadEnter) return@onPreviewKeyEvent true

        val resolved = resolver.resolve(event, layoutDirection) ?: return@onPreviewKeyEvent false
        if (!isAvailable(resolved.action)) return@onPreviewKeyEvent false

        when (resolved.action) {
            PlayerShortcutAction.SEEK_FORWARD -> {
                when (event.type) {
                    KeyEventType.KeyDown -> {
                        if (!fastSkipTasker.isRunning.value) {
                            fastSkipTasker.launch {
                                try {
                                    delay(200)
                                    fastSkipStateState?.let {
                                        fastSkipTicket = it.startSkipping(SkipDirection.FORWARD)
                                    }
                                    awaitCancellation()
                                } finally {
                                    fastSkipTicket?.let {
                                        fastSkipStateState?.stopSkipping(it)
                                    }
                                    fastSkipTicket = null
                                }
                            }
                        }
                    }

                    KeyEventType.KeyUp -> {
                        val isSkipping = fastSkipTicket != null
                        fastSkipTasker.cancel()
                        if (!isSkipping) {
                            seekerState.onSeek(5)
                        }
                    }
                }
                true
            }

            PlayerShortcutAction.VOLUME_UP -> {
                if (event.type == KeyEventType.KeyDown) {
                    onVolumeUpState(PlayerShortcutResolver.isFineAdjustment(resolved, event))
                }
                true
            }

            PlayerShortcutAction.VOLUME_DOWN -> {
                if (event.type == KeyEventType.KeyDown) {
                    onVolumeDownState(PlayerShortcutResolver.isFineAdjustment(resolved, event))
                }
                true
            }

            else -> {
                when (event.type) {
                    KeyEventType.KeyDown -> true // consume so the page does not see a half shortcut
                    KeyEventType.KeyUp -> performOnKeyUp(resolved.action)
                    else -> false
                }
            }
        }
    }
}
