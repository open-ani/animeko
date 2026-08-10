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
import org.openani.mediamp.features.PlaybackSpeed

/**
 * A [DanmakuHost] that is connected with the [player].
 *
 * 弹幕屏幕状态由 [danmakuHostState] 内部编译布局决定, 这里只需要接入:
 * - [danmakuListFlow]: 当前话的完整弹幕列表 (已过滤);
 * - 播放器的进度报告、倍速与播放状态.
 */
@Composable
fun PlayerDanmakuHost(
    player: MediampPlayer,
    danmakuHostState: DanmakuHostState,
    danmakuListFlow: Flow<List<DanmakuPresentation>>,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(player, danmakuHostState) {
        player.state.collect {
            danmakuHostState.setPaused(!it.isPlaying)
        }
    }
    LaunchedEffect(player, danmakuHostState) {
        player.currentPositionMillis.collect {
            danmakuHostState.onPositionReport(it)
        }
    }
    LaunchedEffect(player, danmakuHostState) {
        player.features[PlaybackSpeed]?.valueFlow?.collect {
            danmakuHostState.setPlaybackSpeed(it)
        }
    }
    LaunchedEffect(danmakuListFlow, danmakuHostState) {
        danmakuListFlow.collect { list ->
            danmakuHostState.setDanmakuList(list)
        }
    }

    DanmakuHost(danmakuHostState, modifier)
}
