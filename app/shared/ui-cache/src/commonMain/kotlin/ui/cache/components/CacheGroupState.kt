/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.cache.components

import androidx.compose.runtime.Immutable
import me.him188.ani.app.tools.getOrZero
import me.him188.ani.datasources.api.topic.FileSize
import me.him188.ani.datasources.api.topic.FileSize.Companion.bytes
import me.him188.ani.datasources.api.topic.UnifiedCollectionType
import me.him188.ani.utils.platform.annotations.TestOnly

/**
 * 表示一个合并的缓存组, [subjectId] 在 list 中必须是唯一的.
 */
@Immutable
data class CacheGroupState(
    val subjectId: Int,
    val subjectName: String,
    val entries: List<CacheEpisodeState>,
    val collectionType: UnifiedCollectionType?,
    /**
     * 条目封面图片 URL. `null` 表示未知.
     */
    val imageUrl: String? = null,
    /**
     * 条目的总集数. `null` 表示本地未知, 此时展示 [entries] 的数量.
     */
    val totalEpisodeCount: Int? = null,
) {
    val key = subjectId.toString()

    val finishedCount: Int = entries.count { it.status == CacheStatusFilter.Finished }
    val downloadingCount: Int = entries.size - finishedCount
    val averageProgress: Float =
        entries.map { it.progress.getOrZero() }.ifEmpty { listOf(0f) }.average().toFloat()

    /**
     * "x/y 已完成" 中的 y: 条目总集数, 本地未知时为已有缓存数.
     */
    val displayTotalCount: Int = totalEpisodeCount?.coerceAtLeast(entries.size) ?: entries.size

    /**
     * 正在下载 (非暂停/失败/完成) 的数量.
     */
    val activeDownloadCount: Int = entries.count { it.state == CacheEpisodePaused.IN_PROGRESS }

    /**
     * 所有缓存的总大小. 所有均未知时为 [FileSize.Unspecified].
     */
    val totalSize: FileSize = run {
        var sum = 0L
        var any = false
        entries.forEach { entry ->
            if (entry.totalSize != FileSize.Unspecified) {
                sum += entry.totalSize.inBytes
                any = true
            }
        }
        if (any) sum.bytes else FileSize.Unspecified
    }

    /**
     * 正在下载的缓存的总下载速度. 无正在下载或均未知时为 [FileSize.Unspecified].
     */
    val downloadSpeed: FileSize = run {
        var sum = 0L
        var any = false
        entries.forEach { entry ->
            if (entry.state == CacheEpisodePaused.IN_PROGRESS &&
                entry.stats.downloadSpeed != FileSize.Unspecified
            ) {
                sum += entry.stats.downloadSpeed.inBytes
                any = true
            }
        }
        if (any) sum.bytes else FileSize.Unspecified
    }

    val downloadSpeedText: String? =
        if (downloadSpeed != FileSize.Unspecified) "↓ $downloadSpeed/s" else null

    /**
     * 是否有未完成 (含暂停/失败) 的缓存, 用于决定是否展示进度条.
     */
    val hasUnfinished: Boolean = entries.any { !it.isFinished }
}

@TestOnly
internal val TestCacheGroupSates = listOf(
    CacheGroupState(
        subjectId = 1,
        subjectName = "孤独摇滚",
        entries = TestCacheEpisodes,
        collectionType = UnifiedCollectionType.DOING,
        totalEpisodeCount = 12,
    ),
)
