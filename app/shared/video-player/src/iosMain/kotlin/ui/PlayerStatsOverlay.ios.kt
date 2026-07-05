/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.videoplayer.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import kotlinx.coroutines.delay
import org.openani.mediamp.MediampPlayer
import org.openani.mediamp.features.PlaybackSpeed
import kotlin.time.Duration.Companion.seconds

@Composable
actual fun rememberPlayerStatsState(player: MediampPlayer): State<PlayerStatsSnapshot?> {
    return produceState<PlayerStatsSnapshot?>(initialValue = null, player) {
        while (true) {
            val properties = player.getCurrentMediaProperties()
            value = PlayerStatsSnapshot(
                backend = player.impl::class.simpleName ?: "iOS",
                playbackState = player.playbackState.value.toString(),
                title = properties?.title,
                positionMillis = player.getCurrentPositionMillis(),
                durationMillis = properties?.durationMillis?.takeIf { it >= 0 },
                playbackSpeed = player.features[PlaybackSpeed]?.value,
                resolution = null,
                frameRate = null,
                videoCodec = null,
                videoBitrate = null,
                audioCodec = null,
                audioBitrate = null,
                audioSampleRate = null,
                audioChannels = null,
                realtimeInputBitrate = null,
                realtimeDemuxBitrate = null,
                decodedVideoFrames = null,
                decodedAudioFrames = null,
                droppedVideoFrames = null,
                droppedAudioBuffers = null,
            )
            delay(1.seconds)
        }
    }
}
