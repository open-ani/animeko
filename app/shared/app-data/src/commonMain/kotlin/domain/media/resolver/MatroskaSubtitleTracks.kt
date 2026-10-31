/*
 * Copyright (C) 2026 OpenAni and contributors.
 * Use of this source code is governed by the GNU AGPLv3 license.
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.media.resolver

import kotlinx.io.Buffer
import kotlinx.io.Source
import kotlinx.io.readByteArray

internal data class MatroskaSubtitleTrack(val index: Int, val language: String?, val name: String?)
internal data class MatroskaTrackInfo(
    val videoCodecId: String?,
    val subtitles: List<MatroskaSubtitleTrack>,
)

internal fun mp4VideoTagFor(codecId: String?): String? = when (codecId) {
    "V_MPEGH/ISO/HEVC" -> "hvc1"
    else -> null
}

/** 只读取 Cluster 前的 Tracks 元数据；FFmpeg 的字幕流序号包括位图字幕。 */
internal fun readMatroskaTrackInfo(source: Source): MatroskaTrackInfo {
    fun Source.vint(keepMarker: Boolean): Long {
        val first = readByte().toInt() and 255
        require(first != 0) { "Invalid EBML variable integer" }
        val length = (0..7).first { first and (128 shr it) != 0 } + 1
        var result = (if (keepMarker) first else first and (255 shr length)).toLong()
        repeat(length - 1) { result = (result shl 8) or (readByte().toLong() and 255) }
        return result
    }
    fun bytesUnsigned(bytes: ByteArray): Long = bytes.fold(0L) { n, b -> (n shl 8) or (b.toLong() and 255) }
    fun fields(bytes: ByteArray): List<Pair<Long, ByteArray>> {
        val input = Buffer().apply { write(bytes) }
        val fields = mutableListOf<Pair<Long, ByteArray>>()
        while (!input.exhausted()) {
            val id = input.vint(true)
            val size = input.vint(false)
            require(size <= input.size) { "Truncated Matroska track metadata" }
            fields += id to input.readByteArray(size.toInt())
        }
        return fields
    }
    var beforeSegment = true
    var subtitleIndex = 0
    var inspected = 0L
    while (!source.exhausted() && inspected < 16 * 1024 * 1024) {
        val id = source.vint(true)
        val size = source.vint(false)
        if (id == 0x18538067L) {
            beforeSegment = false
            continue
        }
        if (!beforeSegment && id == 0x1f43b675L) break
        require(size in 0..16 * 1024 * 1024) { "Matroska header exceeds metadata limit" }
        if (id == 0x1654ae6bL) {
            var videoCodecId: String? = null
            val subtitles = fields(source.readByteArray(size.toInt())).filter { it.first == 0xaeL }.mapNotNull { (_, entry) ->
                val values = fields(entry).toMap()
                val type = values[0x83L]?.let(::bytesUnsigned)
                val codec = values[0x86L]?.decodeToString().orEmpty()
                if (type == 1L && videoCodecId == null) videoCodecId = codec
                if (type != 17L) return@mapNotNull null
                val index = subtitleIndex++
                if (codec !in setOf("S_TEXT/UTF8", "S_TEXT/ASS", "S_TEXT/SSA", "S_TEXT/WEBVTT")) return@mapNotNull null
                MatroskaSubtitleTrack(
                    index,
                    (values[0x22b59dL] ?: values[0x22b59cL])?.decodeToString()?.trimEnd('\u0000'),
                    values[0x536eL]?.decodeToString()?.trimEnd('\u0000'),
                )
            }
            return MatroskaTrackInfo(videoCodecId, subtitles)
        }
        source.skip(size)
        inspected += size
    }
    return MatroskaTrackInfo(null, emptyList())
}
