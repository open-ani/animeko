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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.him188.ani.app.data.models.preference.PlayerShortcut
import me.him188.ani.app.data.models.preference.PlayerShortcutAction
import me.him188.ani.app.data.models.preference.PlayerShortcutConfig
import me.him188.ani.app.ui.foundation.input.matches
import me.him188.ani.app.videoplayer.ui.PlayerKeyboardHandler
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
 * 当前生效的回调和可用性. 每次按键时重新读取, 所以回调变化不需要重建处理器.
 */
private class PlayerShortcutCallbacks(
    val currentPlaybackSpeed: Float?,
    val playbackSpeedRange: ClosedFloatingPointRange<Float>,
    val onPlaybackSpeedChanged: (Float) -> Unit,
    val volumeEnabled: Boolean,
    val onVolumeUp: (fineAdjustment: Boolean) -> Unit,
    val onVolumeDown: (fineAdjustment: Boolean) -> Unit,
    val onSeekBackward: () -> Unit,
    val onSeekForward: () -> Unit,
    val onTogglePauseResume: () -> Unit,
    val onToggleFullscreen: () -> Unit,
    val onToggleDanmaku: () -> Unit,
    val onTogglePlayerStats: () -> Unit,
)

/**
 * The player keyboard handler driven by [shortcuts]. Register it on `PlayerControllerState.keyboard`; the
 * [PlayerKeyboardScope][me.him188.ani.app.videoplayer.ui.PlayerKeyboardScope] delivers key events to it.
 *
 * Toggle-style commands fire on key up. Volume fires on every key down so that holding the key keeps adjusting.
 * Seek forward fires on key up unless the key was held long enough to start fast skipping.
 */
@Composable
internal fun rememberPlayerKeyboardShortcuts(
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
): PlayerKeyboardHandler {
    val layoutDirection = LocalLayoutDirection.current
    val resolver = remember(shortcuts) { PlayerShortcutResolver(shortcuts) }
    val callbacks by rememberUpdatedState(
        PlayerShortcutCallbacks(
            currentPlaybackSpeed = currentPlaybackSpeed,
            playbackSpeedRange = playbackSpeedRange,
            onPlaybackSpeedChanged = onPlaybackSpeedChanged,
            volumeEnabled = volumeEnabled,
            onVolumeUp = onVolumeUp,
            onVolumeDown = onVolumeDown,
            onSeekBackward = { seekerState.onSeek(-5) },
            onSeekForward = { seekerState.onSeek(5) },
            onTogglePauseResume = onTogglePauseResume,
            onToggleFullscreen = onToggleFullscreen,
            onToggleDanmaku = onToggleDanmaku,
            onTogglePlayerStats = onTogglePlayerStats,
        ),
    )
    val scope = rememberCoroutineScope()
    val handler = remember(resolver, layoutDirection, fastSkipState, scope) {
        PlayerShortcutHandler(scope, layoutDirection, resolver, fastSkipState) { callbacks }
    }
    DisposableEffect(handler) {
        onDispose { handler.cancel() }
    }
    return handler
}

private class PlayerShortcutHandler(
    private val scope: CoroutineScope,
    private val layoutDirection: LayoutDirection,
    private val resolver: PlayerShortcutResolver,
    private val fastSkipState: FastSkipState?,
    private val callbacks: () -> PlayerShortcutCallbacks,
) : PlayerKeyboardHandler {
    private var forwardPressed = false
    private var forwardJob: Job? = null
    private var fastSkipTicket: Int? = null

    override fun invoke(event: KeyEvent): Boolean {
        val resolved = resolver.resolve(event, layoutDirection) ?: return false
        val callbacks = callbacks()
        if (!callbacks.isAvailable(resolved.action)) return false

        return when (resolved.action) {
            PlayerShortcutAction.SEEK_FORWARD -> {
                when (event.type) {
                    KeyEventType.KeyDown -> {
                        if (!forwardPressed) {
                            forwardPressed = true
                            forwardJob = scope.launch {
                                delay(200)
                                fastSkipTicket = fastSkipState?.startSkipping(SkipDirection.FORWARD)
                            }
                        }
                        true
                    }

                    KeyEventType.KeyUp -> {
                        if (!forwardPressed) return false
                        val wasSkipping = fastSkipTicket != null
                        cancelForward()
                        if (!wasSkipping) callbacks.onSeekForward()
                        true
                    }

                    else -> false
                }
            }

            PlayerShortcutAction.VOLUME_UP, PlayerShortcutAction.VOLUME_DOWN -> {
                if (event.type == KeyEventType.KeyDown) {
                    val fine = PlayerShortcutResolver.isFineAdjustment(resolved, event)
                    if (resolved.action == PlayerShortcutAction.VOLUME_UP) {
                        callbacks.onVolumeUp(fine)
                    } else {
                        callbacks.onVolumeDown(fine)
                    }
                }
                true
            }

            else -> {
                if (event.type == KeyEventType.KeyUp) callbacks.perform(resolved.action)
                true
            }
        }
    }

    private fun cancelForward() {
        forwardJob?.cancel()
        forwardJob = null
        fastSkipTicket?.let { fastSkipState?.stopSkipping(it) }
        fastSkipTicket = null
        forwardPressed = false
    }

    override fun cancel() {
        cancelForward()
    }

    private fun PlayerShortcutCallbacks.isAvailable(action: PlayerShortcutAction): Boolean = when (action) {
        PlayerShortcutAction.VOLUME_UP, PlayerShortcutAction.VOLUME_DOWN -> volumeEnabled

        PlayerShortcutAction.PLAYBACK_SPEED_DOWN, PlayerShortcutAction.PLAYBACK_SPEED_UP,
        PlayerShortcutAction.PLAYBACK_SPEED_RESET, PlayerShortcutAction.PLAYBACK_SPEED_1X,
        PlayerShortcutAction.PLAYBACK_SPEED_2X, PlayerShortcutAction.PLAYBACK_SPEED_3X -> currentPlaybackSpeed != null

        else -> true
    }

    /**
     * 松开时触发的操作. 调用前已经通过 [isAvailable].
     */
    private fun PlayerShortcutCallbacks.perform(action: PlayerShortcutAction) {
        when (action) {
            PlayerShortcutAction.TOGGLE_PAUSE_RESUME -> onTogglePauseResume()
            PlayerShortcutAction.TOGGLE_FULLSCREEN -> onToggleFullscreen()
            PlayerShortcutAction.TOGGLE_DANMAKU -> onToggleDanmaku()
            PlayerShortcutAction.TOGGLE_PLAYER_STATS -> onTogglePlayerStats()
            PlayerShortcutAction.SEEK_BACKWARD -> onSeekBackward()
            PlayerShortcutAction.PLAYBACK_SPEED_DOWN -> changeSpeed { nextPlaybackSpeed(it, playbackSpeedRange, -1) }
            PlayerShortcutAction.PLAYBACK_SPEED_UP -> changeSpeed { nextPlaybackSpeed(it, playbackSpeedRange, 1) }
            PlayerShortcutAction.PLAYBACK_SPEED_RESET, PlayerShortcutAction.PLAYBACK_SPEED_1X -> changeSpeed { 1f }
            PlayerShortcutAction.PLAYBACK_SPEED_2X -> changeSpeed { 2f }
            PlayerShortcutAction.PLAYBACK_SPEED_3X -> changeSpeed { 3f }
            PlayerShortcutAction.SEEK_FORWARD, PlayerShortcutAction.VOLUME_UP, PlayerShortcutAction.VOLUME_DOWN ->
                error("$action is handled on key down")
        }
    }

    private fun PlayerShortcutCallbacks.changeSpeed(compute: (current: Float) -> Float) {
        val current = currentPlaybackSpeed ?: return
        onPlaybackSpeedChanged(compute(current).coerceIn(playbackSpeedRange))
    }
}
