/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.tv.ui.foundation.focus

import androidx.compose.ui.focus.FocusRequester
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame

/** [TvFocusMemory] 协议状态机 (协议全貌见 TvFocusMemory.kt 文件头). */
class TvFocusMemoryTest {

    @Test
    fun `report then arm exposes last id as pending`() {
        val memory = TvFocusMemory()
        val requester = FocusRequester()
        memory.reportFocused(requester, "rec-1")
        memory.armFromLast()
        assertEquals("rec-1", memory.pendingRestoreId)
    }

    @Test
    fun `arm with no reported id yields no pending`() {
        val memory = TvFocusMemory()
        memory.armFromLast()
        assertNull(memory.pendingRestoreId)
        assertNull(memory.takePendingRestore())
    }

    @Test
    fun `matching claim is returned by take and ends the round`() {
        val memory = TvFocusMemory()
        memory.reportFocused(FocusRequester(), "rec-1")
        memory.armFromLast()
        val recreated = FocusRequester()
        memory.claimPendingRestore("rec-1", recreated)
        assertSame(recreated, memory.takePendingRestore())
        // 一轮结束: pending 清空, 再取无结果
        assertNull(memory.pendingRestoreId)
        assertNull(memory.takePendingRestore())
    }

    @Test
    fun `mismatched claim is ignored and take clears pending`() {
        val memory = TvFocusMemory()
        memory.reportFocused(FocusRequester(), "rec-1")
        memory.armFromLast()
        memory.claimPendingRestore("rec-2", FocusRequester())
        assertNull(memory.takePendingRestore())
        assertNull(memory.pendingRestoreId)
    }

    @Test
    fun `id-less report participates in same-page restore but not cross-route`() {
        val memory = TvFocusMemory()
        memory.reportFocused(FocusRequester(), id = null)
        memory.armFromLast()
        assertNull(memory.pendingRestoreId)
    }

    @Test
    fun `clear drops both requester and id`() {
        val memory = TvFocusMemory()
        memory.reportFocused(FocusRequester(), "rec-1")
        memory.clear()
        assertNull(memory.last)
        assertNull(memory.lastId)
        assertFalse(memory.restore())
    }

    @Test
    fun `restore without memory reports failure for caller fallback`() {
        assertFalse(TvFocusMemory().restore())
    }
}
