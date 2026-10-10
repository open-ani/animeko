/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.danmaku

import com.generalk1ng.sokkuri.Sokkuri
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
    private val allServiceIds = listOf(
        DanmakuServiceId.Animeko,
        DanmakuServiceId.Dandanplay,
        DanmakuServiceId.Bilibili,
        DanmakuServiceId.Baha,
        DanmakuServiceId.AcFun,
        DanmakuServiceId.Tucao,
    )

    @Test
    fun `original target never converts`() {
        assertNull(resolveSokkuriConfig(DanmakuTextConversion.ORIGINAL))
    }

    @Test
    fun `each target resolves to the chain producing that target`() {
        // 只按目标选链, 与来源无关
        assertEquals(SokkuriConfig.TW2SP, resolveSokkuriConfig(DanmakuTextConversion.SIMPLIFIED))
        assertEquals(SokkuriConfig.S2T, resolveSokkuriConfig(DanmakuTextConversion.TRADITIONAL))
        assertEquals(SokkuriConfig.S2TWP, resolveSokkuriConfig(DanmakuTextConversion.TAIWAN))
        assertEquals(SokkuriConfig.S2HKP, resolveSokkuriConfig(DanmakuTextConversion.HONG_KONG))
    }

    @Test
    fun `traditional to simplified keeps vocabulary conversion`() = runTest {
        // 转简体必须包含词汇转换 (tw2sp), 而不是逐字的 t2s: 滑鼠 -> 鼠标
        val converter = DanmakuTextConverter()
        assertEquals(
            listOf("鼠标里面的硅二极管坏了"),
            converter.convert(
                listOf("滑鼠裡面的矽二極體壞了"),
                DanmakuTextConversion.SIMPLIFIED,
            ),
        )
    }

    @Test
    fun `simplified to taiwan uses taiwan vocabulary`() = runTest {
        val converter = DanmakuTextConverter()
        assertEquals(
            listOf("滑鼠"),
            converter.convert(listOf("鼠标"), DanmakuTextConversion.TAIWAN),
        )
    }

    @Test
    fun `simplified to traditional uses generic s2t`() = runTest {
        val converter = DanmakuTextConverter()
        assertEquals(
            listOf("軟件和網絡"),
            converter.convert(listOf("软件和网络"), DanmakuTextConversion.TRADITIONAL),
        )
    }

    @Test
    fun `conversion does not depend on the danmaku source`() = runTest {
        // 来源只是弹幕的分类桶, 桶里的文字并不统一, 因此转换结果不能随来源变化
        val converter = DanmakuTextConverter()
        val settings = DanmakuTextConversionSettings(global = DanmakuTextConversion.SIMPLIFIED)
        val byService = allServiceIds.associateWith { serviceId ->
            converter.convertDanmakuInfos(
                listOf(danmakuInfo("1", "滑鼠裡面的矽二極體壞了")),
                serviceId,
                settings,
            ).single().text
        }
        assertEquals(setOf("鼠标里面的硅二极管坏了"), byService.values.toSet())
    }

    @Test
    fun `result is always in the target script`() = runTest {
        // 不管输入是简体、繁体、台繁还是港繁, 结果都必须已经是目标文字 —— 用"再转一次不变"来验证.
        // 这正是"不假设来源文字"的前提: 只有结果对目标文字稳定, 猜错来源才不会有后果.
        val converter = DanmakuTextConverter()
        val texts = listOf(
            "波奇文件夹里的歌",
            "波奇文件夾裏的歌",
            "波奇資料夾裡的歌",
            "波奇資料夾裏的歌",
            "滑鼠裡面的矽二極體壞了",
            "请问这个番什么时候更新",
            "請問這個番什麼時候更新",
        )
        val oracleByTarget = mapOf(
            DanmakuTextConversion.SIMPLIFIED to SokkuriConfig.T2S,
            DanmakuTextConversion.TRADITIONAL to SokkuriConfig.S2T,
            DanmakuTextConversion.TAIWAN to SokkuriConfig.S2TWP,
            DanmakuTextConversion.HONG_KONG to SokkuriConfig.S2HKP,
        )
        for ((target, oracleConfig) in oracleByTarget) {
            val result = converter.convert(texts, target)
            val oracle = Sokkuri.create(oracleConfig)
            assertEquals(
                result,
                result.map { oracle.convert(it) },
                "target=$target, result=$result",
            )
        }
        assertSame(texts, converter.convert(texts, DanmakuTextConversion.ORIGINAL))
    }

    @Test
    fun `original target returns input as is`() = runTest {
        val converter = DanmakuTextConverter()
        val texts = listOf("鼠标", "軟件")
        val result = converter.convert(texts, DanmakuTextConversion.ORIGINAL)
        assertSame(texts, result)
    }

    @Test
    fun `same target converts consistently`() = runTest {
        val converter = DanmakuTextConverter()
        val first = converter.convert(listOf("鼠标", "网络"), DanmakuTextConversion.TRADITIONAL)
        // 再次转换 (memo 命中) 结果一致
        val second = converter.convert(listOf("鼠标", "网络"), DanmakuTextConversion.TRADITIONAL)
        assertEquals(first, second)
        // 切换目标再切回: 两条 memo 都热, 结果仍一致
        converter.convert(listOf("鼠标"), DanmakuTextConversion.SIMPLIFIED)
        val third = converter.convert(listOf("鼠标", "网络"), DanmakuTextConversion.TRADITIONAL)
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
