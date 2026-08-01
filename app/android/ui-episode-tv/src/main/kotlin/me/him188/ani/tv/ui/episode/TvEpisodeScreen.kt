/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.tv.ui.episode

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import me.him188.ani.app.domain.player.VideoLoadingState
import me.him188.ani.app.videoplayer.ui.VideoPlayer
import org.openani.mediamp.isPlaying

/**
 * TV 播放页 (atv-architecture.md §8, M1 MVP):
 * 视频面 + 弹幕层 + 简易控制层 (标题/进度条, 5s 自动隐藏、暂停不隐藏).
 *
 * 按键 (§8.2 简化子集): 确认=播/停; ←→ = ±5s seek; ↑↓ = 唤出控制层; 返回 = 退出 (系统默认).
 */
@Composable
fun TvEpisodeScreen(
    viewModel: TvEpisodeViewModel,
    modifier: Modifier = Modifier,
) {
    val player = viewModel.player
    val playbackState by player.playbackState.collectAsState()
    val loadingState by viewModel.videoLoadingState.collectAsState()
    val title by viewModel.titleFlow.collectAsState()

    var controlsVisible by remember { mutableStateOf(true) }
    var controlsShownAt by remember { mutableLongStateOf(0L) }
    fun showControls() {
        controlsVisible = true
        controlsShownAt++
    }

    // 自动隐藏 5s; 暂停不隐藏 (附录 A)
    LaunchedEffect(controlsShownAt, playbackState) {
        if (controlsVisible && playbackState.isPlaying) {
            delay(5_000)
            controlsVisible = false
        }
    }

    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        viewModel.onUIReady() // 启动 AutoSelect/自动连播等扩展 (§8.1)
    }

    Box(
        modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.DirectionCenter, Key.Enter, Key.MediaPlayPause -> {
                        viewModel.togglePause()
                        showControls()
                        true
                    }

                    Key.DirectionLeft, Key.MediaRewind -> {
                        viewModel.seekBy(-5_000)
                        showControls()
                        true
                    }

                    Key.DirectionRight, Key.MediaFastForward -> {
                        viewModel.seekBy(5_000)
                        showControls()
                        true
                    }

                    Key.DirectionUp, Key.DirectionDown -> {
                        showControls()
                        true
                    }

                    else -> false
                }
            },
    ) {
        VideoPlayer(player, Modifier.fillMaxSize())

        // 挂载 Web 源解析器 (WebView, 不可见), 否则 WebVideoSourceResolver not attached
        viewModel.mediaResolver.ComposeContent()

        TvPlayerDanmakuHost(
            player = player,
            danmakuHostState = viewModel.danmakuHostState,
            danmakuEvent = viewModel.danmakuEventFlow,
            modifier = Modifier.fillMaxSize(),
        )

        // 取源/加载状态
        val loading = loadingState
        if (loading !is VideoLoadingState.Succeed) {
            Text(
                text = when (loading) {
                    is VideoLoadingState.Failed -> "加载失败: $loading"
                    VideoLoadingState.Initial, VideoLoadingState.ResolvingSource -> "正在取源…"
                    else -> "加载中…"
                },
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
            )
        }

        // 控制层
        AnimatedVisibility(
            visible = controlsVisible,
            modifier = Modifier.fillMaxSize(),
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            val mediaProperties by player.mediaProperties.collectAsState()
            val mediaLabel by viewModel.currentMediaLabel.collectAsState()
            var positionMillis by remember { mutableLongStateOf(0L) }
            LaunchedEffect(player) {
                while (isActive) {
                    positionMillis = player.getCurrentPositionMillis()
                    delay(500)
                }
            }
            TvPlayerControlsOverlay(
                title = title,
                mediaLabel = mediaLabel,
                positionMillis = positionMillis,
                durationMillis = mediaProperties?.durationMillis ?: 0L,
                isPlaying = playbackState.isPlaying,
            )
        }
    }
}
