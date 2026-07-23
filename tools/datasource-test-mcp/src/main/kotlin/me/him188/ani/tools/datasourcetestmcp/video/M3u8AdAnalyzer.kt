/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.tools.datasourcetestmcp.video

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Url
import me.him188.ani.app.domain.media.hls.HlsManifestFilter
import me.him188.ani.app.domain.media.hls.HlsManifestFilterStatus
import me.him188.ani.utils.httpdownloader.m3u.DefaultM3u8Parser
import me.him188.ani.utils.httpdownloader.m3u.M3u8Playlist
import me.him188.ani.utils.ktor.UrlHelpers
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * 基于 HLS 播放列表结构的广告启发式分析.
 *
 * 视频源的前贴片广告通常把广告分片拼进 m3u8, 技术特征:
 * - `#EXT-X-DISCONTINUITY`: 广告与正片编码参数不同, 拼接处必打此标记 (最强信号);
 * - 开头若干分片时长明显短于正片主体 (广告常为 5/10/15s 定长);
 * - 广告分片来自独立 CDN, 与正片分片主机不同.
 *
 * 此外还会在最终 media playlist 上运行 Ani 客户端真实的 HLS 广告过滤器
 * ([HlsManifestFilter]), 报告 App 是否会自动滤除这些片段, 以及各组广告的时间偏移.
 */
class M3u8AdAnalyzer(
    private val httpClient: HttpClient,
) {
    /**
     * @param assumeHls true 时跳过 URL 扩展名检查, 直接按 HLS 播放列表分析 (用于 detect_hls_ads)
     */
    suspend fun analyze(url: String, headers: Map<String, String>, assumeHls: Boolean = false): AdAnalysisResult {
        val lower = url.lowercase()
        val isM3u8 = lower.contains(".m3u8") || lower.contains("mpegurl")
        if (!isM3u8 && !assumeHls) {
            return AdAnalysisResult(
                suspicion = "unknown",
                reasons = listOf("非 HLS 播放列表, 无法从结构判断广告 (可看首帧截图)"),
            )
        }
        return runCatching {
            analyzePlaylist(url, headers, depth = 0)
        }.getOrElse { e ->
            AdAnalysisResult(
                suspicion = "unknown",
                reasons = listOf("播放列表分析失败: ${e::class.simpleName}: ${e.message.orEmpty()}"),
            )
        }
    }

    private suspend fun analyzePlaylist(url: String, headers: Map<String, String>, depth: Int): AdAnalysisResult {
        val text = fetch(url, headers)
        val lines = text.lines().map { it.trim() }

        // master playlist: 跟到第一个 media playlist (最多一层)
        if (lines.any { it.startsWith("#EXT-X-STREAM-INF") } &&
            lines.none { it.startsWith("#EXTINF") }
        ) {
            if (depth >= 2) {
                return AdAnalysisResult(suspicion = "unknown", reasons = listOf("master playlist 嵌套过深"))
            }
            val variant = lines.firstOrNull { it.isNotEmpty() && !it.startsWith("#") }
                ?: return AdAnalysisResult(suspicion = "unknown", reasons = listOf("master playlist 无可用变体"))
            return analyzePlaylist(UrlHelpers.computeAbsoluteUrl(url, variant), headers, depth + 1)
        }

        return analyzeMediaPlaylist(url = url, text = text, lines = lines)
    }

    private fun analyzeMediaPlaylist(url: String, text: String, lines: List<String>): AdAnalysisResult {
        var discontinuityCount = 0
        val segmentDurations = mutableListOf<Double>()
        val segmentHosts = linkedSetOf<String>()
        var pendingDuration: Double? = null

        for (line in lines) {
            when {
                line.startsWith("#EXT-X-DISCONTINUITY") && !line.startsWith("#EXT-X-DISCONTINUITY-SEQUENCE") -> {
                    discontinuityCount++
                }

                line.startsWith("#EXTINF:") -> {
                    pendingDuration = line.removePrefix("#EXTINF:")
                        .substringBefore(',')
                        .trim()
                        .toDoubleOrNull()
                }

                line.isNotEmpty() && !line.startsWith("#") -> {
                    pendingDuration?.let { segmentDurations += it }
                    pendingDuration = null
                    hostOf(UrlHelpers.computeAbsoluteUrl(url, line))?.let { segmentHosts += it }
                }
            }
        }

        val playlistHost = hostOf(url)
        val signals = PlaylistAdSignals(
            segmentCount = segmentDurations.size,
            discontinuityCount = discontinuityCount,
            distinctSegmentHosts = segmentHosts.toList(),
            leadingSegmentDurations = segmentDurations.take(5),
            medianSegmentDuration = segmentDurations.median(),
        )
        val filterErrors = mutableListOf<String>()
        val hlsFilter = runClientFilter(text = text, url = url, errors = filterErrors)
        return score(signals, playlistHost, hlsFilter, filterErrors)
    }

    /**
     * 在 media playlist 上运行 Ani 客户端真实的 HLS 广告过滤器, 并从原始 (未过滤的)
     * 播放列表计算各被滤除组的时间偏移.
     */
    private fun runClientFilter(text: String, url: String, errors: MutableList<String>): HlsFilterAnalysis? =
        runCatching {
            val result = HlsManifestFilter.filter(text, url)
            val removedGroups = if (result.removedGroups.isEmpty()) {
                emptyList()
            } else {
                val segments = (DefaultM3u8Parser.parse(text, url) as? M3u8Playlist.MediaPlaylist)
                    ?.segments.orEmpty()
                // startOffsets[i] = segments[0 until i] 的总时长
                val startOffsets = DoubleArray(segments.size + 1)
                segments.forEachIndexed { i, segment ->
                    startOffsets[i + 1] = startOffsets[i] + segment.duration.toDouble()
                }
                result.removedGroups.map { group ->
                    val start = startOffsets.getOrElse(group.startSegmentIndex) { 0.0 }
                    RemovedAdGroup(
                        reasons = group.reasons,
                        segmentCount = group.segmentCount,
                        durationSeconds = group.duration.round3(),
                        startOffsetSeconds = start.round3(),
                        endOffsetSeconds = (start + group.duration).round3(),
                        startSegmentIndex = group.startSegmentIndex,
                        endSegmentIndex = group.endSegmentIndex,
                        firstSegmentUri = segments.getOrNull(group.startSegmentIndex)?.uri,
                    )
                }
            }
            HlsFilterAnalysis(
                status = result.status.name.lowercase(),
                reason = result.reason,
                filterable = result.status == HlsManifestFilterStatus.Filtered,
                mediaPlaylistUrl = url,
                removedGroups = removedGroups,
            )
        }.getOrElse { e ->
            errors += "Ani HLS 广告过滤器分析失败: ${e::class.simpleName}: ${e.message.orEmpty()}"
            null
        }

    private fun score(
        signals: PlaylistAdSignals,
        playlistHost: String?,
        hlsFilter: HlsFilterAnalysis?,
        extraReasons: List<String>,
    ): AdAnalysisResult {
        val reasons = mutableListOf<String>()
        var score = 0

        if (hlsFilter != null && hlsFilter.filterable) {
            score += 2
            val totalSeconds = hlsFilter.removedGroups.sumOf { it.durationSeconds }
            reasons += "Ani HLS 广告过滤器识别出 ${hlsFilter.removedGroups.size} 组疑似插入广告片段 " +
                    "(共 ${"%.1f".format(totalSeconds)}s, 可自动滤除)"
        }

        if (signals.discontinuityCount > 0) {
            score += 2
            reasons += "播放列表含 ${signals.discontinuityCount} 处 EXT-X-DISCONTINUITY (广告/多段拼接标志)"
        }

        // 广告分片来自异于播放列表/正片的独立主机
        val foreignHosts = signals.distinctSegmentHosts.filter { host ->
            playlistHost != null && !sameSite(host, playlistHost)
        }
        if (signals.distinctSegmentHosts.size > 1) {
            score += 1
            reasons += "分片来自 ${signals.distinctSegmentHosts.size} 个不同主机: ${signals.distinctSegmentHosts.joinToString()}"
        } else if (foreignHosts.isNotEmpty()) {
            score += 1
            reasons += "分片主机 (${foreignHosts.joinToString()}) 与播放列表 ($playlistHost) 不同站点"
        }

        // 开头分片明显短于主体 -> 疑似短前贴片
        val median = signals.medianSegmentDuration
        if (median != null && median > 0 && signals.leadingSegmentDurations.isNotEmpty()) {
            val lead = signals.leadingSegmentDurations.first()
            if (lead > 0 && abs(lead - median) / median > 0.5 && lead < median) {
                score += 1
                reasons += "首个分片时长 ${lead}s 明显短于分片中位数 ${median}s (疑似前贴片)"
            }
        }

        val suspicion = when {
            score >= 3 -> "suspected_high"
            score == 2 -> "suspected_medium"
            score == 1 -> "suspected_low"
            else -> "none"
        }
        if (suspicion == "none") {
            reasons += "播放列表结构均匀 (无 discontinuity, 单一分片来源), 未见明显广告拼接"
        }
        reasons += extraReasons
        return AdAnalysisResult(suspicion = suspicion, reasons = reasons, playlist = signals, hlsFilter = hlsFilter)
    }

    private suspend fun fetch(url: String, headers: Map<String, String>): String {
        return httpClient.get(url) {
            headers.forEach { (k, v) -> header(k, v) }
        }.bodyAsText()
    }

    private fun hostOf(url: String): String? = runCatching { Url(url).host }.getOrNull()?.lowercase()

    /** 忽略二级子域, 比较主域是否相同 (如 v14.rstu6.com 与 v9.rstu6.com 视为同站) */
    private fun sameSite(a: String, b: String): Boolean {
        fun root(h: String) = h.split('.').takeLast(2).joinToString(".")
        return root(a) == root(b)
    }

    private fun List<Double>.median(): Double? {
        if (isEmpty()) return null
        val sorted = sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 0) (sorted[mid - 1] + sorted[mid]) / 2 else sorted[mid]
    }

    /** 消除 Float 累加噪声, 保留 3 位小数 */
    private fun Double.round3(): Double = (this * 1000).roundToLong() / 1000.0
}
