/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.models.danmaku

import kotlinx.serialization.json.Json
import me.him188.ani.danmaku.ui.DanmakuConfig
import me.him188.ani.danmaku.ui.DanmakuTextConversion
import kotlin.test.Test
import kotlin.test.assertEquals

class DanmakuConfigSerializerTest {
    private val json = Json

    private fun roundTrip(config: DanmakuConfig): DanmakuConfig =
        json.decodeFromString(DanmakuConfigSerializer, json.encodeToString(DanmakuConfigSerializer, config))

    @Test
    fun `default config survives round trip`() {
        // 注意: DanmakuStyle 没有实现 equals, 不能整对象比较
        val decoded = roundTrip(DanmakuConfig.Default)
        assertEquals(DanmakuConfig.Default.speed, decoded.speed)
        assertEquals(DanmakuConfig.Default.displayArea, decoded.displayArea)
        assertEquals(DanmakuConfig.Default.enableColor, decoded.enableColor)
        assertEquals(DanmakuConfig.Default.textConversion, decoded.textConversion)
        assertEquals(DanmakuConfig.Default.style.fontSize, decoded.style.fontSize)
        assertEquals(DanmakuConfig.Default.style.strokeWidth, decoded.style.strokeWidth)
    }

    @Test
    fun `text conversion survives round trip`() {
        val config = DanmakuConfig.Default.copy(textConversion = DanmakuTextConversion.TAIWAN)
        assertEquals(DanmakuTextConversion.TAIWAN, roundTrip(config).textConversion)
    }

    @Test
    fun `legacy json without text conversion decodes to original`() {
        val legacyJson = """{"style":{"fontSize":18.0,"fontWeight":600,"alpha":0.8,"strokeColor":4278190080,"strokeWidth":4.0},"speed":88.0,"safeSeparation":36.0,"displayArea":0.25,"enableColor":true,"enableTop":true,"enableFloating":true,"enableBottom":false,"isDebug":false}"""
        assertEquals(
            DanmakuTextConversion.ORIGINAL,
            json.decodeFromString(DanmakuConfigSerializer, legacyJson).textConversion,
        )
    }
}
