/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package ui.foundation.widgets

import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import me.him188.ani.app.ui.foundation.ProvideFoundationCompositionLocalsForTest
import me.him188.ani.app.ui.foundation.widgets.ModalSideSheet
import me.him188.ani.app.ui.foundation.widgets.rememberModalSideSheetState
import me.him188.ani.app.ui.framework.runAniComposeUiTest
import kotlin.test.Test
import kotlin.test.assertTrue

private const val TAG = "sheet"

class ModalSideSheetTest {
    @Test
    fun closeInvokesOnDismiss() = runAniComposeUiTest {
        var dismissed = false
        val state = rememberModalSideSheetState()
        setContent {
            ProvideFoundationCompositionLocalsForTest {
                ModalSideSheet(
                    onDismiss = { dismissed = true },
                    state = state,
                    modifier = Modifier.testTag(TAG),
                ) { Box {} }
            }
        }

        runOnIdle { state.close() }
        mainClock.autoAdvance = true
        waitUntil { dismissed }
        assertTrue(dismissed)
    }
}
