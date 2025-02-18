/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.framework

import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runComposeUiTest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.resetMain
import kotlin.coroutines.CoroutineContext

actual fun runAniComposeUiTest(
    effectContext: CoroutineContext,
    testBody: AniComposeUiTest.() -> Unit
) {
    Dispatchers.resetMain()

    runComposeUiTest {
        try {
            testBody()
        } catch (e: Throwable) {
            // Add screenshot of current state
            try {
                onRoot().assertScreenshot("")
            } catch (extraInfo: Throwable) {
                e.addSuppressed(extraInfo) // Will contain base64 of screenshot
                throw e
            }
            throw e
        }
    }
}