/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.subject.episode.list

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import me.him188.ani.app.data.models.preference.EpisodeListProgressTheme


@Composable
fun EpisodeListFlowRow(
    episodes: List<EpisodeListItem>,
    onClick: (episode: EpisodeListItem) -> Unit,
    onLongClick: (episode: EpisodeListItem) -> Unit,
    modifier: Modifier = Modifier,
    theme: EpisodeListProgressTheme = EpisodeListProgressTheme.Default,
) {
    FlowRow(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        for (episode in episodes) {
            EpisodeSortSquareButton(
                episode,
                onClick = { onClick(episode) },
                onLongClick = { onLongClick(episode) },
                colors = EpisodeListDefaults.colors(theme),
            )
        }
    }
}

