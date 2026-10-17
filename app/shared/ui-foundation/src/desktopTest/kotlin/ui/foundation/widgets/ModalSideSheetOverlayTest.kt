/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.foundation.widgets

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import me.him188.ani.app.ui.framework.runAniComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ModalSideSheetOverlayTest {
    @Test
    fun `overlay fills the whole window instead of the sheet`() = runAniComposeUiTest {
        setContent {
            ModalSideSheet(
                onDismiss = {},
                modifier = Modifier.width(100.dp),
                overlay = { Box(Modifier.fillMaxSize().testTag("overlay")) },
            ) {
                Box(Modifier.fillMaxSize().testTag("content"))
            }
        }
        waitForIdle()

        val content = onNodeWithTag("content").getUnclippedBoundsInRoot()
        val overlay = onNodeWithTag("overlay").getUnclippedBoundsInRoot()
        assertEquals(100.dp, content.right - content.left)
        assertTrue(overlay.right - overlay.left > 100.dp, "overlay width = ${overlay.right - overlay.left}")
    }
}
