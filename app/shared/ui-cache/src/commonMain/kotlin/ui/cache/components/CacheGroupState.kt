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
    val collectionType: UnifiedCollectionType?
) {
    val key = subjectId.toString()

    val finishedCount: Int = entries.count { it.status == CacheStatusFilter.Finished }
    val downloadingCount: Int = entries.size - finishedCount
    val averageProgress: Float =
        entries.map { it.progress.getOrZero() }.ifEmpty { listOf(0f) }.average().toFloat()
}

@TestOnly
internal val TestCacheGroupSates = listOf(
    CacheGroupState(
        subjectId = 1,
        subjectName = "孤独摇滚",
        entries = TestCacheEpisodes,
        collectionType = UnifiedCollectionType.DOING,
    ),
)
