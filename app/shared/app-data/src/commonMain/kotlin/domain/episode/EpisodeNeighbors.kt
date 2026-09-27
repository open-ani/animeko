/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.episode

import me.him188.ani.app.data.models.episode.EpisodeCollectionInfo
import me.him188.ani.datasources.api.EpisodeType

/**
 * 切换上一集/下一集时 [currentEpisodeId] 的相邻剧集. 只在同类型的剧集之间切换: 正片的下一集是下一集正片, SP01 的下一集是 SP02.
 *
 * @receiver 一个条目的剧集, 同类型的剧集按序号排列
 * @param offset `1` 为下一集, `-1` 为上一集
 * @return 找不到当前剧集, 或已经是同类型的第一集/最后一集时为 `null`
 */
inline fun <T> List<T>.findNeighborEpisode(
    currentEpisodeId: Int,
    offset: Int,
    episodeId: (T) -> Int,
    episodeType: (T) -> EpisodeType?,
): T? {
    val current = firstOrNull { episodeId(it) == currentEpisodeId } ?: return null
    val sameType = filter { episodeType(it) == episodeType(current) }
    return sameType.getOrNull(sameType.indexOfFirst { episodeId(it) == currentEpisodeId } + offset)
}

/**
 * @see findNeighborEpisode
 */
fun List<EpisodeCollectionInfo>.findNeighborEpisode(currentEpisodeId: Int, offset: Int): EpisodeCollectionInfo? =
    findNeighborEpisode(currentEpisodeId, offset, { it.episodeId }, { it.episodeInfo.type })
