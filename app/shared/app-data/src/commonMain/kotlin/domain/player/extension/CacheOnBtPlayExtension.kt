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
import me.him188.ani.app.domain.episode.EpisodeSession
import me.him188.ani.app.domain.media.cache.DeleteCacheUseCase
import me.him188.ani.app.domain.media.cache.MediaCache
import me.him188.ani.app.domain.media.download.MediaDownloadManager
import me.him188.ani.app.domain.media.download.selectTorrentStorage
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

    private var currentCache: MediaCache? = null

    override fun onStart(episodeSession: EpisodeSession, backgroundTaskScope: ExtensionBackgroundTaskScope) {
        backgroundTaskScope.launch("CacheOnBtPlay") {
            context.sessionFlow.collectLatest { session ->
                val episodeMetadata = session.infoBundleFlow.filterNotNull().first().episodeInfo.toEpisodeMetadata()

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
                        logger.info { "Auto cache BitTorrent media on play with ${storage.engine.engineKey}: $media" }

                        val metadata = MediaCacheMetadata(request, autoCached = true)
                        val cache = downloadManager.createDownload(media, metadata, episodeMetadata, storage)
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

    /**
     * 删除尚未开始传输的自动下载.
     *
     * 云盘引擎的自动记录不主动下载, 进度恒为零, 留下来只会堆在下载页. 删掉它之后下次下载要重新走一次
     * canServe 探测选引擎, 而探测对同一个磁力链是幂等的, 只有 PikPak 自身不可用时才会给出别的答案.
     */
    private suspend fun deleteCurrentAutoSelectedIfNotStarted() {
        val cache = currentCache ?: return
        currentCache = null
        // 用户在播放期间点了下载时, TorrentMediaCacheStorage.cache 会复用这条记录并清掉 autoCached,
        // 而这里仍然指向它. 云盘引擎的记录此时一个字节都没有, 只看进度会把用户刚添加的下载删掉.
        if (!cache.metadata.autoCached) return
        val progress = cache.fileStats.first().downloadedBytes.inBytes
        if (progress == 0L) {
            logger.info { "Auto-cached media ${cache.metadata} hasn't started downloading, deleting it." }
            deleteCacheUseCase(cache)
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
