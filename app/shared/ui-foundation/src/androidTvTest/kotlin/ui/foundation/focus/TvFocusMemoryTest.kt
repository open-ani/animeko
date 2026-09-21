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
import kotlin.test.assertTrue

/** [TvFocusMemory] 协议状态机 (协议全貌见 TvFocusMemory.kt 文件头). */
class TvFocusMemoryTest {

    @Test
    fun `report then arm exposes last id as pending (Armed)`() {
        val memory = TvFocusMemory()
        memory.reportFocused(FocusRequester(), "rec-1")
        memory.armFromLast()
        assertEquals("rec-1", memory.pendingRestoreId)
        assertFalse(memory.restoreDone)
    }

    @Test
    fun `arm with no reported id yields no pending`() {
        val memory = TvFocusMemory()
        memory.armFromLast()
        assertNull(memory.pendingRestoreId)
        assertFalse(memory.restoreDone)
    }

    @Test
    fun `claim before resume only registers, activate performs the restore (Done)`() {
        val memory = TvFocusMemory()
        memory.reportFocused(FocusRequester(), "rec-1")
        memory.armFromLast()
        memory.claimRestore("rec-1", FocusRequester())
        // 转场未完成: 只登记, 不恢复
        assertFalse(memory.restoreDone)
        assertTrue(memory.activate())
        assertNull(memory.pendingRestoreId)
        assertTrue(memory.restoreDone)
    }

    @Test
    fun `late claim after resume restores immediately`() {
        val memory = TvFocusMemory()
        memory.reportFocused(FocusRequester(), "rec-1")
        memory.armFromLast()
        assertFalse(memory.activate()) // RESUMED 时无认领 -> 调用方落默认锚点
        memory.claimRestore("rec-1", FocusRequester()) // 迟到数据: live 态即时恢复
        assertTrue(memory.restoreDone)
        assertNull(memory.pendingRestoreId)
    }

    @Test
    fun `mismatched claim is ignored (stays Armed)`() {
        val memory = TvFocusMemory()
        memory.reportFocused(FocusRequester(), "rec-1")
        memory.armFromLast()
        memory.claimRestore("rec-2", FocusRequester())
        assertEquals("rec-1", memory.pendingRestoreId)
        assertFalse(memory.activate())
        assertFalse(memory.restoreDone)
    }

    @Test
    fun `user interaction cancels claim and pending`() {
        val memory = TvFocusMemory()
        memory.reportFocused(FocusRequester(), "rec-1")
        memory.armFromLast()
        memory.claimRestore("rec-1", FocusRequester())
        memory.onUserInteraction()
        assertFalse(memory.activate())
        assertFalse(memory.restoreDone)
    }

    @Test
    fun `re-arm resets Done and live for the new round`() {
        val memory = TvFocusMemory()
        memory.reportFocused(FocusRequester(), "rec-1")
        memory.armFromLast()
        memory.claimRestore("rec-1", FocusRequester())
        memory.activate()
        assertTrue(memory.restoreDone)
        memory.armFromLast()
        assertFalse(memory.restoreDone)
        // 新一轮: live 已复位, 认领重新等待 RESUMED
        memory.claimRestore("rec-1", FocusRequester())
        assertFalse(memory.restoreDone)
    }

    @Test
    fun `id-less report participates in same-page restore but not cross-route`() {
        val memory = TvFocusMemory()
        memory.reportFocused(FocusRequester(), id = null)
        memory.armFromLast()
        assertNull(memory.pendingRestoreId)
    }

    @Test
    fun `clear drops everything`() {
        val memory = TvFocusMemory()
        memory.reportFocused(FocusRequester(), "rec-1")
        memory.armFromLast()
        memory.clear()
        assertNull(memory.last)
        assertNull(memory.lastId)
        assertNull(memory.pendingRestoreId)
        assertFalse(memory.restoreDone)
        assertFalse(memory.restore())
    }
}
