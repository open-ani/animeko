/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.media.hls

import kotlinx.coroutines.test.runTest
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * [TsTimestampRewriter] 本身是平台无关的纯逻辑, 但测试放在 desktopTest: 真实 TS 夹具
 * (`src/androidDeviceTest/assets/hls/`, 由 build.gradle.kts 挂到 desktopTest 的 resources) 只有 JVM 侧读得到,
 * 而用合成字节自测改写逻辑等于把刚写下的位运算再断言一遍, 抓不到对 TS 格式的理解错误. ffprobe 交叉验证同理只能在 JVM 跑.
 */
class TsTimestampRewriterTest {
    private class Collector : TsByteSink {
        private val out = ArrayList<Byte>()
        override suspend fun write(buffer: ByteArray, offset: Int, length: Int) {
            for (i in offset until offset + length) out.add(buffer[i])
        }

        fun bytes(): ByteArray = out.toByteArray()
    }

    private suspend fun rewrite(input: ByteArray, targetStartMillis: Long, chunk: Int = Int.MAX_VALUE): ByteArray {
        val rewriter = TsTimestampRewriter(targetStartMillis)
        val collector = Collector()
        var at = 0
        while (at < input.size) {
            val size = minOf(chunk, input.size - at)
            rewriter.rewrite(input, at, size, collector)
            at += size
        }
        rewriter.finish(collector)
        return collector.bytes()
    }

    private fun fixture(path: String): ByteArray =
        checkNotNull(javaClass.getResourceAsStream(path)) { "missing fixture $path" }.use { it.readBytes() }

    // region 用独立实现读出所有时间戳, 不复用生产代码的解析

    private class Timestamp(val packet: Int, val kind: String, val pid: Int, val value: Long)

    private fun readTimestamps(ts: ByteArray): List<Timestamp> {
        val result = ArrayList<Timestamp>()
        fun read(at: Int): Long =
            ((ts[at].toLong() and 0x0E) shl 29) or ((ts[at + 1].toLong() and 0xFF) shl 22) or
                    ((ts[at + 2].toLong() and 0xFE) shl 14) or ((ts[at + 3].toLong() and 0xFF) shl 7) or
                    ((ts[at + 4].toLong() and 0xFE) shr 1)

        var index = 0
        var base = 0
        while (base + 188 <= ts.size) {
            check(ts[base] == 0x47.toByte()) { "lost sync at packet $index" }
            val pid = ((ts[base + 1].toInt() and 0x1F) shl 8) or (ts[base + 2].toInt() and 0xFF)
            val pusi = (ts[base + 1].toInt() ushr 6) and 0x1
            val afc = (ts[base + 3].toInt() ushr 4) and 0x3
            var payload = base + 4
            if (afc and 0x2 != 0) {
                val length = ts[base + 4].toInt() and 0xFF
                payload = base + 5 + length
                if (length > 0 && (ts[base + 5].toInt() and 0x10) != 0) {
                    val at = base + 6
                    val pcr = ((ts[at].toLong() and 0xFF) shl 25) or ((ts[at + 1].toLong() and 0xFF) shl 17) or
                            ((ts[at + 2].toLong() and 0xFF) shl 9) or ((ts[at + 3].toLong() and 0xFF) shl 1) or
                            ((ts[at + 4].toLong() and 0xFF) ushr 7)
                    result += Timestamp(index, "PCR", pid, pcr)
                }
            }
            if (afc and 0x1 != 0 && pusi == 1 && payload + 19 <= base + 188 &&
                ts[payload] == 0.toByte() && ts[payload + 1] == 0.toByte() && ts[payload + 2] == 1.toByte()
            ) {
                val streamId = ts[payload + 3].toInt() and 0xFF
                if (streamId != 0xBE && streamId != 0xBF && (ts[payload + 6].toInt() and 0xC0) == 0x80) {
                    val flags = (ts[payload + 7].toInt() ushr 6) and 0x3
                    if (flags and 0x2 != 0) result += Timestamp(index, "PTS", pid, read(payload + 9))
                    if (flags == 0x3) result += Timestamp(index, "DTS", pid, read(payload + 14))
                }
            }
            index++
            base += 188
        }
        return result
    }

    // endregion

    // region 合成包, 用来构造真实夹具里没有的形状 (B 帧的 DTS, 33 位回绕)

    private fun encodeTimestamp(out: ByteArray, at: Int, marker: Int, value: Long) {
        out[at] = ((marker shl 4) or (((value ushr 30).toInt() and 0x07) shl 1) or 0x01).toByte()
        out[at + 1] = ((value ushr 22).toInt() and 0xFF).toByte()
        out[at + 2] = ((((value ushr 15).toInt() and 0x7F) shl 1) or 0x01).toByte()
        out[at + 3] = ((value ushr 7).toInt() and 0xFF).toByte()
        out[at + 4] = (((value.toInt() and 0x7F) shl 1) or 0x01).toByte()
    }

    private fun pesPacket(pid: Int, pts: Long?, dts: Long? = null, pcr: Long? = null): ByteArray {
        val packet = ByteArray(188) { 0xFF.toByte() }
        packet[0] = 0x47
        packet[1] = (0x40 or ((pid ushr 8) and 0x1F)).toByte()
        packet[2] = (pid and 0xFF).toByte()
        var payload = 4
        if (pcr != null) {
            packet[3] = 0x30
            packet[4] = 7
            packet[5] = 0x10
            packet[6] = ((pcr ushr 25).toInt() and 0xFF).toByte()
            packet[7] = ((pcr ushr 17).toInt() and 0xFF).toByte()
            packet[8] = ((pcr ushr 9).toInt() and 0xFF).toByte()
            packet[9] = ((pcr ushr 1).toInt() and 0xFF).toByte()
            packet[10] = ((((pcr.toInt() and 0x01) shl 7)) or 0x7E).toByte()
            packet[11] = 0
            payload = 12
        } else {
            packet[3] = 0x10
        }
        packet[payload] = 0
        packet[payload + 1] = 0
        packet[payload + 2] = 1
        packet[payload + 3] = 0xE0.toByte()
        packet[payload + 4] = 0
        packet[payload + 5] = 0
        packet[payload + 6] = 0x80.toByte()
        val flags = when {
            pts != null && dts != null -> 0x3
            pts != null -> 0x2
            else -> 0x0
        }
        packet[payload + 7] = (flags shl 6).toByte()
        packet[payload + 8] = (if (flags == 0x3) 10 else if (flags == 0x2) 5 else 0).toByte()
        if (pts != null) encodeTimestamp(packet, payload + 9, if (dts != null) 0x3 else 0x2, pts)
        if (dts != null) encodeTimestamp(packet, payload + 14, 0x1, dts)
        return packet
    }

    /** 只有 PCR 没有 PES 的填充包, 用来验证第一个 PES 之前的 PCR 也被平移. */
    private fun pcrOnlyPacket(pid: Int, pcr: Long): ByteArray {
        val packet = ByteArray(188) { 0xFF.toByte() }
        packet[0] = 0x47
        packet[1] = ((pid ushr 8) and 0x1F).toByte()
        packet[2] = (pid and 0xFF).toByte()
        packet[3] = 0x20
        packet[4] = 183.toByte() // adaptation field 占满整个包, 没有 payload
        packet[5] = 0x10
        packet[6] = ((pcr ushr 25).toInt() and 0xFF).toByte()
        packet[7] = ((pcr ushr 17).toInt() and 0xFF).toByte()
        packet[8] = ((pcr ushr 9).toInt() and 0xFF).toByte()
        packet[9] = ((pcr ushr 1).toInt() and 0xFF).toByte()
        packet[10] = ((((pcr.toInt() and 0x01) shl 7)) or 0x7E).toByte()
        packet[11] = 0
        return packet
    }

    private fun nullPacket(): ByteArray = ByteArray(188) { 0xFF.toByte() }.also {
        it[0] = 0x47
        it[1] = 0x1F
        it[2] = 0xFF.toByte()
        it[3] = 0x10
    }

    /** 不足四个包无法确认包间距 (单个 0x47 不足以判定是 TS), 所以用空包补足. */
    private fun stream(vararg packets: ByteArray): ByteArray {
        val all = packets.toMutableList()
        while (all.size < 4) all += nullPacket()
        val out = ByteArray(all.size * 188)
        all.forEachIndexed { i, p -> p.copyInto(out, i * 188) }
        return out
    }

    // endregion

    @Test
    fun `真实分片改写后最早的时间戳落在目标起点`() = runTest {
        // withads.m3u8 里 ad000.ts 就是 vod/seg000.ts 的副本, 位于播放列表 48 秒处
        val input = fixture("/hls/ads/ad000.ts")
        val output = rewrite(input, targetStartMillis = 48_000)

        assertEquals(input.size, output.size, "改写必须等长, 否则 Content-Length 对不上")

        val before = readTimestamps(input)
        val after = readTimestamps(output)
        assertTrue(before.size > 20, "夹具里应当有足够多的时间戳, 实际 ${before.size}")
        assertEquals(before.size, after.size)

        // 锚点只看 PES 的 PTS/DTS. 夹具的首个 PCR (0.83 秒) 早于首个 PTS (音频 1.4 秒), 平移后仍在目标起点之前,
        // 这是对的: PCR 本就先于首帧送达
        assertEquals(48_000 * 90L, after.filter { it.kind != "PCR" }.minOf { it.value })

        // 所有时间戳 (PTS 与 PCR) 必须平移同一个量, 否则解复用器会用 PCR 反推出错乱的时间
        val delta = (48_000 * 90L) - before.filter { it.kind != "PCR" }.minOf { it.value }
        for ((old, new) in before.zip(after)) {
            assertEquals(old.kind, new.kind)
            assertEquals((old.value + delta) and 0x1_FFFF_FFFFL, new.value, "packet ${old.packet} ${old.kind}")
        }
    }

    @Test
    fun `真实分片按任意大小切分喂入, 输出与一次性喂入完全相同`() = runTest {
        val input = fixture("/hls/ads/ad000.ts")
        val expected = rewrite(input, targetStartMillis = 48_000)
        // 1 和 7 会把 PES 头切开, 187 和 189 会让每次切分的相位不断移动
        for (chunk in listOf(1, 7, 187, 188, 189, 1000, 64 * 1024)) {
            assertContentEquals(expected, rewrite(input, 48_000, chunk), "chunk=$chunk")
        }
    }

    @Test
    fun `改写后的分片仍能被 ffprobe 解析, 且时间轴落在目标位置`() = runTest {
        val ffprobe = findExecutable("ffprobe")
        if (ffprobe == null) {
            println("[TsTimestampRewriter] skipped: ffprobe not installed")
            return@runTest
        }
        val output = rewrite(fixture("/hls/ads/ad000.ts"), targetStartMillis = 48_000)
        val file = File.createTempFile("ts-rewrite", ".ts")
        try {
            file.writeBytes(output)
            val process = ProcessBuilder(
                ffprobe, "-v", "error", "-show_entries", "packet=pts_time", "-of", "csv=p=0", file.absolutePath,
            ).redirectErrorStream(true).start()
            val text = process.inputStream.bufferedReader().readText()
            check(process.waitFor(60, TimeUnit.SECONDS))
            assertEquals(0, process.exitValue(), text)

            val times = text.lines().mapNotNull { it.trim().trimEnd(',').toDoubleOrNull() }
            assertTrue(times.size > 20, "ffprobe 应当读出足够多的包: $text")
            assertEquals(48.0, times.min(), 0.001)
            // 原分片跨 0..3 秒, 平移后应当落在 48..51 秒
            assertTrue(times.max() < 51.5, "最大时间 ${times.max()} 超出分片时长")
        } finally {
            file.delete()
        }
    }

    @Test
    fun `同一个 PES 的 PTS 与 DTS 平移同一个量`() = runTest {
        val pts = 50L * 90_000
        val dts = pts - 2 * 3_750 // 两帧 24fps 的重排延迟
        val input = stream(pesPacket(pid = 256, pts = pts, dts = dts))
        val after = readTimestamps(rewrite(input, targetStartMillis = 10_000))

        val newDts = assertNotNull(after.firstOrNull { it.kind == "DTS" }).value
        val newPts = assertNotNull(after.firstOrNull { it.kind == "PTS" }).value
        // 锚点取更早的 DTS, 保证输出不早于目标起点
        assertEquals(10_000 * 90L, newDts)
        assertEquals(pts - dts, newPts - newDts)
    }

    @Test
    fun `第一个 PES 之前的 PCR 也被平移`() = runTest {
        val input = stream(
            pcrOnlyPacket(pid = 256, pcr = 100L * 90_000),
            pesPacket(pid = 256, pts = 101L * 90_000, pcr = 101L * 90_000),
            pesPacket(pid = 256, pts = 102L * 90_000),
        )
        val after = readTimestamps(rewrite(input, targetStartMillis = 7_000))
        // 锚点是第一个 PTS (101 秒), 早于它的 PCR 平移后落在目标起点之前, 这是正确的: PCR 本就先于首帧送达
        assertEquals(listOf("PCR", "PCR", "PTS", "PTS"), after.map { it.kind })
        val delta = 7_000 * 90L - 101L * 90_000
        assertEquals(listOf(100L * 90_000 + delta, 101L * 90_000 + delta), after.filter { it.kind == "PCR" }.map { it.value })
        assertEquals(7_000 * 90L, after.first { it.kind == "PTS" }.value)
    }

    @Test
    fun `输入时间戳跨越 33 位回绕`() = runTest {
        val wrapPoint = 0x1_FFFF_FFFFL + 1
        val first = wrapPoint - 9_000 // 距回绕 0.1 秒
        val second = (first + 90_000) and 0x1_FFFF_FFFFL // 回绕后变成很小的值
        assertTrue(second < first, "构造前提: 第二个时间戳已经回绕")

        val input = stream(
            pesPacket(pid = 256, pts = first),
            pesPacket(pid = 256, pts = second),
        )
        val after = readTimestamps(rewrite(input, targetStartMillis = 100))
        assertEquals(listOf(100L * 90, 100L * 90 + 90_000), after.map { it.value })
    }

    @Test
    fun `输出时间戳跨越 33 位回绕`() = runTest {
        val target = 95_443_000L // 2^33 刻度约合 95443.7 秒, 从这里起 10 秒就越过回绕点
        val input = stream(
            pesPacket(pid = 256, pts = 0),
            pesPacket(pid = 256, pts = 900_000), // 10 秒后, 平移后越过回绕点
        )
        val after = readTimestamps(rewrite(input, targetStartMillis = target))
        assertEquals(target * 90 and 0x1_FFFF_FFFFL, after[0].value)
        assertEquals((target * 90 + 900_000) and 0x1_FFFF_FFFFL, after[1].value)
        assertTrue(after[1].value < after[0].value, "构造前提: 输出已经回绕")
    }

    @Test
    fun `非 TS 输入原样通过`() = runTest {
        // fMP4 分片: 代理若误判成 TS 去改写会直接改坏它
        val input = fixture("/hls/fmp4/init.mp4")
        assertContentEquals(input, rewrite(input, targetStartMillis = 48_000))
        assertContentEquals(input, rewrite(input, targetStartMillis = 48_000, chunk = 333))
    }

    @Test
    fun `没有时间戳的 TS 原样通过`() = runTest {
        val input = stream(
            pcrOnlyPacket(pid = 256, pcr = 0),
            pcrOnlyPacket(pid = 256, pcr = 100),
            pcrOnlyPacket(pid = 256, pcr = 200),
            pcrOnlyPacket(pid = 256, pcr = 300),
        )
        assertContentEquals(input, rewrite(input, targetStartMillis = 48_000))
    }

    private fun findExecutable(name: String): String? {
        val candidates = if (System.getProperty("os.name").startsWith("Windows")) listOf("$name.exe", name) else listOf(name)
        val dirs = (System.getenv("PATH") ?: "").split(File.pathSeparator) + listOf("/opt/homebrew/bin", "/usr/local/bin")
        return dirs.flatMap { dir -> candidates.map { File(dir, it) } }.firstOrNull { it.canExecute() }?.absolutePath
    }
}
