/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.videoplayer.ui.progress

import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import me.him188.ani.app.ui.foundation.AbstractViewModel
import me.him188.ani.app.ui.foundation.dialogs.PlatformPopupProperties
import me.him188.ani.app.ui.lang.*
import org.openani.mediamp.metadata.SubtitleTrack
import org.openani.mediamp.metadata.TrackGroup
import org.jetbrains.compose.resources.*

@Stable
class SubtitleTrackState(
    private val current: StateFlow<SubtitleTrack?>,
    candidates: Flow<List<SubtitleTrack>>,
    private val select: (SubtitleTrack?) -> Unit,
) : AbstractViewModel() {
    // Tracks the user's explicit subtitle selection so it can be re-applied when
    // the player reverts it (e.g. when an external subtitle file finishes loading
    // for Jellyfin sources, ExoPlayer may re-run default track selection).
    //
    // DESIRE_NOT_SET  — user hasn't made an explicit choice yet
    // DESIRE_NONE     — user explicitly chose "no subtitle"
    // any other value — internalId of the track the user wants
    private val userDesiredInternalId = MutableStateFlow(DESIRE_NOT_SET)

    val options = candidates.map { tracks ->
        tracks.map { track ->
            SubtitlePresentation(track, track.subtitleLanguage)
        }
    }.flowOn(Dispatchers.Default).shareInBackground()

    val value = combine(options, current) { options, current ->
        options.firstOrNull { it.subtitleTrack.id == current?.id }
    }.flowOn(Dispatchers.Default)

    init {
        backgroundScope.launch {
            combine(options, current, userDesiredInternalId) { opts, cur, desired ->
                Triple(opts, cur, desired)
            }.collect { (opts, cur, desired) ->
                if (desired == DESIRE_NOT_SET) return@collect
                val targetTrack = if (desired == DESIRE_NONE) null
                else opts.firstOrNull { it.subtitleTrack.internalId == desired }?.subtitleTrack
                // Only re-apply if the desired track exists in the current candidates
                // (or the user wants none), and the player has diverged from it.
                val canApply = desired == DESIRE_NONE || targetTrack != null
                if (canApply && cur?.internalId != targetTrack?.internalId) {
                    select(targetTrack)
                }
            }
        }
    }

    /** Called when the user explicitly chooses a subtitle track (or null for "off"). */
    fun userSelect(track: SubtitleTrack?) {
        userDesiredInternalId.value = track?.internalId ?: DESIRE_NONE
    }

    companion object {
        private const val DESIRE_NOT_SET = "__not_set__"
        private const val DESIRE_NONE = "__none__"
    }
}


@Composable
fun PlayerControllerDefaults.SubtitleSwitcher(
    playerState: TrackGroup<SubtitleTrack>,
    modifier: Modifier = Modifier,
    onSelect: (SubtitleTrack?) -> Unit = { playerState.select(it) },
    onExpandedChanged: (expanded: Boolean) -> Unit = {},
) {
    val state = remember(playerState) {
        SubtitleTrackState(playerState.selected, playerState.candidates) { playerState.select(it) }
    }
    SubtitleSwitcher(state, onSelect, modifier, onExpandedChanged)
}

@Composable
fun PlayerControllerDefaults.SubtitleSwitcher(
    state: SubtitleTrackState,
    onSelect: (SubtitleTrack?) -> Unit,
    modifier: Modifier = Modifier,
    onExpandedChanged: (expanded: Boolean) -> Unit = {},
) {
    val options by state.options.collectAsStateWithLifecycle(emptyList())
    SubtitleSwitcher(
        value = state.value.collectAsStateWithLifecycle(null).value,
        onValueChange = {
            state.userSelect(it?.subtitleTrack)
            onSelect(it?.subtitleTrack)
        },
        optionsProvider = { options },
        modifier,
        onExpandedChanged = onExpandedChanged,
    )
}

/**
 * 选字幕
 */
@Composable
fun PlayerControllerDefaults.SubtitleSwitcher(
    value: SubtitlePresentation?,
    onValueChange: (SubtitlePresentation?) -> Unit,
    optionsProvider: () -> List<SubtitlePresentation>,
    modifier: Modifier = Modifier,
    onExpandedChanged: (expanded: Boolean) -> Unit = {},
) {
    val optionsProviderUpdated by rememberUpdatedState(optionsProvider)
    val options by remember {
        derivedStateOf {
            optionsProviderUpdated() + null
        }
    }
    if (options.size <= 1) return // 1 for `null`
    return OptionsSwitcher(
        value = value,
        onValueChange = onValueChange,
        optionsProvider = { options },
        renderValue = {
            if (it == null) {
                Text(stringResource(Lang.video_player_off))
            } else {
                Text(it.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        },
        renderValueExposed = {
            val subtitleText = stringResource(Lang.video_player_subtitle)
            Text(
                remember(it, subtitleText) { it?.displayName ?: subtitleText },
                Modifier.widthIn(max = 64.dp),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        },
        modifier,
        properties = PlatformPopupProperties(
            clippingEnabled = false,
            focusable = true, // Critical for TV focus (especially Android TV); applied on all platforms
        ),
        onExpandedChanged = onExpandedChanged,
    )
}
