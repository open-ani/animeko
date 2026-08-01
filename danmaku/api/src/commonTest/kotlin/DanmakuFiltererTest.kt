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

class DanmakuFiltererTest {
    private fun danmaku(text: String) = DanmakuInfo(
        id = text,
        serviceId = DanmakuServiceId("dummy"),
        senderId = "sender",
        content = DanmakuContent(0, 0, text, DanmakuLocation.NORMAL),
    )

    private fun texts(list: List<DanmakuInfo>) = list.map { it.text }

    @Test
    fun `empty spec returns the same instance`() {
        val list = listOf(danmaku("a"))
        assertSame(list, DanmakuFilterer().filter(list, DanmakuFilterSpec.Empty))
    }

    @Test
    fun `regex filter`() {
        val list = listOf(danmaku("前方高能"), danmaku("好看"))
        assertEquals(
            listOf("好看"),
            texts(DanmakuFilterer().filter(list, DanmakuFilterSpec(regexPatterns = listOf("高能")))),
        )
    }

    @Test
    fun `keyword filter matches substring`() {
        val list = listOf(danmaku("这里有剧透警告"), danmaku("好看"))
        assertEquals(
            listOf("好看"),
            texts(DanmakuFilterer().filter(list, DanmakuFilterSpec(keywords = listOf("剧透")))),
        )
    }

    @Test
    fun `keyword filter ignores case and full width`() {
        val list = listOf(danmaku("AWSL"), danmaku("ａｗｓｌ！"), danmaku("好看"))
        assertEquals(
            listOf("好看"),
            texts(DanmakuFilterer().filter(list, DanmakuFilterSpec(keywords = listOf("awsl")))),
        )
    }

    @Test
    fun `blank keyword is ignored`() {
        val list = listOf(danmaku("好看"), danmaku("不错"))
        // 归一化之后为空串的关键词会被丢掉, 否则会把所有弹幕都屏蔽掉
        assertEquals(
            listOf("好看", "不错"),
            texts(DanmakuFilterer().filter(list, DanmakuFilterSpec(keywords = listOf("   ", "。。。")))),
        )
    }

    @Test
    fun `keyword filter respects zh conversion`() {
        val list = listOf(danmaku("剧透警告"), danmaku("好看"))
        // 弹幕已转成简体, 用繁体写的关键词也要拦得住
        assertEquals(
            listOf("好看"),
            texts(
                DanmakuFilterer().filter(
                    list,
                    DanmakuFilterSpec(keywords = listOf("劇透"), zhConversion = ZhConversion.TO_SIMPLIFIED),
                ),
            ),
        )
    }

    @Test
    fun `regex and keyword are applied together`() {
        val list = listOf(danmaku("前方高能"), danmaku("剧透警告"), danmaku("好看"))
        assertEquals(
            listOf("好看"),
            texts(
                DanmakuFilterer().filter(
                    list,
                    DanmakuFilterSpec(regexPatterns = listOf("高能"), keywords = listOf("剧透")),
                ),
            ),
        )
    }

    @Test
    fun `regex is compiled only once across list updates`() {
        val filterer = DanmakuFilterer()
        val spec = DanmakuFilterSpec(regexPatterns = listOf("高能", "剧透"))
        repeat(5) {
            filterer.filter(listOf(danmaku("好看$it")), spec)
        }
        assertEquals(2, filterer.compileCount)
    }

    @Test
    fun `regex is reused when the pattern list changes`() {
        val filterer = DanmakuFilterer()
        val list = listOf(danmaku("好看"))
        filterer.filter(list, DanmakuFilterSpec(regexPatterns = listOf("高能")))
        assertEquals(1, filterer.compileCount)

        // 新增一个 pattern, 只需要编译新增的那个
        filterer.filter(list, DanmakuFilterSpec(regexPatterns = listOf("高能", "剧透")))
        assertEquals(2, filterer.compileCount)

        // 回到原来的组合, 不需要再编译
        filterer.filter(list, DanmakuFilterSpec(regexPatterns = listOf("高能")))
        assertEquals(2, filterer.compileCount)
    }

    @Test
    fun `same conversion does not invalidate the cache but a different one does`() {
        val filterer = DanmakuFilterer()
        val list = listOf(danmaku("好看"))
        filterer.filter(list, DanmakuFilterSpec(regexPatterns = listOf("劇透")))
        assertEquals(1, filterer.compileCount)

        filterer.filter(
            list,
            DanmakuFilterSpec(regexPatterns = listOf("劇透"), zhConversion = ZhConversion.TO_SIMPLIFIED),
        )
        assertEquals(2, filterer.compileCount)
    }
}
