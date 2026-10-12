/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.mediasource.api

import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.http.contentType
import io.ktor.http.decodeURLPart
import io.ktor.http.encodeURLParameter
import io.ktor.http.isSuccess
import kotlinx.coroutines.flow.flow
import kotlinx.io.IOException
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import me.him188.ani.app.domain.media.resolver.MediaResolutionException
import me.him188.ani.app.domain.media.resolver.ResolutionFailures
import me.him188.ani.app.domain.mediasource.MediaListFilter
import me.him188.ani.app.domain.mediasource.MediaListFilterContext
import me.him188.ani.app.domain.mediasource.MediaListFilters
import me.him188.ani.app.domain.mediasource.MediaSourceEngineHelpers
import me.him188.ani.datasources.api.DefaultMedia
import me.him188.ani.datasources.api.EpisodeSort
import me.him188.ani.datasources.api.Media
import me.him188.ani.datasources.api.MediaProperties
import me.him188.ani.datasources.api.SubtitleKind
import me.him188.ani.datasources.api.matcher.WebVideo
import me.him188.ani.datasources.api.matcher.WebVideoResolverProvider
import me.him188.ani.datasources.api.paging.SinglePagePagedSource
import me.him188.ani.datasources.api.paging.SizedSource
import me.him188.ani.datasources.api.paging.emptySizedSource
import me.him188.ani.datasources.api.source.BrowseChannel
import me.him188.ani.datasources.api.source.BrowseEpisode
import me.him188.ani.datasources.api.source.BrowseSubject
import me.him188.ani.datasources.api.source.ConnectionStatus
import me.him188.ani.datasources.api.source.FactoryId
import me.him188.ani.datasources.api.source.HttpMediaSource
import me.him188.ani.datasources.api.source.MatchKind
import me.him188.ani.datasources.api.source.MediaFetchRequest
import me.him188.ani.datasources.api.source.MediaMatch
import me.him188.ani.datasources.api.source.MediaSource
import me.him188.ani.datasources.api.source.MediaSourceConfig
import me.him188.ani.datasources.api.source.MediaSourceFactory
import me.him188.ani.datasources.api.source.MediaSourceInfo
import me.him188.ani.datasources.api.source.MediaSourceKind
import me.him188.ani.datasources.api.source.MediaSourceLocation
import me.him188.ani.datasources.api.source.deserializeArgumentsOrNull
import me.him188.ani.datasources.api.topic.EpisodeRange
import me.him188.ani.datasources.api.topic.FileSize
import me.him188.ani.datasources.api.topic.ResourceLocation
import me.him188.ani.utils.jsonpath.JsonPath
import me.him188.ani.utils.jsonpath.resolveOrNull
import me.him188.ani.utils.ktor.ScopedHttpClient
import me.him188.ani.utils.logging.info
import me.him188.ani.utils.logging.warn
import kotlin.coroutines.cancellation.CancellationException

/**
 * 通用 JSON API 数据源: 搜索、线路与剧集、播放地址都由 [ApiMediaSourceArguments] 声明的 HTTP 请求和 JSONPath 得到.
 *
 * 浏览链接与资源的下载地址只携带条目、线路、剧集的 ID (见 [link]), 不保存视频地址;
 * 播放和下载时通过 [resolveVideo] 重新向 API 请求, 因此带签名的视频地址过期后仍能播放.
 */
class ApiMediaSource(
    override val mediaSourceId: String,
    config: MediaSourceConfig,
    private val client: ScopedHttpClient,
) : HttpMediaSource(), WebVideoResolverProvider {
    private val arguments = config.deserializeArgumentsOrNull(ApiMediaSourceArguments.serializer())
        ?: ApiMediaSourceArguments.Default

    /**
     * 校验配置并编译全部 JSONPath, 每个实例只做一次. 配置不合法时初始化失败, 之后每次访问都重新抛出.
     */
    private val compiled: CompiledArguments by lazy { CompiledArguments(arguments) }

    override val kind: MediaSourceKind = MediaSourceKind.WEB
    override val supportsBrowsing: Boolean = true
    override val info = MediaSourceInfo(
        displayName = arguments.name,
        websiteUrl = arguments.websiteUrl.takeIf { it.isNotBlank() },
        iconUrl = arguments.iconUrl.takeIf { it.isNotBlank() },
        tier = arguments.tier,
    )

    class Factory : MediaSourceFactory {
        override val factoryId: FactoryId get() = FactoryId
        override val allowMultipleInstances: Boolean = true
        override val info = MediaSourceInfo(displayName = "JSON API")
        override fun create(mediaSourceId: String, config: MediaSourceConfig, client: ScopedHttpClient): MediaSource =
            ApiMediaSource(mediaSourceId, config, client)
    }

    /**
     * 只发一个搜索请求 (第一页), 拿到可解析的 JSON 即视为连通. 不分页, 也不解析条目.
     */
    override suspend fun checkConnection(): ConnectionStatus = try {
        val search = compiled.arguments.search
        request(search.request, searchVariables(search, CONNECTION_TEST_KEYWORD, search.firstPage))
        ConnectionStatus.SUCCESS
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        logger.info { "Connection test failed for '$mediaSourceId': $e" }
        ConnectionStatus.FAILED
    }

    private class Subject(val browse: BrowseSubject, val aliases: List<String>)

    override suspend fun searchSubjects(keyword: String): List<BrowseSubject> = search(keyword).map { it.browse }

    private suspend fun search(keyword: String): List<Subject> {
        if (keyword.isBlank()) return emptyList()
        val config = compiled.arguments.search
        val subjects = linkedMapOf<String, Subject>()
        val pages = if (config.paginate) config.maxPages else 1
        for (offset in 0 until pages) {
            val variables = searchVariables(config, keyword, config.firstPage + offset)
            val rows = request(config.request, variables).items(config.itemsPath)
            val before = subjects.size
            for (row in rows.parseRows("subject") { it.toSubject(config) }) {
                subjects[row.id.content] = row.subject
            }
            // 短页、或整页都是已见过的条目, 说明后面没有新结果了
            if (rows.size < config.pageSize || subjects.size == before) break
        }
        return subjects.values.toList()
    }

    private class SearchRow(val id: JsonPrimitive, val subject: Subject)

    private fun JsonElement.toSubject(config: ApiSearch): SearchRow {
        val id = requiredScalar(config.idPath)
        val name = requiredScalar(config.namePath).content
        val identifiers = mapOf("subjectId" to id)
        val variables = identifiers + ("subjectName" to JsonPrimitive(name))
        val aliases = items(config.aliasesPath)
            .flatMap { if (it is JsonArray) it else listOf(it) }
            .filterIsInstance<JsonPrimitive>()
            .filter { it != JsonNull }
            .map { it.content }
        return SearchRow(
            id,
            Subject(
                browse = BrowseSubject(name = name, url = link(config.subjectUrl, variables, identifiers)),
                aliases = aliases,
            ),
        )
    }

    override suspend fun browseSubject(subject: BrowseSubject): List<BrowseChannel> {
        val config = compiled.arguments.detail
        val subjectIdentifiers = subjectIdentifiers(subject.url)
        val variables = subjectIdentifiers + ("subjectName" to JsonPrimitive(subject.name))
        return request(config.request, variables).items(config.channelsPath).mapIndexedNotNull { index, channel ->
            val channelId = channel.scalar(config.channelIdPath) ?: JsonPrimitive(index)
            val channelName = channel.scalar(config.channelNamePath)?.content
            val channelIdentifiers = subjectIdentifiers + ("channelId" to channelId)
            val channelVariables = variables + mapOf(
                "channelId" to channelId,
                "channelName" to JsonPrimitive(channelName.orEmpty()),
            )
            val episodes = channel.items(config.episodesPath).parseRows("episode") { row ->
                row.toEpisode(config, channelIdentifiers, channelVariables)
            }
            if (episodes.isEmpty()) null else BrowseChannel(name = channelName, episodes = episodes)
        }
    }

    private fun JsonElement.toEpisode(
        config: ApiDetail,
        channelIdentifiers: Map<String, JsonPrimitive>,
        channelVariables: Map<String, JsonPrimitive>,
    ): BrowseEpisode {
        val episodeId = requiredScalar(config.episodeIdPath)
        val title = scalar(config.episodeNamePath)?.content.orEmpty()
        val sort = episodeSort(config)
        val name = title.ifBlank { sort?.toString() ?: episodeId.content }
        val identifiers = channelIdentifiers + ("episodeId" to episodeId)
        val variables = channelVariables + mapOf(
            "episodeId" to episodeId,
            "episodeName" to JsonPrimitive(name),
        )
        return BrowseEpisode(
            name = name,
            url = link(config.episodeUrl, variables, identifiers),
            episodeSort = sort,
        )
    }

    /**
     * 集号加上剧集类型对应的前缀后解析为 [EpisodeSort]. 没有集号、类型没有映射、或解析不出时为 `null`,
     * 这样的剧集只能手动选择.
     */
    private fun JsonElement.episodeSort(config: ApiDetail): EpisodeSort? {
        val number = scalar(config.episodeNumberPath)?.content ?: return null
        val prefix = if (config.episodeKindPath.isBlank()) {
            ""
        } else {
            config.episodeKindPrefixes[scalar(config.episodeKindPath)?.content] ?: return null
        }
        return EpisodeSort("$prefix$number").takeUnless { it is EpisodeSort.Unknown }
    }

    /**
     * 自动匹配: 依次搜索条目名, 某个关键词搜到名字能对上的条目并成功打开详情后不再搜其余关键词; 只打开对上的条目的详情.
     * 判据与选择器过滤 WEB 资源的一致 ([MediaListFilters.ContainsSubjectName]), 所以这里认为对上的, 选择器不会以名字不符为由排除.
     *
     * 单个条目的详情请求失败 (过期的搜索结果、站点临时错误) 只跳过该条目, 不让整个数据源失败.
     * 关闭 [ApiMediaSourceArguments.filterBySubjectName] 时每个搜索结果都要打开详情, 为避免请求失控,
     * 最多打开 [MAX_UNFILTERED_SUBJECTS] 个.
     */
    override suspend fun fetch(query: MediaFetchRequest): SizedSource<MediaMatch> {
        if (!arguments.autoMatch) return emptySizedSource()
        return SinglePagePagedSource {
            flow {
                val filterContext = MediaListFilterContext(
                    subjectNames = query.subjectNames.toSet(),
                    episodeSort = query.episodeSort,
                    episodeEp = query.episodeEp,
                    episodeName = query.episodeName,
                )
                val keywords = query.subjectNames.take(arguments.searchNamesCount)
                    .map {
                        MediaSourceEngineHelpers.getSearchKeyword(it, removeSpecial = true, useOnlyFirstWord = false)
                    }
                    .filter { it.isNotBlank() }
                    .distinct()
                val seen = mutableSetOf<String>()
                var opened = 0
                for (keyword in keywords) {
                    val subjects = search(keyword).filter { seen.add(it.browse.url) }
                    val matched = if (arguments.filterBySubjectName) {
                        subjects.filter { it.matches(filterContext) }
                    } else {
                        subjects
                    }
                    var openedForKeyword = 0
                    for ((index, subject) in matched.withIndex()) {
                        if (!arguments.filterBySubjectName && opened >= MAX_UNFILTERED_SUBJECTS) {
                            logger.info {
                                "'$mediaSourceId': opened $MAX_UNFILTERED_SUBJECTS subjects for '$keyword', " +
                                        "skipping the remaining ${matched.size - index} (filterBySubjectName is off)"
                            }
                            break
                        }
                        val channels = try {
                            browseSubject(subject.browse)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            logger.warn(e) { "'$mediaSourceId': failed to browse '${subject.browse.url}', skipping" }
                            continue
                        }
                        opened++
                        openedForKeyword++
                        for (channel in channels) {
                            for (episode in channel.episodes) {
                                val media = createMedia(subject.browse, channel.name, episode, episode.episodeSort)
                                emit(MediaMatch(media, MatchKind.FUZZY))
                            }
                        }
                    }
                    // 这个关键词打开了条目就不再搜其余关键词; 一个都没打开成功则继续试下一个
                    if (openedForKeyword > 0) break
                }
            }
        }
    }

    private fun Subject.matches(context: MediaListFilterContext): Boolean = with(context) {
        (listOf(browse.name) + aliases).any { candidateName ->
            MediaListFilters.ContainsSubjectName.applyOn(
                object : MediaListFilter.Candidate {
                    override val originalTitle: String get() = candidateName
                    override val episodeRange: EpisodeRange? get() = null
                },
            )
        }
    }

    override fun createMedia(
        subject: BrowseSubject,
        channelName: String?,
        episode: BrowseEpisode,
        episodeSort: EpisodeSort?,
    ): Media {
        val identifiers = episodeIdentifiers(episode.url)
        val identity = JsonArray(EPISODE_IDENTIFIER_KEYS.map { identifiers.getValue(it) })
        return DefaultMedia(
            mediaId = "$mediaSourceId:${identity.toString().encodeURLParameter(spaceToPlus = false)}",
            mediaSourceId = mediaSourceId,
            originalUrl = episode.url.substringBefore(FRAGMENT),
            download = ResourceLocation.WebVideo(episode.url),
            originalTitle = "${subject.name} ${episode.name}",
            publishedTime = 0L,
            properties = MediaProperties(
                subjectName = subject.name,
                episodeName = episode.name,
                subtitleLanguageIds = listOf(arguments.subtitleLanguage),
                resolution = arguments.resolution,
                alliance = channelName.orEmpty(),
                size = FileSize.Unspecified,
                subtitleKind = SubtitleKind.EMBEDDED,
            ),
            episodeRange = episodeSort?.let(EpisodeRange::single),
            location = MediaSourceLocation.Online,
            kind = kind,
        )
    }

    /**
     * 播放或下载时请求新的视频地址. 只会抛出 [MediaResolutionException]:
     * - 网络错误 (含超时) 为 [ResolutionFailures.NETWORK_ERROR];
     * - 配置不合法、API 返回非 2xx 或不是 JSON 为 [ResolutionFailures.ENGINE_ERROR];
     * - 资源标识缺失、成功标志不为 true、没有可用地址为 [ResolutionFailures.NO_MATCHING_RESOURCE].
     */
    override suspend fun resolveVideo(media: Media): WebVideo {
        try {
            return resolveVideoOrThrow(media)
        } catch (e: CancellationException) {
            throw e
        } catch (e: MediaResolutionException) {
            throw e
        } catch (e: IOException) {
            throw MediaResolutionException(ResolutionFailures.NETWORK_ERROR, e)
        } catch (e: Exception) {
            throw MediaResolutionException(ResolutionFailures.ENGINE_ERROR, e)
        }
    }

    private suspend fun resolveVideoOrThrow(media: Media): WebVideo {
        val config = compiled.arguments.playback
        val identifiers = try {
            episodeIdentifiers(media.download.uri)
        } catch (e: IllegalArgumentException) {
            throw MediaResolutionException(ResolutionFailures.NO_MATCHING_RESOURCE, e)
        }
        val variables = identifiers + mapOf(
            "subjectName" to JsonPrimitive(media.properties.subjectName.orEmpty()),
            "channelName" to JsonPrimitive(media.properties.alliance),
            "episodeName" to JsonPrimitive(media.properties.episodeName.orEmpty()),
        )
        val response = request(config.request, variables)
        if (config.successPath.isNotBlank() && response.scalar(config.successPath)?.content != "true") {
            throw MediaResolutionException(ResolutionFailures.NO_MATCHING_RESOURCE)
        }
        val channelId = identifiers.getValue("channelId").content
        val candidate = response.items(config.candidatesPath)
            .firstOrNull { it.scalar(config.candidateIdPath)?.content == channelId }
            ?.scalar(config.candidateUrlPath)?.content
        val url = candidate?.takeIf { it.isNotBlank() }
            ?: response.scalar(config.urlPath)?.content
            ?: throw MediaResolutionException(ResolutionFailures.NO_MATCHING_RESOURCE)
        try {
            requireHttpUrl(url)
        } catch (e: Exception) {
            throw MediaResolutionException(ResolutionFailures.NO_MATCHING_RESOURCE, e)
        }
        val headers = config.videoHeaders.mapValues { substitute(it.value, variables) }
        for ((name, value) in headers) {
            // 变量来自站点返回的名称, 这里不经过 ktor 直接交给播放器, 自己挡住控制字符以防注入请求头
            check(value.none { it < ' ' && it != '\t' || it == '\u007f' }) {
                "Video header '$name' contains control characters"
            }
        }
        return WebVideo(url, headers)
    }

    /**
     * 发送配置的请求并解析响应. 非 2xx 抛出 [ApiStatusException]; 响应不是 JSON 对象或数组时抛出
     * [IllegalStateException] 或 [kotlinx.serialization.SerializationException].
     */
    private suspend fun request(config: ApiRequest, variables: Map<String, JsonPrimitive>): JsonElement = client.use {
        val url = substitute(config.url, variables, escapeUrl = true)
        requireHttpUrl(url)
        val response = request(url) {
            method = HttpMethod.parse(config.method)
            (arguments.headers + config.headers).forEach { (key, value) ->
                header(key, substitute(value, variables))
            }
            config.body?.let {
                contentType(ContentType.Application.Json)
                setBody(substituteJson(it, variables).toString())
            }
        }
        if (!response.status.isSuccess()) throw ApiStatusException(response.status)
        val element = responseJson.parseToJsonElement(response.bodyAsText())
        // 宽松解析会把 HTML 等纯文本当成字符串字面量, 这里只接受对象或数组
        check(element is JsonObject || element is JsonArray) { "API response is not a JSON object or array" }
        element
    }

    private fun searchVariables(config: ApiSearch, keyword: String, page: Int): Map<String, JsonPrimitive> = mapOf(
        "keyword" to JsonPrimitive(keyword),
        "page" to JsonPrimitive(page),
        "pageSize" to JsonPrimitive(config.pageSize),
    )

    /**
     * 把模板渲染成站点页面地址, 再在 fragment 里附上 [identifiers] (条目/线路/剧集 ID, 保持 JSON 原类型).
     *
     * 页面地址本身是公开网址, 作为 [Media.originalUrl]; fragment 让浏览链接、下载地址和重启后的播放都能找回 ID,
     * 不需要内存映射或临时地址. 只放 ID 不放名称, 这样站点改名后同一条目/剧集的链接仍然相同.
     */
    private fun link(
        template: String,
        variables: Map<String, JsonPrimitive>,
        identifiers: Map<String, JsonPrimitive>,
    ): String {
        val page = substitute(template, variables, escapeUrl = true)
        requireHttpUrl(page)
        require(Url(page).fragment.isBlank()) { "Page templates cannot contain fragments" }
        return page + FRAGMENT + JsonObject(identifiers).toString().encodeURLParameter(spaceToPlus = false)
    }

    private fun subjectIdentifiers(url: String): Map<String, JsonPrimitive> = identifiers(url, SUBJECT_IDENTIFIER_KEYS)

    private fun episodeIdentifiers(url: String): Map<String, JsonPrimitive> = identifiers(url, EPISODE_IDENTIFIER_KEYS)

    /**
     * 读取 [link] 写进 fragment 的 ID. 缺失或格式不对时抛出 [IllegalArgumentException].
     */
    private fun identifiers(url: String, keys: List<String>): Map<String, JsonPrimitive> {
        require(FRAGMENT in url) { "Missing API identifiers in '$url'" }
        val data = runCatching { Json.parseToJsonElement(url.substringAfter(FRAGMENT).decodeURLPart()) }
            .getOrNull() as? JsonObject
        requireNotNull(data) { "Invalid API identifiers in '$url'" }
        return keys.associateWith { key ->
            requireNotNull(data[key] as? JsonPrimitive) { "Missing API identifier '$key' in '$url'" }
        }
    }

    private fun JsonElement.items(path: String): List<JsonElement> = items(compiled.path(path))

    private fun JsonElement.scalar(path: String): JsonPrimitive? = scalar(compiled.path(path))

    private fun JsonElement.requiredScalar(path: String): JsonPrimitive =
        scalar(path) ?: error("Missing scalar at $path")

    /**
     * 逐行解析. 缺少必填字段的行跳过并记录; 一整批都解析不出时抛出第一行的异常,
     * 让填错的 JSONPath 在测试器里显示为错误, 而不是空结果.
     */
    private inline fun <T> List<JsonElement>.parseRows(kind: String, parse: (JsonElement) -> T): List<T> {
        var firstError: IllegalStateException? = null
        val parsed = mapNotNull { row ->
            try {
                parse(row)
            } catch (e: IllegalStateException) {
                if (firstError == null) firstError = e
                logger.warn { "'$mediaSourceId': skipping a $kind row: ${e.message}" }
                null
            }
        }
        if (parsed.isEmpty()) firstError?.let { throw it }
        return parsed
    }

    companion object {
        val FactoryId = FactoryId("json-api")
        private const val FRAGMENT = "#ani-api="
        private const val CONNECTION_TEST_KEYWORD = "anime"
        private const val MAX_UNFILTERED_SUBJECTS = 8
        private val SUBJECT_IDENTIFIER_KEYS = listOf("subjectId")
        private val EPISODE_IDENTIFIER_KEYS = listOf("subjectId", "channelId", "episodeId")

        /**
         * 站点返回的 JSON 常有尾随逗号或注释, 放宽解析. 配置与 fragment 仍用严格解析.
         */
        @OptIn(ExperimentalSerializationApi::class)
        private val responseJson = Json {
            isLenient = true
            allowTrailingComma = true
            allowComments = true
        }
    }
}

/**
 * API 返回了非 2xx 状态码. 继承 [IllegalStateException] 以保持搜索与浏览路径的异常类型不变.
 */
private class ApiStatusException(val status: HttpStatusCode) :
    IllegalStateException("API request failed (HTTP ${status.value})")

/**
 * 校验过的配置和编译好的 JSONPath.
 */
private class CompiledArguments(val arguments: ApiMediaSourceArguments) {
    init {
        arguments.validate()
    }

    private val paths: Map<String, JsonPath> = arguments.jsonPaths().associateWith { JsonPath.compile(it) }

    /**
     * 空路径表示该项未配置, 返回 `null`.
     */
    fun path(expression: String): JsonPath? {
        if (expression.isBlank()) return null
        return paths[expression] ?: JsonPath.compile(expression)
    }
}

private fun JsonElement.items(path: JsonPath?): List<JsonElement> {
    if (path == null) return emptyList()
    return when (val value = resolveOrNull(path)) {
        null, JsonNull -> emptyList()
        is JsonArray -> value.toList()
        else -> listOf(value)
    }
}

private fun JsonElement.scalar(path: JsonPath?): JsonPrimitive? {
    if (path == null) return null
    return (resolveOrNull(path) as? JsonPrimitive)?.takeUnless { it == JsonNull }
}

// Android 的 ICU 正则要求两侧花括号都转义.
private val placeholder = Regex("\\{([A-Za-z][A-Za-z0-9]*)\\}")

internal fun substitute(
    value: String,
    variables: Map<String, JsonPrimitive>,
    escapeUrl: Boolean = false,
): String =
    placeholder.replace(value) { match ->
        val text = variables[match.groupValues[1]]?.content ?: error("Unknown variable ${match.value}")
        if (escapeUrl) text.encodeURLParameter(spaceToPlus = false) else text
    }

internal fun substituteJson(value: JsonElement, variables: Map<String, JsonPrimitive>): JsonElement = when (value) {
    is JsonObject -> JsonObject(value.mapValues { substituteJson(it.value, variables) })
    is JsonArray -> JsonArray(value.map { substituteJson(it, variables) })
    is JsonPrimitive -> when {
        !value.isString -> value
        else -> {
            val match = placeholder.matchEntire(value.content)
            if (match == null) {
                JsonPrimitive(substitute(value.content, variables))
            } else {
                variables[match.groupValues[1]] ?: error("Unknown variable ${match.value}")
            }
        }
    }
}
