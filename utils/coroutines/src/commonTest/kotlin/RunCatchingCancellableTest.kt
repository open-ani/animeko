/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.utils.coroutines

import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.test.fail

class RunCatchingCancellableTest {
    @Test
    fun `success is wrapped`() {
        assertEquals(Result.success(1), runCatchingCancellable { 1 })
    }

    @Test
    fun `exception is wrapped as failure`() {
        val e = IllegalStateException("boom")
        assertSame(e, runCatchingCancellable<Int> { throw e }.exceptionOrNull())
    }

    @Test
    fun `error is wrapped as failure like runCatching`() {
        val e = NotImplementedError()
        assertSame(e, runCatchingCancellable<Int> { throw e }.exceptionOrNull())
    }

    @Test
    fun `cancellation is rethrown as is`() {
        val e = CancellationException("cancelled")
        val thrown = assertFailsWith<CancellationException> { runCatchingCancellable<Int> { throw e } }
        assertSame(e, thrown)
    }

    @Test
    fun `cancelling the coroutine does not resume after the block`() = runTest {
        val job = launch {
            runCatchingCancellable { awaitCancellation() }
            fail("cancellation must not be swallowed")
        }
        job.cancelAndJoin()
        assertTrue(job.isCancelled)
    }
}
