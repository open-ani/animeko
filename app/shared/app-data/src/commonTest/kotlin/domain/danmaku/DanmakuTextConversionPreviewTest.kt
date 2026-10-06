/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.danmaku

import kotlinx.coroutines.test.runTest
import me.him188.ani.danmaku.api.DanmakuServiceId
import me.him188.ani.danmaku.ui.DanmakuTextConversion
import kotlin.test.Test
import kotlin.test.assertEquals

class DanmakuTextConversionPreviewTest {
    @Test
    fun `simplified source shows four distinct results`() = runTest {
        val samples = DanmakuTextConversion.entries.associateWith {
            DanmakuTextConversionPreview.sample(DanmakuServiceId.Bilibili, it)
        }
        // 设置界面靠这条示例说明用词差异 (文件夹 -> 資料夾) 与台港「裡 / 裏」的差别
        assertEquals("波奇文件夹里的歌", samples[DanmakuTextConversion.ORIGINAL])
        assertEquals("波奇文件夹里的歌", samples[DanmakuTextConversion.SIMPLIFIED])
        assertEquals("波奇文件夾裏的歌", samples[DanmakuTextConversion.TRADITIONAL])
        assertEquals("波奇資料夾裡的歌", samples[DanmakuTextConversion.TAIWAN])
        assertEquals("波奇資料夾裏的歌", samples[DanmakuTextConversion.HONG_KONG])
        assertEquals(
            4,
            samples.values.toSet().size,
            "示例必须在四个目标文字下两两不同, 否则用户看不出它们有什么区别",
        )
    }

    @Test
    fun `taiwan source sample is written in taiwan script`() = runTest {
        val original = DanmakuTextConversionPreview.sample(DanmakuServiceId.Baha, DanmakuTextConversion.ORIGINAL)
        // 台湾来源已经写着「資料夾」「裡」, 台繁对它无需转换
        assertEquals("波奇資料夾裡的歌", original)
        assertEquals(
            "波奇資料夾裡的歌",
            DanmakuTextConversionPreview.sample(DanmakuServiceId.Baha, DanmakuTextConversion.TAIWAN),
        )
        assertEquals(
            "波奇文件夹里的歌",
            DanmakuTextConversionPreview.sample(DanmakuServiceId.Baha, DanmakuTextConversion.SIMPLIFIED),
        )
    }

    @Test
    fun `original target returns the source sample unchanged`() = runTest {
        // 转换失败时降级为原文, 不应抛出
        assertEquals(
            DanmakuTextConversionPreview.sourceSample(DanmakuServiceId.Dandanplay),
            DanmakuTextConversionPreview.sample(DanmakuServiceId.Dandanplay, DanmakuTextConversion.ORIGINAL),
        )
    }
}
