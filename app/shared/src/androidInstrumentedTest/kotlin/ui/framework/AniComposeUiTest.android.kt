/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.framework

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.runComposeUiTest
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.resetMain
import kotlin.time.Duration.Companion.minutes

/**
 * 截图当前的 UI 并与 resources 目录下的图片 [expectedResource] 进行比较.
 */
actual fun AniComposeUiTest.assertScreenshot(expectedResource: String) {
}

actual fun ImageBitmap.assertScreenshot(expectedResource: String) {
}

/**
 * 相对于 [runComposeUiTest], 有一些修改:
 * - [ComposeUiTest.waitUntil] 的超时时间更长
 */
@OptIn(DelicateCoroutinesApi::class)
actual fun runAniComposeUiTest(testBody: AniComposeUiTest.() -> Unit) {
    // 一定要调用这个, 否则在跟其他协程测试一起跑的时候, lifecycle 的一个地方会一直 block
    // androidx.lifecycle.MainDispatcherChecker.updateMainDispatcherThread
    Dispatchers.resetMain()


    val testThread = Thread.currentThread()
    var timedOut = false
    val job = GlobalScope.launch {
        delay(1.minutes)
        timedOut = true
        testThread.interrupt()
    }

    try {
        return runComposeUiTest {
            AniComposeUiTestImpl(this).run(testBody)
        }
    } catch (e: InterruptedException) {
        if (timedOut) {
            throw AssertionError("Test timed out after 1 minute")
        } else {
            throw e
        }
    } finally {
        job.cancel()
    }
}

internal class AniComposeUiTestImpl(composeUiTest: ComposeUiTest) : AbstractAniComposeUiTest(composeUiTest) {
    override fun waitUntil(conditionDescription: String?, timeoutMillis: Long, condition: () -> Boolean) =
        composeUiTest.waitUntil(conditionDescription, timeoutMillis, condition)
}
