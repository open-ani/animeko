/*
 * Copyright (C) 2026 OpenAni and contributors.
 * Use of this source code is governed by the GNU AGPLv3 license.
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */
package me.him188.ani.app.domain.mediasource.api

import io.ktor.http.Url
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import me.him188.ani.app.domain.mediasource.codec.DefaultMediaSourceCodec
import me.him188.ani.app.domain.mediasource.codec.DontForgetToRegisterCodec
import me.him188.ani.app.domain.mediasource.codec.MediaSourceArguments
import me.him188.ani.datasources.api.source.MediaSourceTier
import me.him188.ani.utils.jsonpath.JsonPath

@OptIn(DontForgetToRegisterCodec::class)
@Serializable
data class ApiMediaSourceArguments(
    override val name: String = "JSON API",
    val websiteUrl: String = "",
    val iconUrl: String = "",
    val headers: Map<String, String> = emptyMap(),
    val search: ApiSearch = ApiSearch(),
    val detail: ApiDetail = ApiDetail(),
    val playback: ApiPlayback = ApiPlayback(),
    val autoMatch: Boolean = true,
    val filterBySubjectName: Boolean = true,
    val searchNamesCount: Int = 3,
    val resolution: String = "1080P",
    val subtitleLanguage: String = "CHS",
    override val tier: MediaSourceTier = MediaSourceTier.Fallback,
    override val channelTiers: Map<String, MediaSourceTier> = emptyMap(),
) : MediaSourceArguments {
    fun validate() {
        require(name.isNotBlank()) { "name is required" }
        require(searchNamesCount in 1..10) { "searchNamesCount must be 1..10" }
        require(search.pageSize > 0 && search.maxPages in 1..100 && search.firstPage >= 0) { "Invalid pagination" }
        listOf(search.request, detail.request, playback.request).forEach { request ->
            require(request.method == "GET" || request.method == "POST") { "Only GET and POST are supported" }
            requireHttpUrl(request.url)
            require(request.method != "GET" || request.body == null) { "GET requests cannot have a body" }
        }
        requireHttpUrl(search.subjectUrl)
        requireHttpUrl(detail.episodeUrl)
        listOf(search.itemsPath, search.idPath, search.namePath, detail.channelsPath, detail.episodesPath,
            detail.episodeIdPath, playback.urlPath).forEach { JsonPath.compile(it) }
        listOf(search.aliasesPath, detail.channelIdPath, detail.channelNamePath, detail.episodeNamePath,
            detail.episodeNumberPath, detail.episodeKindPath, playback.successPath, playback.candidatesPath,
            playback.candidateIdPath, playback.candidateUrlPath).filter(String::isNotBlank).forEach { JsonPath.compile(it) }
        require(playback.candidatesPath.isBlank() ||
                playback.candidateIdPath.isNotBlank() && playback.candidateUrlPath.isNotBlank()) { "Candidate paths are required" }
        val searchVariables = mapOf("keyword" to JsonPrimitive("test"), "page" to JsonPrimitive(1), "pageSize" to JsonPrimitive(search.pageSize))
        val detailVariables = mapOf("subjectId" to JsonPrimitive(1), "subjectName" to JsonPrimitive("test"))
        val playbackVariables = detailVariables + mapOf("channelId" to JsonPrimitive("test"), "channelName" to JsonPrimitive("test"),
            "episodeId" to JsonPrimitive(1), "episodeName" to JsonPrimitive("test"))
        listOf(search.request to searchVariables, detail.request to detailVariables, playback.request to playbackVariables).forEach { (request, variables) ->
            substitute(request.url, variables, escapeUrl = true)
            (headers + request.headers).values.forEach { substitute(it, variables) }
            request.body?.let { substituteJson(it, variables) }
        }
        substitute(search.subjectUrl, detailVariables, escapeUrl = true)
        substitute(detail.episodeUrl, playbackVariables, escapeUrl = true)
        playback.videoHeaders.values.forEach { substitute(it, playbackVariables) }
    }
}

internal fun requireHttpUrl(value: String) {
    require(value.startsWith("https://", ignoreCase = true) || value.startsWith("http://", ignoreCase = true)) {
        "Expected an absolute HTTP(S) URL"
    }
    val url = Url(value)
    require(url.protocol.name in listOf("http", "https") && url.host.isNotBlank()) { "Expected an HTTP(S) URL" }
}

@Serializable
data class ApiRequest(
    val url: String = "",
    val method: String = "GET",
    val headers: Map<String, String> = emptyMap(),
    val body: JsonElement? = null,
)

@Serializable
data class ApiSearch(
    val request: ApiRequest = ApiRequest(),
    val itemsPath: String = "$[*]",
    val idPath: String = "$.id",
    val namePath: String = "$.title",
    val aliasesPath: String = "",
    val subjectUrl: String = "",
    val paginate: Boolean = false,
    val firstPage: Int = 1,
    val pageSize: Int = 50,
    val maxPages: Int = 20,
)

@Serializable
data class ApiDetail(
    val request: ApiRequest = ApiRequest(),
    val channelsPath: String = "$.sources[*]",
    val channelIdPath: String = "$.id",
    val channelNamePath: String = "$.name",
    val episodesPath: String = "$.episodes[*]",
    val episodeIdPath: String = "$.id",
    val episodeNamePath: String = "$.title",
    val episodeNumberPath: String = "$.number",
    val episodeKindPath: String = "",
    val episodeKindPrefixes: Map<String, String> = emptyMap(),
    val episodeUrl: String = "",
)

@Serializable
data class ApiPlayback(
    val request: ApiRequest = ApiRequest(),
    val urlPath: String = "$.url",
    val successPath: String = "",
    val candidatesPath: String = "",
    val candidateIdPath: String = "",
    val candidateUrlPath: String = "",
    val videoHeaders: Map<String, String> = emptyMap(),
)

object ApiMediaSourceCodec : DefaultMediaSourceCodec<ApiMediaSourceArguments>(
    ApiMediaSource.FactoryId,
    ApiMediaSourceArguments::class,
    currentVersion = 1,
    ApiMediaSourceArguments.serializer(),
)
