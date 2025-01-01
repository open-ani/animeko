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
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import me.him188.ani.app.ui.foundation.AbstractViewModel
import me.him188.ani.app.ui.foundation.dialogs.PlatformPopupProperties
import org.openani.mediamp.metadata.AudioTrack
import org.openani.mediamp.metadata.TrackGroup

@Stable
class AudioTrackState(
    current: StateFlow<AudioTrack?>,
    candidates: Flow<List<AudioTrack>>,
) : AbstractViewModel() {
    val options = candidates.map { tracks ->
        tracks.map { track ->
            AudioPresentation(track, track.audioName)
        }
    }.flowOn(Dispatchers.Default).shareInBackground()

    val value = combine(options, current) { options, current ->
        options.firstOrNull { it.audioTrack.id == current?.id }
    }.flowOn(Dispatchers.Default)
}


@Composable
fun PlayerControllerDefaults.AudioSwitcher(
    playerState: TrackGroup<AudioTrack>,
    modifier: Modifier = Modifier,
    onSelect: (AudioTrack?) -> Unit = { playerState.select(it) },
) {
    val state = remember(playerState) {
        AudioTrackState(playerState.current, playerState.candidates)
    }
    AudioSwitcher(state, onSelect, modifier)
}

@Composable
fun PlayerControllerDefaults.AudioSwitcher(
    state: AudioTrackState,
    onSelect: (AudioTrack?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val options by state.options.collectAsStateWithLifecycle(emptyList())
    AudioSwitcher(
        value = state.value.collectAsStateWithLifecycle(null).value,
        onValueChange = { onSelect(it?.audioTrack) },
        optionsProvider = { options },
        modifier,
    )
}

/**
 * 选音轨.
 */
@Composable
fun PlayerControllerDefaults.AudioSwitcher(
    value: AudioPresentation?,
    onValueChange: (AudioPresentation?) -> Unit,
    optionsProvider: () -> List<AudioPresentation>,
    modifier: Modifier = Modifier,
) {
    val optionsProviderUpdated by rememberUpdatedState(optionsProvider)
    val options by remember {
        derivedStateOf {
            optionsProviderUpdated() + null
        }
    }
    if (options.size <= 2) return // 1 for `null`, 只有一个的时候也不要显示
    return OptionsSwitcher(
        value = value,
        onValueChange = onValueChange,
        optionsProvider = { options },
        renderValue = {
            if (it == null) {
                Text("自动")
            } else {
                Text(it.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        },
        renderValueExposed = {
            Text(
                remember(it) { it?.displayName ?: "音轨" },
                Modifier.widthIn(max = 64.dp),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        },
        modifier,
        properties = PlatformPopupProperties(
            clippingEnabled = false,
        ),
    )
}
