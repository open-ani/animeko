/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.leanback.ui.episode.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.video_player_off
import me.him188.ani.app.ui.lang.video_player_performance
import me.him188.ani.app.ui.lang.video_player_quality
import me.him188.ani.app.ui.lang.video_player_stats_title
import me.him188.ani.app.videoplayer.videoenhancement.VideoEnhancementMode
import me.him188.ani.leanback.ui.episode.components.TvPlayerOptionPanelLayout
import me.him188.ani.leanback.ui.episode.presentation.TvPlayerPanel
import me.him188.ani.leanback.ui.foundation.widgets.TvOptionDefaults
import me.him188.ani.leanback.ui.foundation.widgets.TvOptionRow
import me.him188.ani.leanback.ui.foundation.widgets.tvOptionSurfaceColors
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun TvEnhancementSelector(
    selectedMode: VideoEnhancementMode,
    entryModifier: Modifier,
    onSelect: (VideoEnhancementMode) -> Unit,
) {
    val labels = mapOf(
        VideoEnhancementMode.OFF to stringResource(Lang.video_player_off),
        VideoEnhancementMode.PERFORMANCE to stringResource(Lang.video_player_performance),
        VideoEnhancementMode.QUALITY to stringResource(Lang.video_player_quality),
    )
    val textMeasurer = rememberTextMeasurer()
    val style = MaterialTheme.typography.labelLarge
    val paddingAndCheck = with(LocalDensity.current) { 32.dp.toPx() }
    // Share space by each label's longest word; reserve the check in all modes so selection doesn't resize them.
    val weights = labels.mapValues { (_, label) ->
        textMeasurer.measure(label, style, softWrap = false).multiParagraph.minIntrinsicWidth + paddingAndCheck
    }
    Row(
        Modifier.fillMaxWidth().height(IntrinsicSize.Min).background(TvOptionDefaults.Raised, CircleShape).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        VideoEnhancementMode.entries.forEach { mode ->
            Surface(
                onClick = { onSelect(mode) },
                modifier = Modifier.weight(weights.getValue(mode)).fillMaxHeight()
                    .then(if (mode == selectedMode) entryModifier else Modifier)
                    .testTag("tv-enhancement-${mode.name}")
                    .semantics { selected = mode == selectedMode },
                shape = ClickableSurfaceDefaults.shape(CircleShape),
                colors = tvOptionSurfaceColors(mode == selectedMode),
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
            ) {
                Row(
                    Modifier.fillMaxWidth().fillMaxHeight().heightIn(min = 44.dp).padding(horizontal = 8.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (mode == selectedMode) Icon(Icons.Rounded.Check, null, Modifier.size(16.dp))
                    Text(
                        labels.getValue(mode),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.labelLarge,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

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
