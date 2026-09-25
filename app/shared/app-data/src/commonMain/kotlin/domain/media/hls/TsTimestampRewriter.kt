/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.media.hls

/**
 * 接收改写后的字节. 形状与 [HlsProxyResponseSink] 一致, 代理可直接用方法引用接上.
 */
internal fun interface TsByteSink {
    suspend fun write(buffer: ByteArray, offset: Int, length: Int)
}

/**
 * MPEG-TS 包与 PES 头的只读解析.
 *
 * 与 [TsTimestampRewriter] 的流式状态机分开, 让只需要读时间戳的调用方 (如判断某组分片属于哪条时间轴)
 * 不必牵进暂存与平移那一套.
 */
internal object TsPacketReader {
    const val PACKET_SIZE = 188
    const val SYNC_BYTE: Byte = 0x47

    /** 90kHz 刻度转毫秒. */
    fun ticksToMillis(ticks: Long): Long = ticks / 90

    /**
     * 从 [bytes] 的 [offset] 起 [length] 字节里读出第一个 PTS (90kHz 刻度), 没有则返回 null.
     *
     * 只扫描 188 字节对齐的包, 不处理 192 (M2TS) 与 204 (带 RS 校验) 的变体: 调用方拿到的是分片开头的若干字节,
     * 判定包间距需要更多数据, 而这两种变体在 HLS 里极少见.
     *
     * @param videoOnly 只认视频 PES (stream_id 0xE0..0xEF). 音视频首个 PTS 通常差上百毫秒, 比较不同分片时要取同一路.
     */
    fun firstPts(bytes: ByteArray, offset: Int = 0, length: Int = bytes.size - offset, videoOnly: Boolean = true): Long? {
        var base = offset
        while (base < offset + length && bytes[base] != SYNC_BYTE) base++
        while (base + PACKET_SIZE <= offset + length) {
            if (bytes[base] != SYNC_BYTE) return null // 失同步后继续扫下去只会读到垃圾
            val pes = pesHeaderOffset(bytes, base)
            if (pes != null && (!videoOnly || (bytes[pes + 3].toInt() and 0xFF) in 0xE0..0xEF)) {
                val flags = (bytes[pes + 7].toInt() ushr 6) and 0x3
                if (flags and 0x2 != 0 && pes + 14 <= base + PACKET_SIZE) return readTimestamp(bytes, pes + 9)
            }
            base += PACKET_SIZE
        }
        return null
    }

    /**
     * [base] 处 TS 包里 PES 头 (`00 00 01` 起) 的下标, 不是带可选头部的 PES 包则返回 null.
     */
    fun pesHeaderOffset(b: ByteArray, base: Int): Int? {
        if (b[base] != SYNC_BYTE) return null
        val payloadUnitStart = (b[base + 1].toInt() ushr 6) and 0x1
        if (payloadUnitStart == 0) return null
        val adaptationFieldControl = (b[base + 3].toInt() ushr 4) and 0x3
        if (adaptationFieldControl and 0x1 == 0) return null
        var pes = base + 4
        if (adaptationFieldControl and 0x2 != 0) {
            pes += 1 + (b[base + 4].toInt() and 0xFF)
        }
        // 需要读到 pes+7 的 PTS_DTS_flags. 时间戳字段本身的边界由调用方各自检查
        if (pes + 9 > base + PACKET_SIZE) return null
        if (b[pes] != 0.toByte() || b[pes + 1] != 0.toByte() || b[pes + 2] != 1.toByte()) return null
        if (!hasOptionalPesHeader(b[pes + 3].toInt() and 0xFF)) return null
        if ((b[pes + 6].toInt() and 0xC0) != 0x80) return null // 不是 MPEG-2 PES 头
        return pes
    }

    /** 33 位时间戳分散在 5 字节里, 每字节末位是 marker bit. */
    fun readTimestamp(b: ByteArray, at: Int): Long {
        val b0 = b[at].toLong() and 0x0E
        val b1 = b[at + 1].toLong() and 0xFF
        val b2 = b[at + 2].toLong() and 0xFE
        val b3 = b[at + 3].toLong() and 0xFF
        val b4 = b[at + 4].toLong() and 0xFE
        return (b0 shl 29) or (b1 shl 22) or (b2 shl 14) or (b3 shl 7) or (b4 shr 1)
    }

    /** 这些 stream_id 的 PES 没有可选头部, 因而没有 PTS/DTS. */
    private fun hasOptionalPesHeader(streamId: Int): Boolean = when (streamId) {
        0xBC, 0xBE, 0xBF, 0xF0, 0xF1, 0xF2, 0xF8, 0xFF -> false
        else -> true
    }
}

/**
 * 把一个 MPEG-TS 分片内的所有时间戳 (PES 的 PTS/DTS, adaptation field 的 PCR/OPCR) 平移 [shiftTicks].
 * 平移量由调用方按分片所在的 discontinuity 组算出 (见 [spliceShiftTicks]), 同组分片共用一个.
 *
 * 采集站在正片里插的广告分片是独立转码的, PTS 从 0 附近开始. libavformat 的 HLS 解复用器不按
 * `#EXT-X-DISCONTINUITY` 重映射分片时间戳, mpv 的 `time-pos` 直接取解复用器的 PTS, 于是桌面端播到广告时
 * 进度条会跳到离谱的位置. ExoPlayer 和 AVPlayer 自己按 discontinuity 重映射, 不需要本类.
 *
 * 一个分片一个实例, 用法是边收边转发:
 * ```
 * val rewriter = TsTimestampRewriter(shiftTicks)
 * while (true) {
 *     val read = channel.readAvailable(buffer, 0, buffer.size)
 *     if (read < 0) break
 *     rewriter.rewrite(buffer, 0, read, sink)
 * }
 * rewriter.finish(sink)
 * ```
 *
 * 改写是等长的: 输出字节数与输入完全相同, 所以源站的 `Content-Length` 可以原样转发.
 *
 * 只认 TS. 输入不是 TS (fMP4 分片、AES-128 密文) 时退化为原样转发, 但调用方仍应避免对加密分片使用本类:
 * 密文有极小概率通过同步字节探测, 那样会破坏分片.
 *
 * @param shiftTicks 平移量 (90kHz 刻度), 按 2^33 取模.
 */
internal class TsTimestampRewriter(shiftTicks: Long) {
    private val shift = shiftTicks and TIMESTAMP_MASK

    /**
     * 尚未输出的字节: 探测包长所需的前缀, 以及跨调用的不足一个包的尾巴. 包长一旦确定, 完整的包立即改写输出,
     * 所以内存占用是常数级, 不会缓存整个分片.
     */
    private var held = ByteArray(INITIAL_HELD_CAPACITY)
    private var heldSize = 0

    /** 下一个包的同步字节在 [held] 中的下标. 首个同步字节之前是源站给的垃圾前缀, 原样转发. */
    private var syncStart = -1

    /** 相邻两个同步字节的间距: 188 (标准), 192 (M2TS, 每包前 4 字节时间戳) 或 204 (带 RS 校验). */
    private var stride = 0

    private var passthrough = false

    /**
     * 吸收 [buffer] 的 [offset] 起 [length] 字节, 把其中完整的包改写后写入 [sink].
     *
     * 允许任意切分, 包括切在 TS 包中间: 跨调用的半个包会留到下次.
     */
    suspend fun rewrite(buffer: ByteArray, offset: Int, length: Int, sink: TsByteSink) {
        if (length <= 0) return
        if (passthrough) {
            sink.write(buffer, offset, length)
            return
        }
        append(buffer, offset, length)

        if (stride == 0 && !detectLayout(atEnd = false)) {
            // 攒够这些字节还找不到周期性的同步字节, 再多攒也没用: 输入不是 TS
            if (heldSize >= DETECT_LIMIT) giveUp(sink)
            return
        }
        emitCompletePackets(sink)
    }

    /**
     * 分片读完后调用, 输出剩余字节 (不足一个包的尾巴, 或始终没能认出包长时的全部暂存).
     */
    suspend fun finish(sink: TsByteSink) {
        if (!passthrough && heldSize > 0) {
            if (stride == 0) detectLayout(atEnd = true)
            if (stride != 0) emitCompletePackets(sink)
        }
        flushHeld(sink)
    }

    private suspend fun giveUp(sink: TsByteSink) {
        passthrough = true
        flushHeld(sink)
    }

    private suspend fun flushHeld(sink: TsByteSink) {
        if (heldSize > 0) {
            sink.write(held, 0, heldSize)
            heldSize = 0
        }
    }

    private fun append(buffer: ByteArray, offset: Int, length: Int) {
        if (heldSize + length > held.size) {
            var capacity = held.size
            while (capacity < heldSize + length) capacity *= 2
            held = held.copyOf(capacity)
        }
        buffer.copyInto(held, heldSize, offset, offset + length)
        heldSize += length
    }

    /**
     * 探测同步字节位置与包间距. 要连着看到 [CONFIRMATIONS] 个同步字节才认: 单个 0x47 在任何数据里都很常见,
     * 只看一两个会把非 TS 数据误判成 TS 而改坏它.
     *
     * @return 是否已经确定. [atEnd] 为 true 时数据不会再多了, 放宽到两次确认 (分片至少四个包时仍能认出).
     */
    private fun detectLayout(atEnd: Boolean): Boolean {
        val searchLimit = minOf(heldSize, MAX_SYNC_SEARCH)
        for (start in 0 until searchLimit) {
            if (held[start] != SYNC_BYTE) continue
            for (candidate in STRIDES) {
                var confirmed = 0
                var truncated = false
                for (k in 1..CONFIRMATIONS) {
                    val at = start + k * candidate
                    if (at >= heldSize) {
                        truncated = true
                        break
                    }
                    if (held[at] != SYNC_BYTE) break
                    confirmed++
                }
                if (confirmed == CONFIRMATIONS || (atEnd && truncated && confirmed >= 2)) {
                    syncStart = start
                    stride = candidate
                    return true
                }
                if (truncated && !atEnd) return false // 数据还不够判定, 等下一块
            }
        }
        return false
    }

    private suspend fun emitCompletePackets(sink: TsByteSink) {
        var packet = syncStart
        var processed = false
        while (packet + PACKET_SIZE <= heldSize) {
            shiftPacket(held, packet, shift)
            packet += stride
            processed = true
        }
        if (!processed) return
        // packet 是下一个同步字节应在的位置. stride 大于 188 时它可能还没收到, 此时余下的间隙字节要记进 syncStart,
        // 否则下一轮会把间隙当成包头, 静默地停止改写
        val end = minOf(packet, heldSize)
        sink.write(held, 0, end)
        held.copyInto(held, 0, end, heldSize)
        heldSize -= end
        syncStart = packet - end
    }

    companion object {
        private const val PACKET_SIZE = TsPacketReader.PACKET_SIZE
        private const val SYNC_BYTE = TsPacketReader.SYNC_BYTE
        private val STRIDES = intArrayOf(188, 192, 204)

        private const val TICKS_PER_MILLI = 90L // 90kHz
        private const val TIMESTAMP_MASK = 0x1_FFFF_FFFFL // 33 位, 90kHz 下约 26.5 小时回绕
        private const val TIMESTAMP_HALF = 0x0_FFFF_FFFFL

        private const val INITIAL_HELD_CAPACITY = 4 * 1024
        private const val MAX_SYNC_SEARCH = 1024
        private const val CONFIRMATIONS = 3
        private const val DETECT_LIMIT = MAX_SYNC_SEARCH + CONFIRMATIONS * 204

        /**
         * 把一个 discontinuity 组接到播放列表首组时间轴上的平移量: 平移后, 组首片的首个视频 PTS 与首组首片的
         * 首个视频 PTS 之差, 等于两组在播放列表中的起点之差.
         *
         * 首组不平移, 其余组都对齐到它. 按组而不是按分片定平移量: 组内各片的时间戳本来就首尾相接,
         * 逐片对齐到 `#EXTINF` 累加出的起点反而会在每个片界引入 `#EXTINF` 舍入误差和音视频先后造成的跳变
         * (测试夹具的首片音频比视频早 128 毫秒, 逐片对齐后第二片起整体回退 128 毫秒).
         * 以视频 PTS 为基准: 广告过滤已经探测过各组首片的这个值, 转发分片时不必再等锚点.
         *
         * 广告组平移后仍可能有音频早于前一段正片的末尾 (音频先于视频的那几十毫秒), 只发生在拼接点上.
         * 首组之后的组起点至少晚于首组一个组的时长, 平移后不会变成负值.
         *
         * @param groupStartMillis 组在播放列表时间轴上的起点, 相对首组.
         */
        fun spliceShiftTicks(groupStartMillis: Long, groupFirstPtsTicks: Long, referenceFirstPtsTicks: Long): Long {
            val elapsed = (groupFirstPtsTicks - referenceFirstPtsTicks) and TIMESTAMP_MASK
            val signedElapsed = if (elapsed > TIMESTAMP_HALF) elapsed - (TIMESTAMP_MASK + 1) else elapsed
            return (groupStartMillis * TICKS_PER_MILLI - signedElapsed) and TIMESTAMP_MASK
        }

        /**
         * 把 [base] 处 TS 包里的所有时间戳加上 [shift].
         */
        private fun shiftPacket(b: ByteArray, base: Int, shift: Long) {
            if (b[base] != SYNC_BYTE) return // 失同步, 不猜
            val adaptationFieldControl = (b[base + 3].toInt() ushr 4) and 0x3
            if (adaptationFieldControl and 0x2 != 0) {
                val length = b[base + 4].toInt() and 0xFF
                if (length > 0 && base + 5 + length <= base + PACKET_SIZE) {
                    val flags = b[base + 5].toInt() and 0xFF
                    var at = base + 6
                    if (flags and 0x10 != 0) { // PCR
                        if (at + 6 <= base + 5 + length) shiftPcr(b, at, shift)
                        at += 6
                    }
                    if (flags and 0x08 != 0) { // OPCR
                        if (at + 6 <= base + 5 + length) shiftPcr(b, at, shift)
                    }
                }
            }
            val pes = TsPacketReader.pesHeaderOffset(b, base) ?: return
            val flags = (b[pes + 7].toInt() ushr 6) and 0x3
            if (flags and 0x2 != 0 && pes + 14 <= base + PACKET_SIZE) shiftTimestamp(b, pes + 9, shift)
            if (flags == 0x3 && pes + 19 <= base + PACKET_SIZE) shiftTimestamp(b, pes + 14, shift)
        }

        private fun shiftTimestamp(b: ByteArray, at: Int, shift: Long) {
            val value = (TsPacketReader.readTimestamp(b, at) + shift) and TIMESTAMP_MASK
            b[at] = ((b[at].toInt() and 0xF0) or (((value ushr 30).toInt() and 0x07) shl 1) or 0x01).toByte()
            b[at + 1] = ((value ushr 22).toInt() and 0xFF).toByte()
            b[at + 2] = ((((value ushr 15).toInt() and 0x7F) shl 1) or 0x01).toByte()
            b[at + 3] = ((value ushr 7).toInt() and 0xFF).toByte()
            b[at + 4] = (((value.toInt() and 0x7F) shl 1) or 0x01).toByte()
        }

        /** PCR 是 33 位 90kHz base 加 9 位 27MHz extension. 平移只动 base, extension 原样保留. */
        private fun shiftPcr(b: ByteArray, at: Int, shift: Long) {
            val base = ((b[at].toLong() and 0xFF) shl 25) or
                    ((b[at + 1].toLong() and 0xFF) shl 17) or
                    ((b[at + 2].toLong() and 0xFF) shl 9) or
                    ((b[at + 3].toLong() and 0xFF) shl 1) or
                    ((b[at + 4].toLong() and 0xFF) ushr 7)
            val extension = ((b[at + 4].toInt() and 0x01) shl 8) or (b[at + 5].toInt() and 0xFF)
            val shifted = (base + shift) and TIMESTAMP_MASK
            b[at] = ((shifted ushr 25).toInt() and 0xFF).toByte()
            b[at + 1] = ((shifted ushr 17).toInt() and 0xFF).toByte()
            b[at + 2] = ((shifted ushr 9).toInt() and 0xFF).toByte()
            b[at + 3] = ((shifted ushr 1).toInt() and 0xFF).toByte()
            b[at + 4] = ((((shifted.toInt() and 0x01) shl 7) or 0x7E) or ((extension ushr 8) and 0x01)).toByte()
            b[at + 5] = (extension and 0xFF).toByte()
        }
    }
}
