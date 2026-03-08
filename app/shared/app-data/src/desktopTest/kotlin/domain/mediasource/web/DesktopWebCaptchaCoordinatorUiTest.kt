/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.mediasource.web

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import me.him188.ani.app.ui.framework.exists
import me.him188.ani.app.ui.framework.runAniComposeUiTest
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals

class DesktopWebCaptchaCoordinatorUiTest {
    @Test
    fun `dialog content shows top bar with host and actions`() = runAniComposeUiTest {
        setContent {
            DesktopCaptchaDialogContent(
                pageUrl = "https://anime.girigirilove.icu/search/-------------/?wd=frieren",
                onDismiss = {},
                onConfirm = {},
            ) {
                Box(Modifier.fillMaxSize())
            }
        }

        runOnIdle {
            assertTrue(onNodeWithText("返回").exists())
            assertTrue(onNodeWithText("anime.girigirilove.icu").exists())
            assertTrue(onNodeWithText("✓").exists())
        }
    }

    @Test
    fun `dialog content actions invoke callbacks`() = runAniComposeUiTest {
        var dismissCount = 0
        var confirmCount = 0

        setContent {
            DesktopCaptchaDialogContent(
                pageUrl = "https://example.com/challenge",
                onDismiss = { dismissCount++ },
                onConfirm = { confirmCount++ },
            ) {
                Box(Modifier.fillMaxSize())
            }
        }

        onNodeWithText("返回").performClick()
        onNodeWithText("✓").performClick()

        runOnIdle {
            assertEquals(1, dismissCount)
            assertEquals(1, confirmCount)
        }
    }
}
