/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.danmaku

import com.generalk1ng.sokkuri.SokkuriConfig
import kotlinx.coroutines.test.runTest
import me.him188.ani.danmaku.api.DanmakuContent
import me.him188.ani.danmaku.api.DanmakuInfo
import me.him188.ani.danmaku.api.DanmakuLocation
import me.him188.ani.danmaku.api.DanmakuServiceId
import me.him188.ani.danmaku.ui.DanmakuTextConversion
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

class DanmakuTextConverterTest {
    @Test
    fun `original target never converts`() {
        for (source in DanmakuSourceScript.entries) {
            assertNull(resolveSokkuriConfig(source, DanmakuTextConversion.ORIGINAL), "source=$source")
        }
    }

    @Test
    fun `target equal to source script never converts`() {
        assertNull(resolveSokkuriConfig(DanmakuSourceScript.SIMPLIFIED, DanmakuTextConversion.SIMPLIFIED))
        assertNull(resolveSokkuriConfig(DanmakuSourceScript.TRADITIONAL, DanmakuTextConversion.TRADITIONAL))
        assertNull(resolveSokkuriConfig(DanmakuSourceScript.TAIWAN, DanmakuTextConversion.TAIWAN))
        assertNull(resolveSokkuriConfig(DanmakuSourceScript.HONG_KONG, DanmakuTextConversion.HONG_KONG))
    }

    @Test
    fun `simplified source resolves to s2 family`() {
        assertEquals(SokkuriConfig.S2T, resolveSokkuriConfig(DanmakuSourceScript.SIMPLIFIED, DanmakuTextConversion.TRADITIONAL))
        assertEquals(SokkuriConfig.S2TWP, resolveSokkuriConfig(DanmakuSourceScript.SIMPLIFIED, DanmakuTextConversion.TAIWAN))
        assertEquals(SokkuriConfig.S2HKP, resolveSokkuriConfig(DanmakuSourceScript.SIMPLIFIED, DanmakuTextConversion.HONG_KONG))
    }

    @Test
    fun `taiwan source to simplified resolves to tw2sp with vocabulary`() = runTest {
        // 巴哈弹幕以台湾正体为主: 转简体必须包含词汇转换 (tw2sp), 而不是逐字的 t2s
        assertEquals(
            SokkuriConfig.TW2SP,
            resolveSokkuriConfig(DanmakuSourceScript.TAIWAN, DanmakuTextConversion.SIMPLIFIED),
        )
        val converter = DanmakuTextConverter()
        assertEquals(
            listOf("鼠标里面的硅二极管坏了"),
            converter.convert(
                listOf("滑鼠裡面的矽二極體壞了"),
                DanmakuServiceId.Baha,
                DanmakuTextConversion.SIMPLIFIED,
            ),
        )
    }

    @Test
    fun `simplified source to taiwan uses taiwan vocabulary`() = runTest {
        val converter = DanmakuTextConverter()
        assertEquals(
            listOf("滑鼠"),
            converter.convert(
                listOf("鼠标"),
                DanmakuServiceId.Bilibili,
                DanmakuTextConversion.TAIWAN,
            ),
        )
    }

    @Test
    fun `simplified source to traditional uses generic s2t`() = runTest {
        val converter = DanmakuTextConverter()
        assertEquals(
            listOf("軟件和網絡"),
            converter.convert(
                listOf("软件和网络"),
                DanmakuServiceId.Bilibili,
                DanmakuTextConversion.TRADITIONAL,
            ),
        )
    }

    @Test
    fun `original target returns input as is`() = runTest {
        val converter = DanmakuTextConverter()
        val texts = listOf("鼠标", "軟件")
        val result = converter.convert(texts, DanmakuServiceId.Bilibili, DanmakuTextConversion.ORIGINAL)
        assertSame(texts, result)
    }

    @Test
    fun `same source converts consistently`() = runTest {
        val converter = DanmakuTextConverter()
        val first = converter.convert(
            listOf("鼠标", "网络"),
            DanmakuServiceId.Bilibili,
            DanmakuTextConversion.TRADITIONAL,
        )
        // 再次转换 (memo 命中) 结果一致
        val second = converter.convert(
            listOf("鼠标", "网络"),
            DanmakuServiceId.Bilibili,
            DanmakuTextConversion.TRADITIONAL,
        )
        assertEquals(first, second)
        // 切换目标再切回: 两条 memo 都热, 结果仍一致
        converter.convert(listOf("鼠标"), DanmakuServiceId.Bilibili, DanmakuTextConversion.SIMPLIFIED)
        val third = converter.convert(
            listOf("鼠标", "网络"),
            DanmakuServiceId.Bilibili,
            DanmakuTextConversion.TRADITIONAL,
        )
        assertEquals(first, third)
    }

    @Test
    fun `convertDanmakuInfos replaces only text and keeps other fields`() = runTest {
        val converter = DanmakuTextConverter()
        val list = listOf(
            danmakuInfo("1", "滑鼠裡面的矽二極體壞了", playTimeMillis = 1500, color = 0xFF0000),
            danmakuInfo("2", "網絡", playTimeMillis = 3000),
        )
        val result = converter.convertDanmakuInfos(
            list,
            DanmakuServiceId.Baha,
            DanmakuTextConversionSettings(global = DanmakuTextConversion.SIMPLIFIED),
        )
        assertEquals("鼠标里面的硅二极管坏了", result[0].text)
        assertEquals("网络", result[1].text)
        // 其他字段原样保留
        assertEquals("1", result[0].id)
        assertEquals(1500, result[0].playTimeMillis)
        assertEquals(0xFF0000, result[0].color)
        assertEquals(3000, result[1].playTimeMillis)
    }

    @Test
    fun `convertDanmakuInfos original target returns same list instance`() = runTest {
        val converter = DanmakuTextConverter()
        val list = listOf(danmakuInfo("1", "滑鼠"))
        val result = converter.convertDanmakuInfos(
            list,
            DanmakuServiceId.Baha,
            DanmakuTextConversionSettings.Default,
        )
        assertSame(list, result)
    }

    @Test
    fun `convertDanmakuInfos empty list returns same list instance`() = runTest {
        val converter = DanmakuTextConverter()
        val empty = emptyList<DanmakuInfo>()
        val result = converter.convertDanmakuInfos(
            empty,
            DanmakuServiceId.Baha,
            DanmakuTextConversionSettings(global = DanmakuTextConversion.SIMPLIFIED),
        )
        assertSame(empty, result)
    }

    @Test
    fun `convertDanmakuInfos per source override beats global original`() = runTest {
        val converter = DanmakuTextConverter()
        val settings = DanmakuTextConversionSettings(
            global = DanmakuTextConversion.ORIGINAL,
            overrides = mapOf(DanmakuServiceId.Baha to DanmakuTextConversion.SIMPLIFIED),
        )
        val baha = converter.convertDanmakuInfos(
            listOf(danmakuInfo("1", "滑鼠")),
            DanmakuServiceId.Baha,
            settings,
        )
        assertEquals("鼠标", baha[0].text)
        // 其他源跟随全局 (原样), 不受影响
        val other = converter.convertDanmakuInfos(
            listOf(danmakuInfo("2", "滑鼠")),
            DanmakuServiceId.Bilibili,
            settings,
        )
        assertEquals("滑鼠", other[0].text)
    }

    private fun danmakuInfo(
        id: String,
        text: String,
        playTimeMillis: Long = 0,
        color: Int = 0,
    ): DanmakuInfo {
        return DanmakuInfo(
            id = id,
            serviceId = DanmakuServiceId.Baha,
            senderId = "sender",
            content = DanmakuContent(
                playTimeMillis = playTimeMillis,
                color = color,
                text = text,
                location = DanmakuLocation.NORMAL,
            ),
        )
    }
}
