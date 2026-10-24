/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.foundation.widgets

import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ToastQueueTest {
    /**
     * 是否正在显示, 以及显示的文字.
     */
    private val ToastQueue.state get() = showing.value to content.value

    /**
     * 让正在显示的 toast 显示完并淡出.
     */
    private fun TestScope.finishCurrentToast() {
        advanceTimeBy(ToastShowDuration + ToastFadeDuration)
        runCurrent()
    }

    @Test
    fun `texts are shown one after another for the full duration`() = runTest {
        val queue = ToastQueue(backgroundScope)
        queue.show("a")
        queue.show("b")
        runCurrent()
        assertEquals(true to "a", queue.state)

        advanceTimeBy(ToastShowDuration)
        runCurrent()
        assertEquals(false to "a", queue.state)

        advanceTimeBy(ToastFadeDuration)
        runCurrent()
        assertEquals(true to "b", queue.state)

        finishCurrentToast()
        assertEquals(false to "b", queue.state)
    }

    @Test
    fun `same text queued while showing is shown once`() = runTest {
        val queue = ToastQueue(backgroundScope)
        queue.show("a")
        runCurrent()
        queue.show("a")
        queue.show("a")

        finishCurrentToast()
        assertEquals(false to "a", queue.state)
        finishCurrentToast()
        assertEquals(false to "a", queue.state)
    }

    @Test
    fun `same text after the previous one finished is shown again`() = runTest {
        val queue = ToastQueue(backgroundScope)
        queue.show("a")
        runCurrent()
        finishCurrentToast()
        assertEquals(false to "a", queue.state)

        queue.show("a")
        runCurrent()
        assertEquals(true to "a", queue.state)
    }

    @Test
    fun `different text between duplicates keeps its turn`() = runTest {
        val queue = ToastQueue(backgroundScope)
        queue.show("a")
        runCurrent()
        queue.show("a")
        queue.show("b")
        queue.show("a")

        finishCurrentToast()
        assertEquals(true to "b", queue.state)
        finishCurrentToast()
        assertEquals(true to "a", queue.state)
        finishCurrentToast()
        assertEquals(false to "a", queue.state)
    }
}
