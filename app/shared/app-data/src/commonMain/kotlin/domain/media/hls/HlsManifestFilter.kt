/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.media.hls

object HlsManifestFilter {
    fun filter(content: String): HlsManifestFilterResult {
        val lines = content.lines()
        if (lines.firstOrNull()?.trimStart()?.startsWith("#EXTM3U") != true) {
            return HlsManifestFilterResult.unsupported(content, "invalid_playlist")
        }
        if (lines.any { it.trim().startsWith("#EXT-X-STREAM-INF") }) {
            return HlsManifestFilterResult.unsupported(content, "master_playlist")
        }
        if (lines.none { it.trim().startsWith("#EXT-X-ENDLIST") }) {
            return HlsManifestFilterResult.unsupported(content, "live_or_incomplete_playlist")
        }
        if (lines.none { it.trim().startsWith("#EXT-X-DISCONTINUITY") }) {
            return HlsManifestFilterResult.unchanged(content, "no_discontinuity")
        }

        val groups = parseGroups(lines)
        if (groups.isEmpty()) {
            return HlsManifestFilterResult.unchanged(content, "no_segments")
        }

        val candidates = detectCandidates(groups)
        if (candidates.isEmpty()) {
            return HlsManifestFilterResult.unchanged(content, "no_candidate")
        }
        if (hasAes128KeyWithoutExplicitIv(lines)) {
            return HlsManifestFilterResult.unchanged(content, "encrypted_implicit_iv")
        }
        if (hasByteRangeWithoutExplicitOffset(lines)) {
            return HlsManifestFilterResult.unchanged(content, "byterange_implicit_offset")
        }

        val removableIndexes = candidates.mapTo(mutableSetOf()) { it.group.index }
        val remainingSegmentCount = groups
            .filterNot { it.index in removableIndexes }
            .sumOf { it.count }
        if (remainingSegmentCount == 0) {
            return HlsManifestFilterResult.unchanged(content, "all_segments_candidate")
        }

        val removedLines = candidates
            .flatMapTo(mutableSetOf()) { candidate -> candidate.group.lineStart..candidate.group.lineEnd }
        val filtered = lines
            .filterIndexed { index, _ -> index + 1 !in removedLines }
            .joinToString("\n")
            .let { if (content.endsWith('\n')) "$it\n" else it }

        return HlsManifestFilterResult(
            status = HlsManifestFilterStatus.Filtered,
            content = filtered,
            reason = null,
            removedGroups = candidates.map { candidate ->
                HlsRemovedGroup(
                    index = candidate.group.index,
                    lineStart = candidate.group.lineStart,
                    lineEnd = candidate.group.lineEnd,
                    startSegmentIndex = candidate.group.startSegmentIndex,
                    endSegmentIndex = candidate.group.endSegmentIndex,
                    duration = candidate.group.duration,
                    segmentCount = candidate.group.count,
                    reasons = candidate.reasons.map { it.id },
                )
            },
        )
    }

    private fun parseGroups(lines: List<String>): List<ManifestGroup> {
        val groups = mutableListOf<ManifestGroup>()
        val allSegments = mutableListOf<ManifestSegment>()
        var builder = ManifestGroupBuilder(index = 0)
        var pendingDuration: Double? = null
        var pendingExtInfLine: Int? = null
        var pendingDiscontinuityLine: Int? = null

        fun close() {
            val group = builder.build() ?: return
            groups += group
            builder = ManifestGroupBuilder(index = group.index + 1)
        }

        for ((zeroIndex, rawLine) in lines.withIndex()) {
            val lineNo = zeroIndex + 1
            val line = rawLine.trim()
            when {
                line.startsWith("#EXT-X-DISCONTINUITY") -> {
                    close()
                    pendingDiscontinuityLine = lineNo
                }

                line.startsWith("#EXTINF:") -> {
                    pendingDuration = line.substringAfter(":").substringBefore(",").toDoubleOrNull()
                    pendingExtInfLine = lineNo
                }

                line.isNotBlank() && !line.startsWith("#") && pendingDuration != null -> {
                    val segment = ManifestSegment(
                        index = allSegments.size,
                        duration = pendingDuration,
                        uri = line,
                        fileName = segmentFileName(line),
                        extInfLine = pendingExtInfLine ?: lineNo,
                        uriLine = lineNo,
                    )
                    allSegments += segment
                    builder.add(segment, pendingDiscontinuityLine)
                    pendingDuration = null
                    pendingExtInfLine = null
                    pendingDiscontinuityLine = null
                }
            }
        }
        close()

        return groups
    }

    private fun detectCandidates(groups: List<ManifestGroup>): List<CandidateGroup> {
        val segments = groups.flatMap { it.segments }
        val dense = groups.size >= 20 || groups.size.toDouble() / segments.size > 0.08
        val signatureCounts = groups.groupingBy { it.fileSignature }.eachCount()
        val sequencePrefix = if (dense) numericModel(segments) else null

        return groups.mapIndexedNotNull { groupIndex, group ->
            val reasons = mutableListOf<HlsCandidateReason>()
            val previous = groups.getOrNull(groupIndex - 1)
            val next = groups.getOrNull(groupIndex + 1)
            val short = group.count <= 12 || group.duration <= 45.0

            val paths = group.segments.joinToString(" ") { segmentPath(it.uri).lowercase() }
            val strongPath = listOf("adjump", "/ad/", "/ads/", "advert").any { it in paths }
            if (strongPath) {
                reasons += HlsCandidateReason.StrongPath
            }

            val repeatShort = signatureCounts.getValue(group.fileSignature) > 1 &&
                group.count <= 12 &&
                group.duration <= 45.0
            if (repeatShort) {
                reasons += HlsCandidateReason.RepeatShort
            }

            val sandwichedShort = previous != null &&
                next != null &&
                previous.duration >= 60.0 &&
                next.duration >= 60.0 &&
                group.duration <= 45.0 &&
                group.count >= 2
            if (sandwichedShort) {
                reasons += HlsCandidateReason.SandwichedShort
            }

            if (!dense && short && group.count >= 2) {
                reasons += HlsCandidateReason.LowDensityShort
            }

            if (isSequenceIsland(dense, sequencePrefix, segments, group, short)) {
                reasons += HlsCandidateReason.SequenceIsland
            }

            val denseTiny = dense &&
                (group.count <= 3 || group.duration <= 12.0) &&
                previous != null &&
                next != null
            if (denseTiny) {
                reasons += HlsCandidateReason.DenseTiny
            }

            reasons.distinct().takeIf { it.isNotEmpty() }?.let {
                CandidateGroup(group, it)
            }
        }
    }

    private fun isSequenceIsland(
        dense: Boolean,
        sequencePrefix: String?,
        segments: List<ManifestSegment>,
        group: ManifestGroup,
        short: Boolean,
    ): Boolean {
        if (!dense || sequencePrefix == null || group.count < 2 || !short) {
            return false
        }

        val numbers = group.segments.map { trailingNumber(it.fileName, sequencePrefix) }
        val first = numbers.firstOrNull()
        val last = numbers.lastOrNull()
        val previous = segments.getOrNull(group.startSegmentIndex - 1)?.let { trailingNumber(it.fileName, sequencePrefix) }
        val following = segments.getOrNull(group.endSegmentIndex + 1)?.let { trailingNumber(it.fileName, sequencePrefix) }
        val linearIsland = numbers.all { it != null } &&
            numbers.zipWithNext().all { (left, right) -> right == left?.plus(1) }

        return previous != null &&
            following != null &&
            first != null &&
            last != null &&
            linearIsland &&
            following == previous + 1 &&
            kotlin.math.abs(first - previous) > 1000 &&
            kotlin.math.abs(last - following) > 1000
    }
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

data class HlsRemovedGroup(
    val index: Int,
    val lineStart: Int,
    val lineEnd: Int,
    val startSegmentIndex: Int,
    val endSegmentIndex: Int,
    val duration: Double,
    val segmentCount: Int,
    val reasons: List<String>,
)

private enum class HlsCandidateReason(val id: String) {
    StrongPath("strong_path"),
    RepeatShort("repeat_short"),
    SandwichedShort("sandwiched_short"),
    LowDensityShort("low_density_short"),
    SequenceIsland("sequence_island"),
    DenseTiny("dense_tiny"),
}

private data class ManifestSegment(
    val index: Int,
    val duration: Double,
    val uri: String,
    val fileName: String,
    val extInfLine: Int,
    val uriLine: Int,
)

private data class ManifestGroup(
    val index: Int,
    val lineStart: Int,
    val lineEnd: Int,
    val startSegmentIndex: Int,
    val endSegmentIndex: Int,
    val duration: Double,
    val count: Int,
    val fileSignature: String,
    val segments: List<ManifestSegment>,
)

private class ManifestGroupBuilder(
    val index: Int,
) {
    private val segments = mutableListOf<ManifestSegment>()
    private var lineStart: Int? = null

    fun add(segment: ManifestSegment, discontinuityLine: Int?) {
        if (segments.isEmpty()) {
            lineStart = discontinuityLine ?: segment.extInfLine
        }
        segments += segment
    }

    fun build(): ManifestGroup? {
        if (segments.isEmpty()) return null
        return ManifestGroup(
            index = index,
            lineStart = lineStart ?: segments.first().extInfLine,
            lineEnd = segments.last().uriLine,
            startSegmentIndex = segments.first().index,
            endSegmentIndex = segments.last().index,
            duration = segments.sumOf { it.duration },
            count = segments.size,
            fileSignature = segments.joinToString("|") { it.fileName },
            segments = segments.toList(),
        )
    }
}

private data class CandidateGroup(
    val group: ManifestGroup,
    val reasons: List<HlsCandidateReason>,
)

private fun numericModel(segments: List<ManifestSegment>): String? {
    val values = segments.mapNotNull { segment ->
        NUMERIC_TS_REGEX.matchEntire(segment.fileName)?.let {
            it.groupValues[1] to it.groupValues[2].toInt()
        }
    }
    if (values.isEmpty()) return null

    val prefix = values.groupingBy { it.first }.eachCount().maxByOrNull { it.value } ?: return null
    if (prefix.value.toDouble() / segments.size < 0.8) return null

    val numbers = values.filter { it.first == prefix.key }.map { it.second }
    val deltas = numbers.zipWithNext().map { (left, right) -> right - left }
    if (deltas.isEmpty()) return null

    val delta = deltas.groupingBy { it }.eachCount().maxByOrNull { it.value } ?: return null
    if (delta.key != 1 || delta.value.toDouble() / deltas.size < 0.5) return null
    return prefix.key
}

private fun trailingNumber(fileName: String, prefix: String): Int? {
    if (!fileName.startsWith(prefix) || !fileName.endsWith(".ts")) return null
    return fileName.substring(prefix.length, fileName.length - ".ts".length).toIntOrNull()
}

private fun segmentFileName(uri: String): String {
    return uri.substringBefore('?').substringBefore('#').substringAfterLast('/')
}

private fun segmentPath(uri: String): String {
    val clean = uri.substringBefore('?').substringBefore('#')
    val path = if ("://" in clean) {
        clean.substringAfter("://").substringAfter('/', missingDelimiterValue = "")
    } else {
        clean
    }
    if (path.isEmpty()) return ""
    return if (path.startsWith('/')) path else "/$path"
}

private fun hasAes128KeyWithoutExplicitIv(lines: List<String>): Boolean {
    return lines.any { rawLine ->
        val line = rawLine.trim()
        line.startsWith("#EXT-X-KEY") &&
            line.contains("METHOD=AES-128") &&
            !line.contains("IV=")
    }
}

private fun hasByteRangeWithoutExplicitOffset(lines: List<String>): Boolean {
    return lines.any { rawLine ->
        val line = rawLine.trim()
        line.startsWith("#EXT-X-BYTERANGE:") && "@" !in line.substringAfter(":")
    }
}

private val NUMERIC_TS_REGEX = Regex("""^(.*?)(\d{1,7})\.ts$""")
