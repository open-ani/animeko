/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.leanback.ui.episode

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.video_player_stats_title
import me.him188.ani.app.videoplayer.videoenhancement.VideoEnhancementMode
import me.him188.ani.leanback.ui.foundation.widgets.TvOptionRow
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun TvPlayerVideoSettingsPanel(
    enhancementMode: VideoEnhancementMode?,
    statsVisible: Boolean,
    onSetEnhancement: (VideoEnhancementMode) -> Unit,
    onToggleStats: () -> Unit,
    listState: LazyListState,
    entryModifier: Modifier,
    modifier: Modifier = Modifier,
) {
    TvPlayerOptionPanelLayout(TvPlayerPanel.VideoSettings, listState, modifier) {
        if (enhancementMode != null) {
            item {
                TvEnhancementSelector(enhancementMode, entryModifier) {
                    onSetEnhancement(it)
                }
            }
        }
        item {
            TvOptionRow(
                stringResource(Lang.video_player_stats_title),
                checked = statsVisible,
                modifier = (if (enhancementMode == null) entryModifier else Modifier)
                    .testTag("tv-player-stats-toggle"),
            ) { onToggleStats() }
        }
    }
}
