/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.videoplayer.ui

import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import kotlinx.coroutines.delay
import org.openani.mediamp.MediampPlayer
import org.openani.mediamp.features.PlaybackSpeed
import kotlin.time.Duration.Companion.seconds

/**
 * 由 [AnalyticsListener] 回调采集的、无法从 [ExoPlayer] 直接轮询的信息.
 */
private class ExoPlayerListenerStats {
    var bandwidthEstimate: Long? = null
    var videoDecoderName: String? = null
    var audioDecoderName: String? = null
}

@OptIn(UnstableApi::class)
@Composable
actual fun rememberPlayerStatsState(player: MediampPlayer): State<PlayerStatsSnapshot?> {
    return produceState<PlayerStatsSnapshot?>(initialValue = null, player) {
        val listenerStats = ExoPlayerListenerStats()
        val exoPlayer = player.impl as? ExoPlayer
        val listener = object : AnalyticsListener {
            override fun onBandwidthEstimate(
                eventTime: AnalyticsListener.EventTime,
                totalLoadTimeMs: Int,
                totalBytesLoaded: Long,
                bitrateEstimate: Long,
            ) {
                listenerStats.bandwidthEstimate = bitrateEstimate.takeIf { it > 0 }
            }

            override fun onVideoDecoderInitialized(
                eventTime: AnalyticsListener.EventTime,
                decoderName: String,
                initializedTimestampMs: Long,
                initializationDurationMs: Long,
            ) {
                listenerStats.videoDecoderName = decoderName
            }

            override fun onVideoDecoderReleased(eventTime: AnalyticsListener.EventTime, decoderName: String) {
                if (listenerStats.videoDecoderName == decoderName) listenerStats.videoDecoderName = null
            }

            override fun onAudioDecoderInitialized(
                eventTime: AnalyticsListener.EventTime,
                decoderName: String,
                initializedTimestampMs: Long,
                initializationDurationMs: Long,
            ) {
                listenerStats.audioDecoderName = decoderName
            }

            override fun onAudioDecoderReleased(eventTime: AnalyticsListener.EventTime, decoderName: String) {
                if (listenerStats.audioDecoderName == decoderName) listenerStats.audioDecoderName = null
            }
        }
        runCatching { exoPlayer?.addAnalyticsListener(listener) }
        try {
            while (true) {
                value = runCatching { player.readAndroidPlayerStats(listenerStats) }
                    .getOrElse { player.readFallbackPlayerStats("ExoPlayer") }
                delay(1.seconds)
            }
        } finally {
            runCatching { exoPlayer?.removeAnalyticsListener(listener) }
        }
    }
}

@OptIn(UnstableApi::class)
private fun MediampPlayer.readAndroidPlayerStats(listenerStats: ExoPlayerListenerStats): PlayerStatsSnapshot {
    val exoPlayer = impl as? ExoPlayer
        ?: return readFallbackPlayerStats(impl::class.simpleName ?: "Android")
    val videoFormat = exoPlayer.videoFormat
    val audioFormat = exoPlayer.audioFormat
    val videoSize = exoPlayer.videoSize
    val width = videoFormat?.width?.validMedia3Value() ?: videoSize.width.validMedia3Value()
    val height = videoFormat?.height?.validMedia3Value() ?: videoSize.height.validMedia3Value()
    val properties = getCurrentMediaProperties()

    return PlayerStatsSnapshot(
        backend = "ExoPlayer",
        playbackState = playbackState.value.toString(),
        title = properties?.title,
        positionMillis = exoPlayer.currentPosition,
        durationMillis = properties?.durationMillis?.takeIf { it >= 0 }
            ?: exoPlayer.duration.takeIf { it != C.TIME_UNSET && it >= 0 },
        playbackSpeed = features[PlaybackSpeed]?.value ?: exoPlayer.playbackParameters.speed,
        resolution = if (width != null && height != null) "${width}×${height}" else null,
        frameRate = videoFormat?.frameRate?.takeIf { it > 0f },
        videoCodec = readableCodecWithDecoder(videoFormat?.readableCodecName(), listenerStats.videoDecoderName),
        videoBitrate = videoFormat?.readableBitrate(),
        audioCodec = readableCodecWithDecoder(audioFormat?.readableCodecName(), listenerStats.audioDecoderName),
        audioBitrate = audioFormat?.readableBitrate(),
        audioSampleRate = audioFormat?.sampleRate?.validMedia3Value(),
        audioChannels = audioFormat?.channelCount?.validMedia3Value(),
        realtimeInputBitrate = listenerStats.bandwidthEstimate,
        realtimeDemuxBitrate = null,
        decodedVideoFrames = exoPlayer.videoDecoderCounters?.renderedOutputBufferCount?.toLong(),
        decodedAudioFrames = exoPlayer.audioDecoderCounters?.renderedOutputBufferCount?.toLong(),
        droppedVideoFrames = exoPlayer.videoDecoderCounters?.droppedBufferCount?.toLong(),
        droppedAudioBuffers = exoPlayer.audioDecoderCounters?.droppedBufferCount?.toLong(),
    )
}

private fun Format.readableCodecName(): String? {
    return codecs?.takeIf { it.isNotBlank() }
        ?: sampleMimeType?.takeIf { it.isNotBlank() }
}

private fun readableCodecWithDecoder(codec: String?, decoderName: String?): String? {
    if (codec == null) return decoderName
    if (decoderName == null) return codec
    return "$codec [$decoderName]"
}

private fun Format.readableBitrate(): Long? {
    return averageBitrate.validMedia3Value()?.toLong()
        ?: peakBitrate.validMedia3Value()?.toLong()
}

private fun Int.validMedia3Value(): Int? = takeIf { it != Format.NO_VALUE && it > 0 }

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
