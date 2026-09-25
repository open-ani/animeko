/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
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
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import me.him188.ani.app.domain.episode.EpisodeSession
import me.him188.ani.app.domain.media.cache.DeleteCacheUseCase
import me.him188.ani.app.domain.media.cache.MediaCache
import me.him188.ani.app.domain.media.download.MediaDownloadManager
import me.him188.ani.app.domain.media.download.selectTorrentStorage
import me.him188.ani.app.domain.media.fetch.create
import me.him188.ani.app.domain.media.resolver.toEpisodeMetadata
import me.him188.ani.app.domain.player.VideoLoadingState
import me.him188.ani.datasources.api.CachedMedia
import me.him188.ani.datasources.api.MediaCacheMetadata
import me.him188.ani.datasources.api.source.MediaFetchRequest
import me.him188.ani.utils.logging.info
import me.him188.ani.utils.logging.logger
import me.him188.ani.utils.logging.warn
import org.koin.core.Koin

/**
 * Automatically create a cache task when playback is handed to a torrent engine.
 *
 * The gate uses the post-resolve [VideoLoadingState.Succeed.engineKey] rather than the pre-resolve
 * [me.him188.ani.datasources.api.source.MediaSourceKind]: opening may fall back from the cloud to
 * anitorrent, and the record must be created for the engine that actually serves playback.
 */
class CacheOnBtPlayExtension(
    private val context: PlayerExtensionContext,
    koin: Koin,
) : PlayerExtension("CacheOnBtPlay") {
    private val downloadManager: MediaDownloadManager by koin.inject()
    private val deleteCacheUseCase: DeleteCacheUseCase by koin.inject()

    private val autoCaches = mutableSetOf<MediaCache>()

    override fun onStart(episodeSession: EpisodeSession, backgroundTaskScope: ExtensionBackgroundTaskScope) {
        backgroundTaskScope.launch("CacheOnBtPlay") {
            context.sessionFlow.collectLatest { session ->
                val info = session.infoBundleFlow.filterNotNull().first()
                val episodeMetadata = info.episodeInfo.toEpisodeMetadata()

                session.fetchSelectFlow.collectLatest fsf@{ bundle ->
                    if (bundle == null) return@fsf

                    context.videoLoadingStateFlow.collectLatest { state ->
                        if (state !is VideoLoadingState.Succeed || state.engineKey == null) return@collectLatest

                        val selected = bundle.mediaSelector.selected.filterNotNull().first()
                        val request = bundle.mediaFetchSession.request.first()

                        val media = if (selected is CachedMedia) {
                            // 选中了正在下载中的 BT 源.
                            if (hasCacheRecordFor(selected, request)) return@collectLatest
                            selected.origin
                        } else {
                            selected
                        }

                        val storage = selectTorrentStorage(
                            downloadManager.storages,
                            media,
                            playedWith = state.engineKey,
                        )
                        if (storage == null) {
                            logger.warn { "No cache storage supports $media, skipping auto cache." }
                            return@collectLatest
                        }
                        if (storage.engine.engineKey.isCloud) {
                            // A cloud record would fetch nothing: the stream is served on demand. It would
                            // still count as a local cache in media selection and win over a real completed
                            // download of the same episode from another torrent.
                            logger.info { "Playback runs on ${storage.engine.engineKey}, no auto cache needed." }
                            return@collectLatest
                        }
                        logger.info { "Auto cache BitTorrent media on play with ${storage.engine.engineKey}: $media" }

                        val metadata =
                            // 查询会话按条目共用, 其请求中的当前剧集是首次打开的那一集; 记录要用本集自己的信息.
                            MediaCacheMetadata(MediaFetchRequest.create(info.subjectInfo, info.episodeInfo), autoCached = true)
                        val cache = downloadManager.createDownload(media, metadata, episodeMetadata, storage)
                        if (cache.metadata.autoCached) {
                            autoCaches += cache
                        }
                    }
                }
            }
        }
    }

    override suspend fun onBeforeSwitchEpisode(newEpisodeId: Int) {
        deleteUnstartedAutoCaches()
    }

    override suspend fun onClose() {
        deleteUnstartedAutoCaches()
    }

    /**
     * 删除尚未开始传输的自动下载.
     */
    private suspend fun deleteUnstartedAutoCaches() = withContext(NonCancellable) {
        // 同集切源产生的记录在切集或退出时统一清理; 用户转正的下载和已有进度的记录保留.
        for (cache in autoCaches.toList()) {
            if (cache.metadata.autoCached && cache.fileStats.first().downloadedBytes.inBytes == 0L) {
                logger.info { "Auto-cached media ${cache.metadata} hasn't started downloading, deleting it." }
                deleteCacheUseCase(cache)
            }
            autoCaches -= cache
        }
    }

    private suspend fun hasCacheRecordFor(media: CachedMedia, request: MediaFetchRequest): Boolean =
        downloadManager.findCaches {
            it.origin.mediaId == media.origin.mediaId &&
                    it.metadata.subjectId == request.subjectId &&
                    it.metadata.episodeId == request.episodeId
        }.isNotEmpty()

    companion object : EpisodePlayerExtensionFactory<CacheOnBtPlayExtension> {
        private val logger = logger<CacheOnBtPlayExtension>()
        override fun create(context: PlayerExtensionContext, koin: Koin): CacheOnBtPlayExtension {
            return CacheOnBtPlayExtension(context, koin)
        }
    }
}
