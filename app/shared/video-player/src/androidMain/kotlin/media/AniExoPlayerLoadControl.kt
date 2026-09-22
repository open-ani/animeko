/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.videoplayer.media

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.LoadControl
import me.him188.ani.app.videoplayer.player.PlayerBufferPolicy

/**
 * 按 [PlayerBufferPolicy] 构造 ExoPlayer 的 [LoadControl].
 *
 * - 往前: min/max buffer 都设为 [PlayerBufferPolicy.forward], 即持续预读到该时长为止.
 * - 往后: `setBackBuffer(backward, retainFromKeyframe = true)`. media3 默认为 0, 播放过的样本会立即释放,
 *   导致在线源往后退必须重新下载. 保留到上一个关键帧, 否则回退落在关键帧之间时仍要重新下载.
 * - 字节上限沿用 media3 默认值 (按轨道类型计算, 约 138 MiB). 前后各 30 秒即使在 15 Mbps 码率下也只需约 110 MB,
 *   达到上限时 ExoPlayer 只会暂停往前预读, 不影响播放.
 */
@OptIn(UnstableApi::class)
internal fun aniExoPlayerLoadControl(): LoadControl {
    val forwardMs = PlayerBufferPolicy.forward.inWholeMilliseconds.toInt()
    val backwardMs = PlayerBufferPolicy.backward.inWholeMilliseconds.toInt()
    return DefaultLoadControl.Builder()
        .setBufferDurationsMs(
            /* minBufferMs = */ forwardMs,
            /* maxBufferMs = */ forwardMs,
            /* bufferForPlaybackMs = */ DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_MS,
            /* bufferForPlaybackAfterRebufferMs = */ DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS,
        )
        .setBackBuffer(backwardMs, /* retainBackBufferFromKeyframe = */ true)
        .build()
}
