/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.danmaku

import me.him188.ani.danmaku.api.DanmakuServiceId
import me.him188.ani.danmaku.ui.DanmakuTextConversion
import kotlin.test.Test
import kotlin.test.assertEquals

class DanmakuTextConversionSettingsTest {
    @Test
    fun `service without override follows global`() {
        val settings = DanmakuTextConversionSettings(
            global = DanmakuTextConversion.TAIWAN,
            overrides = emptyMap(),
        )
        assertEquals(
            DanmakuTextConversion.TAIWAN,
            settings.targetFor(DanmakuServiceId.Bilibili),
        )
    }

    @Test
    fun `override takes precedence over global`() {
        val settings = DanmakuTextConversionSettings(
            global = DanmakuTextConversion.ORIGINAL,
            overrides = mapOf(DanmakuServiceId.Baha to DanmakuTextConversion.SIMPLIFIED),
        )
        assertEquals(
            DanmakuTextConversion.SIMPLIFIED,
            settings.targetFor(DanmakuServiceId.Baha),
        )
        assertEquals(
            DanmakuTextConversion.ORIGINAL,
            settings.targetFor(DanmakuServiceId.Dandanplay),
        )
    }

    @Test
    fun `override can restore original independently of global`() {
        val settings = DanmakuTextConversionSettings(
            global = DanmakuTextConversion.TRADITIONAL,
            overrides = mapOf(DanmakuServiceId.Bilibili to DanmakuTextConversion.ORIGINAL),
        )
        assertEquals(
            DanmakuTextConversion.ORIGINAL,
            settings.targetFor(DanmakuServiceId.Bilibili),
        )
        assertEquals(
            DanmakuTextConversion.TRADITIONAL,
            settings.targetFor(DanmakuServiceId.Tucao),
        )
    }
}
