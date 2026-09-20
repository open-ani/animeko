/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.media.player.prefetch

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TorrentPrefetchRangeTest {
    @Test
    fun `maps time linearly with margin on both sides`() {
        // 100 秒, 1000 字节: 1 字节 = 100 毫秒
        val range = estimateTorrentByteRange(
            MediaTimeRange(50_000, 60_000),
            durationMillis = 100_000,
            fileLength = 1000,
            marginMillis = 10_000,
        )
        assertEquals(400L..700L, range)
    }

    @Test
    fun `clamps to file bounds`() {
        val range = estimateTorrentByteRange(
            MediaTimeRange(95_000, 130_000),
            durationMillis = 100_000,
            fileLength = 1000,
            marginMillis = 10_000,
        )
        assertEquals(850L..999L, range)
        val head = estimateTorrentByteRange(
            MediaTimeRange(0, 5_000),
            durationMillis = 100_000,
            fileLength = 1000,
            marginMillis = 10_000,
        )
        assertEquals(0L..150L, head)
    }

    @Test
    fun `returns null when duration or length unknown`() {
        assertNull(estimateTorrentByteRange(MediaTimeRange(0, 1000), durationMillis = 0, fileLength = 1000))
        assertNull(estimateTorrentByteRange(MediaTimeRange(0, 1000), durationMillis = 1000, fileLength = 0))
        assertNull(estimateTorrentByteRange(MediaTimeRange(200_000, 210_000), durationMillis = 100_000, fileLength = 1000, marginMillis = 0))
    }
}
