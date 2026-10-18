/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.models.preference

import me.him188.ani.app.data.persistent.DataStoreJson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlayerShortcutConfigTest {
    private val space = PlayerShortcut("Spacebar")
    private val f = PlayerShortcut("F")
    private val k = PlayerShortcut("K")
    private val ctrlK = PlayerShortcut("K", ctrl = true)

    @Test
    fun `default config resolves every action to its default binding`() {
        val config = PlayerShortcutConfig.Default
        for (action in PlayerShortcutAction.entries) {
            assertEquals(PlayerShortcutConfig.DEFAULT_BINDINGS.getValue(action), config.shortcutsFor(action))
            assertTrue(config.isDefault(action))
        }
        assertEquals(PlayerShortcutAction.TOGGLE_PAUSE_RESUME, config.actionFor(space))
        assertNull(config.actionFor(k))
    }

    @Test
    fun `default bindings do not overlap`() {
        val all = PlayerShortcutConfig.DEFAULT_BINDINGS.values.flatten()
        assertEquals(all.size, all.toSet().size)
    }

    @Test
    fun `withShortcuts replaces the binding and removes it from other actions`() {
        val config = PlayerShortcutConfig.Default.withShortcuts(PlayerShortcutAction.TOGGLE_DANMAKU, listOf(space, k))

        assertEquals(listOf(space, k), config.shortcutsFor(PlayerShortcutAction.TOGGLE_DANMAKU))
        assertEquals(emptyList(), config.shortcutsFor(PlayerShortcutAction.TOGGLE_PAUSE_RESUME))
        assertEquals(PlayerShortcutAction.TOGGLE_DANMAKU, config.actionFor(space))
        // untouched actions keep their defaults and are not recorded as overrides
        assertEquals(listOf(f), config.shortcutsFor(PlayerShortcutAction.TOGGLE_FULLSCREEN))
        assertEquals(
            setOf(PlayerShortcutAction.TOGGLE_DANMAKU, PlayerShortcutAction.TOGGLE_PAUSE_RESUME),
            config.overrides.keys,
        )
    }

    @Test
    fun `withShortcuts deduplicates and treats modifiers as part of the key`() {
        val config = PlayerShortcutConfig.Default.withShortcuts(PlayerShortcutAction.TOGGLE_FULLSCREEN, listOf(k, k, ctrlK))

        assertEquals(listOf(k, ctrlK), config.shortcutsFor(PlayerShortcutAction.TOGGLE_FULLSCREEN))
        assertEquals(PlayerShortcutAction.TOGGLE_FULLSCREEN, config.actionFor(ctrlK))
    }

    @Test
    fun `setting an action back to its default drops the override`() {
        val changed = PlayerShortcutConfig.Default.withShortcuts(PlayerShortcutAction.TOGGLE_FULLSCREEN, listOf(k))
        assertEquals(setOf(PlayerShortcutAction.TOGGLE_FULLSCREEN), changed.overrides.keys)

        val restored = changed.withShortcuts(PlayerShortcutAction.TOGGLE_FULLSCREEN, listOf(f))
        assertEquals(PlayerShortcutConfig.Default, restored)
    }

    @Test
    fun `reset restores the default and takes it back from the action that borrowed it`() {
        val config = PlayerShortcutConfig.Default
            .withShortcuts(PlayerShortcutAction.TOGGLE_DANMAKU, listOf(f)) // steals F from fullscreen
            .reset(PlayerShortcutAction.TOGGLE_FULLSCREEN)

        assertEquals(listOf(f), config.shortcutsFor(PlayerShortcutAction.TOGGLE_FULLSCREEN))
        assertEquals(emptyList(), config.shortcutsFor(PlayerShortcutAction.TOGGLE_DANMAKU))
        assertTrue(config.isDefault(PlayerShortcutAction.TOGGLE_FULLSCREEN))
    }

    @Test
    fun `empty json decodes to default`() {
        val config = DataStoreJson.decodeFromString(PlayerShortcutConfig.serializer(), "{}")
        assertEquals(PlayerShortcutConfig.Default, config)
    }

    @Test
    fun `overrides survive serialization`() {
        val config = PlayerShortcutConfig.Default
            .withShortcuts(PlayerShortcutAction.TOGGLE_FULLSCREEN, listOf(ctrlK))
            .withShortcuts(PlayerShortcutAction.TOGGLE_PLAYER_STATS, emptyList())

        val decoded = DataStoreJson.decodeFromString(
            PlayerShortcutConfig.serializer(),
            DataStoreJson.encodeToString(PlayerShortcutConfig.serializer(), config),
        )

        assertEquals(config, decoded)
        assertEquals(listOf(ctrlK), decoded.shortcutsFor(PlayerShortcutAction.TOGGLE_FULLSCREEN))
        assertEquals(emptyList(), decoded.shortcutsFor(PlayerShortcutAction.TOGGLE_PLAYER_STATS))
    }
}
