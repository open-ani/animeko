/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.episode

import androidx.compose.ui.util.fastAll
import me.him188.ani.app.data.models.episode.EpisodeInfo
import me.him188.ani.app.data.models.subject.SubjectRecurrence
import me.him188.ani.app.domain.episode.EpisodeCompletionContext.isKnownCompleted
import me.him188.ani.datasources.api.PackedDate
import me.him188.ani.datasources.api.minus
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
}