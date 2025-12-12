/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.media.cache

import me.him188.ani.app.domain.danmaku.DanmakuRepository

interface DeleteCacheUseCase {
    suspend operator fun invoke(cache: MediaCache)
}

interface DeleteCacheByCacheIdUseCase {
    suspend operator fun invoke(subjectId: Int, episodeId: Int, cacheId: String)
}

interface DeleteCacheByEpisodeIdUseCase {
    suspend operator fun invoke(subjectId: Int, episodeId: Int)
}

class DeleteCacheUseCaseImpl(
    private val mediaCacheManager: MediaCacheManager,
    private val danmakuRepository: DanmakuRepository
) : DeleteCacheUseCase {
    override suspend fun invoke(cache: MediaCache) {
        mediaCacheManager.deleteCache(cache)
        val subjectId = cache.metadata.subjectId.toIntOrNull()
        val episodeId = cache.metadata.episodeId.toIntOrNull()
        if (subjectId != null && episodeId != null) {
            danmakuRepository.deleteDanmakuIfDontNeeded(subjectId, episodeId)
        }
    }
}

class DeleteCacheByCacheIdUseCaseImpl(
    private val mediaCacheManager: MediaCacheManager,
    private val danmakuRepository: DanmakuRepository
) : DeleteCacheByCacheIdUseCase {
    override suspend fun invoke(subjectId: Int, episodeId: Int, cacheId: String) {
        mediaCacheManager.deleteFirstCache { it.cacheId == cacheId }
        danmakuRepository.deleteDanmakuIfDontNeeded(subjectId, episodeId)
    }
}

class DeleteCacheByEpisodeIdUseCaseImpl(
    private val mediaCacheManager: MediaCacheManager,
    private val danmakuRepository: DanmakuRepository
) : DeleteCacheByEpisodeIdUseCase {
    override suspend fun invoke(subjectId: Int, episodeId: Int) {
        mediaCacheManager.deleteFirstCache { it.metadata.episodeId.toIntOrNull() == episodeId }
        danmakuRepository.deleteDanmakuIfDontNeeded(subjectId, episodeId)
    }
}