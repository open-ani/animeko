/*
 * Copyright (C) 2026 OpenAni and contributors.
 * Use of this source code is governed by the GNU AGPLv3 license.
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */
package me.him188.ani.app.domain.mediasource.api

import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.Url
import io.ktor.http.contentType
import io.ktor.http.decodeURLPart
import io.ktor.http.encodeURLParameter
import io.ktor.http.isSuccess
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import me.him188.ani.app.domain.media.resolver.MediaResolutionException
import me.him188.ani.app.domain.media.resolver.ResolutionFailures
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
import kotlin.coroutines.cancellation.CancellationException

/** Declarative JSON requests with persistent identifiers and fresh playback resolution. */
class ApiMediaSource(
    override val mediaSourceId: String,
    config: MediaSourceConfig,
    private val client: ScopedHttpClient,
) : HttpMediaSource(), WebVideoResolverProvider {
    private val arguments = config.deserializeArgumentsOrNull(ApiMediaSourceArguments.serializer()) ?: ApiMediaSourceArguments()
    override val kind: MediaSourceKind = MediaSourceKind.WEB
    override val supportsBrowsing: Boolean = true
    override val info = MediaSourceInfo(
        displayName = arguments.name, websiteUrl = arguments.websiteUrl, iconUrl = arguments.iconUrl, tier = arguments.tier,
    )

    class Factory : MediaSourceFactory {
        override val factoryId: FactoryId get() = FactoryId
        override val allowMultipleInstances: Boolean = true
        override val info = MediaSourceInfo(displayName = "JSON API")
        override fun create(mediaSourceId: String, config: MediaSourceConfig, client: ScopedHttpClient): MediaSource =
            ApiMediaSource(mediaSourceId, config, client)
    }

    override suspend fun checkConnection(): ConnectionStatus = try {
        search("anime")
        ConnectionStatus.SUCCESS
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        ConnectionStatus.FAILED
    }

    private data class Subject(val browse: BrowseSubject, val aliases: List<String>)

    override suspend fun searchSubjects(keyword: String): List<BrowseSubject> = search(keyword).map { it.browse }

    private suspend fun search(keyword: String): List<Subject> {
        if (keyword.isBlank()) return emptyList()
        arguments.validate()
        val config = arguments.search
        val subjects = linkedMapOf<String, Subject>()
        repeat(if (config.paginate) config.maxPages else 1) { offset ->
            val variables = mapOf("keyword" to JsonPrimitive(keyword), "page" to JsonPrimitive(config.firstPage + offset),
                "pageSize" to JsonPrimitive(config.pageSize))
            val rows = request(config.request, variables).items(config.itemsPath)
            val before = subjects.size
            for (row in rows) {
                val id = row.requiredScalar(config.idPath)
                val name = row.requiredScalar(config.namePath).content
                val vars = mapOf("subjectId" to id, "subjectName" to JsonPrimitive(name))
                subjects[id.toString()] = Subject(
                    BrowseSubject(name, link(config.subjectUrl, vars)),
                    row.items(config.aliasesPath).flatMap { if (it is JsonArray) it else listOf(it) }
                        .filterIsInstance<JsonPrimitive>().filter { it != JsonNull }.map { it.content },
                )
            }
            if (rows.size < config.pageSize || subjects.size == before) return subjects.values.toList()
        }
        return subjects.values.toList()
    }

    override suspend fun browseSubject(subject: BrowseSubject): List<BrowseChannel> {
        arguments.validate()
        val variables = variables(subject.url)
        val config = arguments.detail
        return request(config.request, variables).items(config.channelsPath).mapIndexedNotNull { index, channel ->
            val id = channel.scalar(config.channelIdPath) ?: JsonPrimitive(index)
            val name = channel.scalar(config.channelNamePath)?.content
            val channelVars = variables + mapOf("channelId" to id, "channelName" to JsonPrimitive(name.orEmpty()))
            val episodes = channel.items(config.episodesPath).map { row ->
                val episodeId = row.requiredScalar(config.episodeIdPath)
                val title = row.scalar(config.episodeNamePath)?.content.orEmpty()
                val number = row.scalar(config.episodeNumberPath)?.content
                val kind = row.scalar(config.episodeKindPath)?.content
                val prefix = if (config.episodeKindPath.isBlank()) "" else config.episodeKindPrefixes[kind]
                val sort = if (number != null && prefix != null) EpisodeSort("$prefix$number").takeUnless {
                    it is EpisodeSort.Unknown
                } else null
                val episodeName = title.ifBlank { sort?.toString() ?: episodeId.content }
                val vars = channelVars + mapOf("episodeId" to episodeId, "episodeName" to JsonPrimitive(episodeName))
                BrowseEpisode(episodeName, link(config.episodeUrl, vars), sort)
            }
            if (episodes.isEmpty()) null else BrowseChannel(name, episodes = episodes)
        }
    }

    override suspend fun fetch(query: MediaFetchRequest): SizedSource<MediaMatch> = SinglePagePagedSource {
        flow {
            if (!arguments.autoMatch) return@flow
            val seen = mutableSetOf<String>()
            val keywords = query.subjectNames.take(arguments.searchNamesCount).map {
                MediaSourceEngineHelpers.getSearchKeyword(it, removeSpecial = true, useOnlyFirstWord = false)
            }.filter(String::isNotBlank).distinct()
            for (keyword in keywords) for (subject in search(keyword)) {
                if (!seen.add(subject.browse.url)) continue
                if (arguments.filterBySubjectName && (listOf(subject.browse.name) + subject.aliases).none { candidate ->
                        query.subjectNames.any { MediaListFilters.specialEquals(candidate, it) }
                    }) continue
                for (channel in browseSubject(subject.browse)) for (episode in channel.episodes) {
                    emit(MediaMatch(createMedia(subject.browse, channel.name, episode, episode.episodeSort), MatchKind.FUZZY))
                }
            }
        }
    }

    override fun createMedia(subject: BrowseSubject, channelName: String?, episode: BrowseEpisode, episodeSort: EpisodeSort?): Media {
        val vars = variables(episode.url)
        val identity = JsonArray(listOf("subjectId", "channelId", "episodeId").map { vars.getValue(it) })
        return DefaultMedia(
            mediaId = "$mediaSourceId:${identity.toString().encodeURLParameter(spaceToPlus = false)}",
            mediaSourceId = mediaSourceId,
            originalUrl = episode.url.substringBefore(FRAGMENT),
            download = ResourceLocation.WebVideo(episode.url),
            originalTitle = "${subject.name} ${episode.name}",
            publishedTime = 0L,
            properties = MediaProperties(subjectName = subject.name, episodeName = episode.name,
                subtitleLanguageIds = listOf(arguments.subtitleLanguage), resolution = arguments.resolution,
                alliance = channelName.orEmpty(), size = FileSize.Unspecified, subtitleKind = SubtitleKind.EMBEDDED),
            episodeRange = episodeSort?.let(EpisodeRange::single),
            location = MediaSourceLocation.Online,
            kind = kind,
        )
    }

    override suspend fun resolveVideo(media: Media): WebVideo {
        arguments.validate()
        val variables = variables(media.download.uri)
        val config = arguments.playback
        val response = request(config.request, variables)
        if (config.successPath.isNotBlank() && response.scalar(config.successPath)?.content != "true") {
            throw MediaResolutionException(ResolutionFailures.NO_MATCHING_RESOURCE)
        }
        val candidate = response.items(config.candidatesPath).firstOrNull {
            it.scalar(config.candidateIdPath) == variables["channelId"]
        }?.scalar(config.candidateUrlPath)?.content
        val url = candidate?.takeIf(String::isNotBlank) ?: response.scalar(config.urlPath)?.content
            ?: throw MediaResolutionException(ResolutionFailures.NO_MATCHING_RESOURCE)
        try {
            requireHttpUrl(url)
        } catch (e: IllegalArgumentException) {
            throw MediaResolutionException(ResolutionFailures.NO_MATCHING_RESOURCE, e)
        }
        return WebVideo(url, config.videoHeaders.mapValues { substitute(it.value, variables) })
    }

    private suspend fun request(config: ApiRequest, variables: Map<String, JsonPrimitive>): JsonElement = client.use {
        val url = substitute(config.url, variables, escapeUrl = true)
        requireHttpUrl(url)
        val response = request(url) {
            method = HttpMethod.parse(config.method)
            (arguments.headers + config.headers).forEach { (key, value) -> header(key, substitute(value, variables)) }
            config.body?.let {
                contentType(ContentType.Application.Json)
                setBody(substituteJson(it, variables).toString())
            }
        }
        check(response.status.isSuccess()) { "API request failed (HTTP ${response.status.value})" }
        Json.parseToJsonElement(response.bodyAsText())
    }

    // The fragment carries typed identifiers through browsing, downloads and app restarts.
    // originalUrl remains the public website URL; no transient URL or in-memory lookup is needed.
    private fun link(template: String, variables: Map<String, JsonPrimitive>): String =
        substitute(template, variables, escapeUrl = true).also(::requireHttpUrl).also {
            require(Url(it).fragment.isBlank()) { "Page templates cannot contain fragments" }
        } + FRAGMENT + JsonObject(variables).toString().encodeURLParameter(spaceToPlus = false)

    private fun variables(url: String): Map<String, JsonPrimitive> {
        require(FRAGMENT in url) { "Missing API playback identifiers" }
        val data = Json.parseToJsonElement(url.substringAfter(FRAGMENT).decodeURLPart()) as? JsonObject
            ?: error("Invalid API playback identifiers")
        return data.mapValues { (_, value) -> value as? JsonPrimitive ?: error("Invalid API identifier") }
    }

    companion object {
        val FactoryId = FactoryId("json-api")
        private const val FRAGMENT = "#ani-api="
    }
}

internal fun JsonElement.items(path: String): List<JsonElement> {
    if (path.isBlank()) return emptyList()
    return when (val value = resolveOrNull(JsonPath.compile(path))) {
        null, JsonNull -> emptyList()
        is JsonArray -> value.toList()
        else -> listOf(value)
    }
}

internal fun JsonElement.scalar(path: String): JsonPrimitive? =
    if (path.isBlank()) null else (resolveOrNull(JsonPath.compile(path)) as? JsonPrimitive)?.takeUnless { it == JsonNull }

private fun JsonElement.requiredScalar(path: String): JsonPrimitive = scalar(path) ?: error("Missing scalar at $path")

// Android's ICU regex engine requires both literal braces to be escaped.
private val placeholder = Regex("\\{([A-Za-z][A-Za-z0-9]*)\\}")

internal fun substitute(value: String, variables: Map<String, JsonPrimitive>, escapeUrl: Boolean = false): String =
    placeholder.replace(value) { match ->
        val text = variables[match.groupValues[1]]?.content ?: error("Unknown variable ${match.value}")
        if (escapeUrl) text.encodeURLParameter(spaceToPlus = false) else text
    }

internal fun substituteJson(value: JsonElement, variables: Map<String, JsonPrimitive>): JsonElement = when (value) {
    is JsonObject -> JsonObject(value.mapValues { substituteJson(it.value, variables) })
    is JsonArray -> JsonArray(value.map { substituteJson(it, variables) })
    is JsonPrimitive -> if (!value.isString) value else {
        val match = placeholder.matchEntire(value.content)
        if (match == null) JsonPrimitive(substitute(value.content, variables))
        else variables[match.groupValues[1]] ?: error("Unknown variable ${match.value}")
    }
}
