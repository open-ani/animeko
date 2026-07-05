/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.tools.datasourcetestmcp.source

import io.ktor.client.HttpClient
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import me.him188.ani.client.apis.SubjectsAniApi
import me.him188.ani.client.models.AniEpisodeCollection
import me.him188.ani.client.models.AniSubjectCollection
import me.him188.ani.datasources.api.DefaultMedia
import me.him188.ani.datasources.api.EpisodeSort
import me.him188.ani.datasources.api.MediaProperties
import me.him188.ani.datasources.api.SubtitleKind
import me.him188.ani.datasources.api.matcher.WebVideoMatcherProvider
import me.him188.ani.datasources.api.source.MediaFetchRequest
import me.him188.ani.datasources.api.source.MediaMatch
import me.him188.ani.datasources.api.source.MediaSource
import me.him188.ani.datasources.api.source.MediaSourceKind
import me.him188.ani.datasources.api.topic.FileSize
import me.him188.ani.datasources.api.topic.ResourceLocation
import me.him188.ani.tools.datasourcetestmcp.StageResult
import me.him188.ani.tools.datasourcetestmcp.resolver.CandidateTestMode
import me.him188.ani.tools.datasourcetestmcp.resolver.CandidateVideoResolver
import me.him188.ani.tools.datasourcetestmcp.resolver.ChannelTestExecutor
import me.him188.ani.tools.datasourcetestmcp.resolver.WebViewVideoResolverEngine
import me.him188.ani.tools.datasourcetestmcp.resolver.toCandidateResult
import me.him188.ani.tools.datasourcetestmcp.video.VideoProbe
import me.him188.ani.tools.datasourcetestmcp.video.VideoProbeResult
import me.him188.ani.tools.datasourcetestmcp.video.VideoUrlProbeEngine

class SourceTestService(
    private val httpClient: HttpClient,
    private val registry: DataSourceRegistry,
    private val json: Json,
    private val resolver: CandidateVideoResolver = WebViewVideoResolverEngine(),
    private val probe: VideoUrlProbeEngine = VideoProbe(httpClient),
    private val handshakeFailureDomainAdvisor: HandshakeFailureDomainAdvisor = HandshakeFailureDomainAdvisor(httpClient),
) {
    private val channelTestExecutor = ChannelTestExecutor(resolver, probe)

    suspend fun testSubjectEpisodeSource(input: TestSubjectEpisodeSourceInput): SourceTestResult {
        val start = System.currentTimeMillis()
        return testSubjectEpisodeSourceImpl(input)
            .let { it.copy(totalDurationMillis = System.currentTimeMillis() - start) }
    }

    private suspend fun testSubjectEpisodeSourceImpl(input: TestSubjectEpisodeSourceInput): SourceTestResult {
        val stages = mutableListOf<StageResult>()
        val errors = mutableListOf<String>()

        val metadataStart = System.currentTimeMillis()
        val metadata = runCatching {
            fetchAniMetadata(input)
        }.onSuccess { result ->
            stages += StageResult(
                name = "ani_metadata",
                status = "success",
                summary = "Fetched subject and episode metadata from Ani API",
                details = buildJsonObject {
                    put("subjectId", result.subjectId)
                    put("subjectName", result.subjectName)
                    put("subjectNameCn", result.subjectNameCn ?: "")
                    put("episodeId", result.episodeId)
                    put("episodeSort", result.episodeSort)
                    put("episodeEp", result.episodeEp ?: "")
                },
                durationMillis = System.currentTimeMillis() - metadataStart,
            )
        }.onFailure { exception ->
            errors += "Ani metadata lookup failed: ${exception.message.orEmpty()}"
            stages += StageResult(
                name = "ani_metadata",
                status = "failed",
                summary = "Failed to fetch metadata from Ani API",
                errors = listOf("${exception::class.simpleName}: ${exception.message.orEmpty()}"),
                durationMillis = System.currentTimeMillis() - metadataStart,
            )
        }.getOrNull() ?: return SourceTestResult(
            ok = false,
            summary = "Ani metadata lookup failed",
            input = encodeInput(input),
            stages = stages,
            errors = errors,
        )

        val sources = registry.createSources(input.mediaSource)
        return useSources(sources) { createdSources ->
            val sourceById = createdSources.associateBy { it.mediaSourceId }
            val allMatches = mutableListOf<MediaMatch>()
            val fetchStart = System.currentTimeMillis()
            val perSourceJson = buildJsonArray {
                createdSources.forEach { source ->
                    val sourceSpec = input.mediaSource
                        ?.takeIf { createdSources.size == 1 || it.mediaSourceId == source.mediaSourceId }
                    val sourceFetchStart = System.currentTimeMillis()
                    val sourceResult = runCatching {
                        val connection = source.checkConnection()
                        val matches = withTimeout(input.fetchTimeoutMillis) {
                            source.fetch(metadata.request)
                                .results
                                .take(input.maxCandidates)
                                .toList()
                        }
                        connection to matches
                    }
                    val sourceFetchDuration = System.currentTimeMillis() - sourceFetchStart

                    sourceResult.onSuccess { (connection, matches) ->
                        allMatches += matches
                        add(
                            buildJsonObject {
                                put("mediaSourceId", source.mediaSourceId)
                                put("factoryDisplayName", source.info.displayName)
                                put("connectionStatus", connection.toString())
                                put("matchCount", matches.size)
                                put("durationMillis", sourceFetchDuration)
                            },
                        )
                    }.onFailure { exception ->
                        errors += "Fetch failed for ${source.mediaSourceId}: ${exception.message.orEmpty()}"
                        val handshakeHint = handshakeFailureDomainAdvisor.analyzeIfNeeded(
                            sourceName = source.info.displayName,
                            mediaSourceSpec = sourceSpec,
                            exception = exception,
                        )
                        handshakeHint?.let {
                            errors += "Handshake diagnostics for ${source.mediaSourceId}: ${it.summary}"
                        }
                        add(
                            buildJsonObject {
                                put("mediaSourceId", source.mediaSourceId)
                                put("factoryDisplayName", source.info.displayName)
                                put("error", "${exception::class.simpleName}: ${exception.message.orEmpty()}")
                                put("durationMillis", sourceFetchDuration)
                                handshakeHint?.let {
                                    put(
                                        "handshakeFailureDomainHint",
                                        json.encodeToJsonElement(HandshakeFailureDomainHint.serializer(), it),
                                    )
                                }
                            },
                        )
                    }
                }
            }

            stages += StageResult(
                name = "media_fetch",
                status = if (allMatches.isNotEmpty()) "success" else "failed",
                summary = if (allMatches.isNotEmpty()) {
                    "Fetched ${allMatches.size} candidate media entries"
                } else {
                    "No media candidates fetched"
                },
                details = buildJsonObject {
                    put("sources", perSourceJson)
                },
                errors = errors.toList(),
                durationMillis = System.currentTimeMillis() - fetchStart,
            )

            finishWithCandidates(
                input = encodeInput(input),
                stages = stages,
                errors = errors,
                matches = allMatches,
                sourceById = sourceById,
                request = metadata.request,
                probeTimeoutMillis = input.probeTimeoutMillis,
                candidateTestMode = input.candidateTestMode,
            )
        }
    }

    suspend fun testResourcePageUrl(input: TestResourcePageUrlInput): SourceTestResult {
        val start = System.currentTimeMillis()
        return testResourcePageUrlImpl(input)
            .let { it.copy(totalDurationMillis = System.currentTimeMillis() - start) }
    }

    private suspend fun testResourcePageUrlImpl(input: TestResourcePageUrlInput): SourceTestResult {
        val stages = mutableListOf<StageResult>()
        val errors = mutableListOf<String>()
        val source = input.mediaSource?.let { registry.createSource(it) }

        return useSources(source?.let(::listOf) ?: emptyList()) { createdSources ->
            val matcherProvider = createdSources.firstOrNull() as? WebVideoMatcherProvider
            val manualMedia = createManualWebMedia(
                pageUrl = input.pageUrl,
                mediaSourceId = createdSources.firstOrNull()?.mediaSourceId ?: "manual",
            )
            val resolveStart = System.currentTimeMillis()
            val resolveResult = resolver.resolvePage(
                media = manualMedia,
                pageUrl = input.pageUrl,
                matchers = matcherProvider?.let { listOf(it.matcher) }.orEmpty(),
            )
            stages += StageResult(
                name = "video_resolve",
                status = if (resolveResult.resolvedVideo != null) "success" else "failed",
                summary = if (resolveResult.resolvedVideo != null) "Resolved final video URL" else "Failed to resolve final video URL",
                details = resolveResult.diagnostics,
                errors = resolveResult.errors,
                durationMillis = System.currentTimeMillis() - resolveStart,
            )
            errors += resolveResult.errors

            val resolved = resolveResult.resolvedVideo ?: return@useSources SourceTestResult(
                ok = false,
                summary = "Failed to resolve video from page URL",
                input = encodeInput(input),
                stages = stages,
                errors = errors,
            )

            val probeStart = System.currentTimeMillis()
            val probeResult = withTimeout(input.probeTimeoutMillis) {
                probe.probe(resolved.url, resolved.headers)
            }
            stages += StageResult(
                name = "video_probe",
                status = if (probeResult.ok) "success" else "failed",
                summary = probeResult.summary,
                details = probeDetails(probeResult),
                errors = probeResult.errors,
                durationMillis = System.currentTimeMillis() - probeStart,
            )

            SourceTestResult(
                ok = probeResult.ok,
                summary = probeResult.summary,
                input = encodeInput(input),
                stages = stages,
                resolvedVideo = resolved,
                probe = probeResult,
                errors = errors + probeResult.errors,
            )
        }
    }

    private suspend fun fetchAniMetadata(input: TestSubjectEpisodeSourceInput): AniMetadataResult {
        val api = SubjectsAniApi(input.aniApiBaseUrl, httpClient).apply {
            input.aniBearerToken?.takeIf { it.isNotBlank() }?.let(::setBearerToken)
        }
        val subject = api.getSubject(input.subjectId).body()
        val episode = api.getEpisode(input.subjectId, input.episodeId).body()
        return AniMetadataResult(
            request = MediaFetchRequest(
                subjectId = subject.id.toString(),
                episodeId = episode.episodeId.toString(),
                subjectNameCN = subject.nameCn.ifBlank { subject.name }.ifBlank { null },
                subjectNames = subject.allKnownNames(),
                episodeSort = EpisodeSort(episode.sort),
                episodeName = episode.displayName(),
                episodeEp = episode.ep?.let(::EpisodeSort),
            ),
            subjectId = subject.id,
            subjectName = subject.name,
            subjectNameCn = subject.nameCn.ifBlank { null },
            episodeId = episode.episodeId,
            episodeSort = episode.sort,
            episodeEp = episode.ep,
        )
    }

    private suspend fun finishWithCandidates(
        input: JsonElement,
        stages: MutableList<StageResult>,
        errors: MutableList<String>,
        matches: List<MediaMatch>,
        sourceById: Map<String, MediaSource>,
        request: MediaFetchRequest,
        probeTimeoutMillis: Long,
        candidateTestMode: CandidateTestMode,
    ): SourceTestResult {
        val candidates = matches.distinctBy { it.media.mediaId }.map { it.toCandidateResult() }
        val playableCandidates = matches
            .filter { it.media.kind == MediaSourceKind.WEB }
            .sortedForRequest(request)

        stages += StageResult(
            name = "media_select",
            status = if (playableCandidates.isNotEmpty()) "success" else "failed",
            summary = if (playableCandidates.isNotEmpty()) {
                "Selected ${playableCandidates.size} web-playable candidate(s)"
            } else {
                "No web-playable candidate found"
            },
            details = buildJsonObject {
                put("candidateCount", candidates.size)
                put("webCandidateCount", playableCandidates.size)
                put("candidateTestMode", json.encodeToJsonElement(CandidateTestMode.serializer(), candidateTestMode))
                put("selectionOrder", buildJsonArray {
                    playableCandidates.forEach { add(JsonPrimitive(it.media.mediaId)) }
                })
            },
        )

        if (playableCandidates.isEmpty()) {
            return SourceTestResult(
                ok = false,
                summary = "No playable web candidate found",
                input = input,
                stages = stages,
                candidates = candidates,
                errors = errors,
            )
        }

        val channelResults = channelTestExecutor.execute(
            playableCandidates = playableCandidates,
            matchersFor = { match ->
                (sourceById[match.media.mediaSourceId] as? WebVideoMatcherProvider)
                    ?.let { listOf(it.matcher) }
                    .orEmpty()
            },
            probeTimeoutMillis = probeTimeoutMillis,
            candidateTestMode = candidateTestMode,
        )
        errors += channelResults.flatMap { it.errors }

        val resolvedChannels = channelResults.filter { it.resolvedVideo != null }
        val successfulChannels = channelResults.filter { it.ok }

        stages += StageResult(
            name = "video_resolve",
            status = if (resolvedChannels.isNotEmpty()) "success" else "failed",
            summary = if (resolvedChannels.isNotEmpty()) {
                "Resolved ${resolvedChannels.size}/${channelResults.size} channel(s) to final video URLs"
            } else {
                "Failed to resolve any channel to a final video URL"
            },
            details = buildJsonObject {
                put("testedChannelCount", channelResults.size)
                put("resolvedChannelCount", resolvedChannels.size)
                put("successfulChannelIds", buildJsonArray {
                    successfulChannels.forEach { add(JsonPrimitive(it.candidate.mediaId)) }
                })
            },
            errors = channelResults.filter { it.resolveStatus == "failed" }.flatMap { it.errors },
            durationMillis = channelResults.mapNotNull { it.resolveDurationMillis }.sum(),
        )

        if (resolvedChannels.isEmpty()) {
            stages += StageResult(
                name = "video_probe",
                status = "failed",
                summary = "No resolved channel available for playback probing",
                errors = errors.toList(),
            )
            return SourceTestResult(
                ok = false,
                summary = "Failed to resolve any channel to a final video URL",
                input = input,
                stages = stages,
                candidates = candidates,
                channelResults = channelResults,
                errors = errors,
            )
        }

        stages += StageResult(
            name = "video_probe",
            status = if (successfulChannels.isNotEmpty()) "success" else "failed",
            summary = when {
                successfulChannels.isNotEmpty() -> {
                    "Playable channels: ${successfulChannels.size}/${resolvedChannels.size} resolved channel(s)"
                }

                else -> "Resolved channels were found, but none passed playback probing"
            },
            details = buildJsonObject {
                put("testedChannelCount", channelResults.size)
                put("resolvedChannelCount", resolvedChannels.size)
                put("probedChannelCount", resolvedChannels.size)
                put("playableChannelCount", successfulChannels.size)
                put("playableChannelIds", buildJsonArray {
                    successfulChannels.forEach { add(JsonPrimitive(it.candidate.mediaId)) }
                })
            },
            errors = channelResults.filter { it.probeStatus == "failed" }.flatMap { it.errors },
            durationMillis = channelResults.mapNotNull { it.probeDurationMillis }.sum(),
        )

        val representativeResult = successfulChannels.firstOrNull()
            ?: resolvedChannels.firstOrNull()
            ?: channelResults.firstOrNull()

        return SourceTestResult(
            ok = successfulChannels.isNotEmpty(),
            summary = when {
                successfulChannels.isNotEmpty() -> "Playable channels: ${successfulChannels.size}/${resolvedChannels.size}"
                resolvedChannels.isNotEmpty() -> "Resolved ${resolvedChannels.size}/${channelResults.size} channels, but none passed playback probing"
                else -> "Failed to resolve any channel to a final video URL"
            },
            input = input,
            stages = stages,
            candidates = candidates,
            channelResults = channelResults,
            selectedCandidate = representativeResult?.candidate,
            resolvedVideo = representativeResult?.resolvedVideo,
            probe = representativeResult?.probe,
            errors = errors,
        )
    }

    private fun probeDetails(probeResult: VideoProbeResult): JsonElement {
        return buildJsonObject {
            put("kind", probeResult.kind)
            put("statusCode", probeResult.statusCode ?: -1)
            put("contentType", probeResult.contentType ?: "")
            put("finalUrl", probeResult.finalUrl ?: "")
            put("playlistEntries", probeResult.playlistEntries ?: -1)
            put("sampledSegmentUrl", probeResult.sampledSegmentUrl ?: "")
            put("sampledSegmentStatusCode", probeResult.sampledSegmentStatusCode ?: -1)
        }
    }

    private fun encodeInput(input: TestSubjectEpisodeSourceInput): JsonElement =
        json.encodeToJsonElement(TestSubjectEpisodeSourceInput.serializer(), input)

    private fun encodeInput(input: TestResourcePageUrlInput): JsonElement =
        json.encodeToJsonElement(TestResourcePageUrlInput.serializer(), input)

    private suspend fun <T> useSources(sources: List<MediaSource>, block: suspend (List<MediaSource>) -> T): T {
        return try {
            block(sources)
        } finally {
            sources.forEach { source ->
                runCatching { source.close() }
            }
        }
    }

    private fun createManualWebMedia(
        pageUrl: String,
        mediaSourceId: String,
    ): DefaultMedia {
        return DefaultMedia(
            mediaId = "$mediaSourceId.manual",
            mediaSourceId = mediaSourceId,
            originalUrl = pageUrl,
            download = ResourceLocation.WebVideo(pageUrl),
            originalTitle = pageUrl,
            publishedTime = 0L,
            properties = MediaProperties(
                subjectName = pageUrl,
                episodeName = pageUrl,
                subtitleLanguageIds = listOf("CHS"),
                resolution = "1080P",
                alliance = mediaSourceId,
                size = FileSize.Unspecified,
                subtitleKind = SubtitleKind.EMBEDDED,
            ),
            episodeRange = null,
            location = me.him188.ani.datasources.api.source.MediaSourceLocation.Online,
            kind = MediaSourceKind.WEB,
        )
    }
}

private data class AniMetadataResult(
    val request: MediaFetchRequest,
    val subjectId: Long,
    val subjectName: String,
    val subjectNameCn: String?,
    val episodeId: Long,
    val episodeSort: String,
    val episodeEp: String?,
)

private fun AniSubjectCollection.allKnownNames(): List<String> {
    return buildList {
        add(nameCn)
        add(name)
        addAll(aliases)
    }.map(String::trim)
        .filter(String::isNotBlank)
        .distinct()
}

private fun AniEpisodeCollection.displayName(): String {
    return nameCn.ifBlank { name }.ifBlank { description }
}
