/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.tv.ui.episode

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import kotlinx.coroutines.flow.Flow
import me.him188.ani.danmaku.ui.DanmakuHost
import me.him188.ani.danmaku.ui.DanmakuHostState
import me.him188.ani.danmaku.ui.DanmakuPresentation
import org.openani.mediamp.MediampPlayer
import org.openani.mediamp.isPlaying

/**
 * 与 [player] 联动的弹幕层. 接线拷自手机端 PlayerDanmakuHost (atv-architecture.md §8.3) —
 * 不依赖 `:app:shared`, 保持 §4.2 约定边界.
 */
@Composable
fun TvPlayerDanmakuHost(
    player: MediampPlayer,
    danmakuHostState: DanmakuHostState,
    danmakuEvent: Flow<TvUIDanmakuEvent>,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(player, danmakuHostState) {
        player.playbackState.collect {
            danmakuHostState.setPaused(!it.isPlaying)
        }
    }
    LaunchedEffect(danmakuEvent, danmakuHostState) {
        danmakuEvent.collect { event ->
            when (event) {
                is TvUIDanmakuEvent.Add -> {
                    danmakuHostState.trySend(event.presentation)
                }

                is TvUIDanmakuEvent.Repopulate -> {
                    danmakuHostState.repopulate(event.list, event.currentPositionMillis)
                }
            }
        }
    }

    DanmakuHost(danmakuHostState, modifier)
}

sealed class TvUIDanmakuEvent {
    data class Add(
        val presentation: DanmakuPresentation,
    ) : TvUIDanmakuEvent()

    data class Repopulate(
        val list: List<DanmakuPresentation>,
        val currentPositionMillis: Long,
    ) : TvUIDanmakuEvent()
}
