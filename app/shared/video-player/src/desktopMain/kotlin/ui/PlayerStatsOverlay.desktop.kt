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
import org.openani.mediamp.mpv.MPVHandle
import kotlin.time.Duration.Companion.seconds

@Composable
actual fun rememberPlayerStatsState(player: MediampPlayer): State<PlayerStatsSnapshot?> {
    return produceState<PlayerStatsSnapshot?>(initialValue = null, player) {
        while (true) {
            value = runCatching { player.readDesktopPlayerStats() }
                .getOrElse { player.readFallbackPlayerStats("Desktop") }
            delay(1.seconds)
        }
    }
}

private fun MediampPlayer.readDesktopPlayerStats(): PlayerStatsSnapshot {
    return when (val impl = impl) {
        is MPVHandle -> readMpvPlayerStats(impl)
        else -> readFallbackPlayerStats(impl::class.simpleName ?: "Desktop")
    }
}

private fun MediampPlayer.readMpvPlayerStats(handle: MPVHandle): PlayerStatsSnapshot {
    val properties = mediaProperties.value
    val width = handle.getPropertyInt("width").takeIf { it > 0 }
    val height = handle.getPropertyInt("height").takeIf { it > 0 }
    val hwdec = handle.stringPropertyOrNull("hwdec-current")?.takeIf { it != "no" }
    val decoderDroppedFrames = handle.getPropertyInt("decoder-frame-drop-count").coerceAtLeast(0)
    val voDroppedFrames = handle.getPropertyInt("frame-drop-count").coerceAtLeast(0)

    return PlayerStatsSnapshot(
        backend = "mpv",
        playbackState = state.value.toString(),
        title = properties?.title ?: handle.stringPropertyOrNull("media-title"),
        positionMillis = currentPositionMillis.value,
        durationMillis = properties?.durationMillis,
        playbackSpeed = features[PlaybackSpeed]?.value
            ?: handle.getPropertyDouble("speed").takeIf { it > 0.0 }?.toFloat(),
        resolution = if (width != null && height != null) "${width}×${height}" else null,
        frameRate = handle.getPropertyDouble("container-fps").takeIf { it > 0.0 }?.toFloat(),
        videoCodec = (handle.stringPropertyOrNull("video-codec") ?: handle.stringPropertyOrNull("video-format"))
            ?.let { codec -> if (hwdec != null) "$codec [$hwdec]" else codec },
        videoBitrate = handle.getPropertyInt("video-bitrate").takeIf { it > 0 }?.toLong(),
        audioCodec = handle.stringPropertyOrNull("audio-codec")
            ?: handle.stringPropertyOrNull("audio-codec-name"),
        audioBitrate = handle.getPropertyInt("audio-bitrate").takeIf { it > 0 }?.toLong(),
        audioSampleRate = handle.getPropertyInt("audio-params/samplerate").takeIf { it > 0 },
        audioChannels = handle.getPropertyInt("audio-params/channel-count").takeIf { it > 0 },
        // cache-speed 为字节每秒
        realtimeInputBitrate = handle.getPropertyInt("cache-speed").takeIf { it > 0 }?.toLong()?.times(8),
        realtimeDemuxBitrate = null,
        // mpv 不暴露已解码帧计数
        decodedVideoFrames = null,
        decodedAudioFrames = null,
        droppedVideoFrames = (decoderDroppedFrames + voDroppedFrames).takeIf { it > 0 }?.toLong(),
        droppedAudioBuffers = null,
    )
}

private fun MPVHandle.stringPropertyOrNull(name: String): String? =
    getPropertyString(name)?.takeIf { it.isNotBlank() }

private fun MediampPlayer.readFallbackPlayerStats(backend: String): PlayerStatsSnapshot {
    val properties = mediaProperties.value
    return PlayerStatsSnapshot(
        backend = backend,
        playbackState = state.value.toString(),
        title = properties?.title,
        positionMillis = currentPositionMillis.value,
        durationMillis = properties?.durationMillis,
        playbackSpeed = features[PlaybackSpeed]?.value,
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
}
