/*
 * Copyright (C) 2026 OpenAni and contributors.
 * Use of this source code is governed by the GNU AGPLv3 license.
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.framework

import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import org.opentest4j.TestAbortedException

class ComposeUiRuntimeTest {
    @Test
    fun `stub Android runtime aborts UI tests before executing their body`() {
        var executed = false
        assertFailsWith<TestAbortedException> {
            runAniComposeUiTest { executed = true }
        }
        assertFalse(executed)
    }
}
