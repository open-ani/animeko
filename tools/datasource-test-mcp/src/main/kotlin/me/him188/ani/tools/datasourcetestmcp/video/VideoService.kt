/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.tools.datasourcetestmcp.video

import kotlinx.coroutines.withTimeout

/**
 * 视频能力: HTTP 可达性探测 + ffprobe/ffmpeg 真实解码分析.
 */
class VideoService(
    private val probe: VideoUrlProbeEngine,
    private val analyzer: FfmpegVideoAnalyzer,
) {
    suspend fun probeVideo(input: ProbeVideoInput): ProbeVideoResult {
        val httpProbe = runCatching {
            withTimeout(input.probeTimeoutMillis) {
                probe.probe(input.videoUrl, input.headers)
            }
        }.getOrElse { exception ->
            VideoProbeResult(
                ok = false,
                url = input.videoUrl,
                kind = "unknown",
                summary = "HTTP probe failed",
                errors = listOf("${exception::class.simpleName}: ${exception.message.orEmpty()}"),
            )
        }

        val analysis = if (input.analyze) analyzer.analyze(input) else null

        // 解码测试是最接近真实播放的验证; 其次是 ffprobe 能识别出视频流; 都没有时退回 HTTP 探测结论
        val ok = when {
            analysis?.decodeTest?.ran == true -> analysis.decodeTest.ok
            analysis?.available == true && analysis.video != null -> true
            else -> httpProbe.ok
        }

        return ProbeVideoResult(
            ok = ok,
            summary = buildSummary(ok, httpProbe, analysis),
            httpProbe = httpProbe,
            mediaAnalysis = analysis,
            errors = httpProbe.errors + analysis?.errors.orEmpty() + analysis?.decodeTest?.errors.orEmpty(),
        )
    }

    private fun buildSummary(ok: Boolean, httpProbe: VideoProbeResult, analysis: MediaAnalysisResult?): String {
        if (analysis == null || !analysis.available) {
            val suffix = if (analysis != null) " (未找到 ffprobe, 仅 HTTP 探测)" else ""
            return httpProbe.summary + suffix
        }
        if (analysis.video == null && analysis.errors.isNotEmpty()) {
            // ffprobe 失败但 HTTP 可达: 常见于分片用伪装扩展名 (如 .jpeg 防盗链), 播放器通常仍可播.
            // 不能笼统说"分析失败", 否则会与 fallback 得到的 ok=true 矛盾.
            return if (httpProbe.ok) {
                "HTTP 可达 (${httpProbe.kind}), 但 ffprobe 无法分析: ${analysis.errors.first()}"
            } else {
                "不可播放, 媒体分析失败: ${analysis.errors.first()}"
            }
        }
        return buildString {
            append(if (ok) "可播放" else "不可播放")
            analysis.containerFormat?.let { append(": ").append(it) }
            analysis.video?.let { video ->
                append(", ").append(video.codec ?: "?")
                if (video.width != null && video.height != null) {
                    append(" ${video.width}x${video.height}")
                }
            }
            analysis.durationSeconds?.let { seconds ->
                append(", ").append(formatDuration(seconds))
            }
            // HLS playlist 的 format.bit_rate 是播放列表文件自身的码率, 无意义; 优先用视频流码率并过滤噪声值
            val bitrate = analysis.video?.bitrate ?: analysis.overallBitrate
            bitrate?.takeIf { it >= 10_000 }?.let {
                append(", ").append(formatBitrate(it))
            }
            analysis.decodeTest?.takeIf { it.ran }?.let { test ->
                append(if (test.ok) ", 解码测试通过" else ", 解码测试失败")
            }
        }
    }

    private fun formatDuration(seconds: Double): String {
        val total = seconds.toLong()
        val hours = total / 3600
        val minutes = (total % 3600) / 60
        val secs = total % 60
        return if (hours > 0) {
            "%d:%02d:%02d".format(hours, minutes, secs)
        } else {
            "%d:%02d".format(minutes, secs)
        }
    }

    private fun formatBitrate(bitsPerSecond: Long): String {
        return when {
            bitsPerSecond >= 1_000_000 -> "%.1f Mbps".format(bitsPerSecond / 1_000_000.0)
            bitsPerSecond >= 1_000 -> "%.0f Kbps".format(bitsPerSecond / 1_000.0)
            else -> "$bitsPerSecond bps"
        }
    }
}
