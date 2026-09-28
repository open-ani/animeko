/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.framework

import androidx.activity.ComponentActivity
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.test.AndroidComposeUiTest
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runAndroidComposeUiTest
import androidx.compose.ui.test.runComposeUiTest
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.resetMain
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration.Companion.minutes


@Deprecated(
    "This function may be affected by different window sizes. Use assertScreenshot on node instead.",
    ReplaceWith("onNodeWithTag(\"YOUR_TAG\").assertScreenshot(expectedResource)"),
    level = DeprecationLevel.ERROR,
)
actual fun AniComposeUiTest.assertScreenshot(expectedResource: String) {
}

actual fun ImageBitmap.assertScreenshot(expectedResource: String) {
}

actual fun SemanticsNodeInteraction.assertScreenshot(expectedResource: String) {
}

/**
 * 相对于 [runComposeUiTest], 有一些修改:
 * - [ComposeUiTest.waitUntil] 的超时时间更长
 * - 测试 Activity 的窗口获得焦点后才运行 [testBody]
 */
@OptIn(DelicateCoroutinesApi::class)
actual fun runAniComposeUiTest(effectContext: CoroutineContext, testBody: AniComposeUiTest.() -> Unit) {
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

    runAndroidComposeUiTest<ComponentActivity>(effectContext = effectContext) {
        try {
            awaitWindowFocus()
            testBody()
        } catch (e: InterruptedException) {
            if (timedOut) {
                throw AssertionError("Test timed out after 1 minute")
            } else {
                throw e
            }
        } catch (e: Throwable) {
            // Add screenshot of current state
            try {
                onRoot().assertScreenshot("")
            } catch (extraInfo: Throwable) {
                e.addSuppressed(extraInfo) // Will contain base64 of screenshot
                throw e
            }
            throw e
        } finally {
            job.cancel()
        }
    }
}

/**
 * 窗口焦点由系统在 Activity 启动后异步授予, 不在 [ComposeUiTest.waitForIdle] 等待的范围内.
 * 依赖窗口焦点的界面在此之前不响应按键, 例如 TV 焦点作用域在窗口获得焦点后才分配初始焦点.
 * 测试若在此之前发送按键, 结果取决于设备快慢.
 */
private fun AndroidComposeUiTest<ComponentActivity>.awaitWindowFocus() {
    waitUntil("the test activity's window gains focus", timeoutMillis = 10_000) {
        runOnUiThread { activity?.hasWindowFocus() == true }
    }
}
