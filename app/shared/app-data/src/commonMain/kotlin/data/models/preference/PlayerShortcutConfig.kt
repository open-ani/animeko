/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.models.preference

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

/**
 * 播放器里可以用键盘触发的操作. 每个操作可以绑定零个或多个 [PlayerShortcut].
 */
@Serializable
enum class PlayerShortcutAction {
    TOGGLE_PAUSE_RESUME,
    TOGGLE_FULLSCREEN,
    TOGGLE_DANMAKU,
    TOGGLE_PLAYER_STATS,

    /** 后退 5 秒. */
    SEEK_BACKWARD,

    /** 前进 5 秒; 按住不放进入倍速快进. */
    SEEK_FORWARD,

    /** 增大音量. 绑定不含 Shift 时, 按住 Shift 触发微调. */
    VOLUME_UP,

    /** 减小音量. 绑定不含 Shift 时, 按住 Shift 触发微调. */
    VOLUME_DOWN,

    PLAYBACK_SPEED_DOWN,
    PLAYBACK_SPEED_UP,

    /** 回到 1x. */
    PLAYBACK_SPEED_RESET,
    PLAYBACK_SPEED_1X,
    PLAYBACK_SPEED_2X,
    PLAYBACK_SPEED_3X,
}

/**
 * 一个按键组合. [key] 是与平台无关的按键标识 (如 `"A"`, `"Spacebar"`, `"DirectionLeft"`, `"NumPad1"`),
 * 与 Compose `Key` 常量同名; 标识到 `Key` 的映射由 UI 层维护, 因为 `Key` 的键码在各平台不同, 不能直接持久化.
 */
@Serializable
@Immutable
data class PlayerShortcut(
    val key: String,
    val ctrl: Boolean = false,
    val shift: Boolean = false,
    val alt: Boolean = false,
    val meta: Boolean = false,
)

/**
 * 播放器键盘快捷键配置.
 *
 * 只持久化用户改过的操作 ([overrides]); 其余操作使用 [DEFAULT_BINDINGS]. 这样默认快捷键的调整会自动应用到
 * 没有自定义过该操作的用户. 一个按键组合同时只能属于一个操作, 由 [withShortcuts] 保证.
 *
 * @since 6.4
 */
@Serializable
@Immutable
data class PlayerShortcutConfig(
    /**
     * 用户自定义过的操作. 空列表表示该操作没有快捷键; 缺失表示使用默认值.
     */
    val overrides: Map<PlayerShortcutAction, List<PlayerShortcut>> = emptyMap(),
    @Suppress("PropertyName") @Transient val _placeholder: Int = 0,
) {
    /**
     * [action] 当前生效的快捷键.
     */
    fun shortcutsFor(action: PlayerShortcutAction): List<PlayerShortcut> =
        overrides[action] ?: DEFAULT_BINDINGS.getValue(action)

    /**
     * 所有操作当前生效的快捷键.
     */
    val bindings: Map<PlayerShortcutAction, List<PlayerShortcut>>
        get() = PlayerShortcutAction.entries.associateWith { shortcutsFor(it) }

    /**
     * 当前绑定了 [shortcut] 的操作, 没有则为 `null`.
     */
    fun actionFor(shortcut: PlayerShortcut): PlayerShortcutAction? =
        PlayerShortcutAction.entries.firstOrNull { shortcut in shortcutsFor(it) }

    /**
     * 把 [action] 的快捷键设为 [shortcuts] (去重, 保持顺序), 并从其他操作中移除这些按键组合.
     * 结果与默认值相同的操作不再记录为自定义.
     */
    fun withShortcuts(action: PlayerShortcutAction, shortcuts: List<PlayerShortcut>): PlayerShortcutConfig {
        val distinct = shortcuts.distinct()
        val newOverrides = overrides.toMutableMap()
        for (other in PlayerShortcutAction.entries) {
            if (other == action) continue
            val current = shortcutsFor(other)
            val remaining = current.filterNot { it in distinct }
            if (remaining.size != current.size) {
                newOverrides[other] = remaining
            }
        }
        newOverrides[action] = distinct
        return PlayerShortcutConfig(
            newOverrides.filter { (other, shortcuts) -> shortcuts != DEFAULT_BINDINGS.getValue(other) },
        )
    }

    companion object {
        @Stable
        val Default = PlayerShortcutConfig()

        /**
         * 每个操作的默认快捷键. 按键标识见 [PlayerShortcut.key].
         */
        val DEFAULT_BINDINGS: Map<PlayerShortcutAction, List<PlayerShortcut>> = mapOf(
            PlayerShortcutAction.TOGGLE_PAUSE_RESUME to listOf(PlayerShortcut("Spacebar")),
            PlayerShortcutAction.TOGGLE_FULLSCREEN to listOf(PlayerShortcut("F")),
            PlayerShortcutAction.TOGGLE_DANMAKU to listOf(PlayerShortcut("B")),
            PlayerShortcutAction.TOGGLE_PLAYER_STATS to listOf(PlayerShortcut("I")),
            PlayerShortcutAction.SEEK_BACKWARD to listOf(PlayerShortcut("DirectionLeft")),
            PlayerShortcutAction.SEEK_FORWARD to listOf(PlayerShortcut("DirectionRight")),
            PlayerShortcutAction.VOLUME_UP to listOf(PlayerShortcut("DirectionUp")),
            PlayerShortcutAction.VOLUME_DOWN to listOf(PlayerShortcut("DirectionDown")),
            PlayerShortcutAction.PLAYBACK_SPEED_DOWN to listOf(PlayerShortcut("A")),
            PlayerShortcutAction.PLAYBACK_SPEED_UP to listOf(PlayerShortcut("D")),
            PlayerShortcutAction.PLAYBACK_SPEED_RESET to listOf(PlayerShortcut("S")),
            PlayerShortcutAction.PLAYBACK_SPEED_1X to listOf(PlayerShortcut("One"), PlayerShortcut("NumPad1")),
            PlayerShortcutAction.PLAYBACK_SPEED_2X to listOf(PlayerShortcut("Two"), PlayerShortcut("NumPad2")),
            PlayerShortcutAction.PLAYBACK_SPEED_3X to listOf(PlayerShortcut("Three"), PlayerShortcut("NumPad3")),
        ).also { bindings ->
            check(bindings.keys == PlayerShortcutAction.entries.toSet()) {
                "Every PlayerShortcutAction needs a default binding"
            }
        }
    }
}
