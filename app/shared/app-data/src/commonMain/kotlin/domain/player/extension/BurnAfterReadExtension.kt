package me.him188.ani.app.domain.player.extension

import kotlinx.coroutines.cancellableCoroutineScope
import kotlinx.coroutines.flow.combine
import me.him188.ani.app.domain.media.cache.CacheCleanupService
import me.him188.ani.app.domain.media.cache.MediaCache
import me.him188.ani.app.domain.media.player.data.TorrentMediaData
import me.him188.ani.utils.logging.info
import me.him188.ani.utils.logging.logger
import org.koin.core.Koin
import org.openani.mediamp.MediampPlayer
import org.openani.mediamp.PlaybackState

/**
 * 阅后即焚扩展
 * 在播放完成后自动删除缓存
 */
class BurnAfterReadExtension(
    private val context: PlayerExtensionContext,
    koin: Koin,
) : PlayerExtension(name = "BurnAfterReadExtension") {
    private val cacheCleanupService: CacheCleanupService by koin.inject()
    private val logger = logger<BurnAfterReadExtension>()

    override fun onStart(episodeSession: EpisodeSession, backgroundTaskScope: ExtensionBackgroundTaskScope) {
        backgroundTaskScope.launch("BurnAfterReadListener") {
            val player = context.player
            player.playbackState.collect { playbackState ->
                when (playbackState) {
                    PlaybackState.FINISHED -> {
                        val mediaData = player.mediaData.value
                        if (mediaData is TorrentMediaData) {
                            try {
                                logger.info { "播放完成，执行阅后即焚" }
                                cacheCleanupService.handleBurnAfterRead(mediaData)
                            } catch (e: Exception) {
                                logger.error(e) { "阅后即焚失败" }
                            }
                        }
                    }
                    else -> Unit
                }
            }
        }
    }

    companion object : EpisodePlayerExtensionFactory<BurnAfterReadExtension> {
        override fun create(context: PlayerExtensionContext, koin: Koin): BurnAfterReadExtension =
            BurnAfterReadExtension(context, koin)
    }
} 