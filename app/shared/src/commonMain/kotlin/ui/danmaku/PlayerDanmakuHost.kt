/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
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
import me.him188.ani.danmaku.ui.DanmakuHost
import me.him188.ani.danmaku.ui.DanmakuHostState
import me.him188.ani.danmaku.ui.DanmakuPresentation
import org.openani.mediamp.MediampPlayer

/**
 * A [DanmakuHost] that is connected with the [player].
 *
 * @param forcePaused 强制冻结弹幕 (不跟随 [player] 播放状态). 用于画中画小窗模式:
 * 小窗不显示弹幕, 但保持组合与事件收集, 退出小窗后弹幕从冻结位置无缝恢复.
 */
@Composable
fun PlayerDanmakuHost(
    player: MediampPlayer,
    danmakuHostState: DanmakuHostState,
    danmakuEvent: Flow<UIDanmakuEvent>,
    modifier: Modifier = Modifier,
    forcePaused: Boolean = false,
) {
    LaunchedEffect(player, danmakuHostState, forcePaused) {
        if (forcePaused) {
            // 冻结弹幕时钟; 事件收集由下方的 effect 继续, 语义与"后台暂停"一致
            danmakuHostState.setPaused(true)
        } else {
            player.state.collect {
                danmakuHostState.setPaused(!it.isPlaying)
            }
        }
    }
    LaunchedEffect(danmakuEvent, danmakuHostState) {
        danmakuEvent.collect { event ->
            when (event) {
                is UIDanmakuEvent.Add -> {
                    danmakuHostState.trySend(event.presentation)
                }

                is UIDanmakuEvent.Repopulate -> {
                    danmakuHostState.repopulate(event.list, event.currentPositionMillis)
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
        val list: List<DanmakuPresentation>,
        val currentPositionMillis: Long
    ) : UIDanmakuEvent()
}
