/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.mediasource.jsonapi

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respondError
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import me.him188.ani.app.data.repository.RepositoryRateLimitedException
import me.him188.ani.app.domain.mediasource.web.InMemoryWebSearchSessionCacheDao
import me.him188.ani.datasources.api.DefaultMedia
import me.him188.ani.datasources.api.EpisodeSort
import me.him188.ani.datasources.api.source.BrowseChannel
import me.him188.ani.datasources.api.source.BrowseEpisode
import me.him188.ani.datasources.api.source.BrowseSubject
import me.him188.ani.datasources.api.source.MediaFetchRequest
import me.him188.ani.datasources.api.topic.EpisodeRange
import me.him188.ani.datasources.api.topic.ResourceLocation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds

class JsonApiMediaSourceTest {
    /**
     * 模拟站点: 搜索「芙莉莲」得到两个条目, 只有 3252 有剧集, 播放只认 47463.
     */
    private class Site {
        val requests = mutableListOf<HttpRequestData>()
        var rateLimitPlayOnce = false

        /** 条目 3252 的剧集列表响应. */
        var episodesResponse = SampleSite.EPISODES_RESPONSE

        /** 搜索这些词时站点返回空列表, 模拟站点搜索只认短关键词. */
        val emptySearchTerms = mutableSetOf<String>()

        val engine = MockEngine { request ->
            requests += request
            val url = request.url.toString()
            when {
                url == "https://api.example.com/search" && request.method == HttpMethod.Post -> {
                    val body = request.bodyText.orEmpty()
                    val hasResults = ("芙莉莲" in body || "Sousou" in body) && emptySearchTerms.none { "\"keyword\":\"$it\"" in body }
                    respondJson(if (hasResults) SampleSite.SEARCH_RESPONSE else "[]")
                }

                url.startsWith("https://api.example.com/episodes?anime_id=3252") -> respondJson(episodesResponse)
                url.startsWith("https://api.example.com/episodes?anime_id=") -> respondJson("[]")

                url == "https://api.example.com/playback" -> {
                    if (rateLimitPlayOnce) {
                        rateLimitPlayOnce = false
                        return@MockEngine respondError(HttpStatusCode.TooManyRequests)
                    }
                    val body = request.bodyText.orEmpty()
                    respondJson(if ("\"episode_id\":47463" in body) SampleSite.PLAY_RESPONSE else """{"ok": false, "candidates": []}""")
                }

                else -> respondError(HttpStatusCode.NotFound)
            }
        }

        val searchCount get() = requests.count { it.url.encodedPath.endsWith("/search") }
        val episodeRequests get() = requests.filter { it.url.encodedPath.endsWith("/episodes") }.map { it.url.toString() }
        val playCount get() = requests.count { it.url.encodedPath.endsWith("/playback") }
    }

    private val config = SampleSite.config.copy(requestInterval = Duration.ZERO)

    private fun TestScope.createSource(
        searchConfig: JsonApiSearchConfig = config,
        site: Site = Site(),
        cacheDao: InMemoryWebSearchSessionCacheDao? = null,
        getTimeMillis: () -> Long = { testScheduler.currentTime },
    ): Pair<JsonApiMediaSource, Site> {
        val source = if (cacheDao == null) {
            createTestJsonApiMediaSource(searchConfig, site.engine, getTimeMillis = getTimeMillis)
        } else {
            createTestJsonApiMediaSource(searchConfig, site.engine, cacheDao = cacheDao, cacheTtl = 1.hours, getTimeMillis = getTimeMillis)
        }
        return source to site
    }

    private fun request(
        subjectNames: List<String> = listOf(SampleSite.SUBJECT_NAME),
        episode: Int = 1,
        fallbackSearchKeywords: List<String> = emptyList(),
    ) = MediaFetchRequest(
        subjectId = "515759",
        episodeId = episode.toString(),
        subjectNames = subjectNames,
        episodeSort = EpisodeSort(episode),
        episodeName = "",
        fallbackSearchKeywords = fallbackSearchKeywords,
    )

    private suspend fun JsonApiMediaSource.fetchMedia(request: MediaFetchRequest): List<DefaultMedia> =
        fetch(request).results.toList().map { it.media as DefaultMedia }

    private val subject = BrowseSubject(SampleSite.SUBJECT_NAME, "https://example.com/anime/3252")

    // region 浏览

    @Test
    fun `searchSubjects posts the keyword with the configured headers and lists every result`() = runTest {
        val (source, site) = createSource()

        val subjects = source.searchSubjects("葬送的芙莉莲 第二季")

        assertEquals(
            listOf(
                BrowseSubject("葬送的芙莉莲", "https://example.com/anime/26"),
                BrowseSubject("葬送的芙莉莲 第二季", "https://example.com/anime/3252"),
            ),
            subjects,
        )
        val search = site.requests.single()
        assertEquals(HttpMethod.Post, search.method)
        assertEquals(SampleSite.API_KEY, search.headers["apikey"])
        assertEquals("Bearer ${SampleSite.API_KEY}", search.headers["Authorization"])
        // 关键字原样代入, 不做取首词等自动匹配处理
        assertTrue(search.bodyText!!.contains("\"keyword\":\"葬送的芙莉莲 第二季\""))
        assertTrue(search.bodyText!!.contains("\"pageSize\":20"))
    }

    @Test
    fun `browseSubject lists declared channels with every episode and page urls`() = runTest {
        val (source, site) = createSource()

        val channels = source.browseSubject(subject)

        assertEquals(
            "https://api.example.com/episodes?anime_id=3252",
            site.episodeRequests.single(),
        )
        assertEquals(listOf("线路A", "线路B", "备用", "线路D"), channels.map { it.name })
        assertEquals(channels.map { it.name }, channels.map { it.label })
        val first = channels.first()
        assertEquals(
            BrowseEpisode("01 那我们走吧", "https://example.com/play/47463", EpisodeSort(1)),
            first.episodes.first(),
        )
        assertEquals(
            BrowseEpisode("SP01 SP01", "https://example.com/play/14369", EpisodeSort("SP1")),
            first.episodes[3],
        )
    }

    @Test
    fun `browseSubject returns nothing without a request when the subject url does not match the template`() = runTest {
        val (source, site) = createSource()

        assertEquals(emptyList<BrowseChannel>(), source.browseSubject(BrowseSubject("x", "https://elsewhere.example.com/anime/3252")))
        assertTrue(site.requests.isEmpty())
    }

    @Test
    fun `createMedia from browsing produces the same media as auto match`() = runTest {
        val (source, _) = createSource()

        val channels = source.browseSubject(subject)
        val channel = channels[0]
        val browsed = source.createMedia(subject, channel.name, channel.episodes[0], EpisodeSort(1)) as DefaultMedia

        val fetched = source.fetchMedia(request(episode = 1)).first { it.properties.alliance == channel.name }

        assertEquals(fetched, browsed)
        assertEquals("json-api-test.3252-线路A-47463", browsed.mediaId)
        assertEquals(EpisodeRange.single(EpisodeSort(1)), browsed.episodeRange)
        val download = browsed.download as ResourceLocation.WebVideo
        assertEquals("https://example.com/play/47463", download.uri)
        assertEquals(
            mapOf(
                "id" to "47463",
                "url" to "https://example.com/play/47463",
                "name" to "01 那我们走吧",
                "subjectId" to "3252",
                "subjectUrl" to "https://example.com/anime/3252",
                "subjectName" to "葬送的芙莉莲 第二季",
                "channel" to "线路A",
                "channelMatch" to "A",
            ),
            download.apiParameters,
        )
        assertEquals("葬送的芙莉莲 第二季", browsed.properties.subjectName)
        assertEquals("线路A", browsed.properties.alliance)
    }

    @Test
    fun `createMedia without a known episode sort has no episode range`() = runTest {
        val (source, _) = createSource()
        val media = source.createMedia(
            subject, "备用",
            BrowseEpisode("OVA", "https://example.com/play/1", null), null,
        )
        assertNull(media.episodeRange)
        assertEquals("C", (media.download as ResourceLocation.WebVideo).apiParameters!!["channelMatch"])
    }

    @Test
    fun `createMedia rejects urls that do not match the templates`() = runTest {
        val (source, _) = createSource()
        assertFailsWith<IllegalArgumentException> {
            source.createMedia(subject, null, BrowseEpisode("x", "https://example.com/watch/1", null), null)
        }
        assertFailsWith<IllegalArgumentException> {
            source.createMedia(
                BrowseSubject("x", "https://example.com/anime"), null,
                BrowseEpisode("x", "https://example.com/play/1", null), null,
            )
        }
    }

    // endregion

    // region 自动匹配

    @Test
    fun `fetch only loads episodes of subjects whose name matches and returns one media per declared channel`() = runTest {
        val (source, site) = createSource()

        val media = source.fetchMedia(request(subjectNames = listOf("葬送的芙莉莲 第二季"), episode = 1))

        // 「葬送的芙莉莲」(第一季) 名字对不上第二季, 不为它请求剧集
        assertEquals(1, site.searchCount)
        assertEquals(listOf("https://api.example.com/episodes?anime_id=3252"), site.episodeRequests)
        assertEquals(
            listOf("线路A", "线路B", "备用", "线路D"),
            media.map { it.properties.alliance },
        )
        assertTrue(media.all { it.episodeRange == EpisodeRange.single(EpisodeSort(1)) })
        // 特别篇 SP01 / EX01 虽然编号也是 1, 不会被当成第 1 集
        assertTrue(media.none { it.mediaId.endsWith("-14369") || it.mediaId.endsWith("-14367") })
    }

    @Test
    fun `fetch matches a subject by alias and names the media after the matching alias also from the cache`() = runTest {
        val site = Site()
        val source = createTestJsonApiMediaSource(
            config.copy(search = config.search.copy(selectAliases = "$.aliases[*]")),
            site.engine, cacheDao = InMemoryWebSearchSessionCacheDao(), cacheTtl = 1.hours,
            getTimeMillis = { testScheduler.currentTime },
        )
        val names = listOf("Sousou no Frieren 2nd Season")

        val media = source.fetchMedia(request(subjectNames = names, episode = 1))

        assertEquals(4, media.size)
        assertTrue(media.all { it.mediaId.startsWith("json-api-test.3252-") })
        // 资源的条目名是对上的别名, 选择器下游按它过滤时才不会排除
        assertTrue(media.all { it.properties.subjectName == "Sousou no Frieren 2nd Season" })

        val cached = source.fetchMedia(request(subjectNames = names, episode = 2))
        assertEquals(1, site.searchCount)
        assertEquals(4, cached.size)
        assertTrue(cached.all { it.properties.subjectName == "Sousou no Frieren 2nd Season" })
    }

    @Test
    fun `fetch filters by the requested episode`() = runTest {
        val (source, _) = createSource()

        val media = source.fetchMedia(request(episode = 2))

        assertEquals(4, media.size)
        assertTrue(media.all { it.mediaId.endsWith("-47464") && it.episodeRange == EpisodeRange.single(EpisodeSort(2)) })
    }

    @Test
    fun `fetch sends no request when auto match is disabled`() = runTest {
        val (source, site) = createSource(config.copy(autoMatch = config.autoMatch.copy(enabled = false)))
        assertEquals(emptyList(), source.fetchMedia(request()))
        assertTrue(site.requests.isEmpty())
    }

    @Test
    fun `fetch falls back to the next keyword when the primary finds nothing`() = runTest {
        val (source, site) = createSource()
        // 默认 searchUseOnlyFirstWord: 主关键词只取第一个词
        site.emptySearchTerms += "葬送的芙莉莲"

        val media = source.fetchMedia(
            request(subjectNames = listOf(SampleSite.SUBJECT_NAME), fallbackSearchKeywords = listOf("芙莉莲")),
        )

        assertEquals(2, site.searchCount)
        assertTrue(site.requests[1].bodyText!!.contains("\"keyword\":\"芙莉莲\""))
        // 回退关键词只用于搜索, 名称匹配仍以 subjectNames 为准: 第一季被排除
        assertEquals(4, media.size)
        assertTrue(media.all { it.mediaId.startsWith("json-api-test.3252-") })
    }

    @Test
    fun `fallback keywords do not widen name matching`() = runTest {
        val (source, site) = createSource()

        val media = source.fetchMedia(
            request(subjectNames = listOf("不存在的番"), fallbackSearchKeywords = listOf("芙莉莲")),
        )

        assertEquals(2, site.searchCount)
        assertTrue(site.episodeRequests.isEmpty())
        assertEquals(emptyList(), media)
    }

    @Test
    fun `fetch reuses the search cache for another episode of the same subject`() = runTest {
        val dao = InMemoryWebSearchSessionCacheDao()
        val (source, site) = createSource(cacheDao = dao)

        val first = source.fetchMedia(request(episode = 1))
        val second = source.fetchMedia(request(episode = 2))

        assertEquals(4, first.size)
        assertEquals(4, second.size)
        // 第二次完全从缓存构建, 不发请求
        assertEquals(1, site.searchCount)
        assertEquals(1, site.episodeRequests.size)
        // 缓存里还原的资源与在线搜索得到的一致 (含播放参数)
        val cached = second.first { it.properties.alliance == "线路A" }
        assertEquals("json-api-test.3252-线路A-47464", cached.mediaId)
        assertEquals("A", (cached.download as ResourceLocation.WebVideo).apiParameters!!["channelMatch"])
    }

    @Test
    fun `fetch waits requestInterval between consecutive requests`() = runTest {
        val (source, _) = createSource(config.copy(requestInterval = 1.seconds))

        source.fetchMedia(request(episode = 1))

        // 搜索一次 + 剧集一次 = 两个请求, 中间等了一个间隔
        assertEquals(1000, testScheduler.currentTime)
    }

    // endregion

    // region 播放

    @Test
    fun `resolveApiVideo picks the candidate of the media channel and adds player headers`() = runTest {
        val (source, site) = createSource(config.copy(userAgent = "Ani-Test/1"))
        val media = source.fetchMedia(request()).first { it.properties.alliance == "线路A" }

        val video = assertNotNull(source.resolveApiVideo(media))

        assertEquals(SampleSite.LINE_A_URL, video.m3u8Url)
        assertEquals(mapOf("User-Agent" to "Ani-Test/1", "Referer" to "https://example.com/"), video.headers)
        val play = site.requests.last()
        assertEquals("https://api.example.com/playback", play.url.toString())
        assertEquals("""{"episode_id":47463}""", play.bodyText)
    }

    @Test
    fun `video headers are rendered with play variables and override the referer field`() = runTest {
        val (source, _) = createSource(
            config.copy(
                userAgent = "Ani-Test/1",
                play = config.play.copy(
                    videoHeaders = "Origin: https://example.com\nX-Episode: {id}-{channelMatch}\nreferer: https://example.com/override/\nX-Bad: a\u0001b",
                ),
            ),
        )
        val media = source.fetchMedia(request()).first { it.properties.alliance == "线路A" }

        val video = assertNotNull(source.resolveApiVideo(media))

        assertEquals(
            mapOf(
                "User-Agent" to "Ani-Test/1",
                "referer" to "https://example.com/override/",
                "Origin" to "https://example.com",
                "X-Episode" to "47463-A",
            ),
            video.headers,
        )
    }

    @Test
    fun `resolveApiVideo reuses the play response for another channel of the same episode`() = runTest {
        val (source, site) = createSource()
        val media = source.fetchMedia(request())

        val al = assertNotNull(source.resolveApiVideo(media.first { it.properties.alliance == "线路A" }))
        val cs = assertNotNull(source.resolveApiVideo(media.first { it.properties.alliance == "备用" }))

        assertEquals(SampleSite.LINE_A_URL, al.m3u8Url)
        assertEquals(SampleSite.LINE_C_URL, cs.m3u8Url)
        assertEquals(1, site.playCount)
    }

    @Test
    fun `play responses are memoized per request so a channel variable in the body is honoured`() = runTest {
        val play = config.play
        val (source, site) = createSource(
            config.copy(
                play = play.copy(
                    request = play.request.copy(body = """{"episode_id":{id},"line":"{channelMatch}"}"""),
                ),
            ),
        )
        val media = source.fetchMedia(request())

        source.resolveApiVideo(media.first { it.properties.alliance == "线路A" })
        source.resolveApiVideo(media.first { it.properties.alliance == "备用" })
        source.resolveApiVideo(media.first { it.properties.alliance == "线路A" })

        // 两条线路的请求体不同, 各发一次; 同一线路第二次复用
        assertEquals(2, site.playCount)
        assertTrue(site.requests.filter { it.url.encodedPath.endsWith("/playback") }.any { it.bodyText!!.contains("\"line\":\"A\"") })
    }

    @Test
    fun `cache rows written with another episode url template are ignored and the keyword is searched again`() = runTest {
        val dao = InMemoryWebSearchSessionCacheDao()
        val site = Site()
        val first = createTestJsonApiMediaSource(config, site.engine, cacheDao = dao, cacheTtl = 1.hours, getTimeMillis = { testScheduler.currentTime })
        assertEquals(4, first.fetchMedia(request(episode = 1)).size)

        val episodes = config.subject.episodes
        val changed = createTestJsonApiMediaSource(
            config.copy(subject = config.subject.copy(episodes = episodes.copy(episodeUrlTemplate = "https://example.com/watch/{id}"))),
            site.engine, cacheDao = dao, cacheTtl = 1.hours, getTimeMillis = { testScheduler.currentTime },
        )
        val media = changed.fetchMedia(request(episode = 2))

        assertEquals(4, media.size)
        assertTrue(media.all { it.originalUrl == "https://example.com/watch/47464" })
        assertEquals(2, site.searchCount)
    }

    @Test
    fun `resolveApiVideo requests again after the play response expires`() = runTest {
        val (source, site) = createSource()
        val media = source.fetchMedia(request()).first()

        source.resolveApiVideo(media)
        testScheduler.advanceTimeBy(JsonApiMediaSource.PLAY_RESPONSE_TTL.inWholeMilliseconds + 1)
        source.resolveApiVideo(media)

        assertEquals(2, site.playCount)
    }

    @Test
    fun `resolveApiVideo yields null when the site has no candidate for the channel`() = runTest {
        val (source, _) = createSource()
        val media = source.createMedia(
            subject, "备用",
            BrowseEpisode("x", "https://example.com/play/47464", null), null,
        )
        assertNull(source.resolveApiVideo(media))
    }

    @Test
    fun `resolveApiVideo falls back to the first candidate for a declared line the site does not offer`() = runTest {
        val (lenient, _) = createSource()
        val (strict, _) = createSource(config.copy(play = config.play.copy(fallbackToFirstCandidate = false)))
        val media = lenient.createMedia(
            subject, "线路D",
            BrowseEpisode("01", "https://example.com/play/47463", EpisodeSort(1)), EpisodeSort(1),
        )

        assertEquals(SampleSite.LINE_B_URL, assertNotNull(lenient.resolveApiVideo(media)).m3u8Url)
        assertNull(strict.resolveApiVideo(media))
    }

    @Test
    fun `resolveApiVideo reads the url from the episode list and sends no play request when the list carries it`() = runTest {
        val site = Site().apply { episodesResponse = SampleSite.EPISODES_WITH_VIDEO_RESPONSE }
        val (source, _) = createSource(SampleSite.listVideoConfig.copy(requestInterval = Duration.ZERO, userAgent = "Ani-Test/1"), site)
        val media = source.fetchMedia(request(episode = 2)).single()

        val video = assertNotNull(source.resolveApiVideo(media))

        assertEquals(SampleSite.EPISODE_2_VIDEO_URL, video.m3u8Url)
        assertEquals(mapOf("User-Agent" to "Ani-Test/1", "Referer" to "https://example.com/"), video.headers)
        assertEquals(0, site.playCount)
        // 查询时取过一次列表, 播放时重取一次, 不用查询时读到的地址
        assertEquals(2, site.episodeRequests.size)
    }

    @Test
    fun `the episode list refetched at play time is shared by episodes of the same subject until it expires`() = runTest {
        val site = Site().apply { episodesResponse = SampleSite.EPISODES_WITH_VIDEO_RESPONSE }
        val (source, _) = createSource(SampleSite.listVideoConfig.copy(requestInterval = Duration.ZERO), site)
        val second = source.fetchMedia(request(episode = 2)).single()
        val third = source.fetchMedia(request(episode = 3)).single()
        val listRequestsAfterFetch = site.episodeRequests.size

        assertEquals(SampleSite.EPISODE_2_VIDEO_URL, assertNotNull(source.resolveApiVideo(second)).m3u8Url)
        assertEquals(SampleSite.EPISODE_3_VIDEO_URL, assertNotNull(source.resolveApiVideo(third)).m3u8Url)
        assertEquals(listRequestsAfterFetch + 1, site.episodeRequests.size)

        testScheduler.advanceTimeBy(JsonApiMediaSource.PLAY_RESPONSE_TTL.inWholeMilliseconds + 1)
        assertEquals(SampleSite.EPISODE_2_VIDEO_URL, assertNotNull(source.resolveApiVideo(second)).m3u8Url)
        assertEquals(listRequestsAfterFetch + 2, site.episodeRequests.size)
    }

    @Test
    fun `an episode without a list url falls back to the play request only when one is configured`() = runTest {
        val site = Site().apply { episodesResponse = SampleSite.EPISODES_WITH_VIDEO_RESPONSE }
        val withoutPlayRequest = SampleSite.listVideoConfig.copy(requestInterval = Duration.ZERO)
        val (strict, _) = createSource(withoutPlayRequest, site)
        val (lenient, _) = createSource(withoutPlayRequest.copy(play = config.play), site)
        // 第 1 集在列表里没有地址
        val media = strict.fetchMedia(request(episode = 1)).single()

        assertNull(strict.resolveApiVideo(media))
        assertEquals(0, site.playCount)

        val video = assertNotNull(lenient.resolveApiVideo(media))
        assertEquals(SampleSite.LINE_B_URL, video.m3u8Url)
        assertEquals(1, site.playCount)
    }

    @Test
    fun `the list url is looked up within the channel of the media`() = runTest {
        val site = Site().apply { episodesResponse = SampleSite.EPISODES_WITH_VIDEO_RESPONSE }
        val (source, _) = createSource(
            config.copy(
                subject = config.subject.copy(episodes = config.subject.episodes.copy(selectVideoUrl = "$.video")),
                play = JsonApiPlayStage(),
            ),
            site,
        )
        val media = source.fetchMedia(request(episode = 2))

        assertEquals(4, media.size)
        val lineD = media.first { it.properties.alliance == SampleSite.LINE_D }
        assertEquals(SampleSite.EPISODE_2_VIDEO_URL, assertNotNull(source.resolveApiVideo(lineD)).m3u8Url)
        assertEquals(0, site.playCount)
    }

    @Test
    fun `resolveApiVideo ignores media that is not resolved by api`() = runTest {
        val (source, site) = createSource()
        val media = source.fetchMedia(request()).first()
        val webView = media.copy(download = ResourceLocation.WebVideo(media.originalUrl))

        assertNull(source.resolveApiVideo(webView))
        assertEquals(0, site.playCount)
    }

    @Test
    fun `a rate limited play request is retried once after the interval`() = runTest {
        val (source, site) = createSource()
        val media = source.fetchMedia(request()).first { it.properties.alliance == "线路A" }
        site.rateLimitPlayOnce = true

        val video = assertNotNull(source.resolveApiVideo(media))

        assertEquals(SampleSite.LINE_A_URL, video.m3u8Url)
        assertEquals(2, site.playCount)
    }

    @Test
    fun `a persistently rate limited request propagates the failure`() = runTest {
        val site = Site()
        val alwaysLimited = MockEngine { respondError(HttpStatusCode.TooManyRequests) }
        val source = createTestJsonApiMediaSource(config, alwaysLimited, getTimeMillis = { testScheduler.currentTime })

        assertFailsWith<RepositoryRateLimitedException> { source.searchSubjects("芙莉莲") }
        assertTrue(site.requests.isEmpty())
    }

    // endregion
}
