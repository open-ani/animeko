/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.models.danmaku

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import kotlinx.serialization.json.Json
import me.him188.ani.danmaku.api.ZhConversion
import me.him188.ani.danmaku.ui.DanmakuConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DanmakuConfigSerializerTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `shadow round trip`() {
        val config = DanmakuConfig.Default.copy(
            style = DanmakuConfig.Default.style.copy(
                shadow = Shadow(Color.Red, Offset(1f, 2f), blurRadius = 3f),
            ),
        )
        val encoded = json.encodeToString(DanmakuConfigSerializer, config)
        val decoded = json.decodeFromString(DanmakuConfigSerializer, encoded)
        assertEquals(Color.Red, decoded.style.shadow?.color)
        assertEquals(Offset(1f, 2f), decoded.style.shadow?.offset)
        assertEquals(3f, decoded.style.shadow?.blurRadius)
    }

    @Test
    fun `stroke color round trip`() {
        val config = DanmakuConfig.Default.copy(
            style = DanmakuConfig.Default.style.copy(strokeColor = Color.Blue),
        )
        val decoded = json.decodeFromString(
            DanmakuConfigSerializer,
            json.encodeToString(DanmakuConfigSerializer, config),
        )
        assertEquals(Color.Blue, decoded.style.strokeColor)
    }

    @Test
    fun `old config without shadow still deserializes`() {
        val decoded = json.decodeFromString(
            DanmakuConfigSerializer,
            """{"style":{"fontSize":18.0,"fontWeight":600,"alpha":0.8,"strokeWidth":4.0},"speed":88.0}""",
        )
        assertNull(decoded.style.shadow)
        assertEquals(88f, decoded.speed)
    }

    @Test
    fun `old filter config without new fields still deserializes`() {
        val decoded = json.decodeFromString(DanmakuFilterConfig.serializer(), """{"enableRegexFilter":true}""")
        assertEquals(true, decoded.enableRegexFilter)
        assertEquals(false, decoded.enableMerge)
        assertEquals(ZhConversion.NONE, decoded.zhConversion)
        assertEquals(emptyList(), decoded.keywordBlocklist)
    }
}
