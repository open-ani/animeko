/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.episode

import androidx.compose.ui.util.fastAll
import me.him188.ani.app.data.models.episode.EpisodeCollectionInfo
import me.him188.ani.app.data.models.episode.EpisodeInfo
import me.him188.ani.app.data.models.subject.SubjectRecurrence
import me.him188.ani.app.domain.episode.EpisodeCompletionContext.isKnownCompleted
import me.him188.ani.datasources.api.EpisodeType
import me.him188.ani.datasources.api.PackedDate
import me.him188.ani.datasources.api.minus
import kotlin.math.absoluteValue
import kotlin.math.sign
import kotlin.time.Duration.Companion.days

object EpisodeCollections {
    fun isSubjectCompleted(
        episodes: List<EpisodeInfo>,
        recurrence: SubjectRecurrence?,
        now: PackedDate = PackedDate.now(), // todo: change to Instant. isKnownCompleted 目前没有使用这个
    ): Boolean {
        val allEpisodesFinished = episodes.fastAll { it.isKnownCompleted(recurrence) }
        if (!allEpisodesFinished) return false // 如果无法肯定已经完结, 则认为未完结
        return isSubjectCompleted(episodes.asSequence().map { it.airDate }, now)
    }

    fun isSubjectCompleted(dates: Sequence<PackedDate>, now: PackedDate = PackedDate.now()): Boolean {
        val maxAirDate = dates
            .filter { it.isValid }
            .maxOrNull()

        return maxAirDate != null && now - maxAirDate >= 365.days
    }

    /**
     * 切换上一集/下一集时 [currentEpisodeId] 的相邻剧集. 只在同类型的剧集之间切换: 正片的下一集是下一集正片, SP01 的下一集是 SP02.
     *
     * @param episodes 一个条目的剧集, 同类型的剧集按序号排列
     * @param offset `1` 为下一集, `-1` 为上一集; 绝对值更大时跳过相应数量的同类型剧集
     * @return 找不到当前剧集, 或已经是同类型的第一集/最后一集时为 `null`
     */
    inline fun <T> findNeighborEpisode(
        episodes: List<T>,
        currentEpisodeId: Int,
        offset: Int,
        episodeId: (T) -> Int,
        episodeType: (T) -> EpisodeType?,
    ): T? {
        val currentIndex = episodes.indexOfFirst { episodeId(it) == currentEpisodeId }
        if (currentIndex == -1) return null
        if (offset == 0) return episodes[currentIndex]

        val type = episodeType(episodes[currentIndex])
        val step = offset.sign
        var remaining = offset.absoluteValue
        var index = currentIndex + step
        while (index in episodes.indices) {
            if (episodeType(episodes[index]) == type && --remaining == 0) return episodes[index]
            index += step
        }
        return null
    }
}

/**
 * @see EpisodeCollections.findNeighborEpisode
 */
fun List<EpisodeCollectionInfo>.findNeighborEpisode(currentEpisodeId: Int, offset: Int): EpisodeCollectionInfo? =
    EpisodeCollections.findNeighborEpisode(this, currentEpisodeId, offset, { it.episodeId }, { it.episodeInfo.type })
