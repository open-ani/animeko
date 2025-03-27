package me.him188.ani.app.domain.media.cache

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import me.him188.ani.app.data.models.preference.MediaCacheSettings
import me.him188.ani.app.data.repository.episode.EpisodeCollectionRepository
import me.him188.ani.app.data.repository.user.SettingsRepository
import me.him188.ani.app.domain.media.player.data.TorrentMediaData
import me.him188.ani.datasources.api.episodeIdInt
import me.him188.ani.datasources.api.topic.ResourceLocation
import me.him188.ani.datasources.api.topic.UnifiedCollectionType
import me.him188.ani.datasources.api.topic.isDoneOrDropped
import me.him188.ani.utils.logging.info
import me.him188.ani.utils.logging.logger
import me.him188.ani.utils.logging.error

/**
 * 缓存清理服务
 * 负责处理阅后即焚和自动清理功能
 */
class CacheCleanupService(
    private val mediaCacheManager: MediaCacheManager,
    private val episodeCollectionRepository: EpisodeCollectionRepository,
    private val settingsRepository: SettingsRepository,
) {
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
                    if (settings.enabled) {
                        cleanupWatchedEpisodes()
                    }
                } catch (e: Exception) {
                    logger.error("An error occurred while clearing the cache", e)
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
        // 使用enabledStorages来获取缓存列表
        val cachesList = mediaCacheManager.enabledStorages.first().flatMap { storage ->
            storage.listFlow.first()
        }
        
        for (cache in cachesList) {
            val subjectId = cache.metadata.subjectId
            val episodeId = cache.metadata.episodeId
            
            // 检查剧集是否已观看
            val collectionType = episodeId.toIntOrNull()?.let { episodeCollectionRepository.getEpisodeCollectionType(it, true) }
            if (collectionType?.isDoneOrDropped() == true) {
                logger.info { "Clearing cache of watched episode: subjectId=$subjectId, episodeId=$episodeId" }
                mediaCacheManager.deleteCache(cache)
            }
        }
    }
    
    /**
     * 处理阅后即焚
     * @param mediaData 要删除的媒体数据
     */
    suspend fun handleBurnAfterRead(mediaData: TorrentMediaData) {
        val settings = settingsRepository.mediaCacheSettings.flow.first()
        // TODO: 添加阅后即焚设置
        // 临时修复方案：使用 enabled 设置代替
        if (!settings.enabled) {
            logger.info { "Burn after reading disabled, skipping cache deletion" }
            return
        }
        
        // 从媒体数据中查找缓存
        val cachesList = mediaCacheManager.enabledStorages.first().flatMap { storage ->
            storage.listFlow.first()
        }
        
        // 查找匹配的缓存
        var foundCache: MediaCache? = null
        val targetUri = mediaData.uri
        
        for (cache in cachesList) {
            try {
                val download = cache.origin.download
                // 简化比较逻辑，直接比较uri字符串，避免类型转换问题
                val downloadUri = when (download) {
                    is ResourceLocation.MagnetLink -> download.uri
                    is ResourceLocation.HttpTorrentFile -> download.uri
                    else -> null
                }
                
                if (downloadUri != null && downloadUri == targetUri) {
                    foundCache = cache
                    break
                }
            } catch (e: Exception) {
                logger.error(e) { "Error comparing cache: ${e.message}" }
            }
        }
        
        if (foundCache == null) {
            logger.info { "Cache not found for media data: $targetUri" }
            return
        }
        
        logger.info { "Executing burn after reading: subjectId=${foundCache.metadata.subjectId}, episodeId=${foundCache.metadata.episodeId}" }
        mediaCacheManager.deleteCache(foundCache)
    }
} 