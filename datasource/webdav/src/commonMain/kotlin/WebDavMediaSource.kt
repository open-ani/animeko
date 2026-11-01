/*
 * Copyright (C) 2026 OpenAni and contributors.
 * Use of this source code is governed by the GNU AGPLv3 license.
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.datasources.webdav

import io.ktor.http.Url
import io.ktor.http.decodeURLPart
import kotlin.time.Duration.Companion.minutes
import kotlin.time.TimeMark
import kotlin.time.TimeSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.io.IOException
import me.him188.ani.datasources.api.DefaultMedia
import me.him188.ani.datasources.api.EpisodeSort
import me.him188.ani.datasources.api.Media
import me.him188.ani.datasources.api.MediaProperties
import me.him188.ani.datasources.api.matcher.ApiVideoResolver
import me.him188.ani.datasources.api.matcher.WebVideo
import me.him188.ani.datasources.api.paging.SinglePagePagedSource
import me.him188.ani.datasources.api.paging.SizedSource
import me.him188.ani.datasources.api.source.BrowseChannel
import me.him188.ani.datasources.api.source.BrowseEpisode
import me.him188.ani.datasources.api.source.BrowseSubject
import me.him188.ani.datasources.api.source.ConnectionStatus
import me.him188.ani.datasources.api.source.FactoryId
import me.him188.ani.datasources.api.source.MatchKind
import me.him188.ani.datasources.api.source.MediaFetchRequest
import me.him188.ani.datasources.api.source.MediaMatch
import me.him188.ani.datasources.api.source.MediaSource
import me.him188.ani.datasources.api.source.MediaSourceConfig
import me.him188.ani.datasources.api.source.MediaSourceFactory
import me.him188.ani.datasources.api.source.MediaSourceInfo
import me.him188.ani.datasources.api.source.MediaSourceKind
import me.him188.ani.datasources.api.source.MediaSourceLocation
import me.him188.ani.datasources.api.source.get
import me.him188.ani.datasources.api.source.parameter.MediaSourceParameters
import me.him188.ani.datasources.api.source.parameter.MediaSourceParametersBuilder
import me.him188.ani.datasources.api.source.parameter.StringParameter
import me.him188.ani.datasources.api.topic.EpisodeRange
import me.him188.ani.datasources.api.topic.FileSize
import me.him188.ani.datasources.api.topic.FileSize.Companion.bytes
import me.him188.ani.datasources.api.topic.ResourceLocation
import me.him188.ani.datasources.api.topic.titles.RawTitleParser
import me.him188.ani.datasources.api.topic.titles.parse
import me.him188.ani.utils.ktor.ScopedHttpClient

/**
 * WebDAV 媒体目录。每个含视频的文件夹是一个可浏览条目，子目录路径参与名称搜索。
 * 目录索引在实例内缓存五分钟；播放时由当前实例提供 Basic 认证头，资源 URL 不包含账号密码。
 */
class WebDavMediaSource(
    override val mediaSourceId: String,
    config: MediaSourceConfig,
    client: ScopedHttpClient,
) : MediaSource, ApiVideoResolver {
    companion object {
        const val ID = "webdav"
        val INFO = MediaSourceInfo(displayName = "WebDAV", description = "WebDAV 媒体文件服务器")
        private const val MAX_DIRECTORIES = 2000
    }

    object Parameters : MediaSourceParametersBuilder() {
        val name = string("name", defaultProvider = { "" }, description = "服务器名称，留空时显示 WebDAV")
        val baseUrl = StringParameter(
            name = "baseUrl",
            description = "媒体根目录地址，例如 https://nas.example.com/dav/动画/；自动搜索会扫描其子目录。HTTP 会明文传输登录凭据，建议使用 HTTPS",
            default = { "" },
            isRequired = true,
            sanitize = String::trim,
            validate = { runCatching { WebDavClient.parseRoot(it) }.isSuccess },
        ).also { add(it) }
        val username = string("username", defaultProvider = { "" }, description = "用户名，匿名访问时留空")
        val password = string("password", defaultProvider = { "" }, description = "密码，允许为空", isSensitive = true)
    }

    class Factory : MediaSourceFactory {
        override val factoryId: FactoryId = FactoryId(ID)
        override val info: MediaSourceInfo get() = INFO
        override val parameters: MediaSourceParameters = Parameters.build()
        override val allowMultipleInstances: Boolean get() = true

        override fun create(mediaSourceId: String, config: MediaSourceConfig, client: ScopedHttpClient): MediaSource =
            WebDavMediaSource(mediaSourceId, config, client)
    }

    private val dav = WebDavClient(config[Parameters.baseUrl], config[Parameters.username], config[Parameters.password], client)
    override val info = MediaSourceInfo(
        displayName = config[Parameters.name].trim().ifEmpty { INFO.displayName },
        description = INFO.description,
        websiteUrl = dav.root.toString(),
    )
    override val kind: MediaSourceKind get() = MediaSourceKind.WEB
    override val location: MediaSourceLocation get() = dav.location
    override val supportsBrowsing: Boolean get() = true
    private val titleParser = RawTitleParser.getDefault()
    private val indexMutex = Mutex()
    private var cachedIndex: List<IndexedDirectory>? = null
    private var indexedAt: TimeMark? = null

    override suspend fun checkConnection(): ConnectionStatus = try {
        dav.list(dav.root.toString())
        ConnectionStatus.SUCCESS
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        ConnectionStatus.FAILED
    }

    private data class IndexedDirectory(val subject: BrowseSubject, val videos: List<WebDavEntry>)

    private fun subjectOf(url: String): BrowseSubject {
        val relative = Url(url).encodedPath.removePrefix(dav.root.encodedPath).trimEnd('/')
        return BrowseSubject(
            name = relative.split('/').joinToString(" / ") { it.decodeURLPart() }.ifEmpty { info.displayName },
            url = url,
        )
    }

    private suspend fun index(): List<IndexedDirectory> = indexMutex.withLock {
        cachedIndex?.takeIf { indexedAt?.elapsedNow()?.let { age -> age < 5.minutes } == true }?.let { return@withLock it }
        val pending = ArrayDeque<String>()
        val visited = mutableSetOf<String>()
        val result = mutableListOf<IndexedDirectory>()
        pending.add(dav.root.toString())
        while (pending.isNotEmpty()) {
            val folder = pending.removeFirst()
            if (!visited.add(folder)) continue
            if (visited.size > MAX_DIRECTORIES) {
                throw IOException("WebDAV has more than $MAX_DIRECTORIES directories; choose a smaller media root directory")
            }
            val entries = dav.list(folder)
            val videos = entries.filter { it.isVideo }
            if (videos.isNotEmpty()) result.add(IndexedDirectory(subjectOf(folder), videos))
            entries.filter { it.isDirectory }.forEach { pending.add(it.url) }
        }
        result.also {
            cachedIndex = it
            indexedAt = TimeSource.Monotonic.markNow()
        }
    }

    override suspend fun searchSubjects(keyword: String): List<BrowseSubject> = index().filter { directory ->
        directory.subject.name.contains(keyword, ignoreCase = true) ||
                directory.videos.any { it.name.contains(keyword, ignoreCase = true) }
    }.map { it.subject }

    override suspend fun browseSubject(subject: BrowseSubject): List<BrowseChannel> {
        val videos = dav.list(subject.url).filter { it.isVideo }
        return listOf(
            BrowseChannel(
                name = null,
                episodes = videos.map {
                    BrowseEpisode(it.name, it.url, episodeRange(it.name)?.knownSorts?.singleOrNull())
                }.sortedWith(compareBy({ it.episodeSort }, { it.name })),
            ),
        )
    }

    override suspend fun fetch(query: MediaFetchRequest): SizedSource<MediaMatch> = SinglePagePagedSource {
        val names = (listOfNotNull(query.subjectNameCN) + query.subjectNames).filter { it.isNotBlank() }.distinct()
        index().flatMap { directory ->
            directory.videos.mapNotNull { file ->
                val matchedName = names.firstOrNull {
                    directory.subject.name.contains(it, ignoreCase = true) || file.name.contains(it, ignoreCase = true)
                } ?: return@mapNotNull null
                val range = episodeRange(file.name) ?: return@mapNotNull null
                MediaMatch(
                    media = media(directory.subject, file, range, subjectName = matchedName),
                    kind = MatchKind.FUZZY,
                )
            }
        }.asFlow()
    }

    override fun createMedia(
        subject: BrowseSubject,
        channelName: String?,
        episode: BrowseEpisode,
        episodeSort: EpisodeSort?,
    ): Media {
        val url = dav.requireOwnedUrl(episode.url).toString()
        val folder = dav.requireOwnedUrl(subject.url).toString()
        require(url.startsWith(folder) && !url.endsWith('/')) { "WebDAV video must belong to the selected directory" }
        return media(subject, WebDavEntry(url, episode.name, false, null), episodeSort?.let { EpisodeRange.single(it) })
    }

    private fun episodeRange(fileName: String): EpisodeRange? {
        val name = fileName.substringBeforeLast('.')
        val seasonEpisode = Regex("""(?i)(?:^|[ ._\[-])S(\d+)E(\d+(?:\.\d{1,2}(?!\d|[pPiI]))?)""").find(name)
        if (seasonEpisode != null) {
            val episode = seasonEpisode.groupValues[2]
            val sort = if (seasonEpisode.groupValues[1].toIntOrNull() == 0) "SP$episode" else episode
            return EpisodeRange.single(EpisodeSort(sort))
        }
        if (Regex("""\d+(?:\.\d+)?""").matches(name)) return EpisodeRange.single(EpisodeSort(name))
        return titleParser.parse(name).episodeRange
    }

    private fun media(subject: BrowseSubject, file: WebDavEntry, range: EpisodeRange?, subjectName: String = subject.name): Media {
        val parsed = titleParser.parse(file.name.substringBeforeLast('.'))
        return DefaultMedia(
            mediaId = "$mediaSourceId.${file.url}",
            mediaSourceId = mediaSourceId,
            originalUrl = file.url,
            download = ResourceLocation.WebVideo(file.url, apiParameters = emptyMap()),
            originalTitle = file.name,
            publishedTime = 0,
            properties = MediaProperties(
                subjectName = subjectName,
                episodeName = file.name,
                subtitleLanguageIds = parsed.subtitleLanguages.map { it.id }.ifEmpty { listOf("CHS") },
                resolution = parsed.resolution?.id.orEmpty(),
                alliance = mediaSourceId,
                size = file.size?.bytes ?: FileSize.Unspecified,
                subtitleKind = parsed.subtitleKind,
            ),
            episodeRange = range,
            location = dav.location,
            kind = MediaSourceKind.WEB,
        )
    }

    override suspend fun resolveApiVideo(media: Media): WebVideo? {
        if (media.mediaSourceId != mediaSourceId) return null
        val url = dav.requireOwnedUrl(media.download.uri).toString()
        return WebVideo(url, dav.playbackHeaders)
    }
}
