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
import me.him188.ani.app.domain.media.cache.DeleteCacheUseCase
import me.him188.ani.app.domain.media.cache.MediaCache
import me.him188.ani.app.domain.media.cache.MediaCacheManager
import me.him188.ani.app.domain.media.cache.engine.MediaCacheEngineKey
import me.him188.ani.app.domain.media.resolver.toEpisodeMetadata
import me.him188.ani.app.domain.player.VideoLoadingState
import me.him188.ani.datasources.api.CachedMedia
import me.him188.ani.datasources.api.MediaCacheMetadata
import me.him188.ani.utils.logging.info
import me.him188.ani.utils.logging.logger
import me.him188.ani.utils.logging.warn
import org.koin.core.Koin

/**
 * Automatically create a cache task when playback is handed to the local
 * BitTorrent engine.
 *
 * The gate uses the post-resolve [VideoLoadingState.Succeed.isBt] flag rather
 * than the pre-resolve [me.him188.ani.datasources.api.source.MediaSourceKind],
 * so a cloud offline backend (e.g. PikPak) that intercepts BT magnets and
 * returns a plain HTTPS URL does not trigger a redundant anitorrent download
 * in the background — and a runtime fallback from such a backend back to
 * anitorrent is still caught.
 */
class CacheOnBtPlayExtension(
    private val context: PlayerExtensionContext,
    koin: Koin,
) : PlayerExtension("CacheOnBtPlay") {
    private val mediaCacheManager: MediaCacheManager by koin.inject()
    private val deleteCacheUseCase: DeleteCacheUseCase by koin.inject()

    private var currentCache: MediaCache? = null

    override fun onStart(episodeSession: EpisodeSession, backgroundTaskScope: ExtensionBackgroundTaskScope) {
        backgroundTaskScope.launch("CacheOnBtPlay") {
            context.sessionFlow.collectLatest { session ->
                val episodeMetadata = session.infoBundleFlow.filterNotNull().first().episodeInfo.toEpisodeMetadata()

                session.fetchSelectFlow.collectLatest fsf@{ bundle ->
                    if (bundle == null) return@fsf

                    context.videoLoadingStateFlow.collectLatest { state ->
                        deleteCurrentAutoSelectedIfNotStarted()

                        if (state !is VideoLoadingState.Succeed || !state.isBt) return@collectLatest

                        val storage = mediaCacheManager.storagesIncludingDisabled
                            .find { it.engine.engineKey == MediaCacheEngineKey.Anitorrent }
                        if (storage == null) {
                            logger.warn { "TorrentMediaCacheEngine is not found in MediaCachedManager." }
                            return@collectLatest
                        }

                        val media = bundle.mediaSelector.selected.filterNotNull().first()
                        if (media is CachedMedia) {
                            // 选中了正在下载中的 BT 源.
                            return@collectLatest
                        }
                        logger.info { "Auto cache BitTorrent media on play: $media" }

                        val metadata =
                            MediaCacheMetadata(bundle.mediaFetchSession.request.first(), autoCached = true)
                        val cache = storage.cache(media, metadata, episodeMetadata, resume = true)
                        if (cache.metadata.autoCached) {
                            currentCache = cache
                        }
                    }
                }
            }
        }
    }

    override suspend fun onBeforeSwitchEpisode(newEpisodeId: Int) {
        deleteCurrentAutoSelectedIfNotStarted()
    }

    override suspend fun onClose() {
        deleteCurrentAutoSelectedIfNotStarted()
    }

    private suspend fun deleteCurrentAutoSelectedIfNotStarted() {
        val cache = currentCache ?: return
        val progress = cache.fileStats.first().downloadedBytes.inBytes
        if (progress == 0L) {
            logger.info { "Auto-cached media ${cache.metadata} hasn't started downloading, deleting it." }
            deleteCacheUseCase(cache)
        }
        currentCache = null
    }

    companion object : EpisodePlayerExtensionFactory<CacheOnBtPlayExtension> {
        private val logger = logger<CacheOnBtPlayExtension>()
        override fun create(context: PlayerExtensionContext, koin: Koin): CacheOnBtPlayExtension {
            return CacheOnBtPlayExtension(context, koin)
        }
    }
}
