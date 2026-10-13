/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.mediasource.test.jsonapi

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respondError
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import me.him188.ani.app.domain.mediasource.jsonapi.JsonApiChannelFormatNoChannel
import me.him188.ani.app.domain.mediasource.jsonapi.JsonApiMediaSourceEngine
import me.him188.ani.app.domain.mediasource.jsonapi.JsonApiSearchConfig
import me.him188.ani.app.domain.mediasource.jsonapi.SampleSite
import me.him188.ani.app.domain.mediasource.jsonapi.bodyText
import me.him188.ani.app.domain.mediasource.jsonapi.respondJson
import me.him188.ani.app.domain.mediasource.test.TestEpisodeListResult
import me.him188.ani.app.domain.mediasource.test.TestSubjectSearchResult
import me.him188.ani.datasources.api.EpisodeSort
import me.him188.ani.utils.ktor.asScopedHttpClient
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class JsonApiMediaSourceTesterTest {
    private val requests = mutableListOf<HttpRequestData>()
    private val site = MockEngine { request ->
        requests += request
        val url = request.url.toString()
        when {
            url.endsWith("/search") -> respondJson(if ("芙莉莲" in request.bodyText.orEmpty()) SampleSite.SEARCH_RESPONSE else "[]")
            url.contains("/episodes?anime_id=3252") -> respondJson(SampleSite.EPISODES_RESPONSE)
            url.contains("/episodes?") -> respondJson("[]")
            url.endsWith("/playback") -> respondJson(SampleSite.PLAY_RESPONSE)
            else -> respondError(HttpStatusCode.NotFound)
        }
    }

    private fun TestScope.createTester(): JsonApiMediaSourceTester {
        val dispatcher = StandardTestDispatcher(testScheduler)
        return JsonApiMediaSourceTester(
            JsonApiMediaSourceEngine(HttpClient(site).asScopedHttpClient(), ioDispatcher = dispatcher),
            flowContext = dispatcher,
            sharingStarted = SharingStarted.Lazily,
        )
    }

    private fun JsonApiMediaSourceTester.queryFrieren(config: JsonApiSearchConfig = SampleSite.config) {
        setSearchConfig(config)
        setSubjectQuery(JsonApiMediaSourceTester.SubjectQuery("葬送的芙莉莲 第二季", searchUseOnlyFirstWord = false, searchRemoveSpecial = true))
        setEpisodeQuery(JsonApiMediaSourceTester.EpisodeQuery(EpisodeSort(2)))
    }

    @Test
    fun `searches subjects and tags whether the name matches`() = runTest {
        val tester = createTester()
        tester.queryFrieren()

        val result = assertIs<TestSubjectSearchResult.Success>(tester.subjectSelectionResultFlow.filterNotNull().first())

        assertEquals("https://api.example.com/search", result.encodedUrl)
        assertEquals(listOf("葬送的芙莉莲", "葬送的芙莉莲 第二季"), result.subjects.map { it.name })
        assertEquals(listOf(false, true), result.subjects.map { s -> s.tags.first { it.value == "标题" }.isMatch })
        assertEquals("https://example.com/anime/3252", result.subjects[1].subjectDetailsPageUrl)
    }

    @Test
    fun `selecting a subject loads its episodes and marks the requested episode`() = runTest {
        val tester = createTester()
        tester.queryFrieren()
        tester.subjectSelectionResultFlow.filterNotNull().first()

        tester.setSubjectIndex(1)
        val result = assertIs<TestEpisodeListResult.Success>(tester.episodeListSelectionResultFlow.filterNotNull().first())
        val episodes = tester.testEpisodesFlow.first { it.isNotEmpty() }

        assertEquals(listOf("线路A", "线路B", "备用", "线路D"), result.channels)
        assertEquals(20, result.episodes.size)
        assertEquals(result.episodes.map { it.name }, episodes.map { it.presentation.name })
        val second = result.episodes.first { it.name == "02 南方勇者" }
        assertEquals(true, second.tags.first { it.value.startsWith("EP:") }.isMatch)
        val first = result.episodes.first { it.name == "01 那我们走吧" }
        assertEquals(false, first.tags.first { it.value.startsWith("EP:") }.isMatch)
        assertEquals("3252", episodes.first().subject.id)
        assertEquals("A", episodes.first().channelMatch)
    }

    @Test
    fun `selecting another subject never shows the previous subject's episodes under the new one`() = runTest {
        val tester = createTester()
        tester.queryFrieren()
        tester.subjectSelectionResultFlow.filterNotNull().first()
        tester.setSubjectIndex(1)
        tester.testEpisodesFlow.first { it.isNotEmpty() }

        // 条目 26 没有剧集: 切换后第一个出现的列表要么为空, 要么仍标着 3252 (旧响应), 不能把 3252 的剧集标成 26 的
        tester.setSubjectIndex(0)
        val list = tester.testEpisodesFlow.first { episodes -> episodes.isEmpty() || episodes.any { it.subject.id == "26" } }

        assertTrue(list.isEmpty(), "stale episodes were attributed to the new subject: " + list.map { it.subject.id + "/" + it.episode.id })
        val result = assertIs<TestEpisodeListResult.Success>(tester.episodeListSelectionResultFlow.filterNotNull().first { it is TestEpisodeListResult.Success && it.episodes.isEmpty() })
        // 声明的线路与剧集无关, 仍然列出
        assertEquals(4, result.channels?.size)
    }

    @Test
    fun `changing parse config re-parses the cached response without a new request`() = runTest {
        val tester = createTester()
        tester.queryFrieren()
        tester.subjectSelectionResultFlow.filterNotNull().first()
        tester.setSubjectIndex(1)
        tester.episodeListSelectionResultFlow.filterNotNull().first()
        val requestsBefore = requests.size

        val config = SampleSite.config
        tester.setSearchConfig(config.copy(subject = config.subject.copy(channelFormatId = JsonApiChannelFormatNoChannel.id)))
        val result = tester.episodeListSelectionResultFlow.filterNotNull()
            .first { it is TestEpisodeListResult.Success && it.channels == null }

        assertEquals(5, (result as TestEpisodeListResult.Success).episodes.size)
        assertEquals(requestsBefore, requests.size)
    }

    @Test
    fun `viewing an episode issues the play request and selects the channel candidate`() = runTest {
        val tester = createTester()
        tester.queryFrieren()
        tester.subjectSelectionResultFlow.filterNotNull().first()
        tester.setSubjectIndex(1)
        tester.episodeListSelectionResultFlow.filterNotNull().first()
        val episode = tester.testEpisodesFlow.first { it.isNotEmpty() }.first { it.episode.id == "47463" && it.episode.channel == "备用" }

        tester.setPlayEpisode(episode)
        val result = assertIs<JsonApiPlayTestResult.Success>(tester.playResultFlow.filterNotNull().first())

        assertEquals("""{"episode_id":47463}""", result.request.body)
        assertEquals(200, assertNotNull(result.response).status)
        val video = assertNotNull(result.video)
        assertEquals(3, video.candidates.size)
        assertEquals(SampleSite.LINE_C_URL, assertNotNull(video.selected).url)
    }

    @Test
    fun `an invalid request template yields InvalidConfig without a request`() = runTest {
        val tester = createTester()
        val config = SampleSite.config
        tester.queryFrieren(config.copy(search = config.search.copy(request = config.search.request.copy(url = ""))))

        assertEquals(TestSubjectSearchResult.InvalidConfig, tester.subjectSelectionResultFlow.filterNotNull().first())
        assertTrue(requests.isEmpty())
    }

    @Test
    fun `an empty keyword produces no result`() = runTest {
        val tester = createTester()
        tester.setSearchConfig(SampleSite.config)
        tester.setSubjectQuery(JsonApiMediaSourceTester.SubjectQuery("", false, true))
        tester.setEpisodeQuery(JsonApiMediaSourceTester.EpisodeQuery(EpisodeSort(1)))

        assertNull(tester.subjectSelectionResultFlow.first())
        assertTrue(requests.isEmpty())
    }
}
