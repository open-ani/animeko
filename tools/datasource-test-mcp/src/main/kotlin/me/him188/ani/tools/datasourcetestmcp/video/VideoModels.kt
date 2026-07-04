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
     * 是否用 ffprobe 分析容器格式/码率/分辨率/时长
     */
    val analyze: Boolean = true,
    /**
     * 是否用 ffmpeg 实际解码几秒以验证可播放性
     */
    val decodeTest: Boolean = true,
    val decodeDurationSeconds: Int = 5,
    val analyzeTimeoutMillis: Long = 60_000,
    /**
     * ffprobe 可执行文件路径. 默认从 ANI_MCP_FFPROBE 环境变量或 PATH 查找.
     */
    val ffprobePath: String? = null,
    val ffmpegPath: String? = null,
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
     * ffprobe / ffmpeg 是否可用. 不可用时其余字段为空, 只有 HTTP 探测结果.
     */
    val available: Boolean,
    val tool: String? = null,
    val containerFormat: String? = null,
    val durationSeconds: Double? = null,
    /**
     * 整体码率, bits per second
     */
    val overallBitrate: Long? = null,
    val video: VideoStreamInfo? = null,
    val audio: AudioStreamInfo? = null,
    val decodeTest: DecodeTestResult? = null,
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
data class DecodeTestResult(
    val ran: Boolean,
    val ok: Boolean,
    val requestedSeconds: Int? = null,
    val errors: List<String> = emptyList(),
)
