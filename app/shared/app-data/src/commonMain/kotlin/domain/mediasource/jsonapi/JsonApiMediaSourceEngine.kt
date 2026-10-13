/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.mediasource.jsonapi

import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.request.accept
import io.ktor.client.request.header
import io.ktor.client.request.prepareRequest
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import me.him188.ani.app.data.repository.RepositoryException
import me.him188.ani.app.data.repository.RepositoryRateLimitedException
import me.him188.ani.app.domain.foundation.webSourceUserAgent
import me.him188.ani.app.domain.mediasource.jsonapi.JsonApiTemplate.Escaping
import me.him188.ani.utils.coroutines.IO_
import me.him188.ani.utils.jsonpath.JsonPath
import me.him188.ani.utils.jsonpath.compileOrNull
import me.him188.ani.utils.jsonpath.resolveOrNull
import me.him188.ani.utils.ktor.ScopedHttpClient
import me.him188.ani.utils.ktor.UrlHelpers
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.cancellation.CancellationException

/**
 * 代入变量后准备好发送的请求. 编辑器的测试面板也展示它.
 */
data class JsonApiPreparedRequest(
    val url: String,
    val method: JsonApiHttpMethod,
    /**
     * 不含 User-Agent: 它由 HTTP 客户端按 [userAgent] 统一设置.
     */
    val headers: List<Pair<String, String>>,
    /**
     * `null` 表示不发送请求体.
     */
    val body: String?,
    val userAgent: String?,
)

/**
 * 站点返回的响应. 404 与空响应也在此表示, 由各阶段解释为"没有结果".
 */
class JsonApiResponse(
    val url: String,
    val status: Int,
    val body: String,
) {
    /**
     * 响应体解析为 JSON. 不是 JSON 时为 `null`.
     */
    val json: JsonElement? by lazy(LazyThreadSafetyMode.PUBLICATION) {
        runCatching { Json.parseToJsonElement(body) }.getOrNull()
    }

    override fun toString(): String = "JsonApiResponse(url=$url, status=$status, body=${body.length} chars)"
}

/**
 * 播放阶段解析出的视频.
 */
data class JsonApiVideoCandidate(
    /**
     * 候选的线路匹配值, 响应没有候选数组时为 `null`.
     */
    val channelMatch: String?,
    val url: String,
)

data class JsonApiVideoResult(
    val candidates: List<JsonApiVideoCandidate>,
    /**
     * 按资源的线路选中的候选. `null` 表示没有可用视频.
     */
    val selected: JsonApiVideoCandidate?,
    /**
     * 选中的是退而求其次的第一条候选, 不是资源所在线路的.
     */
    val isFallback: Boolean,
)

/**
 * JSON API 数据源的引擎: 按配置构造、发送请求, 并用 JSONPath 解析响应.
 *
 * 构造与解析都是纯函数, 编辑器的测试面板用它们在改配置后重新解析已取得的响应, 不重新请求.
 * 请求节流由 `JsonApiMediaSource` 负责, 引擎每次调用 [execute] 都真的发请求.
 */
open class JsonApiMediaSourceEngine(
    private val client: ScopedHttpClient,
    private val ioDispatcher: CoroutineContext = Dispatchers.IO_,
) {
    /**
     * 代入变量, 得到要发送的请求. 请求头的值按原文代入; 请求体默认按 JSON 发送, 代入后必须是合法 JSON.
     * GET 请求不带请求体, 配置里填了也忽略: 常见的 HTTP 客户端拒绝带请求体的 GET.
     *
     * @return `null` 表示配置无效: 地址为空或无法解析, 或请求体代入后不是合法 JSON.
     */
    fun prepareRequest(
        config: JsonApiSearchConfig,
        request: JsonApiRequestConfig,
        variables: Map<String, String>,
    ): JsonApiPreparedRequest? {
        if (!request.isValid()) return null
        val urlTemplate = request.url.trim()
        val substituted = JsonApiTemplate.substitute(urlTemplate, variables, Escaping.URL_COMPONENT)
        val url = if (isAbsoluteUrl(substituted)) {
            substituted
        } else {
            if (config.baseUrl.isBlank()) return null
            UrlHelpers.computeAbsoluteUrlOrNull(config.baseUrl.trim(), substituted) ?: return null
        }
        val headers = mergeHeaders(
            JsonApiSearchConfig.parseHeaderLines(config.headers),
            JsonApiSearchConfig.parseHeaderLines(request.headers),
        ).map { (name, value) -> name to JsonApiTemplate.substitute(value, variables, Escaping.NONE) }
        val body = request.body.takeIf { it.isNotBlank() && request.method != JsonApiHttpMethod.GET }?.let { template ->
            val rendered = JsonApiTemplate.substitute(template, variables, Escaping.JSON_STRING)
            val isJson = headers.none { (name, _) -> name.equals(HttpHeaders.ContentType, ignoreCase = true) } ||
                    headers.any { (name, value) ->
                        name.equals(HttpHeaders.ContentType, ignoreCase = true) && value.contains("json", ignoreCase = true)
                    }
            if (isJson && !isStrictJson(rendered)) return null
            rendered
        }
        return JsonApiPreparedRequest(
            url = url,
            method = request.method,
            headers = headers,
            body = body,
            userAgent = config.userAgentOrNull,
        )
    }

    /**
     * 发送请求.
     *
     * - 2xx: 返回响应;
     * - 404: 返回 `null`, 表示合法的"没有";
     * - 429: [RepositoryRateLimitedException];
     * - 401 / 403: [me.him188.ani.app.data.repository.RepositoryAuthorizationException];
     * - 其他: 由 [RepositoryException.wrapOrThrowCancellation] 包装.
     */
    @Throws(RepositoryException::class, CancellationException::class)
    open suspend fun execute(request: JsonApiPreparedRequest): JsonApiResponse? = withContext(ioDispatcher) {
        try {
            client.use {
                prepareRequest(request.url) {
                    method = when (request.method) {
                        JsonApiHttpMethod.GET -> HttpMethod.Get
                        JsonApiHttpMethod.POST -> HttpMethod.Post
                    }
                    accept(ContentType.Application.Json)
                    webSourceUserAgent(request.userAgent)
                    for ((name, value) in request.headers) {
                        // User-Agent 由客户端统一设置, 直接写 header 会被 UserAgent 插件追加成两个值
                        if (name.equals(HttpHeaders.UserAgent, ignoreCase = true)) continue
                        if (name.equals(HttpHeaders.ContentType, ignoreCase = true)) continue
                        header(name, value)
                    }
                    if (request.body != null) {
                        val contentType = request.headers.firstOrNull { (name, _) ->
                            name.equals(HttpHeaders.ContentType, ignoreCase = true)
                        }?.second?.let { runCatching { ContentType.parse(it) }.getOrNull() }
                        contentType(contentType ?: ContentType.Application.Json)
                        setBody(request.body)
                    }
                }.execute { response ->
                    when (response.status) {
                        HttpStatusCode.NotFound -> null
                        HttpStatusCode.TooManyRequests -> throw RepositoryRateLimitedException()
                        else -> JsonApiResponse(request.url, response.status.value, response.bodyAsText())
                    }
                }
            }
        } catch (e: ClientRequestException) {
            when (e.response.status) {
                HttpStatusCode.NotFound -> null
                else -> throw RepositoryException.wrapOrThrowCancellation(e)
            }
        } catch (e: RepositoryException) {
            throw e
        } catch (e: Exception) {
            throw RepositoryException.wrapOrThrowCancellation(e)
        }
    }

    /**
     * 解析搜索响应.
     *
     * @return `null` 表示配置无效.
     */
    fun parseSubjects(json: JsonElement, stage: JsonApiSearchStage): List<JsonApiSubjectInfo>? {
        if (!stage.isValid()) return null
        val selectItems = JsonPath.compileOrNull(stage.selectItems) ?: return null
        val selectName = JsonPath.compileOrNull(stage.selectName) ?: return null
        val selectId = JsonPath.compileOrNull(stage.selectId) ?: return null
        val selectAliases = stage.selectAliases.takeIf { it.isNotBlank() }?.let { JsonPath.compileOrNull(it) ?: return null }
        return json.resolveOrNull(selectItems).items().mapNotNull { item ->
            val id = item.resolveOrNull(selectId)?.stringValueOrNull()?.trim()?.takeIf { it.isNotEmpty() }
                ?: return@mapNotNull null
            val name = item.resolveOrNull(selectName)?.stringValueOrNull()?.trim()?.takeIf { it.isNotEmpty() }
                ?: return@mapNotNull null
            val aliases = selectAliases?.let { path ->
                item.resolveOrNull(path).items()
                    .mapNotNull { alias -> alias.stringValueOrNull()?.trim()?.takeIf { it.isNotEmpty() && it != name } }
                    .distinct()
            }.orEmpty()
            JsonApiSubjectInfo(
                id = id,
                name = name,
                url = JsonApiTemplate.substitute(stage.subjectUrlTemplate, mapOf("id" to id), Escaping.NONE),
                aliases = aliases,
            )
        }
    }

    /**
     * 解析条目响应为线路与剧集.
     *
     * @return `null` 表示配置无效.
     */
    fun parseChannels(
        json: JsonElement,
        stage: JsonApiSubjectStage,
        subject: JsonApiSubjectInfo,
    ): List<JsonApiChannelInfo>? {
        if (!stage.isValid()) return null
        return when (val format = JsonApiChannelFormat.findById(stage.channelFormatId) ?: return null) {
            is JsonApiChannelFormatNoChannel -> format.select(json, Unit, stage.episodes, subject)
            is JsonApiChannelFormatGrouped -> {
                if (!stage.channelFormatGrouped.isValid()) return null
                format.select(json, stage.channelFormatGrouped, stage.episodes, subject)
            }

            is JsonApiChannelFormatDeclared -> format.select(json, stage.channelFormatDeclared, stage.episodes, subject)
        }
    }

    /**
     * 解析播放响应, 按线路匹配值 [channelMatch] 选出候选.
     *
     * @param channelMatch 资源所在线路的匹配值, 没有线路概念时为 `null` (取第一条候选).
     * @return `null` 表示配置无效.
     */
    fun parseVideo(json: JsonElement, stage: JsonApiPlayStage, channelMatch: String?): JsonApiVideoResult? {
        if (!stage.isValid()) return null
        val selectVideoUrl = JsonPath.compileOrNull(stage.selectVideoUrl) ?: return null
        val selectCandidates = stage.selectCandidates.takeIf { it.isNotBlank() }
            ?.let { JsonPath.compileOrNull(it) ?: return null }
        val selectCandidateChannel = stage.selectCandidateChannel.takeIf { it.isNotBlank() }
            ?.let { JsonPath.compileOrNull(it) ?: return null }

        if (selectCandidates == null) {
            val url = json.resolveOrNull(selectVideoUrl)?.stringValueOrNull()?.trim()?.takeIf { isAbsoluteUrl(it) }
            val candidate = url?.let { JsonApiVideoCandidate(channelMatch = null, url = it) }
            return JsonApiVideoResult(listOfNotNull(candidate), candidate, isFallback = false)
        }

        val candidates = json.resolveOrNull(selectCandidates).items().mapNotNull { element ->
            val url = element.resolveOrNull(selectVideoUrl)?.stringValueOrNull()?.trim()
                ?.takeIf { isAbsoluteUrl(it) } ?: return@mapNotNull null
            val match = selectCandidateChannel?.let { element.resolveOrNull(it)?.stringValueOrNull()?.trim() }
            JsonApiVideoCandidate(channelMatch = match, url = url)
        }
        if (channelMatch == null || selectCandidateChannel == null) {
            return JsonApiVideoResult(candidates, candidates.firstOrNull(), isFallback = false)
        }
        val matched = candidates.firstOrNull { it.channelMatch.equals(channelMatch, ignoreCase = true) }
        if (matched != null) return JsonApiVideoResult(candidates, matched, isFallback = false)
        if (stage.fallbackToFirstCandidate) {
            return JsonApiVideoResult(candidates, candidates.firstOrNull(), isFallback = candidates.isNotEmpty())
        }
        return JsonApiVideoResult(candidates, null, isFallback = false)
    }

    private fun mergeHeaders(
        common: List<Pair<String, String>>,
        specific: List<Pair<String, String>>,
    ): List<Pair<String, String>> {
        val merged = LinkedHashMap<String, Pair<String, String>>()
        for (header in common + specific) {
            merged[header.first.lowercase()] = header
        }
        return merged.values.toList()
    }

    companion object {
        private val JSON_NUMBER = Regex("""-?(0|[1-9]\d*)(\.\d+)?([eE][+-]?\d+)?""")

        /**
         * [text] 是否为严格的 JSON. [Json.parseToJsonElement] 接受不带引号的裸字面量 (如 `{"id": abc}`),
         * 这里再检查每个非字符串原始值都是数字、布尔或 `null`, 以便在发送前发现模板代入错误.
         */
        fun isStrictJson(text: String): Boolean {
            val root = runCatching { Json.parseToJsonElement(text) }.getOrNull() ?: return false
            fun check(element: JsonElement): Boolean = when (element) {
                is JsonObject -> element.values.all(::check)
                is JsonArray -> element.all(::check)
                is JsonPrimitive -> element.isString ||
                        element.content == "true" || element.content == "false" || element.content == "null" ||
                        JSON_NUMBER.matches(element.content)
            }
            return check(root)
        }

        fun isAbsoluteUrl(url: String): Boolean =
            url.startsWith("http://", ignoreCase = true) || url.startsWith("https://", ignoreCase = true)
    }
}
