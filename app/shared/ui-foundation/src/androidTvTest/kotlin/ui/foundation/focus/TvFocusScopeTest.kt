/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.tv.ui.foundation.focus

import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

private enum class Keys : TvFocusKey { A, B }

/** [TvFocusScope] 的事件驱动状态机 (送焦分发在 Resolver 组合内, 此处测非组合部分). */
class TvFocusScopeTest {

    @Test
    fun `each request has a separate cancellation identity`() {
        val scope = TvFocusScope()
        scope.request(Keys.A)
        assertSame(scope.targetOf(Keys.A), scope.pending?.target?.invoke())
        val first = scope.pending
        scope.request(Keys.A)
        assertNotSame(first, scope.pending)
    }

    @Test
    fun `later request replaces the earlier one`() {
        val scope = TvFocusScope()
        scope.request(Keys.A)
        scope.request(Keys.B)
        assertSame(scope.targetOf(Keys.B), scope.pending?.target?.invoke())
    }

    @Test
    fun `user navigation cancels the pending request and bumps generation`() {
        val scope = TvFocusScope()
        scope.request(Keys.A)
        val gen = scope.userNavGeneration
        scope.notifyUserNavigation()
        assertNull(scope.pending)
        assertEquals(gen + 1, scope.userNavGeneration)
    }

    @Test
    fun `later requests cancel suspended preparation`() = runTest {
        val focus = TvFocusScope()
        val data = CompletableDeferred<Unit>()
        val work = launch { focus.requestPrepared { data.await(); Keys.A } }
        runCurrent()
        focus.request(Keys.B)
        Snapshot.sendApplyNotifications()
        runCurrent()
        assertTrue(work.isCompleted)
        data.complete(Unit)
        runCurrent()
        assertSame(focus.targetOf(Keys.B), focus.pending?.target?.invoke())
    }

    @Test
    fun `navigation cancels preparation including work after early delivery`() = runTest {
        val focus = TvFocusScope()
        val work = launch { focus.requestPrepared { focusNow(Keys.A); CompletableDeferred<Unit>().await(); null } }
        runCurrent()
        assertSame(focus.targetOf(Keys.A), focus.pending?.target?.invoke())
        focus.notifyUserNavigation()
        Snapshot.sendApplyNotifications()
        runCurrent()
        assertTrue(work.isCompleted)
        assertNull(focus.pending)
    }

    @Test
    fun `parent boundary navigation cancels a child preparation`() = runTest {
        val parent = TvFocusBoundaryState(mutableStateOf(true), null)
        parent.notifyUserNavigation()
        val focus = TvFocusScope(TvFocusBoundaryState(mutableStateOf(true), parent))
        assertEquals(0, focus.userNavGeneration)
        val work = launch { focus.requestPrepared { CompletableDeferred<Unit>().await(); Keys.A } }
        runCurrent()
        parent.notifyUserNavigation()
        Snapshot.sendApplyNotifications()
        runCurrent()
        assertTrue(work.isCompleted)
        assertNull(focus.pending)
    }

    @Test
    fun `leaving a boundary cancels preparation without replay on reactivation`() = runTest {
        val active = mutableStateOf(true)
        val focus = TvFocusScope(TvFocusBoundaryState(active, null))
        val work = launch { focus.requestPrepared { CompletableDeferred<Unit>().await(); Keys.A } }
        runCurrent()
        active.value = false
        Snapshot.sendApplyNotifications()
        runCurrent()
        assertTrue(work.isCompleted)
        active.value = true
        assertNull(focus.pending)
    }

    @Test
    fun `focus bookkeeping tracks gain and loss`() {
        val scope = TvFocusScope()
        scope.onAnchorFocusChanged(Keys.A, true)
        assertTrue(scope.isFocused(Keys.A))
        scope.onAnchorFocusChanged(Keys.A, false)
        assertFalse(scope.isFocused(Keys.A))
    }

    @Test
    fun `grid focus state cancels on later interaction generation`() {
        val scope = TvFocusScope()
        val grid = TvGridFocusState(scope)
        scope.notifyUserNavigation() // 发起切换的那次按键
        grid.focusItem(5)
        assertTrue(grid.switching)
        assertEquals(scope.userNavGeneration, grid.navGenerationAtRequest)
        scope.notifyUserNavigation() // 后续交互 -> 观察方应取消
        assertTrue(scope.userNavGeneration != grid.navGenerationAtRequest)
        grid.cancel()
        assertFalse(grid.switching)
    }

    @Test
    fun `edge request waits for destination grid after asynchronous category change`() {
        val grid = TvGridFocusState(TvFocusScope())
        val source = LazyGridState()
        val destination = LazyGridState()

        grid.focusRowEdge(row = 2, direction = 1, sourceGridState = source)

        assertTrue(grid.switching)
        assertFalse(grid.canResolveIn(source))
        assertTrue(grid.canResolveIn(destination))

        // Cancelling before the destination arrives must also prevent late focus delivery.
        grid.cancel()
        assertFalse(grid.canResolveIn(destination))

        // A later request within the original grid is independent of the cancelled switch.
        grid.focusItem(5)
        assertTrue(grid.canResolveIn(source))
    }
}
