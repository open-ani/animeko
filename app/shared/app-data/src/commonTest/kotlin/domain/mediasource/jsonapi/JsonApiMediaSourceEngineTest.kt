/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.mediasource.jsonapi

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import kotlinx.serialization.json.Json
import me.him188.ani.datasources.api.EpisodeSort
import me.him188.ani.datasources.api.EpisodeType
import me.him188.ani.utils.ktor.asScopedHttpClient
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class JsonApiMediaSourceEngineTest {
    private val engine = JsonApiMediaSourceEngine(HttpClient(MockEngine { error("no network in parsing tests") }).asScopedHttpClient())
    private val config = SampleSite.config
    private val subject = JsonApiSubjectInfo("3252", "葬送的芙莉莲 第二季", "https://example.com/anime/3252")

    private fun json(text: String) = Json.parseToJsonElement(text)

    // region subjects

    @Test
    fun `parseSubjects maps id and title and builds the page url from the template`() {
        val subjects = assertNotNull(engine.parseSubjects(json(SampleSite.SEARCH_RESPONSE), config.search))
        assertEquals(
            listOf(
                JsonApiSubjectInfo("26", "葬送的芙莉莲", "https://example.com/anime/26"),
                JsonApiSubjectInfo("3252", "葬送的芙莉莲 第二季", "https://example.com/anime/3252"),
            ),
            subjects,
        )
    }

    @Test
    fun `parseSubjects treats an object root as a single item and skips items without id or name`() {
        val stage = config.search.copy(selectItems = "$.data", selectId = "$.url", subjectUrlTemplate = "{id}")
        val subjects = assertNotNull(
            engine.parseSubjects(json("""{"data": {"url": "https://a/b", "title": "x"}}"""), stage),
        )
        assertEquals(listOf(JsonApiSubjectInfo("https://a/b", "x", "https://a/b")), subjects)

        assertEquals(
            emptyList(),
            engine.parseSubjects(json("""[{"title": "no id"}, {"id": 1}]"""), config.search),
        )
    }

    @Test
    fun `parseSubjects reads aliases from an array or a single string and leaves out the name itself`() {
        val stage = config.search.copy(selectAliases = "$.aliases")
        val subjects = assertNotNull(
            engine.parseSubjects(
                json(
                    """[{"id": 1, "title": "A", "aliases": ["A", " a-1 ", "", "a-2", "a-1"]},
                        {"id": 2, "title": "B", "aliases": "b-1"},
                        {"id": 3, "title": "C"}]""",
                ),
                stage,
            ),
        )
        assertEquals(listOf(listOf("a-1", "a-2"), listOf("b-1"), emptyList()), subjects.map { it.aliases })
        assertTrue(engine.parseSubjects(json(SampleSite.SEARCH_RESPONSE), config.search)!!.all { it.aliases.isEmpty() })
        assertNull(engine.parseSubjects(json("[]"), config.search.copy(selectAliases = "$[")))
    }

    @Test
    fun `parseSubjects returns null for invalid jsonpath or a template without id`() {
        assertNull(engine.parseSubjects(json("[]"), config.search.copy(selectItems = "$[")))
        assertNull(engine.parseSubjects(json("[]"), config.search.copy(subjectUrlTemplate = "https://a/anime")))
    }

    // endregion

    // region episodes

    @Test
    fun `declared channels replicate every episode per channel with the channel match value`() {
        val channels = assertNotNull(engine.parseChannels(json(SampleSite.EPISODES_RESPONSE), config.subject, subject))

        assertEquals(listOf("线路A", "线路B", "备用", "线路D"), channels.map { it.name })
        assertEquals(listOf("A", "B", "C", "D"), channels.map { it.match })
        for (channel in channels) {
            assertEquals(5, channel.episodes.size)
            assertTrue(channel.episodes.all { it.channel == channel.name && it.channelMatch == channel.match })
        }
        val first = channels[0].episodes[0]
        assertEquals("47463", first.id)
        assertEquals("01 那我们走吧", first.name)
        assertEquals(EpisodeSort(1), first.episodeSortOrEp)
        assertEquals("https://example.com/play/47463", first.url)
    }

    @Test
    fun `episode kind maps specials to special sorts so they do not collide with main episodes`() {
        val episodes = assertNotNull(engine.parseChannels(json(SampleSite.EPISODES_RESPONSE), config.subject, subject))
            .first().episodes

        val sp = episodes.first { it.id == "14369" }
        assertEquals(EpisodeSort("SP1"), sp.episodeSortOrEp)
        assertEquals(EpisodeType.SP, (sp.episodeSortOrEp as EpisodeSort.Special).type)
        assertEquals("SP01 SP01", sp.name)

        // ex 映射为 SP, 与正片第 1 集区分
        val ex = episodes.first { it.id == "14367" }
        assertEquals(EpisodeSort("SP1"), ex.episodeSortOrEp)
        assertFalse(ex.episodeSortOrEp == EpisodeSort(1))
    }

    @Test
    fun `no channel format yields a single unnamed channel`() {
        val stage = config.subject.copy(channelFormatId = JsonApiChannelFormatNoChannel.id)
        val channels = assertNotNull(engine.parseChannels(json(SampleSite.EPISODES_RESPONSE), stage, subject))
        assertEquals(1, channels.size)
        assertNull(channels.single().name)
        assertNull(channels.single().match)
        assertEquals(5, channels.single().episodes.size)
        assertNull(channels.single().episodes.first().channel)
    }

    @Test
    fun `grouped format reads channels from the response and parses sort from the name when selectSort is empty`() {
        val stage = config.subject.copy(
            channelFormatId = JsonApiChannelFormatGrouped.id,
            channelFormatGrouped = JsonApiChannelFormatGrouped.Config(selectChannels = "$.lines[*]", selectChannelName = "$.name"),
            episodes = config.subject.episodes.copy(
                selectEpisodes = "$.episodes[*]",
                selectSort = "",
                selectType = "",
                nameTemplate = "",
                episodeUrlTemplate = "https://site/play/{subjectId}/{id}",
            ),
        )
        val channels = assertNotNull(
            engine.parseChannels(
                json(
                    """{"lines": [
                        {"name": "线路A", "episodes": [{"id": "a1", "title": "第1集"}, {"id": "a2", "title": "第2集"}]},
                        {"name": "线路B", "episodes": [{"id": "b1", "title": "第1集"}]},
                        {"episodes": [{"id": "c1", "title": "没有名字的线路被跳过"}]}
                    ]}""",
                ),
                stage, subject,
            ),
        )
        assertEquals(listOf("线路A", "线路B"), channels.map { it.name })
        assertEquals(listOf("线路A", "线路B"), channels.map { it.match })
        assertEquals(
            listOf(
                JsonApiEpisodeInfo("线路A", "线路A", "第1集", EpisodeSort(1), "a1", "https://site/play/3252/a1"),
                JsonApiEpisodeInfo("线路A", "线路A", "第2集", EpisodeSort(2), "a2", "https://site/play/3252/a2"),
            ),
            channels[0].episodes,
        )
    }

    @Test
    fun `episode names are unique within a channel and blank names fall back to the id`() {
        val stage = config.subject.copy(
            channelFormatId = JsonApiChannelFormatNoChannel.id,
            episodes = config.subject.episodes.copy(selectSort = "", selectType = "", nameTemplate = ""),
        )
        val episodes = assertNotNull(
            engine.parseChannels(
                json("""[{"id": 1, "title": "OVA"}, {"id": 2, "title": "OVA"}, {"id": 3, "title": ""}, {"id": 4}]"""),
                stage, subject,
            ),
        ).single().episodes
        assertEquals(listOf("OVA", "OVA (2)", "3", "4"), episodes.map { it.name })
    }

    @Test
    fun `episode fields read an absolute video url from the list and ignore relative or missing ones`() {
        val stage = config.subject.copy(
            channelFormatId = JsonApiChannelFormatNoChannel.id,
            episodes = config.subject.episodes.copy(selectVideoUrl = "$.video"),
        )
        val episodes = assertNotNull(
            engine.parseChannels(json(SampleSite.EPISODES_WITH_VIDEO_RESPONSE), stage, subject),
        ).single().episodes
        assertEquals(
            listOf(null, SampleSite.EPISODE_2_VIDEO_URL, SampleSite.EPISODE_3_VIDEO_URL, null, null),
            episodes.map { it.videoUrl },
        )

        // 没有配置时不读列表里的地址
        val plain = assertNotNull(
            engine.parseChannels(json(SampleSite.EPISODES_WITH_VIDEO_RESPONSE), stage.copy(episodes = config.subject.episodes), subject),
        ).single().episodes
        assertEquals(listOf(null, null, null, null, null), plain.map { it.videoUrl })
    }

    @Test
    fun `parseChannels returns null for an unknown format or an invalid episode jsonpath`() {
        assertNull(engine.parseChannels(json("[]"), config.subject.copy(channelFormatId = JsonApiFormatId("nope")), subject))
        assertNull(
            engine.parseChannels(
                json("[]"),
                config.subject.copy(episodes = config.subject.episodes.copy(selectId = "$[")),
                subject,
            ),
        )
    }

    // endregion

    // region video

    @Test
    fun `parseVideo picks the candidate whose channel matches`() {
        val result = assertNotNull(engine.parseVideo(json(SampleSite.PLAY_RESPONSE), config.play, "A"))
        assertEquals(3, result.candidates.size)
        assertEquals(JsonApiVideoCandidate("A", SampleSite.LINE_A_URL), result.selected)
        assertFalse(result.isFallback)
    }

    @Test
    fun `parseVideo yields nothing for an unknown channel unless fallback is enabled`() {
        val strict = assertNotNull(
            engine.parseVideo(json(SampleSite.PLAY_RESPONSE), config.play.copy(fallbackToFirstCandidate = false), "nope"),
        )
        assertNull(strict.selected)
        assertFalse(strict.isFallback)

        val lenient = assertNotNull(engine.parseVideo(json(SampleSite.PLAY_RESPONSE), config.play, "nope"))
        assertEquals(JsonApiVideoCandidate("B", SampleSite.LINE_B_URL), lenient.selected)
        assertTrue(lenient.isFallback)
    }

    @Test
    fun `parseVideo takes the first candidate when the media has no channel`() {
        val result = assertNotNull(engine.parseVideo(json(SampleSite.PLAY_RESPONSE), config.play, null))
        assertEquals(JsonApiVideoCandidate("B", SampleSite.LINE_B_URL), result.selected)
    }

    @Test
    fun `parseVideo without a candidate list reads the url from the root`() {
        val stage = config.play.copy(selectCandidates = "", selectVideoUrl = "$.url")
        val result = assertNotNull(engine.parseVideo(json(SampleSite.PLAY_RESPONSE), stage, "A"))
        assertEquals(JsonApiVideoCandidate(null, SampleSite.LINE_B_URL), result.selected)

        assertNull(assertNotNull(engine.parseVideo(json("""{"ok": false}"""), stage, null)).selected)
    }

    @Test
    fun `parseVideo ignores candidates whose url is not absolute`() {
        val result = assertNotNull(
            engine.parseVideo(
                json("""{"candidates": [{"line": "A", "url": "relative.mp4"}, {"line": "B", "url": "https://x/b.mp4"}]}"""),
                config.play, null,
            ),
        )
        assertEquals(listOf(JsonApiVideoCandidate("B", "https://x/b.mp4")), result.candidates)
    }

    // endregion

    // region requests

    @Test
    fun `prepareRequest resolves relative urls against baseUrl and encodes url variables`() {
        val prepared = assertNotNull(
            engine.prepareRequest(
                config.copy(baseUrl = "https://api.example.com/"),
                JsonApiRequestConfig(url = "/rest/v1/episodes?anime_id=eq.{id}&q={name}"),
                mapOf("id" to "3252", "name" to "a b&c"),
            ),
        )
        assertEquals("https://api.example.com/rest/v1/episodes?anime_id=eq.3252&q=a%20b%26c", prepared.url)
        assertEquals(JsonApiHttpMethod.GET, prepared.method)
        assertNull(prepared.body)

        val absolute = assertNotNull(
            engine.prepareRequest(config, JsonApiRequestConfig(url = "https://other.example.com/x/{id}"), mapOf("id" to "1")),
        )
        assertEquals("https://other.example.com/x/1", absolute.url)
    }

    @Test
    fun `prepareRequest renders the body with json escaping and rejects a body that is not valid json`() {
        val prepared = assertNotNull(
            engine.prepareRequest(
                config,
                config.search.request,
                mapOf("keyword" to "芙莉莲 \"S2\"", "page" to "1", "pageSize" to "5"),
            ),
        )
        assertEquals(
            """{"keyword":"芙莉莲 \"S2\"","page":1,"pageSize":5}""",
            prepared.body,
        )
        assertEquals("https://api.example.com/search", prepared.url)

        // 字符串 id 代入了数字位置
        assertNull(
            engine.prepareRequest(config, config.play.request, mapOf("id" to "abc")),
        )
    }

    @Test
    fun `prepareRequest drops the body of a GET request and substitutes variables in header values`() {
        val request = JsonApiRequestConfig(url = "/x", method = JsonApiHttpMethod.GET, headers = "X-Id: {id}", body = """{"id": {id}}""")
        val get = assertNotNull(engine.prepareRequest(config, request, mapOf("id" to "3")))
        assertNull(get.body)
        assertTrue(("X-Id" to "3") in get.headers)

        val post = assertNotNull(engine.prepareRequest(config, request.copy(method = JsonApiHttpMethod.POST), mapOf("id" to "3")))
        assertEquals("""{"id": 3}""", post.body)
    }

    @Test
    fun `prepareRequest merges common and request headers case-insensitively`() {
        val prepared = assertNotNull(
            engine.prepareRequest(
                config.copy(headers = "apikey: common\nX-A: 1", userAgent = " UA/1 "),
                JsonApiRequestConfig(url = "/x", headers = "APIKEY: specific\nX-B: 2"),
                emptyMap(),
            ),
        )
        assertEquals(listOf("APIKEY" to "specific", "X-A" to "1", "X-B" to "2"), prepared.headers)
        assertEquals("UA/1", prepared.userAgent)
    }

    @Test
    fun `prepareRequest returns null for an empty url or an unresolvable relative url without baseUrl`() {
        assertNull(engine.prepareRequest(config, JsonApiRequestConfig(url = ""), emptyMap()))
        assertNull(engine.prepareRequest(config.copy(baseUrl = ""), JsonApiRequestConfig(url = "/x"), emptyMap()))
    }

    // endregion
}
