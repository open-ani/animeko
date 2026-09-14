/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.leanback.ui.episode.controls

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.settings_player_default_playback_speed
import me.him188.ani.app.ui.lang.settings_player_long_press_fast_forward_speed
import me.him188.ani.app.ui.lang.settings_player_remember_playback_speed
import me.him188.ani.leanback.ui.episode.TvEpisodeIntent
import me.him188.ani.leanback.ui.episode.TvEpisodeUiState
import me.him188.ani.leanback.ui.episode.components.tvStepKeys
import me.him188.ani.leanback.ui.foundation.widgets.TvOptionRow
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun TvSpeedDialog(state: TvEpisodeUiState, onIntent: (TvEpisodeIntent) -> Boolean, entryModifier: Modifier) {
    val config = state.options.videoConfig
    LazyColumn(Modifier.testTag("tv-speed-options"), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        item {
            TvSpeedControl(
                speed = state.playbackSpeed,
                minSpeed = config.minPlaybackSpeed,
                maxSpeed = config.maxPlaybackSpeed,
                modifier = entryModifier,
                onStep = { onIntent(TvEpisodeIntent.AdjustSpeed(it)) },
            )
        }
        item {
            TvOptionRow(
                stringResource(Lang.settings_player_remember_playback_speed),
                checked = config.rememberPlaybackSpeed,
            ) { onIntent(TvEpisodeIntent.ToggleRememberSpeed) }
        }
        if (!config.rememberPlaybackSpeed) item {
            TvOptionRow(
                stringResource(Lang.settings_player_default_playback_speed),
                "${config.playbackSpeed}x", adjustable = true,
                modifier = Modifier.tvStepKeys { onIntent(TvEpisodeIntent.SetDefaultSpeed(config.playbackSpeed + it * .25f)) },
            ) {}
        }
        item {
            TvOptionRow(
                stringResource(Lang.settings_player_long_press_fast_forward_speed),
                "${config.fastForwardSpeed}x", adjustable = true,
                modifier = Modifier.tvStepKeys { onIntent(TvEpisodeIntent.SetHoldSpeed(config.fastForwardSpeed + it * .25f)) },
            ) {}
        }
    }
}
