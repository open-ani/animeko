/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.mediasource

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import me.him188.ani.datasources.api.DefaultMedia
import me.him188.ani.datasources.api.EpisodeSort
import me.him188.ani.datasources.api.MediaProperties
import me.him188.ani.datasources.api.source.MediaSourceKind
import me.him188.ani.datasources.api.source.MediaSourceLocation
import me.him188.ani.datasources.api.topic.EpisodeRange
import me.him188.ani.datasources.api.topic.FileSize
import me.him188.ani.datasources.api.topic.ResourceLocation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

private fun media(subjectName: String) = DefaultMedia(
    mediaId = "test.$subjectName",
    mediaSourceId = "test",
    originalUrl = "https://example.com/$subjectName",
    download = ResourceLocation.HttpStreamingFile("https://example.com/$subjectName.mp4"),
    originalTitle = subjectName,
    publishedTime = 0,
    properties = MediaProperties(subjectName, null, listOf("CHS"), "1080P", "", FileSize.Unspecified, null),
    episodeRange = EpisodeRange.single(EpisodeSort(1)),
    location = MediaSourceLocation.Online,
    kind = MediaSourceKind.WEB,
)

class KeywordSearchChainTest {
    private class Recorder(private val scope: TestScope) {
        /** 关键词 → 搜索开始时的虚拟时间 */
        val searched = mutableListOf<Pair<String, Long>>()

        /**
         * [cached] 的关键词命中缓存, 其余的向站点搜索. [found] 的关键词搜到一个同名条目.
         */
        fun search(cached: Set<String> = emptySet(), found: Set<String>): suspend KeywordSearchScope.(String) -> List<DefaultMedia> =
            { keyword ->
                searched += keyword to scope.testScheduler.currentTime
                if (keyword !in cached) beforeOnlineSearch()
                if (keyword in found) listOf(media(keyword)) else emptyList()
            }
    }

    private fun matches(target: String): (List<DefaultMedia>) -> Boolean = { list -> list.any { it.properties.subjectName == target } }

    @Test
    fun `every primary keyword is searched and fallbacks stop at the first match`() = runTest {
        val recorder = Recorder(this)
        val source = keywordSearchChain(
            primary = listOf("A", "B"), fallback = listOf("C", "D", "E"), remembered = emptyList(),
            requestInterval = 1.seconds,
            matches = matches("D"),
            search = recorder.search(found = setOf("A", "D")),
        )

        val results = source.results.toList()

        assertEquals(listOf("A", "B", "C", "D"), recorder.searched.map { it.first })
        assertEquals(listOf("A", "D"), results.map { it.properties.subjectName })
        assertTrue(source.finished.first())
        assertEquals(2, source.totalSize.first())
    }

    @Test
    fun `remembered keywords are searched first and the rest are skipped once they match`() = runTest {
        val recorder = Recorder(this)
        val source = keywordSearchChain(
            primary = listOf("A"), fallback = listOf("B", "C"), remembered = listOf("C"),
            requestInterval = 1.seconds,
            matches = matches("C"),
            search = recorder.search(found = setOf("C")),
        )

        source.results.toList()

        assertEquals(listOf("C"), recorder.searched.map { it.first })
    }

    @Test
    fun `online searches are spaced by the interval while cache hits add no delay`() = runTest {
        val recorder = Recorder(this)
        val source = keywordSearchChain(
            primary = listOf("A", "B", "C", "D"), fallback = emptyList(), remembered = emptyList(),
            requestInterval = 3.seconds,
            matches = { false },
            search = recorder.search(cached = setOf("B"), found = emptySet()),
        )

        source.results.toList()

        // A 在线 (首次不等待), B 命中缓存 (不等待), C 在线 (等 3s), D 在线 (再等 3s)
        assertEquals(listOf("A" to 0L, "B" to 0L, "C" to 0L, "D" to 3000L), recorder.searched)
        assertEquals(6000, testScheduler.currentTime)
    }

    @Test
    fun `a failing search propagates and leaves the chain unfinished`() = runTest {
        val source = keywordSearchChain(
            primary = listOf("A", "B"), fallback = emptyList(), remembered = emptyList(),
            requestInterval = 1.seconds,
            matches = { false },
            search = { keyword -> if (keyword == "B") throw IllegalStateException("boom") else emptyList() },
        )

        assertFailsWith<IllegalStateException> { source.results.toList() }
        assertFalse(source.finished.first())
    }
}
