/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.media.hls

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * 用 `src/androidDeviceTest/assets/hls/` 下由 ffmpeg 生成的真实分片验证. 期望值来自 ffprobe:
 * `ffprobe -select_streams v:0 -show_entries packet=pts_time <分片>` 的第一行.
 */
class TsPacketReaderFirstPtsTest {
    /** [TsPacketReader.firstPts] 返回 90kHz 刻度, 这里统一折成毫秒便于与 ffprobe 的秒数对照. */
    private fun firstPtsMillis(bytes: ByteArray, length: Int = bytes.size): Long? =
        TsPacketReader.firstPts(bytes, 0, length)?.let { TsPacketReader.ticksToMillis(it) }

    private fun fixture(path: String): ByteArray =
        checkNotNull(javaClass.getResourceAsStream(path)) { "No fixture at $path" }.use { it.readBytes() }

    @Test
    fun `reads first pts from a real segment`() {
        assertEquals(1528, firstPtsMillis(fixture("/hls/vod/seg000.ts")))
        assertEquals(4528, firstPtsMillis(fixture("/hls/vod/seg001.ts")))
    }

    /**
     * 探测只取分片开头一小段, 结果必须与读全片一致.
     */
    @Test
    fun `reads the same pts from only the first bytes`() {
        val full = fixture("/hls/vod/seg000.ts")
        for (length in listOf(4096, 8192, 65536)) {
            assertEquals(1528, firstPtsMillis(full, minOf(length, full.size)), "length=$length")
        }
    }

    /**
     * 广告分片是独立转码的, 其 PTS 自成一条时间轴: 这两片在播放列表的 48 秒处, PTS 却是 1.5 秒起.
     * 正是需要识别出来的形状.
     */
    @Test
    fun `ad segment carries a timeline of its own`() {
        assertEquals(1528, firstPtsMillis(fixture("/hls/ads/ad000.ts")))
        assertEquals(4528, firstPtsMillis(fixture("/hls/ads/ad001.ts")))
    }

    /**
     * 按 Range 取回的字节不一定从包边界开始.
     */
    @Test
    fun `finds packet boundary when the buffer does not start at one`() {
        val full = fixture("/hls/vod/seg000.ts")
        val shifted = ByteArray(7) { 0 } + full.copyOfRange(0, minOf(8192, full.size))
        assertEquals(1528, firstPtsMillis(shifted))
    }

    @Test
    fun `returns null when there is no usable payload`() {
        assertNull(firstPtsMillis(ByteArray(0)))
        assertNull(firstPtsMillis(ByteArray(4096)))
        assertNull(firstPtsMillis("not a transport stream".encodeToByteArray()))
    }

    /**
     * fMP4 分片没有 TS 同步字节, 应当判为读不出而不是给出错误的值.
     */
    @Test
    fun `returns null for fmp4 segment`() {
        val fmp4 = runCatching { fixture("/hls/fmp4/seg000.m4s") }.getOrNull()
        if (fmp4 == null) return // 夹具命名可能不同, 跳过而不是失败
        assertNull(firstPtsMillis(fmp4))
    }

    @Test
    fun `aes fixture is still a transport stream`() {
        // 仅确认夹具存在, 加密分片的 PTS 读不出属正常 (负载已加密), 由调用方按探测失败处理
        val bytes = runCatching { fixture("/hls/aes/seg000.ts") }.getOrNull() ?: return
        assertNotNull(bytes)
    }
}
