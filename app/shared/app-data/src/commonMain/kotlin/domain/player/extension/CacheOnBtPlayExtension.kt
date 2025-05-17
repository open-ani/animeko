/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.player.extension

import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import me.him188.ani.app.domain.episode.EpisodeSession
import me.him188.ani.app.domain.media.cache.MediaCache
import me.him188.ani.app.domain.media.cache.MediaCacheManager
import me.him188.ani.app.domain.media.cache.engine.DummyMediaCacheEngine
import me.him188.ani.app.domain.media.resolver.toEpisodeMetadata
import me.him188.ani.datasources.api.MediaCacheMetadata
import me.him188.ani.datasources.api.MetadataKey
import me.him188.ani.datasources.api.source.MediaSourceKind
import me.him188.ani.utils.logging.info
import me.him188.ani.utils.logging.logger
import org.koin.core.Koin

/**
 * Automatically create a cache task when playing BT media.
 */
class CacheOnBtPlayExtension(
    private val context: PlayerExtensionContext,
    koin: Koin,
) : PlayerExtension("CacheOnBtPlay") {
    private val mediaCacheManager: MediaCacheManager by koin.inject()

    private var currentCache: MediaCache? = null

    override fun onStart(episodeSession: EpisodeSession, backgroundTaskScope: ExtensionBackgroundTaskScope) {
        backgroundTaskScope.launch("CacheOnBtPlay") {
            context.sessionFlow.collectLatest { session ->
                val episodeMetadata = session.infoBundleFlow.filterNotNull().first().episodeInfo.toEpisodeMetadata()
                session.fetchSelectFlow.collectLatest { bundle ->
                    if (bundle == null) return@collectLatest
                    bundle.mediaSelector.selected.filterNotNull().collectLatest { media ->
                        cleanup()
                        if (media.kind == MediaSourceKind.BitTorrent) {
                            logger.info {
                                "CacheOnBtPlay: "
                            }

                            val storage = mediaCacheManager.enabledStorages.first()
                                .find { it.engine.engineKey != DummyMediaCacheEngine.engineKey } ?: return@collectLatest
                            val metadata = MediaCacheMetadata(bundle.mediaFetchSession.request.first())
                                .withExtra(mapOf(EXTRA_AUTO_CACHE to "true"))
                            val cache = storage.cache(media, metadata, episodeMetadata, resume = true)
                            if (cache.metadata.extra[EXTRA_AUTO_CACHE] == "true") {
                                currentCache = cache
                            }
                        }
                    }
                }
            }
        }
    }

    override suspend fun onBeforeSwitchEpisode(newEpisodeId: Int) {
        cleanup()
    }

    override suspend fun onClose() {
        cleanup()
    }

    private suspend fun cleanup() {
        val cache = currentCache ?: return
        val progress = cache.fileStats.first().downloadedBytes.inBytes
        if (progress == 0L) {
            logger.info {
                "CacheOnBtPlay: Cache ${cache.metadata} progress is zero, deleting it"
            }
            mediaCacheManager.deleteCache(cache)
        }
        currentCache = null
    }

    companion object : EpisodePlayerExtensionFactory<CacheOnBtPlayExtension> {
        private val logger = logger<CacheOnBtPlayExtension>()
        val EXTRA_AUTO_CACHE = MetadataKey("autoCache")
        override fun create(context: PlayerExtensionContext, koin: Koin): CacheOnBtPlayExtension {
            return CacheOnBtPlayExtension(context, koin)
        }
    }
}
