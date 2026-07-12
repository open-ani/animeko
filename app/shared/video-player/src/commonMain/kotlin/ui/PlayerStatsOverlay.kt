/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.videoplayer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import me.him188.ani.app.ui.lang.*
import org.jetbrains.compose.resources.stringResource
import org.openani.mediamp.MediampPlayer
import kotlin.math.roundToInt

@Immutable
data class PlayerStatsSnapshot(
    val backend: String,
    val playbackState: String,
    val title: String?,
    val positionMillis: Long,
    val durationMillis: Long?,
    val playbackSpeed: Float?,
    val resolution: String?,
    val frameRate: Float?,
    val videoCodec: String?,
    val videoBitrate: Long?,
    val audioCodec: String?,
    val audioBitrate: Long?,
    val audioSampleRate: Int?,
    val audioChannels: Int?,
    val realtimeInputBitrate: Long?,
    val realtimeDemuxBitrate: Long?,
    val decodedVideoFrames: Long?,
    val decodedAudioFrames: Long?,
    val droppedVideoFrames: Long?,
    val droppedAudioBuffers: Long?,
)

@Composable
expect fun rememberPlayerStatsState(player: MediampPlayer): State<PlayerStatsSnapshot?>

@Composable
fun PlayerStatsOverlay(
    stats: PlayerStatsSnapshot?,
    modifier: Modifier = Modifier,
) {
    if (stats == null) return

    Surface(
        modifier,
        color = Color.Black.copy(alpha = 0.72f),
        contentColor = Color.White,
        shape = RoundedCornerShape(10.dp),
        shadowElevation = 4.dp,
    ) {
        Column(
            Modifier
                .background(Color.Transparent)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                "${stringResource(Lang.video_player_stats_title)}  ${stringResource(Lang.video_player_stats_hide_hint)}",
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.Monospace,
                ),
                color = Color.White,
            )
            PlayerStatsRow(stringResource(Lang.video_player_stats_backend), stats.backend)
            PlayerStatsRow(stringResource(Lang.video_player_stats_state), stats.playbackState)
            stats.title?.takeIf { it.isNotBlank() }
                ?.let { PlayerStatsRow(stringResource(Lang.video_player_stats_media_title), it) }
            PlayerStatsRow(
                stringResource(Lang.video_player_stats_progress),
                "${formatDuration(stats.positionMillis)} / ${formatDuration(stats.durationMillis)}",
            )
            stats.resolution?.let { PlayerStatsRow(stringResource(Lang.video_player_stats_resolution), it) }
            stats.frameRate?.let {
                PlayerStatsRow(stringResource(Lang.video_player_stats_frame_rate), "${formatDecimal(it)} fps")
            }
            stats.videoCodec?.let { PlayerStatsRow(stringResource(Lang.video_player_stats_video_codec), it) }
            formatBitrate(stats.videoBitrate)?.let {
                PlayerStatsRow(stringResource(Lang.video_player_stats_video_bitrate), it)
            }
            stats.audioCodec?.let { PlayerStatsRow(stringResource(Lang.video_player_stats_audio_codec), it) }
            formatBitrate(stats.audioBitrate)?.let {
                PlayerStatsRow(stringResource(Lang.video_player_stats_audio_bitrate), it)
            }
            listOfNotNull(
                stats.audioSampleRate?.takeIf { it > 0 }?.let { "${it} Hz" },
                stats.audioChannels?.takeIf { it > 0 }?.let { "${it} ch" },
            ).joinToString(" / ").takeIf { it.isNotBlank() }?.let {
                PlayerStatsRow(stringResource(Lang.video_player_stats_audio_format), it)
            }
            stats.playbackSpeed?.let {
                PlayerStatsRow(stringResource(Lang.video_player_stats_playback_speed), "${formatDecimal(it)}x")
            }
            formatBitrate(stats.realtimeInputBitrate)?.let {
                PlayerStatsRow(stringResource(Lang.video_player_stats_realtime_input), it)
            }
            formatBitrate(stats.realtimeDemuxBitrate)?.let {
                PlayerStatsRow(stringResource(Lang.video_player_stats_realtime_demux), it)
            }
            listOfNotNull(
                stats.decodedVideoFrames?.let { "V $it" },
                stats.decodedAudioFrames?.let { "A $it" },
                stats.droppedVideoFrames?.takeIf { it > 0 }
                    ?.let { stringResource(Lang.video_player_stats_dropped_video_frames, it.toString()) },
                stats.droppedAudioBuffers?.takeIf { it > 0 }
                    ?.let { stringResource(Lang.video_player_stats_dropped_audio_buffers, it.toString()) },
            ).joinToString(" / ").takeIf { it.isNotBlank() }?.let {
                PlayerStatsRow(stringResource(Lang.video_player_stats_decode_stats), it)
            }
        }
    }
}

@Composable
private fun PlayerStatsRow(
    label: String,
    value: String,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            color = Color.White.copy(alpha = 0.72f),
        )
        Text(
            value,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            color = Color.White,
        )
    }
}

private fun formatDuration(millis: Long?): String {
    if (millis == null || millis < 0) return "--:--"
    val totalSeconds = millis / 1000
    val seconds = totalSeconds % 60
    val minutes = (totalSeconds / 60) % 60
    val hours = totalSeconds / 3600
    return if (hours > 0) {
        "${hours}:${minutes.toPadded2()}:${seconds.toPadded2()}"
    } else {
        "${minutes}:${seconds.toPadded2()}"
    }
}

private fun Long.toPadded2(): String = if (this < 10) "0$this" else toString()

private fun formatBitrate(bitsPerSecond: Long?): String? {
    if (bitsPerSecond == null || bitsPerSecond <= 0) return null
    return if (bitsPerSecond >= 1_000_000) {
        "${formatDecimal(bitsPerSecond / 1_000_000f)} Mbps"
    } else {
        "${(bitsPerSecond / 1000f).roundToInt()} kbps"
    }
}

private fun formatDecimal(value: Float): String {
    val rounded = (value * 100).roundToInt() / 100f
    val text = rounded.toString()
    return if (text.endsWith(".0")) text.dropLast(2) else text
}
