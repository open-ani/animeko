/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.danmaku.api

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class DanmakuMergerTest {
    private fun danmaku(
        timeMillis: Long,
        text: String,
        color: Int = 0xFFFFFF,
        location: DanmakuLocation = DanmakuLocation.NORMAL,
    ) = DanmakuInfo(
        id = "$timeMillis-$text",
        serviceId = DanmakuServiceId("dummy"),
        senderId = "sender",
        content = DanmakuContent(timeMillis, color, text, location),
    )

    private fun merge(list: List<DanmakuInfo>, windowMillis: Long = 40_000) =
        DanmakuMerger.merge(list, windowMillis)

    @Test
    fun `empty and single are returned as is`() {
        val empty = emptyList<DanmakuInfo>()
        assertSame(empty, merge(empty))
        val single = listOf(danmaku(0, "a"))
        assertSame(single, merge(single))
    }

    @Test
    fun `nothing to merge returns the same instance`() {
        val list = listOf(danmaku(0, "a"), danmaku(1000, "b"))
        assertSame(list, merge(list))
    }

    @Test
    fun `merges identical text within window`() {
        val list = listOf(
            danmaku(0, "前方高能"),
            danmaku(1000, "前方高能"),
            danmaku(2000, "前方高能"),
        )
        val result = merge(list)
        assertEquals(1, result.size)
        assertEquals("前方高能 ×3", result[0].text)
    }

    @Test
    fun `single occurrence keeps original text`() {
        val list = listOf(
            danmaku(0, "前方高能"),
            danmaku(1000, "前方高能"),
            danmaku(2000, "别的弹幕"),
        )
        val result = merge(list)
        assertEquals(2, result.size)
        assertEquals("前方高能 ×2", result[0].text)
        assertEquals("别的弹幕", result[1].text)
    }

    @Test
    fun `representative is the first danmaku and keeps its time color and location`() {
        val list = listOf(
            danmaku(500, "233", color = 0xFF0000, location = DanmakuLocation.TOP),
            danmaku(1500, "233", color = 0x00FF00, location = DanmakuLocation.BOTTOM),
        )
        val result = merge(list)
        assertEquals(1, result.size)
        assertEquals(500, result[0].playTimeMillis)
        assertEquals(0xFF0000, result[0].color)
        assertEquals(DanmakuLocation.TOP, result[0].location)
        assertEquals("233 ×2", result[0].text)
    }

    @Test
    fun `window boundary is inclusive`() {
        val list = listOf(danmaku(0, "a"), danmaku(40_000, "a"))
        val result = merge(list, windowMillis = 40_000)
        assertEquals(1, result.size)
        assertEquals("a ×2", result[0].text)
    }

    @Test
    fun `outside window starts a new cluster`() {
        val list = listOf(danmaku(0, "a"), danmaku(40_001, "a"))
        val result = merge(list, windowMillis = 40_000)
        assertEquals(2, result.size)
        assertEquals(listOf("a", "a"), result.map { it.text })
        assertEquals(listOf(0L, 40_001L), result.map { it.playTimeMillis })
    }

    @Test
    fun `window is anchored at the representative not the last member`() {
        // 0, 30000 合并到 0; 60000 距离代表 0 已经超过窗口, 另起一簇
        val list = listOf(danmaku(0, "a"), danmaku(30_000, "a"), danmaku(60_000, "a"))
        val result = merge(list, windowMillis = 40_000)
        assertEquals(2, result.size)
        assertEquals("a ×2", result[0].text)
        assertEquals(0, result[0].playTimeMillis)
        assertEquals("a", result[1].text)
        assertEquals(60_000, result[1].playTimeMillis)
    }

    @Test
    fun `merges after normalization`() {
        val list = listOf(
            danmaku(0, "AWSL"),
            danmaku(1000, "awsl！！"),
            danmaku(2000, "ａｗｓｌ"),
        )
        val result = merge(list)
        assertEquals(1, result.size)
        // 保留第一条的原始文本
        assertEquals("AWSL ×3", result[0].text)
    }

    @Test
    fun `pure punctuation danmaku are never merged`() {
        val list = listOf(
            danmaku(0, "？？？"),
            danmaku(1000, "？？？"),
            danmaku(2000, "..."),
        )
        assertSame(list, merge(list))
    }

    @Test
    fun `interleaved clusters keep time order`() {
        val list = listOf(
            danmaku(0, "a"),
            danmaku(100, "b"),
            danmaku(200, "a"),
            danmaku(300, "b"),
            danmaku(400, "c"),
        )
        val result = merge(list)
        assertEquals(listOf("a ×2", "b ×2", "c"), result.map { it.text })
        assertEquals(listOf(0L, 100L, 400L), result.map { it.playTimeMillis })
    }

    @Test
    fun `preprocess respects enableMerge flag`() {
        val list = listOf(danmaku(0, "a"), danmaku(100, "a"))
        assertSame(list, DanmakuPreprocessor.preprocess(list, DanmakuPreprocessConfig(enableMerge = false)))
        assertEquals(
            listOf("a ×2"),
            DanmakuPreprocessor.preprocess(list, DanmakuPreprocessConfig(enableMerge = true)).map { it.text },
        )
    }
}
