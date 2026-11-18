/*
 * Copyright (C) 2026 OpenAni and contributors.
 * Use of this source code is governed by the GNU AGPLv3 license.
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.media.resolver

/** 按顶层 box 大小跳过 mdat，仅有界读取 moov，避免为识别 HEVC 标记下载视频内容。 */
internal suspend fun mp4NeedsHevcRemux(readRange: suspend (offset: Long, size: Int) -> ByteArray): Boolean {
    return readMp4SampleEntries(readRange).contains("hev1")
}

internal suspend fun readMp4SampleEntries(readRange: suspend (offset: Long, size: Int) -> ByteArray): List<String> {
    var offset = 0L
    repeat(64) {
        val header = readRange(offset, 16)
        if (header.size < 8) return emptyList()
        val shortSize = header.unsignedInt(0)
        val headerSize = if (shortSize == 1L) 16 else 8
        if (header.size < headerSize) return emptyList()
        val size = if (shortSize == 1L) {
            val high = header.unsignedInt(8)
            if (high > Int.MAX_VALUE) return emptyList()
            (high shl 32) or header.unsignedInt(12)
        } else shortSize
        if (size < headerSize || offset > Long.MAX_VALUE - size) return emptyList()
        if (header.typeAt(4) == "moov") {
            if (size > 4 * 1024 * 1024) return emptyList()
            val moov = readRange(offset + headerSize, (size - headerSize).toInt())
            return buildList { moov.collectSampleEntries(0, moov.size, this) }
        }
        offset += size
    }
    return emptyList()
}

private fun ByteArray.collectSampleEntries(start: Int, end: Int, entries: MutableList<String>, depth: Int = 0) {
    if (depth > 8) return
    var offset = start
    while (offset <= end - 8) {
        val size = unsignedInt(offset)
        if (size < 8 || size > end - offset) return
        val next = offset + size.toInt()
        when (typeAt(offset + 4)) {
            "trak", "mdia", "minf", "stbl" -> collectSampleEntries(offset + 8, next, entries, depth + 1)
            "stsd" -> {
                if (size < 16) return
                var entry = offset + 16
                val count = unsignedInt(offset + 12)
                if (count > (size - 16) / 8) return
                repeat(count.toInt()) {
                    if (entry > next - 8) return
                    val entrySize = unsignedInt(entry)
                    if (entrySize < 8 || entrySize > next - entry) return
                    entries += typeAt(entry + 4)
                    entry += entrySize.toInt()
                }
            }
        }
        offset = next
    }
}

internal fun mp4CompatibilityTracks(entries: List<String>): MediaContainerTrackInfo? {
    val video = entries.firstOrNull { it in setOf("avc1", "avc3", "hvc1", "hev1", "mp4v", "vp09", "av01", "encv") }
        ?: return null
    val audio = entries.filter { it in setOf("mp4a", "ac-3", "ec-3", "alac", "Opus", "fLaC", "dtsc", "dtsh", "lpcm", "sowt", "twos") }
    if (video in setOf("avc1", "avc3", "hvc1", "mp4v") && audio.all { it in setOf("mp4a", "ac-3", "ec-3", "alac") }) return null
    require(video != "encv") { "Encrypted video requires a compatible DRM player" }
    return MediaContainerTrackInfo(
        videoCodecId = when (video) {
            "hev1", "hvc1" -> "V_MPEGH/ISO/HEVC"
            "avc1", "avc3" -> "V_MPEG4/ISO/AVC"
            "mp4v" -> "V_MPEG4/ISO/ASP"
            else -> video
        },
        subtitles = emptyList(),
        audioCodecIds = audio.map {
            when (it) {
                "mp4a" -> "A_AAC"
                "ac-3" -> "A_AC3"
                "ec-3" -> "A_EAC3"
                "alac" -> "A_ALAC"
                else -> it
            }
        },
    )
}

private fun ByteArray.unsignedInt(offset: Int): Long =
    (0..3).fold(0L) { value, index -> (value shl 8) or (this[offset + index].toLong() and 255) }

private fun ByteArray.typeAt(offset: Int): String = decodeToString(offset, offset + 4)
