/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.media.selector

import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.append
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import me.him188.ani.app.domain.foundation.HttpClientProvider
import me.him188.ani.app.domain.foundation.ScopedHttpClientUserAgent
import me.him188.ani.app.domain.foundation.get
import me.him188.ani.app.domain.media.fetch.MediaFetchSession
import me.him188.ani.app.domain.media.fetch.MediaSourceFetchResult
import me.him188.ani.app.domain.media.fetch.MediaSourceFetchState
import me.him188.ani.app.domain.media.fetch.isFinal
import me.him188.ani.app.domain.media.resolver.EpisodeMetadata
import me.him188.ani.app.domain.media.resolver.MediaResolver
import me.him188.ani.app.domain.usecase.GlobalKoin
import me.him188.ani.app.domain.usecase.UseCase
import me.him188.ani.datasources.api.Media
import me.him188.ani.datasources.api.source.MediaSourceKind
import me.him188.ani.utils.httpdownloader.m3u.DefaultM3u8Parser
import me.him188.ani.utils.httpdownloader.m3u.M3u8Playlist
import me.him188.ani.utils.logging.info
import me.him188.ani.utils.logging.logger
import org.koin.core.Koin
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.openani.mediamp.source.MediaData
import org.openani.mediamp.source.UriMediaData
import kotlin.time.Duration
import kotlin.time.Duration.Companion.ZERO
import kotlin.time.TimeSource

fun interface SelectFastestPlayableWebMediaUseCase : UseCase {
    suspend operator fun invoke(
        session: MediaFetchSession,
        mediaSelector: MediaSelector,
        episodeMetadata: EpisodeMetadata,
        preferredWebMediaSourceId: String?,
        sourceTiers: MediaSelectorSourceTiers,
        blacklistMediaIds: Set<String>,
        sourceDiscoveryTimeout: Duration,
        mediaResolveTimeout: Duration,
        maxProbeSources: Int,
    ): String?
}

class SelectFastestPlayableWebMediaUseCaseImpl(
    private val koin: Koin = GlobalKoin,
    private val measureOpenedMediaSample: (suspend CoroutineScope.(openedMedia: MediaData, timeout: Duration) -> Duration)? = null,
) : SelectFastestPlayableWebMediaUseCase, KoinComponent {
    private val mediaResolver: MediaResolver by inject()
    private val httpClientProvider: HttpClientProvider by inject()

    override suspend fun invoke(
        session: MediaFetchSession,
        mediaSelector: MediaSelector,
        episodeMetadata: EpisodeMetadata,
        preferredWebMediaSourceId: String?,
        sourceTiers: MediaSelectorSourceTiers,
        blacklistMediaIds: Set<String>,
        sourceDiscoveryTimeout: Duration,
        mediaResolveTimeout: Duration,
        maxProbeSources: Int,
    ): String? = coroutineScope {
        if (maxProbeSources <= 0) return@coroutineScope null

        val orderedSources = session.mediaSourceResults
            .asSequence()
            .filter { it.kind == MediaSourceKind.WEB }
            .sortedWith(
                compareBy<MediaSourceFetchResult>(
                    { if (it.mediaSourceId == preferredWebMediaSourceId) 0 else 1 },
                    { sourceTiers[it.mediaSourceId].value.toInt() },
                    { session.mediaSourceResults.indexOf(it) },
                ),
            )
            .distinctBy { it.mediaSourceId }
            .toList()

        if (orderedSources.isEmpty()) return@coroutineScope null

        val targetProbeSources = maxProbeSources.coerceAtMost(orderedSources.size)

        logger.info {
            "Probing up to $targetProbeSources web sources for fastest playable media. " +
                    "preferred=$preferredWebMediaSourceId, discoveryTimeout=${sourceDiscoveryTimeout.inWholeMilliseconds}ms, " +
                    "resolveTimeout=${mediaResolveTimeout.inWholeMilliseconds}ms"
        }

        val sourceOrder = orderedSources.map { it.mediaSourceId }
        val candidateSourceIds = sourceOrder.toSet()
        val sourceStatesFlow = combine(orderedSources.map { it.state }) { it.toList() }
        fun snapshotStatus(media: List<Media>, states: List<MediaSourceFetchState>) = DiscoveryStatus(
            availableMedia = media.filter { it.mediaSourceId in candidateSourceIds },
            states = states,
            sourceOrder = sourceOrder,
        )

        val initialDiscovery = combine(
            mediaSelector.filteredCandidatesMedia,
            sourceStatesFlow,
        ) { media, states ->
            snapshotStatus(media, states)
        }.first { it.availableSourceCount > 0 || it.allSettled() }

        val discoveryStatus = if (initialDiscovery.availableSourceCount == 0) {
            combine(
                mediaSelector.filteredCandidatesMedia,
                sourceStatesFlow,
            ) { media, states ->
                snapshotStatus(media, states)
            }.first()
        } else {
            withTimeoutOrNull(sourceDiscoveryTimeout) {
                combine(
                    mediaSelector.filteredCandidatesMedia,
                    sourceStatesFlow,
                ) { media, states ->
                    snapshotStatus(media, states)
                }.first { it.availableSourceCount >= targetProbeSources || it.allSettled() }
            } ?: snapshotStatus(
                mediaSelector.filteredCandidatesMedia.first(),
                orderedSources.map { it.state.value },
            )
        }

        if (discoveryStatus.availableSourceCount == 0) return@coroutineScope null

        val probeCandidates = discoveryStatus.availableSourceIds.mapNotNull { sourceId ->
            mediaSelector.tryFindFromMediaSources(
                candidateSources = listOf(sourceId),
                blacklistMediaIds = blacklistMediaIds,
                allowNonPreferred = true,
            )?.let { ProbeCandidate(sourceId, it) }
        }

        if (probeCandidates.isEmpty()) return@coroutineScope null

        val workerCount = targetProbeSources.coerceAtMost(probeCandidates.size)
        val candidateChannel = Channel<ProbeCandidate>(capacity = probeCandidates.size)
        val successChannel = Channel<String>(capacity = 1)

        try {
            probeCandidates.forEach { candidate ->
                candidateChannel.trySend(candidate)
            }
            candidateChannel.close()

            repeat(workerCount) {
                launch {
                    for (candidate in candidateChannel) {
                        val resolvedSourceId = runCatching {
                            withTimeoutOrNull(mediaResolveTimeout) {
                                val probeStartedAt = TimeSource.Monotonic.markNow()
                                val provider = mediaResolver.resolve(candidate.media, episodeMetadata)
                                val openedMedia = provider.open(this)
                                try {
                                    val timeoutLeft = mediaResolveTimeout - probeStartedAt.elapsedNow()
                                    sampleOpenedMedia(
                                        openedMedia = openedMedia,
                                        timeout = timeoutLeft,
                                    )
                                    probeStartedAt.elapsedNow()
                                } finally {
                                    (openedMedia as? AutoCloseable)?.close()
                                }
                            }?.let {
                                candidate.sourceId
                            }
                        }.getOrNull()

                        if (resolvedSourceId != null) {
                            logger.info { "Web source probe succeeded first-ready: $resolvedSourceId" }
                            successChannel.trySend(resolvedSourceId)
                            break
                        }
                    }
                }
            }

            withTimeoutOrNull(mediaResolveTimeout) {
                successChannel.receiveCatching().getOrNull()
            }
        } finally {
            currentCoroutineContext().cancelChildren()
            candidateChannel.close()
            successChannel.close()
        }
    }

    override fun getKoin(): Koin = koin

    private suspend fun CoroutineScope.sampleOpenedMedia(
        openedMedia: MediaData,
        timeout: Duration,
    ): Duration {
        if (timeout <= ZERO) {
            throw IllegalStateException("No timeout left for web media sampling")
        }
        return measureOpenedMediaSample?.invoke(this, openedMedia, timeout)
            ?: defaultSampleOpenedMedia(openedMedia, timeout)
    }

    private suspend fun CoroutineScope.defaultSampleOpenedMedia(
        openedMedia: MediaData,
        timeout: Duration,
    ): Duration {
        if (openedMedia !is UriMediaData) {
            return ZERO
        }

        return withTimeoutOrNull(timeout) {
            val request = resolveProbeRequest(openedMedia) ?: return@withTimeoutOrNull ZERO
            val startedAt = TimeSource.Monotonic.markNow()
            httpClientProvider.get(userAgent = ScopedHttpClientUserAgent.BROWSER).use {
                prepareGet(request.url) {
                    request.headers.forEach { (key, value) ->
                        headers.append(key, value)
                    }
                    request.rangeHeader?.let {
                        headers.append(HttpHeaders.Range, it)
                    }
                }.execute { response ->
                    check(response.status.value in 200..299) {
                        "Unexpected web probe status=${response.status.value} for ${request.url}"
                    }

                    val channel = response.bodyAsChannel()
                    val buffer = ByteArray(DEFAULT_WEB_PROBE_SAMPLE_BUFFER_BYTES)
                    var remainingBytes = DEFAULT_WEB_PROBE_SAMPLE_BYTES
                    while (remainingBytes > 0) {
                        val bytesRead = channel.readAvailable(
                            buffer,
                            0,
                            minOf(buffer.size, remainingBytes),
                        )
                        if (bytesRead == -1) {
                            break
                        }
                        remainingBytes -= bytesRead
                    }
                }
            }
            startedAt.elapsedNow()
        } ?: throw IllegalStateException("Timed out while sampling opened web media")
    }

    private suspend fun resolveProbeRequest(mediaData: UriMediaData): ProbeRequest? {
        val directRequest = ProbeRequest(
            url = mediaData.uri,
            headers = mediaData.headers,
            rangeHeader = createRangeHeader(DEFAULT_WEB_PROBE_SAMPLE_BYTES),
        )

        if (!mediaData.uri.isLikelyM3u8Playlist()) {
            return directRequest
        }

        return resolveM3u8ProbeRequest(
            url = mediaData.uri,
            headers = mediaData.headers,
            remainingDepth = DEFAULT_WEB_PROBE_PLAYLIST_DEPTH,
        ) ?: directRequest.copy(rangeHeader = null)
    }

    private suspend fun resolveM3u8ProbeRequest(
        url: String,
        headers: Map<String, String>,
        remainingDepth: Int,
    ): ProbeRequest? {
        if (remainingDepth <= 0) return null

        val playlistText = httpClientProvider.get(userAgent = ScopedHttpClientUserAgent.BROWSER).use {
            prepareGet(url) {
                headers.forEach { (key, value) ->
                    this.headers.append(key, value)
                }
            }.execute { response ->
                if (response.status.value !in 200..299) {
                    return@execute null
                }
                response.bodyAsText()
            }
        } ?: return null

        return when (val playlist = runCatching { DefaultM3u8Parser.parse(playlistText, url) }.getOrNull()) {
            is M3u8Playlist.MediaPlaylist -> {
                val segment = playlist.segments.firstOrNull() ?: return null
                ProbeRequest(
                    url = segment.uri,
                    headers = headers,
                    rangeHeader = segment.byteRange?.toProbeRangeHeader()
                        ?: createRangeHeader(DEFAULT_WEB_PROBE_SAMPLE_BYTES),
                )
            }

            is M3u8Playlist.MasterPlaylist -> {
                val variant = playlist.variants.firstOrNull() ?: return null
                resolveM3u8ProbeRequest(
                    url = variant.uri,
                    headers = headers,
                    remainingDepth = remainingDepth - 1,
                )
            }

            null -> null
        }
    }

    private companion object {
        private val logger = logger<SelectFastestPlayableWebMediaUseCase>()
    }

    private data class ProbeCandidate(
        val sourceId: String,
        val media: Media,
    )

    private data class ProbeRequest(
        val url: String,
        val headers: Map<String, String>,
        val rangeHeader: String?,
    )

    private data class DiscoveryStatus(
        val availableMedia: List<Media>,
        val states: List<MediaSourceFetchState>,
        val sourceOrder: List<String>,
    ) {
        val availableSourceCount: Int
            get() = availableSourceIds.size

        val availableSourceIds: List<String>
            get() = availableMedia
                .map { it.mediaSourceId }
                .distinct()
                .sortedBy(sourceOrder::indexOf)

        fun allSettled(): Boolean = states.all { it.isFinal }
    }
}

private const val DEFAULT_WEB_PROBE_SAMPLE_BYTES = 256 * 1024
private const val DEFAULT_WEB_PROBE_SAMPLE_BUFFER_BYTES = 8 * 1024
private const val DEFAULT_WEB_PROBE_PLAYLIST_DEPTH = 2

private fun String.isLikelyM3u8Playlist(): Boolean {
    return lowercase().contains(".m3u8")
}

private fun createRangeHeader(length: Int): String = "bytes=0-${length - 1}"

private fun me.him188.ani.utils.httpdownloader.m3u.ByteRange.toProbeRangeHeader(): String {
    val start = offset ?: 0L
    val end = start + minOf(length, DEFAULT_WEB_PROBE_SAMPLE_BYTES.toLong()) - 1
    return "bytes=$start-$end"
}
