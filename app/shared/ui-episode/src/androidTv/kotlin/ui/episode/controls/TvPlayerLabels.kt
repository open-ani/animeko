/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.leanback.ui.episode.controls

import androidx.compose.runtime.Composable
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.subject_episode_default_title
import me.him188.ani.leanback.ui.episode.TvEpisodeTitle
import me.him188.ani.leanback.ui.episode.TvStripEpisode
import org.jetbrains.compose.resources.stringResource

/** 倍速展示: 1.0 -> "1x", 1.25 -> "1.25x". */
internal fun formatSpeedLabel(speed: Float): String {
    val text = if (speed == speed.toLong().toFloat()) {
        speed.toLong().toString()
    } else {
        speed.toString()
    }
    return "${text}x"
}

internal val TvStripEpisode.sortLabel: String
    @Composable get() = stringResource(Lang.subject_episode_default_title, sort)

internal val TvEpisodeTitle.episodeLine: String
    @Composable get() = listOf(
        if (episodeSort.isBlank()) "" else stringResource(Lang.subject_episode_default_title, episodeSort),
        episodeName,
    ).filter { it.isNotBlank() }.joinToString(" · ")
