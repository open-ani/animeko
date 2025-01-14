/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.subject.details.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Dataset
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import me.him188.ani.app.domain.media.cache.EpisodeCacheStatus
import me.him188.ani.app.ui.subject.SubjectProgressState
import me.him188.ani.app.ui.subject.collection.progress.SubjectProgressButton

@Suppress("UnusedReceiverParameter")
@Composable
fun SubjectDetailsDefaults.SelectEpisodeButtons(
    state: SubjectProgressState,
    episodeCacheStatus: (episodeId: Int) -> EpisodeCacheStatus?,
    onShowEpisodeList: () -> Unit,
    onPlay: (episodeId: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        IconButton(onShowEpisodeList) {
            Icon(Icons.Outlined.Dataset, null)
        }

        Box(Modifier.weight(1f)) {
            SubjectProgressButton(
                state,
                episodeCacheStatus = episodeCacheStatus,
                onPlay = {
                    state.episodeIdToPlay?.let(onPlay)
                },
                Modifier.fillMaxWidth(),
            )
        }
    }
}
