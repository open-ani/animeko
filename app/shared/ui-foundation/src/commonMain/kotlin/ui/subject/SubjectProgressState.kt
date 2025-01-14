/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.subject

import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import me.him188.ani.app.data.models.subject.ContinueWatchingStatus
import me.him188.ani.app.data.models.subject.SubjectProgressInfo
import me.him188.ani.app.tools.WeekFormatter
import me.him188.ani.datasources.api.toLocalDateOrNull

/**
 * 条目的观看进度, 用于例如 "看到 12" 的按钮
 */
@Stable // Test: AiringProgressTests
class SubjectProgressState(
    info: State<SubjectProgressInfo?>,
    private val weekFormatter: WeekFormatter = WeekFormatter.System,
) {
    private val continueWatchingStatus by derivedStateOf {
        info.value?.continueWatchingStatus
    }

    /**
     * 是否拥有至少一话, 并且已经观看了这一话, 并且没有更新的了.
     */
    val isLatestEpisodeWatched by derivedStateOf {
        continueWatchingStatus is ContinueWatchingStatus.Watched
    }

    /**
     * 已经完结并且看完了
     */
    val isDone by derivedStateOf {
        continueWatchingStatus == ContinueWatchingStatus.Done
    }

    val episodeIdToPlay: Int? by derivedStateOf {
        info.value?.nextEpisodeIdToPlay
    }

    val buttonText by derivedStateOf {
        when (val s = continueWatchingStatus) {
            is ContinueWatchingStatus.Continue -> "继续观看 ${renderEpAndSort(s.episodeEp, s.episodeSort)}"
            ContinueWatchingStatus.Done -> "已看完"
            is ContinueWatchingStatus.NotOnAir -> {
                val date = s.airDate.toLocalDateOrNull()
                if (date != null) {
                    val week = weekFormatter.format(date)
                    "${week}开播"
                } else {
                    "还未开播"
                }
            }

            ContinueWatchingStatus.Start -> "开始观看"
            is ContinueWatchingStatus.Watched -> {
                val date = s.nextEpisodeAirDate.toLocalDateOrNull()
                if (date != null) {
                    val week = weekFormatter.format(date)
                    "${week}更新"
                } else {
                    "看过 ${renderEpAndSort(s.episodeEp, s.episodeSort)}"
                }
            }

            null -> "未知"
        }
    }

    val buttonIsPrimary by derivedStateOf {
        when (continueWatchingStatus) {
            is ContinueWatchingStatus.Start,
            is ContinueWatchingStatus.Continue -> true

            else -> false
        }
    }
}
