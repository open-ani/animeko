/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.media.selector.trace

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import me.him188.ani.app.data.models.preference.MediaPreference
import me.him188.ani.app.data.repository.user.SettingsRepository
import me.him188.ani.app.domain.episode.CreateMediaFetchSelectBundleFlowUseCase
import me.him188.ani.app.domain.episode.MediaFetchSelectBundle
import me.him188.ani.app.domain.episode.SubjectEpisodeInfoBundle
import me.him188.ani.app.domain.media.fetch.MediaSourceFetchState
import me.him188.ani.app.domain.media.fetch.isFinal
import me.him188.ani.app.domain.media.selector.MediaAutoSelectSnapshot
import me.him188.ani.app.domain.media.selector.MediaSourceSelectionSnapshot
import me.him188.ani.app.domain.mediasource.GetPreferredWebMediaSourceUseCase
import me.him188.ani.app.platform.currentAniBuildConfig
import me.him188.ani.datasources.api.DefaultMedia
import me.him188.ani.datasources.api.Media
import me.him188.ani.datasources.api.MediaExtraFiles
import me.him188.ani.datasources.api.topic.ResourceLocation
import me.him188.ani.utils.logging.info
import me.him188.ani.utils.logging.logger
import me.him188.ani.utils.logging.warn
import org.koin.core.Koin
import java.io.File
import java.security.MessageDigest
import java.time.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/** Opt-in desktop diagnostic. Observation is tied to the real playback bundle's subscription. */
class MediaSelectionTraceRecorder(
    private val directory: File,
    private val koin: Koin,
    private val duration: Duration = 45.seconds,
    private val searchCacheCleared: Boolean = false,
) {
    fun decorate(delegate: CreateMediaFetchSelectBundleFlowUseCase): CreateMediaFetchSelectBundleFlowUseCase =
        object : CreateMediaFetchSelectBundleFlowUseCase {
            override fun invoke(subjectEpisodeInfoBundleFlow: Flow<SubjectEpisodeInfoBundle?>): Flow<MediaFetchSelectBundle?> =
                delegate(subjectEpisodeInfoBundleFlow).transformLatest { bundle ->
                    coroutineScope {
                        if (bundle != null) launch(start = CoroutineStart.UNDISPATCHED) {
                            try {
                                record(bundle)
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                logger.warn {
                                    "Media selection capture ended: ${e::class.simpleName}\n" +
                                            e.stackTrace.take(20).joinToString("\n")
                                }
                            }
                        }
                        emit(bundle)
                        awaitCancellation()
                    }
                }
        }

    private data class Observation(
        val elapsedMillis: Long,
        val snapshot: MediaAutoSelectSnapshot,
        val defaultPreference: MediaPreference,
        val selected: Media?,
    )

    private suspend fun record(bundle: MediaFetchSelectBundle) = coroutineScope {
        val started = TimeSource.Monotonic.markNow()
        val capturedAt = Instant.now()
        val session = bundle.mediaFetchSession
        val request = session.request.first()
        val preferred = async {
            request.subjectId.toIntOrNull()?.let { koin.get<GetPreferredWebMediaSourceUseCase>().invoke(it).first() }
        }
        val observations = mutableListOf<Observation>()
        val sourceIds = session.mediaSourceResults.map { it.mediaSourceId }
        val sources = if (session.mediaSourceResults.isEmpty()) flowOf(emptyList()) else combine(
            session.mediaSourceResults.map { source ->
                combine(source.state, source.results) { state, results ->
                    val currentResults = if (state.isFinal) source.results.first() else results
                    MediaSourceSelectionSnapshot(
                        source.mediaSourceId, source.kind,
                        if (source.state.value == state) state else MediaSourceFetchState.Working,
                        currentResults,
                    )
                }
            },
        ) { it.toList() }
        logger.info { "Media selection capture started: subject=${request.subjectId}, episode=${request.episodeId}" }
        try {
            withTimeoutOrNull(duration) {
                combine(
                    bundle.mediaSelector.autoSelectSnapshots(sources),
                    koin.get<SettingsRepository>().defaultMediaPreference.flow,
                    bundle.mediaSelector.selected,
                ) { snapshot, defaultPreference, selected ->
                    Observation(started.elapsedNow().inWholeMilliseconds, snapshot, defaultPreference, selected)
                }.collect { observations += it }
            }
        } finally {
            val durationMillis = started.elapsedNow().inWholeMilliseconds
            // Snapshot references are immutable. Encoding and file I/O happen after capture, off the playback path.
            withContext(NonCancellable + Dispatchers.IO) {
                if (observations.isNotEmpty()) {
                    val trace = MediaSelectionTrace(
                        capturedAt = capturedAt.toString(),
                        applicationVersion = currentAniBuildConfig.versionName,
                        durationMillis = durationMillis,
                        searchCacheCleared = searchCacheCleared,
                        request = request,
                        preferredSourceId = runCatching { preferred.await() }.getOrNull(),
                        sources = session.mediaSourceResults.map {
                            MediaSelectionTrace.Source(it.mediaSourceId, it.sourceInfo.displayName, it.kind)
                        },
                        frames = observations.map { observation ->
                            val snapshot = observation.snapshot
                            MediaSelectionTrace.Frame(
                                observation.elapsedMillis,
                                snapshot.sources.map { source ->
                                    MediaSelectionTrace.SourceUpdate(
                                        source.mediaSourceId, source.state::class.simpleName!!,
                                        (source.state as? MediaSourceFetchState.Completed)?.id,
                                        source.results.map { sanitize(it) },
                                    )
                                },
                                MediaSelectionTrace.Context.capture(snapshot.context, sourceIds),
                                snapshot.settings, snapshot.preference, observation.defaultPreference,
                                observation.selected?.let { mediaId(it) },
                            )
                        },
                    )
                    directory.mkdirs()
                    val file = File(directory, "${request.subjectId}-${request.episodeId}-${capturedAt.toEpochMilli()}.json")
                    file.writeText(MediaSelectionTrace.json.encodeToString(trace))
                    logger.info { "Media selection capture saved: ${file.name}, frames=${observations.size}" }
                }
            }
        }
    }

    private fun mediaId(media: Media): String = "media-" + MessageDigest.getInstance("SHA-256")
        .digest(media.mediaId.toByteArray()).take(12).joinToString("") { "%02x".format(it) }

    private fun sanitize(media: Media): DefaultMedia {
        val id = mediaId(media)
        return DefaultMedia(
            id, media.mediaSourceId, "https://fixture.invalid/$id",
            ResourceLocation.HttpStreamingFile("https://fixture.invalid/$id"),
            media.originalTitle, media.publishedTime, media.properties, media.episodeRange,
            MediaExtraFiles(media.extraFiles.subtitles.map { it.copy(uri = "https://fixture.invalid/subtitle") }),
            media.location, media.kind,
        )
    }

    private companion object {
        val logger = logger<MediaSelectionTraceRecorder>()
    }
}
