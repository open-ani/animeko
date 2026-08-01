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
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DanmakuFileParserTest {
    private fun xml(vararg entries: String) =
        """<?xml version="1.0" encoding="UTF-8"?><i><chatserver>chat.bilibili.com</chatserver>${entries.joinToString("")}</i>"""

    private fun d(p: String, text: String) = """<d p="$p">$text</d>"""

    ///////////////////////////////////////////////////////////////////////////
    // 格式识别
    ///////////////////////////////////////////////////////////////////////////

    @Test
    fun `detectFormat xml`() {
        assertEquals(DanmakuFileFormat.BilibiliXml, DanmakuFileParser.detectFormat(xml(d("1,1,25,16777215", "a"))))
    }

    @Test
    fun `detectFormat xml without declaration`() {
        assertEquals(DanmakuFileFormat.BilibiliXml, DanmakuFileParser.detectFormat("  \n<i></i>"))
    }

    @Test
    fun `detectFormat json object`() {
        assertEquals(
            DanmakuFileFormat.DandanplayJson,
            DanmakuFileParser.detectFormat("""  {"count":0,"comments":[]}"""),
        )
    }

    @Test
    fun `detectFormat json array`() {
        assertEquals(DanmakuFileFormat.DandanplayJson, DanmakuFileParser.detectFormat("[]"))
    }

    @Test
    fun `detectFormat ignores BOM`() {
        assertEquals(DanmakuFileFormat.DandanplayJson, DanmakuFileParser.detectFormat("﻿{}"))
    }

    @Test
    fun `detectFormat unknown`() {
        assertNull(DanmakuFileParser.detectFormat("hello world"))
        assertNull(DanmakuFileParser.detectFormat(""))
        assertNull(DanmakuFileParser.detectFormat("   "))
    }

    @Test
    fun `parse throws on unknown format`() {
        assertFailsWith<DanmakuFileParseException> { DanmakuFileParser.parse("not a danmaku file") }
    }

    ///////////////////////////////////////////////////////////////////////////
    // Bilibili XML
    ///////////////////////////////////////////////////////////////////////////

    @Test
    fun `xml basic mapping`() {
        val result = DanmakuFileParser.parse(
            xml(d("9.02100,1,25,16777215,1516810212,0,f5b70f3d,4210753616,10", "你好")),
        )
        assertEquals(DanmakuFileFormat.BilibiliXml, result.format)
        assertEquals(1, result.list.size)
        val danmaku = result.list.single()
        assertEquals(9021L, danmaku.playTimeMillis)
        assertEquals(0xFFFFFF, danmaku.color)
        assertEquals("你好", danmaku.text)
        assertEquals(DanmakuLocation.NORMAL, danmaku.location)
        assertEquals(DanmakuServiceId.LocalFile, danmaku.serviceId)
        assertEquals("f5b70f3d", danmaku.senderId)
        assertEquals("local-file-4210753616", danmaku.id)
    }

    @Test
    fun `xml mode mapping`() {
        val result = DanmakuFileParser.parse(
            xml(
                d("1,1,25,1", "scroll1"),
                d("2,2,25,1", "scroll2"),
                d("3,3,25,1", "scroll3"),
                d("4,4,25,1", "bottom"),
                d("5,5,25,1", "top"),
                d("6,6,25,1", "reverse"),
            ),
        )
        assertEquals(
            listOf(
                DanmakuLocation.NORMAL,
                DanmakuLocation.NORMAL,
                DanmakuLocation.NORMAL,
                DanmakuLocation.BOTTOM,
                DanmakuLocation.TOP,
                DanmakuLocation.NORMAL,
            ),
            result.list.map { it.location },
        )
        assertEquals(0, result.skippedCount)
    }

    @Test
    fun `xml skips special and code danmaku`() {
        val result = DanmakuFileParser.parse(
            xml(
                d("1,1,25,1", "normal"),
                d("2,7,93,16777215", """[0.5,0.5,"1-1",4,"advanced"]"""),
                d("3,8,25,16777215", "code"),
                d("4,9,25,16777215", "bas"),
            ),
        )
        assertEquals(listOf("normal"), result.list.map { it.text })
        assertEquals(3, result.skippedCount)
    }

    @Test
    fun `xml color parsing`() {
        val result = DanmakuFileParser.parse(
            xml(
                d("1,1,25,16777215", "white"),
                d("2,1,25,16711680", "red"),
                d("3,1,25,0", "black"),
            ),
        )
        assertEquals(listOf(0xFFFFFF, 0xFF0000, 0x000000), result.list.map { it.color })
    }

    @Test
    fun `xml color out of range is truncated`() {
        val result = DanmakuFileParser.parse(xml(d("1,1,25,4294967295", "x")))
        assertEquals(0xFFFFFF, result.list.single().color)
    }

    @Test
    fun `xml time is float seconds`() {
        val result = DanmakuFileParser.parse(
            xml(
                d("0,1,25,1", "zero"),
                d("0.5,1,25,1", "half"),
                d("1234.567,1,25,1", "large"),
            ),
        )
        assertEquals(listOf(0L, 500L, 1234567L), result.list.map { it.playTimeMillis })
    }

    @Test
    fun `xml unescapes entities`() {
        val result = DanmakuFileParser.parse(
            xml(d("1,1,25,1", "a &amp; b &lt;c&gt; &quot;d&quot; &apos;e&apos; &#65; &#x4e2d;")),
        )
        assertEquals("a & b <c> \"d\" 'e' A 中", result.list.single().text)
    }

    @Test
    fun `xml keeps unknown entity as is`() {
        val result = DanmakuFileParser.parse(xml(d("1,1,25,1", "5 &nbsp2; 6")))
        assertEquals("5 &nbsp2; 6", result.list.single().text)
    }

    @Test
    fun `xml skips malformed entries but keeps good ones`() {
        val result = DanmakuFileParser.parse(
            xml(
                d("", "empty p"),
                d("abc,1,25,1", "bad time"),
                d("1,abc,25,1", "bad mode"),
                d("1,1,25,abc", "bad color"),
                d("1,1", "too few fields"),
                d("-5,1,25,1", "negative time"),
                """<d>no p attribute</d>""",
                d("10,1,25,16777215", "good"),
            ),
        )
        assertEquals(listOf("good"), result.list.map { it.text })
        assertEquals(7, result.skippedCount)
    }

    @Test
    fun `xml skips blank and control-char-only text`() {
        val result = DanmakuFileParser.parse(
            xml(
                d("1,1,25,1", "   "),
                d("2,1,25,1", ""),
                d("3,1,25,1", "&#1;&#2;"),
                d("4,1,25,1", "ok"),
            ),
        )
        assertEquals(listOf("ok"), result.list.map { it.text })
    }

    @Test
    fun `xml trims control chars around text`() {
        val result = DanmakuFileParser.parse(xml(d("1,1,25,1", "&#1; hello &#2;")))
        assertEquals("hello", result.list.single().text)
    }

    @Test
    fun `xml ignores non-d elements starting with d`() {
        val content =
            """<i><div>x</div><data p="1,1,25,1">y</data>${d("1,1,25,16777215", "real")}</i>"""
        val result = DanmakuFileParser.parse(content)
        assertEquals(listOf("real"), result.list.map { it.text })
    }

    @Test
    fun `xml handles extra attributes`() {
        val result = DanmakuFileParser.parse(
            """<i><d user = 'someone' p = "3,5,25,255" other="z">hi</d></i>""",
        )
        val danmaku = result.list.single()
        assertEquals(3000L, danmaku.playTimeMillis)
        assertEquals(DanmakuLocation.TOP, danmaku.location)
        assertEquals(0x0000FF, danmaku.color)
        assertEquals("hi", danmaku.text)
    }

    @Test
    fun `xml with no danmaku throws`() {
        assertFailsWith<DanmakuFileParseException> {
            DanmakuFileParser.parse("""<?xml version="1.0"?><i><chatid>1</chatid></i>""")
        }
    }

    @Test
    fun `xml preserves file order`() {
        // 解析不负责排序, 排序发生在 TimeBasedDanmakuSession.create
        val result = DanmakuFileParser.parse(
            xml(
                d("10,1,25,1", "later"),
                d("1,1,25,1", "earlier"),
            ),
        )
        assertEquals(listOf("later", "earlier"), result.list.map { it.text })
    }

    ///////////////////////////////////////////////////////////////////////////
    // dandanplay JSON
    ///////////////////////////////////////////////////////////////////////////

    @Test
    fun `json basic mapping`() {
        val result = DanmakuFileParser.parse(
            """
            {
              "count": 2,
              "comments": [
                {"cid": 1001, "p": "9.021,1,16777215,user1", "m": "你好"},
                {"cid": 1002, "p": "12.5,5,16711680,user2", "m": "顶部"}
              ]
            }
            """.trimIndent(),
        )
        assertEquals(DanmakuFileFormat.DandanplayJson, result.format)
        assertEquals(2, result.list.size)

        val first = result.list[0]
        assertEquals("local-file-1001", first.id)
        assertEquals(9021L, first.playTimeMillis)
        assertEquals(0xFFFFFF, first.color)
        assertEquals("你好", first.text)
        assertEquals(DanmakuLocation.NORMAL, first.location)
        assertEquals("user1", first.senderId)
        assertEquals(DanmakuServiceId.LocalFile, first.serviceId)

        val second = result.list[1]
        assertEquals(12500L, second.playTimeMillis)
        assertEquals(0xFF0000, second.color)
        assertEquals(DanmakuLocation.TOP, second.location)
    }

    @Test
    fun `json mode mapping`() {
        val result = DanmakuFileParser.parse(
            """
            {"comments":[
              {"cid":1,"p":"1,1,1","m":"a"},
              {"cid":2,"p":"2,4,1","m":"b"},
              {"cid":3,"p":"3,5,1","m":"c"},
              {"cid":4,"p":"4,6,1","m":"d"},
              {"cid":5,"p":"5,7,1","m":"e"}
            ]}
            """.trimIndent(),
        )
        assertEquals(
            listOf(
                DanmakuLocation.NORMAL,
                DanmakuLocation.BOTTOM,
                DanmakuLocation.TOP,
                DanmakuLocation.NORMAL,
            ),
            result.list.map { it.location },
        )
        assertEquals(1, result.skippedCount)
    }

    @Test
    fun `json without uid field`() {
        val result = DanmakuFileParser.parse("""{"comments":[{"cid":7,"p":"1,1,255","m":"x"}]}""")
        assertEquals("", result.list.single().senderId)
        assertEquals(0x0000FF, result.list.single().color)
    }

    @Test
    fun `json bare array`() {
        val result = DanmakuFileParser.parse("""[{"cid":7,"p":"1,1,255","m":"x"}]""")
        assertEquals(1, result.list.size)
    }

    @Test
    fun `json skips malformed entries`() {
        val result = DanmakuFileParser.parse(
            """
            {"comments":[
              {"cid":1,"m":"no p"},
              {"cid":2,"p":"1,1,1"},
              {"cid":3,"p":"x,1,1","m":"bad time"},
              {"cid":4,"p":"1,1","m":"too few"},
              {"cid":5,"p":"1,1,1","m":"   "},
              "not an object",
              {"cid":6,"p":"1,1,1","m":"good"}
            ]}
            """.trimIndent(),
        )
        assertEquals(listOf("good"), result.list.map { it.text })
        assertEquals(6, result.skippedCount)
    }

    @Test
    fun `json missing comments throws`() {
        assertFailsWith<DanmakuFileParseException> { DanmakuFileParser.parse("""{"count":0}""") }
    }

    @Test
    fun `json empty comments throws`() {
        assertFailsWith<DanmakuFileParseException> { DanmakuFileParser.parse("""{"count":0,"comments":[]}""") }
    }

    @Test
    fun `json invalid syntax throws`() {
        assertFailsWith<DanmakuFileParseException> { DanmakuFileParser.parse("""{"comments": [ """) }
    }

    @Test
    fun `json ignores unknown fields`() {
        val result = DanmakuFileParser.parse(
            """{"count":1,"extra":"x","comments":[{"cid":1,"p":"1,1,1","m":"a","unknown":123}]}""",
        )
        assertEquals(1, result.list.size)
    }

    ///////////////////////////////////////////////////////////////////////////
    // 字节输入
    ///////////////////////////////////////////////////////////////////////////

    @Test
    fun `parse bytes with BOM`() {
        val content = "﻿" + xml(d("1,1,25,16777215", "你好"))
        val result = DanmakuFileParser.parse(content.encodeToByteArray())
        assertEquals("你好", result.list.single().text)
    }

    @Test
    fun `custom service id`() {
        val result = DanmakuFileParser.parse(
            xml(d("1,1,25,1", "x")),
            serviceId = DanmakuServiceId.Bilibili,
        )
        assertEquals(DanmakuServiceId.Bilibili, result.list.single().serviceId)
    }

    @Test
    fun `parsed danmaku survive the preprocessing pipeline`() {
        // 导入的弹幕会和其他来源一样进入 TimeBasedDanmakuSession.create 的预处理
        val result = DanmakuFileParser.parse(
            xml(
                d("10,1,25,1", "刷屏"),
                d("1,1,25,1", "刷屏"),
                d("2,1,25,1", "别的"),
            ),
        )
        val sorted = result.list.map { DanmakuSanitizer.sanitize(it) }.sortedBy { it.playTimeMillis }
        val merged = DanmakuMerger.merge(sorted)
        assertEquals(listOf(1000L, 2000L), merged.map { it.playTimeMillis })
        assertTrue(merged.first().text.endsWith("×2"), merged.first().text)
    }
}
