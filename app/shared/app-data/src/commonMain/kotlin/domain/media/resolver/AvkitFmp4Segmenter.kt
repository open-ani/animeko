/*
 * Copyright (C) 2026 OpenAni and contributors.
 * Use of this source code is governed by the GNU AGPLv3 license.
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.media.resolver

import kotlinx.io.Buffer
import kotlinx.io.readByteArray

/** 读取 FFmpeg 的独立 moof/mdat 分片，使用视频 sample 时长生成 HLS 时间轴。 */
internal class AvkitFmp4Segmenter(
    private val initialize: (ByteArray) -> Unit,
    private val emit: (AvkitStreamSegment) -> Unit,
) {
    private val initialization = Buffer()
    private val fragment = Buffer()
    private var videoTrack = 0L
    private var timescale = 0L
    private var defaultDuration = 0L
    private var duration = 0.0
    private var index = 0

    fun box(bytes: ByteArray) {
        val box = Mp4Box(bytes, 0, bytes.size)
        when (box.type) {
            "ftyp" -> initialization.write(bytes)
            "moov" -> {
                readMovie(box)
                initialization.write(bytes)
                initialize(initialization.readByteArray())
            }
            "moof" -> {
                require(fragment.size == 0L && timescale > 0) { "Invalid fragmented MP4 sequence" }
                duration = fragmentDuration(box)
                require(duration > 0 && duration <= AvkitTsSegmenter.TargetDuration) { "Unsupported MP4 fragment duration" }
                fragment.write(bytes)
            }
            "mdat" -> {
                require(fragment.size > 0) { "MP4 media requires a fragment header" }
                fragment.write(bytes)
                emit(AvkitStreamSegment(index++, duration, fragment.readByteArray()))
            }
        }
    }

    fun finish() {
        require(fragment.size == 0L) { "Truncated MP4 fragment" }
    }

    private fun readMovie(movie: Mp4Box) {
        for (track in movie.children().filter { it.type == "trak" }) {
            val media = track.children().first { it.type == "mdia" }
            val handler = media.children().first { it.type == "hdlr" }
            if (handler.text(8, 4) != "vide") continue
            val header = track.children().first { it.type == "tkhd" }
            videoTrack = header.uint(if (header.version == 1) 20 else 12)
            val mediaHeader = media.children().first { it.type == "mdhd" }
            timescale = mediaHeader.uint(if (mediaHeader.version == 1) 20 else 12)
            break
        }
        require(videoTrack > 0 && timescale > 0) { "Missing MP4 video timescale" }
        val extends = movie.children().firstOrNull { it.type == "mvex" }
        extends?.children()?.firstOrNull { it.type == "trex" && it.uint(4) == videoTrack }?.let {
            defaultDuration = it.uint(12)
        }
    }

    private fun fragmentDuration(movieFragment: Mp4Box): Double {
        for (track in movieFragment.children().filter { it.type == "traf" }) {
            val header = track.children().first { it.type == "tfhd" }
            if (header.uint(4) != videoTrack) continue
            val flags = header.uint(0).toInt() and 0xffffff
            var cursor = 8
            if (flags and 1 != 0) cursor += 8
            if (flags and 2 != 0) cursor += 4
            val sampleDuration = if (flags and 8 != 0) header.uint(cursor) else defaultDuration
            var ticks = 0L
            for (run in track.children().filter { it.type == "trun" }) {
                val runFlags = run.uint(0).toInt() and 0xffffff
                val count = run.uint(4)
                require(count in 1..1_000_000) { "Invalid MP4 sample count" }
                var offset = 8
                if (runFlags and 1 != 0) offset += 4
                if (runFlags and 4 != 0) offset += 4
                repeat(count.toInt()) {
                    val value = if (runFlags and 0x100 != 0) run.uint(offset).also { offset += 4 } else sampleDuration
                    require(value > 0) { "Missing MP4 sample duration" }
                    ticks += value
                    if (runFlags and 0x200 != 0) offset += 4
                    if (runFlags and 0x400 != 0) offset += 4
                    if (runFlags and 0x800 != 0) offset += 4
                }
                require(offset <= run.payloadSize) { "Truncated MP4 sample run" }
            }
            return ticks / timescale.toDouble()
        }
        error("Missing video fragment")
    }

    private class Mp4Box(private val bytes: ByteArray, private val start: Int, private val end: Int) {
        private val headerSize = if (readUInt(bytes, start) == 1L) 16 else 8
        val type = bytes.decodeToString(start + 4, start + 8)
        val payloadSize get() = end - start - headerSize
        val version get() = bytes[start + headerSize].toInt() and 255

        fun uint(offset: Int): Long {
            require(offset >= 0 && offset + 4 <= payloadSize) { "Truncated MP4 field" }
            return readUInt(bytes, start + headerSize + offset)
        }

        fun text(offset: Int, length: Int): String {
            require(offset >= 0 && offset + length <= payloadSize) { "Truncated MP4 text" }
            return bytes.decodeToString(start + headerSize + offset, start + headerSize + offset + length)
        }

        fun children(): List<Mp4Box> = buildList {
            var cursor = start + headerSize
            while (cursor < end) {
                require(end - cursor >= 8) { "Truncated MP4 box" }
                val size = readUInt(bytes, cursor)
                val length = if (size == 1L) {
                    require(end - cursor >= 16 && readUInt(bytes, cursor + 8) == 0L) { "Oversized MP4 metadata" }
                    readUInt(bytes, cursor + 12)
                } else size
                require(length in (if (size == 1L) 16L else 8L)..(end - cursor).toLong()) { "Invalid MP4 box size" }
                add(Mp4Box(bytes, cursor, cursor + length.toInt()))
                cursor += length.toInt()
            }
        }
    }

    companion object {
        fun readUInt(bytes: ByteArray, offset: Int): Long {
            require(offset >= 0 && offset + 4 <= bytes.size) { "Truncated MP4 integer" }
            return (0..3).fold(0L) { value, i -> (value shl 8) or (bytes[offset + i].toLong() and 255) }
        }
    }
}
