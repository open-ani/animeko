/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package ui.foundation.effect

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import me.him188.ani.app.platform.window.LocalTitleBarThemeController
import me.him188.ani.app.platform.window.TitleBarThemeController
import me.him188.ani.app.ui.foundation.effects.OverrideCaptionButtonAppearance
import me.him188.ani.app.ui.framework.runAniComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals

class OverrideCaptionButtonAppearanceTest {

    @Test
    fun `test override caption button appearance`() = runAniComposeUiTest {
        val titleBarController = TitleBarThemeController()
        val contentDarModeController = mutableStateOf<Boolean?>(null)
        val appDarkMode = mutableStateOf(false)
        setContent {

            CompositionLocalProvider(
                LocalTitleBarThemeController provides titleBarController,
            ) {
                OverrideCaptionButtonAppearance(appDarkMode.value)
                TestContent(contentDarModeController)
            }
        }
        waitForIdle()
        //app is light in default, it should be false
        runOnUiThread { 
            assertEquals(false, titleBarController.isDark)
        }

        //enter content page and set dark, it should be true
        runOnUiThread {
            contentDarModeController.value = true
        }
        waitForIdle()
        runOnUiThread { assertEquals(true, titleBarController.isDark) }

        // exit content page and restore dark, it should be false
        runOnUiThread {
            contentDarModeController.value = null
        }
        waitForIdle()
        runOnUiThread { assertEquals(false, titleBarController.isDark) }

        //enter content page and set dark, it should be true
        runOnUiThread {
            contentDarModeController.value = true
        }
        waitForIdle()
        runOnUiThread { assertEquals(true, titleBarController.isDark) }

        //change app as dark, it should be true
        runOnUiThread {
            appDarkMode.value = true
        }
        waitForIdle()
        runOnUiThread { assertEquals(true, titleBarController.isDark) }

        // exit content page, it should be true
        runOnUiThread {
            contentDarModeController.value = null
        }
        waitForIdle()
        runOnUiThread {
            assertEquals(true, titleBarController.isDark)
        }

        // enter light content page, it should be false
        runOnUiThread {
            contentDarModeController.value = false
        }
        waitForIdle()
        runOnUiThread { assertEquals(false, titleBarController.isDark) }

        // exit content page, it should be true
        runOnUiThread {
            contentDarModeController.value = null
        }
        waitForIdle()
        runOnUiThread { assertEquals(true, titleBarController.isDark) }

        // change app as light, it should be false
        runOnUiThread {
            appDarkMode.value = false
        }
        waitForIdle()
        runOnUiThread { assertEquals(false, titleBarController.isDark) }
    }

    @Composable
    private fun TestContent(darkMode: State<Boolean?>) {
        when (val currentDark = darkMode.value) {
            null -> {}
            else -> {
                OverrideCaptionButtonAppearance(currentDark)
            }
        }
    }
}
