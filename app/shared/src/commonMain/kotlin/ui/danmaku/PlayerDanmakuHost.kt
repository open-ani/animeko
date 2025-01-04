/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.danmaku

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import kotlinx.coroutines.flow.Flow
import me.him188.ani.danmaku.api.DanmakuPresentation
import me.him188.ani.danmaku.ui.DanmakuHost
import me.him188.ani.danmaku.ui.DanmakuHostState
import org.openani.mediamp.MediampPlayer

/**
 * A [DanmakuHost] that is connected with the [player].
 */
@Composable
fun PlayerDanmakuHost(
    player: MediampPlayer,
    danmakuHostState: DanmakuHostState,
    danmakuEvent: Flow<UIDanmakuEvent>,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(Unit) {
        player.playbackState.collect {
            danmakuHostState.setPaused(!it.isPlaying)
        }
    }
    LaunchedEffect(Unit) {
        danmakuEvent.collect { event ->
            when (event) {
                is UIDanmakuEvent.Add -> {
                    danmakuHostState.trySend(event.presentation)
                }

                is UIDanmakuEvent.Repopulate -> {
                    danmakuHostState.repopulate(event.list)
                }
            }
        }
    }

    DanmakuHost(danmakuHostState, modifier)
}

sealed class UIDanmakuEvent {
    data class Add(
        val presentation: DanmakuPresentation
    ) : UIDanmakuEvent()

    data class Repopulate(
        val list: List<DanmakuPresentation>
    ) : UIDanmakuEvent()
}
