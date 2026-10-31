/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.settings.tabs.app

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.lifecycle.compose.LocalLifecycleOwner
import me.him188.ani.app.data.models.preference.PlayerShortcut
import me.him188.ani.app.data.models.preference.PlayerShortcutAction
import me.him188.ani.app.data.models.preference.PlayerShortcutConfig
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.foundation.navigation.LocalOnBackPressedDispatcherOwner
import me.him188.ani.app.ui.foundation.navigation.OnBackPressedDispatcher
import me.him188.ani.app.ui.foundation.navigation.SkikoOnBackPressedDispatcherOwner
import me.him188.ani.app.ui.framework.AniComposeUiTest
import me.him188.ani.app.ui.framework.runAniComposeUiTest
import me.him188.ani.app.ui.settings.framework.SettingsState
import me.him188.ani.app.ui.settings.framework.rememberTestSettingsState
import me.him188.ani.utils.platform.annotations.TestOnly
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(TestOnly::class)
class PlayerShortcutsTabTest {
    private class Fixture(val state: SettingsState<PlayerShortcutConfig>) {
        /** 系统返回 (桌面端为没有节点消费的 Escape) 经它分发; 没有启用的处理器时计入 [hostBackPresses], 相当于退出页面. */
        var hostBackPresses = 0
        val backDispatcher = OnBackPressedDispatcher(fallbackOnBackPressed = { hostBackPresses++ })
        val config: PlayerShortcutConfig get() = state.value
    }

    private fun runTabTest(
        initial: PlayerShortcutConfig = PlayerShortcutConfig.Default,
        block: AniComposeUiTest.(Fixture) -> Unit,
    ) = runAniComposeUiTest {
        lateinit var fixture: Fixture
        setContent {
            ProvideCompositionLocalsForPreview {
                val state = rememberTestSettingsState(initial)
                fixture = remember { Fixture(state) }
                val lifecycleOwner = LocalLifecycleOwner.current
                val backOwner = remember { SkikoOnBackPressedDispatcherOwner(fixture.backDispatcher, lifecycleOwner) }
                CompositionLocalProvider(LocalOnBackPressedDispatcherOwner provides backOwner) {
                    Column {
                        PlayerShortcutsResetAction(state)
                        PlayerShortcutsTab(state)
                    }
                }
            }
        }
        waitForIdle()
        block(fixture)
    }

    /**
     * 打开 [action] 的编辑对话框. 采集区在对话框打开时自动获得焦点, 之后的按键直接发给它.
     */
    private fun AniComposeUiTest.openEditor(action: PlayerShortcutAction) {
        onNodeWithTag(PlayerShortcutsTestTags.item(action)).performClick()
        waitForIdle()
        onNodeWithTag(PlayerShortcutsTestTags.CAPTURE).assertIsDisplayed()
    }

    @Test
    fun `pressing a key in the capture field adds a binding on confirm`() = runTabTest { fixture ->
        openEditor(PlayerShortcutAction.TOGGLE_PAUSE_RESUME)

        onNodeWithTag(PlayerShortcutsTestTags.CAPTURE).performKeyInput { pressKey(Key.K) }
        waitForIdle()
        onNodeWithTag(PlayerShortcutsTestTags.chip(PlayerShortcut("K"))).assertIsDisplayed()
        // nothing is written before confirming
        assertEquals(PlayerShortcutConfig.Default, fixture.config)

        onNodeWithTag(PlayerShortcutsTestTags.CONFIRM).performClick()
        waitUntil {
            fixture.config.shortcutsFor(PlayerShortcutAction.TOGGLE_PAUSE_RESUME) ==
                    listOf(PlayerShortcut("Spacebar"), PlayerShortcut("K"))
        }
    }

    @Test
    fun `modifier combinations are captured and conflicts are reassigned`() = runTabTest { fixture ->
        openEditor(PlayerShortcutAction.TOGGLE_DANMAKU)

        onNodeWithTag(PlayerShortcutsTestTags.CAPTURE).performKeyInput {
            keyDown(Key.CtrlLeft)
            pressKey(Key.K)
            keyUp(Key.CtrlLeft)
            pressKey(Key.F) // currently fullscreen
        }
        waitForIdle()
        onNodeWithTag(PlayerShortcutsTestTags.chip(PlayerShortcut("K", ctrl = true))).assertIsDisplayed()
        onNodeWithTag(PlayerShortcutsTestTags.HINT).assertIsDisplayed()

        onNodeWithTag(PlayerShortcutsTestTags.CONFIRM).performClick()
        waitUntil {
            fixture.config.shortcutsFor(PlayerShortcutAction.TOGGLE_DANMAKU) ==
                    listOf(PlayerShortcut("B"), PlayerShortcut("K", ctrl = true), PlayerShortcut("F"))
        }
        assertEquals(emptyList(), fixture.config.shortcutsFor(PlayerShortcutAction.TOGGLE_FULLSCREEN))
    }

    @Test
    fun `clicking a chip removes it and unsupported keys show a hint`() = runTabTest { fixture ->
        openEditor(PlayerShortcutAction.TOGGLE_FULLSCREEN)

        onNodeWithTag(PlayerShortcutsTestTags.chip(PlayerShortcut("F"))).performClick()
        waitForIdle()
        // clicking the chip took focus; the user clicks the capture field again
        onNodeWithTag(PlayerShortcutsTestTags.CAPTURE).requestFocus()
        onNodeWithTag(PlayerShortcutsTestTags.CAPTURE).performKeyInput { pressKey(Key.Enter) }
        waitForIdle()
        onNodeWithTag(PlayerShortcutsTestTags.HINT).assertIsDisplayed()

        onNodeWithTag(PlayerShortcutsTestTags.CONFIRM).performClick()
        waitUntil { fixture.config.shortcutsFor(PlayerShortcutAction.TOGGLE_FULLSCREEN).isEmpty() }
    }

    @Test
    fun `navigation keys are not captured and show no hint`() = runTabTest { fixture ->
        openEditor(PlayerShortcutAction.TOGGLE_FULLSCREEN)

        onNodeWithTag(PlayerShortcutsTestTags.CAPTURE).performKeyInput {
            pressKey(Key.Back)
            pressKey(Key.Escape)
            pressKey(Key.Tab)
        }
        waitForIdle()
        onNodeWithTag(PlayerShortcutsTestTags.HINT).assertDoesNotExist()
        assertEquals(PlayerShortcutConfig.Default, fixture.config)
    }

    @Test
    fun `cancel discards pending changes`() = runTabTest { fixture ->
        openEditor(PlayerShortcutAction.TOGGLE_FULLSCREEN)

        onNodeWithTag(PlayerShortcutsTestTags.CAPTURE).performKeyInput { pressKey(Key.K) }
        waitForIdle()
        onNodeWithTag(PlayerShortcutsTestTags.CANCEL).performClick()
        waitForIdle()

        assertEquals(PlayerShortcutConfig.Default, fixture.config)
    }

    @Test
    fun `back closes the editor without saving and without leaving the page`() = runTabTest { fixture ->
        openEditor(PlayerShortcutAction.TOGGLE_FULLSCREEN)

        onNodeWithTag(PlayerShortcutsTestTags.CAPTURE).performKeyInput { pressKey(Key.K) }
        waitForIdle()
        runOnIdle { fixture.backDispatcher.onBackPressed() }
        waitForIdle()

        onNodeWithTag(PlayerShortcutsTestTags.CAPTURE).assertDoesNotExist()
        assertEquals(PlayerShortcutConfig.Default, fixture.config)
        assertEquals(0, fixture.hostBackPresses)

        // without a dialog, back goes to the host as usual
        runOnIdle { fixture.backDispatcher.onBackPressed() }
        runOnIdle { assertEquals(1, fixture.hostBackPresses) }
    }

    @Test
    fun `back closes the reset all confirmation without leaving the page`() = runTabTest(
        initial = PlayerShortcutConfig.Default.withShortcuts(PlayerShortcutAction.TOGGLE_FULLSCREEN, listOf(PlayerShortcut("K"))),
    ) { fixture ->
        onNodeWithTag(PlayerShortcutsTestTags.RESET_ALL).performClick()
        waitForIdle()
        onNodeWithTag(PlayerShortcutsTestTags.RESET_ALL_CONFIRM).assertIsDisplayed()

        runOnIdle { fixture.backDispatcher.onBackPressed() }
        waitForIdle()

        onNodeWithTag(PlayerShortcutsTestTags.RESET_ALL_CONFIRM).assertDoesNotExist()
        assertEquals(0, fixture.hostBackPresses)
        assertEquals(listOf(PlayerShortcut("K")), fixture.config.shortcutsFor(PlayerShortcutAction.TOGGLE_FULLSCREEN))
    }

    @Test
    fun `reset in the dialog restores the default binding`() = runTabTest(
        initial = PlayerShortcutConfig.Default.withShortcuts(PlayerShortcutAction.TOGGLE_FULLSCREEN, listOf(PlayerShortcut("K"))),
    ) { fixture ->
        openEditor(PlayerShortcutAction.TOGGLE_FULLSCREEN)

        onNodeWithTag(PlayerShortcutsTestTags.RESET).assertIsEnabled().performClick()
        waitForIdle()
        onNodeWithTag(PlayerShortcutsTestTags.chip(PlayerShortcut("F"))).assertIsDisplayed()
        onNodeWithTag(PlayerShortcutsTestTags.RESET).assertIsNotEnabled()

        onNodeWithTag(PlayerShortcutsTestTags.CONFIRM).performClick()
        waitUntil { fixture.config == PlayerShortcutConfig.Default }
    }

    @Test
    fun `reset all is disabled without overrides and restores everything after confirming`() = runTabTest(
        initial = PlayerShortcutConfig.Default
            .withShortcuts(PlayerShortcutAction.TOGGLE_FULLSCREEN, listOf(PlayerShortcut("K")))
            .withShortcuts(PlayerShortcutAction.VOLUME_UP, emptyList()),
    ) { fixture ->
        onNodeWithTag(PlayerShortcutsTestTags.RESET_ALL).assertIsEnabled().performClick()
        waitForIdle()
        onNodeWithTag(PlayerShortcutsTestTags.RESET_ALL_CONFIRM).performClick()
        waitUntil { fixture.config == PlayerShortcutConfig.Default }

        onNodeWithTag(PlayerShortcutsTestTags.RESET_ALL).assertIsNotEnabled()
    }
}
