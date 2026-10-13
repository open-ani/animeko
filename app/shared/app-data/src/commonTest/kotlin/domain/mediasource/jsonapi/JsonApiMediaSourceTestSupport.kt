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
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpResponseData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import me.him188.ani.app.data.persistent.database.dao.WebSearchSessionCacheDao
import me.him188.ani.app.data.repository.media.SelectorMediaSourceEpisodeCacheRepository
import me.him188.ani.app.domain.mediasource.web.NoopWebSearchSessionCacheDao
import me.him188.ani.datasources.api.source.MediaSourceConfig
import me.him188.ani.datasources.api.source.serializeArguments
import me.him188.ani.utils.ktor.asScopedHttpClient
import me.him188.ani.utils.platform.currentTimeMillis
import kotlin.time.Duration

/**
 * 一个虚构站点的接口, 形态取自这类站点的常见做法: 搜索是 POST, 剧集列表是 GET, 播放地址由 POST 签发并带多条线路候选.
 * 条目与剧集数据保留真实形态 (特别篇从 1 重新编号等).
 */
internal object SampleSite {
    const val API = "https://api.example.com"
    const val WEB = "https://example.com"
    const val API_KEY = "test-key"
    const val SUBJECT_ID = "3252"
    const val SUBJECT_NAME = "葬送的芙莉莲 第二季"

    /**
     * 四条声明线路. 播放响应只给前三条候选, [LINE_D] 用来测试线路没有候选的情况.
     */
    const val LINE_A = "线路A"
    const val LINE_B = "线路B"
    const val LINE_C = "备用"
    const val LINE_D = "线路D"

    val config = JsonApiSearchConfig(
        baseUrl = API,
        headers = "apikey: $API_KEY\nAuthorization: Bearer $API_KEY",
        search = JsonApiSearchStage(
            request = JsonApiRequestConfig(
                url = "/search",
                method = JsonApiHttpMethod.POST,
                body = """{"keyword":"{keyword}","page":{page},"pageSize":{pageSize}}""",
            ),
            subjectUrlTemplate = "$WEB/anime/{id}",
        ),
        subject = JsonApiSubjectStage(
            request = JsonApiRequestConfig(url = "/episodes?anime_id={id}"),
            episodes = JsonApiEpisodeFields(episodeUrlTemplate = "$WEB/play/{id}"),
            channelFormatId = JsonApiChannelFormatDeclared.id,
            channelFormatDeclared = JsonApiChannelFormatDeclared.Config(
                listOf(
                    JsonApiDeclaredChannel(LINE_A, "A"),
                    JsonApiDeclaredChannel(LINE_B, "B"),
                    JsonApiDeclaredChannel(LINE_C, "C"),
                    JsonApiDeclaredChannel(LINE_D, "D"),
                ),
            ),
        ),
        play = JsonApiPlayStage(
            request = JsonApiRequestConfig(
                url = "/playback",
                method = JsonApiHttpMethod.POST,
                body = """{"episode_id":{id}}""",
            ),
            selectCandidates = "$.candidates[*]",
            selectCandidateChannel = "$.line",
            selectVideoUrl = "$.url",
            referer = "$WEB/",
        ),
    )

    /**
     * 剧集列表自带视频地址的站点变体: 没有线路, 每集的 `video` 就是可播放地址, 播放阶段只配置 Referer.
     */
    val listVideoConfig = config.copy(
        subject = config.subject.copy(
            episodes = config.subject.episodes.copy(selectVideoUrl = "$.video"),
            channelFormatId = JsonApiChannelFormatNoChannel.id,
        ),
        play = JsonApiPlayStage(referer = "$WEB/"),
    )

    val SEARCH_RESPONSE = """
        [{"id": 26, "title": "葬送的芙莉莲", "title_original": "葬送のフリーレン", "aliases": ["Sousou no Frieren", "葬送的芙莉蓮"], "total_episodes": 28},
         {"id": 3252, "title": "葬送的芙莉莲 第二季", "title_original": "葬送のフリーレン 第2期", "aliases": ["Sousou no Frieren 2nd Season"], "total_episodes": 10}]
    """.trimIndent()

    /**
     * 三集正片加两集特别篇: 特别篇从 1 重新编号, `kind` 区分.
     */
    val EPISODES_RESPONSE = """
        [{"id": 47463, "episode_number": 1.0, "title": "那我们走吧", "kind": "main"},
         {"id": 47464, "episode_number": 2.0, "title": "南方勇者", "kind": "main"},
         {"id": 47465, "episode_number": 3.0, "title": "喜欢的地方", "kind": "main"},
         {"id": 14369, "episode_number": 1.0, "title": "SP01", "kind": "sp"},
         {"id": 14367, "episode_number": 1.0, "title": "EX01", "kind": "ex"}]
    """.trimIndent()

    const val EPISODE_2_VIDEO_URL = "https://cdn-list.example.com/2601/02.mp4?sign=l2b8f0&t=1791853200"
    const val EPISODE_3_VIDEO_URL = "https://cdn-list.example.com/2601/03.m3u8?sign=l3c9a1&t=1791853200"

    /**
     * 同样的剧集, 列表里直接带 `video`: 第 1 集没有, 第 2、3 集是完整地址, SP01 是相对地址 (不算地址).
     */
    val EPISODES_WITH_VIDEO_RESPONSE = """
        [{"id": 47463, "episode_number": 1.0, "title": "那我们走吧", "kind": "main"},
         {"id": 47464, "episode_number": 2.0, "title": "南方勇者", "kind": "main", "video": "$EPISODE_2_VIDEO_URL"},
         {"id": 47465, "episode_number": 3.0, "title": "喜欢的地方", "kind": "main", "video": "$EPISODE_3_VIDEO_URL"},
         {"id": 14369, "episode_number": 1.0, "title": "SP01", "kind": "sp", "video": "/2601/sp01.mp4"},
         {"id": 14367, "episode_number": 1.0, "title": "EX01", "kind": "ex"}]
    """.trimIndent()

    const val LINE_A_URL = "https://cdn-a.example.com/2601/01.mp4?sign=a1c041aab0a234a0&t=1791853200"
    const val LINE_B_URL = "https://cdn-b.example.com/d/2601/01.mp4?sign=DxLRSDPxxCAxjdSR%3D%3A1791853200"
    const val LINE_C_URL = "https://cdn-c.example.com/2601/01/01.m3u8"

    /**
     * 候选顺序故意不同于声明顺序: 第一条候选是线路 B.
     */
    val PLAY_RESPONSE = """
        {"ok": true, "episode_id": 47463, "anime_id": 3252, "expires_at": 1791853200, "url": "$LINE_B_URL",
         "candidates": [
           {"line": "B", "name": "线路B", "url": "$LINE_B_URL"},
           {"line": "A", "name": "线路A", "url": "$LINE_A_URL"},
           {"line": "C", "name": "备用", "url": "$LINE_C_URL"}]}
    """.trimIndent()
}

internal fun MockRequestHandleScope.respondJson(
    json: String,
    status: HttpStatusCode = HttpStatusCode.OK,
): HttpResponseData = respond(
    json,
    status,
    headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
)

internal val io.ktor.client.request.HttpRequestData.bodyText: String?
    get() = (body as? TextContent)?.text

internal fun TestScope.createTestJsonApiMediaSource(
    searchConfig: JsonApiSearchConfig,
    engine: MockEngine,
    mediaSourceId: String = "json-api-test",
    cacheDao: WebSearchSessionCacheDao = NoopWebSearchSessionCacheDao,
    /** 用户设置的搜索缓存有效期. 默认 0, 即不写缓存. */
    cacheTtl: Duration = Duration.ZERO,
    getTimeMillis: () -> Long = { currentTimeMillis() },
): JsonApiMediaSource {
    val client = HttpClient(engine).asScopedHttpClient()
    val arguments = JsonApiMediaSourceArguments.Default.copy(searchConfig = searchConfig)
    return JsonApiMediaSource(
        mediaSourceId = mediaSourceId,
        config = MediaSourceConfig(
            serializedArguments = MediaSourceConfig.serializeArguments(
                JsonApiMediaSourceArguments.serializer(),
                arguments,
            ),
        ),
        repository = SelectorMediaSourceEpisodeCacheRepository(cacheDao, flowOf(cacheTtl)),
        client = client,
        getTimeMillis = getTimeMillis,
    )
}
