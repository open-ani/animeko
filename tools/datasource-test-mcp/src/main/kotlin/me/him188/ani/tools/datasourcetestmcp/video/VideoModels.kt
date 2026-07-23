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
    val durationMillis: Long? = null,
)

@Serializable
data class ProbeVideoInput(
    val videoUrl: String,
    val headers: Map<String, String> = emptyMap(),
    val probeTimeoutMillis: Long = 15_000,
    /**
     * 是否用 Animeko 播放器 (mpv) 真实播放并读取媒体信息
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
    /**
     * 是否分析 HLS 播放列表判断是否含广告 (前贴片拼接等)
     */
    val detectAds: Boolean = true,
    /**
     * 非空则把首帧/中段帧截图 (PNG) 存到该目录, 用于视觉判断广告
     */
    val captureFramesDir: String? = null,
    /**
     * 额外在这些播放位置 (秒) 各截一帧, 文件名 frame_XXs.png.
     * 需要 playSeconds 不小于其中最大值, 否则播放会提前结束截不到.
     */
    val captureAtSeconds: List<Int> = emptyList(),
)

@Serializable
data class ProbeVideoResult(
    val ok: Boolean,
    val summary: String,
    val httpProbe: VideoProbeResult,
    val mediaAnalysis: MediaAnalysisResult? = null,
    val adAnalysis: AdAnalysisResult? = null,
    /**
     * 截图文件路径 (PNG). 首帧常为广告/logo, 可读图人工判断.
     */
    val capturedFrames: List<CapturedFrame> = emptyList(),
    val errors: List<String> = emptyList(),
    val totalDurationMillis: Long? = null,
)

/**
 * 基于 HLS 播放列表结构的广告启发式判断. 视频广告 (前贴片) 拼接进 m3u8 时通常带
 * [EXT-X-DISCONTINUITY][discontinuityCount] 标记, 且广告分片时长/来源域名异于正片.
 */
@Serializable
data class AdAnalysisResult(
    /**
     * `none` / `suspected_low` / `suspected_medium` / `suspected_high` / `unknown`
     */
    val suspicion: String,
    val reasons: List<String> = emptyList(),
    /**
     * m3u8 才有的结构信号 (非 m3u8 时为 null)
     */
    val playlist: PlaylistAdSignals? = null,
    /**
     * Ani 客户端真实 HLS 广告过滤器 ([me.him188.ani.app.domain.media.hls.HlsManifestFilter]) 的分析结果.
     * 仅当拿到 media playlist 时非 null.
     */
    val hlsFilter: HlsFilterAnalysis? = null,
)

@Serializable
data class HlsFilterAnalysis(
    /** filtered / unchanged / unsupported (小写) */
    val status: String,
    /** status != filtered 时的原因 id, 如 no_discontinuity / master_playlist */
    val reason: String? = null,
    /** true = Ani 客户端的 HLS 广告过滤器会自动滤除 removedGroups 里的片段 */
    val filterable: Boolean,
    val mediaPlaylistUrl: String? = null,
    val removedGroups: List<RemovedAdGroup> = emptyList(),
)

@Serializable
data class RemovedAdGroup(
    /** 命中启发式: strong_path / repeat_short / sandwiched_short / low_density_short / sequence_island / dense_tiny */
    val reasons: List<String>,
    val segmentCount: Int,
    val durationSeconds: Double,
    /** 该组在整条视频里的起止时间偏移 (秒), 可用于定点截帧确认 */
    val startOffsetSeconds: Double,
    val endOffsetSeconds: Double,
    val startSegmentIndex: Int,
    val endSegmentIndex: Int,
    val firstSegmentUri: String? = null,
)

@Serializable
data class DetectHlsAdsInput(
    val url: String,
    val headers: Map<String, String> = emptyMap(),
)

@Serializable
data class DetectHlsAdsResult(
    val ok: Boolean,
    val url: String,
    /** 一句话人话总结: suspicion + 过滤器结论 + 各组时间范围 */
    val summary: String,
    val analysis: AdAnalysisResult,
    val errors: List<String> = emptyList(),
)

@Serializable
data class PlaylistAdSignals(
    val segmentCount: Int,
    /**
     * `#EXT-X-DISCONTINUITY` 数量. >0 强烈提示存在广告/多段拼接.
     */
    val discontinuityCount: Int,
    /**
     * 分片来源的不同主机数. 广告分片常来自独立 CDN.
     */
    val distinctSegmentHosts: List<String> = emptyList(),
    /**
     * 开头若干分片的时长 (秒), 用于识别短前贴片
     */
    val leadingSegmentDurations: List<Double> = emptyList(),
    val medianSegmentDuration: Double? = null,
)

@Serializable
data class CapturedFrame(
    /**
     * 截图时的播放位置 (毫秒)
     */
    val positionMillis: Long,
    val path: String,
    /**
     * `first_frame` (进入播放后首帧, 疑似广告) / `mid` (播放 N 秒后)
     */
    val label: String,
)

@Serializable
data class MediaAnalysisResult(
    /**
     * 播放器 (mpv 原生库) 是否可用. 不可用时其余字段为空, 只有 HTTP 探测结果.
     */
    val available: Boolean,
    val tool: String? = null,
    val durationSeconds: Double? = null,
    /**
     * 整体码率, bits per second (来自 mpv 属性 (video-bitrate + audio-bitrate))
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
    /**
     * setMediaData (打开媒体) 耗时
     */
    val openMillis: Long? = null,
    /**
     * resume 到播放位置首次前进的耗时 (真实起播耗时, 含起播缓冲).
     * mpv 的 PLAYING 状态在 resume 后同步翻转、不含缓冲, 故不用状态而用位置前进作为起播判据.
     */
    val timeToPlayingMillis: Long? = null,
    /**
     * resume 到播放位置首次前进的耗时 (约等于首帧时间, 与 [timeToPlayingMillis] 同源)
     */
    val timeToFirstFrameMillis: Long? = null,
    /**
     * 播放 [requestedSeconds] 秒实际花费的墙钟时间. 明显大于目标秒数说明播放期间有卡顿.
     */
    val playWallClockMillis: Long? = null,
    /**
     * 播放期间 (进入 PLAYING 后) 进入缓冲状态的次数
     */
    val bufferingCount: Int = 0,
    /**
     * 播放期间缓冲总时长
     */
    val bufferingTotalMillis: Long = 0,
)
