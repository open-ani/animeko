/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.login

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import me.him188.ani.app.domain.session.auth.OAuthPlatform
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.framework.runAniComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ThirdPartyLoginMethodsTest {
    @Test
    fun `shows one button per platform and reports which one was clicked`() = runAniComposeUiTest {
        val clicked = mutableListOf<OAuthPlatform>()
        setContent {
            ProvideCompositionLocalsForPreview {
                ThirdPartyLoginMethods(listOf(OAuthPlatform.BANGUMI, OAuthPlatform.GITHUB), onClick = { clicked += it })
            }
        }

        onNodeWithTag("thirdPartyLogin-bangumi").assertIsDisplayed()
        onNodeWithTag("thirdPartyLogin-github").assertIsDisplayed().performClick()
        waitForIdle()

        assertEquals(listOf(OAuthPlatform.GITHUB), clicked)
    }

    @Test
    fun `platforms the server did not enable are not shown`() = runAniComposeUiTest {
        setContent {
            ProvideCompositionLocalsForPreview {
                ThirdPartyLoginMethods(listOf(OAuthPlatform.BANGUMI), onClick = {})
            }
        }

        onNodeWithTag("thirdPartyLogin-bangumi").assertIsDisplayed()
        onNodeWithTag("thirdPartyLogin-github").assertDoesNotExist()
    }

    @Test
    fun `nothing is shown without platforms`() = runAniComposeUiTest {
        setContent {
            ProvideCompositionLocalsForPreview {
                ThirdPartyLoginMethods(emptyList(), onClick = {})
            }
        }

        onNodeWithTag("thirdPartyLogin-bangumi").assertDoesNotExist()
    }
}
