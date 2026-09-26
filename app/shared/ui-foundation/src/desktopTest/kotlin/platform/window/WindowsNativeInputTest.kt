/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.platform.window

import org.junit.jupiter.api.Assumptions.assumeTrue
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.fail

/**
 * Runs the OS-level input scenarios of [WindowsNativeInputVerification] against the Gradle
 * classpath. The same scenarios run against the packaged app in the `windows-native-input-test`
 * CI verify task, where ProGuard and jpackage output is the thing under test.
 *
 * Every scenario needs an interactive Windows session, the window in the foreground and a working
 * touch injection device. Anything else is reported as skipped rather than failed, so the suite
 * stays green on machines without a desktop.
 */
class WindowsNativeInputTest {
    @Test
    fun `injected touch tap reaches compose as touch press and release`() = runScenario(::verifyTouchTap)

    @Test
    fun `injected pinch reaches compose with both contacts in every event`() = runScenario(::verifyPinch)

    @Test
    fun `os mouse click still arrives as a mouse pointer`() = runScenario(::verifyMouseClick)

    /**
     * The CI task must not report a machine that cannot host the scenarios as a regression; it is
     * the only caller that has no test framework to skip for it.
     */
    @Test
    fun `runAll reports unavailable instead of failing when the host cannot host the scenarios`() {
        assumeTrue(!System.getProperty("os.name").startsWith("Windows"), "covers the non-Windows path")
        assertIs<WindowsNativeInputVerificationResult.Unavailable>(WindowsNativeInputVerification.runAll())
    }

    private fun runScenario(scenario: (WindowsNativeInputHarness) -> Unit) {
        try {
            withWindowsNativeInputHarness(scenario)
        } catch (e: WindowsNativeInputUnavailableException) {
            assumeTrue(false, e.message)
        } catch (e: WindowsNativeInputAssertionException) {
            fail(e.message, e)
        }
    }
}
