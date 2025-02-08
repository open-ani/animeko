/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.danmaku.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.launch

@Composable
fun DanmakuHost(
    state: DanmakuHostState,
    modifier: Modifier = Modifier,
    baseStyle: TextStyle = MaterialTheme.typography.bodyMedium
) {
    val density = LocalDensity.current
    val trackStubMeasurer = rememberTextMeasurer(1)
    val danmakuTextMeasurer = rememberTextMeasurer(3000)

    SideEffect {
        state.setUIContext(baseStyle, danmakuTextMeasurer, density)
    }

    // observe config changes
    LaunchedEffect(trackStubMeasurer) { state.observeConfig(trackStubMeasurer) }
    // calculate current play time on every frame
    LaunchedEffect(state.paused) { if (!state.paused) state.interpolateFrameLoop() }
    // logical tick for removal of danmaku
    LaunchedEffect(state.paused) {
        if (!state.paused) {
            while (true) {
                state.tick()
                delay(1000)
            }
        }
    }

    BoxWithConstraints(modifier) {
        val hostWidth = constraints.maxWidth
        state.hostWidth = hostWidth
        state.hostHeight = constraints.maxHeight

        // Canvas only subscribes `danmakuUpdateSubscription` and `hostHeight` to re-draw.
        Canvas(
            modifier = Modifier.fillMaxSize()
                .clipToBounds()
                .alpha(state.canvasAlpha),
        ) {
            state.danmakuUpdateSubscription // subscribe changes

            for (danmaku in state.presentFloatingDanmaku) {
                // don't draw uninitialized danmaku
                if (danmaku.y.isNaN()) continue

                with(danmaku.danmaku) {
                    draw(
                        screenPosX = hostWidth - danmaku.distanceX,
                        screenPosY = danmaku.y,
                    )
                }
            }
            for (danmaku in state.presentFixedDanmaku) {
                // don't draw uninitialized danmaku
                if (danmaku.y.isNaN()) continue

                with(danmaku.danmaku) {
                    draw(
                        screenPosX = (hostWidth - danmaku.danmaku.danmakuWidth) / 2f,
                        screenPosY = danmaku.y,
                    )
                }
            }
        }

        if (state.isDebug) {
            CompositionLocalProvider(LocalTextStyle provides MaterialTheme.typography.bodySmall) {
                Column(modifier = Modifier.padding(4.dp).fillMaxSize()) {
                    Text("DanmakuHost state: ")
                    Text("  hostSize: ${state.hostWidth}x${state.hostHeight}, trackHeight: ${state.trackHeight}")
                    Text("  paused: ${state.paused}, elapsedFrameTimeMillis: ${state.elapsedFrameTimeNanos / 1_000_000}")
                    Text("  presentDanmakuCount: ${state.presentFixedDanmaku.size + state.presentFloatingDanmaku.size}")
                    HorizontalDivider()
                    Text("  floating tracks: ")
                    for (track in state.floatingTrack) {
                        Text("    $track")
                    }
                    Text("  top tracks: ")
                    for (track in state.topTrack) {
                        Text("    $track")
                    }
                    Text("  bottom tracks: ")
                    for (track in state.bottomTrack) {
                        Text("    $track")
                    }
                }
            }
        }
    }
}