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
 * 唯一的例外是音量: 绑定本身不含 Shift 时, 按住 Shift 也能命中, 由调用方把它当作微调.
 * 精确匹配优先, 因此把 Shift+↑ 绑定到别的操作后, 它不再被音量命中.
 */
class PlayerShortcutResolver(config: PlayerShortcutConfig) {
    private val bindings: Map<PlayerShortcutAction, List<PlayerShortcut>> = config.bindings

    /**
     * RTL 布局下, 绑定在左右方向键上的前进 / 后退互换, 让「后退」始终在视觉上的后方; 其他绑定不受布局方向影响.
     */
    fun resolve(
        key: Key,
        ctrl: Boolean,
        shift: Boolean,
        alt: Boolean,
        meta: Boolean,
        layoutDirection: LayoutDirection = LayoutDirection.Ltr,
    ): ResolvedPlayerShortcut? {
        val resolved = resolveExact(key, ctrl, shift, alt, meta) ?: return null
        if (layoutDirection != LayoutDirection.Rtl || (key != Key.DirectionLeft && key != Key.DirectionRight)) {
            return resolved
        }
        val mirrored = when (resolved.action) {
            PlayerShortcutAction.SEEK_BACKWARD -> PlayerShortcutAction.SEEK_FORWARD
            PlayerShortcutAction.SEEK_FORWARD -> PlayerShortcutAction.SEEK_BACKWARD
            else -> return resolved
        }
        return ResolvedPlayerShortcut(mirrored, resolved.shortcut)
    }

    fun resolve(event: KeyEvent, layoutDirection: LayoutDirection): ResolvedPlayerShortcut? =
        resolve(event.key, event.isCtrlPressed, event.isShiftPressed, event.isAltPressed, event.isMetaPressed, layoutDirection)

    private fun resolveExact(key: Key, ctrl: Boolean, shift: Boolean, alt: Boolean, meta: Boolean): ResolvedPlayerShortcut? {
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

    private companion object {
        val VOLUME_ACTIONS = listOf(PlayerShortcutAction.VOLUME_UP, PlayerShortcutAction.VOLUME_DOWN)
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

/**
 * KeyDown 解析到的操作记在 [held] 里, KeyUp 直接沿用它而不看松开时的修饰键:
 * Ctrl+→ 快进中先松 Ctrl 再松 →, 松开事件仍属于「前进」并结束快进.
 *
 * 按住期间重复的 KeyDown 仍按当时的修饰键重新解析. 解析结果和记录的不同 (修饰键变了), 原操作就直接结束,
 * 不触发它松开时的动作; 之后这个按键属于新解析到的操作, 或者 (没有命中时) 交还给作用域处理.
 */
private class PlayerShortcutHandler(
    scope: CoroutineScope,
    private val layoutDirection: LayoutDirection,
    private val resolver: PlayerShortcutResolver,
    fastSkipState: FastSkipState?,
    private val callbacks: () -> PlayerShortcutCallbacks,
) : PlayerKeyboardHandler {
    private val held = mutableMapOf<Key, ResolvedPlayerShortcut>()
    private val forwardHold = FastSkipHold(scope, fastSkipState)

    override fun invoke(event: KeyEvent): Boolean {
        val callbacks = callbacks()
        val resolved = when (event.type) {
            KeyEventType.KeyDown -> {
                val resolved = resolver.resolve(event, layoutDirection)?.takeIf { callbacks.isAvailable(it.action) }
                val previous = held.remove(event.key)
                if (previous?.action == PlayerShortcutAction.SEEK_FORWARD && resolved?.action != previous.action) {
                    forwardHold.cancel()
                }
                resolved?.also { held[event.key] = it }
            }

            KeyEventType.KeyUp -> held.remove(event.key)
            else -> null
        } ?: return false

        val isKeyUp = event.type == KeyEventType.KeyUp
        return with(callbacks) {
            when (resolved.action) {
                PlayerShortcutAction.SEEK_FORWARD -> {
                    if (isKeyUp) {
                        if (!forwardHold.isPressed) return false
                        if (!forwardHold.release()) onSeekForward()
                    } else {
                        forwardHold.press()
                    }
                    true
                }

                // 绑定不含 Shift 时按住 Shift 为微调
                PlayerShortcutAction.VOLUME_UP -> onKeyDown(isKeyUp) { onVolumeUp(event.isShiftPressed && !resolved.shortcut.shift) }
                PlayerShortcutAction.VOLUME_DOWN -> onKeyDown(isKeyUp) { onVolumeDown(event.isShiftPressed && !resolved.shortcut.shift) }

                PlayerShortcutAction.TOGGLE_PAUSE_RESUME -> onKeyUp(isKeyUp, onTogglePauseResume)
                PlayerShortcutAction.TOGGLE_FULLSCREEN -> onKeyUp(isKeyUp, onToggleFullscreen)
                PlayerShortcutAction.TOGGLE_DANMAKU -> onKeyUp(isKeyUp, onToggleDanmaku)
                PlayerShortcutAction.TOGGLE_PLAYER_STATS -> onKeyUp(isKeyUp, onTogglePlayerStats)
                PlayerShortcutAction.SEEK_BACKWARD -> onKeyUp(isKeyUp, onSeekBackward)
                PlayerShortcutAction.PLAYBACK_SPEED_DOWN -> onKeyUp(isKeyUp) { changeSpeed { nextPlaybackSpeed(it, playbackSpeedRange, -1) } }
                PlayerShortcutAction.PLAYBACK_SPEED_UP -> onKeyUp(isKeyUp) { changeSpeed { nextPlaybackSpeed(it, playbackSpeedRange, 1) } }
                PlayerShortcutAction.PLAYBACK_SPEED_RESET, PlayerShortcutAction.PLAYBACK_SPEED_1X -> onKeyUp(isKeyUp) { changeSpeed { 1f } }
                PlayerShortcutAction.PLAYBACK_SPEED_2X -> onKeyUp(isKeyUp) { changeSpeed { 2f } }
                PlayerShortcutAction.PLAYBACK_SPEED_3X -> onKeyUp(isKeyUp) { changeSpeed { 3f } }
            }
        }
    }

    override fun cancel() {
        held.clear()
        forwardHold.cancel()
    }

    /**
     * 松开时触发一次的操作; 按下只消费事件.
     */
    private inline fun onKeyUp(isKeyUp: Boolean, action: () -> Unit): Boolean {
        if (isKeyUp) action()
        return true
    }

    /**
     * 每次按下都触发的操作, 按住不放持续触发; 松开只消费事件.
     */
    private inline fun onKeyDown(isKeyUp: Boolean, action: () -> Unit): Boolean {
        if (!isKeyUp) action()
        return true
    }

    private fun PlayerShortcutCallbacks.isAvailable(action: PlayerShortcutAction): Boolean = when (action) {
        PlayerShortcutAction.VOLUME_UP, PlayerShortcutAction.VOLUME_DOWN -> volumeEnabled

        PlayerShortcutAction.PLAYBACK_SPEED_DOWN, PlayerShortcutAction.PLAYBACK_SPEED_UP,
        PlayerShortcutAction.PLAYBACK_SPEED_RESET, PlayerShortcutAction.PLAYBACK_SPEED_1X,
        PlayerShortcutAction.PLAYBACK_SPEED_2X, PlayerShortcutAction.PLAYBACK_SPEED_3X -> currentPlaybackSpeed != null

        else -> true
    }

    private fun PlayerShortcutCallbacks.changeSpeed(compute: (current: Float) -> Float) {
        val current = currentPlaybackSpeed ?: return
        onPlaybackSpeedChanged(compute(current).coerceIn(playbackSpeedRange))
    }
}
