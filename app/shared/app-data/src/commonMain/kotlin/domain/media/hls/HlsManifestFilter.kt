/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.media.hls

import me.him188.ani.utils.httpdownloader.m3u.DefaultM3u8Parser
import me.him188.ani.utils.httpdownloader.m3u.M3u8Playlist
import kotlin.math.roundToLong

/**
 * 从播放列表中移除插播广告.
 *
 * 唯一的判据是时间戳连续性 ([HlsPtsContinuity]): 广告是独立转码后拼进来的, PTS 自成一条时间轴,
 * 接不上正片. 按组的时长、分片数或文件名猜测都会误删正片: 广告插入点常把正片镜头切出几秒的残片,
 * 正片里也有与广告等长的短镜头段.
 */
object HlsManifestFilter {
    /**
     * 一段连续的链外组超过这个时长就不删. 实测插播 15.8-25.7 秒; 正片也可能分几段独立编码
     * (例如单独转码的片头), 它们同样自成时间轴, 但通常远长于插播. 这个上限只阻止删除, 不产生删除.
     */
    private const val MAX_AD_BREAK_MILLIS = 60_000L

    /**
     * 只做结构分析, 不下判断. 调用方据 [HlsManifestAnalysis.probeTargets] 探测各组首片的时间戳, 再交给 [filter].
     */
    fun analyze(content: String, baseUrl: String = "http://127.0.0.1/playlist.m3u8"): HlsManifestAnalysis {
        val playlist = try {
            DefaultM3u8Parser.parse(content, baseUrl)
        } catch (_: Exception) {
            return HlsManifestAnalysis.earlyReturn(content, HlsManifestFilterResult.unsupported(content, "invalid_playlist"))
        }

        when (playlist) {
            is M3u8Playlist.MasterPlaylist -> {
                return HlsManifestAnalysis.earlyReturn(
                    content,
                    HlsManifestFilterResult.unsupported(content, MASTER_PLAYLIST),
                )
            }

            is M3u8Playlist.MediaPlaylist -> {
                if (!playlist.isEndlist) {
                    return HlsManifestAnalysis.earlyReturn(
                        content,
                        HlsManifestFilterResult.unsupported(content, "live_or_incomplete_playlist"),
                    )
                }
                if (playlist.segments.none { it.isDiscontinuity }) {
                    return HlsManifestAnalysis.earlyReturn(
                        content,
                        HlsManifestFilterResult.unchanged(content, "no_discontinuity"),
                    )
                }
                // 探测读到的是密文, 同步字节有极小概率蒙对而解出假时间戳, 把正片判成广告
                if (playlist.segments.any { it.encryption != null }) {
                    return HlsManifestAnalysis.earlyReturn(
                        content,
                        HlsManifestFilterResult.unchanged(content, "encrypted"),
                    )
                }
                if (playlist.segments.any { it.byteRange != null && it.byteRange?.offset == null }) {
                    return HlsManifestAnalysis.earlyReturn(
                        content,
                        HlsManifestFilterResult.unchanged(content, "byterange_implicit_offset"),
                    )
                }
            }
        }

        val groups = parseGroups(playlist)
        if (groups.size < 2) {
            return HlsManifestAnalysis.earlyReturn(content, HlsManifestFilterResult.unchanged(content, "single_group"))
        }
        return HlsManifestAnalysis(content, groups)
    }

    /**
     * @param probe 按顺序给出各目标首片的首个 PTS (毫秒), 探测失败为 `null`.
     */
    suspend fun filter(
        analysis: HlsManifestAnalysis,
        probe: suspend (List<HlsProbeTarget>) -> List<Long?>,
    ): HlsManifestFilterResult {
        analysis.earlyResult?.let { return it }
        return decide(analysis, classify(analysis, probe(analysis.probeTargets)))
    }

    /**
     * @param firstPts 与 [HlsManifestAnalysis.probeTargets] 一一对应. 探测失败或尚未探测的为 `null`.
     */
    internal fun classify(analysis: HlsManifestAnalysis, firstPts: List<Long?>): HlsPtsContinuity.Verdict {
        return HlsPtsContinuity.classify(
            analysis.probeTargets.mapIndexed { i, target ->
                HlsPtsContinuity.Group(target.groupIndex, target.durationMillis, firstPts[i])
            },
        )
    }

    /**
     * 按 [verdict] 过滤后的时间轴上, [positionMillis] 所在的分片及其后的分片, 共至多 [count] 个.
     * 位置超出过滤后的总时长时返回 `null`.
     *
     * 被移除的组与 [decide] 相同, 时间轴与代理给播放器的播放列表相同 (各分片按 [segmentDurationMillis] 累加),
     * 所以对最终的判定, 这里给出的就是播放器跳到 [positionMillis] 时请求的分片.
     */
    internal fun locate(
        analysis: HlsManifestAnalysis,
        verdict: HlsPtsContinuity.Verdict,
        positionMillis: Long,
        count: Int,
    ): HlsSegmentLocation? {
        if (analysis.isConclusive) return null
        val removed = removableAdGroups(analysis.groups, verdict.ads).removed.mapTo(HashSet()) { it.index }
        val uris = ArrayList<String>(count)
        var lastGroupIndex = -1
        var cursor = 0L
        for (group in analysis.groups) {
            if (group.index in removed) continue
            for (segment in group.segments) {
                val end = cursor + segmentDurationMillis(segment.duration)
                if (uris.isNotEmpty() || positionMillis < end) {
                    uris += segment.uri
                    lastGroupIndex = group.index
                    if (uris.size == count) return HlsSegmentLocation(uris, lastGroupIndex)
                }
                cursor = end
            }
        }
        return if (uris.isEmpty()) null else HlsSegmentLocation(uris, lastGroupIndex)
    }

    internal fun decide(analysis: HlsManifestAnalysis, verdict: HlsPtsContinuity.Verdict): HlsManifestFilterResult {
        analysis.earlyResult?.let { return it }
        val content = analysis.content
        val groups = analysis.groups

        val (removed, oversized) = removableAdGroups(groups, verdict.ads)
        val oversizedIndexes = oversized.map { it.index }
        if (removed.isEmpty()) {
            val reason = if (oversizedIndexes.isEmpty()) "no_ad" else "ad_break_too_long"
            return HlsManifestFilterResult.unchanged(content, reason).copy(oversizedGroups = oversizedIndexes)
        }

        val removedLines = removed.flatMapTo(mutableSetOf()) { it.lineStart..it.lineEnd }
        val filtered = content.lines()
            .filterIndexed { index, _ -> index + 1 !in removedLines }
            .joinToString("\n")
            .let { if (content.endsWith('\n')) "$it\n" else it }

        return HlsManifestFilterResult(
            status = HlsManifestFilterStatus.Filtered,
            content = filtered,
            reason = null,
            removedGroups = removed.map { group ->
                HlsRemovedGroup(
                    index = group.index,
                    lineStart = group.lineStart,
                    lineEnd = group.lineEnd,
                    startSegmentIndex = group.segments.first().index,
                    endSegmentIndex = group.segments.last().index,
                    duration = group.duration,
                    segmentCount = group.count,
                )
            },
            oversizedGroups = oversizedIndexes,
        )
    }

    private data class AdGroups(val removed: List<ManifestGroup>, val oversized: List<ManifestGroup>)

    /** 判为广告的组中, 要移除的与因所在插播超过 [MAX_AD_BREAK_MILLIS] 而保留的. */
    private fun removableAdGroups(groups: List<ManifestGroup>, ads: Set<Int>): AdGroups {
        val (removable, oversized) = adBreaks(groups, ads)
            .partition { breakGroups -> breakGroups.sumOf { it.duration } * 1000 <= MAX_AD_BREAK_MILLIS }
        return AdGroups(removable.flatten(), oversized.flatten())
    }

    /** 把判为广告的组按相邻关系合成一段段插播. 一段插播常被多出来的 `#EXT-X-DISCONTINUITY` 切成两组. */
    private fun adBreaks(groups: List<ManifestGroup>, ads: Set<Int>): List<List<ManifestGroup>> {
        val breaks = mutableListOf<MutableList<ManifestGroup>>()
        var previousIndex: Int? = null
        for (group in groups) {
            if (group.index !in ads) continue
            if (previousIndex != null && group.index == previousIndex + 1) {
                breaks.last() += group
            } else {
                breaks += mutableListOf(group)
            }
            previousIndex = group.index
        }
        return breaks
    }

    private fun parseGroups(playlist: M3u8Playlist.MediaPlaylist): List<ManifestGroup> {
        val groups = mutableListOf<ManifestGroup>()
        var builder = ManifestGroupBuilder(index = 0)

        fun close() {
            val group = builder.build() ?: return
            groups += group
            builder = ManifestGroupBuilder(index = group.index + 1)
        }

        for ((segmentIndex, parsedSegment) in playlist.segments.withIndex()) {
            if (parsedSegment.isDiscontinuity) {
                close()
            }
            val sourceRange = parsedSegment.sourceRange ?: return emptyList()
            builder.add(
                ManifestSegment(
                    index = segmentIndex,
                    duration = parsedSegment.duration.toDouble(),
                    uri = parsedSegment.uri,
                    lineStart = sourceRange.startLine,
                    lineEnd = sourceRange.endLine,
                ),
            )
        }
        close()

        return groups
    }

    internal const val MASTER_PLAYLIST = "master_playlist"
}

enum class HlsManifestFilterStatus {
    Filtered,
    Unchanged,
    Unsupported,
}

data class HlsManifestFilterResult(
    val status: HlsManifestFilterStatus,
    val content: String,
    val reason: String?,
    val removedGroups: List<HlsRemovedGroup>,
    /** 时间戳判为广告, 但所在的一段超过时长上限而保留的组. */
    val oversizedGroups: List<Int> = emptyList(),
) {
    companion object {
        fun unchanged(content: String, reason: String): HlsManifestFilterResult {
            return HlsManifestFilterResult(HlsManifestFilterStatus.Unchanged, content, reason, emptyList())
        }

        fun unsupported(content: String, reason: String): HlsManifestFilterResult {
            return HlsManifestFilterResult(HlsManifestFilterStatus.Unsupported, content, reason, emptyList())
        }
    }
}

/**
 * @property startSegmentIndex 组内首个分片在播放列表所有分片中的序号. [endSegmentIndex] 同理, 含.
 */
data class HlsRemovedGroup(
    val index: Int,
    val lineStart: Int,
    val lineEnd: Int,
    val startSegmentIndex: Int,
    val endSegmentIndex: Int,
    val duration: Double,
    val segmentCount: Int,
)

/**
 * [HlsManifestFilter.analyze] 的结果. [probeTargets] 给出各组首个分片的地址, 供调用方探测时间戳.
 */
class HlsManifestAnalysis internal constructor(
    internal val content: String,
    internal val groups: List<ManifestGroup>,
    internal val earlyResult: HlsManifestFilterResult? = null,
) {
    /** 不需要探测就已有结论, 例如没有拼接点、加密或不是媒体播放列表. */
    val isConclusive: Boolean get() = earlyResult != null

    val isMasterPlaylist: Boolean
        get() = earlyResult?.status == HlsManifestFilterStatus.Unsupported &&
                earlyResult.reason == HlsManifestFilter.MASTER_PLAYLIST

    /** 各组首个分片的绝对地址, 按组序号. */
    val probeTargets: List<HlsProbeTarget> = groups.map { group ->
        HlsProbeTarget(
            groupIndex = group.index,
            durationMillis = (group.duration * 1000).toLong(),
            uri = group.segments.first().uri,
        )
    }

    internal companion object {
        fun earlyReturn(content: String, result: HlsManifestFilterResult) =
            HlsManifestAnalysis(content, groups = emptyList(), earlyResult = result)
    }
}

/**
 * [HlsManifestFilter.locate] 的结果.
 *
 * @property segmentUris 分片地址, 与播放列表中的写法相同 (解析时已相对 baseUrl 解析).
 * @property lastGroupIndex [segmentUris] 中最后一个分片所在组的序号.
 */
internal class HlsSegmentLocation(
    val segmentUris: List<String>,
    val lastGroupIndex: Int,
)

/**
 * 分片在播放列表时间轴上占的毫秒数. 代理给播放器的播放列表与 [HlsManifestFilter.locate] 都按它逐片累加,
 * 两边的时间轴因此逐毫秒一致.
 */
internal fun segmentDurationMillis(durationSeconds: Double): Long =
    (durationSeconds * 1000).roundToLong().coerceAtLeast(0L)

data class HlsProbeTarget(
    val groupIndex: Int,
    val durationMillis: Long,
    val uri: String,
)

/**
 * 探测每个分片时读取的字节数. 实测 3212 个真实分片的首个视频 PTS 都在第 752 字节
 * (PAT, PMT 之后的第一个 PES), 这里留出近 3 倍余量. 源站单连接常只有十几 KB/s, 多读的字节会直接拖慢探测.
 */
const val PTS_PROBE_BYTES = 2 * 1024

internal data class ManifestSegment(
    /** 在播放列表所有分片中的序号. */
    val index: Int,
    val duration: Double,
    val uri: String,
    val lineStart: Int,
    val lineEnd: Int,
)

internal data class ManifestGroup(
    val index: Int,
    val lineStart: Int,
    val lineEnd: Int,
    val duration: Double,
    val count: Int,
    val segments: List<ManifestSegment>,
)

private class ManifestGroupBuilder(
    val index: Int,
) {
    private val segments = mutableListOf<ManifestSegment>()

    fun add(segment: ManifestSegment) {
        segments += segment
    }

    fun build(): ManifestGroup? {
        if (segments.isEmpty()) return null
        return ManifestGroup(
            index = index,
            lineStart = segments.first().lineStart,
            lineEnd = segments.last().lineEnd,
            duration = segments.sumOf { it.duration },
            count = segments.size,
            segments = segments.toList(),
        )
    }
}
