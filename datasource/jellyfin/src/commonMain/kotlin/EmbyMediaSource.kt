/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.datasources.jellyfin

import io.ktor.client.plugins.ClientRequestException
import io.ktor.http.HttpStatusCode
import io.ktor.http.URLBuilder
import io.ktor.http.Url
import io.ktor.http.appendPathSegments
import io.ktor.http.takeFrom
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import me.him188.ani.datasources.api.DefaultMedia
import me.him188.ani.datasources.api.EpisodeSort
import me.him188.ani.datasources.api.Media
import me.him188.ani.datasources.api.MediaProperties
import me.him188.ani.datasources.api.matcher.ApiVideoResolver
import me.him188.ani.datasources.api.matcher.WebVideo
import me.him188.ani.datasources.api.source.BrowseChannel
import me.him188.ani.datasources.api.source.BrowseEpisode
import me.him188.ani.datasources.api.source.BrowseSubject
import me.him188.ani.datasources.api.source.FactoryId
import me.him188.ani.datasources.api.source.MediaSource
import me.him188.ani.datasources.api.source.MediaSourceConfig
import me.him188.ani.datasources.api.source.MediaSourceFactory
import me.him188.ani.datasources.api.source.MediaSourceInfo
import me.him188.ani.datasources.api.source.MediaSourceKind
import me.him188.ani.datasources.api.source.MediaSourceLocation
import me.him188.ani.datasources.api.source.get
import me.him188.ani.datasources.api.source.parameter.MediaSourceParameters
import me.him188.ani.datasources.api.source.parameter.MediaSourceParametersBuilder
import me.him188.ani.datasources.api.source.parameter.hasValue
import me.him188.ani.datasources.api.topic.EpisodeRange
import me.him188.ani.datasources.api.topic.FileSize
import me.him188.ani.datasources.api.topic.ResourceLocation
import me.him188.ani.utils.ktor.ScopedHttpClient

class EmbyMediaSource(
    config: MediaSourceConfig,
    client: ScopedHttpClient,
    instanceId: String = ID,
) : BaseJellyfinMediaSource(client), ApiVideoResolver {
    companion object {
        const val ID = "emby"
        const val AUTH_MODE_API_KEY = "apiKey"
        const val AUTH_MODE_USERNAME_PASSWORD = "usernamePassword"
        private const val BROWSE_PAGE_SIZE = 100
        private val playbackJson = Json { ignoreUnknownKeys = true }
        val INFO = MediaSourceInfo(
            displayName = "Emby",
            description = "Emby Media Server",
            websiteUrl = "https://emby.media",
            iconUrl = "https://emby.media/favicon-32x32.png",
        )
    }

    object Parameters : MediaSourceParametersBuilder() {
        val name = string(
            "name",
            defaultProvider = { "" },
            description = "服务器名称，用于区分不同的 Emby 服务器；留空时显示 Emby",
        )
        val baseUrl = string(
            "baseUrl",
            defaultProvider = { "http://localhost:8096" },
            description = "服务器地址\n示例: http://localhost:8096。用户名密码登录建议使用 HTTPS",
        )
        val authMode = simpleEnum(
            "authMode",
            AUTH_MODE_API_KEY,
            AUTH_MODE_USERNAME_PASSWORD,
            default = AUTH_MODE_API_KEY,
            description = "认证方式：apiKey 使用 User ID 与 API Key；usernamePassword 使用用户名与密码",
        )
        val userId = string(
            "userId",
            description = "User ID, 可在 Emby \"控制台 - 用户\" 中选择一个用户, 在浏览器地址栏找到 \"userId=\" 后面的内容\n示例: cc91f58d951648829c90115520f6adec",
            visibleWhen = authMode.hasValue(AUTH_MODE_API_KEY),
        )
        val apikey = string(
            "apikey",
            description = "API Key, 可在 Emby \"控制台 - API 秘钥\" 中添加\n示例: b7292a71d51a6bf3a31036086a6d2e23",
            visibleWhen = authMode.hasValue(AUTH_MODE_API_KEY),
            isSensitive = true,
        )
        val username = string(
            "username",
            description = "Emby 服务器的用户名",
            visibleWhen = authMode.hasValue(AUTH_MODE_USERNAME_PASSWORD),
        )
        val password = string(
            "password",
            defaultProvider = { "" },
            description = "Emby 服务器的密码，允许为空",
            visibleWhen = authMode.hasValue(AUTH_MODE_USERNAME_PASSWORD),
            isSensitive = true,
        )
    }

    class Factory : MediaSourceFactory {
        override val factoryId: FactoryId get() = FactoryId(ID)

        override val parameters: MediaSourceParameters = Parameters.build()
        override val info: MediaSourceInfo get() = INFO
        override val allowMultipleInstances: Boolean get() = true
        override fun create(
            mediaSourceId: String,
            config: MediaSourceConfig,
            client: ScopedHttpClient
        ): MediaSource = EmbyMediaSource(config, client, instanceId = mediaSourceId)
    }

    override val kind: MediaSourceKind get() = MediaSourceKind.WEB
    override val supportsBrowsing: Boolean get() = true
    override val info: MediaSourceInfo = MediaSourceInfo(
        displayName = config[Parameters.name].trim().ifEmpty { INFO.displayName },
        description = INFO.description,
        websiteUrl = INFO.websiteUrl,
        iconUrl = INFO.iconUrl,
    )
    override val mediaSourceId: String = instanceId
    override val baseUrl = config[Parameters.baseUrl].trim().trimEnd('/')
    private val authMode = config[Parameters.authMode]
    private val userId = config[Parameters.userId]
    private val apiKey = config[Parameters.apikey]
    private val passwordAuthenticator = if (authMode == AUTH_MODE_USERNAME_PASSWORD) {
        JellyfinPasswordAuthenticator(
            baseUrl = baseUrl,
            username = config[Parameters.username],
            password = config[Parameters.password],
            deviceId = "animeko-$instanceId",
            client = client,
            serverName = "Emby",
        )
    } else {
        null
    }

    override suspend fun getAuthorization(): Authorization {
        return when (authMode) {
            AUTH_MODE_API_KEY -> {
                require(userId.isNotBlank()) { "Emby userId must not be blank in API Key mode" }
                require(apiKey.isNotBlank()) { "Emby API Key must not be blank in API Key mode" }
                Authorization(userId, apiKey, """MediaBrowser Token="$apiKey"""")
            }

            AUTH_MODE_USERNAME_PASSWORD -> {
                val session = checkNotNull(passwordAuthenticator).getSession()
                Authorization(session.userId, session.accessToken, session.authorizationHeader)
            }

            else -> error("Unknown Emby authentication mode: $authMode")
        }
    }

    override suspend fun invalidateAuthorization(authorization: Authorization): Boolean {
        val authenticator = passwordAuthenticator ?: return false
        authenticator.invalidate(authorization.accessToken)
        return true
    }

    override fun getDownloadUri(itemId: String, accessToken: String): String {
        // Static streaming uses playback permission and supports byte range requests.
        return URLBuilder(baseUrl).apply {
            appendPathSegments("Videos", itemId, "stream")
            parameters.append("static", "true")
            parameters.append("api_key", accessToken)
        }.buildString()
    }

    override fun getMediaId(itemId: String): String = "$mediaSourceId.$itemId"

    override fun apiParameterName(name: String): String = name.replaceFirstChar { it.uppercaseChar() }

    override fun getDownloadLocation(itemId: String, accessToken: String): ResourceLocation =
        ResourceLocation.WebVideo(
            uri = URLBuilder(baseUrl).apply { appendPathSegments("Items", itemId) }.buildString(),
            apiParameters = mapOf("itemId" to itemId),
        )

    override suspend fun searchSubjects(keyword: String): List<BrowseSubject> = browseItems(
        endpoint("Items"),
        mapOf("SearchTerm" to keyword, "IncludeItemTypes" to "Series,Season,Movie", "Recursive" to "true"),
    ).map { item ->
        val title = if (item.Type == "Season" && !item.SeriesName.isNullOrBlank()) {
            "${item.SeriesName} · ${item.Name}"
        } else {
            item.Name
        }
        BrowseSubject(
            name = item.ProductionYear?.let { "$title ($it)" } ?: title,
            url = endpoint("Items", item.Id),
        )
    }

    override suspend fun browseSubject(subject: BrowseSubject): List<BrowseChannel> {
        val id = browseItemId(subject.url)
        val item = playbackJson.decodeFromJsonElement<EmbyBrowseItem>(authorizedGetJson(endpoint("Items", id)))
        return when (item.Type) {
            "Series" -> coroutineScope {
                val requests = Semaphore(4)
                browseItems(endpoint("Shows", id, "Seasons")).filter { it.Type == "Season" }.map { season ->
                    async {
                        requests.withPermit {
                            BrowseChannel(name = season.Id, label = season.Name, episodes = browseEpisodes(season.Id))
                        }
                    }
                }.awaitAll()
            }

            "Season" -> listOf(BrowseChannel(name = item.Id, label = item.Name, episodes = browseEpisodes(item.Id)))
            "Movie", "Episode" -> listOf(BrowseChannel(name = null, episodes = listOf(item.toBrowseEpisode())))
            else -> emptyList()
        }
    }

    private suspend fun browseEpisodes(parentId: String): List<BrowseEpisode> = browseItems(
        endpoint("Items"),
        mapOf(
            "ParentId" to parentId, "Recursive" to "true", "IncludeItemTypes" to "Episode",
            "SortBy" to "IndexNumber", "SortOrder" to "Ascending",
        ),
    ).filter { it.Type == "Episode" }.map { it.toBrowseEpisode() }

    private suspend fun browseItems(url: String, parameters: Map<String, String> = emptyMap()): List<EmbyBrowseItem> {
        val items = linkedMapOf<String, EmbyBrowseItem>()
        var startIndex = 0
        while (true) {
            val pageUrl = URLBuilder(url).apply {
                parameters.forEach { (name, value) -> this.parameters.append(name, value) }
                this.parameters.append("EnableImages", "false")
                this.parameters.append("Limit", BROWSE_PAGE_SIZE.toString())
                this.parameters.append("StartIndex", startIndex.toString())
            }.buildString()
            val page = playbackJson.decodeFromJsonElement<EmbyBrowseItems>(authorizedGetJson(pageUrl)).Items
            val previousSize = items.size
            page.forEach { items[it.Id] = it }
            if (page.size < BROWSE_PAGE_SIZE || items.size == previousSize) break
            startIndex += page.size
        }
        return items.values.toList()
    }

    private fun EmbyBrowseItem.toBrowseEpisode() = BrowseEpisode(
        name = IndexNumber?.let { "$it $Name" } ?: Name,
        url = endpoint("Items", Id),
        episodeSort = IndexNumber?.let { EpisodeSort(it) },
    )

    private fun endpoint(vararg segments: String): String = URLBuilder(baseUrl).apply {
        appendPathSegments(*segments)
    }.buildString()

    private fun browseItemId(url: String): String {
        val server = Url(baseUrl)
        val item = Url(url)
        val prefix = server.encodedPath.trimEnd('/') + "/Items/"
        require(item.protocol == server.protocol && item.host.equals(server.host, true) && item.port == server.port) {
            "Emby browse item must belong to this server"
        }
        require(item.encodedPath.startsWith(prefix)) { "Emby browse item must use this server path" }
        val id = item.encodedPath.removePrefix(prefix)
        require(Regex("[A-Za-z0-9_-]+").matches(id)) { "Invalid Emby item ID" }
        return id
    }

    override fun createMedia(
        subject: BrowseSubject,
        channelName: String?,
        episode: BrowseEpisode,
        episodeSort: EpisodeSort?,
    ): Media {
        browseItemId(subject.url)
        val id = browseItemId(episode.url)
        return DefaultMedia(
            mediaId = getMediaId(id),
            mediaSourceId = mediaSourceId,
            originalUrl = episode.url,
            download = getDownloadLocation(id, ""),
            originalTitle = episode.name,
            publishedTime = 0,
            properties = MediaProperties(
                subjectName = subject.name,
                episodeName = episode.name,
                subtitleLanguageIds = listOf("CHS"),
                resolution = "",
                alliance = mediaSourceId,
                size = FileSize.Unspecified,
                subtitleKind = null,
            ),
            episodeRange = episodeSort?.let { EpisodeRange.single(it) },
            location = MediaSourceLocation.Lan,
            kind = MediaSourceKind.WEB,
        )
    }

    override suspend fun resolveApiVideo(media: Media): WebVideo? {
        if (media.mediaSourceId != mediaSourceId) return null
        val location = media.download as? ResourceLocation.WebVideo ?: return null
        val itemId = location.apiParameters?.get("itemId")?.takeIf { it.isNotBlank() } ?: return null
        val endpoint = URLBuilder(baseUrl).apply { appendPathSegments("Items", itemId, "PlaybackInfo") }.buildString()
        val playback = try {
            playbackJson.decodeFromJsonElement<EmbyPlaybackInfo>(authorizedGetJson(endpoint))
        } catch (e: ClientRequestException) {
            if (e.response.status != HttpStatusCode.NotFound && e.response.status != HttpStatusCode.MethodNotAllowed) throw e
            EmbyPlaybackInfo()
        }
        val source = playback.MediaSources.firstOrNull { !it.DirectStreamUrl.isNullOrBlank() }
        val authorization = getAuthorization()
        val target = source?.DirectStreamUrl ?: getDownloadUri(itemId, authorization.accessToken)
        val url = URLBuilder("$baseUrl/").apply { takeFrom(target) }.build()
        require(url.protocol.name == "http" || url.protocol.name == "https") { "Emby playback URL must use HTTP or HTTPS" }
        require(url.user.isNullOrEmpty() && url.password.isNullOrEmpty()) { "Emby playback URL must not contain credentials" }
        val server = Url(baseUrl)
        val sameOrigin = url.protocol == server.protocol && url.host.equals(server.host, true) && url.port == server.port
        val headers = source?.RequiredHttpHeaders.orEmpty().toMutableMap()
        val resolvedUrl = URLBuilder(url).apply {
            if (sameOrigin) {
                // Playback clients authenticate with the token; device metadata belongs to API requests.
                headers["X-Emby-Token"] = authorization.accessToken
                if (source?.AddApiKeyToDirectStreamUrl == true && parameters["api_key"] == null) {
                    parameters.append("api_key", authorization.accessToken)
                }
            }
        }.buildString()
        return WebVideo(resolvedUrl, headers)
    }
}

@Serializable
@Suppress("PropertyName")
private data class EmbyPlaybackInfo(val MediaSources: List<EmbyPlaybackSource> = emptyList())

@Serializable
@Suppress("PropertyName")
private data class EmbyPlaybackSource(
    val DirectStreamUrl: String? = null,
    val RequiredHttpHeaders: Map<String, String> = emptyMap(),
    val AddApiKeyToDirectStreamUrl: Boolean = false,
)

@Serializable
@Suppress("PropertyName")
private data class EmbyBrowseItems(val Items: List<EmbyBrowseItem> = emptyList())

@Serializable
@Suppress("PropertyName")
private data class EmbyBrowseItem(
    val Name: String,
    val Id: String,
    val Type: String,
    val SeriesName: String? = null,
    val IndexNumber: Int? = null,
    val ProductionYear: Int? = null,
)
