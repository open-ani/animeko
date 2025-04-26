/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.models.episode

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import me.him188.ani.app.domain.media.cache.EpisodeCacheStatus
import me.him188.ani.datasources.api.topic.UnifiedCollectionType

/**
 * Describes the progress of an episode (of a subject)
 */
@Stable
@Deprecated("Use EpisodeListItem instead")
class EpisodeProgressItem(
    val episodeId: Int,
    val episodeSort: String,
    val collectionType: UnifiedCollectionType,
    val isOnAir: Boolean?,
    val cacheStatus: EpisodeCacheStatus?,
) {
    var isLoading by mutableStateOf(false)
}
