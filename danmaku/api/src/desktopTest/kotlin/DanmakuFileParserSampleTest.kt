/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.danmaku.api

import me.him188.ani.test.readTestResourceAsString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 用真实的 (截断过的) 弹幕文件跑一遍解析.
 *
 * `danmaku-sample-bilibili.xml` 是从 Bilibili av1176840 抽样得到的 152 条弹幕, 保留了滚动/顶部/底部/逆向/高级
 * 多种模式, 多种颜色, 以及大量重复的刷屏弹幕. `danmaku-sample-dandanplay.json` 是从同一份数据转换出来的
 * dandanplay 格式.
 */
class DanmakuFileParserSampleTest {
    private val xmlSample get() = readTestResourceAsString("/danmaku-sample-bilibili.xml")
    private val jsonSample get() = readTestResourceAsString("/danmaku-sample-dandanplay.json")

    @Test
    fun `parse bilibili sample`() {
        val result = DanmakuFileParser.parse(xmlSample)

        assertEquals(DanmakuFileFormat.BilibiliXml, result.format)
        // 152 条里有 6 条是高级弹幕 (mode 7), 会被跳过
        assertEquals(146, result.list.size)
        assertEquals(6, result.skippedCount)

        val byLocation = result.list.groupingBy { it.location }.eachCount()
        assertEquals(80, byLocation[DanmakuLocation.NORMAL]) // 77 条 mode 1 + 3 条 mode 6
        assertEquals(38, byLocation[DanmakuLocation.TOP])
        assertEquals(28, byLocation[DanmakuLocation.BOTTOM])

        assertTrue(result.list.all { it.serviceId == DanmakuServiceId.LocalFile })
        assertTrue(result.list.all { it.text.isNotBlank() })
        assertTrue(result.list.all { it.playTimeMillis >= 0 })
        assertTrue(result.list.all { it.color in 0..0xFFFFFF })
        assertTrue(result.list.map { it.id }.toSet().size == result.list.size, "ids must be unique")
        // 不止一种颜色
        assertTrue(result.list.map { it.color }.toSet().size > 1)
    }

    @Test
    fun `bilibili sample goes through the preprocessing pipeline`() {
        val list = DanmakuFileParser.parse(xmlSample).list
        val sorted = list.map { DanmakuSanitizer.sanitize(it) }.sortedBy { it.playTimeMillis }
        val merged = DanmakuMerger.merge(sorted)

        assertTrue(merged.size < sorted.size, "刷屏弹幕应当被合并: ${merged.size} vs ${sorted.size}")
        assertTrue(merged.any { it.text.contains("×") }, "应当有带 ×N 计数的弹幕")
        assertEquals(merged.sortedBy { it.playTimeMillis }, merged, "合并后仍然按时间升序")
    }

    @Test
    fun `parse dandanplay sample`() {
        val result = DanmakuFileParser.parse(jsonSample)

        assertEquals(DanmakuFileFormat.DandanplayJson, result.format)
        assertEquals(60, result.list.size)
        assertEquals(0, result.skippedCount)
        assertTrue(result.list.all { it.senderId.startsWith("[BiliBili]") })
        assertTrue(result.list.map { it.id }.toSet().size == result.list.size, "ids must be unique")
    }

    @Test
    fun `detects format of both samples`() {
        assertEquals(DanmakuFileFormat.BilibiliXml, DanmakuFileParser.detectFormat(xmlSample))
        assertEquals(DanmakuFileFormat.DandanplayJson, DanmakuFileParser.detectFormat(jsonSample))
    }
}
