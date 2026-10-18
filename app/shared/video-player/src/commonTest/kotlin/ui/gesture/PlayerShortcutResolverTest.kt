/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.videoplayer.ui.gesture

import androidx.compose.ui.input.key.Key
import me.him188.ani.app.data.models.preference.PlayerShortcut
import me.him188.ani.app.data.models.preference.PlayerShortcutAction
import me.him188.ani.app.data.models.preference.PlayerShortcutConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PlayerShortcutResolverTest {
    private fun PlayerShortcutResolver.resolve(
        key: Key,
        ctrl: Boolean = false,
        shift: Boolean = false,
        alt: Boolean = false,
        meta: Boolean = false,
    ): PlayerShortcutAction? = resolve(key, ctrl, shift, alt, meta)?.action

    @Test
    fun `default bindings resolve`() {
        val resolver = PlayerShortcutResolver(PlayerShortcutConfig.Default)
        assertEquals(PlayerShortcutAction.TOGGLE_PAUSE_RESUME, resolver.resolve(Key.Spacebar))
        assertEquals(PlayerShortcutAction.TOGGLE_FULLSCREEN, resolver.resolve(Key.F))
        assertEquals(PlayerShortcutAction.SEEK_FORWARD, resolver.resolve(Key.DirectionRight))
        assertEquals(PlayerShortcutAction.PLAYBACK_SPEED_2X, resolver.resolve(Key.NumPad2))
        assertNull(resolver.resolve(Key.K))
    }

    @Test
    fun `extra modifiers do not trigger a plain binding`() {
        val resolver = PlayerShortcutResolver(PlayerShortcutConfig.Default)
        assertNull(resolver.resolve(Key.Spacebar, ctrl = true))
        assertNull(resolver.resolve(Key.F, shift = true))
        assertNull(resolver.resolve(Key.F, meta = true))
    }

    @Test
    fun `shift on a volume key is a fine adjustment unless shift is bound elsewhere`() {
        val resolver = PlayerShortcutResolver(PlayerShortcutConfig.Default)
        val resolved = resolver.resolve(Key.DirectionUp, ctrl = false, shift = true, alt = false, meta = false)
        assertEquals(PlayerShortcutAction.VOLUME_UP, resolved?.action)
        assertEquals(PlayerShortcut("DirectionUp"), resolved?.shortcut)

        val config = PlayerShortcutConfig.Default
            .withShortcuts(PlayerShortcutAction.TOGGLE_DANMAKU, listOf(PlayerShortcut("DirectionUp", shift = true)))
        val custom = PlayerShortcutResolver(config)
        assertEquals(PlayerShortcutAction.TOGGLE_DANMAKU, custom.resolve(Key.DirectionUp, shift = true))
        assertEquals(PlayerShortcutAction.VOLUME_UP, custom.resolve(Key.DirectionUp))
    }

    @Test
    fun `volume bound with shift does not respond without shift`() {
        val config = PlayerShortcutConfig.Default
            .withShortcuts(PlayerShortcutAction.VOLUME_UP, listOf(PlayerShortcut("DirectionUp", shift = true)))
        val resolver = PlayerShortcutResolver(config)
        assertNull(resolver.resolve(Key.DirectionUp))
        assertEquals(PlayerShortcutAction.VOLUME_UP, resolver.resolve(Key.DirectionUp, shift = true))
    }

    @Test
    fun `custom bindings replace defaults`() {
        val config = PlayerShortcutConfig.Default
            .withShortcuts(PlayerShortcutAction.TOGGLE_PAUSE_RESUME, listOf(PlayerShortcut("K"), PlayerShortcut("Spacebar")))
            .withShortcuts(PlayerShortcutAction.TOGGLE_FULLSCREEN, listOf(PlayerShortcut("F", ctrl = true)))
        val resolver = PlayerShortcutResolver(config)
        assertEquals(PlayerShortcutAction.TOGGLE_PAUSE_RESUME, resolver.resolve(Key.K))
        assertEquals(PlayerShortcutAction.TOGGLE_PAUSE_RESUME, resolver.resolve(Key.Spacebar))
        assertNull(resolver.resolve(Key.F))
        assertEquals(PlayerShortcutAction.TOGGLE_FULLSCREEN, resolver.resolve(Key.F, ctrl = true))
    }

    @Test
    fun `unbound action never resolves`() {
        val config = PlayerShortcutConfig.Default.withShortcuts(PlayerShortcutAction.TOGGLE_PLAYER_STATS, emptyList())
        assertNull(PlayerShortcutResolver(config).resolve(Key.I))
    }
}
