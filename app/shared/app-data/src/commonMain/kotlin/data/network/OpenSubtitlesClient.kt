/*
 * Copyright (C) 2026 OpenAni and contributors.
 * Use of this source code is governed by the GNU AGPLv3 license.
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.network

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.expectSuccess
import io.ktor.client.plugins.timeout
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.prepareGet
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.URLBuilder
import io.ktor.http.URLProtocol
import io.ktor.http.Url
import io.ktor.http.contentType
import io.ktor.http.takeFrom
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.withTimeout
import kotlinx.io.Buffer
import kotlinx.io.readByteArray
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import me.him188.ani.app.data.models.preference.OpenSubtitlesSettings

data class OpenSubtitleFile(val id: Int, val name: String, val language: String, val release: String)
data class OpenSubtitlePage(val files: List<OpenSubtitleFile>, val page: Int, val totalPages: Int)

class OpenSubtitlesException(val status: Int) : Exception("OpenSubtitles HTTP $status")

/** API 凭据仅发送到 OpenSubtitles API；临时文件请求不携带 API Key 或用户令牌。 */
class OpenSubtitlesClient(private val baseClient: HttpClient, private val settings: OpenSubtitlesSettings) {
    suspend fun search(query: String, episode: Int? = null, season: Int? = null, page: Int = 1): OpenSubtitlePage = withClient { client ->
        require(settings.enabled) { "OpenSubtitles is disabled" }
        require(query.isNotBlank() && page > 0)
        if (settings.apiKey.isBlank()) {
            val response = client.callOfficialService("search_subtitles", buildJsonObject {
                put("query", query.trim())
                put("page", page)
                put("per_page", 50)
                episode?.let { put("episode_number", it) }
                season?.let { put("season_number", it) }
                val languages = normalizeOpenSubtitlesLanguages(settings.languages)
                if (languages.isNotEmpty()) put("languages", buildJsonArray { languages.split(',').forEach { add(it) } })
            })
            return@withClient parsePage(response, "results", page)
        }
        val url = URLBuilder("$API/subtitles").apply {
            // 参数和语言排序可避免 API 为规范化地址返回重定向。
            episode?.let { parameters.append("episode_number", it.toString()) }
            normalizeOpenSubtitlesLanguages(settings.languages).takeIf { it.isNotEmpty() }
                ?.let { parameters.append("languages", it) }
            parameters.append("page", page.toString())
            parameters.append("query", query.trim())
            season?.let { parameters.append("season_number", it.toString()) }
        }.build()
        val response = client.searchResponse(url)
        parsePage(response, "data", page)
    }

    private fun parsePage(response: JsonObject, field: String, page: Int): OpenSubtitlePage {
        val files = (response[field] as? JsonArray).orEmpty().flatMap { item ->
            val obj = item as? JsonObject ?: return@flatMap emptyList()
            val attributes = if (field == "data") obj["attributes"] as? JsonObject ?: return@flatMap emptyList() else obj
            (attributes["files"] as? JsonArray).orEmpty().mapNotNull { raw ->
                val file = raw as? JsonObject ?: return@mapNotNull null
                val id = file["file_id"]?.jsonPrimitive?.intOrNull?.takeIf { it > 0 } ?: return@mapNotNull null
                OpenSubtitleFile(
                    id,
                    file.text("file_name").ifBlank { attributes.text("release") }.ifBlank { "$id.srt" },
                    attributes.text("language"),
                    attributes.text("release"),
                )
            }
        }.distinctBy { it.id }
        return OpenSubtitlePage(files, page, (response["total_pages"]?.jsonPrimitive?.intOrNull ?: page).coerceAtLeast(page))
    }

    /** 用户确认某个文件后才申请下载链接，此操作消耗服务端下载配额。 */
    suspend fun download(file: OpenSubtitleFile): ByteArray = withClient { client ->
        withTimeout(60_000) { download(client, file) }
    }

    private suspend fun download(client: HttpClient, file: OpenSubtitleFile): ByteArray {
        if (!settings.isConfigured) throw OpenSubtitlesException(401)
        if (settings.apiKey.isBlank()) {
            val result = client.callOfficialService("download_subtitle", buildJsonObject {
                put("file_id", file.id)
                put("sub_format", "srt")
                put("delivery", "link")
            }, authenticate = true)
            return client.downloadFile(Url(result.text("link")))
        }
        var api = API
        var token: String? = null
        if (settings.hasAccount) {
            val login = client.post("$API/login") {
                credentials()
                contentType(ContentType.Application.Json)
                setBody(buildJsonObject {
                    put("username", settings.username.trim())
                    put("password", settings.password)
                }.toString())
            }.readObject()
            token = login.text("token").takeIf { it.isNotBlank() } ?: error("Missing OpenSubtitles token")
            login.text("base_url").takeIf { it.isNotBlank() }?.let { host ->
                require(host == "api.opensubtitles.com" || host == "vip-api.opensubtitles.com")
                api = "https://$host/api/v1"
            }
        }
        val link = client.post("$api/download") {
            credentials()
            token?.let { header(HttpHeaders.Authorization, "Bearer $it") }
            contentType(ContentType.Application.Json)
            setBody(buildJsonObject { put("file_id", file.id); put("sub_format", "srt") }.toString())
        }.readObject().text("link")
        return client.downloadFile(Url(link))
    }

    /** 官方服务提供匿名搜索，并用用户账号的下载额度签发字幕链接。 */
    private suspend fun HttpClient.callOfficialService(
        tool: String,
        arguments: JsonObject,
        authenticate: Boolean = false,
    ): JsonObject {
        val response = post("https://mcp.opensubtitles.com/mcp") {
            timeout { requestTimeoutMillis = 60_000 }
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Accept, "application/json, text/event-stream")
            header(HttpHeaders.UserAgent, "Animeko v1")
            header("MCP-Protocol-Version", "2025-03-26")
            if (authenticate) {
                header("X-OpenSubtitles-Username", settings.username.trim())
                header("X-OpenSubtitles-Password", settings.password)
            }
            setBody(buildJsonObject {
                put("jsonrpc", "2.0")
                put("id", 1)
                put("method", "tools/call")
                put("params", buildJsonObject { put("name", tool); put("arguments", arguments) })
            }.toString())
        }
        if (response.status.value !in 200..299) throw OpenSubtitlesException(response.status.value)
        val body = response.bodyAsText()
        val data = if (body.trimStart().startsWith('{')) body else {
            body.lineSequence().filter { it.startsWith("data:") }.map { it.removePrefix("data:").trim() }
                .firstOrNull { it.startsWith('{') } ?: throw OpenSubtitlesException(502)
        }
        val envelope = Json.parseToJsonElement(data) as? JsonObject ?: throw OpenSubtitlesException(502)
        val result = envelope["result"] as? JsonObject ?: throw OpenSubtitlesException(502)
        if (result["isError"]?.jsonPrimitive?.booleanOrNull == true) {
            val status = Regex("""\b(401|403|406|429)\b""").find(result.toString())?.value?.toInt() ?: 502
            throw OpenSubtitlesException(status)
        }
        return result["structuredContent"] as? JsonObject ?: throw OpenSubtitlesException(502)
    }

    private suspend fun HttpClient.searchResponse(initialUrl: Url): JsonObject {
        var url = initialUrl
        repeat(5) {
            require(url.protocol == URLProtocol.HTTPS && url.host in API_HOSTS && url.port == 443)
            val response = get(url) { credentials() }
            if (response.status.value !in REDIRECTS) return response.readObject()
            url = URLBuilder(url).takeFrom(response.headers[HttpHeaders.Location] ?: error("Missing redirect location")).build()
        }
        error("Too many OpenSubtitles redirects")
    }

    private suspend fun HttpClient.downloadFile(initialUrl: Url): ByteArray {
        var url = initialUrl
        repeat(5) {
            require(url.protocol == URLProtocol.HTTPS && url.host.isNotBlank() && url.user.isNullOrEmpty() && url.password.isNullOrEmpty())
            var redirect: Url? = null
            val bytes = prepareGet(url) {
                expectSuccess = false
                timeout { requestTimeoutMillis = 30_000 }
            }.execute { response ->
                if (response.status.value in REDIRECTS) {
                    redirect = URLBuilder(url).takeFrom(response.headers[HttpHeaders.Location] ?: error("Missing redirect location")).build()
                    return@execute null
                }
                if (response.status.value !in 200..299) throw OpenSubtitlesException(response.status.value)
                require((response.headers[HttpHeaders.ContentLength]?.toLongOrNull() ?: 0) <= MAX_SUBTITLE_BYTES)
                val buffer = Buffer()
                val chunk = ByteArray(8192)
                val channel = response.bodyAsChannel()
                while (true) {
                    val read = channel.readAvailable(chunk)
                    if (read < 0) break
                    require(buffer.size + read <= MAX_SUBTITLE_BYTES) { "Subtitle file is too large" }
                    buffer.write(chunk, 0, read)
                }
                buffer.readByteArray()
            }
            if (bytes != null) return bytes
            url = checkNotNull(redirect)
        }
        error("Too many subtitle file redirects")
    }

    private suspend fun <T> withClient(block: suspend (HttpClient) -> T): T {
        // 复用引擎的连接池与代理，API 和临时文件请求自行控制重定向及凭据。
        val client = HttpClient(baseClient.engine) {
            followRedirects = false
            expectSuccess = false
            install(HttpTimeout)
        }
        try {
            return block(client)
        } finally {
            client.close()
        }
    }

    private fun HttpRequestBuilder.credentials() {
        expectSuccess = false
        timeout { requestTimeoutMillis = 30_000 }
        header("Api-Key", settings.apiKey.trim())
        header(HttpHeaders.UserAgent, "Animeko v1")
        accept(ContentType.Application.Json)
    }

    private suspend fun HttpResponse.readObject(): JsonObject {
        if (status.value !in 200..299) throw OpenSubtitlesException(status.value)
        return Json.parseToJsonElement(bodyAsText()) as? JsonObject ?: error("Invalid OpenSubtitles response")
    }

    companion object {
        const val MAX_SUBTITLE_BYTES = 16 * 1024 * 1024
        private const val API = "https://api.opensubtitles.com/api/v1"
        private val API_HOSTS = setOf("api.opensubtitles.com", "vip-api.opensubtitles.com")
        private val REDIRECTS = setOf(301, 302, 303, 307, 308)
    }
}

private fun JsonObject.text(key: String) = get(key)?.jsonPrimitive?.contentOrNull.orEmpty()

internal fun normalizeOpenSubtitlesLanguages(languages: String): String = languages.split(',')
    .flatMap {
        when (val code = it.trim().lowercase()) {
            "zh" -> listOf("zh-cn", "zh-tw")
            "pt" -> listOf("pt-br", "pt-pt")
            "" -> emptyList()
            else -> listOf(code)
        }
    }.distinct().sorted().joinToString(",")
