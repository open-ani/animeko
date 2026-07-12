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
import androidx.media3.exoplayer.mediacodec.MediaCodecInfo
import androidx.media3.exoplayer.mediacodec.MediaCodecRenderer
import androidx.media3.exoplayer.upstream.BandwidthMeter
import kotlinx.coroutines.delay
import org.openani.mediamp.MediampPlayer
import org.openani.mediamp.features.PlaybackSpeed
import java.lang.reflect.Field
import kotlin.time.Duration.Companion.seconds

/**
 * 通过反射读取 [ExoPlayer] 未公开的实时信息 (网速估计, 实际解码器名).
 *
 * 反射目标在 proguard 规则中保留 (见 app/shared/proguard-rules.pro). 任一目标解析或读取失败时,
 * 对应访问器被置空, 后续轮询不再重试, 相应字段保持为 null.
 */
@OptIn(UnstableApi::class)
private class ExoPlayerReflectionStats(private val exoPlayer: ExoPlayer) {
    private var bandwidthMeter: BandwidthMeter? = try {
        findField(exoPlayer.javaClass, "bandwidthMeter")?.get(exoPlayer) as? BandwidthMeter
    } catch (_: Exception) {
        null
    }

    private var codecInfoField: Field? = try {
        MediaCodecRenderer::class.java.getDeclaredField("codecInfo").apply { isAccessible = true }
    } catch (_: Exception) {
        null
    }

    fun bandwidthEstimate(): Long? {
        val meter = bandwidthMeter ?: return null
        return try {
            meter.bitrateEstimate.takeIf { it > 0 }
        } catch (_: Exception) {
            bandwidthMeter = null
            null
        }
    }

    fun decoderName(trackType: Int): String? {
        val field = codecInfoField ?: return null
        return try {
            for (i in 0 until exoPlayer.rendererCount) {
                if (exoPlayer.getRendererType(i) != trackType) continue
                val renderer = exoPlayer.getRenderer(i)
                if (renderer !is MediaCodecRenderer) continue
                val info = field.get(renderer) as? MediaCodecInfo ?: continue
                return info.name
            }
            null
        } catch (_: Exception) {
            codecInfoField = null
            null
        }
    }

    private fun findField(clazz: Class<*>, name: String): Field? {
        var current: Class<*>? = clazz
        while (current != null) {
            try {
                return current.getDeclaredField(name).apply { isAccessible = true }
            } catch (_: NoSuchFieldException) {
                current = current.superclass
            }
        }
        return null
    }
}

@OptIn(UnstableApi::class)
@Composable
actual fun rememberPlayerStatsState(player: MediampPlayer): State<PlayerStatsSnapshot?> {
    return produceState<PlayerStatsSnapshot?>(initialValue = null, player) {
        val reflectionStats = (player.impl as? ExoPlayer)?.let { ExoPlayerReflectionStats(it) }
        while (true) {
            value = runCatching { player.readAndroidPlayerStats(reflectionStats) }
                .getOrElse { player.readFallbackPlayerStats("ExoPlayer") }
            delay(1.seconds)
        }
    }
}

@OptIn(UnstableApi::class)
private fun MediampPlayer.readAndroidPlayerStats(reflectionStats: ExoPlayerReflectionStats?): PlayerStatsSnapshot {
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
        videoCodec = readableCodecWithDecoder(
            videoFormat?.readableCodecName(),
            reflectionStats?.decoderName(C.TRACK_TYPE_VIDEO),
        ),
        videoBitrate = videoFormat?.readableBitrate(),
        audioCodec = readableCodecWithDecoder(
            audioFormat?.readableCodecName(),
            reflectionStats?.decoderName(C.TRACK_TYPE_AUDIO),
        ),
        audioBitrate = audioFormat?.readableBitrate(),
        audioSampleRate = audioFormat?.sampleRate?.validMedia3Value(),
        audioChannels = audioFormat?.channelCount?.validMedia3Value(),
        realtimeInputBitrate = reflectionStats?.bandwidthEstimate(),
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
