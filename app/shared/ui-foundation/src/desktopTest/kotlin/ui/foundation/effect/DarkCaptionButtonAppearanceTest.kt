/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package ui.foundation.effect

import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.onNodeWithText
import kotlinx.coroutines.delay
import me.him188.ani.app.platform.window.LocalTitleBarThemeController
import me.him188.ani.app.platform.window.TitleBarThemeController
import me.him188.ani.app.ui.foundation.effects.DarkCaptionButtonAppearance
import me.him188.ani.app.ui.framework.runAniComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds

class DarkCaptionButtonAppearanceTest {

    @Test
    fun `test dark caption button appearance`() = runAniComposeUiTest {
        val titleBarController = TitleBarThemeController()
        setContent {
            val owner = remember { Any() }
            var currentDark by remember { mutableStateOf(false) }
            DisposableEffect(titleBarController, owner, currentDark) {
                titleBarController.requestTheme(owner = owner, isDark = currentDark)
                onDispose {
                    titleBarController.removeTheme(owner = owner)
                }
            }
            LaunchedEffect(owner) {
                delay(6.seconds)
                currentDark = true
            }
            CompositionLocalProvider(
                LocalTitleBarThemeController provides titleBarController,
            ) {
                TestContent()
            }
        }

        //app theme is light. it should be false
        runOnIdle {
            onNodeWithText("page 1").assertExists()
            assertEquals(false, titleBarController.isDark)
        }
        //app theme is light, but show page 2. it should be true
        mainClock.advanceTimeBy(2000L)
        runOnIdle {
            onNodeWithText("page 2").assertExists()
            assertEquals(true, titleBarController.isDark)
        }

        // app theme is light. it should be false.
        mainClock.advanceTimeBy(2200L)
        runOnIdle {
            onNodeWithText("page 1").assertExists()
            assertEquals(false, titleBarController.isDark)
        }

        // app whole theme changed to dark when 6 second. it should be true.
        mainClock.advanceTimeBy(2000L)
        runOnIdle {
            onNodeWithText("page 2").assertExists()
            assertEquals(true, titleBarController.isDark)
        }

        // app whole theme changed to dark when 6 second. it should be true.
        mainClock.advanceTimeBy(2000L)
        runOnIdle {
            onNodeWithText("page 1").assertExists()
            assertEquals(true, titleBarController.isDark)
        }
    }

    @Composable
    private fun TestContent() {
        val items = remember { mutableStateListOf(0) }
        when (items.lastOrNull()) {
            0 -> {
                Text("page 1")
                LaunchedEffect(Unit) {
                    delay(1.seconds)
                    items.add(1)
                }
            }

            1 -> {
                Text("page 2")
                DarkCaptionButtonAppearance()
                LaunchedEffect(Unit) {
                    delay(3.seconds)
                    items.removeLast()
                }
            }
        }
    }
}
