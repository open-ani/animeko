/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.leanback.ui.episode.controls

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.video_player_no_subtitle_tracks
import me.him188.ani.app.ui.lang.video_player_off
import me.him188.ani.leanback.ui.episode.TvEpisodeIntent
import me.him188.ani.leanback.ui.episode.TvPlayerOptionsState
import me.him188.ani.leanback.ui.foundation.widgets.TvOptionDefaults
import me.him188.ani.leanback.ui.foundation.widgets.TvOptionRow
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun TvSubtitleDialog(
    options: TvPlayerOptionsState,
    onIntent: (TvEpisodeIntent) -> Boolean,
    entryModifier: Modifier,
) {
    // Capture the opening selection; later track updates must not steal the user's focus.
    val entryId = remember { options.selectedSubtitleId?.takeIf { id -> options.subtitles.any { it.id == id } } }
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = if (entryId == null) 0 else options.subtitles.indexOfFirst { it.id == entryId } + 1,
    )
    LazyColumn(state = listState, modifier = Modifier.testTag("tv-subtitle-options")) {
        item {
            TvOptionRow(
                stringResource(Lang.video_player_off),
                modifier = if (entryId == null) entryModifier else Modifier,
                selected = options.selectedSubtitleId == null,
            ) { onIntent(TvEpisodeIntent.SelectSubtitle(null)) }
        }
        items(options.subtitles, key = { it.id }) { subtitle ->
            TvOptionRow(
                subtitle.label,
                modifier = if (subtitle.id == entryId) entryModifier else Modifier,
                selected = subtitle.id == options.selectedSubtitleId,
            ) { onIntent(TvEpisodeIntent.SelectSubtitle(subtitle.id)) }
        }
        if (options.subtitles.isEmpty()) item {
            Text(stringResource(Lang.video_player_no_subtitle_tracks), color = TvOptionDefaults.Muted, modifier = Modifier.padding(16.dp))
        }
    }
}

data class TvSubtitleOption(val id: String, val label: String)
