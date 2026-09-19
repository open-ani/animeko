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
    /** TMDB 剧照 (宽 300px) 直链, 见 [me.him188.ani.app.data.models.episode.EpisodeInfo.imageMedium]. */
    val imageMedium: String? = null,
    /** TMDB 原尺寸剧照直链, 见 [me.him188.ani.app.data.models.episode.EpisodeInfo.imageLarge]. */
    val imageLarge: String? = null,
    /**
     * 上次播放进度 `0..1`, 来自本地播放记录 (见 [me.him188.ani.app.data.models.player.playProgressByEpisodeId]);
     * 没有播放过时为 `null`. 未看完且非空时卡片底边显示进度条.
     */
    val playProgress: Float? = null,
) {
    val isDoneOrDropped: Boolean =
        collectionType == UnifiedCollectionType.DONE || collectionType == UnifiedCollectionType.DROPPED

    val displayName: String get() = nameCn.ifBlank { name }

    val nameOrNameCn: String get() = name.ifBlank { nameCn }

    /**
     * 根据用户偏好选择的显示名称, 与 [me.him188.ani.app.data.models.subject.preferredDisplayName] 同一约定.
     * @param useOriginalTitle 为 `true` 时优先显示原名 ([name]), 为 `false` 时行为与 [displayName] 一致.
     */
    fun preferredDisplayName(useOriginalTitle: Boolean): String =
        if (useOriginalTitle) nameOrNameCn else displayName

    companion object {
        /**
         * @param isBroadcast 是否已经开播, 见 [EpisodeListUiState.isEpisodeBroadcast]
         */
        fun from(
            collection: EpisodeCollectionInfo,
            isBroadcast: Boolean,
//            cacheStatus: EpisodeCacheStatus?,
            playProgress: Float? = null,
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
                imageMedium = collection.episodeInfo.imageMedium,
                imageLarge = collection.episodeInfo.imageLarge,
                playProgress = playProgress,
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
    imageMedium: String? = null,
    imageLarge: String? = null,
    playProgress: Float? = null,
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
        imageMedium,
        imageLarge,
        playProgress,
    )
}

/** 预览与测试用的剧照地址. 预览环境的图片加载器对任何 URL 都画同一张占位图. */
@TestOnly
const val TestEpisodeStillUrl: String = "https://static.myani.org/tmdb/preview/still.jpg"
