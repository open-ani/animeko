/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.media.cache

import me.him188.ani.app.domain.usecase.UseCase

interface GetMediaCacheUseCase : UseCase {
    suspend operator fun invoke(subjectId: Int, episodeId: Int): List<MediaCache>
}

class GetMediaCacheUseCaseImpl(
    private val mediaCacheManager: MediaCacheManager,
) : GetMediaCacheUseCase {
    override suspend fun invoke(subjectId: Int, episodeId: Int): List<MediaCache> {
        return mediaCacheManager.findAllCaches { cache ->
            cache.metadata.subjectId == subjectId.toString() &&
                    cache.metadata.episodeId == episodeId.toString()
        }
    }
}