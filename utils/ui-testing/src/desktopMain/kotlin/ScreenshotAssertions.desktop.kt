/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.framework

internal actual fun Throwable.guessTestFunctionName(): String? {
    /*
    org.opentest4j.AssertionFailedError: The actual screenshot does not match the expected screenshot. Actual image Base64:
	at org.junit.jupiter.api.AssertionUtils.fail(AssertionUtils.java:38)
	at org.junit.jupiter.api.Assertions.fail(Assertions.java:138)
	at kotlin.test.junit5.JUnit5Asserter.fail(JUnitSupport.kt:56)
	at kotlin.test.AssertionsKt__AssertionsKt.fail(Assertions.kt:562)
	at kotlin.test.AssertionsKt.fail(Unknown Source)
	at me.him188.ani.app.ui.ScreenshotAssertionsKt.assertScreenshot(ScreenshotAssertions.kt:40)
	at ui.subject.episode.EpisodeVideoControllerTest.touch___clickToToggleController___show$lambda$1(EpisodeVideoControllerTest.kt:123)
	at androidx.compose.ui.test.SkikoComposeUiTest$runTest$1$1.invoke(ComposeUiTest.skikoMain.kt:188)
	at androidx.compose.ui.test.SkikoComposeUiTest.withScene(ComposeUiTest.skikoMain.kt:196)
	at androidx.compose.ui.test.SkikoComposeUiTest.access$withScene(ComposeUiTest.skikoMain.kt:116)
	at androidx.compose.ui.test.SkikoComposeUiTest$runTest$1.invoke(ComposeUiTest.skikoMain.kt:187)
	at androidx.compose.ui.test.ComposeRootRegistry.withRegistry(ComposeRootRegistry.skiko.kt:83)
	at androidx.compose.ui.test.SkikoComposeUiTest.runTest(ComposeUiTest.skikoMain.kt:186)
	at androidx.compose.ui.test.ComposeUiTest_skikoMainKt.runSkikoComposeUiTest-Cqks5Fs(ComposeUiTest.skikoMain.kt:75)
	at androidx.compose.ui.test.ComposeUiTest_skikoMainKt.runSkikoComposeUiTest-Cqks5Fs$default(ComposeUiTest.skikoMain.kt:63)
	at ui.subject.episode.EpisodeVideoControllerTest.touch - clickToToggleController - show(EpisodeVideoControllerTest.kt:108)
	at java.base/java.lang.reflect.Method.invoke(Method.java:568)
	at java.base/java.util.ArrayList.forEach(ArrayList.java:1511)
	at java.base/java.util.ArrayList.forEach(ArrayList.java:1511)
     */
    val runTest = stackTrace.indexOfFirst { it.methodName.contains("runAniComposeUiTest") }
    if (runTest == -1) return null
    val testFunction = stackTrace.getOrNull(runTest - 1) ?: return null
    return testFunction.className.substringAfterLast(".") + "." + testFunction.methodName.substringBefore("$")
}
