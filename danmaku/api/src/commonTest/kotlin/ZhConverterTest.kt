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
    fun `phrase table fixes context dependent chars to traditional`() {
        assertEquals("頭髮", toTraditional("头发"))
        assertEquals("公主的頭髮", toTraditional("公主的头发"))
        // OpenCC 的基础词典用的是 "裏" 而不是 "裡"
        assertEquals("裏面", toTraditional("里面"))
        assertEquals("複雜", toTraditional("复杂"))
        assertEquals("遊戲", toTraditional("游戏"))
        assertEquals("製作", toTraditional("制作"))
    }

    @Test
    fun `phrase table fixes context dependent chars to simplified`() {
        assertEquals("头发", toSimplified("頭髮"))
        assertEquals("干活", toSimplified("幹活"))
        assertEquals("沉默", toSimplified("沈默"))
        assertEquals("松一口气", toSimplified("鬆一口氣"))
    }

    @Test
    fun `phrase table disambiguates 干`() {
        assertEquals("乾杯", toTraditional("干杯"))
        assertEquals("餅乾", toTraditional("饼干"))
        assertEquals("幹活", toTraditional("干活"))
        assertEquals("天干", toTraditional("天干"))
        assertEquals("干杯", toSimplified("乾杯"))
        assertEquals("饼干", toSimplified("餅乾"))
    }

    @Test
    fun `phrase whose value equals its key still blocks the char table`() {
        // 逐字转换会把 "后" 转成 "後", 词组表把它拦下来
        assertEquals("皇后", toTraditional("皇后"))
        assertEquals("天干", toTraditional("天干"))
    }

    @Test
    fun `longest match wins over shorter overlapping phrase`() {
        // "龙须" -> "龍鬚", "龙须面" -> "龍鬚麪", 必须取长的那条
        assertEquals("龍鬚", toTraditional("龙须"))
        assertEquals("龍鬚麪", toTraditional("龙须面"))
        assertEquals("頭髮", toTraditional("头发"))
        assertEquals("頭髮殼子", toTraditional("头发壳子"))
    }

    @Test
    fun `phrase at string boundaries`() {
        assertEquals("頭髮", toTraditional("头发")) // 整串就是一个词组
        assertEquals("頭髮絲", toTraditional("头发丝")) // 词组在开头
        assertEquals("剪頭髮", toTraditional("剪头发")) // 词组在结尾
        // "龙须面" 的前缀在字符串末尾被截断, 不能越界读
        assertEquals("看龍鬚", toTraditional("看龙须"))
    }

    @Test
    fun `phrase initial char that matches nothing returns the same instance`() {
        // "面" 是 "面条" 等词组的首字, 但单独出现时既匹配不上词组, 单字表里也没有它
        val text = "面"
        assertSame(text, toTraditional(text))
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
