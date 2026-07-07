/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.subject.details.components

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EpisodePagingTest {
    // capacity 校验
    @Test
    fun `capacity must be at least 1`() {
        assertFailsWith<IllegalArgumentException> { EpisodePaging(totalCount = 10, capacity = 0) }
    }

    // pageCount
    @Test
    fun `page count is ceil of total over capacity`() {
        assertEquals(0, EpisodePaging(0, 12).pageCount)
        assertEquals(1, EpisodePaging(12, 12).pageCount)
        assertEquals(2, EpisodePaging(13, 12).pageCount)
        assertEquals(11, EpisodePaging(128, 12).pageCount) // 柯南场景: 128 集 / 每页 12 -> 11 页
    }

    // isPaged: 不足一页不分页
    @Test
    fun `is paged only when total exceeds one page`() {
        assertFalse(EpisodePaging(12, 12).isPaged)
        assertFalse(EpisodePaging(5, 12).isPaged)
        assertTrue(EpisodePaging(13, 12).isPaged)
    }

    // pageOf
    @Test
    fun `page of item index`() {
        val p = EpisodePaging(128, 12)
        assertEquals(0, p.pageOf(0))
        assertEquals(0, p.pageOf(11))
        assertEquals(1, p.pageOf(12))
        assertEquals(4, p.pageOf(54)) // 第 55 集 (index 54) 落在第 5 页 (0-based 4)
        assertEquals(10, p.pageOf(127))
    }

    @Test
    fun `page of clamps out of range indices`() {
        val p = EpisodePaging(128, 12)
        assertEquals(0, p.pageOf(-5))
        assertEquals(10, p.pageOf(9999))
    }

    // initialPage: 含当前进度集的页
    @Test
    fun `initial page contains current progress episode`() {
        val p = EpisodePaging(128, 12)
        assertEquals(4, p.initialPage(currentIndex = 54)) // 观看到第 55 集 -> 初始展示第 5 页
        assertEquals(0, p.initialPage(currentIndex = 0))
    }

    @Test
    fun `initial page falls back to first when no current episode`() {
        val p = EpisodePaging(128, 12)
        assertEquals(0, p.initialPage(currentIndex = -1))
    }

    // itemRange: 末页截断
    @Test
    fun `item range for full page`() {
        val p = EpisodePaging(128, 12)
        assertEquals(0 until 12, p.itemRange(0))
        assertEquals(48 until 60, p.itemRange(4)) // 第 5 页 = 第 49~60 集
    }

    @Test
    fun `item range truncates last page`() {
        val p = EpisodePaging(128, 12) // 128 = 10*12 + 8
        assertEquals(120 until 128, p.itemRange(10)) // 末页仅 8 集
    }

    @Test
    fun `item range clamps page and handles empty`() {
        val p = EpisodePaging(128, 12)
        assertEquals(0 until 12, p.itemRange(-3))
        assertEquals(120 until 128, p.itemRange(99))
        assertEquals(IntRange.EMPTY, EpisodePaging(0, 12).itemRange(0))
    }
}
