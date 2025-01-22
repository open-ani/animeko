/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.datasources.api.paging

import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import me.him188.ani.test.TestContainer
import kotlin.test.Test
import kotlin.test.assertEquals

abstract class PagedSourceTest {
    @TestContainer
    class SinglePagedSourceTest : PagedSourceTest() {
        override fun createSource(): PagedSource<Int> = SinglePagePagedSource {
            flowOf(1, 2, 3)
        }
    }

    @TestContainer
    class PageBasedPagedSourceTest : PagedSourceTest() {
        override fun createSource(): PagedSource<Int> = PageBasedPagedSource {
            if (it == 0) {
                setTotalSize(3)
                Paged(listOf(1, 2, 3))
            } else {
                Paged.empty()
            }
        }
    }

    @Test
    fun `initial page is 0`() {
        val source = createSource()

        assertEquals(0, source.currentPage.value)
    }

    @Test
    fun `initial finished is false`() {
        val source = createSource()

        assertEquals(false, source.finished.value)
    }

    @Test
    fun `get first page`() = runTest {
        val source = createSource()

        assertEquals(listOf(1, 2, 3), source.nextPageOrNull())
    }

    @Test
    fun `get second page returns null`() = runTest {
        val source = createSource()

        source.nextPageOrNull()
        assertEquals(null, source.nextPageOrNull())
    }

    @Test
    fun `dont finish after first page`() = runTest {
        val source = createSource()

        assertEquals(listOf(1, 2, 3), source.nextPageOrNull())
        assertEquals(false, source.finished.value)
    }

    @Test
    fun `finish after second nextPageOrNull`() = runTest {
        val source = createSource()

        source.nextPageOrNull()
        source.nextPageOrNull()

        assertEquals(true, source.finished.value)
    }

    @Test
    fun `collect results`() = runTest {
        val source = createSource()

        assertEquals(listOf(1, 2, 3), source.results.toList())
    }

    @Test
    fun `finished after results completed`() = runTest {
        val source = createSource()

        assertEquals(listOf(1, 2, 3), source.results.toList())
        assertEquals(true, source.finished.value)
    }

    @Test
    fun `size not known initially`() = runTest {
        val source = createSource()

        assertEquals(null, source.totalSize.value)
    }

    @Test
    fun `size known after first page`() = runTest {
        val source = createSource()

        source.nextPageOrNull()
        assertEquals(3, source.totalSize.value)
    }

    @Test
    fun `skip to page 1 then empty`() = runTest {
        val source = createSource()

        source.skipToPage(1)
        assertEquals(null, source.nextPageOrNull())
        assertEquals(null, source.totalSize.value)
        assertEquals(true, source.finished.value)
    }

    @Test
    fun `back to previous`() = runTest {
        val source = createSource()

        assertEquals(listOf(1, 2, 3), source.nextPageOrNull())
        source.backToPrevious()
        assertEquals(0, source.currentPage.value)
        assertEquals(3, source.totalSize.value)
        assertEquals(false, source.finished.value)

        assertEquals(listOf(1, 2, 3), source.nextPageOrNull())
        assertEquals(1, source.currentPage.value)
        assertEquals(false, source.finished.value)

        assertEquals(null, source.nextPageOrNull())
        assertEquals(1, source.currentPage.value)
        assertEquals(true, source.finished.value)
    }

    @Test
    fun `back to previous after finish`() = runTest {
        val source = createSource()

        assertEquals(listOf(1, 2, 3), source.nextPageOrNull())
        assertEquals(null, source.nextPageOrNull())
        source.backToPrevious()
        assertEquals(0, source.currentPage.value)
        assertEquals(3, source.totalSize.value)
        assertEquals(false, source.finished.value)

        assertEquals(listOf(1, 2, 3), source.nextPageOrNull())
        assertEquals(1, source.currentPage.value)
        assertEquals(false, source.finished.value)

        assertEquals(null, source.nextPageOrNull())
        assertEquals(1, source.currentPage.value)
        assertEquals(true, source.finished.value)
    }

    abstract fun createSource(): PagedSource<Int>
}