/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.datasources.jellyfin

import io.ktor.client.call.body
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapConcat
import kotlinx.coroutines.flow.toList
import kotlinx.serialization.Serializable
import me.him188.ani.datasources.api.DefaultMedia
import me.him188.ani.datasources.api.EpisodeSort
import me.him188.ani.datasources.api.MediaExtraFiles
import me.him188.ani.datasources.api.MediaProperties
import me.him188.ani.datasources.api.Subtitle
import me.him188.ani.datasources.api.SubtitleKind
import me.him188.ani.datasources.api.paging.SinglePagePagedSource
import me.him188.ani.datasources.api.paging.SizedSource
import me.him188.ani.datasources.api.source.ConnectionStatus
import me.him188.ani.datasources.api.source.HttpMediaSource
import me.him188.ani.datasources.api.source.MatchKind
import me.him188.ani.datasources.api.source.MediaFetchRequest
import me.him188.ani.datasources.api.source.MediaMatch
import me.him188.ani.datasources.api.source.MediaSourceKind
import me.him188.ani.datasources.api.source.MediaSourceLocation
import me.him188.ani.datasources.api.source.matches
import me.him188.ani.datasources.api.topic.EpisodeRange
import me.him188.ani.datasources.api.topic.FileSize
import me.him188.ani.datasources.api.topic.ResourceLocation
import me.him188.ani.utils.ktor.ScopedHttpClient

abstract class BaseJellyfinMediaSource(
    private val client: ScopedHttpClient,
) : HttpMediaSource() {
    abstract val baseUrl: String

    protected data class Authorization(
        val userId: String,
        val accessToken: String,
        val headerValue: String,
    )

    protected abstract suspend fun getAuthorization(): Authorization

    /**
     * Invalidates [authorization] after the server rejects it.
     *
     * @return `true` when the request can be retried with a newly acquired authorization.
     */
    protected open suspend fun invalidateAuthorization(authorization: Authorization): Boolean = false

    override suspend fun checkConnection(): ConnectionStatus {
        try {
            doSearch("AA测试BB")
            return ConnectionStatus.SUCCESS
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            return ConnectionStatus.FAILED
        }
    }

    override suspend fun fetch(query: MediaFetchRequest): SizedSource<MediaMatch> {
        return SinglePagePagedSource {
            val items = query.subjectNames
                .asFlow()
                .flatMapConcat { subjectName ->
                    val (baseName, targetSeason) = parseSubjectName(subjectName)

                    val seriesSearchResp = doSearch(subjectName = baseName)
                    val seriesItems = seriesSearchResp.Items.filter { it.Type == "Series" }

                    if (seriesItems.isNotEmpty()) {
                        seriesItems.asFlow().flatMapConcat { series ->
                            val seasonsResp = doGetSeasons(seriesId = series.Id)
                            val matchedSeasons = if (targetSeason != null) {
                                seasonsResp.Items.filter { it.IndexNumber == targetSeason }
                            } else {
                                seasonsResp.Items
                            }

                            if (matchedSeasons.isNotEmpty()) {
                                matchedSeasons.asFlow().flatMapConcat { season ->
                                    doGetEpisodes(
                                        seriesId = series.Id,
                                        seasonNum = season.IndexNumber
                                            ?: return@flatMapConcat emptyFlow(),
                                    ).Items.asFlow()
                                }
                            } else {
                                doSearch(parentId = series.Id).Items.asFlow()
                            }
                        }
                    } else {
                        val resp = doSearch(subjectName = subjectName)
                        resp.Items.asFlow()
                    }
                }
                .filter { (it.Type == "Episode" || it.Type == "Movie") }
                .toList()
                .distinctBy { it.Id }

            val authorization = getAuthorization()
            items
                .mapNotNull { item ->
                    val (originalTitle, episodeRange) = when (item.Type) {
                        "Episode" -> {
                            val indexNumber = item.IndexNumber ?: return@mapNotNull null
                            Pair(
                                "$indexNumber ${item.Name}",
                                EpisodeRange.single(EpisodeSort(indexNumber)),
                            )
                        }

                        "Movie" -> Pair(
                            item.Name,
                            EpisodeRange.unknownSeason(),
                        )

                        else -> return@mapNotNull null
                    }

                    MediaMatch(
                        media = DefaultMedia(
                            mediaId = item.Id,
                            mediaSourceId = mediaSourceId,
                            originalUrl = "$baseUrl/Items/${item.Id}",
                            download = ResourceLocation.HttpStreamingFile(
                                uri = getDownloadUri(item.Id, authorization.accessToken),
                            ),
                            originalTitle = originalTitle,
                            publishedTime = 0,
                            properties = MediaProperties(
                                subjectName = query.subjectNameCN
                                    ?: item.SeriesName?.takeIf { it.isNotBlank() }
                                    ?: item.SeasonName?.takeIf { it.isNotBlank() }
                                    ?: query.subjectNames.firstOrNull() ?: "",
                                episodeName = item.Name,
                                subtitleLanguageIds = listOf("CHS"),
                                resolution = "1080P",
                                alliance = mediaSourceId,
                                size = FileSize.Unspecified,
                                subtitleKind = SubtitleKind.EXTERNAL_PROVIDED,
                            ),
                            extraFiles = MediaExtraFiles(
                                subtitles = getSubtitles(item.Id, item.MediaStreams),
                            ),
                            episodeRange = episodeRange,
                            location = MediaSourceLocation.Lan,
                            kind = MediaSourceKind.WEB,
                        ),
                        kind = MatchKind.FUZZY,
                    )
                }
                .filter { it.matches(query) != false }
                .asFlow()
        }
    }

    protected abstract fun getDownloadUri(itemId: String, accessToken: String): String

    private fun getSubtitles(itemId: String, mediaStreams: List<MediaStream>): List<Subtitle> {
        return mediaStreams
            .filter { it.Type == "Subtitle" && it.IsTextSubtitleStream && it.IsExternal && it.Codec != null }
            .map { stream ->
                Subtitle(
                    uri = getSubtitleUri(itemId, stream.Index, stream.Codec!!),
                    language = stream.Language,
                    mimeType = when (stream.Codec.lowercase()) {
                        "ass" -> "text/x-ass"
                        else -> "application/octet-stream"  // 默认二进制流
                    },
                    label = stream.Title,
                )
            }
    }

    private fun getSubtitleUri(itemId: String, index: Int, codec: String): String {
        return "$baseUrl/Videos/$itemId/$itemId/Subtitles/$index/0/Stream.$codec"
    }


    private data class ParsedSubjectName(
        val baseName: String,
        val targetSeason: Int?,
    )

    private fun parseSubjectName(name: String): ParsedSubjectName {
        // Chinese: "无职转生 第三季 ～到了异世界就拿出真本事～" → base="无职转生", season=3
        val chineseSeasonRegex = Regex("[第]([一二三四五六七八九十]+)季")
        val chineseMatch = chineseSeasonRegex.find(name)
        if (chineseMatch != null) {
            val baseName = name.substring(0, chineseMatch.range.first).trim()
            if (baseName.isNotEmpty()) {
                return ParsedSubjectName(baseName, chineseToNumber(chineseMatch.groupValues[1]))
            }
        }
        // English: "Mushoku Tensei Season 2", "Mushoku Tensei S2"
        val enSeasonRegex = Regex("""\b(?:Season|S)\s*(\d+)\b""", RegexOption.IGNORE_CASE)
        val enMatch = enSeasonRegex.find(name)
        if (enMatch != null) {
            val baseName = name.substring(0, enMatch.range.first).trim()
            if (baseName.isNotEmpty()) {
                return ParsedSubjectName(baseName, enMatch.groupValues[1].toIntOrNull())
            }
        }
        return ParsedSubjectName(name, null)
    }

    private fun chineseToNumber(chinese: String): Int {
        return when (chinese) {
            "一" -> 1
            "二" -> 2
            "三" -> 3
            "四" -> 4
            "五" -> 5
            "六" -> 6
            "七" -> 7
            "八" -> 8
            "九" -> 9
            "十" -> 10
            else -> chinese.toIntOrNull() ?: 1
        }
    }

    private suspend fun doGetSeasons(seriesId: String): SearchResponse {
        return authorizedGet("$baseUrl/Shows/$seriesId/Seasons")
    }

    private suspend fun doGetEpisodes(seriesId: String, seasonNum: Int): SearchResponse {
        return authorizedGet("$baseUrl/Shows/$seriesId/Episodes") {
            parameter("Season", seasonNum)
            parameter("fields", "MediaStreams")
        }
    }

    private suspend fun doSearch(
        subjectName: String? = null,
        recursive: Boolean = true,
        parentId: String? = null,
    ): SearchResponse {
        return authorizedGet("$baseUrl/Items") {
            parameter("enableImages", false)
            parameter("recursive", recursive)
            parameter("searchTerm", subjectName)
            parameter("fields", "MediaStreams")
            parameter("parentId", parentId)
        }
    }

    private suspend inline fun <reified T> authorizedGet(
        url: String,
        crossinline configure: HttpRequestBuilder.() -> Unit = {},
    ): T {
        var authorization = getAuthorization()
        var hasRetried = false

        while (true) {
            try {
                return client.use {
                    val response = get(url) {
                        header(HttpHeaders.Authorization, authorization.headerValue)
                        parameter("userId", authorization.userId)
                        configure()
                    }
                    if (response.status == HttpStatusCode.Unauthorized) {
                        throw JellyfinAuthorizationException()
                    }
                    response.body()
                }
            } catch (e: Throwable) {
                val isUnauthorized = e is JellyfinAuthorizationException ||
                        (e is ClientRequestException && e.response.status == HttpStatusCode.Unauthorized)
                if (!isUnauthorized || hasRetried || !invalidateAuthorization(authorization)) {
                    throw e
                }

                hasRetried = true
                authorization = getAuthorization()
            }
        }
    }
}

private class JellyfinAuthorizationException :
    IllegalStateException("Jellyfin rejected the configured authorization")

@Serializable
private class SearchResponse(
    val Items: List<Item> = emptyList(),
)

@Serializable
@Suppress("PropertyName")
private data class MediaStream(
    val Title: String? = null, // 除了字幕以外其他可能没有
    val Language: String? = null, // 字幕语言代码，如 chs
    val Type: String,
    val Codec: String? = null, // 除了字幕以外其他可能没有
    val Index: Int,
    val IsExternal: Boolean, // 是否为外挂字幕
    val IsTextSubtitleStream: Boolean, // 是否可下载
)

@Serializable
@Suppress("PropertyName")
private data class Item(
    val Name: String,
    val SeasonName: String? = null,
    val SeriesName: String? = null,
    val Id: String,
    val OriginalTitle: String? = null, // 日文
    val IndexNumber: Int? = null,
    val ParentIndexNumber: Int? = null,
    val Type: String, // "Episode", "Series", ...
    val MediaStreams: List<MediaStream> = emptyList(),
)
