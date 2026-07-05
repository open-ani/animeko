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
import uk.co.caprica.vlcj.media.AudioTrackInfo
import uk.co.caprica.vlcj.media.VideoTrackInfo
import uk.co.caprica.vlcj.player.embedded.EmbeddedMediaPlayer
import kotlin.time.Duration.Companion.seconds

@Composable
actual fun rememberPlayerStatsState(player: MediampPlayer): State<PlayerStatsSnapshot?> {
    return produceState<PlayerStatsSnapshot?>(initialValue = null, player) {
        while (true) {
            value = runCatching { player.readDesktopPlayerStats() }
                .getOrElse { player.readFallbackPlayerStats("VLC") }
            delay(1.seconds)
        }
    }
}

private fun MediampPlayer.readDesktopPlayerStats(): PlayerStatsSnapshot {
    val vlcPlayer = impl as? EmbeddedMediaPlayer
        ?: return readFallbackPlayerStats(impl::class.simpleName ?: "Desktop")
    val mediaInfo = vlcPlayer.media().info()
    val videoTrack: VideoTrackInfo? = mediaInfo?.videoTracks()?.firstOrNull()
    val audioTrack: AudioTrackInfo? = mediaInfo?.audioTracks()?.firstOrNull()
    val statistics = mediaInfo?.statistics()
    val properties = getCurrentMediaProperties()

    return PlayerStatsSnapshot(
        backend = "VLC",
        playbackState = playbackState.value.toString(),
        title = properties?.title,
        positionMillis = getCurrentPositionMillis(),
        durationMillis = properties?.durationMillis?.takeIf { it >= 0 },
        playbackSpeed = features[PlaybackSpeed]?.value ?: vlcPlayer.status().rate(),
        resolution = videoTrack?.takeIf { it.width() > 0 && it.height() > 0 }
            ?.let { "${it.width()}×${it.height()}" },
        frameRate = videoTrack?.takeIf { it.frameRate() > 0 && it.frameRateBase() > 0 }
            ?.let { it.frameRate().toFloat() / it.frameRateBase().toFloat() },
        videoCodec = videoTrack?.readableCodecName(),
        videoBitrate = videoTrack?.bitRate()?.takeIf { it > 0 }?.toLong(),
        audioCodec = audioTrack?.readableCodecName(),
        audioBitrate = audioTrack?.bitRate()?.takeIf { it > 0 }?.toLong(),
        audioSampleRate = audioTrack?.rate()?.takeIf { it > 0 },
        audioChannels = audioTrack?.channels()?.takeIf { it > 0 },
        realtimeInputBitrate = statistics?.inputBitrate()?.takeIf { it > 0f }?.let { (it * 8000).toLong() },
        realtimeDemuxBitrate = statistics?.demuxBitrate()?.takeIf { it > 0f }?.let { (it * 8000).toLong() },
        decodedVideoFrames = statistics?.decodedVideo()?.toLong(),
        decodedAudioFrames = statistics?.decodedAudio()?.toLong(),
        droppedVideoFrames = statistics?.picturesLost()?.toLong(),
        droppedAudioBuffers = statistics?.audioBuffersLost()?.toLong(),
    )
}

private fun VideoTrackInfo.readableCodecName(): String? {
    return codecDescription()?.takeIf { it.isNotBlank() }
        ?: codecName()?.takeIf { it.isNotBlank() }
}

private fun AudioTrackInfo.readableCodecName(): String? {
    return codecDescription()?.takeIf { it.isNotBlank() }
        ?: codecName()?.takeIf { it.isNotBlank() }
}

private fun MediampPlayer.readFallbackPlayerStats(backend: String): PlayerStatsSnapshot {
    val properties = getCurrentMediaProperties()
    return PlayerStatsSnapshot(
        backend = backend,
        playbackState = playbackState.value.toString(),
        title = properties?.title,
        positionMillis = getCurrentPositionMillis(),
        durationMillis = properties?.durationMillis?.takeIf { it >= 0 },
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
