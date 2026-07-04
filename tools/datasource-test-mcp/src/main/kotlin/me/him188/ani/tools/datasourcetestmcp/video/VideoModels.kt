/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.tools.datasourcetestmcp.video

import kotlinx.serialization.Serializable

@Serializable
data class VideoProbeResult(
    val ok: Boolean,
    val url: String,
    val finalUrl: String? = null,
    val kind: String,
    val statusCode: Int? = null,
    val contentType: String? = null,
    val headers: Map<String, String> = emptyMap(),
    val summary: String,
    val playlistEntries: Int? = null,
    val nestedPlaylistUrl: String? = null,
    val sampledSegmentUrl: String? = null,
    val sampledSegmentStatusCode: Int? = null,
    val errors: List<String> = emptyList(),
)

@Serializable
data class ProbeVideoInput(
    val videoUrl: String,
    val headers: Map<String, String> = emptyMap(),
    val probeTimeoutMillis: Long = 15_000,
    /**
     * 是否用 Animeko 播放器 (VLC) 真实播放并读取媒体信息
     */
    val analyze: Boolean = true,
    /**
     * 真实播放多少秒来验证可播放性
     */
    val playSeconds: Int = 5,
    /**
     * 等待进入播放状态 / 播放完成的超时
     */
    val playTimeoutMillis: Long = 60_000,
    /**
     * 是否弹出 Compose 测试窗口实时显示播放画面
     */
    val showWindow: Boolean = true,
)

@Serializable
data class ProbeVideoResult(
    val ok: Boolean,
    val summary: String,
    val httpProbe: VideoProbeResult,
    val mediaAnalysis: MediaAnalysisResult? = null,
    val errors: List<String> = emptyList(),
)

@Serializable
data class MediaAnalysisResult(
    /**
     * 播放器 (VLC 原生库) 是否可用. 不可用时其余字段为空, 只有 HTTP 探测结果.
     */
    val available: Boolean,
    val tool: String? = null,
    val durationSeconds: Double? = null,
    /**
     * 整体码率, bits per second (来自 VLC demux 统计)
     */
    val overallBitrate: Long? = null,
    val video: VideoStreamInfo? = null,
    val audio: AudioStreamInfo? = null,
    /**
     * 真实播放测试: 是否成功进入播放状态并播完目标秒数
     */
    val playback: PlaybackTestResult? = null,
    val errors: List<String> = emptyList(),
)

@Serializable
data class VideoStreamInfo(
    val codec: String? = null,
    val width: Int? = null,
    val height: Int? = null,
    val frameRate: String? = null,
    val bitrate: Long? = null,
)

@Serializable
data class AudioStreamInfo(
    val codec: String? = null,
    val sampleRate: String? = null,
    val channels: Int? = null,
    val bitrate: Long? = null,
)

@Serializable
data class PlaybackTestResult(
    val ran: Boolean,
    val ok: Boolean,
    val requestedSeconds: Int? = null,
    /**
     * 实际播放到的位置 (毫秒)
     */
    val playedPositionMillis: Long? = null,
    /**
     * 结束时的播放器状态, 例如 PLAYING / ERROR
     */
    val finalState: String? = null,
    val errors: List<String> = emptyList(),
)
