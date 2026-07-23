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
 * 视频能力: HTTP 可达性探测 + Animeko 播放器 (mpv) 真实播放测试.
 */
class VideoService(
    private val probe: VideoUrlProbeEngine,
    private val analyzer: MpvVideoAnalyzer,
    private val adAnalyzer: M3u8AdAnalyzer,
) {
    /**
     * 不播放, 仅抓取并分析 HLS 播放列表结构判断是否含插入广告 (detect_hls_ads 工具).
     */
    suspend fun detectHlsAds(input: DetectHlsAdsInput): DetectHlsAdsResult {
        val analysis = adAnalyzer.analyze(input.url, input.headers, assumeHls = true)
        val ok = analysis.suspicion != "unknown" || analysis.hlsFilter != null
        return DetectHlsAdsResult(
            ok = ok,
            url = input.url,
            summary = buildDetectHlsAdsSummary(analysis),
            analysis = analysis,
            errors = if (ok) emptyList() else analysis.reasons,
        )
    }

    private fun buildDetectHlsAdsSummary(analysis: AdAnalysisResult): String {
        val filter = analysis.hlsFilter
        if (filter != null && filter.filterable) {
            val ranges = filter.removedGroups.joinToString(", ") {
                "%.1f-%.1fs".format(it.startOffsetSeconds, it.endOffsetSeconds)
            }
            return "疑似插入广告: ${filter.removedGroups.size} 组 ($ranges), Ani HLS 过滤器可自动滤除 (filtered)"
        }
        return when (analysis.suspicion) {
            "unknown" -> "无法分析: ${analysis.reasons.firstOrNull() ?: "未知原因"}"
            "none" -> "未见明显插入广告拼接; 注意: 烧录进画面的水印广告需截帧目视确认"
            else -> buildString {
                append("结构疑似广告拼接 (${analysis.suspicion})")
                when (filter?.status) {
                    "unchanged" -> append(", 但 Ani HLS 过滤器未滤除 (unchanged: ${filter.reason})")
                    "unsupported" -> append(", Ani HLS 过滤器不支持此播放列表 (unsupported: ${filter.reason})")
                    else -> {}
                }
                append("; 建议用 probe_video captureAtSeconds 截帧目视确认")
            }
        }
    }

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
            return httpProbe.summary + " (mpv 不可用, 仅 HTTP 探测)"
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
