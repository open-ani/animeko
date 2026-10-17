/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.danmaku

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import me.him188.ani.app.data.models.episode.EpisodeInfo
import me.him188.ani.app.data.models.subject.SplitSeason
import me.him188.ani.app.data.models.subject.SubjectInfo
import me.him188.ani.app.data.repository.danmaku.SearchDanmakuRequest
import me.him188.ani.app.data.repository.episode.toEpisodeCollectionInfo
import me.him188.ani.app.data.repository.subject.toEntity
import me.him188.ani.app.data.repository.subject.toSubjectInfo
import me.him188.ani.client.models.AniSubjectCollection
import me.him188.ani.danmaku.api.provider.DanmakuFetchRequest
import me.him188.ani.danmaku.dandanplay.DandanplayDanmakuProvider
import me.him188.ani.datasources.api.EpisodeType
import me.him188.ani.utils.ktor.asScopedHttpClient

/**
 * `resources/dandanplay-split-season` 的数据格式与回放工具, 录制 ([DandanplaySplitSeasonFixtureRecorder])
 * 与回放 ([DandanplaySplitSeasonReplayTest]) 共用. 说明见该目录的 README.
 */
internal object DandanplaySplitSeasonFixtures {
    const val RESOURCE_DIR = "dandanplay-split-season"
    const val INDEX_FILE = "index.json"
    const val ANI_SUBJECTS_FILE = "ani-subjects.json"
    const val DANDANPLAY_FILE = "dandanplay.json"

    val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    @Serializable
    class Index(
        /**
         * 录制日期. 播出日期晚于它的剧集不录制.
         */
        val recordedAt: String,
        val subjects: List<SubjectCase>,
    )

    @Serializable
    class SubjectCase(
        val subjectId: Int,
        val name: String,
        val episodes: List<EpisodeCase> = emptyList(),
    )

    @Serializable
    class EpisodeCase(
        val sort: Int,
        /**
         * 弹弹里与这一集对应的 episodeId. 弹弹把同一集收在几部番剧里时都算对; 弹弹没有这一集时为空, 应匹配不到.
         */
        val expected: List<Long>,
        /**
         * 不能只靠标题确认答案时的核对依据.
         */
        val note: String? = null,
    )

    /**
     * 录制的一次弹弹 play 响应, [body] 只保留匹配用到的字段, 见 [trimDandanplayResponse].
     */
    @Serializable
    class RecordedResponse(
        val status: Int,
        val body: JsonElement,
    )

    /**
     * 回放时 app 看到的一个条目: 从 Ani 服务端的条目数据按 app 的转换得到.
     */
    class Subject(
        val info: SubjectInfo,
        /**
         * 正片.
         */
        val episodes: List<EpisodeInfo>,
        val splitSeason: SplitSeason?,
    ) {
        /**
         * 播放页对这一集发出的弹幕请求. 不带文件名, 不走弹弹的文件匹配. [withSplitSeason] 为 `false` 时模拟没有拆分季信息.
         */
        fun fetchRequest(episode: EpisodeInfo, withSplitSeason: Boolean = true): DanmakuFetchRequest =
            SearchDanmakuRequest(
                subjectInfo = info,
                episodeInfo = episode,
                episodeId = episode.episodeId,
                splitSeason = splitSeason.takeIf { withSplitSeason },
            ).toFetchRequest()
    }

    fun subjectOf(element: JsonElement): Subject {
        val subject = json.decodeFromJsonElement(AniSubjectCollection.serializer(), element)
        val entity = subject.toEntity(lastFetched = 0)
        return Subject(
            info = entity.toSubjectInfo(),
            episodes = subject.episodes.map { it.toEpisodeCollectionInfo().episodeInfo }
                .filter { it.type == EpisodeType.MainStory },
            splitSeason = entity.relations.splitSeason,
        )
    }

    /**
     * 请求的回放键: 方法, 路径和按名字排序的查询参数 (已解码).
     */
    fun requestKey(request: HttpRequestData): String {
        val query = request.url.parameters.entries()
            .flatMap { (name, values) -> values.map { "$name=$it" } }
            .sorted()
            .joinToString("&")
        return buildString {
            append(request.method.value).append(' ').append(request.url.encodedPath)
            if (query.isNotEmpty()) append('?').append(query)
        }
    }

    fun isCommentRequest(request: HttpRequestData): Boolean = request.url.encodedPath.startsWith(COMMENT_PATH)

    fun commentEpisodeId(request: HttpRequestData): Long = request.url.encodedPath.removePrefix(COMMENT_PATH).toLong()

    /**
     * 使用 [handler] 响应请求的弹弹 play provider. 弹幕列表请求总是返回空列表, 并把请求的 episodeId 交给 [onFetchComments].
     */
    fun createProvider(
        appId: String,
        appSecret: String,
        onFetchComments: (Long) -> Unit,
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ): DandanplayDanmakuProvider {
        val engine = MockEngine { request ->
            if (isCommentRequest(request)) {
                onFetchComments(commentEpisodeId(request))
                respondJson("""{"count":0,"comments":[]}""")
            } else {
                handler(request)
            }
        }
        val client = HttpClient(engine) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }
        return DandanplayDanmakuProvider(
            dandanplayAppId = appId,
            dandanplayAppSecret = appSecret,
            client = client.asScopedHttpClient(),
        )
    }

    fun MockRequestHandleScope.respondJson(content: String, status: HttpStatusCode = HttpStatusCode.OK) = respond(
        content = content,
        status = status,
        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
    )

    /**
     * 只保留弹弹 play 匹配用到的字段: 番剧的 id, 名字和类型, 剧集的 id, 标题, 集号和播出日期.
     * 简介, 标签, 图片和评分等去掉.
     */
    fun trimDandanplayResponse(element: JsonElement): JsonElement = when (element) {
        is JsonObject -> JsonObject(
            element.filterKeys { it in DANDANPLAY_KEPT_KEYS }.mapValues { trimDandanplayResponse(it.value) },
        )

        is JsonArray -> JsonArray(element.map { trimDandanplayResponse(it) })
        else -> element
    }

    /**
     * 剧集标题只留 "第x话" 前缀, 模拟弹弹刚收录新一集还没有标题的时候.
     */
    fun withoutEpisodeTitles(element: JsonElement): JsonElement = when (element) {
        is JsonObject -> JsonObject(
            element.mapValues { (key, value) ->
                if (key == "episodeTitle" && value is JsonPrimitive && value.isString) {
                    JsonPrimitive(EPISODE_NUMBER_PREFIX.find(value.content)?.value ?: value.content)
                } else {
                    withoutEpisodeTitles(value)
                }
            },
        )

        is JsonArray -> JsonArray(element.map { withoutEpisodeTitles(it) })
        else -> element
    }

    /**
     * 去掉与匹配无关且较长的字段: 简介, 标签, 信息框, 剧集简介.
     */
    fun trimAniSubject(element: JsonElement): JsonElement {
        val subject = element.jsonObject
        return JsonObject(
            subject.filterKeys { it != "infobox" && it != "tmdbArt" }.mapValues { (key, value) ->
                when (key) {
                    "summary" -> JsonPrimitive("")
                    "tags", "metaTags" -> JsonArray(emptyList())
                    "episodes" -> JsonArray(
                        value.jsonArray.map { episode ->
                            JsonObject(episode.jsonObject + ("description" to JsonPrimitive("")))
                        },
                    )

                    else -> value
                }
            },
        )
    }

    const val ENV_FIXTURE_DIR = "ANIMEKO_DANDANPLAY_FIXTURE_DIR"
    const val ENV_APP_ID = "ANIMEKO_DANDANPLAY_APP_ID"
    const val ENV_APP_SECRET = "ANIMEKO_DANDANPLAY_APP_SECRET"

    private const val COMMENT_PATH = "/api/v2/comment/"
    private val EPISODE_NUMBER_PREFIX = Regex("""^第\s*\d+\s*[话話集]""")

    private val DANDANPLAY_KEPT_KEYS = setOf(
        "success", "errorCode", "errorMessage", "hasMore", "bangumi", "animes", "bangumiList",
        "animeId", "bangumiId", "animeTitle", "type", "typeDescription",
        "episodes", "episodeId", "episodeTitle", "episodeNumber", "lastWatched", "airDate",
    )
}
