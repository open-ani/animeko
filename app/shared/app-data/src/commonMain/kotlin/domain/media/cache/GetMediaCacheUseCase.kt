/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.media.cache

import me.him188.ani.app.domain.media.download.MediaDownloadManager
import me.him188.ani.app.domain.usecase.UseCase

/**
 * 查询某一集当前的下载记录.
 */
interface GetMediaCacheUseCase : UseCase {
    suspend operator fun invoke(subjectId: Int, episodeId: Int): List<MediaCache>
}

/**
 * 直接读取各存储的当前记录 ([MediaDownloadManager.findCaches]), 刚创建或刚删除的记录立即可见.
 */
class GetMediaCacheUseCaseImpl(
    private val downloadManager: MediaDownloadManager,
) : GetMediaCacheUseCase {
    override suspend fun invoke(subjectId: Int, episodeId: Int): List<MediaCache> {
        val subjectKey = subjectId.toString()
        val episodeKey = episodeId.toString()
        return downloadManager.findCaches { cache ->
            cache.metadata.subjectId == subjectKey && cache.metadata.episodeId == episodeKey
        }
    }
}
