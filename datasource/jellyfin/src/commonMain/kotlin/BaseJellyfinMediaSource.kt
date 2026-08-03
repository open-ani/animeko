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
import me.him188.ani.datasources.api.MediaChapter
import me.him188.ani.datasources.api.MediaChapterKind
import me.him188.ani.datasources.api.MediaExtraFiles
import me.him188.ani.datasources.api.MediaPreviewThumbnails
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
    protected open val itemFields: String = "MediaStreams,Chapters"

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

    internal open fun createPreviewThumbnails(
        itemId: String,
        trickplay: Map<String, Map<String, JellyfinTrickplayManifestDto>>?,
    ): MediaPreviewThumbnails? = null

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

                    val match = MediaMatch(
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
                                chapters = emptyList(),
                                previewThumbnails = createPreviewThumbnails(item.Id, item.Trickplay),
                            ),
                            episodeRange = episodeRange,
                            location = MediaSourceLocation.Lan,
                            kind = MediaSourceKind.WEB,
                        ),
                        kind = MatchKind.FUZZY,
                    )
                    Pair(item, match)
                }
                .filter { (_, match) -> match.matches(query) != false }
                .map { (item, match) ->
                    val chapters = fetchChaptersAndSegments(item)
                    if (chapters.isEmpty()) {
                        match
                    } else {
                        val media = match.media as DefaultMedia
                        match.copy(
                            media = media.copy(
                                extraFiles = MediaExtraFiles(
                                    subtitles = media.extraFiles.subtitles,
                                    chapters = chapters,
                                    previewThumbnails = media.extraFiles.previewThumbnails,
                                ),
                            ),
                        )
                    }
                }
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

    private suspend fun fetchChaptersAndSegments(item: Item): List<MediaChapter> {
        val itemId = item.Id

        // 1. Try Jellyfin 10.10+ native MediaSegments API
        val nativeSegments = fallbackOnFailure { doGetMediaSegments(itemId) }
        val segments = nativeSegments?.Items.orEmpty().mapNotNull { segment ->
            val kind = when (segment.Type.lowercase()) {
                "intro" -> MediaChapterKind.OPENING
                "outro" -> MediaChapterKind.ENDING
                else -> return@mapNotNull null
            }
            val offsetMillis = segment.StartTicks / 10000
            val durationMillis = (segment.EndTicks - segment.StartTicks) / 10000
            if (offsetMillis < 0 || durationMillis <= 0) return@mapNotNull null
            MediaChapter(
                name = kind.displayName,
                durationMillis = durationMillis,
                offsetMillis = offsetMillis,
                kind = kind,
            )
        }.toMutableList()

        // 2. Fill missing segment types from the Intro Skipper plugin.
        if (segments.none { it.kind == MediaChapterKind.OPENING } ||
            segments.none { it.kind == MediaChapterKind.ENDING }
        ) {
            val pluginSegments = fallbackOnFailure { doGetIntroSkipperSegments(itemId) }
            pluginSegments?.toMediaChapters()?.forEach { chapter ->
                if (segments.none { it.kind == chapter.kind }) {
                    segments += chapter
                }
            }
        }

        // 3. Fall back to the legacy Intro Skipper API for any still-missing type.
        if (segments.none { it.kind == MediaChapterKind.OPENING }) {
            fallbackOnFailure { doGetIntroTimestamps(itemId) }
                ?.toMediaChapter(MediaChapterKind.OPENING)
                ?.let(segments::add)
        }
        if (segments.none { it.kind == MediaChapterKind.ENDING }) {
            val opening = segments.firstOrNull { it.kind == MediaChapterKind.OPENING }
            fallbackOnFailure { doGetIntroTimestamps(itemId, mode = "Credits") }
                ?.toMediaChapter(MediaChapterKind.ENDING)
                // Old plugin versions may ignore mode=Credits and return the intro again.
                ?.takeIf { credits ->
                    opening == null || credits.offsetMillis >= opening.offsetMillis + opening.durationMillis
                }
                ?.let(segments::add)
        }

        // 4. Keep embedded chapters alongside skip segments. Their explicit kind prevents them
        // from being mistaken for OP/ED while still allowing the player to display them.
        val embeddedChapters = if (item.Chapters.isNotEmpty()) {
            val sortedChapters = item.Chapters.sortedBy { it.StartPositionTicks }
            sortedChapters.mapIndexed { index, chapter ->
                val startTicks = chapter.StartPositionTicks
                val endTicks = sortedChapters.getOrNull(index + 1)?.StartPositionTicks ?: item.RunTimeTicks
                val offsetMillis = startTicks / 10000
                val durationMillis = if (endTicks != null && endTicks > startTicks) {
                    (endTicks - startTicks) / 10000
                } else {
                    0L
                }
                val name = chapter.Name?.takeIf { it.isNotBlank() }
                    ?: chapter.MarkerType?.takeIf { it.isNotBlank() }
                    ?: "Ch ${index + 1}"
                MediaChapter(name = name, durationMillis = durationMillis, offsetMillis = offsetMillis)
            }
        } else {
            emptyList()
        }

        return embeddedChapters + segments
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
            parameter("fields", itemFields)
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
            parameter("fields", itemFields)
            parameter("parentId", parentId)
        }
    }

    private suspend fun doGetMediaSegments(itemId: String): MediaSegmentQueryResult {
        return authorizedGet("$baseUrl/MediaSegments/$itemId") {
            url.parameters.append("includeSegmentTypes", "Intro")
            url.parameters.append("includeSegmentTypes", "Outro")
        }
    }

    private suspend fun doGetIntroSkipperSegments(itemId: String):IntroSkipperSegmentsDto {
        return authorizedGet("$baseUrl/Episode/$itemId/IntroSkipperSegments")
    }

    private suspend fun doGetIntroTimestamps(itemId: String, mode: String? = null): IntroTimestampsDto {
        return authorizedGet("$baseUrl/Episode/$itemId/IntroTimestamps") {
            mode?.let { parameter("mode", it) }
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
            } catch (e: JellyfinAuthorizationException) {
                if (hasRetried || !invalidateAuthorization(authorization)) {
                    throw e
                }
            } catch (e: ClientRequestException) {
                if (e.response.status != HttpStatusCode.Unauthorized ||
                    hasRetried ||
                    !invalidateAuthorization(authorization)
                ) {
                    throw e
                }
            }

            hasRetried = true
            authorization = getAuthorization()
        }
    }

    override suspend fun fetchPreviewThumbnail(url: String): ByteArray = authorizedGet(url)
}

private class JellyfinAuthorizationException :
    IllegalStateException("Jellyfin rejected the configured authorization")

internal suspend fun <T> fallbackOnFailure(block: suspend () -> T): T? {
    return try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }
}

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
internal data class ChapterInfoDto(
    val StartPositionTicks: Long,
    val Name: String? = null,
    val MarkerType: String? = null,
)

@Serializable
@Suppress("PropertyName")
internal data class MediaSegmentDto(
    val Id: String? = null,
    val ItemId: String? = null,
    val Type: String, // "Intro", "Outro", "Recap", "Preview", "Commercial"
    val StartTicks: Long,
    val EndTicks: Long,
)

@Serializable
@Suppress("PropertyName")
internal data class MediaSegmentQueryResult(
    val Items: List<MediaSegmentDto> = emptyList(),
)

@Serializable
@Suppress("PropertyName")
internal data class IntroTimestampsDto(
    val EpisodeId: String? = null,
    val Valid: Boolean = false,
    val IntroStart: Double = 0.0,
    val IntroEnd: Double = 0.0,
    val ShowSkipPromptAt: Double? = null,
    val HideSkipPromptAt: Double? = null,
)

@Serializable
@Suppress("PropertyName")
internal data class IntroSkipperSegmentsDto(
    val Introduction: IntroSkipperSegmentDto? = null,
    val Credits: IntroSkipperSegmentDto? = null,
)

@Serializable
@Suppress("PropertyName")
internal data class IntroSkipperSegmentDto(
    val Valid: Boolean = false,
    val Start: Double? = null,
    val End: Double? = null,
    val IntroStart: Double? = null,
    val IntroEnd: Double? = null,
) {
    val resolvedStart: Double? get() = Start ?: IntroStart
    val resolvedEnd: Double? get() = End ?: IntroEnd
}

internal fun IntroSkipperSegmentsDto.toMediaChapters(): List<MediaChapter> = listOfNotNull(
    Introduction?.toMediaChapter(MediaChapterKind.OPENING),
    Credits?.toMediaChapter(MediaChapterKind.ENDING),
)

private fun IntroSkipperSegmentDto.toMediaChapter(kind: MediaChapterKind): MediaChapter? {
    val start = resolvedStart ?: return null
    val end = resolvedEnd ?: return null
    if (!Valid || start < 0.0 || end <= start) return null
    val offsetMillis = (start * 1000).toLong()
    val endMillis = (end * 1000).toLong()
    return MediaChapter(
        name = kind.displayName,
        durationMillis = endMillis - offsetMillis,
        offsetMillis = offsetMillis,
        kind = kind,
    )
}

private fun IntroTimestampsDto.toMediaChapter(kind: MediaChapterKind): MediaChapter? {
    if (!Valid || IntroStart < 0.0 || IntroEnd <= IntroStart) return null
    val offsetMillis = (IntroStart * 1000).toLong()
    val endMillis = (IntroEnd * 1000).toLong()
    return MediaChapter(
        name = kind.displayName,
        durationMillis = endMillis - offsetMillis,
        offsetMillis = offsetMillis,
        kind = kind,
    )
}

private val MediaChapterKind.displayName: String
    get() = when (this) {
        MediaChapterKind.OPENING -> "OP"
        MediaChapterKind.ENDING -> "ED"
        MediaChapterKind.CHAPTER -> error("A regular chapter has no fixed display name")
    }

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
    val Chapters: List<ChapterInfoDto> = emptyList(),
    val RunTimeTicks: Long? = null,
    val Trickplay: Map<String, Map<String, JellyfinTrickplayManifestDto>>? = null,
)
