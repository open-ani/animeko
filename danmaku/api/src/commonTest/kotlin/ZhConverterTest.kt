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
import kotlin.test.assertTrue

class ZhConverterTest {
    private fun toSimplified(text: String) = ZhConverter.convert(text, ZhConversion.TO_SIMPLIFIED)
    private fun toTraditional(text: String) = ZhConverter.convert(text, ZhConversion.TO_TRADITIONAL)

    @Test
    fun `NONE returns the same instance`() {
        val text = "這個動畫真好看"
        assertSame(text, ZhConverter.convert(text, ZhConversion.NONE))
    }

    @Test
    fun `empty text`() {
        assertEquals("", toSimplified(""))
        assertEquals("", toTraditional(""))
    }

    @Test
    fun `text without any convertible char returns the same instance`() {
        val text = "abc 123 你好"
        assertSame(text, toSimplified(text))
    }

    @Test
    fun `to simplified`() {
        assertEquals("这个动画真好看", toSimplified("這個動畫真好看"))
        assertEquals("剧透警告", toSimplified("劇透警告"))
        assertEquals("弹幕", toSimplified("彈幕"))
    }

    @Test
    fun `to traditional`() {
        assertEquals("這個動畫真好看", toTraditional("这个动画真好看"))
        assertEquals("劇透警告", toTraditional("剧透警告"))
        assertEquals("彈幕", toTraditional("弹幕"))
    }

    @Test
    fun `round trip on common phrases`() {
        for (simplified in listOf(
            "这个动画真好看",
            "前方高能",
            "剧透警告",
            "弹幕护体",
            "泪目了",
            "谁能想到",
            "开始表演",
        )) {
            assertEquals(simplified, toSimplified(toTraditional(simplified)), "round trip failed for $simplified")
        }
    }

    @Test
    fun `non chinese chars untouched`() {
        assertEquals("awsl 233 ！这->这", toSimplified("awsl 233 ！這->這"))
    }

    @Test
    fun `table covers a reasonable amount of chars`() {
        // 防止生成的表被意外清空
        assertTrue(toSimplified("龍鳳龜齒黨個").length == 6)
        assertEquals("龙凤龟齿党个", toSimplified("龍鳳龜齒黨個"))
    }

    @Test
    fun `preprocess converts before merging so both scripts merge together`() {
        fun danmaku(timeMillis: Long, text: String) = DanmakuInfo(
            id = "$timeMillis",
            serviceId = DanmakuServiceId("dummy"),
            senderId = "sender",
            content = DanmakuContent(timeMillis, 0, text, DanmakuLocation.NORMAL),
        )

        val result = DanmakuPreprocessor.preprocess(
            listOf(danmaku(0, "這個動畫"), danmaku(1000, "这个动画")),
            DanmakuPreprocessConfig(enableMerge = true, zhConversion = ZhConversion.TO_SIMPLIFIED),
        )
        assertEquals(listOf("这个动画 ×2"), result.map { it.text })
    }
}
