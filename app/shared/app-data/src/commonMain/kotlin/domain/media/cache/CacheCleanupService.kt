package me.him188.ani.app.domain.media.cache

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import me.him188.ani.app.data.models.preference.MediaCacheSettings
import me.him188.ani.app.data.repository.episode.EpisodeCollectionRepository
import me.him188.ani.app.data.repository.episode.EpisodeCollectionRepository.setEpisodeWatched
import me.him188.ani.app.data.repository.user.SettingsRepository
import me.him188.ani.datasources.api.topic.UnifiedCollectionType
import me.him188.ani.datasources.api.topic.isDoneOrDropped
import me.him188.ani.utils.logging.info
import me.him188.ani.utils.logging.logger
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * 缓存清理服务
 * 负责处理阅后即焚和自动清理功能
 */
class CacheCleanupService : KoinComponent {
    private val mediaCacheManager: MediaCacheManager by inject()
    private val episodeCollectionRepository: EpisodeCollectionRepository by inject()
    private val settingsRepository: SettingsRepository by inject()
    
    private val logger = logger<CacheCleanupService>()
    
    /**
     * 启动自动清理服务
     * @param scope 协程作用域
     */
    fun startAutoCleanup(scope: CoroutineScope) {
        scope.launch(Dispatchers.IO) {
            while (true) {
                try {
                    val settings = settingsRepository.mediaCacheSettings.flow.first()
                    if (settings.autoCleanup) {
                        cleanupWatchedEpisodes()
                    }
                } catch (e: Exception) {
                    logger.error(e) { "清理缓存时发生错误" }
                }
                // 每24小时检查一次
                kotlinx.coroutines.delay(24 * 60 * 60 * 1000)
            }
        }
    }
    
    /**
     * 清理已观看的剧集缓存
     */
    private suspend fun cleanupWatchedEpisodes() {
        val caches = mediaCacheManager.listCaches()
        for (cache in caches) {
            val subjectId = cache.metadata.subjectIdInt
            val episodeId = cache.metadata.episodeIdInt
            
            // 检查剧集是否已观看
            val collectionType = episodeCollectionRepository.getEpisodeCollectionType(subjectId, episodeId)
            if (collectionType?.isDoneOrDropped() == true) {
                logger.info { "清理已观看剧集缓存: subjectId=$subjectId, episodeId=$episodeId" }
                mediaCacheManager.deleteCache(cache)
            }
        }
    }
    
    /**
     * 处理阅后即焚
     * @param cache 要删除的缓存
     */
    suspend fun handleBurnAfterRead(cache: MediaCache) {
        val settings = settingsRepository.mediaCacheSettings.flow.first()
        if (!settings.burnAfterRead) {
            logger.info { "阅后即焚功能未启用，跳过删除缓存" }
            return
        }
        
        logger.info { "执行阅后即焚: subjectId=${cache.metadata.subjectIdInt}, episodeId=${cache.metadata.episodeIdInt}" }
        mediaCacheManager.deleteCache(cache)
    }
} 