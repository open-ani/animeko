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
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapConcat
import kotlinx.coroutines.flow.toList
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
import me.him188.ani.utils.logging.logger
import me.him188.ani.utils.logging.warn
import kotlin.math.min
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

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
        var playbackResult = requestPlaybackInfo(
            requestedMediaSourceId = mediaSourceId,
            maxStreamingBitrate = negotiatedMaxBitrate,
            audioStreamIndex = null,
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
        val incompatibleDefaultAudioIndex = defaultAudioStream
            ?.takeUnless { defaultDeviceProfile.supportsDirectAudioCodec(it.codec) }
            ?.index
        val originalDeviceProfile = if (quality.mode == JellyfinPlaybackQualityMode.ORIGINAL) {
            defaultDeviceProfile.withOriginalVideoCodec(source.videoCodec)
        } else {
            defaultDeviceProfile
        }
        val shouldRenegotiateOriginal = quality.mode == JellyfinPlaybackQualityMode.ORIGINAL &&
                !source.supportsDirectPlay &&
                originalDeviceProfile != defaultDeviceProfile
        if (incompatibleDefaultAudioIndex != null || shouldRenegotiateOriginal) {
            logger.debug {
                "Renegotiating Jellyfin playback for compatibility: mode=${quality.mode}, " +
                        "audioStreamIndex=$incompatibleDefaultAudioIndex, " +
                        "preserveOriginalVideo=${originalDeviceProfile != defaultDeviceProfile}"
            }
            playbackResult = requestPlaybackInfo(
                requestedMediaSourceId = source.id,
                maxStreamingBitrate = negotiatedMaxBitrate,
                audioStreamIndex = incompatibleDefaultAudioIndex,
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
        val transcodingParameters = transcodingUrl?.queryParameters()
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

private val logger = logger<BaseJellyfinMediaSource>()

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
