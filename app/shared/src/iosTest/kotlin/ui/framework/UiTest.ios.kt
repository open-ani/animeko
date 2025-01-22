/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.framework

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.runComposeUiTest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.resetMain

/**
 * 相对于 [runComposeUiTest], 有一些修改:
 * - [ComposeUiTest.waitUntil] 的超时时间更长
 */
actual fun runAniComposeUiTest(testBody: AniComposeUiTest.() -> Unit) {
    Dispatchers.resetMain()
    
    runComposeUiTest {
        AniComposeUiTestImpl(this).run(testBody)
    }
}

internal class AniComposeUiTestImpl(composeUiTest: ComposeUiTest) : AbstractAniComposeUiTest(composeUiTest) {
    override fun waitUntil(conditionDescription: String?, timeoutMillis: Long, condition: () -> Boolean) =
        composeUiTest.waitUntil(conditionDescription, timeoutMillis, condition)
}
