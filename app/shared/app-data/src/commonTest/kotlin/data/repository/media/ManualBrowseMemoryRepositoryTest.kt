/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.repository.media

import app.cash.turbine.test
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import me.him188.ani.app.data.persistent.DataStoreJson
import me.him188.ani.app.data.persistent.MemoryDataStore
import me.him188.ani.datasources.api.EpisodeSort
import me.him188.ani.datasources.api.EpisodeType
import me.him188.ani.datasources.api.source.BrowseSubject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ManualBrowseMemoryRepositoryTest {
    private fun createRepository(
        initial: ManualBrowseMemories = ManualBrowseMemories.Empty,
    ) = ManualBrowseMemoryRepositoryImpl(MemoryDataStore(initial))

    private fun memory(
        mediaSourceId: String = "web-a",
        subjectUrl: String = "https://example.com/subject/1",
        channelIndex: Int = 0,
        channelName: String? = "线路1",
        episodeIndex: Int = 2,
        episodeSort: EpisodeSort? = EpisodeSort(3),
        playedAsSort: EpisodeSort = EpisodeSort(3),
    ) = ManualBrowseMemory(
        mediaSourceId = mediaSourceId,
        subject = BrowseSubject(name = "孤独摇滚", url = subjectUrl),
        channelIndex = channelIndex,
        channelName = channelName,
        episodeIndex = episodeIndex,
        episodeSort = episodeSort,
        playedAsSort = playedAsSort,
    )

    @Test
    fun `memories are isolated by subjectId`() = runTest {
        val repository = createRepository()
        val first = memory(subjectUrl = "https://example.com/subject/1")
        val second = memory(subjectUrl = "https://example.com/subject/2")

        repository.set(SUBJECT_A, first)
        repository.set(SUBJECT_B, second)

        assertEquals(first, repository.get(SUBJECT_A))
        assertEquals(second, repository.get(SUBJECT_B))
        assertEquals(first, repository.flow(SUBJECT_A).first())
        assertNull(repository.get(SUBJECT_A + SUBJECT_B))
        assertNull(repository.flow(SUBJECT_A + SUBJECT_B).first())
    }

    @Test
    fun `set replaces the whole memory of the subject`() = runTest {
        val repository = createRepository()
        repository.set(SUBJECT_A, memory(channelIndex = 0, episodeIndex = 2))

        val updated = memory(channelIndex = 1, channelName = "线路2", episodeIndex = 5, playedAsSort = EpisodeSort(6))
        repository.set(SUBJECT_A, updated)

        assertEquals(updated, repository.get(SUBJECT_A))
    }

    @Test
    fun `remove deletes only the given subject`() = runTest {
        val repository = createRepository()
        val kept = memory(subjectUrl = "https://example.com/subject/2")
        repository.set(SUBJECT_A, memory())
        repository.set(SUBJECT_B, kept)

        repository.remove(SUBJECT_A)

        assertNull(repository.get(SUBJECT_A))
        assertEquals(kept, repository.get(SUBJECT_B))
        // 删除不存在的条目不报错
        repository.remove(SUBJECT_A)
        assertNull(repository.get(SUBJECT_A))
    }

    @Test
    fun `removeIf deletes only when the memory still equals the expected value`() = runTest {
        val repository = createRepository()
        val old = memory(channelIndex = 0)
        val new = memory(channelIndex = 1, channelName = "线路2")
        repository.set(SUBJECT_A, old)
        repository.set(SUBJECT_B, old)

        repository.set(SUBJECT_A, new)
        assertFalse(repository.removeIf(SUBJECT_A, old), "记忆已被改写, 不删")
        assertEquals(new, repository.get(SUBJECT_A))

        assertTrue(repository.removeIf(SUBJECT_B, old))
        assertNull(repository.get(SUBJECT_B))
        assertFalse(repository.removeIf(SUBJECT_B, old), "已不存在")
    }

    @Test
    fun `setIf writes only when the memory still equals the expected value`() = runTest {
        val repository = createRepository()
        val old = memory(channelIndex = 0)
        val rewritten = memory(channelIndex = 1, channelName = "线路2")
        val updated = memory(channelIndex = 0, episodeIndex = 3, playedAsSort = EpisodeSort(4))
        repository.set(SUBJECT_A, old)

        assertTrue(repository.setIf(SUBJECT_A, expected = old, memory = updated))
        assertEquals(updated, repository.get(SUBJECT_A))

        repository.set(SUBJECT_A, rewritten)
        assertFalse(repository.setIf(SUBJECT_A, expected = updated, memory = old), "记忆已被改写, 不写")
        assertEquals(rewritten, repository.get(SUBJECT_A))

        assertFalse(repository.setIf(SUBJECT_B, expected = old, memory = updated), "不存在的条目不写")
        assertNull(repository.get(SUBJECT_B))
    }

    @Test
    fun `flow emits on changes of the subject only`() = runTest {
        val repository = createRepository()
        val first = memory()

        repository.flow(SUBJECT_A).test {
            assertNull(awaitItem())

            repository.set(SUBJECT_A, first)
            assertEquals(first, awaitItem())

            // 别的条目变化不触发
            repository.set(SUBJECT_B, memory(subjectUrl = "https://example.com/subject/2"))
            expectNoEvents()

            // 写入相等的记忆不触发
            repository.set(SUBJECT_A, first.copy())
            expectNoEvents()

            repository.remove(SUBJECT_A)
            assertNull(awaitItem())
        }
    }

    @Test
    fun `json round trip keeps every field`() {
        val memories = ManualBrowseMemories(
            mapOf(
                SUBJECT_A to memory(
                    channelName = null,
                    episodeSort = null,
                    playedAsSort = EpisodeSort(25),
                ),
                SUBJECT_B to memory(
                    channelIndex = 3,
                    channelName = "线路4",
                    episodeIndex = 0,
                    episodeSort = EpisodeSort(1, EpisodeType.SP),
                    playedAsSort = EpisodeSort(1, EpisodeType.SP),
                ),
            ),
        )

        val encoded = DataStoreJson.encodeToString(ManualBrowseMemories.serializer(), memories)
        val decoded = DataStoreJson.decodeFromString(ManualBrowseMemories.serializer(), encoded)

        assertEquals(memories, decoded)
        assertEquals(EpisodeSort(25), decoded.bySubjectId.getValue(SUBJECT_A).playedAsSort)
        assertNull(decoded.bySubjectId.getValue(SUBJECT_A).episodeSort)
        assertEquals(EpisodeSort(1, EpisodeType.SP), decoded.bySubjectId.getValue(SUBJECT_B).episodeSort)
    }

    @Test
    fun `empty memories round trip`() {
        val encoded = DataStoreJson.encodeToString(ManualBrowseMemories.serializer(), ManualBrowseMemories.Empty)
        assertEquals(
            ManualBrowseMemories.Empty,
            DataStoreJson.decodeFromString(ManualBrowseMemories.serializer(), encoded),
        )
        assertEquals(ManualBrowseMemories.Empty, DataStoreJson.decodeFromString(ManualBrowseMemories.serializer(), "{}"))
    }

    private companion object {
        const val SUBJECT_A = 100
        const val SUBJECT_B = 200
    }
}
