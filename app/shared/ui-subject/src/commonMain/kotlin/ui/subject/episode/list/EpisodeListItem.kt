/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.subject.episode.list

import androidx.compose.runtime.Immutable
import me.him188.ani.app.data.models.episode.EpisodeCollectionInfo
import me.him188.ani.app.data.models.subject.SubjectRecurrence
import me.him188.ani.datasources.api.EpisodeSort
import me.him188.ani.datasources.api.topic.UnifiedCollectionType
import me.him188.ani.utils.platform.annotations.TestOnly
import kotlin.random.Random

/**
 * 剧集列表中的单个剧集信息.
 */
@Immutable
data class EpisodeListItem(
    val episodeId: Int,
    val sort: EpisodeSort,
    val ep: EpisodeSort?,
    val name: String,
    val nameCn: String,
    val collectionType: UnifiedCollectionType,
//    val cacheStatus: EpisodeCacheStatus?,
    /**
     * 是否已经开播了
     */
    val isBroadcast: Boolean,
) {
    val isDoneOrDropped: Boolean =
        collectionType == UnifiedCollectionType.DONE || collectionType == UnifiedCollectionType.DROPPED

    companion object {
        /**
         * @param isBroadcast See [SubjectRecurrence.isEpisodeBroadcast]
         */
        fun from(
            collection: EpisodeCollectionInfo,
            isBroadcast: Boolean,
//            cacheStatus: EpisodeCacheStatus?,
        ): EpisodeListItem {
            return EpisodeListItem(
                episodeId = collection.episodeId,
                sort = collection.episodeInfo.sort,
                ep = collection.episodeInfo.ep,
                name = collection.episodeInfo.name,
                nameCn = collection.episodeInfo.nameCn,
                collectionType = collection.collectionType,
//                cacheStatus = cacheStatus,
//                airTime = collection.episodeInfo.airDate.toLocalDateOrNull()?,
                isBroadcast = isBroadcast,
            )
        }
    }
}

@TestOnly
fun createTestEpisodeListItem(
    sort: EpisodeSort = EpisodeSort(1),
    random: Random = Random(sort.hashCode()),
    episodeId: Int = random.nextInt(1, 1000),
    ep: EpisodeSort? = null,
    name: String = "Test Episode $episodeId",
    nameCn: String = "测试剧集 $episodeId",
    collectionType: UnifiedCollectionType = UnifiedCollectionType.entries.random(random),
//    cacheStatus: EpisodeCacheStatus? = EpisodeCacheStatus.randomOrNull(random),
    isBroadcast: Boolean = random.nextBoolean(),
): EpisodeListItem {
    return EpisodeListItem(
        episodeId,
        sort,
        ep,
        name,
        nameCn,
        collectionType,
//        cacheStatus,
        isBroadcast,
    )
}
