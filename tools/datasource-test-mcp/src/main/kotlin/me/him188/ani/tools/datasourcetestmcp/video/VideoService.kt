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
 * 视频能力: HTTP 可达性探测 + Animeko 播放器 (VLC) 真实播放测试.
 */
class VideoService(
    private val probe: VideoUrlProbeEngine,
    private val analyzer: MpvVideoAnalyzer,
    private val adAnalyzer: M3u8AdAnalyzer,
) {
    suspend fun probeVideo(input: ProbeVideoInput): ProbeVideoResult {
        val totalStart = System.currentTimeMillis()
        val httpProbeStart = System.currentTimeMillis()
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
        }.copy(durationMillis = System.currentTimeMillis() - httpProbeStart)

        val adAnalysis = if (input.detectAds) {
            runCatching { adAnalyzer.analyze(input.videoUrl, input.headers) }.getOrNull()
        } else {
            null
        }

        val output = if (input.analyze) analyzer.analyze(input) else null
        val analysis = output?.analysis

        // 真实播放测试是最权威的结论; 播放器不可用时退回 HTTP 探测结论
        val ok = when {
            analysis?.playback?.ran == true -> analysis.playback.ok
            else -> httpProbe.ok
        }

        return ProbeVideoResult(
            ok = ok,
            summary = buildSummary(ok, httpProbe, analysis, adAnalysis),
            httpProbe = httpProbe,
            mediaAnalysis = analysis,
            adAnalysis = adAnalysis,
            capturedFrames = output?.frames.orEmpty(),
            errors = httpProbe.errors + analysis?.errors.orEmpty() + analysis?.playback?.errors.orEmpty(),
            totalDurationMillis = System.currentTimeMillis() - totalStart,
        )
    }

    private fun buildSummary(
        ok: Boolean,
        httpProbe: VideoProbeResult,
        analysis: MediaAnalysisResult?,
        adAnalysis: AdAnalysisResult?,
    ): String {
        val adSuffix = when (adAnalysis?.suspicion) {
            "suspected_high" -> ", 疑似含广告(高)"
            "suspected_medium" -> ", 疑似含广告(中)"
            "suspected_low" -> ", 疑似含广告(低)"
            else -> ""
        }
        return baseSummary(ok, httpProbe, analysis) + adSuffix
    }

    private fun baseSummary(ok: Boolean, httpProbe: VideoProbeResult, analysis: MediaAnalysisResult?): String {
        if (analysis == null) {
            return httpProbe.summary
        }
        if (!analysis.available) {
            return httpProbe.summary + " (VLC 不可用, 仅 HTTP 探测)"
        }
        if (analysis.video == null && analysis.playback?.ok != true) {
            val reason = analysis.playback?.errors?.firstOrNull()
                ?: analysis.errors.firstOrNull()
                ?: "未知原因"
            return "播放器无法播放: $reason"
        }
        return buildString {
            append(if (ok) "可播放 (Animeko 播放器实测)" else "不可播放")
            append(": ").append(httpProbe.kind)
            analysis.video?.let { video ->
                append(", ").append(video.codec ?: "?")
                if (video.width != null && video.height != null) {
                    append(" ${video.width}x${video.height}")
                }
                video.frameRate?.let { append(" @${it}fps") }
            }
            analysis.durationSeconds?.let { seconds ->
                append(", ").append(formatDuration(seconds))
            }
            val bitrate = analysis.video?.bitrate ?: analysis.overallBitrate
            bitrate?.takeIf { it >= 10_000 }?.let {
                append(", ").append(formatBitrate(it))
            }
            analysis.playback?.takeIf { it.ran }?.let { test ->
                append(
                    if (test.ok) {
                        ", 已实际播放 ${test.requestedSeconds}s"
                    } else {
                        ", 播放测试失败 (state=${test.finalState})"
                    },
                )
                test.timeToPlayingMillis?.let { append(", 起播 ${it}ms") }
                if (test.bufferingCount > 0) {
                    append(", 卡顿 ${test.bufferingCount} 次共 ${test.bufferingTotalMillis}ms")
                }
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
