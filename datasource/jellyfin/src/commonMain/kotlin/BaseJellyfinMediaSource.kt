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
import io.ktor.client.HttpClient
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
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
import me.him188.ani.utils.ktor.UrlHelpers
import me.him188.ani.utils.logging.debug
import me.him188.ani.utils.logging.warn
import kotlin.math.min
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

private const val TYPE_EPISODE = "Episode"
private const val TYPE_MOVIE = "Movie"
private const val TYPE_SEASON = "Season"
private const val TYPE_SERIES = "Series"
private const val TITLE_SEARCH_PAGE_SIZE = 50
private const val TITLE_SEARCH_MAX_PAGES = 4
private const val TITLE_SEARCH_ITEM_TYPES = "$TYPE_SERIES,$TYPE_SEASON,$TYPE_EPISODE,$TYPE_MOVIE"
private const val FIELD_PROVIDER_IDS = "ProviderIds"
private const val PROVIDER_ID_BANGUMI = "Bangumi"
private const val PROVIDER_ID_BANGUMI_SUBJECT = "BangumiSubject"

abstract class BaseJellyfinMediaSource(
    private val client: ScopedHttpClient,
) : HttpMediaSource() {
    abstract val baseUrl: String

    private val bitrateDetectionMutex = Mutex()
    private var detectedBitrateCache: DetectedBitrate? = null

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

    /**
     * Negotiates a stream for one physical Jellyfin media source.
     *
     * [mediaSourceId] is deliberately retained across quality changes. Jellyfin may return
     * multiple physical versions in PlaybackInfo, but Animeko continues to treat those as media
     * source selection rather than quality selection.
     */
    suspend fun createPlaybackPlan(
        itemId: String,
        quality: JellyfinPlaybackQuality,
        mediaSourceId: String? = null,
        startPositionMillis: Long = 0,
        forceAutoDetection: Boolean = false,
        audioStreamIndex: Int? = null,
    ): JellyfinPlaybackPlan {
        val effectiveMaxBitrate = when (quality.mode) {
            JellyfinPlaybackQualityMode.AUTO -> detectMaxStreamingBitrate(forceAutoDetection)
            JellyfinPlaybackQualityMode.ORIGINAL -> Int.MAX_VALUE
            JellyfinPlaybackQualityMode.FIXED -> checkNotNull(quality.maxBitrate)
        }
        val defaultDeviceProfile = JellyfinDeviceProfile(
            maxStreamingBitrate = effectiveMaxBitrate,
            maxStaticBitrate = effectiveMaxBitrate,
        )

        suspend fun requestPlaybackInfo(
            requestedMediaSourceId: String?,
            maxStreamingBitrate: Int,
            audioStreamIndex: Int?,
            deviceProfile: JellyfinDeviceProfile,
        ) = authorizedRequest { httpClient, authorization ->
            val response = httpClient.post("$baseUrl/Items/$itemId/PlaybackInfo") {
                header(HttpHeaders.Authorization, authorization.headerValue)
                contentType(ContentType.Application.Json)
                setBody(
                    JellyfinPlaybackInfoRequest(
                        userId = authorization.userId,
                        maxStreamingBitrate = maxStreamingBitrate,
                        startTimeTicks = startPositionMillis.coerceAtLeast(0) * TICKS_PER_MILLISECOND,
                        mediaSourceId = requestedMediaSourceId,
                        audioStreamIndex = audioStreamIndex,
                        // Subtitle burn-in belongs to the separate Jellyfin subtitle feature.
                        // Bitrate negotiation must not select a server subtitle implicitly.
                        subtitleStreamIndex = DISABLED_SUBTITLE_STREAM_INDEX,
                        deviceProfile = deviceProfile,
                    ),
                )
            }
            if (response.status == HttpStatusCode.Unauthorized) {
                throw JellyfinAuthorizationException()
            }
            response.body<JellyfinPlaybackInfoResponse>() to authorization
        }

        fun selectSource(
            response: JellyfinPlaybackInfoResponse,
            requestedMediaSourceId: String?,
        ): JellyfinPlaybackMediaSource {
            check(response.errorCode == null) {
                "Jellyfin could not create playback info: ${response.errorCode}"
            }
            return requestedMediaSourceId
                ?.let { requested -> response.mediaSources.firstOrNull { it.id == requested } }
                ?: response.mediaSources.firstOrNull()
                ?: error("Jellyfin PlaybackInfo did not include a media source")
        }

        var negotiatedMaxBitrate = effectiveMaxBitrate
        var negotiatedAudioStreamIndex = audioStreamIndex
        var playbackResult = requestPlaybackInfo(
            requestedMediaSourceId = mediaSourceId,
            maxStreamingBitrate = negotiatedMaxBitrate,
            audioStreamIndex = negotiatedAudioStreamIndex,
            deviceProfile = defaultDeviceProfile,
        )
        var response = playbackResult.first
        var source = selectSource(response, mediaSourceId)

        val defaultAudioStream = source.defaultAudioStreamIndex?.let { index ->
            source.mediaStreams.firstOrNull {
                it.index == index && it.type.equals("Audio", ignoreCase = true)
            }
        }
        // Jellyfin can approve Direct Play because a compatible alternate audio stream exists,
        // while still returning an incompatible default stream. Explicitly selecting that default
        // makes the server negotiate the required audio conversion instead of handing it to the
        // player unchanged.
        val incompatibleDefaultAudioIndex = if (negotiatedAudioStreamIndex == null) {
            defaultAudioStream
                ?.takeUnless { defaultDeviceProfile.supportsDirectAudioCodec(it.codec) }
                ?.index
        } else {
            null
        }
        val originalDeviceProfile = if (quality.mode == JellyfinPlaybackQualityMode.ORIGINAL) {
            defaultDeviceProfile.withOriginalVideoCodec(source.videoCodec)
        } else {
            defaultDeviceProfile
        }
        val shouldRenegotiateOriginal = quality.mode == JellyfinPlaybackQualityMode.ORIGINAL &&
                !source.supportsDirectPlay &&
                originalDeviceProfile != defaultDeviceProfile
        val compatibilityAudioStreamIndex = negotiatedAudioStreamIndex ?: incompatibleDefaultAudioIndex
        if (incompatibleDefaultAudioIndex != null || shouldRenegotiateOriginal) {
            negotiatedAudioStreamIndex = compatibilityAudioStreamIndex
            logger.debug {
                "Renegotiating Jellyfin playback for compatibility: mode=${quality.mode}, " +
                        "audioStreamIndex=$negotiatedAudioStreamIndex, " +
                        "preserveOriginalVideo=${originalDeviceProfile != defaultDeviceProfile}"
            }
            playbackResult = requestPlaybackInfo(
                requestedMediaSourceId = source.id,
                maxStreamingBitrate = negotiatedMaxBitrate,
                audioStreamIndex = negotiatedAudioStreamIndex,
                deviceProfile = originalDeviceProfile.copy(
                    maxStreamingBitrate = negotiatedMaxBitrate,
                    maxStaticBitrate = negotiatedMaxBitrate,
                ),
            )
            response = playbackResult.first
            source = selectSource(response, source.id)
        }

        val canDirectPlay = source.supportsDirectPlay
        val transcodingUrl = source.transcodingUrl?.takeIf(String::isNotBlank)
        val isTranscoding = !canDirectPlay && transcodingUrl != null
        val videoStream = source.videoStream
        val audioStreamIndices = source.mediaStreams
            .filter { it.type.equals("Audio", ignoreCase = true) && it.index >= 0 }
            .map { it.index }
            .sorted()
        val selectedAudioStreamIndex = negotiatedAudioStreamIndex ?: source.defaultAudioStreamIndex
        check(selectedAudioStreamIndex == null || selectedAudioStreamIndex in audioStreamIndices) {
            "Jellyfin selected audio stream $selectedAudioStreamIndex, but it is not present in MediaStreams"
        }
        val transcodingParameters = transcodingUrl?.queryParameters()
        val transcodingSubtitleStreamIndex = transcodingParameters
            ?.valueIgnoreCase("SubtitleStreamIndex")
            ?.toIntOrNull()
        val subtitleMethod = transcodingParameters?.valueIgnoreCase("SubtitleMethod")
        val transcodeReasons = transcodingParameters?.valueIgnoreCase("TranscodeReasons")
        val serverSelectedSubtitle = transcodingSubtitleStreamIndex?.let { it >= 0 } == true
        // Jellyfin includes SubtitleMethod=Encode on some ordinary video/audio transcodes even
        // when no subtitle is selected. A non-negative stream index or the subtitle-specific
        // transcode reason is the evidence that a subtitle would actually be burned in.
        val serverEncodesSubtitle =
            transcodeReasons?.contains("SubtitleCodecNotSupported", ignoreCase = true) == true
        if (serverSelectedSubtitle || serverEncodesSubtitle) {
            logger.warn {
                "Jellyfin ignored disabled subtitles during bitrate negotiation: " +
                        "subtitleStreamIndex=$transcodingSubtitleStreamIndex, " +
                        "subtitleMethod=$subtitleMethod, transcodeReasons=$transcodeReasons"
            }
            throw JellyfinPlaybackUnavailableException(
                quality = quality,
                supportsTranscoding = source.supportsTranscoding || source.supportsDirectStream,
            )
        }
        logger.debug {
            "Jellyfin playback plan: mode=${quality.mode}, maxBitrate=$negotiatedMaxBitrate, " +
                    "directPlay=${source.supportsDirectPlay}, directStream=${source.supportsDirectStream}, " +
                    "supportsTranscoding=${source.supportsTranscoding}, sourceBitrate=${source.bitrate}, " +
                    "hasTranscodingUrl=${transcodingUrl != null}, " +
                    "isTranscoding=$isTranscoding, " +
                    "videoBitrate=${transcodingParameters?.get("VideoBitrate")}, " +
                    "audioBitrate=${transcodingParameters?.get("AudioBitrate")}"
        }
        if (!canDirectPlay && !isTranscoding) {
            throw JellyfinPlaybackUnavailableException(
                quality = quality,
                supportsTranscoding = source.supportsTranscoding || source.supportsDirectStream,
            )
        }
        val uri = if (isTranscoding) {
            UrlHelpers.computeAbsoluteUrl(baseUrl, checkNotNull(transcodingUrl))
        } else {
            getDownloadUri(itemId, playbackResult.second.accessToken)
        }

        return JellyfinPlaybackPlan(
            uri = uri,
            quality = quality,
            effectiveMaxBitrate = negotiatedMaxBitrate
                .takeUnless { quality.mode == JellyfinPlaybackQualityMode.ORIGINAL },
            sourceBitrate = source.totalBitrate(),
            sourceVideoCodec = videoStream?.codec,
            mediaSourceId = source.id,
            playSessionId = response.playSessionId,
            isTranscoding = isTranscoding,
            audioStreamIndices = audioStreamIndices,
            selectedAudioStreamIndex = selectedAudioStreamIndex,
        )
    }

    /**
     * Stops only the transcoder associated with [playSessionId]. Direct playback is unaffected.
     */
    suspend fun stopActiveEncoding(playSessionId: String) {
        authorizedRequest { httpClient, authorization ->
            val response = httpClient.delete("$baseUrl/Videos/ActiveEncodings") {
                header(HttpHeaders.Authorization, authorization.headerValue)
                parameter("deviceId", playbackDeviceId)
                parameter("playSessionId", playSessionId)
            }
            if (response.status == HttpStatusCode.Unauthorized) {
                throw JellyfinAuthorizationException()
            }
        }
    }

    private val playbackDeviceId: String
        get() = "animeko-$mediaSourceId"

    private suspend fun detectMaxStreamingBitrate(force: Boolean): Int {
        return bitrateDetectionMutex.withLock {
            val cached = detectedBitrateCache
            if (!force && cached != null && cached.createdAt.elapsedNow() <= BITRATE_CACHE_DURATION) {
                return@withLock cached.bitrate
            }

            val endpointInfo = try {
                authorizedGet<JellyfinEndpointInfo>("$baseUrl/System/Endpoint")
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                JellyfinEndpointInfo()
            }

            var measuredBitrate: Int? = null
            for (test in BITRATE_TESTS) {
                val mark = TimeSource.Monotonic.markNow()
                val bytes = try {
                    withTimeout(BITRATE_TEST_TIMEOUT) {
                        authorizedRequest { httpClient, authorization ->
                            val response = httpClient.get("$baseUrl/Playback/BitrateTest") {
                                header(HttpHeaders.Authorization, authorization.headerValue)
                                header(HttpHeaders.CacheControl, "no-cache, no-store")
                                parameter("Size", test.bytes)
                            }
                            if (response.status == HttpStatusCode.Unauthorized) {
                                throw JellyfinAuthorizationException()
                            }
                            response.body<ByteArray>()
                        }
                    }
                } catch (_: TimeoutCancellationException) {
                    break
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Throwable) {
                    break
                }
                val elapsedSeconds = mark.elapsedNow().inWholeNanoseconds / 1_000_000_000.0
                if (elapsedSeconds <= 0.0) continue
                measuredBitrate = min(
                    (bytes.size * 8 / elapsedSeconds).toLong(),
                    Int.MAX_VALUE.toLong(),
                ).toInt()
                if (measuredBitrate < test.threshold) break
            }

            var normalized = measuredBitrate
                ?.let { (it * BITRATE_SAFETY_FACTOR).toInt().coerceAtLeast(1) }
                ?: cached?.bitrate
                ?: DEFAULT_AUTO_BITRATE
            if (endpointInfo.isInNetwork) {
                normalized = maxOf(normalized, LAN_AUTO_BITRATE)
            }
            detectedBitrateCache = DetectedBitrate(normalized, TimeSource.Monotonic.markNow())
            normalized
        }
    }

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
            val matches = findBySubjectNames(query)
            val authorization = getAuthorization()
            matches
                .mapNotNull { it.toMediaMatch(query, authorization.accessToken) }
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

        for (subjectSearch in createSubjectSearches(query.subjectNames)) {
            val searchResults = searchByTitle(subjectSearch.name)

            if (searchResults.isEmpty()) {
                continue
            }

            val preferredCandidates = searchResults.preferredCandidatesFor(subjectSearch.name)
            val preferredMatches = findRequestedItems(
                preferredCandidates,
                query,
                subjectSearch.targetSeason,
                subjectSearch.name,
                subjectSearch.isExplicitSeasonTitle,
            )
            val matches = if (preferredMatches.isNotEmpty()) {
                preferredMatches
            } else {
                findRequestedItems(
                    searchResults.filterNot { candidate ->
                        preferredCandidates.any { it.Id == candidate.Id }
                    },
                    query,
                    subjectSearch.targetSeason,
                    subjectSearch.name,
                    subjectSearch.isExplicitSeasonTitle,
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
        }

        return fallbackMatches.values.sortedByDescending { it.confidence }
    }

    private suspend fun searchByTitle(subjectName: String): List<Item> {
        val results = mutableListOf<Item>()
        for (page in 0 until TITLE_SEARCH_MAX_PAGES) {
            val pageItems = doSearch(
                subjectName = subjectName,
                fields = FIELD_PROVIDER_IDS,
                includeItemTypes = TITLE_SEARCH_ITEM_TYPES,
                startIndex = page * TITLE_SEARCH_PAGE_SIZE,
                limit = TITLE_SEARCH_PAGE_SIZE,
                enableTotalRecordCount = false,
            ).Items
            results += pageItems.filter(Item::isSupportedSearchResult)
            if (pageItems.size < TITLE_SEARCH_PAGE_SIZE) {
                break
            }
        }
        return results.distinctBy(Item::Id)
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
        searchName: String,
        isExplicitSeasonTitle: Boolean,
    ): List<MatchedItem> {
        return buildList {
            for (candidate in candidates) {
                val containerSubjectMatch = candidate.containerSubjectIdMatch(query)
                val episodeMatch = candidate.episodeIdMatch(query)
                if (
                    containerSubjectMatch == ProviderIdMatch.CONFLICT ||
                    episodeMatch == ProviderIdMatch.CONFLICT
                ) {
                    continue
                }
                if (
                    containerSubjectMatch != ProviderIdMatch.MATCH &&
                    episodeMatch != ProviderIdMatch.MATCH &&
                    candidate.hasConflictingTargetSeason(targetSeason)
                ) {
                    continue
                }
                if (
                    containerSubjectMatch != ProviderIdMatch.MATCH &&
                    episodeMatch != ProviderIdMatch.MATCH &&
                    !candidate.hasExactContainerTitle(searchName) &&
                    !candidate.matchesTargetSeason(targetSeason)
                ) {
                    continue
                }

                val requestedItems: List<RequestedItem> = when (candidate.Type) {
                    TYPE_SERIES -> {
                        val seasons = doGetSeasons(seriesId = candidate.Id).Items
                        val seriesSubjectMatch = candidate.seriesSubjectIdMatch(query)
                        val matchesExplicitSeasonTitle =
                            isExplicitSeasonTitle && candidate.hasExactContainerTitle(searchName)
                        val hasIsolatedSeriesEvidence =
                            seriesSubjectMatch == ProviderIdMatch.MATCH || matchesExplicitSeasonTitle
                        val usesLocalSeasonNumbering =
                            seasons.size == 1 && hasIsolatedSeriesEvidence
                        val matchedSeasons = seasons.mapNotNull { season ->
                            val seasonSubjectMatch = season.containerSubjectIdMatch(query)
                            when {
                                seasonSubjectMatch == ProviderIdMatch.CONFLICT -> null
                                seasonSubjectMatch == ProviderIdMatch.MATCH ->
                                    MatchedSeason(season, inheritedSubjectMatch = true)

                                usesLocalSeasonNumbering ->
                                    MatchedSeason(
                                        season,
                                        inheritedSubjectMatch =
                                            seriesSubjectMatch == ProviderIdMatch.MATCH,
                                    )

                                season.hasConflictingTargetSeason(targetSeason) -> null

                                season.hasExactContainerTitle(searchName) ->
                                    MatchedSeason(season, inheritedSubjectMatch = false)

                                season.matchesTargetSeason(targetSeason) ->
                                    MatchedSeason(season, inheritedSubjectMatch = false)

                                else -> null
                            }
                        }

                        if (matchedSeasons.isNotEmpty()) {
                            buildList {
                                for ((season, inheritedSubjectMatch) in matchedSeasons) {
                                    val episodeItems = season.IndexNumber?.let { seasonNumber ->
                                        doGetEpisodes(
                                            seriesId = candidate.Id,
                                            seasonNum = seasonNumber,
                                        ).Items
                                    } ?: doSearch(
                                        parentId = season.Id,
                                        fields = FIELD_PROVIDER_IDS,
                                        enableTotalRecordCount = false,
                                    ).Items
                                    episodeItems
                                        .filter(Item::isPlayableSearchResult)
                                        .forEach { item ->
                                            add(
                                                RequestedItem(
                                                    item = item,
                                                    inheritedSubjectMatch = inheritedSubjectMatch,
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
                                    val itemSubjectMatch = item.containerSubjectIdMatch(query)
                                    val itemEpisodeMatch = item.episodeIdMatch(query)
                                    when {
                                        itemSubjectMatch == ProviderIdMatch.CONFLICT -> false
                                        itemEpisodeMatch == ProviderIdMatch.CONFLICT -> false
                                        itemSubjectMatch == ProviderIdMatch.MATCH -> true
                                        itemEpisodeMatch == ProviderIdMatch.MATCH -> true
                                        item.hasConflictingTargetSeason(targetSeason) -> false
                                        item.hasExactContainerTitle(searchName) -> true
                                        else -> item.matchesTargetSeason(targetSeason)
                                    }
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
                    if (
                        item.episodeIdMatch(query) != ProviderIdMatch.MATCH &&
                        !item.matchesEpisodeNumber(query)
                    ) {
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

    private fun MatchedItem.toMediaMatch(
        query: MediaFetchRequest,
        accessToken: String,
    ): MediaMatch? = with(item) {
        val (originalTitle, episodeRange) = when (Type) {
            TYPE_EPISODE -> {
                val indexNumber = IndexNumber ?: return null
                val jellyfinEpisodeSort = EpisodeSort(indexNumber)
                val selectedEpisodeSort = if (
                    confidence == BangumiMatchConfidence.EPISODE &&
                    jellyfinEpisodeSort != query.episodeSort &&
                    jellyfinEpisodeSort != query.episodeEp
                ) {
                    query.episodeEp ?: query.episodeSort
                } else {
                    jellyfinEpisodeSort
                }
                "$indexNumber $Name" to EpisodeRange.single(selectedEpisodeSort)
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
                    uri = getDownloadUri(Id, accessToken),
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

    private data class SubjectSearch(
        val name: String,
        val targetSeason: Int?,
        val isExplicitSeasonTitle: Boolean,
    )

    private fun createSubjectSearches(subjectNames: List<String>): List<SubjectSearch> {
        val parsedNames = subjectNames
            .asSequence()
            .map(String::trim)
            .filter(String::isNotBlank)
            .distinct()
            .map { name -> name to parseSubjectName(name) }
            .toList()
        val sharedTargetSeason = parsedNames
            .mapNotNull { (_, parsed) -> parsed.targetSeason }
            .distinct()
            .singleOrNull()

        return parsedNames
            .asSequence()
            .flatMap { (name, parsed) ->
                val targetSeason = parsed.targetSeason ?: sharedTargetSeason
                sequence {
                    yield(
                        SubjectSearch(
                            name = name,
                            targetSeason = targetSeason,
                            isExplicitSeasonTitle = parsed.targetSeason != null,
                        ),
                    )
                    if (parsed.baseName != name) {
                        yield(
                            SubjectSearch(
                                name = parsed.baseName,
                                targetSeason = targetSeason,
                                isExplicitSeasonTitle = false,
                            ),
                        )
                    }
                }
            }
            .distinctBy { search -> search.name.lowercase() to search.targetSeason }
            .toList()
    }

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
        return authorizedGet("$baseUrl/Shows/$seriesId/Seasons") {
            parameter("fields", FIELD_PROVIDER_IDS)
        }
    }

    private suspend fun doGetEpisodes(seriesId: String, seasonNum: Int): SearchResponse {
        return authorizedGet("$baseUrl/Shows/$seriesId/Episodes") {
            parameter("Season", seasonNum)
            parameter("fields", FIELD_PROVIDER_IDS)
        }
    }

    private suspend fun doSearch(
        subjectName: String? = null,
        recursive: Boolean = true,
        parentId: String? = null,
        itemIds: String? = null,
        fields: String? = null,
        includeItemTypes: String? = null,
        startIndex: Int? = null,
        limit: Int? = null,
        enableTotalRecordCount: Boolean? = null,
    ): SearchResponse {
        return authorizedGet("$baseUrl/Items") {
            parameter("enableImages", false)
            parameter("recursive", recursive)
            subjectName?.let { parameter("searchTerm", it) }
            parentId?.let { parameter("parentId", it) }
            itemIds?.let { parameter("ids", it) }
            fields?.let { parameter("fields", it) }
            includeItemTypes?.let { parameter("includeItemTypes", it) }
            startIndex?.let { parameter("startIndex", it) }
            limit?.let { parameter("limit", it) }
            enableTotalRecordCount?.let { parameter("enableTotalRecordCount", it) }
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

    private suspend fun <T> authorizedRequest(
        request: suspend (HttpClient, Authorization) -> T,
    ): T {
        var authorization = getAuthorization()
        var hasRetried = false

        while (true) {
            try {
                return client.use {
                    request(this, authorization)
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

    private data class DetectedBitrate(
        val bitrate: Int,
        val createdAt: TimeSource.Monotonic.ValueTimeMark,
    )

    private data class BitrateTest(
        val bytes: Int,
        val threshold: Int,
    )

    private companion object {
        const val TICKS_PER_MILLISECOND = 10_000L
        const val DISABLED_SUBTITLE_STREAM_INDEX = -1
        const val BITRATE_SAFETY_FACTOR = 0.7
        const val DEFAULT_AUTO_BITRATE = 8_000_000
        const val LAN_AUTO_BITRATE = 140_000_000
        val BITRATE_TEST_TIMEOUT = 5.seconds
        val BITRATE_CACHE_DURATION = 1.hours
        val BITRATE_TESTS = listOf(
            BitrateTest(bytes = 500_000, threshold = 500_000),
            BitrateTest(bytes = 1_000_000, threshold = 20_000_000),
            BitrateTest(bytes = 3_000_000, threshold = 50_000_000),
        )
    }
}

private val JellyfinPlaybackMediaSource.videoStream: JellyfinPlaybackMediaStream?
    get() = mediaStreams.firstOrNull { it.type.equals("Video", ignoreCase = true) }

private val JellyfinPlaybackMediaSource.videoCodec: String?
    get() = videoStream?.codec

private fun JellyfinPlaybackMediaSource.totalBitrate(): Int? {
    bitrate?.takeIf { it > 0 }?.let { return it }
    return mediaStreams
        .sumOf { (it.bitrate ?: 0).toLong() }
        .takeIf { it > 0 }
        ?.coerceAtMost(Int.MAX_VALUE.toLong())
        ?.toInt()
}

private fun JellyfinDeviceProfile.supportsDirectAudioCodec(codec: String?): Boolean {
    if (codec.isNullOrBlank()) return true
    return directPlayProfiles.any { it.audioCodec.supportsCodec(codec) }
}

private fun JellyfinDeviceProfile.supportsDirectVideoCodec(codec: String): Boolean {
    return directPlayProfiles.any { it.videoCodec.supportsCodec(codec) }
}

private fun JellyfinDeviceProfile.withOriginalVideoCodec(sourceVideoCodec: String?): JellyfinDeviceProfile {
    val codec = sourceVideoCodec?.takeIf { it.isNotBlank() } ?: return this
    if (!supportsDirectVideoCodec(codec)) return this

    return copy(
        transcodingProfiles = transcodingProfiles.map { profile ->
            if (!profile.type.equals("Video", ignoreCase = true)) return@map profile
            profile.copy(
                videoCodec = buildList {
                    add(codec)
                    addAll(profile.videoCodec.codecNames())
                }.distinctBy(String::lowercase).joinToString(","),
            )
        },
    )
}

private fun String.supportsCodec(codec: String): Boolean {
    return isBlank() || codecNames().any { it.equals(codec, ignoreCase = true) }
}

private fun String.codecNames(): List<String> {
    return split(',').map(String::trim).filter(String::isNotEmpty)
}

private fun String.queryParameters(): Map<String, String> {
    return substringAfter('?', "")
        .split('&')
        .mapNotNull { parameter ->
            val separator = parameter.indexOf('=')
            if (separator <= 0) return@mapNotNull null
            parameter.substring(0, separator) to parameter.substring(separator + 1)
        }
        .toMap()
}

private fun Map<String, String>.valueIgnoreCase(name: String): String? {
    return entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value
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

private fun Item.hasConflictingTargetSeason(targetSeason: Int?): Boolean {
    if (targetSeason == null) return false
    val actualSeason = when (Type) {
        TYPE_SEASON -> IndexNumber
        TYPE_EPISODE -> ParentIndexNumber
        else -> null
    }
    return actualSeason != null && actualSeason != targetSeason
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

private fun Item.hasExactContainerTitle(subjectName: String): Boolean {
    val titles = when (Type) {
        TYPE_EPISODE -> sequenceOf(SeasonName)
        TYPE_MOVIE, TYPE_SEASON, TYPE_SERIES -> sequenceOf(Name, OriginalTitle)
        else -> emptySequence()
    }
    return titles
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

private fun Item.seriesSubjectIdMatch(query: MediaFetchRequest): ProviderIdMatch {
    if (Type != TYPE_SERIES) return ProviderIdMatch.UNKNOWN
    return compareProviderId(providerId(PROVIDER_ID_BANGUMI), query.subjectId)
}

private fun Item.episodeIdMatch(query: MediaFetchRequest): ProviderIdMatch {
    if (Type != TYPE_EPISODE) return ProviderIdMatch.UNKNOWN
    return compareProviderId(providerId(PROVIDER_ID_BANGUMI), query.episodeId)
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

private data class MatchedSeason(
    val item: Item,
    val inheritedSubjectMatch: Boolean,
)

private class JellyfinAuthorizationException :
    IllegalStateException("Jellyfin rejected the configured authorization")

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
