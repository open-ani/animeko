/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ios

import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readString
import me.him188.ani.utils.logging.IosLoggingConfigurator
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUUID
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.getUnhandledExceptionHook
import kotlin.native.setUnhandledExceptionHook
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertNotNull
import kotlin.test.assertSame

@OptIn(ExperimentalNativeApi::class)
class IosUnhandledExceptionHookTest {
    @Test
    fun `hook uses writers configured after installation and flushes before delegation`() {
        val originalHook = getUnhandledExceptionHook()
        val logsDir = Path(NSTemporaryDirectory(), "uncaught-${NSUUID().UUIDString}")
        val failure = IllegalArgumentException("render failure", IllegalStateException("original cause"))
        var delegated: Throwable? = null
        try {
            setUnhandledExceptionHook {
                // Read before the hook returns: the fatal path cannot rely on a later flush.
                val logFile = SystemFileSystem.list(logsDir).single()
                val text = SystemFileSystem.source(logFile).buffered().use { it.readString() }
                assertContains(text, "Uncaught Kotlin exception, terminating")
                assertContains(text, "IllegalArgumentException: render failure")
                assertContains(text, "IllegalStateException: original cause")
                assertContains(text, "IosUnhandledExceptionHookTest")
                delegated = it
            }
            installUnhandledExceptionHook()
            IosLoggingConfigurator.configure(logsDir, SystemFileSystem)

            assertNotNull(getUnhandledExceptionHook()).invoke(failure)

            assertSame(failure, delegated)
        } finally {
            setUnhandledExceptionHook(originalHook)
        }
    }
}
