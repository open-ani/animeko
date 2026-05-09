/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.repository.player

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import me.him188.ani.app.data.models.danmaku.DanmakuRegexFilter
import me.him188.ani.app.data.persistent.MemoryDataStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DanmakuRegexFilterRepositoryTest {
    private fun createRepository(
        initial: List<DanmakuRegexFilter> = emptyList(),
    ): DanmakuRegexFilterRepositoryImpl {
        return DanmakuRegexFilterRepositoryImpl(MemoryDataStore(initial))
    }

    private fun createFilter(
        id: String = "1",
        name: String = "",
        regex: String = "test",
        enabled: Boolean = true,
    ) = DanmakuRegexFilter(id = id, name = name, regex = regex, enabled = enabled)

    // add

    @Test
    fun `add appends filter`() = runTest {
        val repo = createRepository()
        val filter = createFilter()

        repo.add(filter)

        assertEquals(listOf(filter), repo.flow.first())
    }

    @Test
    fun `add preserves existing filters`() = runTest {
        val f1 = createFilter(id = "1", regex = "a")
        val f2 = createFilter(id = "2", regex = "b")
        val repo = createRepository(listOf(f1))

        repo.add(f2)

        assertEquals(listOf(f1, f2), repo.flow.first())
    }

    // remove

    @Test
    fun `remove deletes filter by identity`() = runTest {
        val f1 = createFilter(id = "1", regex = "a")
        val f2 = createFilter(id = "2", regex = "b")
        val repo = createRepository(listOf(f1, f2))

        repo.remove(f1)

        assertEquals(listOf(f2), repo.flow.first())
    }

    // update

    @Test
    fun `update replaces filter with same id`() = runTest {
        val f1 = createFilter(id = "1", regex = "a")
        val repo = createRepository(listOf(f1))
        val updated = createFilter(id = "1", regex = "updated")

        repo.update("1", updated)

        assertEquals(listOf(updated), repo.flow.first())
    }

    @Test
    fun `update does nothing when id not found`() = runTest {
        val f1 = createFilter(id = "1", regex = "a")
        val repo = createRepository(listOf(f1))

        repo.update("nonexistent", createFilter(id = "nonexistent"))

        assertEquals(listOf(f1), repo.flow.first())
    }

    // replaceAll

    @Test
    fun `replaceAll clears and sets new filters`() = runTest {
        val f1 = createFilter(id = "1", regex = "old1")
        val f2 = createFilter(id = "2", regex = "old2")
        val repo = createRepository(listOf(f1, f2))
        val newList = listOf(createFilter(id = "3", regex = "new"))

        repo.replaceAll(newList)

        assertEquals(newList, repo.flow.first())
    }

    @Test
    fun `replaceAll with empty list clears filters`() = runTest {
        val f1 = createFilter(id = "1")
        val repo = createRepository(listOf(f1))

        repo.replaceAll(emptyList())

        assertEquals(emptyList(), repo.flow.first())
    }

    // export

    @Test
    fun `export serializes filters to JSON`() = runTest {
        val f1 = createFilter(id = "1", name = "", regex = "pattern1", enabled = true)
        val f2 = createFilter(id = "2", name = "", regex = "pattern2", enabled = false)
        val repo = createRepository(listOf(f1, f2))

        val json = repo.export()

        assertTrue(json.contains("\"id\":\"1\""))
        assertTrue(json.contains("\"regex\":\"pattern1\""))
        assertTrue(json.contains("\"enabled\":false"))
    }

    @Test
    fun `export empty list produces empty JSON array`() = runTest {
        val repo = createRepository()

        val json = repo.export()

        assertEquals("[]", json.trim())
    }

    // import

    @Test
    fun `import deserializes and replaces filters`() = runTest {
        val oldFilter = createFilter(id = "old", regex = "old")
        val repo = createRepository(listOf(oldFilter))
        val json = """[{"id":"new","name":"","regex":"new","enabled":true}]"""

        val result = repo.import(json)

        assertTrue(result)
        val filters = repo.flow.first()
        assertEquals(1, filters.size)
        assertEquals("new", filters[0].id)
        assertEquals("new", filters[0].regex)
    }

    @Test
    fun `import with invalid JSON returns false`() = runTest {
        val repo = createRepository()

        val result = repo.import("not valid json")

        assertFalse(result)
    }

    @Test
    fun `import with valid JSON but wrong structure returns false`() = runTest {
        val repo = createRepository()

        val result = repo.import("""{"not":"a list"}""")

        assertFalse(result)
    }

    // round-trip

    @Test
    fun `export then import preserves filters`() = runTest {
        val f1 = createFilter(id = "1", name = "n1", regex = "r1", enabled = true)
        val f2 = createFilter(id = "2", name = "n2", regex = "r2", enabled = false)
        val repo = createRepository(listOf(f1, f2))

        val json = repo.export()
        // Create a fresh repository to simulate import on a different instance
        val freshRepo = createRepository()
        val result = freshRepo.import(json)

        assertTrue(result)
        val restored = freshRepo.flow.first()
        assertEquals(2, restored.size)
        assertEquals(f1, restored[0])
        assertEquals(f2, restored[1])
    }

    @Test
    fun `import does not leave old filters behind`() = runTest {
        val f1 = createFilter(id = "1", regex = "keep")
        val f2 = createFilter(id = "2", regex = "keep")
        val repo = createRepository(listOf(f1, f2))

        val json = """[{"id":"3","name":"","regex":"new","enabled":true}]"""
        repo.import(json)

        val filters = repo.flow.first()
        assertEquals(1, filters.size)
        assertEquals("3", filters[0].id)
    }
}
