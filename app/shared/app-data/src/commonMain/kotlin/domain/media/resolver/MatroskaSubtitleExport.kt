/*
 * Copyright (C) 2026 OpenAni and contributors.
 * Use of this source code is governed by the GNU AGPLv3 license.
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.media.resolver

import kotlinx.io.Buffer
import kotlinx.io.Sink
import kotlinx.io.Source
import kotlinx.io.readByteArray
import kotlinx.io.readString
import kotlinx.io.writeString

/** Apple FFmpeg 的 Matroska muxer 保存 SRT 编码后的文本和时间，文本导出不依赖 SRT muxer。 */
internal fun matroskaSubtitleExportArguments(input: String, outputs: List<Pair<Int, String>>): List<String> = buildList {
    addAll(listOf("-nostdin", "-y", "-v", "error", "-copyts", "-i", input))
    for ((index, output) in outputs) {
        addAll(listOf("-map", "0:s:$index", "-c:s", "srt", "-f", "matroska", "-avoid_negative_ts", "disabled", output))
    }
}

/** 读取 FFmpeg 生成的单轨 S_TEXT/UTF8 文件，保留 BlockGroup 的时间和重叠对白。 */
internal fun exportMatroskaSubtitleAsSrt(source: Source, sink: Sink) {
    var timestampScale = 1_000_000L
    var cueIndex = 0
    while (!source.exhausted()) {
        val id = source.readEbmlInteger(keepMarker = true)
        val size = source.readEbmlInteger()
        if (id == 0x18538067L) continue // Segment 可以使用未知长度。
        require(size in 0..16 * 1024 * 1024) { "Subtitle element exceeds size limit" }
        when (id) {
            0x1549a966L -> {
                val info = Buffer().apply { write(source.readByteArray(size.toInt())) }
                info.forEachEbmlElement { tag, value ->
                    if (tag == 0x2ad7b1L) timestampScale = value.readEbmlUnsigned().also {
                        require(it in 1..1_000_000_000) { "Invalid subtitle timestamp scale" }
                    }
                }
            }
            0x1f43b675L -> {
                var timestamp = 0L
                val cluster = Buffer().apply { write(source.readByteArray(size.toInt())) }
                cluster.forEachEbmlElement { tag, value ->
                    when (tag) {
                        0xe7L -> timestamp = value.readEbmlUnsigned()
                        0xa0L -> {
                            var duration = 0L
                            var block: Buffer? = null
                            value.forEachEbmlElement { field, content ->
                                when (field) {
                                    0x9bL -> duration = content.readEbmlUnsigned()
                                    0xa1L -> block = content
                                }
                            }
                            block?.let { data ->
                                require(data.readEbmlInteger() == 1L) { "Expected one subtitle track" }
                                val relativeTimestamp = data.readShort().toLong()
                                require(data.readByte().toInt() and 0x06 == 0) { "Subtitle lacing is unsupported" }
                                val start = (timestamp + relativeTimestamp) * timestampScale / 1_000_000
                                val end = start + duration * timestampScale / 1_000_000
                                if (end > maxOf(0, start)) {
                                    sink.writeString("${++cueIndex}\n${srtTime(maxOf(0, start))} --> ${srtTime(end)}\n")
                                    sink.writeString(data.readString().trimEnd('\u0000', '\r', '\n'))
                                    sink.writeString("\n\n")
                                }
                            }
                        }
                    }
                }
            }
            else -> source.skip(size)
        }
    }
}

private fun srtTime(millis: Long): String =
    "${(millis / 3_600_000).toString().padStart(2, '0')}:" +
            "${(millis / 60_000 % 60).toString().padStart(2, '0')}:" +
            "${(millis / 1000 % 60).toString().padStart(2, '0')}," +
            (millis % 1000).toString().padStart(3, '0')

private fun Source.readEbmlInteger(keepMarker: Boolean = false): Long {
    val first = readByte().toInt() and 255
    require(first != 0) { "Invalid EBML integer" }
    val length = (0..7).first { first and (128 shr it) != 0 } + 1
    var value = (if (keepMarker) first else first and (255 shr length)).toLong()
    repeat(length - 1) { value = (value shl 8) or (readByte().toLong() and 255) }
    return value
}

private fun Buffer.readEbmlUnsigned(): Long {
    require(size in 1..8) { "Invalid EBML unsigned integer" }
    var value = 0L
    while (!exhausted()) value = (value shl 8) or (readByte().toLong() and 255)
    require(value >= 0) { "EBML unsigned integer exceeds signed range" }
    return value
}

private inline fun Buffer.forEachEbmlElement(block: (Long, Buffer) -> Unit) {
    while (!exhausted()) {
        val id = readEbmlInteger(keepMarker = true)
        val length = readEbmlInteger()
        require(length in 0..size) { "Truncated subtitle element" }
        val bytes = readByteArray(length.toInt())
        block(id, Buffer().apply { write(bytes) })
    }
}
