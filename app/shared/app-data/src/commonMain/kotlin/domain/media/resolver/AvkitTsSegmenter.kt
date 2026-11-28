/*
 * Copyright (C) 2026 OpenAni and contributors.
 * Use of this source code is governed by the GNU AGPLv3 license.
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.media.resolver

import kotlinx.io.Buffer
import kotlinx.io.readByteArray
import me.him188.ani.app.domain.media.hls.TsPacketReader
import kotlin.math.ceil

internal data class AvkitStreamSegment(val index: Int, val durationSeconds: Double, val bytes: ByteArray)

/** 在视频随机访问点切分连续 TS；每片携带 PAT/PMT，时间戳保持原时间轴。 */
internal class AvkitTsSegmenter(private val emit: (AvkitStreamSegment) -> Unit) {
    private var held = Buffer()
    private var lastPat = 0L
    private var startPts: Long? = null
    private var maxPts = 0L
    private var index = 0

    fun packet(bytes: ByteArray) {
        require(bytes.size == 188 && bytes[0] == 0x47.toByte()) { "Invalid streaming TS packet" }
        val pid = ((bytes[1].toInt() and 31) shl 8) or (bytes[2].toInt() and 255)
        if (pid == 0) lastPat = held.size
        val pes = TsPacketReader.pesHeaderOffset(bytes, 0)
        if (pid == 256 && pes != null && pes + 14 <= 188 && bytes[pes + 7].toInt() and 0x80 != 0) {
            val pts = TsPacketReader.readTimestamp(bytes, pes + 9)
            maxPts = maxOf(maxPts, pts)
            val adaptation = (bytes[3].toInt() ushr 4) and 3
            val randomAccess = adaptation and 2 != 0 && bytes[4].toInt() and 255 > 0 && bytes[5].toInt() and 0x40 != 0
            if (randomAccess) {
                val start = startPts
                if (start == null) {
                    if (lastPat > 0) held.skip(lastPat)
                    lastPat = 0
                    startPts = pts
                } else if (pts - start >= 180_000 && lastPat > 0) {
                    val duration = (pts - start) / 90_000.0
                    require(duration <= TargetDuration) { "Video keyframes exceed streaming segment limit" }
                    emit(AvkitStreamSegment(index++, duration, held.readByteArray(lastPat.toInt())))
                    lastPat = 0
                    startPts = pts
                }
            }
        }
        held.write(bytes)
        require(held.size <= 32 * 1024 * 1024) { "Streaming video segment exceeds size limit" }
    }

    fun finish() {
        startPts?.let { start ->
            if (held.size > 0) emit(AvkitStreamSegment(index++, ((maxPts - start) / 90_000.0 + 0.1).coerceAtLeast(0.1), held.readByteArray()))
        }
    }

    companion object {
        const val TargetDuration = 10

        fun playlist(durations: List<Double>, complete: Boolean, fragmentedMp4: Boolean = false): String = buildString {
            append("#EXTM3U\n#EXT-X-VERSION:${if (fragmentedMp4) 7 else 3}\n#EXT-X-TARGETDURATION:${maxOf(TargetDuration, ceil(durations.maxOrNull() ?: 0.0).toInt())}\n")
            append("#EXT-X-MEDIA-SEQUENCE:0\n#EXT-X-PLAYLIST-TYPE:EVENT\n#EXT-X-START:TIME-OFFSET=0\n")
            if (fragmentedMp4) append("#EXT-X-MAP:URI=\"init.mp4\"\n")
            val extension = if (fragmentedMp4) "m4s" else "ts"
            durations.forEachIndexed { index, duration -> append("#EXTINF:$duration,\nsegment-$index.$extension\n") }
            if (complete) append("#EXT-X-ENDLIST\n")
        }
    }
}
