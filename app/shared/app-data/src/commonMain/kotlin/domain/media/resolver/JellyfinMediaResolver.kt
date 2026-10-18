/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.media.resolver

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import me.him188.ani.app.data.repository.player.JellyfinPlaybackQualityRepository
import me.him188.ani.app.domain.media.fetch.MediaSourceManager
import me.him188.ani.app.domain.media.player.data.MediaDataProvider
import me.him188.ani.app.domain.mediasource.instance.MediaSourceInstance
import me.him188.ani.datasources.api.Media
import me.him188.ani.datasources.api.source.FactoryId
import me.him188.ani.datasources.api.topic.ResourceLocation
import me.him188.ani.datasources.jellyfin.JellyfinMediaSource
import me.him188.ani.datasources.jellyfin.JellyfinPlaybackPlan
import me.him188.ani.datasources.jellyfin.JellyfinPlaybackQuality
import me.him188.ani.datasources.jellyfin.JellyfinPlaybackUnavailableException
import me.him188.ani.datasources.jellyfin.jellyfinPlaybackQualities
import me.him188.ani.utils.logging.logger
import me.him188.ani.utils.logging.warn
import org.openani.mediamp.source.MediaExtraFiles
import org.openani.mediamp.source.UriMediaData

class JellyfinMediaResolver(
    private val mediaSourceManager: MediaSourceManager,
    private val qualityRepository: JellyfinPlaybackQualityRepository,
    private val fallback: HttpStreamingMediaResolver = HttpStreamingMediaResolver(),
) : MediaResolver {
    override fun supports(media: Media): Boolean {
        return isJellyfinMedia(media)
    }

    override suspend fun resolve(media: Media, episode: EpisodeMetadata): MediaDataProvider<*> {
        if (!supports(media)) throw UnsupportedMediaException(media)

        val source = findJellyfinSource(
            media = media,
            instances = mediaSourceManager.allInstances.first(),
        ) ?: return fallback.resolve(media, episode)

        return JellyfinMediaDataProvider(
            source = source,
            itemId = media.mediaId,
            extraFiles = media.extraFiles.toMediampMediaExtraFiles(),
            qualityRepository = qualityRepository,
        )
    }
}

internal fun isJellyfinMedia(media: Media): Boolean {
    val download = media.download as? ResourceLocation.HttpStreamingFile ?: return false
    val itemPath = "/Items/${media.mediaId}"
    return media.originalUrl.substringBefore('?').endsWith(itemPath) &&
            download.uri.substringBefore('?').endsWith("$itemPath/Download")
}

internal fun findJellyfinSource(
    media: Media,
    instances: List<MediaSourceInstance>,
): JellyfinMediaSource? {
    val jellyfinInstances = instances.filter {
        it.factoryId == FactoryId(JellyfinMediaSource.ID) && it.source is JellyfinMediaSource
    }
    val exact = jellyfinInstances.firstOrNull {
        it.mediaSourceId == media.mediaSourceId
    }
    if (exact != null) {
        return exact.source as JellyfinMediaSource
    }

    // Media queried by an older build used the factory id instead of the unique instance id.
    if (media.mediaSourceId == JellyfinMediaSource.ID && jellyfinInstances.size == 1) {
        return jellyfinInstances.single().source as JellyfinMediaSource
    }
    return null
}

data class JellyfinPlaybackQualityState(
    val selected: JellyfinPlaybackQuality,
    val options: List<JellyfinPlaybackQuality>,
    val effectiveMaxBitrate: Int?,
    val sourceBitrate: Int?,
    val isSwitching: Boolean = false,
)

internal data class OpenedJellyfinPlayback(
    val data: UriMediaData,
    val plan: JellyfinPlaybackPlan,
    val state: JellyfinPlaybackQualityState,
)

/**
 * Stateless description of one Jellyfin item. Active plans returned by [openInitialPlayback] or
 * [openPlayback] are owned by `JellyfinPlaybackController`, not by this provider.
 */
class JellyfinMediaDataProvider internal constructor(
    private val source: JellyfinMediaSource,
    private val itemId: String,
    override val extraFiles: MediaExtraFiles,
    private val qualityRepository: JellyfinPlaybackQualityRepository,
) : MediaDataProvider<UriMediaData> {
    override suspend fun open(scopeForCleanup: CoroutineScope): UriMediaData {
        return openInitialPlayback().data
    }

    internal suspend fun openInitialPlayback(): OpenedJellyfinPlayback {
        val quality = qualityRepository.get(source.mediaSourceId)
        return try {
            openPlayback(quality)
        } catch (e: JellyfinPlaybackUnavailableException) {
            if (quality == JellyfinPlaybackQuality.Original) throw e
            logger.warn(e) {
                "Stored Jellyfin playback quality is unavailable; falling back to Original"
            }
            openPlayback(JellyfinPlaybackQuality.Original).also {
                rememberQuality(it.plan.quality)
            }
        }
    }

    internal suspend fun openPlayback(
        quality: JellyfinPlaybackQuality,
        requestedMediaSourceId: String? = null,
        startPositionMillis: Long = 0,
        forceAutoDetection: Boolean = false,
    ): OpenedJellyfinPlayback {
        val plan = source.createPlaybackPlan(
            itemId = itemId,
            quality = quality,
            mediaSourceId = requestedMediaSourceId,
            startPositionMillis = startPositionMillis,
            forceAutoDetection = forceAutoDetection,
        )
        return OpenedJellyfinPlayback(
            data = UriMediaData(plan.uri, emptyMap(), extraFiles),
            plan = plan,
            state = JellyfinPlaybackQualityState(
                selected = quality,
                options = jellyfinPlaybackQualities(plan.sourceBitrate),
                effectiveMaxBitrate = plan.effectiveMaxBitrate,
                sourceBitrate = plan.sourceBitrate,
            ),
        )
    }

    internal suspend fun rememberQuality(quality: JellyfinPlaybackQuality) {
        withContext(NonCancellable) {
            try {
                qualityRepository.set(source.mediaSourceId, quality)
            } catch (e: Throwable) {
                logger.warn(e) { "Failed to remember Jellyfin playback quality" }
            }
        }
    }

    internal suspend fun stopEncoding(plan: JellyfinPlaybackPlan?) {
        if (plan?.isTranscoding != true) return
        val playSessionId = plan.playSessionId ?: return
        try {
            source.stopActiveEncoding(playSessionId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            logger.warn(e) { "Failed to stop a previous Jellyfin transcoding session" }
        }
    }

    private companion object {
        val logger = logger<JellyfinMediaDataProvider>()
    }
}
