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
import me.him188.ani.danmaku.ui.DanmakuTextConversion
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DanmakuTextConversionPreviewTest {
    @Test
    fun `four targets show four distinct results`() = runTest {
        val samples = DanmakuTextConversion.entries.associateWith {
            DanmakuTextConversionPreview.sample(it)
        }
        // 设置界面靠这条示例说明用词差异 (文件夹 -> 資料夾) 与台港「裡 / 裏」的差别
        assertEquals("波奇文件夹里的歌", samples.getValue(DanmakuTextConversion.SIMPLIFIED).convertedText)
        assertEquals("波奇文件夾裏的歌", samples.getValue(DanmakuTextConversion.TRADITIONAL).convertedText)
        assertEquals("波奇資料夾裡的歌", samples.getValue(DanmakuTextConversion.TAIWAN).convertedText)
        assertEquals("波奇資料夾裏的歌", samples.getValue(DanmakuTextConversion.HONG_KONG).convertedText)
        assertEquals(
            4,
            samples.filterKeys { it != DanmakuTextConversion.ORIGINAL }
                .values.map { it.convertedText }.toSet().size,
            "示例必须在四个目标文字下两两不同, 否则用户看不出它们有什么区别",
        )
    }

    @Test
    fun `sample shows a visible conversion`() = runTest {
        // 目标是简体时用台繁样例, 才看得出转换效果
        val simplified = DanmakuTextConversionPreview.sample(DanmakuTextConversion.SIMPLIFIED)
        assertEquals("波奇資料夾裡的歌", simplified.sourceText)
        assertTrue(simplified.isChanged)

        val original = DanmakuTextConversionPreview.sample(DanmakuTextConversion.ORIGINAL)
        assertEquals(original.sourceText, original.convertedText)
        assertFalse(original.isChanged)
    }
}
