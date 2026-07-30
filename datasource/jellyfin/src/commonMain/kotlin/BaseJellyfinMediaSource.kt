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
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.asFlow
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
import me.him188.ani.utils.logging.warn

private const val TYPE_EPISODE = "Episode"
private const val TYPE_MOVIE = "Movie"
private const val TYPE_SEASON = "Season"
private const val TYPE_SERIES = "Series"
private const val TITLE_SEARCH_LIMIT = 50
private const val FIELD_PROVIDER_IDS = "ProviderIds"
private const val PROVIDER_ID_BANGUMI = "Bangumi"
private const val PROVIDER_ID_BANGUMI_SUBJECT = "BangumiSubject"

abstract class BaseJellyfinMediaSource(
    private val client: ScopedHttpClient,
) : HttpMediaSource() {
    abstract val baseUrl: String
    abstract val userId: String
    abstract val apiKey: String

    override suspend fun checkConnection(): ConnectionStatus {
        try {
            doSearch(
                subjectName = "AA测试BB",
                limit = 1,
                enableTotalRecordCount = false,
            )
            return ConnectionStatus.SUCCESS
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            return ConnectionStatus.FAILED
        }
    }

    override suspend fun fetch(query: MediaFetchRequest): SizedSource<MediaMatch> {
        return SinglePagePagedSource {
            findBySubjectNames(query)
                .mapNotNull { it.toMediaMatch(query) }
                .filter { it.matches(query) != false }
                .asFlow()
        }
    }

    /**
     * Tries all known subject names and their season-less variants in order, but stops once an
     * exact Jellyfin title yields the requested episode. Results from non-exact title matches are
     * retained as a fallback.
     */
    private suspend fun findBySubjectNames(query: MediaFetchRequest): List<MatchedItem> {
        val fallbackMatches = linkedMapOf<String, MatchedItem>()

        for (subjectName in query.subjectNames
            .asSequence()
            .map(String::trim)
            .filter(String::isNotBlank)
            .distinct()) {
            val parsedSubjectName = parseSubjectName(subjectName)
            for (searchName in sequenceOf(subjectName, parsedSubjectName.baseName).distinct()) {
                val searchResults = doSearch(
                    subjectName = searchName,
                    fields = FIELD_PROVIDER_IDS,
                    limit = TITLE_SEARCH_LIMIT,
                    enableTotalRecordCount = false,
                ).Items.filter(Item::isSupportedSearchResult)

                if (searchResults.isEmpty()) {
                    continue
                }

                val preferredCandidates = searchResults.preferredCandidatesFor(searchName)
                val preferredMatches = findRequestedItems(
                    preferredCandidates,
                    query,
                    parsedSubjectName.targetSeason,
                )
                val matches = if (preferredMatches.isNotEmpty()) {
                    preferredMatches
                } else {
                    findRequestedItems(
                        searchResults.filterNot { candidate ->
                            preferredCandidates.any { it.Id == candidate.Id }
                        },
                        query,
                        parsedSubjectName.targetSeason,
                    )
                }

                if (matches.isEmpty()) {
                    continue
                }

                hydrateMediaStreams(matches).forEach { match ->
                    val existing = fallbackMatches[match.item.Id]
                    if (existing == null || match.confidence > existing.confidence) {
                        fallbackMatches[match.item.Id] = match
                    }
                }

                val exactIdMatches = fallbackMatches.values.filter {
                    it.confidence == BangumiMatchConfidence.EPISODE
                }
                if (exactIdMatches.isNotEmpty()) {
                    return exactIdMatches
                }

                if (
                    preferredMatches.isNotEmpty() &&
                    preferredCandidates.any { it.hasExactTitle(searchName) }
                ) {
                    return fallbackMatches.values.sortedByDescending { it.confidence }
                }
            }
        }

        return fallbackMatches.values.sortedByDescending { it.confidence }
    }

    /**
     * Prefer the narrowest exact container so a season title does not expand the whole series.
     */
    private fun List<Item>.preferredCandidatesFor(subjectName: String): List<Item> {
        val exactMatches = filter { it.hasExactTitle(subjectName) }
        if (exactMatches.isEmpty()) return this

        return exactMatches.filter { it.Type == TYPE_SEASON }
            .ifEmpty { exactMatches.filter { it.Type == TYPE_MOVIE } }
            .ifEmpty { exactMatches.filter { it.Type == TYPE_SERIES } }
            .ifEmpty { exactMatches.filter { it.Type == TYPE_EPISODE } }
    }

    private suspend fun findRequestedItems(
        candidates: List<Item>,
        query: MediaFetchRequest,
        targetSeason: Int?,
    ): List<MatchedItem> {
        return buildList {
            for (candidate in candidates) {
                val containerSubjectMatch = candidate.containerSubjectIdMatch(query)
                if (containerSubjectMatch == ProviderIdMatch.CONFLICT) {
                    continue
                }
                if (
                    containerSubjectMatch != ProviderIdMatch.MATCH &&
                    !candidate.matchesTargetSeason(targetSeason)
                ) {
                    continue
                }

                val requestedItems: List<RequestedItem> = when (candidate.Type) {
                    TYPE_SERIES -> {
                        val seasons = doGetSeasons(seriesId = candidate.Id).Items
                        val matchedSeasons = if (targetSeason != null) {
                            seasons.filter { it.IndexNumber == targetSeason }
                        } else {
                            seasons
                        }

                        if (matchedSeasons.isNotEmpty()) {
                            buildList {
                                for (season in matchedSeasons) {
                                    val seasonSubjectMatch = season.containerSubjectIdMatch(query)
                                    if (seasonSubjectMatch == ProviderIdMatch.CONFLICT) continue
                                    val seasonNumber = season.IndexNumber ?: continue
                                    doGetEpisodes(
                                        seriesId = candidate.Id,
                                        seasonNum = seasonNumber,
                                    ).Items
                                        .filter(Item::isPlayableSearchResult)
                                        .forEach { item ->
                                            add(
                                                RequestedItem(
                                                    item = item,
                                                    inheritedSubjectMatch =
                                                        seasonSubjectMatch == ProviderIdMatch.MATCH,
                                                ),
                                            )
                                        }
                                }
                            }
                        } else {
                            doSearch(
                                parentId = candidate.Id,
                                fields = FIELD_PROVIDER_IDS,
                                enableTotalRecordCount = false,
                            ).Items
                                .filter(Item::isPlayableSearchResult)
                                .filter { item ->
                                    item.containerSubjectIdMatch(query) == ProviderIdMatch.MATCH ||
                                            item.matchesTargetSeason(targetSeason)
                                }
                                .map { RequestedItem(it, inheritedSubjectMatch = false) }
                        }
                    }

                    TYPE_SEASON -> {
                        doSearch(
                            parentId = candidate.Id,
                            fields = FIELD_PROVIDER_IDS,
                            enableTotalRecordCount = false,
                        ).Items
                            .filter(Item::isPlayableSearchResult)
                            .map {
                                RequestedItem(
                                    item = it,
                                    inheritedSubjectMatch =
                                        containerSubjectMatch == ProviderIdMatch.MATCH,
                                )
                            }
                    }

                    TYPE_EPISODE, TYPE_MOVIE -> listOf(
                        RequestedItem(
                            item = candidate,
                            inheritedSubjectMatch = containerSubjectMatch == ProviderIdMatch.MATCH,
                        ),
                    )

                    else -> emptyList()
                }

                requestedItems.forEach itemLoop@ { requestedItem ->
                    val item = requestedItem.item
                    if (!item.matchesEpisodeNumber(query)) {
                        return@itemLoop
                    }

                    val confidence = item.bangumiMatchConfidence(
                        query = query,
                        inheritedSubjectMatch = requestedItem.inheritedSubjectMatch,
                    ) ?: return@itemLoop
                    add(MatchedItem(item, confidence))
                }
            }
        }.groupBy { it.item.Id }
            .values
            .map { matches -> matches.maxBy { it.confidence } }
            .sortedByDescending { it.confidence }
    }

    /**
     * MediaStreams is large and unnecessary during discovery. Fetch it only for selected items.
     * If this optional enrichment fails, keep the playable item and continue without subtitles.
     */
    private suspend fun hydrateMediaStreams(matches: List<MatchedItem>): List<MatchedItem> {
        if (matches.isEmpty()) return emptyList()

        val hydratedItems = try {
            doSearch(
                recursive = false,
                itemIds = matches.joinToString(",") { it.item.Id },
                fields = "MediaStreams",
                limit = matches.size,
                enableTotalRecordCount = false,
            ).Items.associateBy { it.Id }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            logger.warn(e) {
                "Failed to load MediaStreams for Jellyfin items; continuing without subtitle metadata"
            }
            emptyMap()
        }

        return matches.map { match ->
            val item = match.item
            match.copy(
                item = hydratedItems[item.Id]
                    ?.let { item.copy(MediaStreams = it.MediaStreams) }
                    ?: item,
            )
        }
    }

    private fun Item.matchesEpisodeNumber(query: MediaFetchRequest): Boolean {
        return when (Type) {
            TYPE_EPISODE -> {
                val indexNumber = IndexNumber ?: return false
                val episodeSort = EpisodeSort(indexNumber)
                episodeSort == query.episodeSort || episodeSort == query.episodeEp
            }

            TYPE_MOVIE -> true
            else -> false
        }
    }

    private fun MatchedItem.toMediaMatch(query: MediaFetchRequest): MediaMatch? = with(item) {
        val (originalTitle, episodeRange) = when (Type) {
            TYPE_EPISODE -> {
                val indexNumber = IndexNumber ?: return null
                "$indexNumber $Name" to EpisodeRange.single(EpisodeSort(indexNumber))
            }

            TYPE_MOVIE -> Name to EpisodeRange.unknownSeason()
            else -> return null
        }

        return MediaMatch(
            media = DefaultMedia(
                mediaId = Id,
                mediaSourceId = mediaSourceId,
                originalUrl = "$baseUrl/Items/$Id",
                download = ResourceLocation.HttpStreamingFile(
                    uri = getDownloadUri(Id),
                ),
                originalTitle = originalTitle,
                publishedTime = 0,
                properties = MediaProperties(
                    subjectName = subjectNameFor(query),
                    episodeName = Name,
                    subtitleLanguageIds = listOf("CHS"),
                    resolution = "1080P",
                    alliance = mediaSourceId,
                    size = FileSize.Unspecified,
                    subtitleKind = SubtitleKind.EXTERNAL_PROVIDED,
                ),
                extraFiles = MediaExtraFiles(
                    subtitles = getSubtitles(Id, MediaStreams),
                ),
                episodeRange = episodeRange,
                location = MediaSourceLocation.Lan,
                kind = MediaSourceKind.WEB,
            ),
            kind = if (confidence == BangumiMatchConfidence.EPISODE) {
                MatchKind.EXACT
            } else {
                MatchKind.FUZZY
            },
        )
    }

    protected abstract fun getDownloadUri(itemId: String): String

    private fun getSubtitles(itemId: String, mediaStreams: List<MediaStream>): List<Subtitle> {
        return mediaStreams
            .filter { it.Type == "Subtitle" && it.IsTextSubtitleStream && it.IsExternal && it.Codec != null }
            .map { stream ->
                Subtitle(
                    uri = getSubtitleUri(itemId, stream.Index, stream.Codec!!),
                    language = stream.Language,
                    mimeType = when (stream.Codec.lowercase()) {
                        "ass" -> "text/x-ass"
                        else -> "application/octet-stream"
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

    private suspend fun doGetSeasons(seriesId: String) = client.use {
        get("$baseUrl/Shows/$seriesId/Seasons") {
            configureAuthorizationHeaders()
            parameter("userId", userId)
            parameter("fields", FIELD_PROVIDER_IDS)
        }.body<SearchResponse>()
    }

    private suspend fun doGetEpisodes(seriesId: String, seasonNum: Int) = client.use {
        get("$baseUrl/Shows/$seriesId/Episodes") {
            configureAuthorizationHeaders()
            parameter("userId", userId)
            parameter("Season", seasonNum)
            parameter("fields", FIELD_PROVIDER_IDS)
        }.body<SearchResponse>()
    }

    private suspend fun doSearch(
        subjectName: String? = null,
        recursive: Boolean = true,
        parentId: String? = null,
        itemIds: String? = null,
        fields: String? = null,
        limit: Int? = null,
        enableTotalRecordCount: Boolean? = null,
    ) = client.use {
        get("$baseUrl/Items") {
            configureAuthorizationHeaders()
            parameter("userId", userId)
            parameter("enableImages", false)
            parameter("recursive", recursive)
            subjectName?.let { parameter("searchTerm", it) }
            parentId?.let { parameter("parentId", it) }
            itemIds?.let { parameter("ids", it) }
            fields?.let { parameter("fields", it) }
            limit?.let { parameter("limit", it) }
            enableTotalRecordCount?.let { parameter("enableTotalRecordCount", it) }
        }.body<SearchResponse>()
    }

    private fun HttpRequestBuilder.configureAuthorizationHeaders() {
        header(
            HttpHeaders.Authorization,
            "MediaBrowser Token=\"$apiKey\"",
        )
    }
}

private val Item.isSupportedSearchResult: Boolean
    get() = Type == TYPE_SERIES || Type == TYPE_SEASON || Type == TYPE_EPISODE || Type == TYPE_MOVIE

private val Item.isPlayableSearchResult: Boolean
    get() = Type == TYPE_EPISODE || Type == TYPE_MOVIE

private fun Item.matchesTargetSeason(targetSeason: Int?): Boolean {
    if (targetSeason == null) return true
    return when (Type) {
        TYPE_SEASON -> IndexNumber == targetSeason
        TYPE_EPISODE -> ParentIndexNumber == targetSeason
        else -> true
    }
}

private fun Item.subjectNameFor(query: MediaFetchRequest): String? {
    val matchingSeasonName = SeasonName
        ?.trim()
        ?.takeIf(String::isNotEmpty)
        ?.takeIf { seasonName ->
            sequenceOf(query.subjectNameCN)
                .plus(query.subjectNames.asSequence())
                .filterNotNull()
                .map(String::trim)
                .any { it.equals(seasonName, ignoreCase = true) }
        }
    return matchingSeasonName
        ?: query.subjectNames.firstOrNull()?.takeIf(String::isNotBlank)
        ?: query.subjectNameCN?.takeIf(String::isNotBlank)
        ?: SeriesName?.takeIf(String::isNotBlank)
}

private fun Item.hasExactTitle(subjectName: String): Boolean {
    return sequenceOf(Name, SeasonName, SeriesName, OriginalTitle)
        .filterNotNull()
        .map(String::trim)
        .any { it.equals(subjectName, ignoreCase = true) }
}

private fun Item.containerSubjectIdMatch(query: MediaFetchRequest): ProviderIdMatch {
    // A Jellyfin Series can contain several Bangumi subjects, one per season. Its provider ID
    // commonly points to the first season, so it cannot authenticate or reject a child episode.
    if (Type == TYPE_SERIES) return ProviderIdMatch.UNKNOWN

    val actualSubjectId = when (Type) {
        TYPE_EPISODE -> providerId(PROVIDER_ID_BANGUMI_SUBJECT)
        TYPE_MOVIE, TYPE_SEASON -> providerId(PROVIDER_ID_BANGUMI)
        else -> null
    }
    return compareProviderId(actualSubjectId, query.subjectId)
}

private fun Item.bangumiMatchConfidence(
    query: MediaFetchRequest,
    inheritedSubjectMatch: Boolean,
): BangumiMatchConfidence? {
    val subjectMatch = when (Type) {
        TYPE_EPISODE -> compareProviderId(
            providerId(PROVIDER_ID_BANGUMI_SUBJECT),
            query.subjectId,
        )

        TYPE_MOVIE -> compareProviderId(
            providerId(PROVIDER_ID_BANGUMI),
            query.subjectId,
        )

        else -> ProviderIdMatch.UNKNOWN
    }
    if (subjectMatch == ProviderIdMatch.CONFLICT) return null

    val episodeMatch = if (Type == TYPE_EPISODE) {
        compareProviderId(providerId(PROVIDER_ID_BANGUMI), query.episodeId)
    } else {
        ProviderIdMatch.UNKNOWN
    }
    if (episodeMatch == ProviderIdMatch.CONFLICT) return null

    return when {
        episodeMatch == ProviderIdMatch.MATCH -> BangumiMatchConfidence.EPISODE
        subjectMatch == ProviderIdMatch.MATCH || inheritedSubjectMatch ->
            BangumiMatchConfidence.SUBJECT

        else -> BangumiMatchConfidence.NONE
    }
}

private fun Item.providerId(name: String): String? {
    return ProviderIds.entries
        .firstOrNull { it.key.equals(name, ignoreCase = true) }
        ?.value
        ?.trim()
        ?.takeIf(String::isNotEmpty)
}

private fun compareProviderId(actual: String?, expected: String): ProviderIdMatch {
    val normalizedExpected = expected.trim().takeIf(String::isNotEmpty)
        ?: return ProviderIdMatch.UNKNOWN
    val normalizedActual = actual?.trim()?.takeIf(String::isNotEmpty)
        ?: return ProviderIdMatch.UNKNOWN
    return if (normalizedActual == normalizedExpected) {
        ProviderIdMatch.MATCH
    } else {
        ProviderIdMatch.CONFLICT
    }
}

private enum class ProviderIdMatch {
    UNKNOWN,
    MATCH,
    CONFLICT,
}

private enum class BangumiMatchConfidence {
    NONE,
    SUBJECT,
    EPISODE,
}

private data class MatchedItem(
    val item: Item,
    val confidence: BangumiMatchConfidence,
)

private data class RequestedItem(
    val item: Item,
    val inheritedSubjectMatch: Boolean,
)

@Serializable
private class SearchResponse(
    val Items: List<Item> = emptyList(),
)

@Serializable
@Suppress("PropertyName")
private data class MediaStream(
    val Title: String? = null,
    val Language: String? = null,
    val Type: String,
    val Codec: String? = null,
    val Index: Int,
    val IsExternal: Boolean,
    val IsTextSubtitleStream: Boolean,
)

@Serializable
@Suppress("PropertyName")
private data class Item(
    val Name: String,
    val SeasonName: String? = null,
    val SeriesName: String? = null,
    val Id: String,
    val OriginalTitle: String? = null,
    val IndexNumber: Int? = null,
    val ParentIndexNumber: Int? = null,
    val Type: String,
    val ProviderIds: Map<String, String> = emptyMap(),
    val MediaStreams: List<MediaStream> = emptyList(),
)
