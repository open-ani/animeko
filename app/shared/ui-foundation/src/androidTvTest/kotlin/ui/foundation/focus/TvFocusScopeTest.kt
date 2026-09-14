/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.leanback.ui.foundation.focus

import androidx.compose.foundation.lazy.grid.LazyGridState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private enum class Keys : TvFocusKey { A, B }

/** [TvFocusScope] 的事件驱动状态机 (送焦分发在 Resolver 组合内, 此处测非组合部分). */
class TvFocusScopeTest {

    @Test
    fun `request is pending until consumed and re-request bumps sequence`() {
        val scope = TvFocusScope()
        scope.request(Keys.A)
        assertEquals(Keys.A, scope.pending?.first)
        val seq1 = scope.pending?.second
        scope.request(Keys.A)
        assertTrue(scope.pending!!.second > seq1!!)
    }

    @Test
    fun `later request replaces the earlier one`() {
        val scope = TvFocusScope()
        scope.request(Keys.A)
        scope.request(Keys.B)
        assertEquals(Keys.B, scope.pending?.first)
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
    fun `anchor attach bookkeeping tracks attach and detach`() {
        val scope = TvFocusScope()
        assertFalse(scope.isAnchorAttached(Keys.A))
        scope.onAnchorAttached(Keys.A)
        assertTrue(scope.isAnchorAttached(Keys.A))
        scope.onAnchorDetached(Keys.A)
        assertFalse(scope.isAnchorAttached(Keys.A))
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
