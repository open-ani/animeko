/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.repository.subject

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import me.him188.ani.app.data.models.preference.NsfwMode
import me.him188.ani.client.models.AniFavourite
import me.him188.ani.client.models.AniSelfRatingInfo
import me.him188.ani.client.models.AniSubjectCollection
import me.him188.ani.client.models.AniSubjectRelations
import me.him188.ani.client.models.AniSubjectType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SubjectSearchCompletionsTest {
    private val id = 622288
    private val url = "https://bgm.tv/subject/$id"
    private val episodeId = 1741638
    private val episodeUrl = "https://bgm.tv/ep/$episodeId"
    private val subject = AniSubjectCollection(
        id = id.toLong(),
        type = AniSubjectType.ANIME,
        name = "Original title",
        nameCn = "番剧名称",
        summary = "",
        nsfw = false,
        airDate = "",
        aliases = emptyList(),
        favorite = AniFavourite(wish = 0, done = 0, doing = 0, onHold = 0, dropped = 0),
        tags = emptyList(),
        metaTags = emptyList(),
        scoreDetails = emptyMap(),
        selfRating = AniSelfRatingInfo(score = 0, tags = emptyList(), isPrivate = false),
        episodes = emptyList(),
        relations = AniSubjectRelations(id.toLong(), emptyList(), emptyList(), emptyList(), emptyList()),
        imageLarge = "",
        imageThumb = "",
    )

    private suspend fun loadSubjectSearchCompletions(
        query: String,
        nsfwMode: NsfwMode,
        excludedIds: Set<Int>,
        getSubject: suspend (Int) -> AniSubjectCollection?,
        getEpisodeSubjectId: suspend (Int) -> Int? = { null },
        searchKeywords: suspend (String) -> List<String>,
    ) = subjectSearchCompletionsFlow(
        query, nsfwMode, excludedIds, getSubject, getEpisodeSubjectId, searchKeywords,
    ).last()

    @Test
    fun `extracts the first subject path before considering a whole integer`() {
        listOf(
            url,
            "$url/?from=search#episode",
            "推荐：$url 这部番剧",
            "https://other.example/subject/$id",
            "subject/$id",
            "subject/00$id",
            "subject/$id subject/1",
            "123 subject/$id",
            "$id",
            "  $id\n",
            "00$id",
            "+$id",
        ).forEach { assertEquals(id, parseSearchSubjectId(it), it) }
        assertEquals(Int.MAX_VALUE, parseSearchSubjectId(Int.MAX_VALUE.toString()))
    }

    @Test
    fun `invalid nonpositive and overflowing ids remain keywords`() {
        listOf(
            "", " ", "番剧", "622288 番剧", "622288.5", "0", "-1", "2147483648",
            "999999999999999999999999999", "subject/", "subject/-1", "subject/0",
            "subject/2147483648", "subject/2147483648 subject/$id", "Subject/$id",
        ).forEach { assertNull(parseSearchSubjectId(it), it) }
    }

    @Test
    fun `resolved subject returns name id and unmodified input without keyword lookup`() = runTest {
        val input = "  推荐：$url  "
        val result = loadSubjectSearchCompletions(
            input, NsfwMode.HIDE, emptySet(),
            getSubject = {
                assertEquals(id, it)
                subject
            },
            searchKeywords = { error("Resolved ID must not search keywords") },
        )
        assertEquals(listOf("番剧名称", "$id", input), result)
    }

    @Test
    fun `deduplicates candidates in order and normalizes only the id candidate`() = runTest {
        suspend fun completions(input: String, name: String = subject.nameCn) = loadSubjectSearchCompletions(
            input, NsfwMode.HIDE, emptySet(), { subject.copy(nameCn = name) },
        ) { error("Unexpected keyword lookup") }

        assertEquals(listOf("番剧名称", "$id"), completions("$id"))
        assertEquals(listOf("番剧名称", "$id", "00$id"), completions("00$id"))
        assertEquals(listOf("$id"), completions("$id", name = "$id"))
    }

    @Test
    fun `uses original title when chinese title is blank`() = runTest {
        val result = loadSubjectSearchCompletions(
            url, NsfwMode.HIDE, emptySet(), { subject.copy(nameCn = "  ") },
        ) { error("Unexpected keyword lookup") }
        assertEquals(listOf(subject.name, "$id", url), result)
    }

    @Test
    fun `episode paths accept urls and shared text but not bare numbers or invalid ids`() {
        listOf(
            episodeUrl, "$episodeUrl?from=share#comment", "推荐：$episodeUrl", "ep/$episodeId",
            "ep/00$episodeId", "ep/$episodeId ep/2",
        ).forEach { assertEquals(episodeId, parseSearchEpisodeId(it), it) }
        listOf("$episodeId", url, "ep/", "ep/-1", "ep/0", "ep/2147483648", "ep/2147483648 ep/1")
            .forEach { assertNull(parseSearchEpisodeId(it), it) }
    }

    @Test
    fun `episode link resolves parent subject before producing normal candidates`() = runTest {
        val calls = mutableListOf<String>()
        val input = "  推荐：$episodeUrl  "
        val result = loadSubjectSearchCompletions(
            input, NsfwMode.HIDE, emptySet(),
            getSubject = {
                calls += "subject:$it"
                subject
            },
            getEpisodeSubjectId = {
                calls += "episode:$it"
                id
            },
        ) { error("Resolved episode must not search keywords") }
        assertEquals(listOf("episode:$episodeId", "subject:$id"), calls)
        assertEquals(listOf(subject.nameCn, "$id", input), result)
    }

    @Test
    fun `subject links and bare numbers retain priority over episode lookup`() = runTest {
        for (input in listOf("$id", "$episodeUrl $url")) {
            val result = loadSubjectSearchCompletions(
                input, NsfwMode.HIDE, emptySet(), { subject },
                getEpisodeSubjectId = { error("Subject ID takes priority") },
            ) { error("Unexpected fallback") }
            assertEquals(listOf(subject.nameCn, "$id", input).distinct(), result)
        }
    }

    @Test
    fun `missing invalid or failed episode lookup falls back to original input`() = runTest {
        val lookups: List<suspend (Int) -> Int?> = listOf(
            { null }, { 0 }, { -1 }, { throw IllegalStateException("Request failed") },
        )
        for (lookup in lookups) {
            val result = loadSubjectSearchCompletions(
                episodeUrl, NsfwMode.HIDE, emptySet(), { error("No valid subject ID") }, lookup,
            ) {
                assertEquals(episodeUrl, it)
                listOf("fallback")
            }
            assertEquals(listOf("fallback"), result)
        }
    }

    @Test
    fun `episode parents obey exclusion and nsfw filters and missing subject fallback`() = runTest {
        val cases = listOf(
            setOf(id) to subject,
            emptySet<Int>() to subject.copy(nsfw = true),
            emptySet<Int>() to null,
        )
        for ((excluded, parent) in cases) {
            val result = loadSubjectSearchCompletions(
                episodeUrl, NsfwMode.HIDE, excluded,
                getSubject = {
                    assertTrue(excluded.isEmpty())
                    parent
                },
                getEpisodeSubjectId = { id },
            ) {
                assertEquals(episodeUrl, it)
                listOf("fallback")
            }
            assertEquals(listOf("fallback"), result)
        }
    }

    @Test
    fun `episode and parent lookup share the three second head start and can finish later`() = runTest {
        var keywordStartedAt = -1L
        val result = subjectSearchCompletionsFlow(
            episodeUrl, NsfwMode.HIDE, emptySet(),
            getSubject = {
                delay(3_000)
                subject
            },
            getEpisodeSubjectId = {
                delay(3_000)
                id
            },
        ) {
            keywordStartedAt = currentTime
            assertEquals(episodeUrl, it)
            listOf("fallback")
        }.toList()
        assertEquals(
            listOf(listOf("fallback"), listOf(subject.nameCn, "$id", episodeUrl, "fallback")),
            result,
        )
        assertEquals(3_000L, keywordStartedAt)
        assertEquals(6_000L, currentTime)
    }

    @Test
    fun `episode lookup cancellation does not fetch parent or start fallback`() = runTest {
        var cancelled = false
        val job = launch {
            loadSubjectSearchCompletions(
                episodeUrl, NsfwMode.HIDE, emptySet(), { error("Cancelled episode has no parent") },
                getEpisodeSubjectId = {
                    try {
                        awaitCancellation()
                    } finally {
                        cancelled = true
                    }
                },
            ) { error("Cancellation must not trigger fallback") }
        }
        runCurrent()
        job.cancelAndJoin()
        assertTrue(cancelled)
    }

    private suspend fun assertFallback(
        nsfwMode: NsfwMode = NsfwMode.HIDE,
        excludedIds: Set<Int> = emptySet(),
        getSubject: suspend (Int) -> AniSubjectCollection?,
    ) {
        val keywords = mutableListOf<String>()
        val result = loadSubjectSearchCompletions(url, nsfwMode, excludedIds, getSubject) {
            keywords += it
            listOf("关键词候选")
        }
        assertEquals(listOf(url), keywords)
        assertEquals(listOf("关键词候选"), result)
    }

    @Test
    fun `missing subject falls back to the original url`() = runTest {
        // SubjectService 将 HTTP 404 映射为 null。
        assertFallback {
            assertEquals(id, it)
            null
        }
    }

    @Test
    fun `failed request falls back to the original url`() = runTest {
        assertFallback { throw IllegalStateException("Request failed") }
    }

    @Test
    fun `empty titles fall back to keywords`() = runTest {
        assertFallback { subject.copy(nameCn = " ", name = "") }
    }

    @Test
    fun `non anime response cannot decode as the anime only API model and falls back`() = runTest {
        val response = Json.encodeToString(AniSubjectCollection.serializer(), subject)
            .replace("\"ANIME\"", "\"BOOK\"")
        assertFallback { Json.decodeFromString<AniSubjectCollection>(response) }
    }

    @Test
    fun `nsfw candidates obey each existing mode`() = runTest {
        val nsfw = subject.copy(nsfw = true)
        assertFallback(nsfwMode = NsfwMode.HIDE) { nsfw }
        assertFallback(nsfwMode = NsfwMode.BLUR) { nsfw }
        assertEquals(
            listOf(subject.nameCn, "$id", url),
            loadSubjectSearchCompletions(url, NsfwMode.DISPLAY, emptySet(), { nsfw }) {
                error("Visible NSFW subject must resolve")
            },
        )
    }

    @Test
    fun `excluded completed or dropped subject falls back without fetching it`() = runTest {
        assertFallback(excludedIds = setOf(id)) { error("Excluded subject must not be fetched") }
    }

    @Test
    fun `late specific success cancels unfinished keyword request`() = runTest {
        var ordinaryCancelled = false
        var keywordStartedAt = -1L
        val results = subjectSearchCompletionsFlow(
            url, NsfwMode.HIDE, emptySet(), { delay(10_000); subject },
        ) {
            keywordStartedAt = currentTime
            try {
                awaitCancellation()
            } finally {
                ordinaryCancelled = true
            }
        }.toList()
        assertEquals(listOf(listOf(subject.nameCn, "$id", url)), results)
        assertEquals(3_000L, keywordStartedAt)
        assertEquals(10_000L, currentTime)
        assertTrue(ordinaryCancelled)
    }

    @Test
    fun `specific success within three seconds never starts keyword lookup`() = runTest {
        val result = subjectSearchCompletionsFlow(
            "$id", NsfwMode.HIDE, emptySet(), {
                delay(2_999)
                subject
            },
        ) { error("Keyword lookup must not start before three seconds") }.toList()
        assertEquals(listOf(listOf(subject.nameCn, "$id")), result)
        assertEquals(2_999L, currentTime)
    }

    @Test
    fun `ordinary results appear before late specific candidates are prepended and deduplicated`() = runTest {
        val results = mutableListOf<List<String>>()
        var keywordStartedAt = -1L
        val ordinary = listOf("普通候选", subject.nameCn, "$id", url)
        val job = launch {
            subjectSearchCompletionsFlow(
                url, NsfwMode.HIDE, emptySet(), {
                    delay(7_000)
                    subject
                },
            ) {
                keywordStartedAt = currentTime
                delay(1_000)
                ordinary
            }.toList(results)
        }
        advanceTimeBy(2_999)
        runCurrent()
        assertEquals(-1L, keywordStartedAt)
        advanceTimeBy(1)
        runCurrent()
        assertEquals(3_000L, keywordStartedAt)
        assertTrue(results.isEmpty())
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(listOf(ordinary), results)
        assertTrue(job.isActive)
        job.join()
        assertEquals(listOf(ordinary, listOf(subject.nameCn, "$id", url, "普通候选")), results)
        assertEquals(7_000L, currentTime)
    }

    @Test
    fun `early lookup failure starts fallback immediately`() = runTest {
        val result = subjectSearchCompletionsFlow(
            url, NsfwMode.HIDE, emptySet(), {
                delay(100)
                null
            },
        ) {
            assertEquals(100L, currentTime)
            listOf("fallback")
        }.toList()
        assertEquals(listOf(listOf("fallback")), result)
    }

    @Test
    fun `late filtered lookup keeps already displayed ordinary results without duplicates`() = runTest {
        var keywordCalls = 0
        val result = subjectSearchCompletionsFlow(
            url, NsfwMode.HIDE, emptySet(), {
                delay(5_000)
                subject.copy(nsfw = true)
            },
        ) {
            keywordCalls++
            listOf("fallback")
        }.toList()
        assertEquals(listOf(listOf("fallback")), result)
        assertEquals(1, keywordCalls)
    }

    @Test
    fun `ordinary failure does not cancel a successful specific lookup`() = runTest {
        val result = subjectSearchCompletionsFlow(
            url, NsfwMode.HIDE, emptySet(), {
                delay(7_000)
                subject
            },
        ) { throw IllegalStateException("Keyword search failed") }.toList()
        assertEquals(listOf(listOf(subject.nameCn, "$id", url)), result)
        assertEquals(7_000L, currentTime)
    }

    @Test
    fun `both parallel lookups failing propagates the keyword error`() = runTest {
        val error = assertFailsWith<IllegalStateException> {
            subjectSearchCompletionsFlow(
                url, NsfwMode.HIDE, emptySet(), {
                    delay(7_000)
                    error("Subject lookup failed")
                },
            ) { error("Keyword search failed") }.toList()
        }
        assertEquals("Keyword search failed", error.message)
    }

    @Test
    fun `collector cancellation stops both parallel requests`() = runTest {
        var specificCancelled = false
        var ordinaryCancelled = false
        val results = mutableListOf<List<String>>()
        val job = launch {
            subjectSearchCompletionsFlow(
                url, NsfwMode.HIDE, emptySet(), {
                    try {
                        awaitCancellation()
                    } finally {
                        specificCancelled = true
                    }
                },
            ) {
                try {
                    awaitCancellation()
                } finally {
                    ordinaryCancelled = true
                }
            }.toList(results)
        }
        advanceTimeBy(3_000)
        runCurrent()
        job.cancelAndJoin()
        assertTrue(specificCancelled)
        assertTrue(ordinaryCancelled)
        assertTrue(results.isEmpty())
    }

    @Test
    fun `late failure awaits the one already running keyword request`() = runTest {
        var calls = 0
        val result = subjectSearchCompletionsFlow(
            url, NsfwMode.HIDE, emptySet(), {
                delay(4_000)
                null
            },
        ) {
            calls++
            delay(3_000)
            listOf("fallback")
        }.toList()
        assertEquals(listOf(listOf("fallback")), result)
        assertEquals(1, calls)
        assertEquals(6_000L, currentTime)
    }

    @Test
    fun `request cancellation propagates without keyword lookup`() = runTest {
        assertFailsWith<CancellationException> {
            loadSubjectSearchCompletions(
                url, NsfwMode.HIDE, emptySet(), { throw CancellationException("Cancelled") },
            ) { error("Cancellation must not trigger fallback") }
        }
    }

    @Test
    fun `cancelling the caller stops the lookup without keyword fallback`() = runTest {
        var started = false
        var cancelled = false
        val job = launch {
            loadSubjectSearchCompletions(url, NsfwMode.HIDE, emptySet(), {
                started = true
                try {
                    awaitCancellation()
                } finally {
                    cancelled = true
                }
            }) { error("Cancellation must not trigger fallback") }
        }
        runCurrent()
        assertTrue(started)
        job.cancelAndJoin()
        assertTrue(cancelled)
    }

    @Test
    fun `ordinary input uses the existing trimmed keyword query`() = runTest {
        val result = loadSubjectSearchCompletions(
            "  普通关键词  ", NsfwMode.HIDE, emptySet(), { error("Not an ID") },
        ) {
            assertEquals("普通关键词", it)
            listOf("普通候选")
        }
        assertEquals(listOf("普通候选"), result)
    }

    @Test
    fun `keyword failure is propagated to paging error handling`() = runTest {
        assertFailsWith<IllegalStateException> {
            loadSubjectSearchCompletions(url, NsfwMode.HIDE, emptySet(), { null }) {
                throw IllegalStateException("Keyword search failed")
            }
        }
    }
}
